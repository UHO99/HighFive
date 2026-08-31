package com.mycom.myapp.team5.domain.couponissue.dto;

import java.util.List;

public record CouponFairnessTimelinePage(
        List<CouponFairnessTimelineEntry> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
