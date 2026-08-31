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

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

/**
 * A003/A004/A005 관리자 쿠폰 API + 대시보드용 open/close/목록 + 시나리오 7 발급 내역.
 */
@Tag(name = "쿠폰 관리자", description = "관리자용 쿠폰 CRUD, 오픈/마감 제어, 발급 이력·선착순 공정성 조회 API")
@RestController
@RequiredArgsConstructor
public class AdminCouponController {

	private final CouponService couponService;
	private final CouponStatusService couponStatusService;
	private final CouponIssueService couponIssueService;
	private final CouponStockRedisService couponStockRedisService;

	// --- A003/A004/A005: /admin/coupons ---

	@Operation(summary = "쿠폰 생성 (관리자)", description = "새 쿠폰을 READY 상태로 생성합니다. 생성 시점에는 Redis 재고가 올라가지 않고, 오픈(open) 시점에 초기화됩니다.")
	@LogDescription("쿠폰 생성 (관리자)")
	@PostMapping("/admin/coupons")
	public ResponseEntity<ApiResponse<CouponResponse>> create(@Valid @RequestBody CouponRequest request) {
		CouponResponse response = couponService.create(request);
		return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
	}

	@Operation(summary = "쿠폰 재고/기간 수정 (관리자)", description = "쿠폰의 재고 수량 및 발급 가능 기간을 수정합니다.")
	@LogDescription("쿠폰 재고/기간 수정 (관리자)")
	@PatchMapping("/admin/coupons/{couponId}")
	public ResponseEntity<ApiResponse<CouponResponse>> update(@PathVariable long couponId, @Valid @RequestBody CouponUpdateRequest request) {
		return ResponseEntity.ok(ApiResponse.success(couponService.update(couponId, request)));
	}

	@Operation(summary = "쿠폰 현황 목록 조회 (관리자)", description = "모든 쿠폰의 관리자용 현황(재고, 발급 수 등 포함) 목록을 조회합니다.")
	@LogDescription("쿠폰 현황 목록 조회 (관리자)")
	@GetMapping("/admin/coupons")
	public ResponseEntity<ApiResponse<List<CouponOverviewResponse>>> getOverviews() {
		return ResponseEntity.ok(ApiResponse.success(couponService.getOverviews()));
	}

	@Operation(summary = "쿠폰 현황 단건 조회 (관리자)", description = "특정 쿠폰의 관리자용 현황(재고, 발급 수 등 포함)을 조회합니다.")
	@LogDescription("쿠폰 현황 단건 조회 (관리자)")
	@GetMapping("/admin/coupons/{couponId}")
	public ResponseEntity<ApiResponse<CouponOverviewResponse>> getOverview(@PathVariable long couponId) {
		return ResponseEntity.ok(ApiResponse.success(couponService.getOverview(couponId)));
	}

	// --- 대시보드/수동 제어: /api/admin/coupons (develop 프론트 호환) ---

	@Operation(summary = "전체 쿠폰 목록 조회 (관리자)", description = "관리자 대시보드용 쿠폰 목록 조회입니다. status를 주면 해당 상태만, 안 주면 전체를 반환합니다.")
	@LogDescription("전체 쿠폰 목록 조회 (관리자)")
	@GetMapping("/api/admin/coupons")
	public ResponseEntity<ApiResponse<List<CouponSummary>>> listCoupons(
			@Parameter(description = "필터링할 쿠폰 상태 (미지정 시 전체 조회)") @RequestParam(required = false) CouponStatus status) {
		List<CouponSummary> coupons = status != null ? couponService.listByStatus(status) : couponService.listAll();
		return ResponseEntity.ok(ApiResponse.success(coupons));
	}

	@Operation(summary = "쿠폰 수동 오픈 (관리자)", description = "READY 상태 쿠폰을 OPEN으로 전환하고 Redis 재고를 초기화합니다.")
	@LogDescription("쿠폰 수동 오픈 (관리자)")
	@PostMapping("/api/admin/coupons/{couponId}/open")
	public ResponseEntity<ApiResponse<Void>> openCoupon(@PathVariable long couponId) {
		couponStatusService.openCoupon(couponId);
		return ResponseEntity.ok(ApiResponse.successNoData());
	}

	@Operation(summary = "쿠폰 수동 마감 (관리자)", description = "OPEN 상태 쿠폰을 CLOSE로 전환하고 Redis 재고 키를 정리합니다.")
	@LogDescription("쿠폰 수동 마감 (관리자)")
	@PostMapping("/api/admin/coupons/{couponId}/close")
	public ResponseEntity<ApiResponse<Void>> closeCoupon(@PathVariable long couponId) {
		couponStatusService.closeCoupon(couponId);
		return ResponseEntity.ok(ApiResponse.successNoData());
	}

	@Operation(summary = "쿠폰 상태 조회 (관리자)", description = "쿠폰의 현재 상태(READY/OPEN/CLOSE)를 조회합니다.")
	@LogDescription("쿠폰 상태 조회 (관리자)")
	@GetMapping("/api/admin/coupons/{couponId}/status")
	public ResponseEntity<ApiResponse<CouponStatus>> getCouponStatus(@PathVariable long couponId) {
		return ResponseEntity.ok(ApiResponse.success(couponStatusService.getStatus(couponId)));
	}

	/**
	 * 시나리오 7: 특정 쿠폰의 전체 발급 이력 (모든 유저). 이력이 매우 많을 수 있어 fairness/timeline과 동일한 오프셋(page/size) 페이지네이션을 적용한다.
	 */
	@Operation(summary = "관리자 쿠폰 발급 내역 조회", description = "특정 쿠폰의 전체 사용자 발급 이력을 페이지 단위로 조회합니다. 이력이 매우 많을 수 있어 오프셋 페이지네이션을 적용합니다.")
	@LogDescription("관리자 쿠폰 발급 내역 조회")
	@GetMapping("/api/admin/coupons/{couponId}/issues")
	public ResponseEntity<ApiResponse<CouponIssueHistoryPage>> getCouponIssues(
			@PathVariable long couponId,
			@Parameter(description = "페이지 번호 (1부터 시작)") @RequestParam(defaultValue = "1") int page,
			@Parameter(description = "페이지당 건수") @RequestParam(defaultValue = "50") int size) {
		return ResponseEntity.ok(ApiResponse.success(couponIssueService.getIssuesByCouponId(couponId, page, size)));
	}

	/**
	 * 선착순 공정성 검증 - Redis fairness-log(coupon:fairness-log:{couponId})에 발급 시도마다 순번과 결과(SUCCESS/SOLDOUT/DUPLICATE)를 기록해둔 걸(CouponStockRedisService.issue()) 순서대로 훑어서, 품절 판정 이후에 성공이 끼어든 적(inversion, 새치기)이 있는지 센다. couponId가 존재하지 않으면 CP001(404). 오픈된 적 없는 쿠폰이거나 resetFairnessLog() 이후 시도가 없으면 totalAttempts=0으로 "공정함"(isFair=true)이 나온다 - 검증할 시도 자체가 없었다는 뜻이므로 구분해서 봐야 한다.
	 */
	@Operation(summary = "쿠폰 선착순 공정성 검증 (관리자)", description = "Redis에 기록된 발급 시도 순번/결과 로그를 순서대로 훑어, 품절 판정 이후 성공이 끼어든 새치기(inversion)가 있었는지 검증합니다. "
			+ "시도 자체가 없었던 쿠폰은 totalAttempts=0에 isFair=true로 반환됩니다. 존재하지 않는 쿠폰이면 404(CP001)를 반환합니다.")
	@LogDescription("쿠폰 선착순 공정성 검증 (관리자)")
	@GetMapping("/api/admin/coupons/{couponId}/fairness")
	public ResponseEntity<ApiResponse<CouponFairnessReport>> getCouponFairness(@PathVariable long couponId) {
		couponService.getCoupon(couponId); // 존재하지 않는 쿠폰이면 CP001로 막는다.
		return ResponseEntity.ok(ApiResponse.success(couponStockRedisService.analyzeFairness(couponId)));
	}

	@Operation(summary = "쿠폰 선착순 타임라인 조회 (관리자)", description = "발급 시도를 시간 순으로 페이지 단위 조회합니다. outcome으로 SUCCESS/SOLDOUT/DUPLICATE 등 결과를 필터링할 수 있습니다.")
	@LogDescription("쿠폰 선착순 타임라인 조회 (관리자)")
	@GetMapping("/api/admin/coupons/{couponId}/fairness/timeline")
	public ResponseEntity<ApiResponse<CouponFairnessTimelinePage>> getFairnessTimeline(
			@PathVariable long couponId,
			@Parameter(description = "페이지 번호 (1부터 시작)") @RequestParam(defaultValue = "1") int page,
			@Parameter(description = "페이지당 건수") @RequestParam(defaultValue = "50") int size,
			@Parameter(description = "발급 시도 결과 필터 (기본값 ALL)") @RequestParam(defaultValue = "ALL") CouponFairnessOutcomeFilter outcome) {
		couponService.getCoupon(couponId);
		return ResponseEntity.ok(ApiResponse.success(couponIssueService.getFairnessTimeline(couponId, page, size, outcome)));
	}
}
