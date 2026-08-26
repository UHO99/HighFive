import type { DashboardVals } from "../hooks/useMonitoringDashboard";
import type { DummyDataCounts } from "../lib/api";

const FMT = new Intl.NumberFormat("ko-KR");

interface Props {
  vals: DashboardVals;
  onDrainPending: () => void;
  dummyDataCounts: DummyDataCounts | null;
  /** 마지막 적재를 "시작하기 직전" 스냅샷 - Before 열에 쓴다. 한 번도 적재 안 했으면 null. */
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

/**
 * 재고·Redis / DB 저장 카드를 하나로 합친 "발급 파이프라인" 카드 - Lua 원자 발급 -> Stream 적재 ->
 * Flush 배치 묶기 -> DB Batch Insert 4단계를 노드+화살표로 이어 보여준다.
 */
export function CouponPipelineCard({ vals, onDrainPending, dummyDataCounts, beforeCounts }: Props) {
  return (
    <div className="card">
      <span className="card-title">발급 파이프라인 · Redis → DB</span>

      <div className="pf-row">
        <div className="pf-node">
          <div className="pf-icon">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#ff6a3d" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <path d="M13 2 4 14h6l-1 8 9-12h-6l1-8Z" />
            </svg>
          </div>
          <span className="pf-node-title">Lua 원자 발급</span>
          <span className="pf-metric">성공 <b>{vals.couponSuccessFmt}</b> · 잔여 <b>{vals.redisStockFmt}</b></span>
          <span className="pf-metric warn">품절 <b>{vals.soldOutFmt}</b> · 중복 <b>{vals.dupFmt}</b></span>
        </div>

        <FlowArrow label={vals.couponPerSecFmt} />

        <div className="pf-node">
          <div className="pf-icon">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#ff6a3d" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <rect x="4" y="4" width="16" height="4.5" rx="1.2" />
              <rect x="4" y="10.2" width="16" height="4.5" rx="1.2" />
              <rect x="4" y="16.4" width="16" height="4.5" rx="1.2" />
            </svg>
          </div>
          <span className="pf-node-title">Stream 적재</span>
          <span className={vals.pelCountRaw > 0 ? "pf-metric danger" : "pf-metric"}>
            PEL 대기 <b>{vals.pelCountFmt}건</b> · 최대 지연 <b>{vals.pelDelayFmt}</b>
          </span>
          <span className="pf-metric" style={{ color: vals.streamSubColor }}>
            구독 <b>{vals.activeStreamSubsFmt} / {vals.openCouponFmt}</b>
          </span>
          {vals.pelCountRaw > 0 && (
            <button type="button" onClick={onDrainPending} className="pf-drain-btn">
              PEL 강제 드레인
            </button>
          )}
        </div>

        <FlowArrow label="buffer" />

        <div className="pf-node">
          <div className="pf-icon">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#ff6a3d" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <path d="M4 5h9M4 9h9M4 13h5M14 5l6 7-6 7" />
            </svg>
          </div>
          <span className="pf-node-title">Flush 배치 묶기</span>
          <span className="pf-metric">평균 <b>{vals.batchAvgFmt}건</b> / 배치</span>
          <span className="pf-metric">상한 <b>{vals.batchMaxFmt}건</b></span>
        </div>

        <FlowArrow label={vals.dbInsertFmt} />

        <div className="pf-node">
          <div className="pf-icon">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#ff6a3d" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <ellipse cx="12" cy="6" rx="8" ry="3" />
              <path d="M4 6v12c0 1.66 3.58 3 8 3s8-1.34 8-3V6" />
              <path d="M4 12c0 1.66 3.58 3 8 3s8-1.34 8-3" />
            </svg>
          </div>
          <span className="pf-node-title">DB Batch Insert</span>
          <span className="pf-metric" style={{ color: vals.dbConnColor }}>Conn <b>{vals.dbConnFmt}</b></span>
          <span className="pf-metric">처리량 <b>{vals.dbInsertFmt}</b></span>
        </div>
      </div>

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

      <span className="section-label">발급 수 현황 (가제)</span>
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