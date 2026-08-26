package com.mycom.myapp.team5.domain.coupon.dto;

import java.time.Instant;

public record CouponSyncLogEntry(
        long couponId,
        Instant syncedAt,
        int issuedQuantity
) {
}
