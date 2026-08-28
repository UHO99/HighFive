import { useCallback, useEffect, useRef, useState } from "react";
import {
  fetchCouponFairness, fetchFairnessTimeline,
  type CouponFairnessOutcomeFilter, type CouponFairnessReport, type CouponFairnessTimelineEntry,
} from "../lib/api";
import { formatTimestampMicros, formatTimestampMs } from "../lib/format";

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
  /** true→false로 바뀌는 순간(테스트 종료)에만 로그를 다시 불러온다. */
  testRunning: boolean;
}

/**
 * "재고 · Redis" 카드 옆 발급 로그 - GET /api/admin/coupons/{couponId}/fairness/timeline을
 * 오프셋(page/size) 페이지네이션으로 부른다. rank는 Redis fairness-log의 원자적 처리 순번(재고 차감과
 * 같은 Lua 스크립트 안에서 매겨짐)이라 DB issued_at 기반 정렬보다 실제 처리 순서를 정확히 보여준다.
 * 부하 테스트 도중 3초 폴링은 요청 폭주와 화면 뒤흔들림만 컸던 반면, 테스트가 끝나기 전까지는 어차피
 * 로그가 최종 확정되지 않으므로, "쿠폰/필터가 바뀔 때 1회 + 테스트가 막 끝나는 순간 1회"만 다시 불러온다.
 * 페이지 이동/필터 변경 시 조회하는 방식과 페이지네이션 자체는 기존과 동일하게 유지한다.
 */
export function CouponIssueHistoryCard({ couponId, testRunning }: Props) {
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

  // setState(비동기)로는 "지금 이 순간 어느 페이지를 보고 있는지"를 재조회 콜백에서 바로 못 믿는다 -
  // 페이지 이동과 테스트 종료 감지가 겹칠 때 서로 다른 페이지를 요청하지 않도록 동기적으로 갱신되는 ref를 둔다.
  const pageRef = useRef(1);
  const loadingRef = useRef(false);
  // 직전 렌더링의 testRunning 값을 기억해서 "true -> false로 바뀌는 그 순간"만 정확히 잡아낸다.
  const wasRunningRef = useRef(testRunning);

  const goToPage = useCallback((target: number) => {
    if (loadingRef.current) return;
    loadingRef.current = true;
    setLoading(true);
    fetchFairnessTimeline(couponId, target, PAGE_SIZE, filter)
      .then((result) => {
        setRows(result.items ?? []);
        setPage(result.page ?? target);
        setTotalPages(result.totalPages ?? 0);
        setTotalElements(result.totalElements ?? 0);
        pageRef.current = result.page ?? target;
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

  const refetchFairness = useCallback(() => {
    fetchCouponFairness(couponId).then(setFairness).catch(() => {
      // 조회 실패는 마지막으로 알던 배지를 그대로 유지한다.
    });
  }, [couponId]);

  // 쿠폰이나 필터가 바뀌면 최신 페이지부터 1회 조회한다.
  useEffect(() => {
    setRows([]);
    setFairness(null);
    setError(null);
    pageRef.current = 1;

    goToPage(LATEST_PAGE);
    refetchFairness();
  }, [couponId, filter, goToPage, refetchFairness]);

  // 폴링 대신, "테스트가 방금 끝난 순간"에만 현재 보고 있는 페이지를 다시 불러온다.
  useEffect(() => {
    const wasRunning = wasRunningRef.current;
    wasRunningRef.current = testRunning;

    if (wasRunning && !testRunning) {
      goToPage(pageRef.current);
      refetchFairness();
    }
  }, [testRunning, goToPage, refetchFairness]);

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
                    <th>Redis처리</th>
                    <th>컨트롤러진입</th>
                    <th>게이트진입</th>
                    <th>발급시각(DB 저장 시각)</th>
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
              {(totalPages ?? 0) === 0 ? "0 / 0" : `${page ?? 0} / ${totalPages ?? 0}`} 페이지 · 전체 {(totalElements ?? 0).toLocaleString("ko-KR")}건
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
