import type { DashboardVals } from "../hooks/useMonitoringDashboard";
import type { DummyDataCounts } from "../lib/api";
import { formatMs } from "../lib/format";

const FMT = new Intl.NumberFormat("ko-KR");

interface Props {
  vals: DashboardVals;
  dummyDataCounts: DummyDataCounts | null;
  /** 마지막 적재를 "시작하기 직전" 스냅샷 - Before 열에 쓴다. 한 번도 적재 안 했으면 null. */
  beforeCounts: DummyDataCounts | null;
}

function throughput(count: number, ms: number): string {
  if (ms <= 0) return "-";
  return `${FMT.format(Math.round((count / ms) * 1000))}건/s`;
}

function formatDelta(before: number, after: number): string {
  const d = after - before;
  if (d === 0) return "±0";
  return d > 0 ? `+${FMT.format(d)}` : FMT.format(d);
}

const BEFORE_AFTER_ROWS: { key: "userCount" | "couponCount" | "couponIssueCount"; label: string }[] = [
  { key: "userCount", label: "회원" },
  { key: "couponCount", label: "쿠폰" },
  { key: "couponIssueCount", label: "발급 이력" },
];

export function DbStorageCard({ vals, dummyDataCounts, beforeCounts }: Props) {
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

      <span className="section-label">더미데이터 적재 현황 · Before / After</span>
      {dummyDataCounts === null ? (
        <span className="tile-label-md">기록 없음</span>
      ) : (
        <div className="ba-table-wrap">
          <table className="ba-table">
            <thead>
              <tr>
                <th>구분</th>
                <th>BEFORE 적재 전</th>
                <th>AFTER 적재 후</th>
                <th>델타</th>
              </tr>
            </thead>
            <tbody>
              {BEFORE_AFTER_ROWS.map(({ key, label }) => {
                const after = dummyDataCounts[key];
                const before = beforeCounts?.[key];
                return (
                  <tr key={key}>
                    <td className="ba-row-label">{label}</td>
                    <td>{before == null ? "-" : FMT.format(before)}</td>
                    <td>{FMT.format(after)}</td>
                    <td className={before != null && after > before ? "ba-delta-pos" : undefined}>
                      {before == null ? "-" : formatDelta(before, after)}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
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
