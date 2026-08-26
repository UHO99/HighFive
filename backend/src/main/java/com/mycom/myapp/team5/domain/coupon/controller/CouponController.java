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

import lombok.RequiredArgsConstructor;

/**
 * U001 사용자 쿠폰 조회 + 발급 요청.
 * 관리자 API는 AdminCouponController 담당 (develop CouponController의 /api/admin 중복 매핑 방지).
 */
@RestController
@RequestMapping("/coupons")
@RequiredArgsConstructor
public class CouponController {

	private final CouponService couponService;
	private final CouponIssueStreamProducer producer;

	@LogDescription("쿠폰 목록 조회")
	@GetMapping
	public ResponseEntity<ApiResponse<List<CouponResponse>>> getCoupons() {
		return ResponseEntity.ok(ApiResponse.success(couponService.getCoupons()));
	}

	@LogDescription("쿠폰 정보 조회")
	@GetMapping("/{couponId}")
	public ResponseEntity<ApiResponse<CouponResponse>> getCoupon(@PathVariable("couponId") long couponId) {
		return ResponseEntity.ok(ApiResponse.success(couponService.getCoupon(couponId)));
	}

	@LogDescription("쿠폰 발급 요청")
	@PostMapping("/{couponId}/issue")
	public ResponseEntity<ApiResponse<Void>> requestIssue(@PathVariable("couponId") long couponId, @RequestParam("userId") long userId) {

		long controllerEnteredAtMs = System.currentTimeMillis(); // 추가

		// 발급 전 OPEN 상태 검증 (READY/CLOSE/미존재 쿠폰은 여기서 차단)
		couponService.validateIssueable(couponId);

		// Redis 재고 차감 + Stream 적재 (기존 파이프라인 유지)
		producer.requestIssue(couponId, userId, controllerEnteredAtMs);

		return ResponseEntity.accepted().body(ApiResponse.successNoData());
	}
}
