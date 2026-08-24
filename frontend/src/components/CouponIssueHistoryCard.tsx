import { useEffect, useState } from "react";
import {
  fetchCouponFairness, fetchFairnessTimeline,
  type CouponFairnessReport, type CouponFairnessTimelineEntry,
} from "../lib/api";

const POLL_INTERVAL_MS = 3000;

const STATUS_LABEL: Record<NonNullable<CouponFairnessTimelineEntry["status"]>, string> = {
  ISSUED: "정상 발급",
  USED: "사용됨",
  CANCELED: "취소됨",
  EXPIRED: "만료됨",
};
const STATUS_COLOR: Record<NonNullable<CouponFairnessTimelineEntry["status"]>, string> = {
  ISSUED: "#16a34a",
  USED: "#5b6bd6",
  CANCELED: "#dc2626",
  EXPIRED: "#8b8fa3",
};
const OUTCOME_LABEL: Record<"SOLDOUT" | "DUPLICATE", string> = {
  SOLDOUT: "품절 실패",
  DUPLICATE: "중복 발급 실패",
};
const OUTCOME_COLOR: Record<"SOLDOUT" | "DUPLICATE", string> = {
  SOLDOUT: "#e0821f",
  DUPLICATE: "#dc2626",
};

function reasonLabel(row: CouponFairnessTimelineEntry): { text: string; color: string } {
  if (row.outcome === "SUCCESS" && row.status) {
    return { text: STATUS_LABEL[row.status], color: STATUS_COLOR[row.status] };
  }
  const outcome = row.outcome as "SOLDOUT" | "DUPLICATE";
  return { text: OUTCOME_LABEL[outcome], color: OUTCOME_COLOR[outcome] };
}

interface Props {
  couponId: number;
}

/**
 * "재고 · Redis" 카드 옆 실시간 발급 이력 - GET /api/admin/coupons/{couponId}/fairness/timeline 폴링.
 * rank는 Redis fairness-log의 원자적 처리 순번(재고 차감과 같은 Lua 스크립트 안에서 매겨짐)이라
 * DB issued_at 기반 정렬보다 실제 처리 순서를 정확히 보여준다. SOLDOUT/DUPLICATE 건은 DB 행이
 * 없어서 발급시각/지연이 "-"로 표시된다.
 */
export function CouponIssueHistoryCard({ couponId }: Props) {
  const [rows, setRows] = useState<CouponFairnessTimelineEntry[]>([]);
  const [fairness, setFairness] = useState<CouponFairnessReport | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    setRows([]);
    setFairness(null);
    setError(null);

    const load = () => {
      fetchFairnessTimeline(couponId)
        .then((next) => {
          if (!cancelled) {
            setRows(next);
            setError(null);
          }
        })
        .catch((e) => {
          if (!cancelled) setError(e instanceof Error ? e.message : "선착순 타임라인 조회 실패");
        });

      // 타임라인과 별개 호출 - 하나가 실패해도 다른 하나는 계속 갱신되게 둔다.
      fetchCouponFairness(couponId)
        .then((next) => {
          if (!cancelled) setFairness(next);
        })
        .catch(() => {
          // 조회 실패는 마지막으로 알던 배지를 그대로 유지한다.
        });
    };

    load();
    const timer = window.setInterval(load, POLL_INTERVAL_MS);
    return () => {
      cancelled = true;
      window.clearInterval(timer);
    };
  }, [couponId]);

  return (
    <div className="card card-wide">
      <div className="card-title-row">
        <span className="card-title card-title-tight">쿠폰 발급 이력 · 선착순</span>
        {fairness && (
          <span
            className="overissue-badge"
            style={{
              background: fairness.fair ? "#e9f9ee" : "#fff4e6",
              color: fairness.fair ? "#16a34a" : "#e0821f",
            }}
          >
            {fairness.fair ? "공정" : `새치기 ${fairness.inversionCount}건`} · 전체 {fairness.totalAttempts}건 시도
          </span>
        )}
      </div>

      {error ? (
        <span className="dialog-error">{error}</span>
      ) : rows.length === 0 ? (
        <span className="tile-label-md">발급 이력이 없습니다</span>
      ) : (
        <div className="history-table-wrap history-table-wrap-compact">
          <table className="history-table history-table-compact">
            <thead>
              <tr>
                <th>건</th>
                <th>순번</th>
                <th>유저 / 사유</th>
                <th>발급시각</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((r, i) => {
                const reason = reasonLabel(r);
                return (
                  <tr key={`${r.rank}-${r.userId}`}>
                    <td>{i + 1}</td>
                    <td className="history-cell-mono">#{r.rank}</td>
                    <td>
                      유저 {r.userId} · <span style={{ color: reason.color }}>{reason.text}</span>
                    </td>
                    <td className="history-cell-mono">
                      {r.issuedAt === null ? "-" : new Date(r.issuedAt).toLocaleTimeString("ko-KR")}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
