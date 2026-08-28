import type { DashboardVals } from "../hooks/useMonitoringDashboard";

export function ApiResponseCard({ vals }: { vals: DashboardVals }) {
  return (
    <div className="card">
      <span className="card-title">API 응답</span>

      <div className="tile-grid-3">
        <div className="tile">
          <div className="tile-label-md">평균 응답시간</div>
          <div className="tile-value-sm" style={{ color: "var(--color-forest-ink)" }}>
            {vals.apiAvgFmt}ms
          </div>
        </div>
        <div className="tile">
          <div className="tile-label-md">p95 / p99</div>
          <div className="tile-value-p95">
            {vals.p95Fmt}/{vals.p99Fmt}
          </div>
        </div>
        <div className="tile">
          <div className="tile-label-md">Error Rate</div>
          <div className="tile-value-sm" style={{ color: vals.errColor }}>
            {vals.errFmt}%
          </div>
        </div>
      </div>

      <span className="section-label">성공 / 실패 응답시간 구분</span>
      <div className="latency-rows">
        <div className="latency-line">
          <span className="latency-tag">성공</span>
          <div className="bar-track">
            <div className="bar-fill" style={{ background: "#16a34a", width: vals.successLatBarPct }} />
          </div>
          <span className="latency-value">{vals.successLatFmt}ms</span>
        </div>
        <div className="latency-line">
          <span className="latency-tag">실패</span>
          <div className="bar-track">
            <div className="bar-fill" style={{ background: "#e0821f", width: vals.failLatBarPct }} />
          </div>
          <span className="latency-value">{vals.failLatFmt}ms</span>
        </div>
      </div>
    </div>
  );
}
