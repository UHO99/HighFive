import { useCallback, useEffect, useRef, useState } from "react";
import { Sidebar } from "../components/Sidebar";
import { DashboardHeader } from "../components/DashboardHeader";
import { ExpandableCard } from "../components/ExpandableCard";
import { CouponStatusCard } from "../components/CouponStatusCard";
import { CouponPipelineCard } from "../components/CouponPipelineCard";
import { CouponIssueHistoryCard } from "../components/CouponIssueHistoryCard";
import { ConsistencyStatusCard } from "../components/ConsistencyStatusCard";
import { CouponHistoryDialog } from "../components/CouponHistoryDialog";
import { FloatingActionMenu } from "../components/FloatingActionMenu";
import { CouponManageDialog } from "../components/CouponManageDialog";
import { ScenarioDialog } from "../components/ScenarioDialog";
import { useMonitoringDashboard } from "../hooks/useMonitoringDashboard";
import { useClock } from "../hooks/useClock";   // 추가
import {
  drainPendingStream, fetchCoupons, fetchDummyDataCounts, fetchDummyDataStatus, fetchK6Summary, loadDummyData, resetMonitoringMetrics,
  type CouponDetail, type CouponSummary, type DummyDataCounts, type DummyDataStatus, type K6RunOptions, type K6SummaryResponse,
} from "../lib/api";
import type { K6Scenario } from "../lib/scenarios";

const DEFAULT_COUPON_ID = 1;

// 변경 후 (이름을 "적재 진행 중일 때만 쓰는 폴링"이라는 의미로 명확히)
const POLL_INTERVAL_MS = 10_000;
const DUMMY_LOADING_POLL_INTERVAL_MS = 2000;

const IDLE_DUMMY_STATUS: DummyDataStatus = { loading: false, startedAt: null, finishedAt: null, before: null, lastResult: null, lastError: null };

type DbCounts = Pick<DummyDataCounts, "userCount" | "couponCount" | "couponIssueCount">;
type ReloadTiming = Pick<DummyDataCounts, "userLoadMs" | "couponIssueLoadMs" | "totalMs">;

export function DashboardPage() {
  const [coupons, setCoupons] = useState<CouponSummary[]>([]);
  const [couponId, setCouponId] = useState(DEFAULT_COUPON_ID);
  const now = useClock();
  const { vals, startTest, stopTest, refresh, refreshing, error, couponMissing } = useMonitoringDashboard(couponId, now);
  const [couponHistoryDialogOpen, setCouponHistoryDialogOpen] = useState(false);
  const [expandedCard, setExpandedCard] = useState<string | null>(null);
  const [activeTab, setActiveTab] = useState<"overview" | "pipeline" | "history">("overview");
  const [showCouponManage, setShowCouponManage] = useState(false);
  const [showScenario, setShowScenario] = useState(false);
  const [dbCounts, setDbCounts] = useState<DbCounts | null>(null);
  // 재적재 시점의 소요시간은 폴링으로 안 지워진다 - GET counts는 이 값을 모르니(null) 마지막으로
  // 성공한 재적재 값을 그대로 들고 있는다.
  const [reloadTiming, setReloadTiming] = useState<ReloadTiming | null>(null);
  const [dummyStatus, setDummyStatus] = useState<DummyDataStatus>(IDLE_DUMMY_STATUS);
  // "더미데이터 적재 결과" 표 전용 - 마지막 적재가 끝난 순간에 딱 한 번 고정되는 스냅샷.
  // dbCounts/dummyDataCounts는 그 이후로도 계속 실시간 폴링되어 바뀌므로(쿠폰 신규 생성, 발급 테스트 등),
  // "적재 결과"에 그 값을 그대로 쓰면 적재와 무관한 변화까지 같이 늘어나 보이는 버그가 생긴다.
  const [lastLoadResult, setLastLoadResult] = useState<DummyDataCounts | null>(null);
  // 이미 알림/카운트 반영을 한 완료 시각을 기억해서, 폴링마다 같은 완료를 중복 처리하지 않는다.
  const lastHandledFinishRef = useRef<string | null>(null);

  // 변경 후
  // DB 건수는 "이벤트가 실제로 일어났을 때"만 다시 세면 충분하다(사용자 1명, 스케줄러는 이 값들을
  // 안 건드림 - 확인 완료). 컴포넌트가 처음 뜰 때(새로고침/백엔드 재시작 후 재접속 포함)만 최초
  // 1회 조회해 기준값을 잡고, 이후로는 쿠폰 생성/발급 테스트 종료/더미데이터 적재 완료 각 시점에
  // 호출부가 직접 재조회한다.
  const reloadDbCounts = useCallback(() => {
    fetchDummyDataCounts()
      .then((c) => setDbCounts({ userCount: c.userCount, couponCount: c.couponCount, couponIssueCount: c.couponIssueCount }))
      .catch(() => {
        // 조회 실패는 카드가 마지막으로 알던 값을 그대로 유지하게 둔다.
      });
  }, []);

  useEffect(() => {
    reloadDbCounts();
  }, [reloadDbCounts]);

  // 변경 후
  const dummyPollRef = useRef<number | null>(null);

  // 서버 응답 하나를 받아서, 상태 반영 + "완료 감지 시 처리"까지 공통으로 수행한다.
  // 마운트 시 최초 1회 호출과, 폴링 중 매 tick 호출 둘 다 이 함수를 거친다.
  const handleDummyStatus = useCallback((status: DummyDataStatus, isFirstLoad: boolean) => {
    setDummyStatus(status);

    if (isFirstLoad) {
      lastHandledFinishRef.current = status.finishedAt;
      if (status.lastResult) {
        const counts = status.lastResult;
        setDbCounts({ userCount: counts.userCount, couponCount: counts.couponCount, couponIssueCount: counts.couponIssueCount });
        setReloadTiming({ userLoadMs: counts.userLoadMs, couponIssueLoadMs: counts.couponIssueLoadMs, totalMs: counts.totalMs });
        setLastLoadResult(counts);
      }
    } else if (status.finishedAt && status.finishedAt !== lastHandledFinishRef.current) {
      lastHandledFinishRef.current = status.finishedAt;
      if (status.lastError) {
        window.alert(`데이터 적재 실패: ${status.lastError}`);
      } else if (status.lastResult) {
        const counts = status.lastResult;
        setDbCounts({ userCount: counts.userCount, couponCount: counts.couponCount, couponIssueCount: counts.couponIssueCount });
        setReloadTiming({ userLoadMs: counts.userLoadMs, couponIssueLoadMs: counts.couponIssueLoadMs, totalMs: counts.totalMs });
        setLastLoadResult(counts);
        window.alert(
          `더미데이터 재적재 완료\n` +
          `회원 ${counts.userCount.toLocaleString()} · ` +
          `쿠폰 ${counts.couponCount.toLocaleString()} · ` +
          `발급 이력 ${counts.couponIssueCount.toLocaleString()}`
        );
      }
    }

    // "진행 중"이 아니게 됐으면(완료/실패/애초에 안 하고 있었음) 폴링을 스스로 멈춘다.
    if (!status.loading && dummyPollRef.current !== null) {
      window.clearInterval(dummyPollRef.current);
      dummyPollRef.current = null;
    }
  }, []);

  // 적재가 실제로 "진행 중"일 때만 짧은 간격으로 폴링을 시작한다. 이미 돌고 있으면 중복 시작하지 않는다.
  const startDummyPolling = useCallback(() => {
    if (dummyPollRef.current !== null) return;
    dummyPollRef.current = window.setInterval(() => {
      fetchDummyDataStatus()
        .then((status) => handleDummyStatus(status, false))
        .catch(() => { });
    }, DUMMY_LOADING_POLL_INTERVAL_MS);
  }, [handleDummyStatus]);

  // 마운트 시 1회만 확인한다 - 혹시 새로고침 직전에 다른 탭/사람이 적재를 시작해둔 상태라면
  // (사용자가 1명이라도, 같은 브라우저에서 이 페이지를 새로고침하는 경우는 있을 수 있다),
  // 그 진행 상황을 이어서 보여줘야 하므로 loading이면 폴링을 이어서 시작한다.
  useEffect(() => {
    Promise.all([fetchDummyDataStatus(), fetchDummyDataCounts()])
      .then(([status, counts]) => {
        handleDummyStatus(status, true);
        if (status.loading) startDummyPolling();

        // "적재 결과" 표는 실제로 DB에 더미데이터가 있을 때만 보여야 한다. 서버 메모리(lastResult)는
        // 재시작 시 사라지므로, 그 경우엔 SEED_BASELINE(before, 코드에 고정된 상수)으로 복원하되,
        // DB 자체가 비어있으면(재시작 전 데이터를 다 지운 경우) 절대 채우지 않는다.
        if (!status.lastResult && counts.userCount > 0 && status.before) {
          setLastLoadResult(status.before);
        }
      })
      .catch(() => { });

    return () => {
      if (dummyPollRef.current !== null) window.clearInterval(dummyPollRef.current);
    };
  }, [handleDummyStatus, startDummyPolling]);

  const toggleExpandCard = useCallback((id: string) => {
    setExpandedCard((current) => (current === id ? null : id));
  }, []);

  // 확대된 카드가 있을 때 Esc로도 닫을 수 있게 한다.
  useEffect(() => {
    if (!expandedCard) return;
    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === "Escape") setExpandedCard(null);
    };
    window.addEventListener("keydown", onKeyDown);
    return () => window.removeEventListener("keydown", onKeyDown);
  }, [expandedCard]);

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

  // k6 실행 결과 요약 - 테스트가 방금 끝났을 때만 조회한다. 실행 중/한 번도 안 돌렸으면 null.
  const [k6Summary, setK6Summary] = useState<K6SummaryResponse | null>(null);

  // 신규 추가 - 부하 테스트가 방금 끝난 순간에만 DB 건수를 다시 센다 (coupon_issue가 대량으로 늘었을 것)
  const wasTestRunningRef = useRef(vals.testRunning);
  useEffect(() => {
    const wasRunning = wasTestRunningRef.current;
    wasTestRunningRef.current = vals.testRunning;
    if (wasRunning && !vals.testRunning) {
      reloadDbCounts();
      fetchK6Summary().then(setK6Summary).catch(() => {});
    }
  }, [vals.testRunning, reloadDbCounts]);

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

  // 변경 후
  const handleLoadData = useCallback(() => {
    loadDummyData()
      .then((status) => {
        setDummyStatus(status);
        startDummyPolling();   // 추가 - 시작하자마자 진행 상황을 보기 위해 폴링 개시
      })
      .catch((e) => {
        console.error("[HighFive] 데이터 적재 시작 실패", e);
        window.alert(`데이터 적재 시작 실패: ${e.message}`);
      });
  }, [startDummyPolling]);

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

  // 변경 후
  const handleCouponCreated = useCallback((coupon: CouponDetail) => {
    refreshCoupons(coupon.id).catch((e) => {
      console.error("[HighFive] 쿠폰 목록 갱신 실패", e);
    });
    reloadDbCounts();   // 추가 - couponCount가 늘었으니 즉시 반영
  }, [refreshCoupons, reloadDbCounts]);

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
        vals={vals}
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

        {/* 관리자 핵심 요약 — 메인에 항상 노출 (회원/쿠폰/이력/OPEN) — Wise 토큰, 크게 */}
        <div className="admin-summary-bar">
          <div className="admin-summary-item">
            <span className="admin-summary-label">회원</span>
            <span className="admin-summary-value">{dbCounts ? dbCounts.userCount.toLocaleString("ko-KR") : "-"}</span>
          </div>
          <div className="admin-summary-divider" />
          <div className="admin-summary-item">
            <span className="admin-summary-label">쿠폰</span>
            <span className="admin-summary-value">{dbCounts ? dbCounts.couponCount.toLocaleString("ko-KR") : "-"}</span>
          </div>
          <div className="admin-summary-divider" />
          <div className="admin-summary-item">
            <span className="admin-summary-label">발급 이력</span>
            <span className="admin-summary-value">{dbCounts ? dbCounts.couponIssueCount.toLocaleString("ko-KR") : "-"}</span>
          </div>
          <div className="admin-summary-divider" />
          <div className="admin-summary-item">
            <span className="admin-summary-label">OPEN 쿠폰</span>
            <span className="admin-summary-value" style={{ color: "var(--color-primary)" }}>{coupons.length.toLocaleString("ko-KR")}</span>
          </div>
          <div className="admin-summary-actions">
            <button type="button" className="admin-action-btn" onClick={handleLoadData} disabled={dummyStatus.loading}>
              {dummyStatus.loading ? "적재 중..." : "데이터 적재"}
            </button>
            <button type="button" className="admin-action-btn primary" onClick={() => setShowCouponManage(true)}>
              쿠폰 관리
            </button>
            <button
              type="button"
              className="admin-action-btn"
              onClick={() => setShowScenario(true)}
              disabled={!dataReady}
              title={!dataReady ? "먼저 데이터 적재를 완료하세요" : undefined}
            >
              {vals.testRunning ? `테스트 중 · ${vals.elapsedText}` : "부하 테스트"}
            </button>
          </div>
        </div>

        {/* 사용자 친화적 탭 네비게이션: 메인에 6개 카드를 한 번에 쌓지 않고 3개 탭으로 분리 — Wise pill + Lime active */}
        <div className="dash-tabs" role="tablist" aria-label="대시보드 섹션">
          <button
            role="tab"
            aria-selected={activeTab === "overview"}
            className={`dash-tab ${activeTab === "overview" ? "active" : ""}`}
            onClick={() => setActiveTab("overview")}
          >
            Overview
          </button>
          <button
            role="tab"
            aria-selected={activeTab === "pipeline"}
            className={`dash-tab ${activeTab === "pipeline" ? "active" : ""}`}
            onClick={() => setActiveTab("pipeline")}
          >
            Pipeline
          </button>
          <button
            role="tab"
            aria-selected={activeTab === "history"}
            className={`dash-tab ${activeTab === "history" ? "active" : ""}`}
            onClick={() => setActiveTab("history")}
          >
            History
          </button>
        </div>

        {activeTab === "overview" && (
          <div className="overview-main">
            <div className="coupon-main-wrap coupon-main-full">
              <ExpandableCard id="coupon-status" expandedId={expandedCard} onToggle={toggleExpandCard}>
                <CouponStatusCard vals={vals} k6Summary={k6Summary} />
              </ExpandableCard>
            </div>
          </div>
        )}

        {activeTab === "pipeline" && (
          <div className="chart-grid-2">
            <ExpandableCard id="coupon-pipeline" expandedId={expandedCard} onToggle={toggleExpandCard}>
              <CouponPipelineCard
                vals={vals}
                onDrainPending={handleDrainPending}
                dummyDataCounts={dummyDataCounts}
                beforeCounts={dummyStatus.before}
                lastLoadResult={lastLoadResult}
              />
            </ExpandableCard>
            <ExpandableCard id="consistency-status" expandedId={expandedCard} onToggle={toggleExpandCard}>
              <ConsistencyStatusCard now={now} />
            </ExpandableCard>
          </div>
        )}

        {activeTab === "history" && (
          <div className="history-full">
            <ExpandableCard id="coupon-issue-history" expandedId={expandedCard} onToggle={toggleExpandCard}>
              <CouponIssueHistoryCard couponId={couponId} testRunning={vals.testRunning} />
            </ExpandableCard>
          </div>
        )}
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

      {showCouponManage && (
        <CouponManageDialog
          onCancel={() => setShowCouponManage(false)}
          onCouponCreated={(c) => { setShowCouponManage(false); handleCouponCreated(c); }}
          onCouponOpened={(id) => { setShowCouponManage(false); handleCouponOpened(id); }}
          onCouponClosed={() => { setShowCouponManage(false); handleCouponClosed(); }}
        />
      )}

      {showScenario && (
        <ScenarioDialog
          coupons={coupons}
          defaultCouponId={couponId}
          onCancel={() => setShowScenario(false)}
          onConfirm={(scenario, targetCouponId, options) => {
            setShowScenario(false);
            handleStartTest(scenario, targetCouponId, options);
          }}
        />
      )}
    </div>
  );
}