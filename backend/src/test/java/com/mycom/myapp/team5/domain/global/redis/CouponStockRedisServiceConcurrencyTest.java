package com.mycom.myapp.team5.domain.global.redis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.mycom.myapp.team5.domain.coupon.dto.CouponFairnessReport;
import com.mycom.myapp.team5.domain.coupon.exception.CouponException;
import com.mycom.myapp.team5.global.redis.CouponStockKeys;
import com.mycom.myapp.team5.global.redis.CouponStockRedisService;

@SpringBootTest
public class CouponStockRedisServiceConcurrencyTest {
	private static final long COUPON_ID = 997L;
	private static final int INITIAL_STOCK = 10_000;
	private static final int REQUEST_COUNT = 20_000;

	@Autowired
	private CouponStockRedisService couponStockRedisService;

	@Autowired
	private StringRedisTemplate stringRedisTemplate;

	@AfterEach
	void tearDown() {
		stringRedisTemplate.delete(CouponStockKeys.stockKey(COUPON_ID));
		stringRedisTemplate.delete(CouponStockKeys.issuedSetKey(COUPON_ID));
		stringRedisTemplate.delete(CouponStockKeys.fairnessSeqKey(COUPON_ID));
		stringRedisTemplate.delete(CouponStockKeys.fairnessLogKey(COUPON_ID));
	}

	@Test
	void 재고_10000개에_20000명이_동시_요청해도_정확히_10000명만_성공한다() throws InterruptedException {
		// given
		couponStockRedisService.initStock(COUPON_ID, INITIAL_STOCK);
		couponStockRedisService.resetFairnessLog(COUPON_ID);

		AtomicInteger successCount = new AtomicInteger(0);
		AtomicInteger soldOutCount = new AtomicInteger(0);
		CountDownLatch latch = new CountDownLatch(REQUEST_COUNT);

		// when
		long startNanos = System.nanoTime();

		try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
			for (int i = 0; i < REQUEST_COUNT; i++) {
				long userId = i;
				executor.submit(() -> {
					try {
						couponStockRedisService.issue(COUPON_ID, userId, System.currentTimeMillis(), System.currentTimeMillis());
						successCount.incrementAndGet();
					}
					catch (CouponException e) {
						soldOutCount.incrementAndGet();
					}
					finally {
						latch.countDown();
					}
				});
			}
			latch.await();
		}

		long elapsedNanos = System.nanoTime() - startNanos;
		printMetrics(elapsedNanos, successCount.get(), soldOutCount.get());

		// then - 기존 검증
		assertThat(successCount.get()).isEqualTo(INITIAL_STOCK);
		assertThat(soldOutCount.get()).isEqualTo(REQUEST_COUNT - INITIAL_STOCK);

		String remaining = stringRedisTemplate.opsForValue().get(CouponStockKeys.stockKey(COUPON_ID));
		assertThat(remaining).isEqualTo("0");

		Long issuedSetSize = stringRedisTemplate.opsForSet().size(CouponStockKeys.issuedSetKey(COUPON_ID));
		assertThat(issuedSetSize).isEqualTo((long) INITIAL_STOCK);

		// then - 추가: 선착순 공정성 검증
		CouponFairnessReport report = couponStockRedisService.analyzeFairness(COUPON_ID);
		printFairnessReport(report);

		assertThat(report.totalAttempts()).isEqualTo(REQUEST_COUNT);
	}

	@Test
	void 같은_회원이_동시에_여러번_요청해도_1개만_성공한다() throws InterruptedException {
		// given
		couponStockRedisService.initStock(COUPON_ID, 100);
		couponStockRedisService.resetFairnessLog(COUPON_ID);
		long sameUserId = 12345L;
		int attemptCount = 50;

		AtomicInteger successCount = new AtomicInteger(0);
		CountDownLatch latch = new CountDownLatch(attemptCount);

		// when
		long startNanos = System.nanoTime();

		try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
			for (int i = 0; i < attemptCount; i++) {
				executor.submit(() -> {
					try {
						couponStockRedisService.issue(COUPON_ID, sameUserId, System.currentTimeMillis(), System.currentTimeMillis());
						successCount.incrementAndGet();
					}
					catch (CouponException ignored) {
						// 중복 실패는 정상
					}
					finally {
						latch.countDown();
					}
				});
			}
			latch.await();
		}

		long elapsedNanos = System.nanoTime() - startNanos;
		printMetrics(elapsedNanos, successCount.get(), attemptCount - successCount.get());

		// then
		assertThat(successCount.get()).isEqualTo(1);
	}

	private void printMetrics(long elapsedNanos, int successCount, int failCount) {
		double elapsedMs = elapsedNanos / 1_000_000.0;
		int total = successCount + failCount;
		double tps = total / (elapsedNanos / 1_000_000_000.0);

		String summary = """

				===== 동시성 테스트 결과 =====
				총 요청 수     : %d
				성공          : %d
				실패          : %d
				총 소요 시간    : %.2f ms
				처리량(TPS)    : %.1f req/s
				==============================
				""".formatted(total, successCount, failCount, elapsedMs, tps);
		System.out.println(summary);
	}

	private void printFairnessReport(CouponFairnessReport report) {
		String summary = """

				===== 선착순 공정성 리포트 =====
				전체 시도 건수  : %d
				순서 역전 건수  : %d
				공정성 판정    : %s
				역전 비율      : %.4f%%
				==============================
				""".formatted(report.totalAttempts(), report.inversionCount(), report.isFair() ? "공정함 (역전 없음)" : "역전 발견", report.inversionRate() * 100);
		System.out.println(summary);
	}
}