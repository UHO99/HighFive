import { useEffect, useState } from "react";
import { fetchCouponIssues, fetchCoupons, type CouponIssueHistoryResponse, type CouponSummary } from "../lib/api";

const STATUS_LABEL: Record<CouponIssueHistoryResponse["status"], string> = {
  ISSUED: "발급됨",
  USED: "사용됨",
  CANCELED: "취소됨",
  EXPIRED: "만료됨",
};

/** 쿠폰 로그 탭의 발급 로그(CouponIssueHistoryCard)와 같은 상태별 색상 - 두 표를 같은 톤으로 읽히게 한다. */
const STATUS_COLOR: Record<CouponIssueHistoryResponse["status"], string> = {
  ISSUED: "#16a34a",
  USED: "#5b6bd6",
  CANCELED: "#dc2626",
  EXPIRED: "#8b8fa3",
};

const COUPON_STATUS_LABEL: Record<CouponSummary["status"], string> = {
  READY: "대기",
  OPEN: "오픈중",
  CLOSE: "마감",
};

const PAGE_SIZE = 50;

interface Props {
  /** variant="dialog"(기본)일 때만 실제로 호출된다 - "page"는 관리자 탭 콘텐츠라 닫을 방법이 없다. */
  onClose?: () => void;
  /** "dialog": 오버레이 + 모달 패널(기존 동작). "page": 관리자 탭에 그대로 얹는 인라인 카드. */
  variant?: "dialog" | "page";
}

/**
 * 전체 쿠폰 발급 이력 — 쿠폰 목록(GET /api/admin/coupons)을 먼저 보여주고, 하나를 선택하면
 * 그 쿠폰의 전체 발급 이력(GET /api/admin/coupons/{couponId}/issues)을 페이지 단위로 이어서 보여준다.
 * 이력이 많은 쿠폰(부하 테스트로 수만 건)에서 한 번에 다 불러오면 느려서, 발급 로그(CouponIssueHistoryCard)와
 * 동일한 오프셋(page/size) 페이지네이션을 적용했다.
 */
export function CouponHistoryDialog({ onClose, variant = "dialog" }: Props) {
  const [coupons, setCoupons] = useState<CouponSummary[]>([]);
  const [couponsLoading, setCouponsLoading] = useState(true);
  const [couponsError, setCouponsError] = useState<string | null>(null);

  const [selectedCouponId, setSelectedCouponId] = useState<number | null>(null);
  const [rows, setRows] = useState<CouponIssueHistoryResponse[]>([]);
  const [page, setPage] = useState(1);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [issuesLoading, setIssuesLoading] = useState(false);
  const [issuesError, setIssuesError] = useState<string | null>(null);

  // 다이얼로그가 열릴 때 쿠폰 목록을 한 번 불러온다.
  useEffect(() => {
    let cancelled = false;
    setCouponsLoading(true);
    setCouponsError(null);

    fetchCoupons()
      .then((data) => {
        if (!cancelled) setCoupons(data);
      })
      .catch((e) => {
        if (!cancelled) setCouponsError(e instanceof Error ? e.message : "쿠폰 목록 조회 실패");
      })
      .finally(() => {
        if (!cancelled) setCouponsLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, []);

  // 쿠폰이나 페이지가 바뀌면 그 쿠폰의 해당 페이지 발급 이력을 불러온다.
  useEffect(() => {
    if (selectedCouponId === null) return;

    let cancelled = false;
    setIssuesLoading(true);
    setIssuesError(null);

    fetchCouponIssues(selectedCouponId, page, PAGE_SIZE)
      .then((result) => {
        if (cancelled) return;
        setRows(result.items);
        setTotalPages(result.totalPages);
        setTotalElements(result.totalElements);
      })
      .catch((e) => {
        if (!cancelled) setIssuesError(e instanceof Error ? e.message : "이력 조회 실패");
      })
      .finally(() => {
        if (!cancelled) setIssuesLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [selectedCouponId, page]);

  // 쿠폰을 새로 선택하면 이전 쿠폰에서 보고 있던 페이지 번호가 그대로 남지 않도록 1로 되돌린다.
  const handleSelectCoupon = (id: number) => {
    setSelectedCouponId(id);
    setPage(1);
  };

  const selectedCoupon = coupons.find((c) => c.id === selectedCouponId) ?? null;
  const hasPrev = page > 1;
  const hasNext = totalPages > 0 && page < totalPages;

  const content = (
    <>
        <div className="card-title-row">
          <span className="card-title card-title-tight">전체 쿠폰 발급 이력</span>
          {selectedCoupon && (
            <span className="overissue-badge" style={{ background: "#eef2ff", color: "#5b6bd6" }}>
              쿠폰 #{selectedCoupon.id} · {selectedCoupon.name} · 전체 {totalElements.toLocaleString("ko-KR")}건
            </span>
          )}
        </div>
        {!selectedCoupon && <span className="tile-label-md">이력을 확인할 쿠폰을 선택하세요.</span>}

        <div className="coupon-history-chip-row">
          {couponsError && <div className="dialog-error">{couponsError}</div>}
          {couponsLoading ? (
            <span className="tile-label-md">쿠폰 목록 불러오는 중…</span>
          ) : coupons.length === 0 ? (
            <span className="tile-label-md">쿠폰이 없습니다.</span>
          ) : (
            coupons.map((c) => (
              <button
                key={c.id}
                type="button"
                className={`coupon-history-chip${c.id === selectedCouponId ? " active" : ""}`}
                onClick={() => handleSelectCoupon(c.id)}
              >
                #{c.id} · {c.name}
                <span className="coupon-history-chip-meta">
                  {COUPON_STATUS_LABEL[c.status]} · {c.totalQuantity.toLocaleString()}개
                </span>
              </button>
            ))
          )}
        </div>

        {selectedCouponId !== null && (
          <>
            <div className="history-table-wrap history-table-wrap-fill">
              {issuesError && <div className="dialog-error">{issuesError}</div>}
              {issuesLoading ? (
                <span className="tile-label-md">불러오는 중…</span>
              ) : rows.length === 0 ? (
                <span className="tile-label-md">발급 이력이 없습니다.</span>
              ) : (
                <table className="history-table history-table-compact">
                  <thead>
                    <tr>
                      <th>이력 ID</th>
                      <th>User ID</th>
                      <th>이름</th>
                      <th>이메일</th>
                      <th>쿠폰 ID</th>
                      <th>상태</th>
                      <th>발급 시각</th>
                    </tr>
                  </thead>
                  <tbody>
                    {rows.map((r) => (
                      <tr key={r.issueId}>
                        <td className="history-cell-mono history-cell-highlight">{r.issueId}</td>
                        <td className="history-cell-mono">{r.userId}</td>
                        <td>{r.userName ?? "-"}</td>
                        <td>{r.userEmail ?? "-"}</td>
                        <td className="history-cell-mono">{r.couponId}</td>
                        <td>
                          <span style={{ color: STATUS_COLOR[r.status] }}>{STATUS_LABEL[r.status]}</span>
                        </td>
                        <td className="history-cell-mono">{new Date(r.issuedAt).toLocaleString("ko-KR")}</td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              )}
            </div>

            {!issuesLoading && rows.length > 0 && (
              <div className="pagination-bar">
                <button type="button" className="pagination-btn" onClick={() => setPage(1)} disabled={!hasPrev}>
                  처음
                </button>
                <button type="button" className="pagination-btn" onClick={() => setPage(page - 1)} disabled={!hasPrev}>
                  이전
                </button>
                <span className="pagination-label">
                  {totalPages === 0 ? "0 / 0" : `${page} / ${totalPages}`} 페이지 · 전체{" "}
                  {totalElements.toLocaleString("ko-KR")}건
                </span>
                <button type="button" className="pagination-btn" onClick={() => setPage(page + 1)} disabled={!hasNext}>
                  다음
                </button>
              </div>
            )}
          </>
        )}

        {variant === "dialog" && (
          <div className="dialog-actions">
            <button type="button" className="dialog-btn primary" onClick={onClose}>
              닫기
            </button>
          </div>
        )}
    </>
  );

  if (variant === "page") {
    return <div className="card">{content}</div>;
  }

  return (
    <div className="dialog-overlay" onClick={onClose}>
      <div className="dialog-panel dialog-panel-wide" onClick={(e) => e.stopPropagation()}>
        {content}
      </div>
    </div>
  );
}
