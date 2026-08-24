package com.mycom.myapp.team5.domain.monitoring.dto;

import java.time.LocalDateTime;

/**
 * 부하 테스트 모니터링 대시보드 응답. 쿠폰 한 건을 기준으로 시스템 지표(서버 리소스/API 응답)와
 * 그 쿠폰의 발급 파이프라인 지표(재고, 스트림, DB 저장)를 한 번에 묶어서 내려준다.
*/
public record MonitoringDashboardResponse(
        long couponId,
        LocalDateTime measuredAt,
        ServerResources serverResources,
        ApiResponseStats apiResponse,
        CouponIssueStatus couponIssueStatus,
        OverIssueMonitor overIssueMonitor,
        StockStatus stockStatus,
        StreamStatus streamStatus,
        DbStorage dbStorage
) {

    public record ServerResources(
            double rps,
            double cpuUsagePercent,
            double memoryUsagePercent,
            double jvmHeapUsagePercent
    ) {}

    public record ApiResponseStats(
            double avgResponseTimeMs,
            double p95ResponseTimeMs,
            double p99ResponseTimeMs,
            double errorRatePercent,
            double successAvgResponseTimeMs,
            double failAvgResponseTimeMs
    ) {}

    public record CouponIssueStatus(
            long totalRequests,
            long successCount,
            long failCount,
            double issuePerSecond,
            long soldOutFailCount,
            long duplicateFailCount
    ) {}

    public record OverIssueMonitor(
            long stockDepletedCount,
            long successIssuedCount,
            long dbHistoryCount,
            boolean matched,
            /** S012(CouponStockSyncService)가 마지막으로 동기화해둔 값. 아직 한 번도 안 됐으면 null. */
            Integer recordedIssuedQuantity,
            /** S013(CouponStockValidationService)가 "드레인 완료 + 기록값=실측값"을 확정했는지. */
            boolean consistencyConfirmed
    ) {}

    public record StockStatus(
            long issuedCount,
            long redisStockRemaining,
            long redisStockTotal,
            double redisStockConsumedPercent
    ) {}

    public record StreamStatus(
            int activeSubscriptions,
            int totalStreams,
            long pendingCount,
            long maxLagMs
    ) {}

    public record DbStorage(
            int dbConnPoolActive,
            int dbConnPoolMax,
            double dbInsertThroughputPerSecond,
            double batchInsertAvgSize,
            int batchInsertMaxSize
    ) {}
}
