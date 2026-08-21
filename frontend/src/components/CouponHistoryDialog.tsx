import { useMemo } from "react";

/**
 * 관리자용 "쿠폰 이력 조회" - Mock 데이터다. 전체 발급 이력을 페이지네이션해서 보여주는 실제 API는
 * 아직 없다(coupon_issue가 수백만 건 단위라 별도 설계가 필요함) - 화면에도 그 사실을 명시한다.
 */
interface MockRow {
  issueId: number;
  userId: number;
  couponName: string;
  status: "ISSUED" | "USED" | "CANCELED" | "EXPIRED";
  issuedAt: string;
}

const STATUSES: MockRow["status"][] = ["ISSUED", "USED", "CANCELED", "EXPIRED"];
const STATUS_LABEL: Record<MockRow["status"], string> = {
  ISSUED: "발급됨",
  USED: "사용됨",
  CANCELED: "취소됨",
  EXPIRED: "만료됨",
};

function generateMockRows(count: number): MockRow[] {
  const now = Date.now();
  return Array.from({ length: count }, (_, i) => ({
    issueId: 100_000 - i,
    userId: Math.floor(Math.random() * 1_000_000) + 1,
    couponName: `이벤트쿠폰_${Math.floor(Math.random() * 30) + 1}`,
    status: STATUSES[Math.floor(Math.random() * STATUSES.length)],
    issuedAt: new Date(now - i * 1000 * (Math.floor(Math.random() * 60) + 1)).toISOString(),
  }));
}

interface Props {
  onClose: () => void;
}

export function CouponHistoryDialog({ onClose }: Props) {
  const rows = useMemo(() => generateMockRows(30), []);

  return (
    <div className="dialog-overlay" onClick={onClose}>
      <div className="dialog-panel dialog-panel-wide" onClick={(e) => e.stopPropagation()}>
        <div className="dialog-header">
          <div className="dialog-title-row">
            <h2 className="dialog-title">쿠폰 발급 이력 조회</h2>
            <span className="mock-badge">Mock 데이터 - 실제 API 미구현</span>
          </div>
          <span className="dialog-subtitle">
            전체 발급 이력 조회는 페이지네이션 등 별도 설계가 필요해 아직 실 데이터가 아닙니다.
          </span>
        </div>

        <div className="history-table-wrap">
          <table className="history-table">
            <thead>
              <tr>
                <th>이력 ID</th>
                <th>User ID</th>
                <th>쿠폰</th>
                <th>상태</th>
                <th>발급 시각</th>
              </tr>
            </thead>
            <tbody>
              {rows.map((r) => (
                <tr key={r.issueId}>
                  <td>{r.issueId}</td>
                  <td>{r.userId}</td>
                  <td>{r.couponName}</td>
                  <td>{STATUS_LABEL[r.status]}</td>
                  <td>{new Date(r.issuedAt).toLocaleString("ko-KR")}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        <div className="dialog-actions">
          <button type="button" className="dialog-btn primary" onClick={onClose}>
            닫기
          </button>
        </div>
      </div>
    </div>
  );
}
