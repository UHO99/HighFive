import { useState } from "react";
import { cancelMyCoupon, useMyCoupon, type MyCouponResponse } from "../lib/api";

const STATUS_LABEL: Record<MyCouponResponse["status"], string> = {
  ISSUED: "발급됨",
  USED: "사용됨",
  CANCELED: "취소됨",
  EXPIRED: "만료됨",
};

interface Props {
  userId: number;
  coupons: MyCouponResponse[];
  /** 사용/취소 요청이 성공한 뒤 목록을 다시 불러오도록 부모에 알린다. */
  onChanged: () => void;
}

/**
 * "사용"은 ISSUED(미사용) 상태에서만, "취소"는 USED(사용 완료) 상태에서만 뜬다 - 취소는 "사용 후
 * 취소" 전용 액션이라 미사용 상태에서는 애초에 취소가 불가능하다(그 상태에선 "사용"만 가능).
 * CANCELED/EXPIRED인 건은 둘 다 없다. 그래도 만약을 대비해 서버가 CI003(409)로 다시 막아주므로,
 * 여기 조건은 UX용이고 실제 방어는 CouponIssueServiceImpl에 있다.
 */
export function MyIssuanceList({ userId, coupons, onChanged }: Props) {
  const [pendingId, setPendingId] = useState<number | null>(null);
  const [errorByIssueId, setErrorByIssueId] = useState<Record<number, string>>({});

  const runAction = (issueId: number, action: (issueId: number, userId: number) => Promise<void>) => {
    setPendingId(issueId);
    setErrorByIssueId((prev) => {
      const next = { ...prev };
      delete next[issueId];
      return next;
    });

    action(issueId, userId)
      .then(onChanged)
      .catch((e) => {
        setErrorByIssueId((prev) => ({ ...prev, [issueId]: e instanceof Error ? e.message : "요청 실패" }));
      })
      .finally(() => setPendingId(null));
  };

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

              {c.status === "ISSUED" && (
                <div className="user-history-actions">
                  <button
                    type="button"
                    className="user-history-btn"
                    disabled={pendingId === c.issueId}
                    onClick={() => runAction(c.issueId, useMyCoupon)}
                  >
                    {pendingId === c.issueId ? "처리 중..." : "사용"}
                  </button>
                </div>
              )}

              {c.status === "USED" && (
                <div className="user-history-actions">
                  <button
                    type="button"
                    className="user-history-btn ghost"
                    disabled={pendingId === c.issueId}
                    onClick={() => runAction(c.issueId, cancelMyCoupon)}
                  >
                    {pendingId === c.issueId ? "처리 중..." : "취소"}
                  </button>
                </div>
              )}

              {errorByIssueId[c.issueId] && (
                <span className="user-history-error">{errorByIssueId[c.issueId]}</span>
              )}
            </div>
          ))}
        </div>
      )}
    </div>
  );
}
