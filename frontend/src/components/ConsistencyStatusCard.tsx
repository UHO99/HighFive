import { useEffect, useState } from "react";
import {
  fetchConsistencyStatus,
  type CouponConsistencyStatusResponse, type MismatchHistoryEntry, type SyncLogEntry, type VerifyLogEntry,
} from "../lib/api";

const FMT = new Intl.NumberFormat("ko-KR");

const POLL_INTERVAL_MS = 3000;
const CLOCK_TICK_MS = 1000;
/** 배치 주기의 이 배수만큼 lastRunAt이 안 갱신되면 "응답 없음"(스케줄러 멈춤)으로 본다. */
const STALE_MULTIPLIER = 2.5;

const RING_SIZE = 40;
const RING_STROKE = 4;
const RING_RADIUS = (RING_SIZE - RING_STROKE) / 2;
const RING_CIRCUMFERENCE = 2 * Math.PI * RING_RADIUS;

/**
 * 시스템 전체 관점 카드 - couponId와 무관하게 동기화(CouponStockSyncService)/검증
 * (CouponStockValidationService) 배치 두 개가 지금 살아서 도는지 보여준다. couponId 스코프인
 * 모니터링 대시보드 폴링과 별개로 자체 폴링한다 - 오픈된 쿠폰이 없어 그쪽이 실패하는 동안에도
 * 이 카드는 계속 갱신되어야 하기 때문이다.
 * "N초 전" 텍스트 대신, 다음 실행까지 남은 시간을 링이 줄어드는 걸로 보여준다(카운트다운) - 링이
 * 다 돌아 원래 배치 주기(예: 동기화 5초, 검증 60초)를 넘도록 안 채워지면 빨간 "!"로 바뀐다.
 */
export function ConsistencyStatusCard() {
  const [status, setStatus] = useState<CouponConsistencyStatusResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [now, setNow] = useState(Date.now());

  useEffect(() => {
    let cancelled = false;
    const load = () => {
      fetchConsistencyStatus()
        .then((next) => {
          if (!cancelled) {
            setStatus(next);
            setError(null);
          }
        })
        .catch((e) => {
          if (!cancelled) setError(e instanceof Error ? e.message : "정합성 상태 조회 실패");
        });
    };
    load();
    const timer = window.setInterval(load, POLL_INTERVAL_MS);
    return () => {
      cancelled = true;
      window.clearInterval(timer);
    };
  }, []);

  useEffect(() => {
    const clock = window.setInterval(() => setNow(Date.now()), CLOCK_TICK_MS);
    return () => window.clearInterval(clock);
  }, []);

  return (
    <div className="card">
      <span className="card-title card-title-tight">정합성 동기화 · 검증</span>

      {error && <span className="dialog-error">{error}</span>}

      {status && (
        <>
          <div className="consistency-row">
            <ConsistencyBlock
              title="동기화"
              lastRunAt={status.sync.lastRunAt}
              intervalMs={status.sync.intervalMs}
              now={now}
              summary={`대상 ${status.sync.targetCount}건 · 처리 ${status.sync.syncedCount}건`}
            >
              <SyncLogList entries={status.sync.log} />
            </ConsistencyBlock>
            <ConsistencyBlock
              title="검증"
              lastRunAt={status.verify.lastRunAt}
              intervalMs={status.verify.intervalMs}
              now={now}
              summary={`재검증 대상 ${status.verify.targetCount}건 · 확정 ${status.verify.confirmedCount}건`}
              extra={
                status.verify.mismatchCount > 0
                  ? `불일치 ${status.verify.mismatchCount}건`
                  : undefined
              }
            >
              <VerifyLogList entries={status.verify.log} />
            </ConsistencyBlock>
          </div>

          <span className="consistency-caption">
            검증은 CLOSE된 쿠폰만 대상으로 합니다 — OPEN 중에는 재검증 대상 0건이 정상입니다.
          </span>

          {status.verify.mismatchHistory.length > 0 && (
            <div className="consistency-mismatch-list">
              {status.verify.mismatchHistory.map((m) => (
                <MismatchHistoryItem key={m.couponId} entry={m} />
              ))}
            </div>
          )}
        </>
      )}
    </div>
  );
}

interface ConsistencyBlockProps {
  title: string;
  lastRunAt: string | null;
  intervalMs: number;
  now: number;
  summary: string;
  extra?: string;
  children?: React.ReactNode;
}

function ConsistencyBlock({ title, lastRunAt, intervalMs, now, summary, extra, children }: ConsistencyBlockProps) {
  const elapsedMs = lastRunAt === null ? Infinity : now - Date.parse(lastRunAt);
  const stale = elapsedMs > intervalMs * STALE_MULTIPLIER;
  const remainingMs = Math.max(0, intervalMs - elapsedMs);
  const remainingFraction = stale ? 0 : remainingMs / intervalMs;
  const ringLabel = stale ? "!" : `${Math.ceil(remainingMs / 1000)}s`;

  return (
    <div className="consistency-block">
      <CountdownRing fraction={remainingFraction} stale={stale} label={ringLabel} />
      <div className="consistency-block-info">
        <span className="consistency-block-title">{title}</span>
        <span className="tile-label-md">
          {summary}
          {extra && <span className="consistency-mismatch-badge">{extra}</span>}
        </span>
        {stale && <span className="consistency-mismatch">응답 없음</span>}
      </div>
      {/* 링+요약 텍스트 옆에 남는 공간에 "실제로 완료된" 건의 최근 로그를 보여준다 - 대상/처리
          카운트는 매 사이클 갱신되는 집계값일 뿐이라, "몇 번 쿠폰이 언제 끝났는지"는 여기서 봐야 한다. */}
      <div className="consistency-block-log">{children}</div>
    </div>
  );
}

/** 동기화(S012) 완료 로그 - 실제로 issuedQuantity가 써진(=드레인 완료 후 처음 동기화된) 순간만 한 줄씩. */
function SyncLogList({ entries }: { entries: SyncLogEntry[] }) {
  if (entries.length === 0) {
    return <span className="consistency-log-empty">완료 내역 없음</span>;
  }
  return (
    <>
      {entries.map((e) => (
        <div key={`${e.couponId}-${e.syncedAt}`} className="consistency-log-item">
          쿠폰 #{e.couponId} · {FMT.format(e.issuedQuantity)}건
          <span className="consistency-log-time"> · {new Date(e.syncedAt).toLocaleTimeString("ko-KR")}</span>
        </div>
      ))}
    </>
  );
}

/** 검증(S013) 확정 로그 - "드레인 완료 + 기록값=실측값"이 처음 확정된 순간만 한 줄씩. */
function VerifyLogList({ entries }: { entries: VerifyLogEntry[] }) {
  if (entries.length === 0) {
    return <span className="consistency-log-empty">완료 내역 없음</span>;
  }
  return (
    <>
      {entries.map((e) => (
        <div key={`${e.couponId}-${e.confirmedAt}`} className="consistency-log-item">
          쿠폰 #{e.couponId} · 확정
          <span className="consistency-log-time"> · {new Date(e.confirmedAt).toLocaleTimeString("ko-KR")}</span>
        </div>
      ))}
    </>
  );
}

/** 미해소(진행 중)는 빨간 강조, 해소된 건은 회색으로 흐리게 - 해소돼도 목록에서 지워지지 않는다. */
function MismatchHistoryItem({ entry }: { entry: MismatchHistoryEntry }) {
  const resolved = entry.resolvedAt !== null;
  const overIssued = entry.actualIssuedCount > entry.totalQuantity;
  const className = resolved
    ? "consistency-mismatch-item consistency-mismatch-item-resolved"
    : "consistency-mismatch-item consistency-mismatch-item-open";

  return (
    <div className={className}>
      <span className="consistency-mismatch-status">{resolved ? "해소됨" : "미해소"}</span>
      {overIssued && <span className="consistency-mismatch-status">초과 발급</span>}
      쿠폰 #{entry.couponId} · 기록값 {entry.recordedIssuedQuantity ?? "미동기화"} / 실측값{" "}
      {entry.actualIssuedCount}
      {overIssued && ` · 재고 ${entry.totalQuantity} 초과 ${entry.actualIssuedCount - entry.totalQuantity}건`}
      {entry.pendingCount > 0 && ` · PEL 대기 ${entry.pendingCount}건`}
      <span className="consistency-mismatch-time">
        {" "}
        · 감지 {new Date(entry.detectedAt).toLocaleTimeString("ko-KR")}
        {resolved && entry.resolvedAt && ` → 해소 ${new Date(entry.resolvedAt).toLocaleTimeString("ko-KR")}`}
      </span>
    </div>
  );
}

interface CountdownRingProps {
  /** 다음 실행까지 남은 시간의 비율(0~1) - 1이면 방금 막 실행됨, 0에 가까워질수록 다음 실행이 임박. */
  fraction: number;
  stale: boolean;
  label: string;
}

function CountdownRing({ fraction, stale, label }: CountdownRingProps) {
  const clamped = Math.max(0, Math.min(1, fraction));
  const offset = RING_CIRCUMFERENCE * (1 - clamped);
  const color = stale ? "#dc2626" : "#16a34a";

  return (
    <svg width={RING_SIZE} height={RING_SIZE} viewBox={`0 0 ${RING_SIZE} ${RING_SIZE}`} className="consistency-ring">
      <circle
        cx={RING_SIZE / 2} cy={RING_SIZE / 2} r={RING_RADIUS}
        fill="none" stroke="#eef0f4" strokeWidth={RING_STROKE}
      />
      <circle
        cx={RING_SIZE / 2} cy={RING_SIZE / 2} r={RING_RADIUS}
        fill="none" stroke={color} strokeWidth={RING_STROKE} strokeLinecap="round"
        strokeDasharray={RING_CIRCUMFERENCE}
        strokeDashoffset={offset}
        transform={`rotate(-90 ${RING_SIZE / 2} ${RING_SIZE / 2})`}
        style={{ transition: "stroke-dashoffset 0.9s linear" }}
      />
      <text x="50%" y="52%" textAnchor="middle" dominantBaseline="middle" className="consistency-ring-text">
        {label}
      </text>
    </svg>
  );
}
