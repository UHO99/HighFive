package com.mycom.myapp.team5.benchmark.crud;

import com.mycom.myapp.team5.domain.coupon.entity.Coupon;
import com.mycom.myapp.team5.domain.coupon.repository.CouponRepository;
import com.mycom.myapp.team5.domain.coupon.service.CouponService;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * CRUD(Normal) — 직접 DB UPDATE 동시성/정합성 테스트 + DB I/O latency 측정.
 *
 * <p>Kafka / Redis 와 동일 조건:
 * INITIAL_STOCK=10_000, REQUEST_COUNT=100_000, VirtualThread, HTTP 미경유.
 */
@SpringBootTest(properties = {
        "spring.kafka.listener.auto-startup=false",
        // 기본 풀 크기(10) 유지. 대기가 길어도 타임아웃 실패하지 않게만 늘림
        "spring.datasource.hikari.connection-timeout=600000",
        "spring.jpa.show-sql=false"
})
public class CrudConcurrencyTest {

    private static final int INITIAL_STOCK = 10_000;
    private static final int REQUEST_COUNT = 100_000;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private CouponService couponService;

    @Autowired
    private DataSource dataSource;

    private long couponId;

    @BeforeEach
    void setUp() {
        // 시드(V2__sample.sql)에 의존하지 않고 테스트가 직접 쿠폰 생성
        Coupon coupon = couponRepository.save(
                Coupon.builder()
                        .name("CRUD-동시성-테스트-쿠폰")
                        .totalQuantity(INITIAL_STOCK)
                        .startAt(LocalDateTime.now().minusMinutes(1))
                        .endAt(LocalDateTime.now().plusDays(1))
                        .build()
        );
        couponId = coupon.getId();
    }

    @AfterEach
    void tearDown() {
        couponRepository.deleteById(couponId);
    }

    @Test
    void 십만명이_동시에_재고감소해도_재고는_정확히_만큼만_소진된다() throws InterruptedException {
        // given
        ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor();
        CountDownLatch done = new CountDownLatch(REQUEST_COUNT);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failCount = new AtomicInteger();
        ConcurrentLinkedQueue<Long> latencies = new ConcurrentLinkedQueue<>();
        AtomicInteger maxThreadsAwaitingConnection = new AtomicInteger();

        HikariPoolMXBean poolMXBean = ((HikariDataSource) dataSource).getHikariPoolMXBean();

        AtomicLong peakUsedBytes = new AtomicLong();
        Runtime runtime = Runtime.getRuntime();
        ScheduledExecutorService memorySampler = Executors.newSingleThreadScheduledExecutor();
        memorySampler.scheduleAtFixedRate(() -> {
            long used = runtime.totalMemory() - runtime.freeMemory();
            peakUsedBytes.accumulateAndGet(used, Math::max);
        }, 0, 100, TimeUnit.MILLISECONDS);

        long wallStart = System.nanoTime();

        // when: 컨트롤러 없이 서비스 재고 차감 직접 호출 (동기 — Awaitility 불필요)
        for (int i = 0; i < REQUEST_COUNT; i++) {
            executorService.execute(() -> {
                try {
                    int awaiting = poolMXBean.getThreadsAwaitingConnection();
                    maxThreadsAwaitingConnection.accumulateAndGet(awaiting, Math::max);

                    long start = System.nanoTime();
                    int updated = couponService.decreaseStockBatch(couponId, 1);
                    latencies.add(System.nanoTime() - start);

                    if (updated == 1) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    failCount.incrementAndGet();
                } finally {
                    done.countDown();
                }
            });
        }
        done.await();
        executorService.shutdown();

        long wallElapsedMs = (System.nanoTime() - wallStart) / 1_000_000;
        memorySampler.shutdownNow();

        // then: 최종 재고 == 0, 성공 발급 == INITIAL_STOCK, 오버셀(음수) 없음
        Coupon result = couponRepository.findById(couponId).orElseThrow();

        assertThat(failCount.get()).as("커넥션/예외 실패 건수").isZero();
        assertThat(result.getTotalQuantity()).isZero();
        assertThat(result.getTotalQuantity()).isGreaterThanOrEqualTo(0);
        assertThat(successCount.get()).isEqualTo(INITIAL_STOCK);

        printReport(wallElapsedMs, result.getTotalQuantity(), successCount.get(),

                maxThreadsAwaitingConnection.get() > 0, latencies, peakUsedBytes.get());

    }

    private void printReport(
            long wallElapsedMs,
            int finalStock,
            int successCount,
            boolean connectionPoolWaitOccurred,
            ConcurrentLinkedQueue<Long> latenciesNanos,
            long peakUsedBytes
    ) {
        List<Long> sorted = new ArrayList<>(latenciesNanos);
        Collections.sort(sorted);

        double avgMs = sorted.stream().mapToLong(Long::longValue).average().orElse(0) / 1_000_000.0;
        double p50 = percentileMs(sorted, 0.50);
        double p95 = percentileMs(sorted, 0.95);
        double p99 = percentileMs(sorted, 0.99);
        double max = sorted.isEmpty() ? 0 : sorted.get(sorted.size() - 1) / 1_000_000.0;

        System.out.println();
        System.out.println("===== [CRUD/Normal] 4.1 동시성/정합성 =====");
        System.out.printf("최종 재고        : %d%n", finalStock);
        System.out.printf("성공 발급 건수   : %d%n", successCount);
        System.out.printf("오버셀 여부      : %s%n", finalStock < 0 ? "YES" : "NO");
        System.out.printf("소요 시간        : %d ms%n", wallElapsedMs);
        System.out.println("===== [CRUD/Normal] 4.2 DB I/O latency =====");
        System.out.printf("평균 / p50 / p95 / p99 / 최대 (ms): %.3f / %.3f / %.3f / %.3f / %.3f%n",
                avgMs, p50, p95, p99, max);
        System.out.printf("커넥션 풀 대기   : %s%n", connectionPoolWaitOccurred ? "YES" : "NO");
        System.out.printf("피크 힙 사용량   : %.1f MB%n", peakUsedBytes / 1024.0 / 1024.0);
        System.out.println("===========================================");
    }

    private double percentileMs(List<Long> sortedNanos, double percentile) {
        if (sortedNanos.isEmpty()) {
            return 0;
        }
        int index = (int) Math.ceil(percentile * sortedNanos.size()) - 1;
        index = Math.max(0, Math.min(index, sortedNanos.size() - 1));
        return sortedNanos.get(index) / 1_000_000.0;
    }
}
