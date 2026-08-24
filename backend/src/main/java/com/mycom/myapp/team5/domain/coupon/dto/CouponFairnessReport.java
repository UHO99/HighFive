package com.mycom.myapp.team5.domain.coupon.dto;

public record CouponFairnessReport(long couponId, long totalAttempts, long inversionCount) {

	public boolean isFair() {
		return inversionCount == 0;
	}

	public double inversionRate() {
		return totalAttempts == 0 ? 0.0 : (double) inversionCount / totalAttempts;
	}
}
