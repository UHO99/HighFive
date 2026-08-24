package com.mycom.myapp.team5.global.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class CouponIssueStreamProducer {

    private final StringRedisTemplate stringRedisTemplate;
    private final CouponStockRedisService couponStockRedisService;

    public void requestIssue(long couponId, long userId) {
        couponStockRedisService.issue(couponId, userId);
        stringRedisTemplate.opsForStream().add(
                CouponStreamKeys.streamKey(couponId),
                Map.of("couponId", String.valueOf(couponId), "userId", String.valueOf(userId)));
    }

}
