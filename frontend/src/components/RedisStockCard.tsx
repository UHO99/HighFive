import type { DashboardVals } from "../hooks/useMonitoringDashboard";

interface Props {
  vals: DashboardVals;
  onDrainPending: () => void;
}

export function RedisStockCard({ vals, onDrainPending }: Props) {
  return (
    <div className="card">
      <span className="card-title">재고 · Redis</span>

      <div className="donut-row">
        <div
          className="donut"
          style={{
            background: `conic-gradient(#ff6a3d 0% ${vals.issuedPct}%, #eef0f4 ${vals.issuedPct}% 100%)`,
          }}
        >
          <div className="donut-inner">
            <span className="donut-inner-text">{vals.issuedPctFmt}%</span>
          </div>
        </div>
        <span className="donut-caption">
          재고 소진 현황
          <br />
          발급 {vals.issuedCountFmt}
        </span>
      </div>

      <div className="tile-grid-2">
        <div className="tile">
          <div className="tile-label-md">Redis 재고</div>
          <div className="tile-value-p95">{vals.redisStockFmt}</div>
        </div>
        <div className="tile">
          <div className="tile-label-md">활성 스트림 구독 수</div>
          <div className="tile-value-p95" style={{ color: vals.streamSubColor }}>
            {vals.activeStreamSubsFmt} / {vals.openCouponFmt}
          </div>
        </div>
      </div>

      <div style={{ display: "flex", alignItems: "center", justifyContent: "space-between" }}>
        <span className="section-label" style={{ marginBottom: 0 }}>Stream PEL (처리 대기)</span>
        {vals.pelCountRaw > 0 && (
          <button
            type="button"
            onClick={onDrainPending}
            style={{
              font: "600 10.5px/1 Inter, sans-serif",
              color: "#dc2626",
              background: "#fff",
              border: "1px solid #f3c9c9",
              borderRadius: 999,
              padding: "4px 9px",
              cursor: "pointer",
            }}
          >
            PEL 강제 드레인
          </button>
        )}
      </div>
      <div className="latency-line">
        <div className="bar-track">
          <div className="bar-fill" style={{ background: vals.pelColor, width: vals.pelBarPct }} />
        </div>
        <span className="latency-value" style={{ color: vals.pelColor }}>
          {vals.pelCountFmt}건
        </span>
      </div>
      <span className="pel-delay-caption">최대 지연 {vals.pelDelayFmt}</span>
    </div>
  );
}
