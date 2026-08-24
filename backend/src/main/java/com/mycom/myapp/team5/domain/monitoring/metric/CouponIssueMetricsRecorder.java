package com.mycom.myapp.team5.domain.monitoring.metric;

import com.mycom.myapp.team5.domain.coupon.exception.CouponErrorCode;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * 쿠폰 발급 시도(CouponIssueStreamProducer#requestIssue) 결과를 쿠폰별로 집계한다.
 * CouponIssueMetricsAspect가 유일한 기록 지점이다.
 *
 * 쿠폰별로 나누는 이유 - "쿠폰 발급 현황" 카드는 대시보드에서 지금 선택된 쿠폰 하나의 발급 활동을
 * 보여주는 자리다. 세션 하나에서 쿠폰을 여러 번 바꿔가며 테스트하면(쿠폰 선택 UI가 생긴 이후 흔한
 * 시나리오) 프로세스 전체 누적치로는 "방금 고른 쿠폰"과 무관한 숫자가 섞여 보인다.
 * 반면 DbInsertMetricsRecorder(배치 insert)와 HttpMetricsRecorder(RPS/응답시간)는 쿠폰별로
 * 나누지 않는다 - 배치 insert 버퍼가 여러 쿠폰 스트림을 한데 모아 처리하고, HTTP 지표는 서버
 * 전체 트래픽을 재는 것이라 애초에 쿠폰 단위 개념이 아니다.
 */
@Component
public class CouponIssueMetricsRecorder {

    private static final long RATE_WINDOW_MILLIS = 1_000L;

    private static final class Counters {
        final LongAdder total = new LongAdder();
        final LongAdder success = new LongAdder();
        final LongAdder soldOut = new LongAdder();
        final LongAdder duplicate = new LongAdder();
        final LongAdder otherFail = new LongAdder();
        final ConcurrentLinkedDeque<Long> successTimestamps = new ConcurrentLinkedDeque<>();
    }

    private final ConcurrentMap<Long, Counters> byCoupon = new ConcurrentHashMap<>();

    private Counters counters(long couponId) {
        return byCoupon.computeIfAbsent(couponId, id -> new Counters());
    }

    public void recordSuccess(long couponId) {
        Counters c = counters(couponId);
        c.total.increment();
        c.success.increment();
        long now = System.currentTimeMillis();
        c.successTimestamps.addLast(now);
        trim(c, now);
    }

    public void recordFailure(long couponId, CouponErrorCode errorCode) {
        Counters c = counters(couponId);
        c.total.increment();
        switch (errorCode) {
            case COUPON_SOLD_OUT -> c.soldOut.increment();
            case COUPON_ISSUE_DUPLICATE -> c.duplicate.increment();
            default -> c.otherFail.increment();
        }
    }

    private void trim(Counters c, long now) {
        long cutoff = now - RATE_WINDOW_MILLIS;
        Long head;
        while ((head = c.successTimestamps.peekFirst()) != null && head < cutoff) {
            c.successTimestamps.pollFirst();
        }
    }

    public void reset() {
        byCoupon.clear();
    }

    public Snapshot snapshot(long couponId) {
        Counters c = byCoupon.get(couponId);
        if (c == null) {
            return new Snapshot(0, 0, 0, 0, 0, 0);
        }
        trim(c, System.currentTimeMillis());
        return new Snapshot(
                c.total.sum(),
                c.success.sum(),
                c.total.sum() - c.success.sum(),
                c.successTimestamps.size(),
                c.soldOut.sum(),
                c.duplicate.sum()
        );
    }

    public record Snapshot(
            long total,
            long success,
            long fail,
            double perSecond,
            long soldOutFail,
            long duplicateFail
    ) {}
}
