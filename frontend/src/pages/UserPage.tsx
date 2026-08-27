import { useCallback, useEffect, useState } from "react";
import { MyIssuanceList } from "../components/MyIssuanceList";
import { StockRing } from "../components/StockRing";
import {
  fetchCoupons, fetchMonitoringDashboard, fetchMyCoupons, requestCouponIssue,
  type CouponSummary, type MonitoringDashboardResponse, type MyCouponResponse,
} from "../lib/api";

const POLL_INTERVAL_MS = 2000;

export function UserPage() {
  const [userId, setUserId] = useState<number | null>(null);

  if (userId === null) {
    return <UserLoginScreen onLogin={setUserId} />;
  }
  return <UserPanel userId={userId} onLogout={() => setUserId(null)} />;
}

function UserLoginScreen({ onLogin }: { onLogin: (userId: number) => void }) {
  const [input, setInput] = useState("");

  const handleSubmit = () => {
    const id = Number(input);
    if (!Number.isFinite(id) || id < 1) return;
    onLogin(Math.trunc(id));
  };

  return (
    <div className="user-shell user-shell-center">
      <div className="user-login-card">
        <h1 className="user-login-title">쿠폰 받기</h1>
        <p className="user-login-subtitle">본인 User ID를 입력해 주세요.</p>
        <input
          className="form-input"
          type="number"
          min={1}
          value={input}
          onChange={(e) => setInput(e.target.value)}
          onKeyDown={(e) => e.key === "Enter" && handleSubmit()}
          placeholder="예: 12345"
          autoFocus
        />
        <button type="button" className="dialog-btn primary" onClick={handleSubmit} disabled={!input}>
          입장
        </button>
      </div>
    </div>
  );
}

function UserPanel({ userId, onLogout }: { userId: number; onLogout: () => void }) {
  const [openCoupons, setOpenCoupons] = useState<CouponSummary[]>([]);
  const [selectedCouponId, setSelectedCouponId] = useState<number | null>(null);
  const [monitoring, setMonitoring] = useState<MonitoringDashboardResponse | null>(null);
  const [myCoupons, setMyCoupons] = useState<MyCouponResponse[]>([]);
  const [requesting, setRequesting] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

  const refreshMyCoupons = useCallback(() => {
    fetchMyCoupons(userId).then(setMyCoupons).catch(() => {
      // 조회 실패는 목록을 마지막으로 알던 상태로 둔다.
    });
  }, [userId]);

  useEffect(() => {
    refreshMyCoupons();
  }, [refreshMyCoupons]);

  // OPEN 쿠폰 전체 목록을 폴링한다 - 지금 고른 쿠폰이 클로즈돼서 목록에서 사라지면 자동으로
  // 다른 OPEN 쿠폰으로 넘어간다. 관리자 헤더와 같은 API를 쓴다.
  useEffect(() => {
    let cancelled = false;
    const load = () => {
      fetchCoupons("OPEN")
        .then((list) => {
          if (cancelled) return;
          setOpenCoupons(list);
          setSelectedCouponId((current) =>
            current !== null && list.some((c) => c.id === current) ? current : (list[0]?.id ?? null)
          );
        })
        .catch(() => {
          // 조회 실패는 마지막으로 알던 목록을 유지한다.
        });
    };
    load();
    const timer = window.setInterval(load, POLL_INTERVAL_MS);
    return () => {
      cancelled = true;
      window.clearInterval(timer);
    };
  }, []);

  // 변경 후 - 폴링 제거, 쿠폰을 선택할 때(또는 바뀔 때)만 1회 조회
  const refreshMonitoring = useCallback(() => {
    if (selectedCouponId === null) {
      setMonitoring(null);
      return;
    }
    fetchMonitoringDashboard(selectedCouponId)
      .then(setMonitoring)
      .catch(() => {
        // 조회 실패는 마지막으로 알던 값을 유지한다.
      });
  }, [selectedCouponId]);

  useEffect(() => {
    refreshMonitoring();
  }, [refreshMonitoring]);

  const selectedCoupon = openCoupons.find((c) => c.id === selectedCouponId) ?? null;

  // 변경 후
  const handleRequestIssue = () => {
    if (!selectedCoupon) return;
    setRequesting(true);
    setMessage(null);
    requestCouponIssue(selectedCoupon.id, userId)
      .then(() => {
        setMessage("발급 신청 완료! 잠시 후 이력에 반영됩니다.");
        window.setTimeout(refreshMyCoupons, 1500);
      })
      .catch((e) => setMessage(`발급 실패: ${e.message}`))
      .finally(() => {
        setRequesting(false);
        refreshMonitoring();   // 추가 - 성공/실패 무관하게, 방금 내 시도로 재고가 바뀌었을 수 있으니 재조회
      });
  };

  return (
    <div className="user-shell">
      <div className="user-topbar">
        <span className="user-id-badge">User #{userId}</span>
        <button type="button" className="dialog-btn ghost" onClick={onLogout}>
          다른 사용자로 전환
        </button>
      </div>

      <div className="user-main">
        <div className="user-ring-panel">
          {openCoupons.length === 0 ? (
            <span className="user-empty">지금 오픈된 쿠폰이 없습니다.</span>
          ) : (
            <>
              <div className="coupon-chip-row">
                {openCoupons.map((c) => (
                  <button
                    key={c.id}
                    type="button"
                    className={`coupon-chip ${c.id === selectedCouponId ? "active" : ""}`}
                    onClick={() => setSelectedCouponId(c.id)}
                  >
                    #{c.id} · {c.name}
                  </button>
                ))}
              </div>

              {selectedCoupon && monitoring && (
                <>
                  <StockRing
                    remaining={monitoring.stockStatus.redisStockRemaining}
                    total={monitoring.stockStatus.redisStockTotal}
                  />
                  <span className="user-coupon-name">{selectedCoupon.name}</span>
                  <button type="button" className="dialog-btn primary" onClick={handleRequestIssue} disabled={requesting}>
                    {requesting ? "신청 중..." : "쿠폰 받기"}
                  </button>
                  {message && <span className="user-message">{message}</span>}
                </>
              )}
            </>
          )}
        </div>

        <MyIssuanceList userId={userId} coupons={myCoupons} onChanged={refreshMyCoupons} />
      </div>
    </div>
  );
}
