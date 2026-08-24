package com.mycom.myapp.team5.global.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.connection.stream.PendingMessage;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.PendingMessagesSummary;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 쿠폰별 스트림(coupon:issue:request:stream:{couponId})의 컨슈머 그룹 미처리(PEL) 건수를 확인한다.
 * S012(정합성 동기화)와 S013(정합성 검증 배치)이 같은 기준으로 "드레인 됐는지"를 판단하기 위해 공유한다.
 */
@Component
@RequiredArgsConstructor
public class CouponStreamPendingChecker {

    private final StringRedisTemplate stringRedisTemplate;

    public long pendingCount(long couponId) {
        try {
            PendingMessagesSummary summary = stringRedisTemplate.opsForStream()
                    .pending(CouponStreamKeys.streamKey(couponId), CouponStreamKeys.CONSUMER_GROUP);
            return summary == null ? 0 : summary.getTotalPendingMessages();
        } catch (RedisSystemException e) {
            // 발급 요청이 한 건도 없었던 쿠폰은 스트림/그룹 자체가 안 만들어져 있을 수 있다 - 이 경우는 드레인된 것으로 취급한다.
            return 0;
        }
    }

    public boolean isDrained(long couponId) {
        return pendingCount(couponId) == 0;
    }

    // 모니터링 대시보드용 - PEL에 남은 메시지들 중 마지막 전달 이후 가장 오래 기다린 건의 지연(ms).
    // pendingCount()와 같은 예외 처리 방침(스트림/그룹이 없으면 지연 없음으로 취급)을 따른다.
    public long maxLagMillis(long couponId) {
        try {
            PendingMessages messages = stringRedisTemplate.opsForStream()
                    .pending(CouponStreamKeys.streamKey(couponId), CouponStreamKeys.CONSUMER_GROUP, Range.unbounded(), 10_000L);
            if (messages == null || messages.isEmpty()) {
                return 0;
            }
            long max = 0;
            for (PendingMessage message : messages) {
                max = Math.max(max, message.getElapsedTimeSinceLastDelivery().toMillis());
            }
            return max;
        } catch (RedisSystemException e) {
            return 0;
        }
    }

}
