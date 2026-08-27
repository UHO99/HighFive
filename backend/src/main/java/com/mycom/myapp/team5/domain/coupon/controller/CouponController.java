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
import com.mycom.myapp.team5.domain.coupon.dto.CouponSummary;
import com.mycom.myapp.team5.domain.coupon.service.CouponService;
import com.mycom.myapp.team5.domain.coupon.service.CouponStatusService;
import com.mycom.myapp.team5.global.aspect.LogDescription;
import com.mycom.myapp.team5.global.common.dto.ApiResponse;
import com.mycom.myapp.team5.global.common.enums.CouponStatus;
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
	private final CouponStatusService couponStatusService; // 상태 전환 서비스 추가
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

	// 쿠폰 선택 UI(모니터링 대시보드, 쿠폰 오픈 다이얼로그 등)에서 쓰는 목록 조회.
	// status를 안 주면 전체, 주면 그 상태만 - 쿠폰이 아무리 많아도 OPEN/READY는 소수라
	// 필터를 걸면 응답 크기가 전체 쿠폰 수와 무관하게 작게 유지된다.
	@Operation(summary = "쿠폰 목록 조회 (관리자)", description = "관리자 대시보드의 쿠폰 선택 UI용 목록 조회입니다. status를 주면 해당 상태만, 안 주면 전체를 반환합니다.")
	@LogDescription("쿠폰 목록 조회 (관리자)")
	@GetMapping("/api/admin/coupons")
	public ResponseEntity<ApiResponse<List<CouponSummary>>> listCoupons(
			@Parameter(description = "필터링할 쿠폰 상태 (미지정 시 전체 조회)") @RequestParam(required = false) CouponStatus status) {
		List<CouponSummary> coupons = status != null ? couponService.listByStatus(status) : couponService.listAll();
		return ResponseEntity.ok(ApiResponse.success(coupons));
	}

	// 수동 OPEN : READY -> OPEN + Redis 재고 초기화 (스케줄러와 동일 로직 공유)
	@Operation(summary = "쿠폰 수동 오픈 (관리자)", description = "READY 상태 쿠폰을 OPEN으로 전환하고 Redis 재고를 초기화합니다.")
	@LogDescription("쿠폰 수동 오픈 (관리자)")
	@PostMapping("/api/admin/coupons/{couponId}/open")
	public ResponseEntity<ApiResponse<Void>> openCoupon(@PathVariable("couponId") long couponId) {
		couponStatusService.openCoupon(couponId);
		return ResponseEntity.ok(ApiResponse.successNoData());
	}

	// 수동 CLOSE : OPEN -> CLOSE + Redis 재고 키 정리
	@Operation(summary = "쿠폰 수동 마감 (관리자)", description = "OPEN 상태 쿠폰을 CLOSE로 전환하고 Redis 재고 키를 정리합니다.")
	@LogDescription("쿠폰 수동 마감 (관리자)")
	@PostMapping("/api/admin/coupons/{couponId}/close")
	public ResponseEntity<ApiResponse<Void>> closeCoupon(@PathVariable("couponId") long couponId) {
		couponStatusService.closeCoupon(couponId);
		return ResponseEntity.ok(ApiResponse.successNoData());
	}

	// 현재 상태 조회 (관리자 확인용)
	@Operation(summary = "쿠폰 상태 조회 (관리자)", description = "쿠폰의 현재 상태(READY/OPEN/CLOSE)를 조회합니다.")
	@LogDescription("쿠폰 상태 조회 (관리자)")
	@GetMapping("/api/admin/coupons/{couponId}/status")
	public ResponseEntity<ApiResponse<CouponStatus>> getCouponStatus(@PathVariable("couponId") long couponId) {
		return ResponseEntity.ok(ApiResponse.success(couponStatusService.getStatus(couponId)));
	}
}
