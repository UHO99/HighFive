package com.mycom.myapp.team5.domain.coupon.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mycom.myapp.team5.domain.coupon.dto.CouponFairnessReport;
import com.mycom.myapp.team5.domain.coupon.dto.CouponOverviewResponse;
import com.mycom.myapp.team5.domain.coupon.dto.CouponRequest;
import com.mycom.myapp.team5.domain.coupon.dto.CouponResponse;
import com.mycom.myapp.team5.domain.coupon.dto.CouponSummary;
import com.mycom.myapp.team5.domain.coupon.dto.CouponUpdateRequest;
import com.mycom.myapp.team5.domain.coupon.service.CouponService;
import com.mycom.myapp.team5.domain.coupon.service.CouponStatusService;
import com.mycom.myapp.team5.domain.couponissue.dto.CouponFairnessOutcomeFilter;
import com.mycom.myapp.team5.domain.couponissue.dto.CouponFairnessTimelinePage;
import com.mycom.myapp.team5.domain.couponissue.dto.CouponIssueHistoryPage;
import com.mycom.myapp.team5.domain.couponissue.service.CouponIssueService;
import com.mycom.myapp.team5.global.aspect.LogDescription;
import com.mycom.myapp.team5.global.common.dto.ApiResponse;
import com.mycom.myapp.team5.global.common.enums.CouponStatus;
import com.mycom.myapp.team5.global.redis.CouponStockRedisService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * A003/A004/A005 관리자 쿠폰 API + 대시보드용 open/close/목록 + 시나리오 7 발급 내역.
 */
@RestController
@RequiredArgsConstructor
public class AdminCouponController {

	private final CouponService couponService;
	private final CouponStatusService couponStatusService;
	private final CouponIssueService couponIssueService;
	private final CouponStockRedisService couponStockRedisService;

	// --- A003/A004/A005: /admin/coupons ---

	@LogDescription("쿠폰 생성 (관리자)")
	@PostMapping("/admin/coupons")
	public ResponseEntity<ApiResponse<CouponResponse>> create(@Valid @RequestBody CouponRequest request) {
		CouponResponse response = couponService.create(request);
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
	}

	@LogDescription("쿠폰 재고/기간 수정 (관리자)")
	@PatchMapping("/admin/coupons/{couponId}")
	public ResponseEntity<ApiResponse<CouponResponse>> update(@PathVariable long couponId, @Valid @RequestBody CouponUpdateRequest request) {
		return ResponseEntity.ok(ApiResponse.success(couponService.update(couponId, request)));
	}

	@LogDescription("쿠폰 현황 목록 조회 (관리자)")
	@GetMapping("/admin/coupons")
	public ResponseEntity<ApiResponse<List<CouponOverviewResponse>>> getOverviews() {
		return ResponseEntity.ok(ApiResponse.success(couponService.getOverviews()));
	}

	@LogDescription("쿠폰 현황 단건 조회 (관리자)")
	@GetMapping("/admin/coupons/{couponId}")
	public ResponseEntity<ApiResponse<CouponOverviewResponse>> getOverview(@PathVariable long couponId) {
		return ResponseEntity.ok(ApiResponse.success(couponService.getOverview(couponId)));
	}

	// --- 대시보드/수동 제어: /api/admin/coupons (develop 프론트 호환) ---

	@LogDescription("전체 쿠폰 목록 조회 (관리자)")
	@GetMapping("/api/admin/coupons")
	public ResponseEntity<ApiResponse<List<CouponSummary>>> listCoupons(@RequestParam(required = false) CouponStatus status) {
		List<CouponSummary> coupons = status != null ? couponService.listByStatus(status) : couponService.listAll();
		return ResponseEntity.ok(ApiResponse.success(coupons));
	}

	@LogDescription("쿠폰 수동 오픈 (관리자)")
	@PostMapping("/api/admin/coupons/{couponId}/open")
	public ResponseEntity<ApiResponse<Void>> openCoupon(@PathVariable long couponId) {
		couponStatusService.openCoupon(couponId);
		return ResponseEntity.ok(ApiResponse.successNoData());
	}

	@LogDescription("쿠폰 수동 마감 (관리자)")
	@PostMapping("/api/admin/coupons/{couponId}/close")
	public ResponseEntity<ApiResponse<Void>> closeCoupon(@PathVariable long couponId) {
		couponStatusService.closeCoupon(couponId);
		return ResponseEntity.ok(ApiResponse.successNoData());
	}

	@LogDescription("쿠폰 상태 조회 (관리자)")
	@GetMapping("/api/admin/coupons/{couponId}/status")
	public ResponseEntity<ApiResponse<CouponStatus>> getCouponStatus(@PathVariable long couponId) {
		return ResponseEntity.ok(ApiResponse.success(couponStatusService.getStatus(couponId)));
	}

	/**
	 * 시나리오 7: 특정 쿠폰의 전체 발급 이력 (모든 유저). 이력이 매우 많을 수 있어 fairness/timeline과 동일한 오프셋(page/size) 페이지네이션을 적용한다.
	 */
	@LogDescription("관리자 쿠폰 발급 내역 조회")
	@GetMapping("/api/admin/coupons/{couponId}/issues")
	public ResponseEntity<ApiResponse<CouponIssueHistoryPage>> getCouponIssues(@PathVariable long couponId, @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "50") int size) {
		return ResponseEntity.ok(ApiResponse.success(couponIssueService.getIssuesByCouponId(couponId, page, size)));
	}

	/**
	 * 선착순 공정성 검증 - Redis fairness-log(coupon:fairness-log:{couponId})에 발급 시도마다 순번과 결과(SUCCESS/SOLDOUT/DUPLICATE)를 기록해둔 걸(CouponStockRedisService.issue()) 순서대로 훑어서, 품절 판정 이후에 성공이 끼어든 적(inversion, 새치기)이 있는지 센다. couponId가 존재하지 않으면 CP001(404). 오픈된 적 없는 쿠폰이거나 resetFairnessLog() 이후 시도가 없으면 totalAttempts=0으로 "공정함"(isFair=true)이 나온다 - 검증할 시도 자체가 없었다는 뜻이므로 구분해서 봐야 한다.
	 */
	@LogDescription("쿠폰 선착순 공정성 검증 (관리자)")
	@GetMapping("/api/admin/coupons/{couponId}/fairness")
	public ResponseEntity<ApiResponse<CouponFairnessReport>> getCouponFairness(@PathVariable long couponId) {
		couponService.getCoupon(couponId); // 존재하지 않는 쿠폰이면 CP001로 막는다.
		return ResponseEntity.ok(ApiResponse.success(couponStockRedisService.analyzeFairness(couponId)));
	}

	@LogDescription("쿠폰 선착순 타임라인 조회 (관리자)")
	@GetMapping("/api/admin/coupons/{couponId}/fairness/timeline")
	public ResponseEntity<ApiResponse<CouponFairnessTimelinePage>> getFairnessTimeline(@PathVariable long couponId, @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "50") int size, @RequestParam(defaultValue = "ALL") CouponFairnessOutcomeFilter outcome) {
		couponService.getCoupon(couponId);
		return ResponseEntity.ok(ApiResponse.success(couponIssueService.getFairnessTimeline(couponId, page, size, outcome)));
	}
}
