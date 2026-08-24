package com.mycom.myapp.team5.domain.coupon.dto;

import com.mycom.myapp.team5.domain.coupon.entity.Coupon;
import com.mycom.myapp.team5.global.common.enums.CouponStatus;

import java.time.LocalDateTime;

public record CouponResponse(
        Long id,
        String name,
        Integer totalQuantity,
        LocalDateTime startAt,
        LocalDateTime endAt,
        CouponStatus status
) {
    public static CouponResponse from(Coupon coupon) {
        return new CouponResponse(
                coupon.getId(),
                coupon.getName(),
                coupon.getTotalQuantity(),
                coupon.getStartAt(),
                coupon.getEndAt(),
                coupon.getStatus()
        );
    }
}
