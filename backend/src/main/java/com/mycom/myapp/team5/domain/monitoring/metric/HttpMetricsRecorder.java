package com.mycom.myapp.team5.domain.monitoring.metric;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;

/**
 * 요청 1건마다 소요 시간/성공 여부를 최근 WINDOW_MILLIS 구간만 들고 있다가 스냅샷 시점에 집계한다.
 * Micrometer 없이, 대시보드 폴링 주기(수 초)에 맞춰 계산 비용을 감당할 수 있는 선에서 최소로 구현한다.
*/
@Component
public class HttpMetricsRecorder {

    private static final long WINDOW_MILLIS = 60_000L;

    private record Sample(long timestampMillis, long durationNanos, boolean success) {}

    private final ConcurrentLinkedDeque<Sample> samples = new ConcurrentLinkedDeque<>();

    public void record(long durationNanos, boolean success) {
        samples.addLast(new Sample(System.currentTimeMillis(), durationNanos, success));
        trim();
    }

    public void reset() {
        samples.clear();
    }

    private void trim() {
        long cutoff = System.currentTimeMillis() - WINDOW_MILLIS;
        Sample head;
        while ((head = samples.peekFirst()) != null && head.timestampMillis() < cutoff) {
            samples.pollFirst();
        }
    }

    public Snapshot snapshot() {
        trim();
        List<Sample> copy = new ArrayList<>(samples);
        if (copy.isEmpty()) {
            return new Snapshot(0, 0, 0, 0, 0, 0, 0);
        }

        long now = System.currentTimeMillis();
        long rps = copy.stream().filter(s -> s.timestampMillis() >= now - 1000).count();

        double[] successDurations = copy.stream()
                .filter(Sample::success)
                .mapToDouble(s -> s.durationNanos() / 1_000_000.0)
                .toArray();
        double[] failDurations = copy.stream()
                .filter(s -> !s.success())
                .mapToDouble(s -> s.durationNanos() / 1_000_000.0)
                .toArray();
        double[] allDurations = copy.stream()
                .mapToDouble(s -> s.durationNanos() / 1_000_000.0)
                .sorted()
                .toArray();

        double errorRate = failDurations.length * 100.0 / copy.size();

        return new Snapshot(
                rps,
                average(allDurations),
                percentile(allDurations, 95),
                percentile(allDurations, 99),
                errorRate,
                average(successDurations),
                average(failDurations)
        );
    }

    private double average(double[] values) {
        if (values.length == 0) {
            return 0;
        }
        double sum = 0;
        for (double v : values) {
            sum += v;
        }
        return sum / values.length;
    }

    private double percentile(double[] sortedValues, double percentile) {
        if (sortedValues.length == 0) {
            return 0;
        }
        int index = (int) Math.ceil(percentile / 100.0 * sortedValues.length) - 1;
        index = Math.max(0, Math.min(index, sortedValues.length - 1));
        return sortedValues[index];
    }

    public record Snapshot(
            long rps,
            double avgMs,
            double p95Ms,
            double p99Ms,
            double errorRatePercent,
            double successAvgMs,
            double failAvgMs
    ) {}
}
