import { useEffect, useState } from "react";
import {
  closeCoupon, createCoupon, fetchCoupons, openCoupon,
  type CouponDetail, type CouponSummary,
} from "../lib/api";

type Tab = "create" | "open" | "close";

interface Props {
  onCancel: () => void;
  onCouponCreated: (coupon: CouponDetail) => void;
  onCouponOpened: (couponId: number) => void;
  onCouponClosed: (couponId: number) => void;
}

/**
 * 쿠폰 생성/오픈/클로즈를 탭 하나짜리 다이얼로그로 모았다. 이전엔 FAB 메뉴에 세 버튼이 따로
 * 있었는데, 생성→오픈처럼 연달아 하는 흐름을 자연스럽게 이어가도록 다이얼로그를 닫지 않고
 * 탭만 바꿔서 계속 조작할 수 있게 했다. 각 탭의 실제 폼/API 호출 로직은 예전 개별 다이얼로그
 * (CouponCreateDialog/CouponOpenDialog/CouponCloseDialog)와 동일하다.
 */
export function CouponManageDialog({ onCancel, onCouponCreated, onCouponOpened, onCouponClosed }: Props) {
  const [tab, setTab] = useState<Tab>("create");

  return (
    <div className="dialog-overlay" onClick={onCancel}>
      <div className="dialog-panel" onClick={(e) => e.stopPropagation()}>
        <div className="dialog-header">
          <h2 className="dialog-title">쿠폰 관리</h2>
          <span className="dialog-subtitle">쿠폰 생성 / 오픈 / 클로즈를 한 곳에서 처리합니다.</span>
        </div>

        <div className="dialog-tabs">
          <button type="button" className={`dialog-tab ${tab === "create" ? "active" : ""}`} onClick={() => setTab("create")}>
            생성
          </button>
          <button type="button" className={`dialog-tab ${tab === "open" ? "active" : ""}`} onClick={() => setTab("open")}>
            오픈
          </button>
          <button type="button" className={`dialog-tab ${tab === "close" ? "active" : ""}`} onClick={() => setTab("close")}>
            클로즈
          </button>
        </div>

        {tab === "create" && <CreateTab onDone={onCouponCreated} />}
        {tab === "open" && <OpenTab onDone={onCouponOpened} />}
        {tab === "close" && <CloseTab onDone={onCouponClosed} />}

        <div className="dialog-actions">
          <button type="button" className="dialog-btn ghost" onClick={onCancel}>
            닫기
          </button>
        </div>
      </div>
    </div>
  );
}

/** 쿠폰 생성(READY로만 등록, Redis는 안 건드림). "즉시 오픈"은 편의 옵션일 뿐 강제는 아니다. */
function CreateTab({ onDone }: { onDone: (coupon: CouponDetail) => void }) {
  const [name, setName] = useState("부하테스트용 쿠폰");
  const [totalQuantity, setTotalQuantity] = useState(10000);
  const [openImmediately, setOpenImmediately] = useState(true);
  const [step, setStep] = useState<"idle" | "creating" | "opening">("idle");
  const [error, setError] = useState<string | null>(null);

  const busy = step !== "idle";

  const handleSubmit = async () => {
    setError(null);

    if (!name.trim()) {
      setError("쿠폰 이름을 입력하세요.");
      return;
    }
    if (!Number.isFinite(totalQuantity) || totalQuantity < 1) {
      setError("재고 수량은 1 이상이어야 합니다.");
      return;
    }

    try {
      setStep("creating");
      const coupon = await createCoupon(name.trim(), totalQuantity);

      if (!openImmediately) {
        onDone(coupon);
        setStep("idle");
        return;
      }

      setStep("opening");
      await openCoupon(coupon.id);
      onDone({ ...coupon, status: "OPEN" });
    } catch (e) {
      setError(e instanceof Error ? e.message : "쿠폰 생성 실패");
    } finally {
      setStep("idle");
    }
  };

  return (
    <>
      {error && <div className="dialog-error">{error}</div>}

      <label className="form-field">
        <span className="form-label">쿠폰 이름</span>
        <input
          className="form-input"
          value={name}
          onChange={(e) => setName(e.target.value)}
          maxLength={20}
          disabled={busy}
        />
      </label>

      <label className="form-field">
        <span className="form-label">재고 수량</span>
        <input
          className="form-input"
          type="number"
          min={1}
          value={totalQuantity}
          onChange={(e) => setTotalQuantity(Number(e.target.value))}
          disabled={busy}
        />
      </label>

      <label className="form-checkbox">
        <input
          type="checkbox"
          checked={openImmediately}
          onChange={(e) => setOpenImmediately(e.target.checked)}
          disabled={busy}
        />
        <span>생성 후 즉시 오픈 (부하테스트 바로 시작하려면 켜두세요)</span>
      </label>

      <div className="dialog-actions">
        <button type="button" className="dialog-btn primary" onClick={handleSubmit} disabled={busy}>
          {step === "idle" && (openImmediately ? "생성 + 오픈" : "생성")}
          {step === "creating" && "생성 중..."}
          {step === "opening" && "오픈 중..."}
        </button>
      </div>
    </>
  );
}

/** READY 쿠폰 중 하나를 골라 오픈한다 (Redis 재고 초기화 포함). */
function OpenTab({ onDone }: { onDone: (couponId: number) => void }) {
  const [coupons, setCoupons] = useState<CouponSummary[]>([]);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [loading, setLoading] = useState(true);
  const [opening, setOpening] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    fetchCoupons("READY")
      .then((list) => {
        if (cancelled) return;
        setCoupons(list);
        setSelectedId((current) => current ?? list[0]?.id ?? null);
      })
      .catch((e) => {
        if (!cancelled) setError(e instanceof Error ? e.message : "쿠폰 목록 조회 실패");
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const handleSubmit = async () => {
    if (selectedId === null) return;
    setError(null);
    setOpening(true);
    try {
      await openCoupon(selectedId);
      onDone(selectedId);
    } catch (e) {
      setError(e instanceof Error ? e.message : "쿠폰 오픈 실패");
    } finally {
      setOpening(false);
    }
  };

  const busy = opening;

  return (
    <>
      {error && <div className="dialog-error">{error}</div>}
      {loading && <div className="dialog-loading">READY 쿠폰 목록을 불러오는 중...</div>}
      {!loading && !error && coupons.length === 0 && (
        <div className="dialog-loading">오픈할 수 있는 READY 쿠폰이 없습니다. 먼저 생성하세요.</div>
      )}

      {coupons.length > 0 && (
        <div className="scenario-list">
          {coupons.map((coupon) => (
            <label key={coupon.id} className={`scenario-item ${selectedId === coupon.id ? "selected" : ""}`}>
              <input
                type="radio"
                name="ready-coupon"
                value={coupon.id}
                checked={selectedId === coupon.id}
                onChange={() => setSelectedId(coupon.id)}
                className="scenario-radio"
                disabled={busy}
              />
              <div className="scenario-body">
                <div className="scenario-name-row">
                  <span className="scenario-name">#{coupon.id} · {coupon.name}</span>
                </div>
                <span className="scenario-description">재고 {coupon.totalQuantity.toLocaleString()}</span>
              </div>
            </label>
          ))}
        </div>
      )}

      <div className="dialog-actions">
        <button type="button" className="dialog-btn primary" onClick={handleSubmit} disabled={busy || selectedId === null}>
          {opening ? "오픈 중..." : "오픈"}
        </button>
      </div>
    </>
  );
}

/** OPEN 쿠폰 중 하나를 골라 클로즈한다. 되돌릴 수 없는 단방향 전이라 실행 전에 확인받는다. */
function CloseTab({ onDone }: { onDone: (couponId: number) => void }) {
  const [coupons, setCoupons] = useState<CouponSummary[]>([]);
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const [loading, setLoading] = useState(true);
  const [closing, setClosing] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    fetchCoupons("OPEN")
      .then((list) => {
        if (cancelled) return;
        setCoupons(list);
        setSelectedId((current) => current ?? list[0]?.id ?? null);
      })
      .catch((e) => {
        if (!cancelled) setError(e instanceof Error ? e.message : "쿠폰 목록 조회 실패");
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const handleSubmit = async () => {
    if (selectedId === null) return;

    const confirmed = window.confirm(
      `쿠폰 #${selectedId}을 클로즈합니다.\n` +
      `READY로 되돌릴 수 없는 단방향 전이입니다. 계속할까요?`
    );
    if (!confirmed) return;

    setError(null);
    setClosing(true);
    try {
      await closeCoupon(selectedId);
      onDone(selectedId);
    } catch (e) {
      setError(e instanceof Error ? e.message : "쿠폰 클로즈 실패");
    } finally {
      setClosing(false);
    }
  };

  const busy = closing;

  return (
    <>
      {error && <div className="dialog-error">{error}</div>}
      {loading && <div className="dialog-loading">OPEN 쿠폰 목록을 불러오는 중...</div>}
      {!loading && !error && coupons.length === 0 && (
        <div className="dialog-loading">클로즈할 수 있는 OPEN 쿠폰이 없습니다.</div>
      )}

      {coupons.length > 0 && (
        <div className="scenario-list">
          {coupons.map((coupon) => (
            <label key={coupon.id} className={`scenario-item ${selectedId === coupon.id ? "selected" : ""}`}>
              <input
                type="radio"
                name="open-coupon"
                value={coupon.id}
                checked={selectedId === coupon.id}
                onChange={() => setSelectedId(coupon.id)}
                className="scenario-radio"
                disabled={busy}
              />
              <div className="scenario-body">
                <div className="scenario-name-row">
                  <span className="scenario-name">#{coupon.id} · {coupon.name}</span>
                </div>
                <span className="scenario-description">재고 {coupon.totalQuantity.toLocaleString()}</span>
              </div>
            </label>
          ))}
        </div>
      )}

      <div className="dialog-actions">
        <button type="button" className="dialog-btn primary" onClick={handleSubmit} disabled={busy || selectedId === null}>
          {closing ? "클로즈 중..." : "클로즈"}
        </button>
      </div>
    </>
  );
}
