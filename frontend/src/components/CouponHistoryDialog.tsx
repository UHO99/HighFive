import { useEffect, useState } from "react";
import { fetchCouponIssues, fetchCoupons, type CouponIssueHistoryResponse, type CouponSummary } from "../lib/api";

const STATUS_LABEL: Record<CouponIssueHistoryResponse["status"], string> = {
  ISSUED: "발급됨",
  USED: "사용됨",
  CANCELED: "취소됨",
  EXPIRED: "만료됨",
};

const COUPON_STATUS_LABEL: Record<CouponSummary["status"], string> = {
  READY: "대기",
  OPEN: "오픈중",
  CLOSE: "마감",
};

interface Props {
  onClose: () => void;
}

/**
 * 전체 쿠폰 발급 이력 — 쿠폰 목록(GET /api/admin/coupons)을 먼저 보여주고, 하나를 선택하면
 * 그 쿠폰의 전체 발급 이력(GET /api/admin/coupons/{couponId}/issues)을 이어서 보여준다.
 * 이전에는 대시보드 상단에서 이미 골라둔 쿠폰 하나만 고정으로 봤는데, 그 쿠폰과 무관하게
 * 임의의 쿠폰을 골라 확인할 수 있어야 한다는 요구로 두 단계 구조로 바꿨다.
 */
export function CouponHistoryDialog({ onClose }: Props) {
  const [coupons, setCoupons] = useState<CouponSummary[]>([]);
  const [couponsLoading, setCouponsLoading] = useState(true);
  const [couponsError, setCouponsError] = useState<string | null>(null);

  const [selectedCouponId, setSelectedCouponId] = useState<number | null>(null);
  const [rows, setRows] = useState<CouponIssueHistoryResponse[]>([]);
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

  // 쿠폰을 선택하면 그 쿠폰의 발급 이력을 불러온다.
  useEffect(() => {
    if (selectedCouponId === null) return;

    let cancelled = false;
    setIssuesLoading(true);
    setIssuesError(null);

    fetchCouponIssues(selectedCouponId)
      .then((data) => {
        if (!cancelled) setRows(data);
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
  }, [selectedCouponId]);

  const selectedCoupon = coupons.find((c) => c.id === selectedCouponId) ?? null;

  return (
    <div className="dialog-overlay" onClick={onClose}>
      <div className="dialog-panel dialog-panel-wide" onClick={(e) => e.stopPropagation()}>
        <div className="dialog-header">
          <div className="dialog-title-row">
            <h2 className="dialog-title">전체 쿠폰 발급 이력</h2>
          </div>
          <span className="dialog-subtitle">
            {selectedCoupon
              ? `쿠폰 #${selectedCoupon.id} · ${selectedCoupon.name}의 전체 발급 이력입니다. (최근 발급 순)`
              : "이력을 확인할 쿠폰을 선택하세요."}
          </span>
        </div>

        <div className="coupon-select-list">
          {couponsError && <div className="dialog-error">{couponsError}</div>}
          {couponsLoading ? (
            <p className="dialog-subtitle">쿠폰 목록 불러오는 중…</p>
          ) : coupons.length === 0 ? (
            <p className="dialog-subtitle">쿠폰이 없습니다.</p>
          ) : (
            <ul className="coupon-select-items">
              {coupons.map((c) => (
                <li
                  key={c.id}
                  className={`coupon-select-item${c.id === selectedCouponId ? " coupon-select-item-active" : ""}`}
                  onClick={() => setSelectedCouponId(c.id)}
                >
                  <span className="coupon-select-id">#{c.id}</span>
                  <span className="coupon-select-name">{c.name}</span>
                  <span className="coupon-select-status">{COUPON_STATUS_LABEL[c.status]}</span>
                  <span className="coupon-select-qty">{c.totalQuantity.toLocaleString()}개</span>
                </li>
              ))}
            </ul>
          )}
        </div>

        {selectedCouponId !== null && (
          <div className="history-table-wrap">
            {issuesError && <div className="dialog-error">{issuesError}</div>}
            {issuesLoading ? (
              <p className="dialog-subtitle">불러오는 중…</p>
            ) : rows.length === 0 ? (
              <p className="dialog-subtitle">발급 이력이 없습니다.</p>
            ) : (
              <table className="history-table">
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
                      <td>{r.issueId}</td>
                      <td>{r.userId}</td>
                      <td>{r.userName ?? "-"}</td>
                      <td>{r.userEmail ?? "-"}</td>
                      <td>{r.couponId}</td>
                      <td>{STATUS_LABEL[r.status]}</td>
                      <td>{new Date(r.issuedAt).toLocaleString("ko-KR")}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>
        )}

        <div className="dialog-actions">
          <button type="button" className="dialog-btn primary" onClick={onClose}>
            닫기
          </button>
        </div>
      </div>
    </div>
  );
}