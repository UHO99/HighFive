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

/**
 * RedisConcurrencyTest(요청 1건당 왕복 1번)와 달리, Kafka 컨슈머와 같은 알고리즘으로 비교하기 위한 버전.
 * 요청을 BATCH_SIZE(=Kafka MAX_POLL_RECORDS와 동일)만큼 묶어서 min(재고, 배치크기)를 원자적으로
 * 승인하는 Lua 호출을 배치당 1번만 한다 — Kafka가 poll당 decreaseStockBatch를 1번만 호출하는 것과 동일 구조.
 */
@SpringBootTest
public class RedisBatchConcurrencyTest {

	private static final long COUPON_ID = 2L;
	private static final int INITIAL_STOCK = 10_000;
	private static final int REQUEST_COUNT = 100_000;
	private static final int BATCH_SIZE = 500;

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
	public void 배치로_묶어_원자적_차감하면_카프카와_동일한_구조로_처리된다() throws InterruptedException {
		int batchCount = REQUEST_COUNT / BATCH_SIZE;

		ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor();
		CountDownLatch done = new CountDownLatch(batchCount);
		AtomicInteger successCount = new AtomicInteger(0);

		AtomicLong peakUsedBytes = new AtomicLong();
		Runtime runtime = Runtime.getRuntime();
		ScheduledExecutorService memorySampler = Executors.newSingleThreadScheduledExecutor();
		memorySampler.scheduleAtFixedRate(() -> {
			long used = runtime.totalMemory() - runtime.freeMemory();
			peakUsedBytes.accumulateAndGet(used, Math::max);
		}, 0, 100, TimeUnit.MILLISECONDS);

		long start = System.currentTimeMillis();

		// when: Kafka 컨슈머처럼 BATCH_SIZE건씩 묶어서 배치당 원자적 호출 1번만 한다.
		for (int batch = 0; batch < batchCount; batch++) {
			executorService.execute(() -> {
				try {
					int granted = redisCouponStockTestService.decreaseStockBatch(COUPON_ID, BATCH_SIZE);
					successCount.addAndGet(granted);
				}
				finally {
					done.countDown();
				}
			});
		}
		done.await();
		executorService.shutdown();
		long elapsed = System.currentTimeMillis() - start;
		memorySampler.shutdownNow();

		double elapsedSeconds = elapsed / 1000.0;
		long throughput = Math.round(REQUEST_COUNT / elapsedSeconds);

		// then
		assertThat(redisCouponStockTestService.getStock(COUPON_ID)).isZero();
		assertThat(successCount.get()).isEqualTo(INITIAL_STOCK);

		System.out.println("========== Redis(배치) 동시성 테스트 결과 ==========");
		System.out.printf("배치 크기       : %,d%n", BATCH_SIZE);
		System.out.printf("초기 재고       : %,d%n", INITIAL_STOCK);
		System.out.printf("요청 사용자     : %,d%n", REQUEST_COUNT);
		System.out.printf("성공 발급       : %,d%n", successCount.get());
		System.out.printf("실패 요청       : %,d%n", REQUEST_COUNT - successCount.get());
		System.out.printf("최종 Redis 재고 : %,d%n", redisCouponStockTestService.getStock(COUPON_ID));
		System.out.printf("총 소요 시간    : %.2f sec%n", elapsedSeconds);
		System.out.printf("처리량          : %,d req/s%n", throughput);
		System.out.printf("피크 힙 사용량  : %.1f MB%n", peakUsedBytes.get() / 1024.0 / 1024.0);
		System.out.println("===================================================");
	}
}
