package com.mycom.myapp.team5.domain.coupon.dto;

import java.time.Instant;
import java.util.List;

public record CouponConsistencyStatusResponse(
        Sync sync,
        Verify verify
) {

    public record Sync(
            Instant lastRunAt, long intervalMs, int targetCount, int syncedCount,
            /** 실제로 issuedQuantity가 써진(=드레인 완료 후 동기화된) 최근 건 로그 - 최신순, 최대 20건. */
            List<CouponSyncLogEntry> log
    ) {
    }

    /** verify는 CLOSE된 쿠폰만 대상으로 한다 - OPEN 중에는 targetCount에 절대 안 잡힌다. */
    public record Verify(
            Instant lastRunAt, long intervalMs, int targetCount, int confirmedCount, int mismatchCount,
            List<MismatchEvent> mismatchHistory,
            /** "드레인 완료 + 기록값=실측값"이 확정된 최근 건 로그 - 최신순, 최대 20건. */
            List<CouponVerifyLogEntry> log
    ) {
    }
}
