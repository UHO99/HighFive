import { useMemo } from "react";
import { formatMs } from "../lib/format";

/**
 * "재고 · Redis" 카드 옆에 나란히 두는 발급 이력 카드 - 지금은 UI 레이아웃만 잡아둔 상태다.
 * 백엔드에 아직 선착순 순번/발급 간격을 내려주는 조회 API가 없어서(서버 쪽 선착순 발급 로직 자체와는
 * 별개), 실 데이터 연동 전까지는 목데이터로 모양만 보여준다. 실제 API가 생기면 이 파일의 mock 생성
 * 부분만 fetch 훅으로 교체하면 되고, 아래 표는 그대로 둬도 된다.
 */
type MockStatus = "ISSUED" | "USED" | "CANCELED" | "EXPIRED";

interface MockRow {
  issueId: number;
  rank: number;
  userId: number;
  status: MockStatus;
  issuedAt: string;
  delayMs: number | null;
}

const STATUSES: MockStatus[] = ["ISSUED", "USED", "CANCELED", "EXPIRED"];
const STATUS_LABEL: Record<MockStatus, string> = {
  ISSUED: "정상 발급",
  USED: "사용됨",
  CANCELED: "취소됨",
  EXPIRED: "만료됨",
};
const STATUS_COLOR: Record<MockStatus, string> = {
  ISSUED: "#16a34a",
  USED: "#5b6bd6",
  CANCELED: "#dc2626",
  EXPIRED: "#8b8fa3",
};

function generateMockRows(count: number, topRank: number): MockRow[] {
  const now = Date.now();
  let cursor = now;
  return Array.from({ length: count }, (_, i) => {
    const delayMs = i === count - 1 ? null : Math.floor(Math.random() * 800) + 40;
    if (i > 0 && delayMs !== null) cursor -= delayMs;
    return {
      issueId: 100_000 - i,
      rank: topRank - i,
      userId: Math.floor(Math.random() * 1_000_000) + 1,
      status: STATUSES[Math.floor(Math.random() * STATUSES.length)],
      issuedAt: new Date(cursor).toISOString(),
      delayMs,
    };
  });
}

export function CouponIssueHistoryCard() {
  const rows = useMemo(() => generateMockRows(20, 8842), []);

  return (
    <div className="card card-wide">
      <div className="card-title-row">
        <span className="card-title card-title-tight">쿠폰 발급 이력 · 선착순</span>
        <span className="mock-badge">Mock 데이터 - 실제 API 미구현</span>
      </div>

      <div className="history-table-wrap history-table-wrap-compact">
        <table className="history-table history-table-compact">
          <thead>
            <tr>
              <th>건</th>
              <th>순번</th>
              <th>유저 / 사유</th>
              <th>발급시각</th>
              <th>지연</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((r, i) => (
              <tr key={r.issueId}>
                <td>{i + 1}</td>
                <td className="history-cell-mono">#{r.rank}</td>
                <td>
                  유저 {r.userId} ·{" "}
                  <span style={{ color: STATUS_COLOR[r.status] }}>{STATUS_LABEL[r.status]}</span>
                </td>
                <td className="history-cell-mono">{new Date(r.issuedAt).toLocaleTimeString("ko-KR")}</td>
                <td className="history-cell-mono">{r.delayMs === null ? "-" : formatMs(r.delayMs)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </div>
  );
}
