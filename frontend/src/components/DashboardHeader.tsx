import type { DashboardVals } from "../hooks/useMonitoringDashboard";
import type { CouponSummary } from "../lib/api";

interface Props {
  vals: DashboardVals;
  error?: string | null;
  /** couponId가 DB에 없어서 404 - 오픈된 쿠폰이 없을 때 정상적으로 발생하므로 error와 분리해서 다룬다. */
  couponMissing?: boolean;
  coupons: CouponSummary[];
  couponId: number;
  onCouponChange: (couponId: number) => void;
  loadingData: boolean;
}

/**
 * coupons는 항상 OPEN 상태만 받는다(DashboardPage가 status=OPEN으로 필터해서 넘김) - 쿠폰이
 * 아무리 많아져도 지금 실제로 열려있는 건 소수라, 드롭다운 대신 칩으로 그냥 다 펼쳐 보여준다.
 * 오픈 자체는 FloatingActionMenu의 "쿠폰 오픈" 다이얼로그에서 READY 쿠폰 중 골라 하므로
 * 여기서는 "지금 뭘 보고 있나"만 다룬다.
 */
export function DashboardHeader({ vals, error, couponMissing, coupons, couponId, onCouponChange, loadingData }: Props) {
  return (
    <div className="main-header">
      <div>
        <h1 className="main-title">부하 테스트 모니터링</h1>
        <span className="main-subtitle">
          대규모 트래픽 선착순 쿠폰 발급 시스템 · 최종 갱신 {vals.clockText}
          {error && <span style={{ color: "#dc2626" }}> · 백엔드 연결 실패 ({error})</span>}
          {!error && couponMissing && <span style={{ color: "#8b8fa3" }}> · 모니터링할 쿠폰 없음</span>}
        </span>
      </div>

      <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
        <div className="coupon-chip-row" role="radiogroup" aria-label="모니터링할 OPEN 쿠폰 선택">
          {coupons.length === 0 ? (
            <span className="coupon-chip-empty">오픈된 쿠폰 없음</span>
          ) : (
            coupons.map((c) => (
              <button
                key={c.id}
                type="button"
                role="radio"
                aria-checked={c.id === couponId}
                className={`coupon-chip ${c.id === couponId ? "active" : ""}`}
                onClick={() => onCouponChange(c.id)}
              >
                #{c.id} · {c.name}
              </button>
            ))
          )}
        </div>

        {loadingData && (
          <div className="status-pill">
            <div className="status-dot" style={{ background: "#f59e0b" }} />
            <span className="status-label">데이터 적재 중</span>
          </div>
        )}

        <div className="status-pill">
          <div className="status-dot" style={{ background: vals.systemStatusColor }} />
          <span className="status-label">{vals.systemStatusLabel}</span>
        </div>
      </div>
    </div>
  );
}
