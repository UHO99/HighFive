import type { DashboardVals } from "../hooks/useMonitoringDashboard";

export function ServerResourceCard({ vals }: { vals: DashboardVals }) {
  return (
    <div className="card">
      <span className="card-title">서버 리소스</span>

      <div className="tile-grid-2">
        <div className="tile">
          <div className="tile-label">RPS / Throughput</div>
          <div className="tile-value" style={{ color: "#171b2e" }}>
            {vals.rpsFmt}
          </div>
        </div>
        <div className="tile">
          <div className="tile-label">CPU 사용률</div>
          <div className="tile-value" style={{ color: vals.cpuColor }}>
            {vals.cpuFmt}%
          </div>
        </div>
        <div className="tile">
          <div className="tile-label">Memory 사용률</div>
          <div className="tile-value" style={{ color: vals.memColor }}>
            {vals.memFmt}%
          </div>
        </div>
        <div className="tile">
          <div className="tile-label">JVM Heap</div>
          <div className="tile-value" style={{ color: vals.heapColor }}>
            {vals.heapFmt}%
          </div>
        </div>
      </div>

      <div className="bars-row">
        <div className="bar-col">
          <div className="bar-col-fill" style={{ background: vals.cpuColor, height: vals.cpuBarHeight }} />
          <span className="bar-col-label">CPU</span>
        </div>
        <div className="bar-col">
          <div className="bar-col-fill" style={{ background: vals.memColor, height: vals.memBarHeight }} />
          <span className="bar-col-label">Memory</span>
        </div>
        <div className="bar-col">
          <div className="bar-col-fill" style={{ background: vals.heapColor, height: vals.heapBarHeight }} />
          <span className="bar-col-label">JVM Heap</span>
        </div>
      </div>
    </div>
  );
}
