import { useCallback, useEffect, useRef, useState } from "react";
import {
  fetchCouponFairness, fetchFairnessTimeline,
  type CouponFairnessReport, type CouponFairnessTimelineEntry,
} from "../lib/api";

const POLL_INTERVAL_MS = 3000;
/** 한 번에 받아오는 건수 - 로그가 아무리 쌓여도 요청/렌더 비용이 이 값에만 비례하도록 고정한다. */
const PAGE_SIZE = 50;
/** 스크롤이 바닥에서 이만큼(px) 안으로 들어오면 다음 페이지를 미리 당겨온다. */
const SCROLL_LOAD_THRESHOLD_PX = 80;

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
 * "재고 · Redis" 카드 옆 실시간 발급 로그 - GET /api/admin/coupons/{couponId}/fairness/timeline을
 * 커서(afterRank) 페이지네이션으로 부른다. rank는 Redis fairness-log의 원자적 처리 순번(재고 차감과
 * 같은 Lua 스크립트 안에서 매겨짐)이라 DB issued_at 기반 정렬보다 실제 처리 순서를 정확히 보여준다.
 * 전체를 한 번에 안 받고 매번 PAGE_SIZE만큼만 이어붙이므로(스크롤이 바닥에 닿을 때 + 3초 폴링마다)
 * 로그가 수만 건으로 쌓여도(부하테스트 중 등) 요청/렌더 비용이 늘지 않는다 - 부하가 심할 때 스크롤이
 * 렉 걸리는 걸 막으려고 "전체를 한 번에 스크롤" 방식에서 이 방식으로 바꿨다. SOLDOUT/DUPLICATE 건은
 * DB 행이 없어서 발급시각이 "-"로 표시된다. 제목 옆 공정성 배지는 analyzeFairness()를 그대로 노출하는
 * GET .../fairness를 별도로 호출한다 - 집계 로직의 단일 소스는 백엔드에만 둔다.
 */
export function CouponIssueHistoryCard({ couponId }: Props) {
  const [rows, setRows] = useState<CouponFairnessTimelineEntry[]>([]);
  const [fairness, setFairness] = useState<CouponFairnessReport | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loadingMore, setLoadingMore] = useState(false);

  // setState(비동기)로는 "지금 이 순간의 커서"를 다음 호출에서 바로 못 믿는다 - 폴링/스크롤 트리거가
  // 겹칠 때 같은 afterRank로 중복 요청하지 않도록 동기적으로 갱신되는 ref에 커서/진행상태를 둔다.
  const cursorRef = useRef(0);
  const hasMoreRef = useRef(true);
  const loadingRef = useRef(false);

  const loadMore = useCallback(() => {
    if (loadingRef.current) return;
    loadingRef.current = true;
    setLoadingMore(true);
    fetchFairnessTimeline(couponId, cursorRef.current, PAGE_SIZE)
      .then((page) => {
        if (page.items.length > 0) {
          setRows((prev) => [...prev, ...page.items]);
        }
        cursorRef.current = page.nextCursor;
        hasMoreRef.current = page.hasMore;
        setError(null);
      })
      .catch((e) => {
        setError(e instanceof Error ? e.message : "선착순 타임라인 조회 실패");
      })
      .finally(() => {
        loadingRef.current = false;
        setLoadingMore(false);
      });
  }, [couponId]);

  useEffect(() => {
    setRows([]);
    setFairness(null);
    setError(null);
    cursorRef.current = 0;
    hasMoreRef.current = true;

    loadMore();
    fetchCouponFairness(couponId).then(setFairness).catch(() => {
      // 조회 실패는 마지막으로 알던 배지를 그대로 유지한다.
    });

    // 3초마다 "다음 페이지"를 이어서 당겨온다 - hasMore와 무관하게 항상 시도한다(그 사이 새로
    // 발급된 건이 있을 수 있으므로). 이미 커서 끝까지 따라잡았으면 서버가 빈 페이지를 돌려줄 뿐이다.
    const timer = window.setInterval(() => {
      loadMore();
      fetchCouponFairness(couponId).then(setFairness).catch(() => {});
    }, POLL_INTERVAL_MS);
    return () => window.clearInterval(timer);
  }, [couponId, loadMore]);

  const handleScroll = (e: React.UIEvent<HTMLDivElement>) => {
    const el = e.currentTarget;
    const distanceFromBottom = el.scrollHeight - el.scrollTop - el.clientHeight;
    if (distanceFromBottom < SCROLL_LOAD_THRESHOLD_PX && hasMoreRef.current) {
      loadMore();
    }
  };

  return (
    <div className="card card-wide">
      <div className="card-title-row">
        <span className="card-title card-title-tight">발급 로그</span>
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
        <div className="history-table-wrap history-table-wrap-compact" onScroll={handleScroll}>
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
              {loadingMore && (
                <tr>
                  <td colSpan={4} className="history-loading-more">
                    불러오는 중…
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
