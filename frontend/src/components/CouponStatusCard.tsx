import type { DashboardVals } from "../hooks/useMonitoringDashboard";

export function CouponStatusCard({ vals }: { vals: DashboardVals }) {
  return (
    <div className="card">
      <span className="card-title card-title-tight">쿠폰 발급 현황</span>

      <div className="tile-grid-2 tile-grid-2-tight">
        <div className="tile tile-sm">
          <div className="tile-label-xs">총 발급 요청</div>
          <div className="tile-value-xs" style={{ color: "#171b2e" }}>
            {vals.couponTotalFmt}
          </div>
        </div>
        <div className="tile tile-sm">
          <div className="tile-label-xs">성공 발급</div>
          <div className="tile-value-xs" style={{ color: "#16a34a" }}>
            {vals.couponSuccessFmt}
          </div>
        </div>
        <div className="tile tile-sm">
          <div className="tile-label-xs">발급 실패</div>
          <div className="tile-value-xs" style={{ color: "#e0821f" }}>
            {vals.couponFailFmt}
          </div>
        </div>
        <div className="tile tile-sm">
          <div className="tile-label-xs">초당 발급량</div>
          <div className="tile-value-xs" style={{ color: "#171b2e" }}>
            {vals.couponPerSecFmt}
          </div>
        </div>
      </div>

      <span className="section-label-xs">
        실패 사유: 품절 {vals.soldOutFmt} · 중복 {vals.dupFmt}
      </span>
      <div className="stacked-bar">
        <div style={{ background: "#e0821f", width: vals.soldOutPctStyle }} />
        <div style={{ background: "#c76bd6", width: vals.dupPctStyle }} />
      </div>

      <span className="section-label-xs">초과 발급 감시 · 재고/성공/DB이력</span>
      <div className="overissue-box">
        <div className="overissue-grid">
          <div className="overissue-item">
            <span className="tile-label-xs">재고소진</span>
            <span className="tile-value-mono">{vals.issuedCountFmt}</span>
          </div>
          <div className="overissue-item">
            <span className="tile-label-xs">성공발급</span>
            <span className="tile-value-mono">{vals.overissueSuccessFmt}</span>
          </div>
          <div className="overissue-item">
            <span className="tile-label-xs">DB이력</span>
            <span className="tile-value-mono">{vals.dbIssueCountFmt}</span>
          </div>
        </div>
        <span
          className="overissue-badge"
          style={{ background: vals.overissueBg, color: vals.overissueFg }}
        >
          {vals.overissueLabel}
        </span>
      </div>

      <div style={{ display: "flex", justifyContent: "flex-end", marginTop: 6 }}>
        <span
          className="overissue-badge"
          style={{ background: vals.s013ConfirmedBg, color: vals.s013ConfirmedFg }}
        >
          {vals.s013ConfirmedLabel}
        </span>
      </div>
    </div>
  );
}
