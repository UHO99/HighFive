package com.mycom.myapp.team5.domain.coupon.service.sync;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.mycom.myapp.team5.domain.coupon.dto.CouponSyncLogEntry;
import com.mycom.myapp.team5.domain.coupon.dto.CouponVerifyLogEntry;
import com.mycom.myapp.team5.domain.coupon.dto.MismatchEvent;

@Component
public class CouponConsistencyStatusHolder {

    public static final long SYNC_INTERVAL_MS = CouponStockSyncService.INTERVAL_MS;
    public static final long VERIFY_INTERVAL_MS = CouponStockValidationService.INTERVAL_MS;

    /** mismatchHistory에 보관하는 "이미 해소된" 건의 최대 개수 - 진행 중인(미해소) 건은 개수와 무관하게 전부 유지한다. */
    private static final int MAX_RESOLVED_HISTORY = 30;

    /** 동기화/검증 완료 로그로 보관하는 최근 건수 - 대시보드 카드 한 켠에 최근 내역만 보여주면 되므로 짧게 잡는다. */
    private static final int MAX_LOG_ENTRIES = 20;

    public record SyncSnapshot(Instant lastRunAt, int targetCount, int syncedCount) {
        static SyncSnapshot initial() {
            return new SyncSnapshot(null, 0, 0);
        }
    }

    public record VerifySnapshot(Instant lastRunAt, int targetCount, int confirmedCount, int mismatchCount) {
        static VerifySnapshot initial() {
            return new VerifySnapshot(null, 0, 0, 0);
        }
    }

    private volatile SyncSnapshot sync = SyncSnapshot.initial();
    private volatile VerifySnapshot verify = VerifySnapshot.initial();

    private final Object historyLock = new Object();
    private final Map<Long, MismatchEvent> mismatchHistory = new LinkedHashMap<>();

    private final Object logLock = new Object();
    private final Deque<CouponSyncLogEntry> syncLog = new ArrayDeque<>(MAX_LOG_ENTRIES);
    private final Deque<CouponVerifyLogEntry> verifyLog = new ArrayDeque<>(MAX_LOG_ENTRIES);

    public void updateSync(Instant lastRunAt, int targetCount, int syncedCount) {
        this.sync = new SyncSnapshot(lastRunAt, targetCount, syncedCount);
    }

    public void updateVerify(Instant lastRunAt, int targetCount, int confirmedCount, int mismatchCount) {
        this.verify = new VerifySnapshot(lastRunAt, targetCount, confirmedCount, mismatchCount);
    }

    /** S012가 실제로 issuedQuantity를 써넣은 순간(=드레인 완료 후 처음 동기화된 순간)마다 한 줄 남긴다. */
    public void recordSyncCompletion(long couponId, Instant syncedAt, int issuedQuantity) {
        synchronized (logLock) {
            syncLog.addFirst(new CouponSyncLogEntry(couponId, syncedAt, issuedQuantity));
            while (syncLog.size() > MAX_LOG_ENTRIES) {
                syncLog.removeLast();
            }
        }
    }

    /** S013이 "드레인 완료 + 기록값=실측값"을 처음 확정한 순간마다 한 줄 남긴다. */
    public void recordVerifyConfirmation(long couponId, Instant confirmedAt) {
        synchronized (logLock) {
            verifyLog.addFirst(new CouponVerifyLogEntry(couponId, confirmedAt));
            while (verifyLog.size() > MAX_LOG_ENTRIES) {
                verifyLog.removeLast();
            }
        }
    }

    public List<CouponSyncLogEntry> syncLog() {
        synchronized (logLock) {
            return List.copyOf(syncLog);
        }
    }

    public List<CouponVerifyLogEntry> verifyLog() {
        synchronized (logLock) {
            return List.copyOf(verifyLog);
        }
    }

    public void recordMismatchCycle(Instant now, List<CouponMismatchReport> currentMismatches) {
        synchronized (historyLock) {
            Map<Long, CouponMismatchReport> byCoupon = new LinkedHashMap<>();
            for (CouponMismatchReport report : currentMismatches) {
                byCoupon.put(report.couponId(), report);
            }

            // 이전에 미해소였는데 이번엔 안 보이면 해소 처리
            for (Map.Entry<Long, MismatchEvent> entry : mismatchHistory.entrySet()) {
                MismatchEvent event = entry.getValue();
                if (!event.isResolved() && !byCoupon.containsKey(entry.getKey())) {
                    entry.setValue(event.resolved(now));
                }
            }

            // 이번 사이클 불일치를 반영 (신규 등록 또는 진행 중 스냅샷 갱신)
            for (CouponMismatchReport report : currentMismatches) {
                MismatchEvent existing = mismatchHistory.get(report.couponId());
                if (existing == null || existing.isResolved()) {
                    mismatchHistory.put(report.couponId(), new MismatchEvent(
                            report.couponId(), now, null,
                            report.recordedIssuedQuantity(), report.actualIssuedCount(), report.pendingCount()
                    ));
                } else {
                    mismatchHistory.put(report.couponId(), existing.withLatestSnapshot(
                            report.recordedIssuedQuantity(), report.actualIssuedCount(), report.pendingCount()
                    ));
                }
            }

            // 해소된 건 중 오래된 것부터 정리 - 진행 중인 건은 몇 개든 항상 남긴다.
            while (mismatchHistory.size() > MAX_RESOLVED_HISTORY) {
                Long oldestResolvedKey = mismatchHistory.entrySet().stream()
                        .filter(e -> e.getValue().isResolved())
                        .map(Map.Entry::getKey)
                        .findFirst()
                        .orElse(null);
                if (oldestResolvedKey == null) {
                    break; // 전부 진행 중이면 더 못 지운다.
                }
                mismatchHistory.remove(oldestResolvedKey);
            }
        }
    }

    public SyncSnapshot sync() {
        return sync;
    }

    public VerifySnapshot verify() {
        return verify;
    }

    /** 최근 갱신 순(미해소가 위로 오도록: 진행 중 -> 최근 해소 순)으로 정렬해서 돌려준다. */
    public List<MismatchEvent> mismatchHistory() {
        synchronized (historyLock) {
            List<MismatchEvent> list = new ArrayList<>(mismatchHistory.values());
            list.sort((a, b) -> {
                if (a.isResolved() != b.isResolved()) {
                    return a.isResolved() ? 1 : -1; // 미해소 먼저
                }
                Instant aTime = a.isResolved() ? a.resolvedAt() : a.detectedAt();
                Instant bTime = b.isResolved() ? b.resolvedAt() : b.detectedAt();
                return bTime.compareTo(aTime); // 최신 먼저
            });
            return list;
        }
    }
}
