import { useEffect, useState } from "react";
import { fetchK6Scenarios, type CouponSummary, type K6RunOptions } from "../lib/api";
import type { K6Scenario } from "../lib/scenarios";

interface Props {
  /** OPEN 쿠폰 목록(대시보드 헤더와 같은 소스) - 여기서 실행 대상을 명시적으로 고른다. */
  coupons: CouponSummary[];
  defaultCouponId: number;
  onCancel: () => void;
  onConfirm: (scenario: K6Scenario, couponId: number, options: K6RunOptions) => void;
}

export function ScenarioDialog({ coupons, defaultCouponId, onCancel, onConfirm }: Props) {
  const [scenarios, setScenarios] = useState<K6Scenario[]>([]);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [selectedCouponId, setSelectedCouponId] = useState<number | null>(
    coupons.some((c) => c.id === defaultCouponId) ? defaultCouponId : (coupons[0]?.id ?? null)
  );
  const [error, setError] = useState<string | null>(null);
  const [maxVusInput, setMaxVusInput] = useState(20000);
  // advanced 시나리오(main_test.js) 전용 - 기본값은 스크립트 기본값과 맞춰둔다.
  const [requestSizeMode, setRequestSizeMode] = useState<"ratio" | "count">("ratio");
  const [requestRatioInput, setRequestRatioInput] = useState(2);
  const [requestCountInput, setRequestCountInput] = useState(20000);
  // 기본값은 평가 조건(유저 20,000명 / 램프업 60초)에 맞춰둔다 - 별도로 안 건드려도 그 조건으로 돌아간다.
  const [arrivalInput, setArrivalInput] = useState<"burst" | "even" | "ramp">("ramp");
  const [durationInput, setDurationInput] = useState(60);
  const [spamRatioInput, setSpamRatioInput] = useState(0);
  const [spamClicksInput, setSpamClicksInput] = useState(3);

  const selectedScenario = scenarios.find((s) => s.id === selectedId);
  // burst만 VU 수가 곧 부하다. even/ramp는 도착량으로 부하를 정하므로 이 값은 상한 역할만 한다.
  const vusIsCap = selectedScenario?.advanced === true && arrivalInput !== "burst";
  // 재고는 사용자가 또 입력할 값이 아니다 - 이미 선택한 쿠폰의 실제 재고를 그대로 쓴다.
  // k6 스크립트에서 STOCK은 "쿠폰 재고를 설정"하는 게 아니라 "요청을 몇 번 보낼지"(STOCK×2) 계산용이라,
  // 실제 재고와 다른 값을 넣으면 결과가 왜곡된다(둘이 따로 놀면 안 됨).
  const selectedCoupon = coupons.find((c) => c.id === selectedCouponId);

  // 지정 방식에 따라 실제 전체 요청 수를 미리 계산해서 보여준다.
  const estimatedIterations = requestSizeMode === "count"
    ? requestCountInput
    : (selectedCoupon ? selectedCoupon.totalQuantity * requestRatioInput : null);

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
      <div className="dialog-panel dialog-panel-xl" onClick={(e) => e.stopPropagation()}>
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
                {scenario.guides?.length > 0 && (
                  <ul className="scenario-guides">
                    {scenario.guides.map((g) => (
                      <li key={g}>{g}</li>
                    ))}
                  </ul>
                )}
                <div className="scenario-meta">
                  {scenario.rampUp && <span>램프업 {scenario.rampUp}</span>}
                  {scenario.hold && <span>유지 {scenario.hold}</span>}
                  {scenario.targetVus && <span>{scenario.targetVus}</span>}
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
              <span className="form-label">{vusIsCap ? "동시접속 상한" : "동시접속"}</span>
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

        {selectedScenario?.advanced && (
          <>
            {/* 위 줄은 규모(몇 명이 몇 번 누르나), 아래 줄은 유입(언제 도착하나).
                조건부로 나타나는 칸은 항상 오른쪽 열에 놓아 켜고 꺼도 왼쪽 칸이 안 흔들린다. */}
            <div className="scale-input-row">
              <label className="form-field">
                <span className="form-label">요청 수 지정 방식</span>
                <select
                  className="form-input"
                  value={requestSizeMode}
                  onChange={(e) => setRequestSizeMode(e.target.value as "ratio" | "count")}
                >
                  <option value="ratio">배수로 지정 (재고 × N)</option>
                  <option value="count">전체 요청 수 직접 입력</option>
                </select>
              </label>

              {requestSizeMode === "ratio" ? (
                <label className="form-field">
                  <span className="form-label">요청 배수 (재고 × N명이 몰림)</span>
                  <input
                    type="number"
                    min={1}
                    className="form-input"
                    value={requestRatioInput}
                    onChange={(e) => setRequestRatioInput(Number(e.target.value))}
                  />
                </label>
              ) : (
                <label className="form-field">
                  <span className="form-label">전체 요청 수 (직접 입력)</span>
                  <input
                    type="number"
                    min={1}
                    className="form-input"
                    value={requestCountInput}
                    onChange={(e) => setRequestCountInput(Number(e.target.value))}
                  />
                </label>
              )}

              {estimatedIterations !== null && (
                <span style={{ fontSize: "12px", color: "#8b8fa3" }}>
                  전체 요청 수 예상: <b>{estimatedIterations.toLocaleString("ko-KR")}</b>건
                </span>
              )}
              <label className="form-field">
                <span className="form-label">연타 유저 비율 (0~1)</span>
                <input
                  type="number"
                  min={0}
                  max={1}
                  step={0.1}
                  className="form-input"
                  value={spamRatioInput}
                  onChange={(e) => setSpamRatioInput(Number(e.target.value))}
                />
              </label>
            </div>

            {spamRatioInput > 0 && (
              <div className="scale-input-row">
                <label className="form-field form-field-under-right">
                  <span className="form-label">연타 횟수</span>
                  <input
                    type="number"
                    min={2}
                    className="form-input"
                    value={spamClicksInput}
                    onChange={(e) => setSpamClicksInput(Number(e.target.value))}
                  />
                </label>
              </div>
            )}

            <div className="scale-input-row">
              <label className="form-field">
                <span className="form-label">유입 방식</span>
                <select
                  className="form-input"
                  value={arrivalInput}
                  onChange={(e) => {
                    const next = e.target.value as "burst" | "even" | "ramp";
                    setArrivalInput(next);
                    // 평가 조건이 램프업 60초라, 모드를 바꾸면 그쪽 기본값으로 같이 옮겨준다.
                    setDurationInput(next === "ramp" ? 60 : 10);
                  }}
                >
                  {/* 평가 조건인 램프업이 기본이자 첫 번째 - 아래로 갈수록 같은 인원을 더 압축해서 던진다. */}
                  <option value="ramp">램프업 (평가 조건)</option>
                  <option value="even">시간에 걸쳐 (균등)</option>
                  <option value="burst">한꺼번에 (오픈 직후)</option>
                </select>
              </label>
              {arrivalInput !== "burst" && (
                <label className="form-field">
                  <span className="form-label">{arrivalInput === "ramp" ? "램프업 시간(초)" : "유입 시간(초)"}</span>
                  <input
                    type="number"
                    min={1}
                    className="form-input"
                    value={durationInput}
                    onChange={(e) => setDurationInput(Number(e.target.value))}
                  />
                </label>
              )}
            </div>
          </>
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

              const options: K6RunOptions = {};
              if (scenario.configurable) {
                options.stock = selectedCoupon?.totalQuantity;
                options.maxVus = maxVusInput;
              }
              if (scenario.advanced) {
                // 변경 후
                if (requestSizeMode === "count") {
                  options.requestCount = requestCountInput;
                } else {
                  options.requestRatio = requestRatioInput;
                }
                options.arrival = arrivalInput;
                if (arrivalInput !== "burst") options.duration = durationInput;
                options.spamRatio = spamRatioInput;
                if (spamRatioInput > 0) options.spamClicks = spamClicksInput;
              }
              onConfirm(scenario, selectedCouponId, options);
            }}
          >
            실행
          </button>
        </div>
      </div>
    </div>
  );
}
