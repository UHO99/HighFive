package com.mycom.myapp.team5.domain.couponissue.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mycom.myapp.team5.domain.coupon.entity.Coupon;
import com.mycom.myapp.team5.domain.coupon.exception.CouponErrorCode;
import com.mycom.myapp.team5.domain.coupon.exception.CouponException;
import com.mycom.myapp.team5.domain.coupon.repository.CouponRepository;
import com.mycom.myapp.team5.domain.couponissue.dto.CouponFairnessTimelineEntry;
import com.mycom.myapp.team5.domain.couponissue.dto.CouponFairnessTimelinePage;
import com.mycom.myapp.team5.domain.couponissue.dto.CouponIssueHistoryResponse;
import com.mycom.myapp.team5.domain.couponissue.dto.MyCouponResponse;
import com.mycom.myapp.team5.domain.couponissue.entity.CouponIssue;
import com.mycom.myapp.team5.domain.couponissue.repository.CouponIssueRepository;
import com.mycom.myapp.team5.global.common.enums.CouponIssueStatus;
import com.mycom.myapp.team5.global.redis.CouponStockRedisService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CouponIssueServiceImpl implements CouponIssueService{

	private final CouponIssueRepository couponIssueRepository;
	private final CouponRepository couponRepository;
	private final CouponStockRedisService couponStockRedisService;

	@Override
	@Transactional(readOnly=true)
	public List<MyCouponResponse> getMyCoupons(long userId) {
		List<CouponIssue> issues = couponIssueRepository.findByUserIdOrderByIssuedAtDesc(userId);
		if(issues.isEmpty()) {
		return List.of();
		}
		
		// N + 1 방지 : couponId 목록으로 쿠폰을 한 번에 조회 후 Map으로 조합 (S012 리팩토링과 동일한 방식)
		Map<Long, Coupon> couponMap = couponRepository.findAllById(
				issues.stream().map(CouponIssue::getCouponId).toList()
		).stream().collect(Collectors.toMap(Coupon::getId, Function.identity()));
		
        return issues.stream()
                .map(issue -> MyCouponResponse.of(issue, couponMap.get(issue.getCouponId()), rankOf(issue)))
                .toList();
	}

	@Override
	@Transactional(readOnly=true)
	public MyCouponResponse getMyCoupon(long userId, long issueId) {
		CouponIssue issue = couponIssueRepository.findByIdAndUserId(issueId, userId)
				.orElseThrow(() -> new CouponException(CouponErrorCode.COUPON_ISSUE_NOT_FOUND));

		Coupon coupon = couponRepository.findById(issue.getCouponId())
				.orElseThrow(() -> new CouponException(CouponErrorCode.COUPON_NOT_FOUND));
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
		CouponIssue issue = couponIssueRepository.findByIdAndUserIdForUpdate(issueId, userId)
				.orElseThrow(() -> new CouponException(CouponErrorCode.COUPON_ISSUE_NOT_FOUND));
		
		if(issue.getStatus() != CouponIssueStatus.ISSUED) {
			throw new CouponException(CouponErrorCode.COUPON_ISSUE_STATUS_CONFLICT);
		}
		issue.use();						// entity 메서드 호출 (status=USED, usedAt=now)
	}

	@Override
	@Transactional
	public void cancelCoupon(long userId, long issueId) {
		CouponIssue issue = couponIssueRepository.findByIdAndUserIdForUpdate(issueId, userId)
				.orElseThrow(() -> new CouponException(CouponErrorCode.COUPON_ISSUE_NOT_FOUND));
		
		if(issue.getStatus() != CouponIssueStatus.ISSUED) {
			throw new CouponException(CouponErrorCode.COUPON_ISSUE_STATUS_CONFLICT);
		}
		issue.cancel(); 					// entity 메서드 호출 (status=CANCELED, canceledAt=now)
	}

	@Override
	@Transactional(readOnly = true)
	public List<CouponIssueHistoryResponse> getIssuesByCouponId(long couponId) {
		if (!couponRepository.existsById(couponId)) {
			throw new CouponException(CouponErrorCode.COUPON_NOT_FOUND);
		}
		return couponIssueRepository.findByCouponIdOrderByIssuedAtDesc(couponId).stream()
				.map(CouponIssueHistoryResponse::from)
				.toList();
	}

	@Override
	@Transactional(readOnly = true)
	public CouponFairnessTimelinePage getFairnessTimeline(long couponId, long afterRank, int limit) {
		if (!couponRepository.existsById(couponId)) {
			throw new CouponException(CouponErrorCode.COUPON_NOT_FOUND);
		}

		// limit+1건을 받아 hasMore를 판단하고, 실제로 내려줄 건 앞의 limit건만 자른다.
		List<CouponStockRedisService.FairnessLogEntry> fetched =
				couponStockRedisService.fairnessLog(couponId, afterRank, limit);
		if (fetched.isEmpty()) {
			return new CouponFairnessTimelinePage(List.of(), afterRank, false);
		}
		boolean hasMore = fetched.size() > limit;
		List<CouponStockRedisService.FairnessLogEntry> logEntries =
				hasMore ? fetched.subList(0, limit) : fetched;

		// SUCCESS 건만 DB 행이 있다 - 그 userId들만 모아 한 번에 조회 (N+1 방지)
		List<Long> successUserIds = logEntries.stream()
				.filter(e -> "SUCCESS".equals(e.outcome()))
				.map(CouponStockRedisService.FairnessLogEntry::userId)
				.toList();
		Map<Long, CouponIssue> issueByUserId = successUserIds.isEmpty()
				? Map.of()
				: couponIssueRepository.findByCouponIdAndUserIdIn(couponId, successUserIds).stream()
						.collect(Collectors.toMap(CouponIssue::getUserId, Function.identity()));

		List<CouponFairnessTimelineEntry> result = new ArrayList<>(logEntries.size());
		for (CouponStockRedisService.FairnessLogEntry entry : logEntries) {
			CouponIssue issue = issueByUserId.get(entry.userId());
			result.add(new CouponFairnessTimelineEntry(
					entry.rank(), entry.userId(), entry.outcome(),
					issue != null ? issue.getStatus() : null,
					issue != null ? issue.getIssuedAt() : null
			));
		}

		long nextCursor = logEntries.get(logEntries.size() - 1).rank();
		return new CouponFairnessTimelinePage(result, nextCursor, hasMore);
	}

}
