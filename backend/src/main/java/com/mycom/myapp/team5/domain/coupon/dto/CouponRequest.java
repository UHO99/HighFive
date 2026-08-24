package com.mycom.myapp.team5.domain.coupon.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;

public record CouponRequest(
        @NotBlank(message = "이름 필수")
        @Size(max = 20, message = "이름 20자 이하")
        String name,

        @NotNull(message = "재고 수량 필수")
        @Min(value = 1, message = "재고 수량은 1 이상")
        Integer totalQuantity,

        LocalDateTime startAt,

        LocalDateTime endAt
) {
}
