import { useCallback, useEffect, useRef, useState } from "react";
import { colorFor, fmt } from "../lib/format";
import {
  fetchK6Status,
  fetchMonitoringDashboard,
  MonitoringCouponNotFoundError,
  runK6Scenario,
  stopK6Scenario,
  type K6RunOptions,
  type K6StatusResponse,
  type MonitoringDashboardResponse,
} from "../lib/api";

/**
 * 실 백엔드(GET /api/admin/monitoring/coupons/{couponId}) 폴링 훅.
 * 예전 useDashboardSimulation을 그대로 대체한다 - DashboardVals 필드 이름/의미를 유지해서
 * 카드 컴포넌트들은 손대지 않아도 되게 했다.
 * "테스트 시작/중지"는 K6TestService(백엔드가 도커로 형제 k6 컨테이너를 띄움)를 그대로 호출하고,
 * 실행 여부(testRunning)는 GET /api/admin/k6/status 폴링 결과를 그대로 반영한다 - 로컬 UI 상태가 아니다.
 */

const DATA_POLL_INTERVAL_MS = 2000;
const CLOCK_TICK_INTERVAL_MS = 1000;

const ZERO_DASHBOARD: MonitoringDashboardResponse = {
  couponId: 0,
  measuredAt: new Date(0).toISOString(),
  serverResources: { rps: 0, cpuUsagePercent: 0, memoryUsagePercent: 0, jvmHeapUsagePercent: 0 },
  apiResponse: {
    avgResponseTimeMs: 0, p95ResponseTimeMs: 0, p99ResponseTimeMs: 0,
    errorRatePercent: 0, successAvgResponseTimeMs: 0, failAvgResponseTimeMs: 0,
  },
  couponIssueStatus: {
    totalRequests: 0, successCount: 0, failCount: 0,
    issuePerSecond: 0, soldOutFailCount: 0, duplicateFailCount: 0,
  },
  overIssueMonitor: {
    stockDepletedCount: 0, successIssuedCount: 0, dbHistoryCount: 0, matched: true,
    recordedIssuedQuantity: null, consistencyConfirmed: false,
  },
  stockStatus: { issuedCount: 0, redisStockRemaining: 0, redisStockTotal: 0, redisStockConsumedPercent: 0 },
  streamStatus: { activeSubscriptions: 0, totalStreams: 0, pendingCount: 0, maxLagMs: 0 },
  dbStorage: { dbConnPoolActive: 0, dbConnPoolMax: 0, dbInsertThroughputPerSecond: 0, batchInsertAvgSize: 0, batchInsertMaxSize: 0 },
};

const ZERO_K6_STATUS: K6StatusResponse = {
  running: false, scenarioId: null, scenarioFile: null, couponId: null, startedAt: null, exitCode: null,
};

export interface DashboardVals {
  clockText: string;
  systemStatusColor: string;
  systemStatusLabel: string;
  rpsFmt: string;
  cpuFmt: string; cpuColor: string;
  memFmt: string; memColor: string;
  heapFmt: string; heapColor: string;
  apiAvgFmt: string; p95Fmt: string; p99Fmt: string;
  errFmt: string; errColor: string;
  successLatFmt: string; failLatFmt: string;
  successLatBarPct: string; failLatBarPct: string;
  couponTotalFmt: string;
  couponSuccessFmt: string;
  couponFailFmt: string;
  couponPerSecFmt: string;
  soldOutFmt: string; dupFmt: string;
  soldOutPctStyle: string; dupPctStyle: string;
  issuedCountFmt: string;
  overissueSuccessFmt: string;
  dbIssueCountFmt: string;
  overissueLabel: string; overissueBg: string; overissueFg: string;
  s013ConfirmedLabel: string; s013ConfirmedBg: string; s013ConfirmedFg: string;
  redisStockFmt: string;
  issuedPct: number; issuedPctFmt: string;
  activeStreamSubsFmt: number; openCouponFmt: number;
  streamSubColor: string;
  pelCountFmt: string; pelCountRaw: number; pelColor: string;
  pelBarPct: string;
  pelDelayFmt: string;
  cpuBarHeight: string; memBarHeight: string; heapBarHeight: string;
  dbInsertFmt: string;
  dbConnFmt: string; dbConnColor: string;
  batchAvgFmt: string; batchMaxFmt: string;
  batchAvgPct: string; batchMaxPct: string;
  testRunning: boolean;
  elapsedText: string;
  testButtonLabel: string;
  /** 실행 중인(또는 방금 끝난) 시나리오 파일명 - 서버 상태에서 그대로 가져온다. 새로고침해도 안 사라진다. */
  scenarioFile: string | null;
}

function formatDelay(ms: number): string {
  return ms < 1000 ? `${Math.round(ms)}ms` : `${(ms / 1000).toFixed(1)}s`;
}

function toVals(data: MonitoringDashboardResponse, now: number, k6Status: K6StatusResponse): DashboardVals {
  const sr = data.serverResources;
  const ar = data.apiResponse;
  const ci = data.couponIssueStatus;
  const oi = data.overIssueMonitor;
  const ss = data.stockStatus;
  const st = data.streamStatus;
  const db = data.dbStorage;

  const dbConnPct = db.dbConnPoolMax === 0 ? 0 : (db.dbConnPoolActive / db.dbConnPoolMax) * 100;
  const anyDanger = sr.cpuUsagePercent >= 85 || sr.memoryUsagePercent >= 85 || ar.errorRatePercent >= 3 || dbConnPct >= 90;
  const anyWarn = sr.cpuUsagePercent >= 60 || sr.memoryUsagePercent >= 60 || ar.errorRatePercent >= 1 || dbConnPct >= 70;

  const couponFailTotal = ci.soldOutFailCount + ci.duplicateFailCount;
  const soldOutPct = couponFailTotal > 0 ? (ci.soldOutFailCount / couponFailTotal) * 100 : 0;

  let elapsedText = "";
  if (k6Status.running && k6Status.startedAt) {
    const sec = Math.floor((now - Date.parse(k6Status.startedAt)) / 1000);
    const m = Math.floor(sec / 60);
    const r = sec % 60;
    elapsedText = `${m}:${String(r).padStart(2, "0")}`;
  }

  return {
    clockText: new Date(now).toLocaleTimeString("ko-KR"),
    systemStatusColor: anyDanger ? "#dc2626" : anyWarn ? "#e0821f" : "#16a34a",
    systemStatusLabel: anyDanger ? "병목 감지" : anyWarn ? "부하 상승" : "전체 정상",
    rpsFmt: fmt(sr.rps),
    cpuFmt: sr.cpuUsagePercent.toFixed(1), cpuColor: colorFor(sr.cpuUsagePercent, 60, 85),
    memFmt: sr.memoryUsagePercent.toFixed(1), memColor: colorFor(sr.memoryUsagePercent, 60, 85),
    heapFmt: sr.jvmHeapUsagePercent.toFixed(1), heapColor: colorFor(sr.jvmHeapUsagePercent, 70, 90),
    apiAvgFmt: fmt(ar.avgResponseTimeMs), p95Fmt: fmt(ar.p95ResponseTimeMs), p99Fmt: fmt(ar.p99ResponseTimeMs),
    errFmt: ar.errorRatePercent.toFixed(2), errColor: colorFor(ar.errorRatePercent, 1, 3),
    successLatFmt: fmt(ar.successAvgResponseTimeMs), failLatFmt: fmt(ar.failAvgResponseTimeMs),
    successLatBarPct: `${Math.min(100, (ar.successAvgResponseTimeMs / 500) * 100)}%`,
    failLatBarPct: `${Math.min(100, (ar.failAvgResponseTimeMs / 500) * 100)}%`,
    couponTotalFmt: fmt(ci.totalRequests),
    couponSuccessFmt: fmt(ci.successCount),
    couponFailFmt: fmt(ci.failCount),
    couponPerSecFmt: `${fmt(ci.issuePerSecond)} / s`,
    soldOutFmt: fmt(ci.soldOutFailCount), dupFmt: fmt(ci.duplicateFailCount),
    soldOutPctStyle: `${soldOutPct}%`, dupPctStyle: `${100 - soldOutPct}%`,
    issuedCountFmt: fmt(oi.stockDepletedCount),
    overissueSuccessFmt: fmt(oi.successIssuedCount),
    dbIssueCountFmt: fmt(oi.dbHistoryCount),
    overissueLabel: oi.matched ? "일치" : "반영 지연",
    overissueBg: oi.matched ? "#e9f9ee" : "#fff4e6",
    overissueFg: oi.matched ? "#16a34a" : "#e0821f",
    s013ConfirmedLabel: oi.consistencyConfirmed ? "동기화 검증 완료" : "동기화 검증 대기",
    s013ConfirmedBg: oi.consistencyConfirmed ? "#e9f9ee" : "#f7f8fb",
    s013ConfirmedFg: oi.consistencyConfirmed ? "#16a34a" : "#8b8fa3",
    redisStockFmt: `${fmt(ss.redisStockRemaining)} / ${fmt(ss.redisStockTotal)}`,
    issuedPct: ss.redisStockConsumedPercent, issuedPctFmt: ss.redisStockConsumedPercent.toFixed(1),
    activeStreamSubsFmt: st.activeSubscriptions, openCouponFmt: st.totalStreams,
    streamSubColor: st.activeSubscriptions === st.totalStreams ? "#16a34a" : "#e0821f",
    pelCountFmt: fmt(st.pendingCount), pelCountRaw: st.pendingCount, pelColor: colorFor(st.pendingCount, 200, 1000),
    pelBarPct: `${Math.min(100, (st.pendingCount / 1500) * 100)}%`,
    pelDelayFmt: formatDelay(st.maxLagMs),
    cpuBarHeight: `${Math.max(4, sr.cpuUsagePercent)}%`,
    memBarHeight: `${Math.max(4, sr.memoryUsagePercent)}%`,
    heapBarHeight: `${Math.max(4, sr.jvmHeapUsagePercent)}%`,
    dbInsertFmt: `${fmt(db.dbInsertThroughputPerSecond)} / s`,
    dbConnFmt: `${fmt(db.dbConnPoolActive)} / ${db.dbConnPoolMax}`,
    dbConnColor: colorFor(dbConnPct, 70, 90),
    batchAvgFmt: fmt(db.batchInsertAvgSize), batchMaxFmt: fmt(db.batchInsertMaxSize),
    batchAvgPct: `${Math.min(100, (db.batchInsertAvgSize / 500) * 100)}%`,
    batchMaxPct: `${Math.min(100, (db.batchInsertMaxSize / 500) * 100)}%`,
    testRunning: k6Status.running,
    elapsedText,
    testButtonLabel: k6Status.running ? "테스트 중지" : "테스트 시작",
    scenarioFile: k6Status.scenarioFile,
  };
}

export interface UseMonitoringDashboardResult {
  vals: DashboardVals;
  startTest: (scenarioId: string, targetCouponId: number, options?: K6RunOptions) => Promise<void>;
  stopTest: () => Promise<void>;
  /** 진짜 연결 실패(네트워크 오류/5xx 등) - 헤더가 빨간 "백엔드 연결 실패"로 보여준다. */
  error: string | null;
  /** couponId가 DB에 없어서 404 - 오픈된 쿠폰이 없을 때 정상적으로 발생하므로 error와 분리해서 다룬다. */
  couponMissing: boolean;
}

export function useMonitoringDashboard(couponId: number): UseMonitoringDashboardResult {
  const [data, setData] = useState<MonitoringDashboardResponse>(ZERO_DASHBOARD);
  const [now, setNow] = useState(Date.now());
  const [k6Status, setK6Status] = useState<K6StatusResponse>(ZERO_K6_STATUS);
  const [error, setError] = useState<string | null>(null);
  const [couponMissing, setCouponMissing] = useState(false);

  const dataPollRef = useRef<number | null>(null);
  const clockTickRef = useRef<number | null>(null);

  // 쿠폰을 바꾸면 이전 쿠폰의 화면 값이 잠깐이라도 남아있지 않도록 0으로 되돌리고 새로 폴링을 시작한다.
  useEffect(() => {
    let cancelled = false;
    setData(ZERO_DASHBOARD);
    setError(null);
    setCouponMissing(false);

    const poll = async () => {
      try {
        const next = await fetchMonitoringDashboard(couponId);
        if (!cancelled) {
          setData(next);
          setError(null);
          setCouponMissing(false);
        }
      } catch (e) {
        if (cancelled) return;
        if (e instanceof MonitoringCouponNotFoundError) {
          // 백엔드는 멀쩡하고, 그냥 지금 볼 쿠폰이 없는 것 - 연결 실패로 취급하지 않는다.
          setError(null);
          setCouponMissing(true);
        } else {
          setError(e instanceof Error ? e.message : "모니터링 조회 실패");
          setCouponMissing(false);
        }
      }
    };

    poll();
    dataPollRef.current = window.setInterval(poll, DATA_POLL_INTERVAL_MS);

    return () => {
      cancelled = true;
      if (dataPollRef.current !== null) window.clearInterval(dataPollRef.current);
    };
  }, [couponId]);

  // k6 실행 상태는 쿠폰과 무관하게(단일 러너) 계속 폴링한다 - 스크립트가 끝나면(약 45초) testRunning이
  // 자동으로 false로 바뀌는 게 이 폴링 덕분이다.
  useEffect(() => {
    let cancelled = false;

    const poll = async () => {
      try {
        const next = await fetchK6Status();
        if (!cancelled) setK6Status(next);
      } catch {
        // k6 상태 조회 실패는 모니터링 에러와 별도로 취급하지 않는다 - 모니터링 폴링이 이미 같은 원인
        // (백엔드 다운)을 error로 드러낸다.
      }
    };

    poll();
    const timer = window.setInterval(poll, DATA_POLL_INTERVAL_MS);
    return () => {
      cancelled = true;
      window.clearInterval(timer);
    };
  }, []);

  useEffect(() => {
    clockTickRef.current = window.setInterval(() => setNow(Date.now()), CLOCK_TICK_INTERVAL_MS);
    return () => {
      if (clockTickRef.current !== null) window.clearInterval(clockTickRef.current);
    };
  }, []);

  // couponId(이 훅이 모니터링 중인 쿠폰)와 별개로 targetCouponId를 명시적으로 받는다 - 대시보드에
  // 보이는 쿠폰과 다른 쿠폰을 대상으로 테스트를 시작할 수도 있어서(ScenarioDialog의 쿠폰 선택).
  const startTest = useCallback(async (scenarioId: string, targetCouponId: number, options?: K6RunOptions) => {
    const next = await runK6Scenario(scenarioId, targetCouponId, options);
    setK6Status(next);
  }, []);

  const stopTest = useCallback(async () => {
    const next = await stopK6Scenario();
    setK6Status(next);
  }, []);

  return { vals: toVals(data, now, k6Status), startTest, stopTest, error, couponMissing };
}
