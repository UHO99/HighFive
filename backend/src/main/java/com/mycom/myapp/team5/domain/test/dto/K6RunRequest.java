package com.mycom.myapp.team5.domain.test.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record K6RunRequest(
        @NotBlank(message = "시나리오 ID 필수")
        String scenarioId,

        @NotNull(message = "쿠폰 ID 필수")
        @Positive(message = "쿠폰 ID는 1 이상")
        Long couponId,

        // configurable 시나리오(동시성 정합성 검증)에서만 쓰인다 - 그 외 시나리오면 무시된다.
        @Positive(message = "재고는 1 이상")
        Integer stock,

        @Positive(message = "동시접속 수는 1 이상")
        Integer maxVus
) {
}
