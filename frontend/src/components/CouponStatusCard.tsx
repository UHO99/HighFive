import type { DashboardVals } from "../hooks/useMonitoringDashboard";
import type { K6SummaryResponse } from "../lib/api";

interface Props {
  vals: DashboardVals;
  k6Summary: K6SummaryResponse | null;
}

export function CouponStatusCard({ vals, k6Summary }: Props) {
  return (
    <div className="card">
      <span className="card-title card-title-tight">쿠폰 발급 현황</span>

      <div className="tile-grid-2 tile-grid-2-tight">
        <div className="tile tile-sm">
          <div className="tile-label-xs">총 발급 요청</div>
          <div className="tile-value-xs" style={{ color: "var(--color-forest-ink)" }}>
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
          <div className="tile-value-xs" style={{ color: "var(--color-forest-ink)" }}>
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
        {(() => {
          const toNum = (s: string) => parseInt(s.replace(/,/g, ""), 10) || 0;
          const issued = toNum(vals.issuedCountFmt);
          const success = toNum(vals.overissueSuccessFmt);
          const db = toNum(vals.dbIssueCountFmt);
          const max = Math.max(issued, success, db, 1);
          const pct = (v: number) => `${(v / max) * 100}%`;
          return (
            <div className="overissue-chart overissue-chart-vertical">
              <div className="overissue-chart-col">
                <div className="overissue-chart-track-vertical">
                  <div className="overissue-chart-fill-vertical" style={{ height: pct(issued), background: "#e0821f" }} />
                </div>
                <span className="overissue-chart-label">재고</span>
              </div>
              <div className="overissue-chart-col">
                <div className="overissue-chart-track-vertical">
                  <div className="overissue-chart-fill-vertical" style={{ height: pct(success), background: "#16a34a" }} />
                </div>
                <span className="overissue-chart-label">성공</span>
              </div>
              <div className="overissue-chart-col">
                <div className="overissue-chart-track-vertical">
                  <div className="overissue-chart-fill-vertical" style={{ height: pct(db), background: "#5b6bd6" }} />
                </div>
                <span className="overissue-chart-label">DB</span>
              </div>
            </div>
          );
        })()}
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

      {k6Summary?.available && k6Summary.metrics && (
        <div className="k6-result-block">
          <span className="section-label-xs" style={{ marginTop: 10 }}>
            마지막 테스트 결과 요약
          </span>
          <div className="tile-grid-2 tile-grid-2-tight" style={{ marginTop: 6 }}>
            <div className="tile tile-sm">
              <div className="tile-label-xs">소요 시간</div>
              <div className="tile-value-xs">
                {k6Summary.metrics.totalDurationSeconds != null
                  ? `${k6Summary.metrics.totalDurationSeconds.toFixed(1)}초`
                  : "-"}
              </div>
            </div>
            <div className="tile tile-sm">
              <div className="tile-label-xs">초당 처리량</div>
              <div className="tile-value-xs">
                {k6Summary.metrics.throughputPerSecond != null ? `${Math.round(k6Summary.metrics.throughputPerSecond)}/s` : "-"}
              </div>
            </div>
            <div className="tile tile-sm">
              <div className="tile-label-xs">반복당 시간</div>
              <div className="tile-value-xs">
                {k6Summary.metrics.iterationAvgMs != null ? `${Math.round(k6Summary.metrics.iterationAvgMs)}ms` : "-"}
              </div>
            </div>
            <div className="tile tile-sm">
              <div className="tile-label-xs">수신량</div>
              <div className="tile-value-xs">
                {k6Summary.metrics.dataReceivedKb != null ? `${(k6Summary.metrics.dataReceivedKb / 1024).toFixed(1)}MB` : "-"}
              </div>
            </div>
            <div className="tile tile-sm">
              <div className="tile-label-xs">송신량</div>
              <div className="tile-value-xs">
                {k6Summary.metrics.dataSentKb != null ? `${(k6Summary.metrics.dataSentKb / 1024).toFixed(1)}MB` : "-"}
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
