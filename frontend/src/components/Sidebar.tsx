import type { KeyboardEvent } from "react";
import type { DashboardVals } from "../hooks/useMonitoringDashboard";

interface Props {
  /** 더미데이터가 한 번도 적재되지 않았으면 발급 이력 자체가 의미 없다 - FAB의 다른 기능들과 같은 기준. */
  couponHistoryDisabled: boolean;
  onOpenCouponHistory: () => void;
  vals?: DashboardVals;
}

export function Sidebar({ couponHistoryDisabled, onOpenCouponHistory, vals }: Props) {
  const handleCouponHistoryKeyDown = (e: KeyboardEvent) => {
    if (couponHistoryDisabled) return;
    if (e.key === "Enter" || e.key === " ") {
      e.preventDefault();
      onOpenCouponHistory();
    }
  };

  return (
    <aside className="sidebar">
      <div className="sidebar-brand">
        <div className="sidebar-logo">
          <svg width="44" height="44" viewBox="0 0 24 24" fill="none" aria-hidden="true">
            <rect width="24" height="24" rx="8" fill="#163300" />
            <path d="M5.5 8.8H18.5A1.6 1.6 0 0 1 20.1 10.4V13.6A1.6 1.6 0 0 1 18.5 15.2H5.5A1.6 1.6 0 0 1 3.9 13.6V10.4A1.6 1.6 0 0 1 5.5 8.8Z" fill="#9fe870" />
            <circle cx="5.2" cy="12" r="1.3" fill="#163300" />
            <circle cx="18.8" cy="12" r="1.3" fill="#163300" />
            <path d="M9 12h6M12 10v4" stroke="#163300" strokeWidth="1.4" strokeLinecap="round" />
          </svg>
        </div>
        <span className="sidebar-brand-name">HighFive</span>
      </div>

      <span className="sidebar-section-label">모니터링</span>
      <nav className="sidebar-nav">
        <div className="sidebar-nav-item active">대시보드</div>
        <div
          className={`sidebar-nav-item${couponHistoryDisabled ? " sidebar-nav-item-disabled" : ""}`}
          role="button"
          tabIndex={couponHistoryDisabled ? -1 : 0}
          onClick={couponHistoryDisabled ? undefined : onOpenCouponHistory}
          onKeyDown={handleCouponHistoryKeyDown}
          title={couponHistoryDisabled ? "먼저 데이터 적재를 완료하세요" : undefined}
        >
          전체 쿠폰 발급 이력
        </div>
      </nav>

      {vals && (
        <>
          <div className="sidebar-card">
            <span className="sidebar-card-title">서버 리소스</span>
            <div className="sidebar-metrics">
              <div className="sidebar-metric">
                <span className="sidebar-metric-label">RPS</span>
                <span className="sidebar-metric-value" style={{ color: "var(--color-forest-ink)" }}>{vals.rpsFmt}</span>
              </div>
              <div className="sidebar-metric">
                <span className="sidebar-metric-label">CPU</span>
                <span className="sidebar-metric-value" style={{ color: vals.cpuColor }}>{vals.cpuFmt}%</span>
              </div>
              <div className="sidebar-metric">
                <span className="sidebar-metric-label">Memory</span>
                <span className="sidebar-metric-value" style={{ color: vals.memColor }}>{vals.memFmt}%</span>
              </div>
            </div>
            <div className="sidebar-chart-bars">
              <div className="sidebar-chart-col">
                <div className="sidebar-chart-track">
                  <div className="sidebar-chart-fill" style={{ height: vals.cpuBarHeight, background: vals.cpuColor }} />
                </div>
                <span className="sidebar-chart-label">CPU</span>
                <span className="sidebar-chart-value" style={{ color: vals.cpuColor }}>{vals.cpuFmt}%</span>
              </div>
              <div className="sidebar-chart-col">
                <div className="sidebar-chart-track">
                  <div className="sidebar-chart-fill" style={{ height: vals.memBarHeight, background: vals.memColor }} />
                </div>
                <span className="sidebar-chart-label">MEM</span>
                <span className="sidebar-chart-value" style={{ color: vals.memColor }}>{vals.memFmt}%</span>
              </div>
              <div className="sidebar-chart-col">
                <div className="sidebar-chart-track">
                  <div className="sidebar-chart-fill" style={{ height: vals.heapBarHeight, background: vals.heapColor }} />
                </div>
                <span className="sidebar-chart-label">HEAP</span>
                <span className="sidebar-chart-value" style={{ color: vals.heapColor }}>{vals.heapFmt}%</span>
              </div>
            </div>
          </div>

          <div className="sidebar-card">
            <span className="sidebar-card-title">API 응답</span>
            <div className="sidebar-metrics">
              <div className="sidebar-metric">
                <span className="sidebar-metric-label">평균</span>
                <span className="sidebar-metric-value">{vals.apiAvgFmt}ms</span>
              </div>
              <div className="sidebar-metric">
                <span className="sidebar-metric-label">p95</span>
                <span className="sidebar-metric-value">{vals.p95Fmt}ms</span>
              </div>
              <div className="sidebar-metric">
                <span className="sidebar-metric-label">Error</span>
                <span className="sidebar-metric-value" style={{ color: vals.errColor }}>{vals.errFmt}%</span>
              </div>
            </div>
            <div className="sidebar-chart-error">
              <div className="sidebar-chart-error-track">
                <div className="sidebar-chart-error-fill" style={{ width: `${Math.min(100, parseFloat(vals.errFmt) * 20)}%`, background: vals.errColor }} />
              </div>
              <div className="sidebar-chart-error-labels">
                <span>Error Rate</span>
                <span style={{ color: vals.errColor }}>{vals.errFmt}%</span>
              </div>
            </div>
            <div className="sidebar-latency">
              <div className="sidebar-latency-row">
                <span className="sidebar-latency-label">성공</span>
                <div className="sidebar-latency-track">
                  <div className="sidebar-latency-fill" style={{ width: vals.successLatBarPct, background: "#16a34a" }} />
                </div>
                <span className="sidebar-latency-value">{vals.successLatFmt}ms</span>
              </div>
              <div className="sidebar-latency-row">
                <span className="sidebar-latency-label">실패</span>
                <div className="sidebar-latency-track">
                  <div className="sidebar-latency-fill" style={{ width: vals.failLatBarPct, background: "#e0821f" }} />
                </div>
                <span className="sidebar-latency-value">{vals.failLatFmt}ms</span>
              </div>
            </div>
          </div>
        </>
      )}

      <div className="sidebar-profile">
        <div className="sidebar-avatar">관</div>
        <div className="sidebar-profile-info">
          <span className="sidebar-profile-name">운영 관리자</span>
          <span className="sidebar-profile-role">Backend Team</span>
        </div>
      </div>
    </aside>
  );
}
