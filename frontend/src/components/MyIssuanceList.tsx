import type { MyCouponResponse } from "../lib/api";

const STATUS_LABEL: Record<MyCouponResponse["status"], string> = {
  ISSUED: "발급됨",
  USED: "사용됨",
  CANCELED: "취소됨",
  EXPIRED: "만료됨",
};

interface Props {
  coupons: MyCouponResponse[];
}

export function MyIssuanceList({ coupons }: Props) {
  return (
    <div className="user-history-panel">
      <span className="section-label">내 발급 이력</span>
      {coupons.length === 0 ? (
        <span className="tile-label-md">아직 발급받은 쿠폰이 없습니다.</span>
      ) : (
        <div className="user-history-list">
          {coupons.map((c) => (
            <div key={c.issueId} className="user-history-item">
              <div className="user-history-item-main">
                <span className="user-history-coupon-name">{c.couponName}</span>
                <span className="user-history-rank">#{c.rank}번째 발급</span>
              </div>
              <div className="user-history-item-meta">
                <span>이력 ID {c.issueId}</span>
                <span>{STATUS_LABEL[c.status]}</span>
                <span>{new Date(c.issuedAt).toLocaleString("ko-KR")}</span>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
