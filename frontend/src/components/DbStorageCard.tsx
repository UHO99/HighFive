import type { DashboardVals } from "../hooks/useMonitoringDashboard";
import type { DummyDataCounts } from "../lib/api";
import { formatMs } from "../lib/format";

const FMT = new Intl.NumberFormat("ko-KR");

interface Props {
  vals: DashboardVals;
  dummyDataCounts: DummyDataCounts | null;
}

function throughput(count: number, ms: number): string {
  if (ms <= 0) return "-";
  return `${FMT.format(Math.round((count / ms) * 1000))}건/s`;
}

export function DbStorageCard({ vals, dummyDataCounts }: Props) {
  return (
    <div className="card">
      <span className="card-title">DB 저장</span>

      <div className="tile-grid-2">
        <div className="tile">
          <div className="tile-label-md">DB Conn Pool</div>
          <div className="tile-value-sm" style={{ color: vals.dbConnColor }}>
            {vals.dbConnFmt}
          </div>
        </div>
        <div className="tile">
          <div className="tile-label-md">DB Insert 처리량</div>
          <div className="tile-value-sm" style={{ color: "#171b2e" }}>
            {vals.dbInsertFmt}
          </div>
        </div>
      </div>

      <span className="section-label">Batch Insert 크기 분포 (상한 500건)</span>
      <div className="latency-rows">
        <div className="latency-line">
          <span className="latency-tag">평균</span>
          <div className="bar-track">
            <div className="bar-fill" style={{ background: "#5b6bd6", width: vals.batchAvgPct }} />
          </div>
          <span className="latency-value">{vals.batchAvgFmt}</span>
        </div>
        <div className="latency-line">
          <span className="latency-tag">최대</span>
          <div className="bar-track">
            <div className="bar-fill" style={{ background: "#171b2e", width: vals.batchMaxPct }} />
          </div>
          <span className="latency-value">{vals.batchMaxFmt}</span>
        </div>
      </div>

      <span className="section-label">더미데이터 적재 현황</span>
      {dummyDataCounts === null ? (
        <span className="tile-label-md">기록 없음</span>
      ) : (
        <div className="tile-grid-3">
          <div className="tile tile-sm">
            <div className="tile-label-xs">회원</div>
            <div className="tile-value-xs">{FMT.format(dummyDataCounts.userCount)}</div>
          </div>
          <div className="tile tile-sm">
            <div className="tile-label-xs">쿠폰</div>
            <div className="tile-value-xs">{FMT.format(dummyDataCounts.couponCount)}</div>
          </div>
          <div className="tile tile-sm">
            <div className="tile-label-xs">발급 이력</div>
            <div className="tile-value-xs">{FMT.format(dummyDataCounts.couponIssueCount)}</div>
          </div>
        </div>
      )}

      {dummyDataCounts?.totalMs != null &&
        dummyDataCounts.userLoadMs != null &&
        dummyDataCounts.couponIssueLoadMs != null && (
          <>
            <span className="section-label">이번 적재 소요시간 (총 {formatMs(dummyDataCounts.totalMs)})</span>
            <div className="latency-rows">
              <div className="latency-line">
                <span className="latency-tag">회원</span>
                <div className="bar-track">
                  <div
                    className="bar-fill"
                    style={{
                      background: "#5b6bd6",
                      width: `${Math.min(100, (dummyDataCounts.userLoadMs / dummyDataCounts.totalMs) * 100)}%`,
                    }}
                  />
                </div>
                <span className="latency-value">
                  {formatMs(dummyDataCounts.userLoadMs)} · {throughput(dummyDataCounts.userCount, dummyDataCounts.userLoadMs)}
                </span>
              </div>
              <div className="latency-line">
                <span className="latency-tag">쿠폰</span>
                <div className="bar-track">
                  <div
                    className="bar-fill"
                    style={{
                      background: "#171b2e",
                      width: `${Math.min(100, (dummyDataCounts.couponIssueLoadMs / dummyDataCounts.totalMs) * 100)}%`,
                    }}
                  />
                </div>
                <span className="latency-value">
                  {formatMs(dummyDataCounts.couponIssueLoadMs)} ·{" "}
                  {throughput(dummyDataCounts.couponIssueCount, dummyDataCounts.couponIssueLoadMs)}
                </span>
              </div>
            </div>
          </>
        )}
    </div>
  );
}
