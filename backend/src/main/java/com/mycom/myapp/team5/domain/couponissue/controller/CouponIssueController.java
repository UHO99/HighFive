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

import lombok.RequiredArgsConstructor;

@RestController
@RequiredArgsConstructor
public class CouponIssueController {
	private final CouponIssueService couponIssueService;

	// 내 쿠폰 목록 조회 (최근 발급 순)
	@LogDescription("내 쿠폰 목록  (최근 발급 순)")
	@GetMapping("/api/my/coupons")
	public ResponseEntity<ApiResponse<List<MyCouponResponse>>> getMyCoupons(@RequestParam(name = "userId") long userId){
		return ResponseEntity.ok(ApiResponse.success(couponIssueService.getMyCoupons(userId)));
	}

	// 내 쿠폰 단건 조회 (보인 소유만, 아닌 경우 CI002)
	@LogDescription("내 쿠폰 단건 조회")
	@GetMapping("/api/my/coupons/{issueId}")
	public ResponseEntity<ApiResponse<MyCouponResponse>> getMyCoupon(@PathVariable(name = "issueId") long issueId, @RequestParam(name = "userId") long userId){
		return ResponseEntity.ok(ApiResponse.success(couponIssueService.getMyCoupon(userId, issueId)));
	}
	
	// 쿠폰 사용
	@LogDescription("내 쿠폰 사용")
	@PostMapping("/api/my/coupons/{issueId}/use")
	public ResponseEntity<ApiResponse<Void>> useCoupon(@PathVariable(name = "issueId") long issueId, @RequestParam(name = "userId") long userId){
		couponIssueService.useCoupon(userId, issueId);
		return ResponseEntity.ok(ApiResponse.success(null));
	}
	
	// 쿠폰 취소
	@LogDescription("내 쿠폰 취소")
	@PostMapping("/api/my/coupons/{issueId}/cancel")
	public ResponseEntity<ApiResponse<Void>> cancelCoupon(@PathVariable(name = "issueId") long issueId, @RequestParam(name = "userId") long userId){
		couponIssueService.cancelCoupon(userId, issueId);
		return ResponseEntity.ok(ApiResponse.success(null));
	}
}
