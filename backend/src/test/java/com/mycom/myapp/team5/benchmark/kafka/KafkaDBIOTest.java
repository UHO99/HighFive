package com.mycom.myapp.team5.benchmark.kafka;

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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import javax.sql.DataSource;
import java.time.Duration;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;

/**
 * 1.2 DB I/O 성능 테스트 (Kafka) - 컨트롤 문서 참고.
 * 처리 방식(메시지 단위/배치 단위)과 무관하게, "DB에 실제로 몇 번 왕복했고 그 1회가 얼마나
 * 걸렸는지"를 기준으로 latency를 수집한다. 현재 컨슈머는 배치 리스너라 표본 수는
 * 요청 건수(REQUEST_COUNT)가 아니라 실제 DB 호출(배치) 횟수만큼만 모인다.
 */
@SpringBootTest(properties = "app.kafka.enabled=true")
public class KafkaDBIOTest {

    private static final int INITIAL_STOCK = 10_000;
    private static final int REQUEST_COUNT = 20_000;

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private CouponRequestProducer couponRequestProducer;

    @Autowired
    private CouponIssueConsumer couponIssueConsumer;

    @Autowired
    private DataSource dataSource;

    @MockitoSpyBean
    private CouponService couponService;

    private final ConcurrentLinkedQueue<Long> dbCallLatenciesNanos = new ConcurrentLinkedQueue<>();
    private final AtomicInteger maxThreadsAwaitingConnection = new AtomicInteger(0);

    private long couponId;

    @BeforeEach
    void setUp() {
        Coupon coupon = couponRepository.save(
                Coupon.builder()
                        .name("DB-IO-테스트-쿠폰")
                        .totalQuantity(INITIAL_STOCK)
                        .startAt(LocalDateTime.now().minusMinutes(1))
                        .endAt(LocalDateTime.now().plusDays(1))
                        .build()
        );
        couponId = coupon.getId();
        couponIssueConsumer.reset();
        dbCallLatenciesNanos.clear();
        maxThreadsAwaitingConnection.set(0);

        // decreaseStockBatch 실제 호출 1회 = DB 왕복 1회를 감싸서 소요시간을 기록한다.
        doAnswer(invocation -> {
            long start = System.nanoTime();
            try {
                return invocation.callRealMethod();
            } finally {
                dbCallLatenciesNanos.add(System.nanoTime() - start);
            }
        }).when(couponService).decreaseStockBatch(anyLong(), anyInt());
    }

    @AfterEach
    void tearDown() {
        couponRepository.deleteById(couponId);
    }

    @Test
    public void kafka_컨슈머의_DB_호출_latency를_측정한다() throws InterruptedException {
        ScheduledExecutorService poolSampler = Executors.newSingleThreadScheduledExecutor();
        poolSampler.scheduleAtFixedRate(this::samplePoolState, 0, 100, TimeUnit.MILLISECONDS);

        ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor();
        CountDownLatch requestsSent = new CountDownLatch(REQUEST_COUNT);

        for (long userId = 1; userId <= REQUEST_COUNT; userId++) {
            long currentUserId = userId;
            executorService.execute(() -> {
                try {
                    couponRequestProducer.request(couponId, currentUserId);
                } finally {
                    requestsSent.countDown();
                }
            });
        }
        requestsSent.await();
        executorService.shutdown();

        await()
                .atMost(Duration.ofMinutes(5))
                .pollInterval(Duration.ofMillis(500))
                .untilAsserted(() ->
                        assertThat(couponIssueConsumer.getProcessedCount().get()).isEqualTo(REQUEST_COUNT)
                );

        poolSampler.shutdown();

        Coupon result = couponRepository.findById(couponId).orElseThrow();
        printSummary();

        assertThat(result.getTotalQuantity()).isZero();
        assertThat(couponIssueConsumer.getSuccessCount().get()).isEqualTo(INITIAL_STOCK);
        assertThat(dbCallLatenciesNanos).isNotEmpty();
    }

    private void samplePoolState() {
        if (!(dataSource instanceof HikariDataSource hikariDataSource)) {
            return;
        }
        HikariPoolMXBean pool = hikariDataSource.getHikariPoolMXBean();
        if (pool == null) {
            return;
        }
        maxThreadsAwaitingConnection.updateAndGet(current -> Math.max(current, pool.getThreadsAwaitingConnection()));
    }

    private void printSummary() {
        List<Long> sortedNanos = new ArrayList<>(dbCallLatenciesNanos);
        Collections.sort(sortedNanos);

        int callCount = sortedNanos.size();
        double avgMs = sortedNanos.stream().mapToLong(Long::longValue).average().orElse(0) / 1_000_000.0;
        double p50 = percentileMs(sortedNanos, 50);
        double p95 = percentileMs(sortedNanos, 95);
        double p99 = percentileMs(sortedNanos, 99);
        double maxMs = sortedNanos.isEmpty() ? 0 : sortedNanos.get(sortedNanos.size() - 1) / 1_000_000.0;
        boolean poolWaitOccurred = maxThreadsAwaitingConnection.get() > 0;

        System.out.println("========== Kafka DB I/O 성능 테스트 결과 ==========");
        System.out.printf("DB 호출 횟수    : %,d%n", callCount);
        System.out.printf("평균            : %.2f ms%n", avgMs);
        System.out.printf("p50             : %.2f ms%n", p50);
        System.out.printf("p95             : %.2f ms%n", p95);
        System.out.printf("p99             : %.2f ms%n", p99);
        System.out.printf("최대            : %.2f ms%n", maxMs);
        System.out.printf("커넥션 풀 대기 발생 : %s (최대 대기 스레드 수 %d)%n",
                poolWaitOccurred ? "발생" : "없음", maxThreadsAwaitingConnection.get());
        System.out.println("====================================================");
        System.out.printf("| Kafka | %.2f | %.2f | %.2f | %.2f | %.2f | %s |%n",
                avgMs, p50, p95, p99, maxMs, poolWaitOccurred ? "O" : "X");
    }

    private static double percentileMs(List<Long> sortedNanos, double percentile) {
        if (sortedNanos.isEmpty()) {
            return 0;
        }
        int index = (int) Math.ceil(percentile / 100.0 * sortedNanos.size()) - 1;
        index = Math.max(0, Math.min(index, sortedNanos.size() - 1));
        return sortedNanos.get(index) / 1_000_000.0;
    }

}
