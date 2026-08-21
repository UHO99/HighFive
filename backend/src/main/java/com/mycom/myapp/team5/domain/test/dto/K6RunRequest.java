package com.mycom.myapp.team5.domain.test.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record K6RunRequest(
        @NotBlank(message = "시나리오 ID 필수")
        String scenarioId,

        @NotNull(message = "쿠폰 ID 필수")
        @Positive(message = "쿠폰 ID는 1 이상")
        Long couponId
) {
}
