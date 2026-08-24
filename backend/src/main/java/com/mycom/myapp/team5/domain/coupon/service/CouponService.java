package com.mycom.myapp.team5.domain.coupon.service;

import com.mycom.myapp.team5.domain.coupon.dto.*;
import com.mycom.myapp.team5.global.common.enums.CouponStatus;

import java.util.List;

public interface CouponService {

    CouponResponse getExampleById(Long id);

    CouponResponse create(CouponRequest request);

    CouponResponse update(long couponId, CouponUpdateRequest request);

    CouponResponse getCoupon(long couponId);

    int decreaseStockBatch(long couponId, int requestedCount);

    List<CouponResponse> getCoupons();

    CouponOverviewResponse getOverview(long couponId);

    List<CouponOverviewResponse> getOverviews();

    /**
     * 발급 전 쿠폰이 발급 가능한 상태(OPEN)인지 검증
     * READY/CLOSE 상태면 COUPON_NOT_OPEN(CP002), 존재하지 않으면 COUPON_NOT_FOUND(CP001) 예외 발생
     */
    void validateIssueable(long couponId);

    List<CouponSummary> listAll();

    /**
     * 상태별 쿠폰 목록 - 대시보드 모니터링 선택지(OPEN만)나 오픈 대상 선택지(READY만)처럼
     * 전체 쿠폰 수와 무관하게 항상 작은 부분집합만 필요한 화면에서 쓴다.
     */
    List<CouponSummary> listByStatus(CouponStatus status);

}
