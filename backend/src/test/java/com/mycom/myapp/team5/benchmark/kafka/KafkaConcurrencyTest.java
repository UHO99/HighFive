package com.mycom.myapp.team5.benchmark.kafka;

import com.mycom.myapp.team5.domain.coupon.entity.Coupon;
import com.mycom.myapp.team5.global.kafka.CouponIssueConsumer;
import com.mycom.myapp.team5.global.kafka.CouponRequestProducer;
import com.mycom.myapp.team5.domain.coupon.repository.CouponRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest(properties = "app.kafka.enabled=true")
public class KafkaConcurrencyTest {

    private static final int INITIAL_STOCK = 10_000;
    private static final int REQUEST_COUNT = 100_000;
    // CouponKafkaConsumerConfig.MAX_POLL_RECORDS는 코드에서 직접 바꾸는 값이라, 이건 리포트용 라벨일 뿐이다.
    private static final String BATCH_SIZE_LABEL = System.getProperty("kafka.batch.label", "500");

    @Autowired
    private CouponRepository couponRepository;

    @Autowired
    private CouponRequestProducer couponRequestProducer;

    @Autowired
    private CouponIssueConsumer couponIssueConsumer;

    private long couponId;

    @BeforeEach
    void setUp() {
        Coupon coupon = couponRepository.save(
                Coupon.builder()
                        .name("동시성-테스트-쿠폰")
                        .totalQuantity(INITIAL_STOCK)
                        .startAt(LocalDateTime.now().minusMinutes(1))
                        .endAt(LocalDateTime.now().plusDays(1))
                        .build()
        );
        couponId = coupon.getId();
        couponIssueConsumer.reset();
    }

    @AfterEach
    void tearDown() {
        couponRepository.deleteById(couponId);
    }

    @Test
    public void 십만명이_동시에_발급요청해도_재고는_정확히_만큼만_소진된다() throws InterruptedException {
        // given: 재고 10,000개 짜리 쿠폰에 10만 명(가상 유저)이 동시에 발급을 요청한다.
        ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor();
        CountDownLatch requestsSent = new CountDownLatch(REQUEST_COUNT);

        // 배치 크기가 커질수록 힙에 더 오래 쌓아두는 게 있는지 보려고, 테스트 내내 사용 힙(peak)을 샘플링한다.
        AtomicLong peakUsedBytes = new AtomicLong();
        Runtime runtime = Runtime.getRuntime();
        ScheduledExecutorService memorySampler = Executors.newSingleThreadScheduledExecutor();
        memorySampler.scheduleAtFixedRate(() -> {
            long used = runtime.totalMemory() - runtime.freeMemory();
            peakUsedBytes.accumulateAndGet(used, Math::max);
        }, 0, 100, TimeUnit.MILLISECONDS);

        long startNanos = System.nanoTime();

        // when
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

        // Kafka 컨슈머가 10만 건을 모두 소비(=DB 반영)할 때까지 대기
        await()
                .atMost(Duration.ofMinutes(5))
                .pollInterval(Duration.ofMillis(500))
                .untilAsserted(() ->
                        assertThat(couponIssueConsumer.getProcessedCount().get()).isEqualTo(REQUEST_COUNT)
                );

        long elapsedNanos = System.nanoTime() - startNanos;
        memorySampler.shutdownNow();

        // 성공적으로 발급된 건수는 초기 재고 수(10,000)와 정확히 일치해야 한다.
        Coupon result = couponRepository.findById(couponId).orElseThrow();
        int successCount = couponIssueConsumer.getSuccessCount().get();
        int duplicateCount = couponIssueConsumer.getDuplicateCount().get();

        printSummary(result.getTotalQuantity(), successCount, duplicateCount, elapsedNanos, peakUsedBytes.get());

        assertThat(result.getTotalQuantity()).isZero();
        assertThat(successCount).isEqualTo(INITIAL_STOCK);
        assertThat(duplicateCount).isZero();
    }

    private void printSummary(int finalStock, int successCount, int duplicateCount, long elapsedNanos, long peakUsedBytes) {
        double elapsedSeconds = elapsedNanos / 1_000_000_000.0;
        long throughput = Math.round(REQUEST_COUNT / elapsedSeconds);
        double peakUsedMb = peakUsedBytes / 1024.0 / 1024.0;

        System.out.println("========== Kafka 동시성 테스트 결과 ==========");
        System.out.printf("MAX_POLL_RECORDS: %s%n", BATCH_SIZE_LABEL);
        System.out.printf("초기 재고       : %,d%n", INITIAL_STOCK);
        System.out.printf("요청 사용자     : %,d%n", REQUEST_COUNT);
        System.out.printf("성공 발급       : %,d%n", successCount);
        System.out.printf("실패 요청       : %,d%n", REQUEST_COUNT - successCount);
        System.out.printf("최종 DB 재고    : %,d%n", finalStock);
        System.out.printf("중복 발급       : %,d%n", duplicateCount);
        System.out.printf("총 소요 시간    : %.2f sec%n", elapsedSeconds);
        System.out.printf("처리량          : %,d req/s%n", throughput);
        System.out.printf("피크 힙 사용량  : %.1f MB%n", peakUsedMb);
        System.out.println("===============================================");
    }

}
