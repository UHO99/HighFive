package com.mycom.myapp.team5.global.redis;

import java.util.List;

import org.springframework.data.domain.Range;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.connection.stream.PendingMessages;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 재시도해도 절대 성공할 수 없는 PEL 엔트리(예: FK가 영구히 유효하지 않은 테스트 데이터)를
 * 관리자가 명시적으로 포기하고 비우는 최후 수단.
 *
 * CouponIssueStreamConsumer의 실패 경로는 실패를 절대 조용히 삼키지 않으려고 ACK을 보류하는데
 * ({@link CouponIssueStreamConsumer#insertIndividually}), 이 코드베이스엔 그 보류된 메시지를
 * 다시 시도하는 로직이 없다. 그래서 원인이 영구적이면(예: FK 위반) 사람이 개입하기 전까지
 * PEL에 영원히 남는다 - 이 클래스는 그 개입을 위한 것이다. 자동으로는 절대 호출되지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CouponStreamPendingDrainer {

    private static final int BATCH_SIZE = 5000;

    private final StringRedisTemplate stringRedisTemplate;

    /**
     * couponId 스트림의 PEL 전체를 강제로 ACK한다. DB에는 아무것도 넣지 않는다 - 이미 실패가
     * 확정된 메시지를 큐에서만 지우는 것이므로, 호출한 쪽이 "이 발급 건들은 DB 이력에 영영
     * 안 남는다"는 걸 감수한 것으로 간주한다.
     *
     * @return 실제로 ACK한 건수
     */
    public int drainAll(long couponId) {
        String streamKey = CouponStreamKeys.streamKey(couponId);
        int total = 0;

        while (true) {
            PendingMessages pending;
            try {
                pending = stringRedisTemplate.opsForStream()
                        .pending(streamKey, CouponStreamKeys.CONSUMER_GROUP, Range.unbounded(), BATCH_SIZE);
            } catch (RedisSystemException e) {
                break; // 스트림/그룹이 없음 - 비울 것도 없음
            }
            if (pending == null || pending.isEmpty()) {
                break;
            }

            List<RecordId> ids = pending.stream().map(message -> message.getId()).toList();
            stringRedisTemplate.opsForStream()
                    .acknowledge(streamKey, CouponStreamKeys.CONSUMER_GROUP, ids.toArray(new RecordId[0]));
            total += ids.size();

            if (ids.size() < BATCH_SIZE) {
                break;
            }
        }

        if (total > 0) {
            log.warn("PEL 강제 드레인 실행 - couponId={}, ackedCount={} (DB coupon_issue에는 반영되지 않은 채 포기됨)",
                    couponId, total);
        }
        return total;
    }
}
