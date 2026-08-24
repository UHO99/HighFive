package com.mycom.myapp.team5.benchmark.redis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
public class RedisConcurrencyTest {

	private static final long COUPON_ID = 1L;
	private static final int INITIAL_STOCK = 10_000;
	private static final int REQUEST_COUNT = 100_000;

	@Autowired
	private RedisCouponStockTestService redisCouponStockTestService;

	@BeforeEach
	void setUp() {
		redisCouponStockTestService.initStock(COUPON_ID, INITIAL_STOCK);
	}

	@AfterEach
	void tearDown() {
		redisCouponStockTestService.clear(COUPON_ID);
	}

	@Test
	public void 십만명이_동시에_발급요청해도_재고는_정확히_만큼만_소진된다() throws InterruptedException {
		// given
		ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor();
		CountDownLatch requestsDone = new CountDownLatch(REQUEST_COUNT);
		AtomicInteger successCount = new AtomicInteger(0);

		AtomicLong peakUsedBytes = new AtomicLong();
		Runtime runtime = Runtime.getRuntime();
		ScheduledExecutorService memorySampler = Executors.newSingleThreadScheduledExecutor();
		memorySampler.scheduleAtFixedRate(() -> {
			long used = runtime.totalMemory() - runtime.freeMemory();
			peakUsedBytes.accumulateAndGet(used, Math::max);
		}, 0, 100, TimeUnit.MILLISECONDS);

		long start = System.currentTimeMillis();

		// when: 동기 처리이므로 리턴값으로 바로 성공 여부 판단
		for (long userId = 1; userId <= REQUEST_COUNT; userId++) {
			executorService.execute(() -> {
				try {
					boolean success = redisCouponStockTestService.decreaseStock(COUPON_ID);
					if (success) {
						successCount.incrementAndGet();
					}
				} finally {
					requestsDone.countDown();
				}
			});
		}
		requestsDone.await();
		executorService.shutdown();
		long elapsed = System.currentTimeMillis() - start;
		memorySampler.shutdownNow();

		// then
		assertThat(redisCouponStockTestService.getStock(COUPON_ID)).isZero();
		assertThat(successCount.get()).isEqualTo(INITIAL_STOCK);

		double elapsedSeconds = elapsed / 1000.0;
		long throughput = Math.round(REQUEST_COUNT / elapsedSeconds);

		System.out.println("========== Redis 동시성 테스트 결과 ==========");
		System.out.printf("초기 재고       : %,d%n", INITIAL_STOCK);
		System.out.printf("요청 사용자     : %,d%n", REQUEST_COUNT);
		System.out.printf("성공 발급       : %,d%n", successCount.get());
		System.out.printf("실패 요청       : %,d%n", REQUEST_COUNT - successCount.get());
		System.out.printf("최종 Redis 재고 : %,d%n", redisCouponStockTestService.getStock(COUPON_ID));
		System.out.printf("총 소요 시간    : %.2f sec%n", elapsedSeconds);
		System.out.printf("처리량          : %,d req/s%n", throughput);
		System.out.printf("피크 힙 사용량  : %.1f MB%n", peakUsedBytes.get() / 1024.0 / 1024.0);
		System.out.println("===============================================");
	}
}