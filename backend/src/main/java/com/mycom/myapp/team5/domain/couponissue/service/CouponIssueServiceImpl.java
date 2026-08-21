package com.mycom.myapp.team5.domain.couponissue.service;

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
import com.mycom.myapp.team5.domain.couponissue.dto.CouponIssueHistoryResponse;
import com.mycom.myapp.team5.domain.couponissue.dto.MyCouponResponse;
import com.mycom.myapp.team5.domain.couponissue.entity.CouponIssue;
import com.mycom.myapp.team5.domain.couponissue.repository.CouponIssueRepository;
import com.mycom.myapp.team5.global.common.enums.CouponIssueStatus;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CouponIssueServiceImpl implements CouponIssueService{
	
	private final CouponIssueRepository couponIssueRepository;
	private final CouponRepository couponRepository;

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

}
