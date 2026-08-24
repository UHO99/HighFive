package com.mycom.myapp.team5.domain.couponissue.service;

import java.util.List;

import com.mycom.myapp.team5.domain.couponissue.dto.CouponFairnessTimelineEntry;
import com.mycom.myapp.team5.domain.couponissue.dto.CouponIssueHistoryResponse;
import com.mycom.myapp.team5.domain.couponissue.dto.MyCouponResponse;
import com.mycom.myapp.team5.global.aspect.LogDescription;

public interface CouponIssueService {
	// 내 쿠폰 목록 (최근 발급 순)
	@LogDescription("내 쿠폰 목록 조회(최근 발급 순)")
	List<MyCouponResponse> getMyCoupons(long userId);

	// 내 쿠폰 단건 (본인 소유만, 없으면 CI002)
	@LogDescription("내 쿠폰 단건 조회")
	MyCouponResponse getMyCoupon(long userId, long issueId);

	/**
	 * 시나리오 7: 관리자 — 특정 쿠폰의 전체 발급 이력.
	 * 쿠폰이 없으면 CP001.
	 */
	@LogDescription("관리자 쿠폰 발급 내역 조회")
	List<CouponIssueHistoryResponse> getIssuesByCouponId(long couponId);

	/**
	 * 대시보드 "쿠폰 발급 이력 · 선착순" 카드용 - Redis fairness-log의 실제 처리 순서(rank)에 DB
	 * 상태/시각을 얹은 최근 목록. 쿠폰이 없으면 CP001.
	 */
	@LogDescription("쿠폰 선착순 타임라인 조회 (관리자)")
	List<CouponFairnessTimelineEntry> getFairnessTimeline(long couponId);

	// 쿠폰 사용 (본인 소유, ISSUED만 가능, 그 외 CI003)
	@LogDescription("내 쿠폰 사용")
	void useCoupon(long userId, long issueId);

	// 쿠폰 취소 (본인 소유, ISSUED만 가능, 그 외 CI003)
	@LogDescription("내 쿠폰 취소")
	void cancelCoupon(long userId, long issueId);
}
