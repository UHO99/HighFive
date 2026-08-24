package com.mycom.myapp.team5.domain.monitoring.service;

import com.mycom.myapp.team5.domain.coupon.entity.Coupon;
import com.mycom.myapp.team5.domain.coupon.exception.CouponErrorCode;
import com.mycom.myapp.team5.domain.coupon.exception.CouponException;
import com.mycom.myapp.team5.domain.coupon.repository.CouponRepository;
import com.mycom.myapp.team5.domain.couponissue.repository.CouponIssueRepository;
import com.mycom.myapp.team5.domain.monitoring.dto.MonitoringDashboardResponse;
import com.mycom.myapp.team5.domain.monitoring.dto.MonitoringDashboardResponse.ApiResponseStats;
import com.mycom.myapp.team5.domain.monitoring.dto.MonitoringDashboardResponse.CouponIssueStatus;
import com.mycom.myapp.team5.domain.monitoring.dto.MonitoringDashboardResponse.DbStorage;
import com.mycom.myapp.team5.domain.monitoring.dto.MonitoringDashboardResponse.OverIssueMonitor;
import com.mycom.myapp.team5.domain.monitoring.dto.MonitoringDashboardResponse.ServerResources;
import com.mycom.myapp.team5.domain.monitoring.dto.MonitoringDashboardResponse.StockStatus;
import com.mycom.myapp.team5.domain.monitoring.dto.MonitoringDashboardResponse.StreamStatus;
import com.mycom.myapp.team5.domain.monitoring.metric.CouponIssueMetricsRecorder;
import com.mycom.myapp.team5.domain.monitoring.metric.DbInsertMetricsRecorder;
import com.mycom.myapp.team5.domain.monitoring.metric.HttpMetricsRecorder;
import com.mycom.myapp.team5.global.common.enums.CouponStatus;
import com.mycom.myapp.team5.global.redis.CouponIssueStreamConsumer;
import com.mycom.myapp.team5.global.redis.CouponStockKeys;
import com.mycom.myapp.team5.global.redis.CouponStreamPendingChecker;
import com.mycom.myapp.team5.global.redis.CouponStreamPendingDrainer;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryUsage;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
public class MonitoringServiceImpl implements MonitoringService {

    private final CouponRepository couponRepository;
    private final CouponIssueRepository couponIssueRepository;
    private final StringRedisTemplate stringRedisTemplate;
    private final CouponStreamPendingChecker pendingChecker;
    private final CouponStreamPendingDrainer pendingDrainer;
    private final CouponIssueStreamConsumer streamConsumer;
    private final DataSource dataSource;

    private final HttpMetricsRecorder httpMetricsRecorder;
    private final CouponIssueMetricsRecorder couponIssueMetricsRecorder;
    private final DbInsertMetricsRecorder dbInsertMetricsRecorder;

    @Override
    public void resetMetrics() {
        httpMetricsRecorder.reset();
        couponIssueMetricsRecorder.reset();
        dbInsertMetricsRecorder.reset();
    }

    @Override
    public int drainPendingStream(long couponId) {
        return pendingDrainer.drainAll(couponId);
    }

    @Override
    public MonitoringDashboardResponse getDashboard(long couponId) {
        Coupon coupon = couponRepository.findById(couponId)
                .orElseThrow(() -> new CouponException(CouponErrorCode.COUPON_NOT_FOUND));

        return new MonitoringDashboardResponse(
                couponId,
                // DB에 안 들어가고 바로 JSON으로 나가는 값이라 JDBC connectionTimeZone 보정을 못 받는다 -
                // 컨테이너 TZ와 무관하게 항상 KST가 되도록 명시적으로 zone을 지정한다.
                LocalDateTime.now(ZoneId.of("Asia/Seoul")),
                serverResources(),
                apiResponseStats(),
                couponIssueStatus(couponId),
                overIssueMonitor(coupon),
                stockStatus(coupon),
                streamStatus(couponId),
                dbStorage()
        );
    }

    private ServerResources serverResources() {
        com.sun.management.OperatingSystemMXBean osBean =
                (com.sun.management.OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();

        double cpuUsagePercent = Math.max(0, osBean.getProcessCpuLoad()) * 100;

        long totalPhysical = osBean.getTotalMemorySize();
        long freePhysical = osBean.getFreeMemorySize();
        double memoryUsagePercent = totalPhysical == 0
                ? 0
                : (totalPhysical - freePhysical) * 100.0 / totalPhysical;

        MemoryMXBean memoryMXBean = ManagementFactory.getMemoryMXBean();
        MemoryUsage heap = memoryMXBean.getHeapMemoryUsage();
        double jvmHeapUsagePercent = heap.getMax() <= 0
                ? 0
                : heap.getUsed() * 100.0 / heap.getMax();

        double rps = httpMetricsRecorder.snapshot().rps();

        return new ServerResources(rps, cpuUsagePercent, memoryUsagePercent, jvmHeapUsagePercent);
    }

    private ApiResponseStats apiResponseStats() {
        HttpMetricsRecorder.Snapshot snapshot = httpMetricsRecorder.snapshot();
        return new ApiResponseStats(
                snapshot.avgMs(),
                snapshot.p95Ms(),
                snapshot.p99Ms(),
                snapshot.errorRatePercent(),
                snapshot.successAvgMs(),
                snapshot.failAvgMs()
        );
    }

    private CouponIssueStatus couponIssueStatus(long couponId) {
        CouponIssueMetricsRecorder.Snapshot snapshot = couponIssueMetricsRecorder.snapshot(couponId);
        return new CouponIssueStatus(
                snapshot.total(),
                snapshot.success(),
                snapshot.fail(),
                snapshot.perSecond(),
                snapshot.soldOutFail(),
                snapshot.duplicateFail()
        );
    }

    // "초과 발급 감시" - Redis에서 실제로 차감/기록된 발급 건수(재고 소진 기준)와 DB 이력 건수를 나란히
    // 비교한다. 배치 flush가 비동기라 짧은 순간의 불일치는 정상이고, DB쪽이 Redis쪽을 넘어서면(=초과
    // 발급) 그때가 진짜 이상 신호다.
    private OverIssueMonitor overIssueMonitor(Coupon coupon) {
        long issuedCount = redisIssuedCount(coupon.getId());
        long dbHistoryCount = couponIssueRepository.countByCouponId(coupon.getId());

        return new OverIssueMonitor(
                issuedCount,
                issuedCount,
                dbHistoryCount,
                issuedCount == dbHistoryCount,
                coupon.getIssuedQuantity(),
                coupon.getConsistencyConfirmedAt() != null
        );
    }

    private StockStatus stockStatus(Coupon coupon) {
        long remaining = redisStockRemaining(coupon.getId());
        long total = coupon.getTotalQuantity();
        long issuedCount = redisIssuedCount(coupon.getId());
        double consumedPercent = total == 0 ? 0 : issuedCount * 100.0 / total;

        return new StockStatus(issuedCount, remaining, total, consumedPercent);
    }

    private StreamStatus streamStatus(long couponId) {
        int activeSubscriptions = streamConsumer.activeSubscriptionCount();
        int totalStreams = couponRepository.findByStatus(CouponStatus.OPEN).size();

        return new StreamStatus(
                activeSubscriptions,
                totalStreams,
                pendingChecker.pendingCount(couponId),
                pendingChecker.maxLagMillis(couponId)
        );
    }

    private DbStorage dbStorage() {
        int active = 0;
        int max = 0;
        if (dataSource instanceof HikariDataSource hikariDataSource) {
            HikariPoolMXBean pool = hikariDataSource.getHikariPoolMXBean();
            if (pool != null) {
                active = pool.getActiveConnections();
            }
            max = hikariDataSource.getMaximumPoolSize();
        }

        DbInsertMetricsRecorder.Snapshot snapshot = dbInsertMetricsRecorder.snapshot();
        return new DbStorage(active, max, snapshot.throughputPerSecond(), snapshot.avgBatchSize(), snapshot.maxBatchSize());
    }

    private long redisStockRemaining(long couponId) {
        String raw = stringRedisTemplate.opsForValue().get(CouponStockKeys.stockKey(couponId));
        return raw == null ? 0 : Long.parseLong(raw);
    }

    private long redisIssuedCount(long couponId) {
        Long size = stringRedisTemplate.opsForSet().size(CouponStockKeys.issuedSetKey(couponId));
        return size == null ? 0 : size;
    }
}
