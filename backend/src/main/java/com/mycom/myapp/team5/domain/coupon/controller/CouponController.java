package com.mycom.myapp.team5.domain.coupon.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.mycom.myapp.team5.domain.coupon.dto.CouponResponse;
import com.mycom.myapp.team5.domain.coupon.dto.CouponSummary;
import com.mycom.myapp.team5.domain.coupon.service.CouponService;
import com.mycom.myapp.team5.domain.coupon.service.CouponStatusService;
import com.mycom.myapp.team5.global.aspect.LogDescription;
import com.mycom.myapp.team5.global.common.dto.ApiResponse;
import com.mycom.myapp.team5.global.common.enums.CouponStatus;
import com.mycom.myapp.team5.global.redis.CouponIssueStreamProducer;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/coupons")
@RequiredArgsConstructor
public class CouponController {

	private final CouponService couponService;
	private final CouponStatusService couponStatusService; // 상태 전환 서비스 추가
	private final CouponIssueStreamProducer producer;

	@LogDescription("쿠폰 목록 조회")
	@GetMapping
	public ResponseEntity<ApiResponse<List<CouponResponse>>> getCoupons() {
		return ResponseEntity.ok(ApiResponse.success(couponService.getCoupons()));
	}

	@LogDescription("쿠폰 정보 조회")
	@GetMapping("/{couponId}")
	public ResponseEntity<ApiResponse<CouponResponse>> getCoupon(@PathVariable long couponId) {
		return ResponseEntity.ok(ApiResponse.success(couponService.getCoupon(couponId)));
	}

	@LogDescription("쿠폰 발급 요청")
	@PostMapping("/{couponId}/issue")
	public ResponseEntity<ApiResponse<Void>> requestIssue(@PathVariable("couponId") long couponId, @RequestParam("userId") long userId) {

		// 발급 전 OPEN 상태 검증 (READY/CLOSE/미존재 쿠폰은 여기서 차단)
		couponService.validateIssueable(couponId);

		// Redis 재고 차감 + Stream 적재 (기존 파이프라인 유지)
		producer.requestIssue(couponId, userId);

		return ResponseEntity.accepted().body(ApiResponse.successNoData());
	}

	// 쿠폰 선택 UI(모니터링 대시보드, 쿠폰 오픈 다이얼로그 등)에서 쓰는 목록 조회.
	// status를 안 주면 전체, 주면 그 상태만 - 쿠폰이 아무리 많아도 OPEN/READY는 소수라
	// 필터를 걸면 응답 크기가 전체 쿠폰 수와 무관하게 작게 유지된다.
	@LogDescription("쿠폰 목록 조회 (관리자)")
	@GetMapping("/api/admin/coupons")
	public ResponseEntity<ApiResponse<List<CouponSummary>>> listCoupons(
			@RequestParam(required = false) CouponStatus status) {
		List<CouponSummary> coupons = status != null ? couponService.listByStatus(status) : couponService.listAll();
		return ResponseEntity.ok(ApiResponse.success(coupons));
	}

	// 수동 OPEN : READY -> OPEN + Redis 재고 초기화 (스케줄러와 동일 로직 공유)
	@LogDescription("쿠폰 수동 오픈 (관리자)")
	@PostMapping("/api/admin/coupons/{couponId}/open")
	public ResponseEntity<ApiResponse<Void>> openCoupon(@PathVariable("couponId") long couponId) {
		couponStatusService.openCoupon(couponId);
		return ResponseEntity.ok(ApiResponse.successNoData());
	}

	// 수동 CLOSE : OPEN -> CLOSE + Redis 재고 키 정리
	@LogDescription("쿠폰 수동 마감 (관리자)")
	@PostMapping("/api/admin/coupons/{couponId}/close")
	public ResponseEntity<ApiResponse<Void>> closeCoupon(@PathVariable("couponId") long couponId) {
		couponStatusService.closeCoupon(couponId);
		return ResponseEntity.ok(ApiResponse.successNoData());
	}

	// 현재 상태 조회 (관리자 확인용)
	@LogDescription("쿠폰 상태 조회 (관리자)")
	@GetMapping("/api/admin/coupons/{couponId}/status")
	public ResponseEntity<ApiResponse<CouponStatus>> getCouponStatus(@PathVariable("couponId") long couponId) {
		return ResponseEntity.ok(ApiResponse.success(couponStatusService.getStatus(couponId)));
	}
}
