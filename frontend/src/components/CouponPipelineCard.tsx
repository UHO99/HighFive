import type { DashboardVals } from "../hooks/useMonitoringDashboard";
import type { DummyDataCounts } from "../lib/api";

const FMT = new Intl.NumberFormat("ko-KR");

interface Props {
  vals: DashboardVals;
  onDrainPending: () => void;
  dummyDataCounts: DummyDataCounts | null;
  beforeCounts: DummyDataCounts | null;
}

function formatDelta(before: number, after: number): string {
  const d = after - before;
  if (d === 0) return "±0";
  return d > 0 ? `+${FMT.format(d)}` : FMT.format(d);
}

function ArrowIcon() {
  return (
    <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2.4" strokeLinecap="round" strokeLinejoin="round">
      <path d="M9 6l6 6-6 6" />
    </svg>
  );
}

interface FlowArrowProps {
  label: string;
}

function FlowArrow({ label }: FlowArrowProps) {
  return (
    <div className="pf-arrow">
      <span className="pf-arrow-label">{label}</span>
      <div className="pf-arrow-track" />
      <span className="pf-arrow-chevron"><ArrowIcon /></span>
    </div>
  );
}

export function CouponPipelineCard({ vals, onDrainPending, dummyDataCounts, beforeCounts }: Props) {
  return (
    <div className="card">
      <span className="card-title">발급 파이프라인 · Redis → DB</span>

      <div className="pf-row">
        {/* ... 기존 4개 노드(Lua 원자 발급 ~ DB Batch Insert) 그대로, 변경 없음 ... */}
      </div>

      {/* 1. 더미데이터 적재 결과 - Before/After/델타 → 적재 수량 + 소요 시간으로 단순화 */}
      <span className="section-label">더미데이터 적재 결과</span>
      {dummyDataCounts === null ? (
        <span className="tile-label-md">기록 없음</span>
      ) : (
        <div className="ba-table-wrap">
          <table className="ba-table">
            <thead>
              <tr>
                <th>구분</th>
                <th>적재 수량</th>
                <th>소요 시간</th>
              </tr>
            </thead>
            <tbody>
              <tr>
                <td className="ba-row-label">회원</td>
                <td>{FMT.format(dummyDataCounts.userCount)}</td>
                <td>{dummyDataCounts.userLoadMs == null ? "-" : `${FMT.format(dummyDataCounts.userLoadMs)}ms`}</td>
              </tr>
              <tr>
                <td className="ba-row-label">쿠폰</td>
                <td>{FMT.format(dummyDataCounts.couponCount)}</td>
                <td>-</td>
              </tr>
              <tr>
                <td className="ba-row-label">발급 이력</td>
                <td>{FMT.format(dummyDataCounts.couponIssueCount)}</td>
                <td>{dummyDataCounts.couponIssueLoadMs == null ? "-" : `${FMT.format(dummyDataCounts.couponIssueLoadMs)}ms`}</td>
              </tr>
              <tr>
                <td className="ba-row-label">전체</td>
                <td>-</td>
                <td>{dummyDataCounts.totalMs == null ? "-" : `${FMT.format(dummyDataCounts.totalMs)}ms`}</td>
              </tr>
            </tbody>
          </table>
        </div>
      )}

      {/* 2. 발급 수 현황(가제) - 적재 이후 실제 테스트로 늘어난 만큼만 별도로 확인 */}
      <span className="section-label">발급 수 현황</span>
      {dummyDataCounts === null || beforeCounts === null ? (
        <span className="tile-label-md">적재 기록 없음</span>
      ) : (
        <div className="ba-table-wrap">
          <table className="ba-table">
            <thead>
              <tr>
                <th>구분</th>
                <th>적재 직후</th>
                <th>현재</th>
                <th>증가분</th>
              </tr>
            </thead>
            <tbody>
              <tr>
                <td className="ba-row-label">발급 이력</td>
                <td>{FMT.format(beforeCounts.couponIssueCount)}</td>
                <td>{FMT.format(dummyDataCounts.couponIssueCount)}</td>
                <td className={dummyDataCounts.couponIssueCount > beforeCounts.couponIssueCount ? "ba-delta-pos" : undefined}>
                  {formatDelta(beforeCounts.couponIssueCount, dummyDataCounts.couponIssueCount)}
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      )}

      <div className="pf-footer">
        Stream PEL이 남아있는 동안엔 정합성 동기화 대상에서 제외됩니다 — 드레인 완료 후 아래{" "}
        <b>정합성 동기화 · 검증</b> 카드에서 최종 확인하세요.
      </div>
    </div>
  );
}