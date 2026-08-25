package com.mycom.myapp.team5.domain.coupon.dto;

import java.time.Instant;

public record MismatchEvent(
        long couponId,
        Instant detectedAt,
        Instant resolvedAt,
        Integer recordedIssuedQuantity,
        long actualIssuedCount,
        long pendingCount
) {
    public boolean isResolved() {
        return resolvedAt != null;
    }

    /** 아직 불일치가 계속되는 사이클 - detectedAt은 유지, 스냅샷만 최신으로 갱신. */
    public MismatchEvent withLatestSnapshot(Integer recordedIssuedQuantity, long actualIssuedCount, long pendingCount) {
        return new MismatchEvent(couponId, detectedAt, null, recordedIssuedQuantity, actualIssuedCount, pendingCount);
    }

    /** 이번 사이클엔 더 이상 불일치 목록에 안 보임 - 해소 시각을 찍는다. */
    public MismatchEvent resolved(Instant resolvedAt) {
        return new MismatchEvent(couponId, detectedAt, resolvedAt, recordedIssuedQuantity, actualIssuedCount, pendingCount);
    }
}
