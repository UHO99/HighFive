package com.mycom.myapp.team5.global.redis;

import java.util.Map;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class CouponIssueStreamProducer {

	private final StringRedisTemplate stringRedisTemplate;
	private final CouponStockRedisService couponStockRedisService;

	public void requestIssue(long couponId, long userId, long controllerEnteredAtMs) {
		long gateEnteredAtMs = System.currentTimeMillis();

		couponStockRedisService.issue(couponId, userId, gateEnteredAtMs, controllerEnteredAtMs);

		stringRedisTemplate.opsForStream().add(CouponStreamKeys.streamKey(couponId), Map.of("couponId", String.valueOf(couponId), "userId", String.valueOf(userId)));
	}
}
