package com.mycom.myapp.team5.domain.coupon.dto;

import jakarta.validation.constraints.Min;

import java.time.LocalDateTime;

/**
 * A004: 재고/기간 수정. null 필드는 기존 값 유지.
 */
public record CouponUpdateRequest(
        @Min(value = 1, message = "재고 수량은 1 이상")
        Integer totalQuantity,

        LocalDateTime startAt,

        LocalDateTime endAt
) {
}
