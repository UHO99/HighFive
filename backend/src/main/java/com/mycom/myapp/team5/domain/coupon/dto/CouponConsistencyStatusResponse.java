package com.mycom.myapp.team5.domain.coupon.dto;

import java.time.Instant;
import java.util.List;

/**
 * 대시보드 "정합성 동기화 · 검증" 카드용 - 동기화/검증 배치가 마지막으로 언제, 몇 건을 처리했는지.
 * couponId와 무관한 시스템 전체 상태라 MonitoringController에 couponId 없이 독립 경로로 노출한다 -
 * 특정 쿠폰이 없어서(오픈된 쿠폰 없음 등) 모니터링 대시보드 조회가 실패하는 동안에도 이 값은 계속
 * 갱신되어야 하기 때문이다. lastRunAt이 intervalMs의 몇 배 이상 지나도록 안 갱신되면 프론트에서
 * "응답 없음"으로 판단한다.
 */
public record CouponConsistencyStatusResponse(
        Sync sync,
        Verify verify
) {

    public record Sync(Instant lastRunAt, long intervalMs, int targetCount, int syncedCount) {
    }

    /** verify는 CLOSE된 쿠폰만 대상으로 한다 - OPEN 중에는 targetCount에 절대 안 잡힌다. */
    public record Verify(
            Instant lastRunAt, long intervalMs, int targetCount, int confirmedCount, int mismatchCount,
            List<MismatchEvent> mismatchHistory
    ) {
    }
}
