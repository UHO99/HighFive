package com.mycom.myapp.team5.domain.couponissue.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mycom.myapp.team5.domain.coupon.entity.Coupon;
import com.mycom.myapp.team5.domain.coupon.exception.CouponErrorCode;
import com.mycom.myapp.team5.domain.coupon.exception.CouponException;
import com.mycom.myapp.team5.domain.coupon.repository.CouponRepository;
import com.mycom.myapp.team5.domain.couponissue.dto.CouponFairnessOutcomeFilter;
import com.mycom.myapp.team5.domain.couponissue.dto.CouponFairnessTimelineEntry;
import com.mycom.myapp.team5.domain.couponissue.dto.CouponFairnessTimelinePage;
import com.mycom.myapp.team5.domain.couponissue.dto.CouponIssueHistoryPage;
import com.mycom.myapp.team5.domain.couponissue.dto.CouponIssueHistoryResponse;
import com.mycom.myapp.team5.domain.couponissue.dto.MyCouponResponse;
import com.mycom.myapp.team5.domain.couponissue.entity.CouponIssue;
import com.mycom.myapp.team5.domain.couponissue.repository.CouponIssueRepository;
import com.mycom.myapp.team5.domain.user.entity.User;
import com.mycom.myapp.team5.domain.user.repository.UserRepository;
import com.mycom.myapp.team5.global.common.enums.CouponIssueStatus;
import com.mycom.myapp.team5.global.common.util.MaskingUtils;
import com.mycom.myapp.team5.global.redis.CouponStockRedisService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CouponIssueServiceImpl implements CouponIssueService {

	private final CouponIssueRepository couponIssueRepository;
	private final CouponRepository couponRepository;
	private final UserRepository userRepository;
	private final CouponStockRedisService couponStockRedisService;

	@Override
	@Transactional(readOnly = true)
	public List<MyCouponResponse> getMyCoupons(long userId) {
		List<CouponIssue> issues = couponIssueRepository.findByUserIdOrderByIssuedAtDesc(userId);
		if (issues.isEmpty()) {
			return List.of();
		}

		// N + 1 방지 : couponId 목록으로 쿠폰을 한 번에 조회 후 Map으로 조합 (S012 리팩토링과 동일한 방식)
		Map<Long, Coupon> couponMap = couponRepository.findAllById(issues.stream().map(CouponIssue::getCouponId).toList()).stream().collect(Collectors.toMap(Coupon::getId, Function.identity()));

		return issues.stream().map(issue -> MyCouponResponse.of(issue, couponMap.get(issue.getCouponId()), rankOf(issue))).toList();
	}

	@Override
	@Transactional(readOnly = true)
	public MyCouponResponse getMyCoupon(long userId, long issueId) {
		CouponIssue issue = couponIssueRepository.findByIdAndUserId(issueId, userId).orElseThrow(() -> new CouponException(CouponErrorCode.COUPON_ISSUE_NOT_FOUND));

		Coupon coupon = couponRepository.findById(issue.getCouponId()).orElseThrow(() -> new CouponException(CouponErrorCode.COUPON_NOT_FOUND));
		return MyCouponResponse.of(issue, coupon, rankOf(issue));
	}

	// 발급 id 오름차순 = 발급 순서라, 같은 쿠폰에서 자기 id 이하 건수를 세면 그게 곧 자기 순번이다.
	// 사용자 1명이 보유한 쿠폰 수는 보통 한두 개라 목록 조회에서도 N+1 부담이 크지 않다.
	private long rankOf(CouponIssue issue) {
		return couponIssueRepository.countByCouponIdAndIdLessThanEqual(issue.getCouponId(), issue.getId());
	}

	@Override
	@Transactional
	public void useCoupon(long userId, long issueId) {
		CouponIssue issue = couponIssueRepository.findByIdAndUserIdForUpdate(issueId, userId).orElseThrow(() -> new CouponException(CouponErrorCode.COUPON_ISSUE_NOT_FOUND));

		if (issue.getStatus() != CouponIssueStatus.ISSUED) {
			throw new CouponException(CouponErrorCode.COUPON_ISSUE_STATUS_CONFLICT);
		}
		issue.use(); // entity 메서드 호출 (status=USED, usedAt=now)
	}

	@Override
	@Transactional
	public void cancelCoupon(long userId, long issueId) {
		CouponIssue issue = couponIssueRepository.findByIdAndUserIdForUpdate(issueId, userId).orElseThrow(() -> new CouponException(CouponErrorCode.COUPON_ISSUE_NOT_FOUND));

		if (issue.getStatus() != CouponIssueStatus.USED) {
			throw new CouponException(CouponErrorCode.COUPON_ISSUE_STATUS_CONFLICT);
		}
		issue.cancel(); // entity 메서드 호출 (status=CANCELED, canceledAt=now, usedAt은 보존)
	}

	@Override
	@Transactional(readOnly = true)
	public CouponIssueHistoryPage getIssuesByCouponId(long couponId, int page, int size) {
		if (!couponRepository.existsById(couponId)) {
			throw new CouponException(CouponErrorCode.COUPON_NOT_FOUND);
		}


		int zeroBasedPage = Math.max(0, page - 1);
		Pageable pageable = PageRequest.of(zeroBasedPage, size);

		Page<CouponIssue> issuePage = couponIssueRepository.findByCouponIdOrderByIssuedAtDesc(couponId, pageable);

		List<Long> userIds = issuePage.getContent().stream().map(CouponIssue::getUserId).distinct().toList();
		Map<Long, User> userMap = userIds.isEmpty() ? Map.of() : userRepository.findAllById(userIds).stream().collect(Collectors.toMap(User::getId, Function.identity()));

		List<CouponIssueHistoryResponse> items = issuePage.getContent().stream().map(issue -> CouponIssueHistoryResponse.from(issue, userMap.get(issue.getUserId()))).toList();

		return new CouponIssueHistoryPage(items, issuePage.getNumber() + 1, issuePage.getTotalPages(), issuePage.getTotalElements());

	}

	@Override
	@Transactional(readOnly = true)
	public CouponFairnessTimelinePage getFairnessTimeline(long couponId, int page, int size, CouponFairnessOutcomeFilter filter) {
		if (!couponRepository.existsById(couponId)) {
			throw new CouponException(CouponErrorCode.COUPON_NOT_FOUND);
		}

		long totalElements = couponStockRedisService.fairnessLogCount(couponId, filter);
		int totalPages = size <= 0 ? 0 : (int) ((totalElements + size - 1) / size);
		int safePage = totalPages == 0 ? 1 : Math.min(Math.max(page, 1), totalPages);
		long offset = (long) (safePage - 1) * size;

		List<CouponStockRedisService.FairnessLogEntry> logEntries = couponStockRedisService.fairnessLogPage(couponId, offset, size, filter);
		if (logEntries.isEmpty()) {
			return new CouponFairnessTimelinePage(List.of(), safePage, size, totalElements, totalPages);
		}

		// SUCCESS 건만 DB 행이 있다 - 그 userId들만 모아 한 번에 조회 (N+1 방지)
		List<Long> successUserIds = logEntries.stream().filter(e -> "SUCCESS".equals(e.outcome())).map(CouponStockRedisService.FairnessLogEntry::userId).toList();
		Map<Long, CouponIssue> issueByUserId = successUserIds.isEmpty() ? Map.of() : couponIssueRepository.findByCouponIdAndUserIdIn(couponId, successUserIds).stream().collect(Collectors.toMap(CouponIssue::getUserId, Function.identity()));

		// 타임라인 표시용 마스킹 이름/이메일 — 페이지의 모든 userId를 한 번에 조회
		Map<Long, User> userMap = userRepository.findAllById(logEntries.stream().map(CouponStockRedisService.FairnessLogEntry::userId).distinct().toList()).stream()
				.collect(Collectors.toMap(User::getId, Function.identity()));

		List<CouponFairnessTimelineEntry> result = new ArrayList<>(logEntries.size());
		for (CouponStockRedisService.FairnessLogEntry entry : logEntries) {
			CouponIssue issue = issueByUserId.get(entry.userId());
			User user = userMap.get(entry.userId());
			boolean hasTimings = entry.controllerEnteredAtMs() != null && entry.gateEnteredAtMs() != null && entry.redisTimeMicros() != null;
			result.add(new CouponFairnessTimelineEntry(entry.rank(), entry.userId(), entry.outcome(), issue != null ? issue.getStatus() : null, issue != null ? issue.getIssuedAt() : null, hasTimings ? entry.controllerEnteredAtMs() : null, hasTimings ? entry.gateEnteredAtMs() : null, hasTimings ? entry.redisTimeMicros() : null, // 변경 - redisTimeMs() → redisTimeMicros()
					hasTimings ? entry.gateEnteredAtMs() - entry.controllerEnteredAtMs() : null, hasTimings ? (entry.redisTimeMicros() / 1000) - entry.gateEnteredAtMs() : null, // 변경 - µs→ms 환산 후 차감
					user == null ? null : MaskingUtils.maskName(user.getName()), user == null ? null : MaskingUtils.maskEmail(user.getEmail())));
		}

		return new CouponFairnessTimelinePage(result, safePage, size, totalElements, totalPages);
	}

}
