package com.mycom.myapp.team5.domain.test.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

public record K6RunRequest(@NotBlank(message = "시나리오 ID 필수") String scenarioId,

		@NotNull(message = "쿠폰 ID 필수") @Positive(message = "쿠폰 ID는 1 이상") Long couponId,

		// configurable 시나리오(동시성 정합성 검증)에서만 쓰인다 - 그 외 시나리오면 무시된다.
		@Positive(message = "재고는 1 이상") Integer stock,

		@Positive(message = "동시접속 수는 1 이상") Integer maxVus,

		// 아래는 advanced 시나리오(main_test.js)에서만 쓰인다. null이면 스크립트 기본값이 적용된다.
		/** 총 요청 유저 수 = 재고 × 이 값. 1이면 재고와 딱 맞고, 2면 재고의 2배가 몰린다. */
		@Positive(message = "요청 배수는 1 이상") Integer requestRatio,

		/**
		 * 배수 대신 총 요청 수를 직접 지정한다 - 32,767건처럼 재고×배수로는 정확히 안 떨어지는 값을 실험할 때 쓴다. null이 아니면 requestRatio를 무시하고 이 값을 그대로 총 요청 수로 쓴다.
		 */
		@Positive(message = "요청 수는 1 이상") Integer requestCount,

		/** burst = 최대한 빨리 / even = duration 초에 걸쳐 균등 / ramp = duration 초에 걸쳐 선형 증가(평가 조건) */
		@Pattern(regexp = "burst|even|ramp", message = "유입 방식은 burst, even, ramp 중 하나") String arrival,

		/** arrival이 even이면 유입 시간(초), ramp면 램프업 시간(초). ramp 기본값은 스크립트 쪽 60. */
		@Positive(message = "유입 시간은 1초 이상") Integer duration,

		/** 연타하는 유저 비율 (0.0 ~ 1.0). 0이면 전원 1회만 클릭한다. */
		@DecimalMin(value = "0.0", message = "연타 비율은 0 이상") @DecimalMax(value = "1.0", message = "연타 비율은 1 이하") Double spamRatio,

		/** 연타하는 유저가 몇 번 누르는지 */
		@Positive(message = "연타 횟수는 1 이상") Integer spamClicks) {
}
