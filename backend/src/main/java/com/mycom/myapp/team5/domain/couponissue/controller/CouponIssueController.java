package com.mycom.myapp.team5.domain.couponissue.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mycom.myapp.team5.domain.couponissue.dto.MyCouponResponse;
import com.mycom.myapp.team5.domain.couponissue.service.CouponIssueService;
import com.mycom.myapp.team5.global.aspect.LogDescription;
import com.mycom.myapp.team5.global.common.dto.ApiResponse;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;

@Tag(name = "내 쿠폰", description = "사용자 본인이 발급받은 쿠폰 조회/사용/취소 API")
@RestController
@RequiredArgsConstructor
public class CouponIssueController {
	private final CouponIssueService couponIssueService;

	// 내 쿠폰 목록 조회 (최근 발급 순)
	@Operation(summary = "내 쿠폰 목록 조회", description = "해당 사용자가 발급받은 쿠폰 목록을 최근 발급 순으로 조회합니다.")
	@LogDescription("내 쿠폰 목록  (최근 발급 순)")
	@GetMapping("/api/my/coupons")
	public ResponseEntity<ApiResponse<List<MyCouponResponse>>> getMyCoupons(@Parameter(description = "조회할 사용자 ID") @RequestParam long userId){
		return ResponseEntity.ok(ApiResponse.success(couponIssueService.getMyCoupons(userId)));
	}

	// 내 쿠폰 단건 조회 (보인 소유만, 아닌 경우 CI002)
	@Operation(summary = "내 쿠폰 단건 조회", description = "발급 이력 ID로 쿠폰 상세를 조회합니다. 본인 소유가 아니면 CI002 오류가 발생합니다.")
	@LogDescription("내 쿠폰 단건 조회")
	@GetMapping("/api/my/coupons/{issueId}")
	public ResponseEntity<ApiResponse<MyCouponResponse>> getMyCoupon(
			@Parameter(description = "발급 이력 ID") @PathVariable long issueId,
			@Parameter(description = "조회 요청자 사용자 ID (본인 소유 확인용)") @RequestParam long userId){
		return ResponseEntity.ok(ApiResponse.success(couponIssueService.getMyCoupon(userId, issueId)));
	}

	// 쿠폰 사용
	@Operation(summary = "내 쿠폰 사용", description = "발급받은 쿠폰을 사용 처리합니다.")
	@LogDescription("내 쿠폰 사용")
	@PostMapping("/api/my/coupons/{issueId}/use")
	public ResponseEntity<ApiResponse<Void>> useCoupon(
			@Parameter(description = "발급 이력 ID") @PathVariable long issueId,
			@Parameter(description = "요청자 사용자 ID (본인 소유 확인용)") @RequestParam long userId){
		couponIssueService.useCoupon(userId, issueId);
		return ResponseEntity.ok(ApiResponse.success(null));
	}

	// 쿠폰 취소
	@Operation(summary = "내 쿠폰 취소", description = "발급받은 쿠폰을 취소 처리합니다.")
	@LogDescription("내 쿠폰 취소")
	@PostMapping("/api/my/coupons/{issueId}/cancel")
	public ResponseEntity<ApiResponse<Void>> cancelCoupon(
			@Parameter(description = "발급 이력 ID") @PathVariable long issueId,
			@Parameter(description = "요청자 사용자 ID (본인 소유 확인용)") @RequestParam long userId){
		couponIssueService.cancelCoupon(userId, issueId);
		return ResponseEntity.ok(ApiResponse.success(null));
	}
}
