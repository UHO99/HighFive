package com.mycom.myapp.team5.domain.couponissue.dto;

import java.util.List;

public record CouponFairnessTimelinePage(
        List<CouponFairnessTimelineEntry> items,
        long nextCursor,
        boolean hasMore
) {
}
