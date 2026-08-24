package com.mycom.myapp.team5.global.redis;

import com.mycom.myapp.team5.domain.coupon.exception.CouponErrorCode;
import com.mycom.myapp.team5.domain.coupon.exception.CouponException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Redis Lua 스크립트로 "1인 1매 확인"과 "재고 원자적 차감"을 하나의 원자적 연산으로 묶는다.
 * 두 검사를 별도 호출로 나누면 그 사이에 실패 시 수동으로 되돌리는 보상 로직이 필요한데,
 * Lua 스크립트는 Redis 안에서 끊기지 않고 통째로 실행되므로 그 문제가 아예 발생하지 않는다.
 */
@Component
@RequiredArgsConstructor
public class CouponStockRedisService {

    private final StringRedisTemplate redisTemplate;

    // 반환값: 1 = 발급 성공, 0 = 품절, -1 = 이미 발급됨(중복), -2 = 재고 미적재(쿠폰 오픈 안 됨)
    private static final String ISSUE_SCRIPT = "if redis.call('sismember', KEYS[2], ARGV[1]) == 1 then " +
            "  return -1 " +
            "end " +
            "local stock = tonumber(redis.call('get', KEYS[1])) " +
            "if not stock then " +
            "  return -2 " +
            "end " +
            "if stock <= 0 then " +
            "  return 0 " +
            "end " +
            "redis.call('decr', KEYS[1]) " +
            "redis.call('sadd', KEYS[2], ARGV[1]) " +
            "return 1";

    private static final DefaultRedisScript<Long> ISSUE = new DefaultRedisScript<>(ISSUE_SCRIPT, Long.class);

    public void initStock(long couponId, int quantity) {
        redisTemplate.opsForValue().set(CouponStockKeys.stockKey(couponId), String.valueOf(quantity));
    }

    /**
     * Redis 잔여 재고. 키가 없으면 null (미오픈/미적재).
     */
    public Integer getStock(long couponId) {
        String value = redisTemplate.opsForValue().get(CouponStockKeys.stockKey(couponId));
        if (value == null) {
            return null;
        }
        return Integer.valueOf(value);
    }

    /**
     * 발급을 시도한다. 성공하면 조용히 반환하고(Redis 안에서 재고 차감+1인1매 기록이 끝난 상태),
     * 실패 사유별로 다른 CouponException을 던진다.
     */
    public void issue(long couponId, long userId) {
        List<String> keys = List.of(
                CouponStockKeys.stockKey(couponId),
                CouponStockKeys.issuedSetKey(couponId));

        Long result = redisTemplate.execute(ISSUE, keys, String.valueOf(userId));
        long code = result == null ? -2 : result;

        if (code == 1) {
            return;
        }
        if (code == -1) {
            throw new CouponException(CouponErrorCode.COUPON_ISSUE_DUPLICATE);
        }
        if (code == -2) {
            throw new CouponException(CouponErrorCode.COUPON_INVENTORY_NOT_STOCKED);
        }
        // code == 0
        throw new CouponException(CouponErrorCode.COUPON_SOLD_OUT);
    }
}