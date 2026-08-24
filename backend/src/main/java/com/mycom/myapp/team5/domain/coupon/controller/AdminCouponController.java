package com.mycom.myapp.team5.domain.coupon.controller;

import com.mycom.myapp.team5.domain.coupon.dto.CouponFairnessReport;
import com.mycom.myapp.team5.domain.coupon.dto.CouponOverviewResponse;
import com.mycom.myapp.team5.domain.coupon.dto.CouponRequest;
import com.mycom.myapp.team5.domain.coupon.dto.CouponResponse;
import com.mycom.myapp.team5.domain.coupon.dto.CouponSummary;
import com.mycom.myapp.team5.domain.coupon.dto.CouponUpdateRequest;
import com.mycom.myapp.team5.domain.coupon.service.CouponService;
import com.mycom.myapp.team5.domain.coupon.service.CouponStatusService;
import com.mycom.myapp.team5.domain.couponissue.dto.CouponFairnessTimelineEntry;
import com.mycom.myapp.team5.domain.couponissue.dto.CouponIssueHistoryResponse;
import com.mycom.myapp.team5.domain.couponissue.service.CouponIssueService;
import com.mycom.myapp.team5.global.aspect.LogDescription;
import com.mycom.myapp.team5.global.common.dto.ApiResponse;
import com.mycom.myapp.team5.global.common.enums.CouponStatus;
import com.mycom.myapp.team5.global.redis.CouponStockRedisService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * A003/A004/A005 관리자 쿠폰 API + 대시보드용 open/close/목록 + 시나리오 7 발급 내역 + 선착순 검증.
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
    public ResponseEntity<ApiResponse<CouponResponse>> create(
            @Valid @RequestBody CouponRequest request
    ) {
        CouponResponse response = couponService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response));
    }

    @LogDescription("쿠폰 재고/기간 수정 (관리자)")
    @PatchMapping("/admin/coupons/{couponId}")
    public ResponseEntity<ApiResponse<CouponResponse>> update(
            @PathVariable(name = "couponId") long couponId,
            @Valid @RequestBody CouponUpdateRequest request
    ) {
        return ResponseEntity.ok(ApiResponse.success(couponService.update(couponId, request)));
    }

    @LogDescription("쿠폰 현황 목록 조회 (관리자)")
    @GetMapping("/admin/coupons")
    public ResponseEntity<ApiResponse<List<CouponOverviewResponse>>> getOverviews() {
        return ResponseEntity.ok(ApiResponse.success(couponService.getOverviews()));
    }

    @LogDescription("쿠폰 현황 단건 조회 (관리자)")
    @GetMapping("/admin/coupons/{couponId}")
    public ResponseEntity<ApiResponse<CouponOverviewResponse>> getOverview(
            @PathVariable(name = "couponId") long couponId
    ) {
        return ResponseEntity.ok(ApiResponse.success(couponService.getOverview(couponId)));
    }

    // --- 대시보드/수동 제어: /api/admin/coupons (develop 프론트 호환) ---

    @LogDescription("전체 쿠폰 목록 조회 (관리자)")
    @GetMapping("/api/admin/coupons")
    public ResponseEntity<ApiResponse<List<CouponSummary>>> listCoupons(
            @RequestParam(name = "status", required = false) CouponStatus status
    ) {
        List<CouponSummary> coupons = status != null
                ? couponService.listByStatus(status)
                : couponService.listAll();
        return ResponseEntity.ok(ApiResponse.success(coupons));
    }

    @LogDescription("쿠폰 수동 오픈 (관리자)")
    @PostMapping("/api/admin/coupons/{couponId}/open")
    public ResponseEntity<ApiResponse<Void>> openCoupon(@PathVariable(name = "couponId") long couponId) {
        couponStatusService.openCoupon(couponId);
        return ResponseEntity.ok(ApiResponse.successNoData());
    }

    @LogDescription("쿠폰 수동 마감 (관리자)")
    @PostMapping("/api/admin/coupons/{couponId}/close")
    public ResponseEntity<ApiResponse<Void>> closeCoupon(@PathVariable(name = "couponId") long couponId) {
        couponStatusService.closeCoupon(couponId);
        return ResponseEntity.ok(ApiResponse.successNoData());
    }

    @LogDescription("쿠폰 상태 조회 (관리자)")
    @GetMapping("/api/admin/coupons/{couponId}/status")
    public ResponseEntity<ApiResponse<CouponStatus>> getCouponStatus(@PathVariable(name = "couponId") long couponId) {
        return ResponseEntity.ok(ApiResponse.success(couponStatusService.getStatus(couponId)));
    }

    /**
     * 시나리오 7: 특정 쿠폰의 전체 발급 이력 (모든 유저).
     */
    @LogDescription("관리자 쿠폰 발급 내역 조회")
    @GetMapping("/api/admin/coupons/{couponId}/issues")
    public ResponseEntity<ApiResponse<List<CouponIssueHistoryResponse>>> getCouponIssues(
            @PathVariable(name = "couponId") long couponId
    ) {
        return ResponseEntity.ok(ApiResponse.success(couponIssueService.getIssuesByCouponId(couponId)));
    }

    /**
     * 선착순 공정성 검증 - Redis fairness-log 기반 inversion 감지.
     */
    @LogDescription("쿠폰 선착순 공정성 검증 (관리자)")
    @GetMapping("/api/admin/coupons/{couponId}/fairness")
    public ResponseEntity<ApiResponse<CouponFairnessReport>> getCouponFairness(
            @PathVariable(name = "couponId") long couponId
    ) {
        couponService.getCoupon(couponId);
        return ResponseEntity.ok(ApiResponse.success(couponStockRedisService.analyzeFairness(couponId)));
    }

    /**
     * 대시보드 "쿠폰 발급 이력 · 선착순" 카드용 타임라인.
     */
    @LogDescription("쿠폰 선착순 타임라인 조회 (관리자)")
    @GetMapping("/api/admin/coupons/{couponId}/fairness/timeline")
    public ResponseEntity<ApiResponse<List<CouponFairnessTimelineEntry>>> getFairnessTimeline(
            @PathVariable(name = "couponId") long couponId
    ) {
        couponService.getCoupon(couponId);
        return ResponseEntity.ok(ApiResponse.success(couponIssueService.getFairnessTimeline(couponId)));
    }
}
