import { useState } from "react";
import type { DashboardVals } from "../hooks/useMonitoringDashboard";
import type { CouponDetail, CouponSummary } from "../lib/api";
import type { K6Scenario } from "../lib/scenarios";
import { CouponHistoryDialog } from "./CouponHistoryDialog";
import { CouponManageDialog } from "./CouponManageDialog";
import { ScenarioDialog } from "./ScenarioDialog";

interface Props {
  vals: DashboardVals;
  coupons: CouponSummary[];
  couponId: number;
  /** 더미데이터가 한 번이라도 적재됐는지(회원 수 > 0) - 아니면 발급/테스트 등 나머지 기능이 의미가 없다. */
  dataReady: boolean;
  /** 지금 적재가 진행 중인지 - "데이터 적재" 버튼 중복 클릭만 막는다. */
  dataLoading: boolean;
  onStartTest: (scenario: K6Scenario, couponId: number) => void;
  onStopTest: () => void;
  onLoadData: () => void;
  onResetMetrics: () => void;
  onCouponCreated: (coupon: CouponDetail) => void;
  onCouponOpened: (couponId: number) => void;
  onCouponClosed: (couponId: number) => void;
}

export function FloatingActionMenu({
  vals, coupons, couponId, dataReady, dataLoading, onStartTest, onStopTest, onLoadData, onResetMetrics,
  onCouponCreated, onCouponOpened, onCouponClosed,
}: Props) {
  const [menuOpen, setMenuOpen] = useState(false);
  const [dialogOpen, setDialogOpen] = useState(false);
  const [couponManageDialogOpen, setCouponManageDialogOpen] = useState(false);
  const [couponHistoryDialogOpen, setCouponHistoryDialogOpen] = useState(false);

  const handleTestButtonClick = () => {
    setMenuOpen(false);
    if (vals.testRunning) {
      onStopTest();
    } else {
      setDialogOpen(true);
    }
  };

  // scenarioFile은 서버 상태(K6TestService)에서 그대로 온다 - 새로고침해도 실행 중이면 다시 뜬다.
  const testButtonText = vals.testRunning
    ? `${vals.testButtonLabel} · ${vals.scenarioFile ? `${vals.scenarioFile} · ` : ""}${vals.elapsedText}`
    : vals.testButtonLabel;

  // 데이터 적재 전이면 쿠폰/테스트 관련 기능은 어차피 의미가 없다(쿠폰 생성 자체는 되지만 발급은
  // 유저가 없어서 다 실패함) - 실행 중인 테스트를 멈추는 것만은 항상 허용한다.
  const otherActionsDisabled = !dataReady;
  const testButtonDisabled = !vals.testRunning && !dataReady;
  const lockedTitle = "먼저 데이터 적재를 완료하세요";

  return (
    <>
      <div className="fab-container">
        <button
          type="button"
          aria-label="메뉴"
          aria-expanded={menuOpen}
          className={`fab-main ${menuOpen ? "open" : ""}`}
          onClick={() => setMenuOpen((v) => !v)}
        >
          <span className="fab-icon">+</span>
          {vals.testRunning && <span className="fab-badge" />}
        </button>

        {menuOpen && (
          <div className="fab-menu">
            <button
              type="button"
              className="fab-action"
              onClick={() => {
                onLoadData();
                setMenuOpen(false);
              }}
              disabled={dataLoading}
            >
              {dataLoading ? "데이터 적재 중..." : "데이터 적재"}
            </button>
            <button
              type="button"
              className="fab-action"
              onClick={() => {
                setCouponManageDialogOpen(true);
                setMenuOpen(false);
              }}
              disabled={otherActionsDisabled}
              title={otherActionsDisabled ? lockedTitle : undefined}
            >
              쿠폰 관리
            </button>
            <button
              type="button"
              className="fab-action"
              onClick={() => {
                setCouponHistoryDialogOpen(true);
                setMenuOpen(false);
              }}
              disabled={otherActionsDisabled}
              title={otherActionsDisabled ? lockedTitle : undefined}
            >
              쿠폰 이력 조회
            </button>
            <button
              type="button"
              className="fab-action"
              onClick={() => {
                onResetMetrics();
                setMenuOpen(false);
              }}
              disabled={otherActionsDisabled}
              title={otherActionsDisabled ? lockedTitle : undefined}
            >
              지표 초기화
            </button>
            <button
              type="button"
              className={`fab-action primary ${vals.testRunning ? "running" : "idle"}`}
              onClick={handleTestButtonClick}
              disabled={testButtonDisabled}
              title={testButtonDisabled ? lockedTitle : undefined}
            >
              {testButtonText}
            </button>
          </div>
        )}
      </div>

      {dialogOpen && (
        <ScenarioDialog
          coupons={coupons}
          defaultCouponId={couponId}
          onCancel={() => setDialogOpen(false)}
          onConfirm={(scenario, targetCouponId) => {
            setDialogOpen(false);
            onStartTest(scenario, targetCouponId);
          }}
        />
      )}

      {couponManageDialogOpen && (
        <CouponManageDialog
          onCancel={() => setCouponManageDialogOpen(false)}
          onCouponCreated={onCouponCreated}
          onCouponOpened={onCouponOpened}
          onCouponClosed={onCouponClosed}
        />
      )}

      {couponHistoryDialogOpen && (
        <CouponHistoryDialog onClose={() => setCouponHistoryDialogOpen(false)} />
      )}
    </>
  );
}
