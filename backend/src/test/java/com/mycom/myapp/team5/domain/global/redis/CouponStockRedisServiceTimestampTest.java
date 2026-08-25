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
		couponStockRedisService.initStock(COUPON_ID, 1);

		long controllerEnteredAtMs = System.currentTimeMillis();
		long gateEnteredAtMs = controllerEnteredAtMs + 5;

		couponStockRedisService.issue(COUPON_ID, 1L, gateEnteredAtMs, controllerEnteredAtMs);

		List<CouponStockRedisService.FairnessLogEntry> entries = couponStockRedisService.fairnessLog(COUPON_ID, 0, 10);
		CouponStockRedisService.FairnessLogEntry entry = entries.get(0);

		assertThat(entry.controllerEnteredAtMs()).isEqualTo(controllerEnteredAtMs);
		assertThat(entry.gateEnteredAtMs()).isEqualTo(gateEnteredAtMs);

		// 엄격한 순서 비교 대신, "서버 간 시계 오차(수십 ms)를 감안해도 크게 안 어긋나는지"만 확인
		long clockSkewTolerance = 20;
		assertThat(entry.redisTimeMs()).isGreaterThan(entry.gateEnteredAtMs() - clockSkewTolerance);
	}

	@Test
	void redisTimeMs는_Lua_스크립트_실행_시점의_실제_Redis_서버_시각과_비슷하다() {
		couponStockRedisService.initStock(COUPON_ID, 1);
		long beforeCallMs = System.currentTimeMillis();

		couponStockRedisService.issue(COUPON_ID, 1L, beforeCallMs, beforeCallMs);
		long afterCallMs = System.currentTimeMillis();

		List<CouponStockRedisService.FairnessLogEntry> entries = couponStockRedisService.fairnessLog(COUPON_ID, 0, 10);
		long redisTimeMs = entries.get(0).redisTimeMs();

		// 시계 오차를 감안해 여유(20ms)를 두고 "대략 그 시점"인지만 확인
		long tolerance = 20;
		assertThat(redisTimeMs).isBetween(beforeCallMs - tolerance, afterCallMs + tolerance);
	}

	@Test
	void fairnessLog의_afterRank_커서가_정확히_다음_항목부터_가져온다() {
		// given - 3건 연속 발급
		couponStockRedisService.initStock(COUPON_ID, 3);
		for (long userId = 1; userId <= 3; userId++) {
			long now = System.currentTimeMillis();
			couponStockRedisService.issue(COUPON_ID, userId, now, now);
		}

		// when - 처음엔 전체 조회
		List<CouponStockRedisService.FairnessLogEntry> all = couponStockRedisService.fairnessLog(COUPON_ID, 0, 10);
		assertThat(all).hasSize(3);

		long firstRank = all.get(0).rank();

		// then - firstRank 이후로만 조회하면, 그 항목은 다시 안 나와야 함(커서 방식 검증)
		List<CouponStockRedisService.FairnessLogEntry> afterFirst = couponStockRedisService.fairnessLog(COUPON_ID, firstRank, 10);

		assertThat(afterFirst).hasSize(2);
		assertThat(afterFirst).noneMatch(e -> e.rank() == firstRank);
	}
}
