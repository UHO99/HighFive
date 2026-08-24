import { useEffect, useState } from "react";
import { fetchK6Scenarios, type CouponSummary } from "../lib/api";
import type { K6Scenario } from "../lib/scenarios";

interface Props {
  /** OPEN 쿠폰 목록(대시보드 헤더와 같은 소스) - 여기서 실행 대상을 명시적으로 고른다. */
  coupons: CouponSummary[];
  defaultCouponId: number;
  onCancel: () => void;
  onConfirm: (scenario: K6Scenario, couponId: number, stock?: number, maxVus?: number) => void;
}

export function ScenarioDialog({ coupons, defaultCouponId, onCancel, onConfirm }: Props) {
  const [scenarios, setScenarios] = useState<K6Scenario[]>([]);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [selectedCouponId, setSelectedCouponId] = useState<number | null>(
    coupons.some((c) => c.id === defaultCouponId) ? defaultCouponId : (coupons[0]?.id ?? null)
  );
  const [error, setError] = useState<string | null>(null);
  const [maxVusInput, setMaxVusInput] = useState(50);

  const selectedScenario = scenarios.find((s) => s.id === selectedId);
  // 재고는 사용자가 또 입력할 값이 아니다 - 이미 선택한 쿠폰의 실제 재고를 그대로 쓴다.
  // k6 스크립트에서 STOCK은 "쿠폰 재고를 설정"하는 게 아니라 "요청을 몇 번 보낼지"(STOCK×2) 계산용이라,
  // 실제 재고와 다른 값을 넣으면 결과가 왜곡된다(둘이 따로 놀면 안 됨).
  const selectedCoupon = coupons.find((c) => c.id === selectedCouponId);

  useEffect(() => {
    let cancelled = false;
    fetchK6Scenarios()
      .then((list) => {
        if (cancelled) return;
        setScenarios(list);
        setSelectedId((current) => current ?? list[0]?.id ?? null);
      })
      .catch((e) => {
        if (!cancelled) setError(e instanceof Error ? e.message : "시나리오 목록 조회 실패");
      });
    return () => {
      cancelled = true;
    };
  }, []);

  return (
    <div className="dialog-overlay" onClick={onCancel}>
      <div className="dialog-panel" onClick={(e) => e.stopPropagation()}>
        <div className="dialog-header">
          <h2 className="dialog-title">K6 부하테스트 시나리오 선택</h2>
          <span className="dialog-subtitle">backend/k6 에 있는 스크립트와 대상 쿠폰을 골라 실행합니다.</span>
        </div>

        {error && <div className="dialog-error">{error}</div>}

        <span className="form-label">대상 쿠폰 (OPEN만)</span>
        {coupons.length === 0 ? (
          <div className="dialog-error">
            오픈된 쿠폰이 없습니다. 먼저 "쿠폰 생성"/"쿠폰 오픈"으로 대상을 준비하세요.
          </div>
        ) : (
          <div className="coupon-chip-row">
            {coupons.map((c) => (
              <button
                key={c.id}
                type="button"
                className={`coupon-chip ${selectedCouponId === c.id ? "active" : ""}`}
                onClick={() => setSelectedCouponId(c.id)}
              >
                #{c.id} · {c.name}
              </button>
            ))}
          </div>
        )}

        {!error && scenarios.length === 0 && <div className="dialog-loading">시나리오 목록을 불러오는 중...</div>}

        <div className="scenario-list">
          {scenarios.map((scenario) => (
            <label
              key={scenario.id}
              className={`scenario-item ${selectedId === scenario.id ? "selected" : ""}`}
            >
              <input
                type="radio"
                name="scenario"
                value={scenario.id}
                checked={selectedId === scenario.id}
                onChange={() => setSelectedId(scenario.id)}
                className="scenario-radio"
              />
              <div className="scenario-body">
                <div className="scenario-name-row">
                  <span className="scenario-name">{scenario.name}</span>
                  <span className="scenario-file">{scenario.file}</span>
                </div>
                <span className="scenario-description">{scenario.description}</span>
                <div className="scenario-meta">
                  <span>램프업 {scenario.rampUp}</span>
                  <span>유지 {scenario.hold}</span>
                  <span>{scenario.targetVus}</span>
                </div>
              </div>
            </label>
          ))}
        </div>

        {selectedScenario?.configurable && (
          <div className="scale-input-row">
            <div className="form-field">
              <span className="form-label">재고 (선택한 쿠폰 기준)</span>
              <span className="form-input" style={{ display: "inline-block" }}>
                {selectedCoupon ? selectedCoupon.totalQuantity.toLocaleString() : "-"}
              </span>
            </div>
            <label className="form-field">
              <span className="form-label">동시접속</span>
              <input
                type="number"
                min={1}
                className="form-input"
                value={maxVusInput}
                onChange={(e) => setMaxVusInput(Number(e.target.value))}
              />
            </label>
          </div>
        )}

        <div className="dialog-actions">
          <button type="button" className="dialog-btn ghost" onClick={onCancel}>
            취소
          </button>
          <button
            type="button"
            className="dialog-btn primary"
            disabled={!selectedId || selectedCouponId === null}
            onClick={() => {
              const scenario = scenarios.find((s) => s.id === selectedId);
              if (!scenario || selectedCouponId === null) return;
              if (scenario.configurable) {
                onConfirm(scenario, selectedCouponId, selectedCoupon?.totalQuantity, maxVusInput);
              } else {
                onConfirm(scenario, selectedCouponId);
              }
            }}
          >
            실행
          </button>
        </div>
      </div>
    </div>
  );
}
