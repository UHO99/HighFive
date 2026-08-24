package com.mycom.myapp.team5.domain.monitoring.metric;

import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;

/**
 * CouponIssueStreamConsumer의 배치 insert 결과를 집계한다.
 * flushLoop()가 같은 인스턴스 안에서 insertBatch/insertIndividually를 직접 호출(self-invocation)하기
 * 때문에 AOP로는 가로챌 수 없어서, 컨슈머 쪽에서 성공한 insert마다 recordBatch()를 직접 호출해준다.
 */
@Component
public class DbInsertMetricsRecorder {

    private static final long THROUGHPUT_WINDOW_MILLIS = 10_000L;

    private final LongAdder totalBatches = new LongAdder();
    private final LongAdder totalRows = new LongAdder();
    private final AtomicInteger maxBatchSize = new AtomicInteger(0);

    private record TimestampedCount(long timestampMillis, int count) {}

    private final ConcurrentLinkedDeque<TimestampedCount> recent = new ConcurrentLinkedDeque<>();

    public void recordBatch(int size) {
        if (size <= 0) {
            return;
        }
        totalBatches.increment();
        totalRows.add(size);
        maxBatchSize.updateAndGet(current -> Math.max(current, size));

        long now = System.currentTimeMillis();
        recent.addLast(new TimestampedCount(now, size));
        trim(now);
    }

    private void trim(long now) {
        long cutoff = now - THROUGHPUT_WINDOW_MILLIS;
        TimestampedCount head;
        while ((head = recent.peekFirst()) != null && head.timestampMillis() < cutoff) {
            recent.pollFirst();
        }
    }

    public void reset() {
        totalBatches.reset();
        totalRows.reset();
        maxBatchSize.set(0);
        recent.clear();
    }

    public Snapshot snapshot() {
        long now = System.currentTimeMillis();
        trim(now);
        long windowRows = recent.stream().mapToLong(TimestampedCount::count).sum();
        double throughputPerSecond = windowRows / (THROUGHPUT_WINDOW_MILLIS / 1000.0);

        long batches = totalBatches.sum();
        double avgBatchSize = batches == 0 ? 0 : (double) totalRows.sum() / batches;

        return new Snapshot(throughputPerSecond, avgBatchSize, maxBatchSize.get());
    }

    public record Snapshot(double throughputPerSecond, double avgBatchSize, int maxBatchSize) {}
}
