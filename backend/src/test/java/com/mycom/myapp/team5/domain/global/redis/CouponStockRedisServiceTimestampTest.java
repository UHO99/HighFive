package com.mycom.myapp.team5.domain.global.redis;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.mycom.myapp.team5.global.redis.CouponStockKeys;
import com.mycom.myapp.team5.global.redis.CouponStockRedisService;

@SpringBootTest
public class CouponStockRedisServiceTimestampTest {

	private static final long COUPON_ID = 996L;

	/** 서버 간 프로세스 통신 지연을 감안한 허용 오차(ms). 로컬 실측 시 1~4ms 수준의 차이가 정상적으로 발생함을 확인. */
	private static final long CLOCK_SKEW_TOLERANCE_MS = 20;

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
	void 기록된_세_시각이_대략_순서대로_증가한다() {
		// given
		couponStockRedisService.initStock(COUPON_ID, 1);

		long controllerEnteredAtMs = System.currentTimeMillis();
		long gateEnteredAtMs = controllerEnteredAtMs + 5;

		// when
		couponStockRedisService.issue(COUPON_ID, 1L, gateEnteredAtMs, controllerEnteredAtMs);

		// then
		List<CouponStockRedisService.FairnessLogEntry> entries = couponStockRedisService.fairnessLog(COUPON_ID, 0, 10);

		assertThat(entries).hasSize(1);
		CouponStockRedisService.FairnessLogEntry entry = entries.get(0);

		assertThat(entry.controllerEnteredAtMs()).isEqualTo(controllerEnteredAtMs);
		assertThat(entry.gateEnteredAtMs()).isEqualTo(gateEnteredAtMs);

		// redisTimeMicros는 마이크로초 단위라 ms로 환산해서 비교
		long redisTimeMs = entry.redisTimeMicros() / 1000;
		assertThat(redisTimeMs).isGreaterThan(entry.gateEnteredAtMs() - CLOCK_SKEW_TOLERANCE_MS);

		assertThat(entry.outcome()).isEqualTo("SUCCESS");
	}

	@Test
	void redisTimeMicros는_Lua_스크립트_실행_시점의_실제_Redis_서버_시각이다() {
		// given
		couponStockRedisService.initStock(COUPON_ID, 1);
		long beforeCallMs = System.currentTimeMillis();

		// when
		couponStockRedisService.issue(COUPON_ID, 1L, beforeCallMs, beforeCallMs);
		long afterCallMs = System.currentTimeMillis();

		// then
		List<CouponStockRedisService.FairnessLogEntry> entries = couponStockRedisService.fairnessLog(COUPON_ID, 0, 10);
		long redisTimeMs = entries.get(0).redisTimeMicros() / 1000; // µs → ms 환산

		assertThat(redisTimeMs).isBetween(beforeCallMs - CLOCK_SKEW_TOLERANCE_MS, afterCallMs + CLOCK_SKEW_TOLERANCE_MS);
	}

	@Test
	void fairnessLog의_afterRank_커서가_정확히_다음_항목부터_가져온다() {
		// given
		couponStockRedisService.initStock(COUPON_ID, 3);
		for (long userId = 1; userId <= 3; userId++) {
			long now = System.currentTimeMillis();
			couponStockRedisService.issue(COUPON_ID, userId, now, now);
		}

		// when
		List<CouponStockRedisService.FairnessLogEntry> all = couponStockRedisService.fairnessLog(COUPON_ID, 0, 10);
		assertThat(all).hasSize(3);

		long firstRank = all.get(0).rank();

		// then
		List<CouponStockRedisService.FairnessLogEntry> afterFirst = couponStockRedisService.fairnessLog(COUPON_ID, firstRank, 10);

		assertThat(afterFirst).hasSize(2);
		assertThat(afterFirst).noneMatch(e -> e.rank() == firstRank);
	}

	@Test
	void 매우_짧은_간격으로_연속_발급해도_redisTimeMicros는_서로_다르다() {
		couponStockRedisService.initStock(COUPON_ID, 5);
		long now = System.currentTimeMillis();

		for (long userId = 1; userId <= 5; userId++) {
			couponStockRedisService.issue(COUPON_ID, userId, now, now);
		}

		List<CouponStockRedisService.FairnessLogEntry> entries = couponStockRedisService.fairnessLog(COUPON_ID, 0, 10);

		long distinctMicroTimes = entries.stream().map(CouponStockRedisService.FairnessLogEntry::redisTimeMicros).distinct().count();

		// 마이크로초까지 내려가면, 밀리초 단위에서는 뭉쳐 보이던 것도 서로 구분될 가능성이 높다
		assertThat(distinctMicroTimes).isGreaterThan(1);
	}
}
