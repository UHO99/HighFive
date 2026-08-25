package com.mycom.myapp.team5.domain.coupon.dto;

import java.time.Instant;

public record CouponVerifyLogEntry(
        long couponId,
        Instant confirmedAt
) {
}
