package com.mycom.myapp.team5.domain.coupon.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mycom.myapp.team5.domain.coupon.dto.CouponResponse;
import com.mycom.myapp.team5.domain.coupon.service.CouponService;
import com.mycom.myapp.team5.global.aspect.LogDescription;
import com.mycom.myapp.team5.global.common.dto.ApiResponse;
import com.mycom.myapp.team5.global.redis.CouponIssueStreamProducer;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "쿠폰", description = "일반 사용자용 쿠폰 조회 및 선착순 발급 요청 API")
@RestController
@RequestMapping("/coupons")
@RequiredArgsConstructor
public class CouponController {

	private final CouponService couponService;
	private final CouponIssueStreamProducer producer;

	@Operation(summary = "쿠폰 목록 조회", description = "전체 쿠폰 목록을 조회합니다.")
	@LogDescription("쿠폰 목록 조회")
	@GetMapping
	public ResponseEntity<ApiResponse<List<CouponResponse>>> getCoupons() {
		return ResponseEntity.ok(ApiResponse.success(couponService.getCoupons()));
	}

	@Operation(summary = "쿠폰 정보 조회", description = "쿠폰 ID로 단건 상세 정보를 조회합니다.")
	@LogDescription("쿠폰 정보 조회")
	@GetMapping("/{couponId}")
	public ResponseEntity<ApiResponse<CouponResponse>> getCoupon(@PathVariable("couponId") long couponId) {
		return ResponseEntity.ok(ApiResponse.success(couponService.getCoupon(couponId)));
	}

	@Operation(summary = "쿠폰 발급 요청", description = "선착순 쿠폰 발급을 요청합니다. OPEN 상태가 아닌 쿠폰은 즉시 거부되고, "
			+ "통과된 요청은 Redis에서 원자적으로 재고를 차감한 뒤 Stream에 적재되어 비동기로 처리됩니다(202 Accepted).")
	@LogDescription("쿠폰 발급 요청")
	@PostMapping("/{couponId}/issue")
	public ResponseEntity<ApiResponse<Void>> requestIssue(
			@Parameter(description = "발급 대상 쿠폰 ID") @PathVariable("couponId") long couponId,
			@Parameter(description = "발급 요청자 사용자 ID") @RequestParam("userId") long userId) {

		long controllerEnteredAtMs = System.currentTimeMillis(); // 추가

		// 발급 전 OPEN 상태 검증 (READY/CLOSE/미존재 쿠폰은 여기서 차단)
		couponService.validateIssueable(couponId);

		// Redis 재고 차감 + Stream 적재 (기존 파이프라인 유지)
		producer.requestIssue(couponId, userId, controllerEnteredAtMs);

		return ResponseEntity.accepted().body(ApiResponse.successNoData());
	}
}
