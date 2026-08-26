import { useCallback, useEffect, useRef, useState } from "react";
import {
  fetchCouponFairness, fetchFairnessTimeline,
  type CouponFairnessOutcomeFilter, type CouponFairnessReport, type CouponFairnessTimelineEntry,
} from "../lib/api";
import { formatTimestampMicros, formatTimestampMs } from "../lib/format";

const POLL_INTERVAL_MS = 3000;
/** 한 페이지에 받아오는 건수 - 로그가 아무리 쌓여도 요청/렌더 비용이 이 값에만 비례하도록 고정한다. */
const PAGE_SIZE = 50;
/** 백엔드가 이 값을 totalPages로 clamp해준다는 걸 이용해, "최신 페이지"를 한 번의 요청으로 구한다. */
const LATEST_PAGE = 2147483647;

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

const FILTER_OPTIONS: { value: CouponFairnessOutcomeFilter; label: string }[] = [
  { value: "ALL", label: "전체" },
  { value: "SUCCESS", label: "성공" },
  { value: "FAILURE", label: "실패" },
  { value: "DUPLICATE", label: "중복" },
  { value: "SOLDOUT", label: "재고 소진" },
];

interface Props {
  couponId: number;
}

/**
 * "재고 · Redis" 카드 옆 실시간 발급 로그 - GET /api/admin/coupons/{couponId}/fairness/timeline을
 * 오프셋(page/size) 페이지네이션으로 부른다. rank는 Redis fairness-log의 원자적 처리 순번(재고 차감과
 * 같은 Lua 스크립트 안에서 매겨짐)이라 DB issued_at 기반 정렬보다 실제 처리 순서를 정확히 보여준다.
 * 한 번에 PAGE_SIZE만큼만 렌더링하고(이전 페이지 목록을 누적하지 않음) 폴링도 "현재 보고 있는 페이지"만
 * 다시 받아오므로, 로그가 수만 건으로 쌓여도(부하테스트 중 등) DOM/요청 비용이 늘지 않는다 - 이전의
 * "스크롤마다 이어붙이기" 방식은 시간이 지날수록 행이 계속 누적돼 렉의 원인이 됐다. SOLDOUT/DUPLICATE
 * 건은 DB 행이 없어서 발급시각이 "-"로 표시된다. 제목 옆 공정성 배지는 analyzeFairness()를 그대로
 * 노출하는 GET .../fairness를 별도로 호출한다 - 집계 로직의 단일 소스는 백엔드에만 둔다.
 */
export function CouponIssueHistoryCard({ couponId }: Props) {
  const [rows, setRows] = useState<CouponFairnessTimelineEntry[]>([]);
  const [page, setPage] = useState(1);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [fairness, setFairness] = useState<CouponFairnessReport | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [loading, setLoading] = useState(false);
  // outcome 필터는 서버(fairness/timeline?outcome=)로 그대로 전달된다 - 백엔드가 발급 시도
  // 시점에 outcome별 ZSET을 함께 색인해두므로, 필터가 걸려도 전체 로그를 스캔하지 않고
  // page/size만으로 필터링된 결과와 정확한 totalElements/totalPages를 받는다.
  const [filter, setFilter] = useState<CouponFairnessOutcomeFilter>("ALL");

  // setState(비동기)로는 "지금 이 순간 어느 페이지를 보고 있는지"를 폴링 콜백에서 바로 못 믿는다 -
  // 페이지 이동과 3초 폴링이 겹칠 때 서로 다른 페이지를 요청하지 않도록 동기적으로 갱신되는 ref를 둔다.
  const pageRef = useRef(1);
  const loadingRef = useRef(false);

  const goToPage = useCallback((target: number) => {
    if (loadingRef.current) return;
    loadingRef.current = true;
    setLoading(true);
    fetchFairnessTimeline(couponId, target, PAGE_SIZE, filter)
      .then((result) => {
        setRows(result.items);
        setPage(result.page);
        setTotalPages(result.totalPages);
        setTotalElements(result.totalElements);
        pageRef.current = result.page;
        setError(null);
      })
      .catch((e) => {
        setError(e instanceof Error ? e.message : "선착순 타임라인 조회 실패");
      })
      .finally(() => {
        loadingRef.current = false;
        setLoading(false);
      });
  }, [couponId, filter]);

  useEffect(() => {
    setRows([]);
    setFairness(null);
    setError(null);
    pageRef.current = 1;

    // 코드나 필터가 바뀌면 처음엔 가장 최근 페이지(=그 필터 기준 마지막 페이지)부터 보여준다.
    goToPage(LATEST_PAGE);
    fetchCouponFairness(couponId).then(setFairness).catch(() => {
      // 조회 실패는 마지막으로 알던 배지를 그대로 유지한다.
    });

    // 3초마다 "현재 보고 있는 페이지"만 다시 받아온다 - 페이지를 넘기지 않는 한 보던 위치를 유지한다.
    const timer = window.setInterval(() => {
      goToPage(pageRef.current);
      fetchCouponFairness(couponId).then(setFairness).catch(() => { });
    }, POLL_INTERVAL_MS);
    return () => window.clearInterval(timer);
  }, [couponId, filter, goToPage]);

  const hasPrev = page > 1;
  const hasNext = totalPages > 0 && page < totalPages;

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
      ) : (
        <>
          <div className="filter-bar">
            {FILTER_OPTIONS.map((opt) => (
              <button
                key={opt.value}
                type="button"
                className={`filter-btn${filter === opt.value ? " filter-btn-active" : ""}`}
                onClick={() => setFilter(opt.value)}
              >
                {opt.label}
              </button>
            ))}
          </div>

          {rows.length === 0 && !loading ? (
            <span className="tile-label-md">
              {filter === "ALL" ? "발급 이력이 없습니다" : "조건에 맞는 발급 내역이 없습니다"}
            </span>
          ) : (
          <div className="history-table-wrap history-table-wrap-fill">
            <table className="history-table history-table-compact">
              <thead>
                <tr>
                  <th>순번</th>
                  <th>유저 / 사유</th>
                  <th>Redis처리</th>              {/* 순서 변경 - 신뢰도 1순위 */}
                  <th>컨트롤러진입</th>
                  <th>게이트진입</th>
                  <th>발급시각(DB 저장 시각)</th>   {/* 이름 변경 - "이 값이 rank 순서와 다를 수 있다"는 걸 명확히 */}
                </tr>
              </thead>
              <tbody>
                {rows.map((r) => {
                  const reason = reasonLabel(r);
                  return (
                    <tr key={`${r.rank}-${r.userId}`}>
                      <td className="history-cell-mono history-cell-highlight">#{r.rank}</td>
                      <td>
                        {r.userName ?? `유저 ${r.userId}`}
                        {r.userEmail ? ` (${r.userEmail})` : ""}
                        {" · "}
                        <span style={{ color: reason.color }}>{reason.text}</span>
                      </td>
                      <td className="history-cell-mono history-cell-highlight">
                        {r.redisTimeMicros === null ? "-" : formatTimestampMicros(r.redisTimeMicros)}
                      </td>
                      <td className="history-cell-mono">
                        {r.controllerEnteredAtMs === null ? "-" : formatTimestampMs(r.controllerEnteredAtMs)}
                      </td>
                      <td className="history-cell-mono">
                        {r.gateEnteredAtMs === null ? "-" : formatTimestampMs(r.gateEnteredAtMs)}
                      </td>
                      <td className="history-cell-mono">
                        {r.issuedAt === null ? "-" : formatTimestampMs(new Date(r.issuedAt).getTime())}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
          )}

          <div className="pagination-bar">
            <button type="button" className="pagination-btn" onClick={() => goToPage(1)} disabled={!hasPrev || loading}>
              처음
            </button>
            <button type="button" className="pagination-btn" onClick={() => goToPage(page - 1)} disabled={!hasPrev || loading}>
              이전
            </button>
            <span className="pagination-label">
              {totalPages === 0 ? "0 / 0" : `${page} / ${totalPages}`} 페이지 · 전체 {totalElements.toLocaleString("ko-KR")}건
            </span>
            <button type="button" className="pagination-btn" onClick={() => goToPage(page + 1)} disabled={!hasNext || loading}>
              다음
            </button>
            <button type="button" className="pagination-btn" onClick={() => goToPage(LATEST_PAGE)} disabled={!hasNext || loading}>
              최신
            </button>
          </div>
        </>
      )}
    </div>
  );
}
