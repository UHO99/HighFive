package com.mycom.myapp.team5.domain.coupon.dto;

import com.mycom.myapp.team5.domain.coupon.entity.Coupon;
import com.mycom.myapp.team5.global.common.enums.CouponStatus;

/** 쿠폰 선택 UI 등 목록 조회용 - CouponResponse보다 가볍고 status를 포함한다. */
public record CouponSummary(
        Long id,
        String name,
        CouponStatus status,
        Integer totalQuantity
) {
    public static CouponSummary from(Coupon coupon) {
        return new CouponSummary(
                coupon.getId(),
                coupon.getName(),
                coupon.getStatus(),
                coupon.getTotalQuantity()
        );
    }
}
