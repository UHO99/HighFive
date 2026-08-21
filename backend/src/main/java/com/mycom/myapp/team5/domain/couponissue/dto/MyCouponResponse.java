package com.mycom.myapp.team5.domain.couponissue.dto;

import java.time.LocalDateTime;

import com.mycom.myapp.team5.domain.coupon.entity.Coupon;
import com.mycom.myapp.team5.domain.couponissue.entity.CouponIssue;
import com.mycom.myapp.team5.global.common.enums.CouponIssueStatus;

public record MyCouponResponse (
	Long issueId,
	Long couponId,
	String couponName,
	CouponIssueStatus status,
	/** 이 쿠폰에서 몇 번째로 발급받았는지 (1부터 시작) - CouponIssueService가 계산해서 채운다. */
	Long rank,
	LocalDateTime issuedAt,
	LocalDateTime usedAt,
	LocalDateTime cancelAt,
	LocalDateTime expiredAt
) {
	public static MyCouponResponse of(CouponIssue issue, Coupon coupon, long rank) {
		return new MyCouponResponse(
				issue.getId(),
				issue.getCouponId(),
				coupon.getName(),
				issue.getStatus(),
				rank,
				issue.getIssuedAt(),
				issue.getUsedAt(),
				issue.getCanceledAt(),
				issue.getExpiredAt()
		);
	}
}
