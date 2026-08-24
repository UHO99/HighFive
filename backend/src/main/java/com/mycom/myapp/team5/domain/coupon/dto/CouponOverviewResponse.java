package com.mycom.myapp.team5.domain.coupon.dto;

import com.mycom.myapp.team5.domain.coupon.entity.Coupon;
import com.mycom.myapp.team5.global.common.enums.CouponStatus;

import java.time.LocalDateTime;

/**
 * A005: 관리자 쿠폰 현황 (총량·발급·잔여·상태).
 */
public record CouponOverviewResponse(
        Long id,
        String name,
        CouponStatus status,
        Integer totalQuantity,
        long issuedQuantity,
        long remainingQuantity,
        Integer redisRemainingQuantity,
        LocalDateTime startAt,
        LocalDateTime endAt
) {
    public static CouponOverviewResponse of(
            Coupon coupon,
            long issuedQuantity,
            Integer redisRemainingQuantity
    ) {
        int total = coupon.getTotalQuantity() == null ? 0 : coupon.getTotalQuantity();
        long remaining = Math.max(0, total - issuedQuantity);
        return new CouponOverviewResponse(
                coupon.getId(),
                coupon.getName(),
                coupon.getStatus(),
                total,
                issuedQuantity,
                remaining,
                redisRemainingQuantity,
                coupon.getStartAt(),
                coupon.getEndAt()
        );
    }
}
