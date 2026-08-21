import { useEffect, useState } from "react";
import { fetchCouponIssues, type CouponIssueHistoryResponse } from "../lib/api";

const STATUS_LABEL: Record<CouponIssueHistoryResponse["status"], string> = {
  ISSUED: "발급됨",
  USED: "사용됨",
  CANCELED: "취소됨",
  EXPIRED: "만료됨",
};

interface Props {
  couponId: number;
  onClose: () => void;
}

/**
 * 시나리오 7: 관리자용 특정 쿠폰 발급 이력 — GET /api/admin/coupons/{couponId}/issues
 */
export function CouponHistoryDialog({ couponId, onClose }: Props) {
  const [rows, setRows] = useState<CouponIssueHistoryResponse[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);

    fetchCouponIssues(couponId)
      .then((data) => {
        if (!cancelled) setRows(data);
      })
      .catch((e) => {
        if (!cancelled) setError(e instanceof Error ? e.message : "이력 조회 실패");
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [couponId]);

  return (
    <div className="dialog-overlay" onClick={onClose}>
      <div className="dialog-panel dialog-panel-wide" onClick={(e) => e.stopPropagation()}>
        <div className="dialog-header">
          <div className="dialog-title-row">
            <h2 className="dialog-title">쿠폰 발급 이력 조회</h2>
          </div>
          <span className="dialog-subtitle">
            쿠폰 ID {couponId}의 전체 발급 이력입니다. (최근 발급 순)
          </span>
        </div>

        {error && <div className="dialog-error">{error}</div>}

        <div className="history-table-wrap">
          {loading ? (
            <p className="dialog-subtitle">불러오는 중…</p>
          ) : rows.length === 0 ? (
            <p className="dialog-subtitle">발급 이력이 없습니다.</p>
          ) : (
            <table className="history-table">
              <thead>
                <tr>
                  <th>이력 ID</th>
                  <th>User ID</th>
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
                    <td>{r.couponId}</td>
                    <td>{STATUS_LABEL[r.status]}</td>
                    <td>{new Date(r.issuedAt).toLocaleString("ko-KR")}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>

        <div className="dialog-actions">
          <button type="button" className="dialog-btn primary" onClick={onClose}>
            닫기
          </button>
        </div>
      </div>
    </div>
  );
}
