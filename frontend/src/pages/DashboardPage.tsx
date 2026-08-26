import { useCallback, useEffect, useRef, useState } from "react";
import { Sidebar } from "../components/Sidebar";
import { DashboardHeader } from "../components/DashboardHeader";
import { ServerResourceCard } from "../components/ServerResourceCard";
import { ApiResponseCard } from "../components/ApiResponseCard";
import { CouponStatusCard } from "../components/CouponStatusCard";
import { CouponPipelineCard } from "../components/CouponPipelineCard";
import { CouponIssueHistoryCard } from "../components/CouponIssueHistoryCard";
import { ConsistencyStatusCard } from "../components/ConsistencyStatusCard";
import { CouponHistoryDialog } from "../components/CouponHistoryDialog";
import { FloatingActionMenu } from "../components/FloatingActionMenu";
import { useMonitoringDashboard } from "../hooks/useMonitoringDashboard";
import {
  drainPendingStream, fetchCoupons, fetchDummyDataCounts, fetchDummyDataStatus, loadDummyData, resetMonitoringMetrics,
  type CouponDetail, type CouponSummary, type DummyDataCounts, type DummyDataStatus, type K6RunOptions,
} from "../lib/api";
import type { K6Scenario } from "../lib/scenarios";

const DEFAULT_COUPON_ID = 1;
const POLL_INTERVAL_MS = 10_000;
const DUMMY_STATUS_POLL_INTERVAL_MS = 2000;

const IDLE_DUMMY_STATUS: DummyDataStatus = { loading: false, startedAt: null, finishedAt: null, before: null, lastResult: null, lastError: null };

type DbCounts = Pick<DummyDataCounts, "userCount" | "couponCount" | "couponIssueCount">;
type ReloadTiming = Pick<DummyDataCounts, "userLoadMs" | "couponIssueLoadMs" | "totalMs">;

export function DashboardPage() {
  const [coupons, setCoupons] = useState<CouponSummary[]>([]);
  const [couponId, setCouponId] = useState(DEFAULT_COUPON_ID);
  const { vals, startTest, stopTest, refresh, refreshing, error, couponMissing } = useMonitoringDashboard(couponId);
  const [couponHistoryDialogOpen, setCouponHistoryDialogOpen] = useState(false);
  const [dbCounts, setDbCounts] = useState<DbCounts | null>(null);
  // 재적재 시점의 소요시간은 폴링으로 안 지워진다 - GET counts는 이 값을 모르니(null) 마지막으로
  // 성공한 재적재 값을 그대로 들고 있는다.
  const [reloadTiming, setReloadTiming] = useState<ReloadTiming | null>(null);
  const [dummyStatus, setDummyStatus] = useState<DummyDataStatus>(IDLE_DUMMY_STATUS);
  // 이미 알림/카운트 반영을 한 완료 시각을 기억해서, 폴링마다 같은 완료를 중복 처리하지 않는다.
  const lastHandledFinishRef = useRef<string | null>(null);

  // DB 건수는 항상 실제 DB 상태로 수렴하도록 주기적으로 조회한다 - 백엔드가 재시작되면
  // dummyStatus.lastResult는 사라지지만(메모리 상태), DB 자체는 그대로 남아있으므로 이 폴링이
  // 진짜 기준이다. dataReady(FAB 잠금 여부)도 이 값으로 판단한다.
  useEffect(() => {
    const load = () => {
      fetchDummyDataCounts()
        .then((c) => setDbCounts({ userCount: c.userCount, couponCount: c.couponCount, couponIssueCount: c.couponIssueCount }))
        .catch(() => {
          // 조회 실패는 카드가 마지막으로 알던 값을 그대로 유지하게 둔다.
        });
    };
    load();
    const timer = window.setInterval(load, POLL_INTERVAL_MS);
    return () => window.clearInterval(timer);
  }, []);

  // 적재 진행 상태를 폴링한다(K6 상태와 같은 패턴) - 서버 상태 그대로라 새로고침해도 "적재 중"이
  // 안 사라지고, 다른 사람이/다른 탭이 적재를 눌렀어도 여기서 알 수 있다.
  useEffect(() => {
    // 새로고침 직후 첫 조회에서 받는 finishedAt은 "방금 끝난 일"이 아니라 "이미 예전에 끝나서
    // 서버가 계속 기억하고 있던 결과"다 - lastHandledFinishRef가 마운트마다 null로 리셋되는 것과
    // 부딪혀서 매번 새 완료로 오인하고 팝업이 또 뜨는 걸 막는다. 첫 조회는 조용히 반영만 한다.
    let isFirstLoad = true;
    const load = () => {
      fetchDummyDataStatus()
        .then((status) => {
          setDummyStatus(status);

          if (isFirstLoad) {
            isFirstLoad = false;
            lastHandledFinishRef.current = status.finishedAt;
            if (status.lastResult) {
              const counts = status.lastResult;
              setDbCounts({ userCount: counts.userCount, couponCount: counts.couponCount, couponIssueCount: counts.couponIssueCount });
              setReloadTiming({ userLoadMs: counts.userLoadMs, couponIssueLoadMs: counts.couponIssueLoadMs, totalMs: counts.totalMs });
            }
            return;
          }

          if (status.finishedAt && status.finishedAt !== lastHandledFinishRef.current) {
            lastHandledFinishRef.current = status.finishedAt;
            if (status.lastError) {
              window.alert(`데이터 적재 실패: ${status.lastError}`);
            } else if (status.lastResult) {
              const counts = status.lastResult;
              setDbCounts({ userCount: counts.userCount, couponCount: counts.couponCount, couponIssueCount: counts.couponIssueCount });
              setReloadTiming({ userLoadMs: counts.userLoadMs, couponIssueLoadMs: counts.couponIssueLoadMs, totalMs: counts.totalMs });
              window.alert(
                `더미데이터 재적재 완료\n` +
                `회원 ${counts.userCount.toLocaleString()} · ` +
                `쿠폰 ${counts.couponCount.toLocaleString()} · ` +
                `발급 이력 ${counts.couponIssueCount.toLocaleString()}`
              );
            }
          }
        })
        .catch(() => {
          // 조회 실패는 마지막으로 알던 상태를 유지한다.
        });
    };
    load();
    const timer = window.setInterval(load, DUMMY_STATUS_POLL_INTERVAL_MS);
    return () => window.clearInterval(timer);
  }, []);

  const dummyDataCounts: DummyDataCounts | null = dbCounts && {
    ...dbCounts,
    userLoadMs: reloadTiming?.userLoadMs ?? null,
    couponIssueLoadMs: reloadTiming?.couponIssueLoadMs ?? null,
    totalMs: reloadTiming?.totalMs ?? null,
  };
  // 데이터가 한 번도 안 적재됐으면(회원 0명) 다른 기능들은 어차피 의미가 없다(발급 FK 다 실패) -
  // FAB의 나머지 액션을 이걸로 잠근다.
  const dataReady = dbCounts !== null && dbCounts.userCount > 0;

  // 모니터링 선택지는 OPEN 쿠폰만 본다 - 쿠폰이 아무리 많아져도(더미데이터로 수십만 건) OPEN은
  // 항상 소수라 이 목록은 안 커진다. 오픈 자체는 CouponManageDialog의 "오픈" 탭(READY만 따로 조회)에서 한다.
  const refreshCoupons = useCallback((selectId?: number) => {
    return fetchCoupons("OPEN").then((list) => {
      setCoupons(list);
      setCouponId((current) => {
        if (selectId !== undefined && list.some((c) => c.id === selectId)) return selectId;
        return list.some((c) => c.id === current) ? current : (list[0]?.id ?? current);
      });
      return list;
    });
  }, []);

  useEffect(() => {
    const load = () => {
      refreshCoupons().catch(() => {
        // 목록 조회 실패는 별도 에러 UI 없이 조용히 넘어간다 - 대시보드 헤더의
        // 연결 실패 표시가 이미 같은 원인(백엔드 다운)을 알려준다.
      });
    };
    load();
    const timer = window.setInterval(load, POLL_INTERVAL_MS);
    return () => window.clearInterval(timer);
  }, [refreshCoupons]);

  // K6TestService(백엔드가 도커로 형제 k6 컨테이너를 띄움)를 실제로 호출한다. ScenarioDialog에서 고른
  // 대상 쿠폰으로 모니터링 화면도 같이 전환한다 - 다른 쿠폰을 보면서 엉뚱한 쿠폰에 테스트가 도는 걸 방지.
  // vals.testRunning/scenarioFile은 이 호출과 무관하게 GET /api/admin/k6/status 폴링으로 갱신된다.
  const handleStartTest = (scenario: K6Scenario, targetCouponId: number, options: K6RunOptions) => {
    setCouponId(targetCouponId);
    startTest(scenario.id, targetCouponId, options).catch((e) => {
      console.error("[HighFive] k6 실행 실패", e);
    });
  };

  const handleStopTest = () => {
    stopTest().catch((e) => {
      console.error("[HighFive] k6 중지 실패", e);
    });
  };

  // 적재를 "시작"만 시킨다 - OPEN 쿠폰이 있으면 백엔드가 거부한다. 완료/실패는 위 폴링이 감지해서
  // 알림을 띄운다(여기서 기다리지 않는다 - 새로고침해도 진행 상태가 안 끊기게 하려고).
  const handleLoadData = useCallback(() => {
    loadDummyData()
      .then(setDummyStatus)
      .catch((e) => {
        console.error("[HighFive] 데이터 적재 시작 실패", e);
        window.alert(`데이터 적재 시작 실패: ${e.message}`);
      });
  }, []);

  // 대시보드 지표(HTTP/발급/DB insert 집계)만 0으로 되돌린다. Redis 재고·Stream·DB의 실 데이터는
  // 건드리지 않는다 - 예를 들어 정합성 동기화(S012)/검증(S013) 배치는 이 초기화와 무관하게 계속
  // 자기 스케줄대로 돈다.
  const handleResetMetrics = useCallback(() => {
    resetMonitoringMetrics().catch((e) => {
      console.error("[HighFive] 지표 초기화 실패", e);
    });
  }, []);

  // 재시도해도 영원히 실패할 PEL을 강제로 비운다 - DB에는 반영되지 않고 그냥 버려지는 것이므로
  // 되돌릴 수 없다는 걸 확인받는다.
  const handleDrainPending = useCallback(() => {
    const confirmed = window.confirm(
      `쿠폰 #${couponId}의 Stream PEL을 강제로 비웁니다.\n` +
      `대기 중인 메시지는 DB에 반영되지 않고 그대로 버려지며, 되돌릴 수 없습니다.\n` +
      `계속할까요?`
    );
    if (!confirmed) return;

    drainPendingStream(couponId)
      .then((acked) => {
        console.info(`[HighFive] PEL 강제 드레인 완료 - couponId=${couponId}, acked=${acked}`);
      })
      .catch((e) => {
        console.error("[HighFive] PEL 강제 드레인 실패", e);
      });
  }, [couponId]);

  // 새로 생성된 쿠폰이 즉시 오픈까지 됐다면(CouponManageDialog "생성" 탭의 기본 옵션) OPEN 목록에
  // 곧바로 잡힌다. READY로만 만들었다면 여기 목록엔 안 뜨고, "오픈" 탭에서 따로 열어야 보인다.
  const handleCouponCreated = useCallback((coupon: CouponDetail) => {
    refreshCoupons(coupon.id).catch((e) => {
      console.error("[HighFive] 쿠폰 목록 갱신 실패", e);
    });
  }, [refreshCoupons]);

  // CouponManageDialog의 "오픈" 탭이 끝낸 뒤 넘겨준 쿠폰을 OPEN 목록에 반영하고 곧바로 선택한다.
  const handleCouponOpened = useCallback((openedCouponId: number) => {
    refreshCoupons(openedCouponId).catch((e) => {
      console.error("[HighFive] 쿠폰 목록 갱신 실패", e);
    });
  }, [refreshCoupons]);

  // CouponManageDialog의 "클로즈" 탭이 끝낸 뒤 넘겨준 쿠폰 id - 이제 OPEN 목록에서 빠지므로 목록만
  // 새로고침한다(selectId를 안 넘겨서, 방금 닫힌 쿠폰이 없으면 자동으로 다른 OPEN 쿠폰으로 넘어간다).
  const handleCouponClosed = useCallback(() => {
    refreshCoupons().catch((e) => {
      console.error("[HighFive] 쿠폰 목록 갱신 실패", e);
    });
  }, [refreshCoupons]);

  return (
    <div className="app-shell">
      <Sidebar
        couponHistoryDisabled={!dataReady}
        onOpenCouponHistory={() => setCouponHistoryDialogOpen(true)}
      />

      <div className="main">
        <DashboardHeader
          vals={vals}
          error={error}
          couponMissing={couponMissing}
          coupons={coupons}
          couponId={couponId}
          onCouponChange={setCouponId}
          loadingData={dummyStatus.loading}
        />

        <div className="row">
          <ServerResourceCard vals={vals} />
          <ApiResponseCard vals={vals} />
          <CouponStatusCard vals={vals} />
        </div>

        <div className="row">
          <div className="pipeline-col">
            <CouponPipelineCard
              vals={vals}
              onDrainPending={handleDrainPending}
              dummyDataCounts={dummyDataCounts}
              beforeCounts={dummyStatus.before}
            />
            <ConsistencyStatusCard />
          </div>
          <CouponIssueHistoryCard couponId={couponId} testRunning={vals.testRunning} />
        </div>
      </div>

      <FloatingActionMenu
        vals={vals}
        coupons={coupons}
        couponId={couponId}
        dataReady={dataReady}
        dataLoading={dummyStatus.loading}
        onRefresh={refresh}
        refreshing={refreshing}
        onStartTest={handleStartTest}
        onStopTest={handleStopTest}
        onLoadData={handleLoadData}
        onResetMetrics={handleResetMetrics}
        onCouponCreated={handleCouponCreated}
        onCouponOpened={handleCouponOpened}
        onCouponClosed={handleCouponClosed}
      />

      {couponHistoryDialogOpen && (
        <CouponHistoryDialog
          onClose={() => setCouponHistoryDialogOpen(false)}
        />
      )}
    </div>
  );
}
