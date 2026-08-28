export interface ApiResponse<T> {
  success: boolean;
  data: T | null;
  message: string | null;
}

const API_BASE_STORAGE_KEY = "highfive.apiBaseUrl";

// 배포된 정적 빌드(GitHub Pages 등)를 보는 사람이 자기 컴퓨터에서 백엔드를 로컬로 띄워두고 있다는
// 전제로 곧바로 이 주소를 부른다. 브라우저는 https 페이지에서 http://localhost 로 나가는 것을
// mixed-content 차단 예외로 허용하므로 터널 없이도 동작한다 - 단, "배포 링크를 연 사람의 로컬
// 백엔드"만 호출하는 것이라 그 사람 본인 컴퓨터에서 열 때만 의미가 있다(공개 데모용이 아님).
const DEFAULT_LOCAL_BACKEND = "http://localhost:8080";

/**
 * 로컬 dev(`npm run dev`)에서는 vite.config.ts의 server.proxy가 "/api"를 8080으로 넘겨주므로
 * 상대경로만으로 충분하다 - 그래서 그 경우엔 그대로 ""(상대경로)를 쓴다.
 * 반면 GitHub Pages 같은 정적 배포본은 프록시가 없어서, 기본값으로 DEFAULT_LOCAL_BACKEND를
 * 호출한다. `?api=<URL>`을 한 번 넘기면 그 값을 localStorage에 기억해두고 이후 새로고침에도
 * 계속 그 백엔드를 본다(터널 등 다른 백엔드를 가리키고 싶을 때 쓰는 탈출구).
 */
function resolveApiBase(): string {
  if (typeof window === "undefined") return "";

  const fromQuery = new URLSearchParams(window.location.search).get("api");
  if (fromQuery) {
    const normalized = fromQuery.replace(/\/$/, "");
    window.localStorage.setItem(API_BASE_STORAGE_KEY, normalized);
    return normalized;
  }

  const stored = window.localStorage.getItem(API_BASE_STORAGE_KEY);
  if (stored) return stored;

  const isLocalDev = window.location.hostname === "localhost" || window.location.hostname === "127.0.0.1";
  return isLocalDev ? "" : DEFAULT_LOCAL_BACKEND;
}

export const API_BASE = resolveApiBase();

/** backend MonitoringDashboardResponse(domain/monitoring/dto)와 1:1로 대응한다. */
export interface MonitoringDashboardResponse {
  couponId: number;
  measuredAt: string;
  serverResources: {
    rps: number;
    cpuUsagePercent: number;
    memoryUsagePercent: number;
    jvmHeapUsagePercent: number;
  };
  apiResponse: {
    avgResponseTimeMs: number;
    p95ResponseTimeMs: number;
    p99ResponseTimeMs: number;
    errorRatePercent: number;
    successAvgResponseTimeMs: number;
    failAvgResponseTimeMs: number;
  };
  couponIssueStatus: {
    totalRequests: number;
    successCount: number;
    failCount: number;
    issuePerSecond: number;
    soldOutFailCount: number;
    duplicateFailCount: number;
  };
  overIssueMonitor: {
    stockDepletedCount: number;
    successIssuedCount: number;
    dbHistoryCount: number;
    matched: boolean;
    /** S012(정합성 동기화)가 마지막으로 기록한 값 - 아직 한 번도 동기화 안 됐으면 null. */
    recordedIssuedQuantity: number | null;
    /** S013(정합성 검증)이 드레인 완료 + 기록값=실측값을 확정했는지. */
    consistencyConfirmed: boolean;
  };
  stockStatus: {
    issuedCount: number;
    redisStockRemaining: number;
    redisStockTotal: number;
    redisStockConsumedPercent: number;
  };
  streamStatus: {
    activeSubscriptions: number;
    totalStreams: number;
    pendingCount: number;
    maxLagMs: number;
  };
  dbStorage: {
    dbConnPoolActive: number;
    dbConnPoolMax: number;
    dbInsertThroughputPerSecond: number;
    batchInsertAvgSize: number;
    batchInsertMaxSize: number;
  };
}

/**
 * backend MismatchEvent(domain/coupon/dto)와 1:1로 대응한다. resolvedAt이 null이면 아직 미해소 -
 * 해소되어도 목록에서 사라지지 않고 resolvedAt이 채워진 채로 계속 남아있는 이력이다.
 */
export interface MismatchHistoryEntry {
  couponId: number;
  detectedAt: string;
  resolvedAt: string | null;
  recordedIssuedQuantity: number | null;
  actualIssuedCount: number;
  pendingCount: number;
  totalQuantity: number;
}

/** S012가 실제로 issuedQuantity를 써넣은(=드레인 완료 후 동기화된) 순간의 로그 한 줄. */
export interface SyncLogEntry {
  couponId: number;
  syncedAt: string;
  issuedQuantity: number;
}

/** S013이 "드레인 완료 + 기록값=실측값"을 확정한 순간의 로그 한 줄. */
export interface VerifyLogEntry {
  couponId: number;
  confirmedAt: string;
}

/**
 * backend CouponConsistencyStatusResponse(domain/coupon/dto)와 1:1로 대응한다. couponId와 무관한
 * 시스템 전체 상태라 모니터링 대시보드 조회(couponId 스코프)와 별도 경로다 - 오픈된 쿠폰이 없어서
 * 그쪽이 실패하는 동안에도 이 값은 계속 갱신된다. 동기화/검증 배치가 사이클마다(대상이 0건이어도)
 * lastRunAt을 갱신하므로, "지금 - lastRunAt"이 intervalMs의 몇 배를 넘으면 배치가 멈춘 것으로 볼 수
 * 있다. verify는 CLOSE된 쿠폰만 대상으로 하므로 OPEN 쿠폰만 있을 땐 targetCount가 항상 0이다(정상).
 */
export interface CouponConsistencyStatusResponse {
  sync: {
    lastRunAt: string | null;
    intervalMs: number;
    targetCount: number;
    syncedCount: number;
    log: SyncLogEntry[];
  };
  verify: {
    lastRunAt: string | null;
    intervalMs: number;
    targetCount: number;
    confirmedCount: number;
    mismatchCount: number;
    mismatchHistory: MismatchHistoryEntry[];
    log: VerifyLogEntry[];
  };
}

/** MonitoringController.getConsistencyStatus() - 동기화/검증 배치의 최근 실행 스냅샷. */
export async function fetchConsistencyStatus(): Promise<CouponConsistencyStatusResponse> {
  const res = await fetch(`${API_BASE}/api/admin/monitoring/consistency-status`);
  return parseApiResponse<CouponConsistencyStatusResponse>(res, "정합성 동기화/검증 상태 조회 실패");
}

/**
 * couponId가 DB에 아예 없을 때(CouponErrorCode.COUPON_NOT_FOUND) 백엔드가 던지는 404 - 오픈된 쿠폰이
 * 하나도 없는 정상 상태에서도 발생하므로, 진짜 연결 실패(네트워크 오류/5xx)와 구분해서 다뤄야 한다.
 */
export class MonitoringCouponNotFoundError extends Error { }

export async function fetchMonitoringDashboard(couponId: number): Promise<MonitoringDashboardResponse> {
  const res = await fetch(`${API_BASE}/api/admin/monitoring/coupons/${couponId}`);

  if (res.status === 404) {
    throw new MonitoringCouponNotFoundError(`쿠폰 #${couponId}을(를) 찾을 수 없습니다`);
  }
  if (!res.ok) {
    throw new Error(`모니터링 조회 실패 (HTTP ${res.status})`);
  }

  const body: ApiResponse<MonitoringDashboardResponse> = await res.json();
  if (!body.success || !body.data) {
    throw new Error(body.message ?? "모니터링 조회 실패");
  }

  return body.data;
}

/** 대시보드 지표(HTTP/발급/DB insert 집계)만 0으로 되돌린다 - Redis/DB 실 데이터는 그대로 유지. */
export async function resetMonitoringMetrics(): Promise<void> {
  const res = await fetch(`${API_BASE}/api/admin/monitoring/reset`, { method: "POST" });
  if (!res.ok) {
    throw new Error(`지표 초기화 실패 (HTTP ${res.status})`);
  }
}

/**
 * backend DummyDataAll.Counts와 1:1로 대응한다.
 * *LoadMs는 GET counts(단순 조회)일 땐 null - 방금 재적재(loadDummyData)한 응답에만 채워진다.
 */
export interface DummyDataCounts {
  userCount: number;
  couponCount: number;
  couponIssueCount: number;
  userLoadMs: number | null;
  couponIssueLoadMs: number | null;
  totalMs: number | null;
}

/** 지금 DB에 실제로 있는 건수 - 새로고침 직후에도 마지막 적재 결과를 알 수 있다. */
export async function fetchDummyDataCounts(): Promise<DummyDataCounts> {
  const res = await fetch(`${API_BASE}/api/admin/dummy-data/counts`);
  if (!res.ok) {
    throw new Error(`더미데이터 현황 조회 실패 (HTTP ${res.status})`);
  }

  const body: ApiResponse<DummyDataCounts> = await res.json();
  if (!body.success || !body.data) {
    throw new Error(body.message ?? "더미데이터 현황 조회 실패");
  }

  return body.data;
}

/**
 * 적재 진행 상태 - 적재는 수십 초 걸려서 백엔드가 백그라운드로 돌리고 이 상태를 폴링용으로 둔다.
 * 새로고침해도 loading이 서버 상태 그대로라 "적재 중"이 사라지지 않는다(로컬 UI 상태가 아님).
 */
export interface DummyDataStatus {
  loading: boolean;
  startedAt: string | null;
  finishedAt: string | null;
  /** 이번 적재를 시작하기 직전 DB 스냅샷 - Before/After 비교용. 새 적재를 시작할 때마다 갱신된다. */
  before: DummyDataCounts | null;
  lastResult: DummyDataCounts | null;
  lastError: string | null;
}

export async function fetchDummyDataStatus(): Promise<DummyDataStatus> {
  const res = await fetch(`${API_BASE}/api/admin/dummy-data/status`);
  return parseApiResponse<DummyDataStatus>(res, "더미데이터 상태 조회 실패");
}

/**
 * 더미데이터 재적재를 "시작"만 시킨다 - OPEN 쿠폰이 있으면 백엔드가 거부하고(진행 중 캠페인과 TRUNCATE
 * 충돌 방지), 이미 적재 중이어도 거부한다. 완료 여부/결과는 fetchDummyDataStatus() 폴링으로 안다.
 */
export async function loadDummyData(): Promise<DummyDataStatus> {
  const res = await fetch(`${API_BASE}/api/admin/dummy-data/reload`, { method: "POST" });
  return parseApiResponse<DummyDataStatus>(res, "데이터 적재 실패");
}

/**
 * couponId 스트림의 PEL을 강제로 비운다(DB에는 반영 안 됨) - 재시도해도 영원히 실패할 메시지를
 * 명시적으로 포기하는 최후 수단. 성공하면 실제로 ACK된 건수를 반환한다.
 */
export async function drainPendingStream(couponId: number): Promise<number> {
  const res = await fetch(`${API_BASE}/api/admin/monitoring/coupons/${couponId}/stream/drain`, { method: "POST" });
  if (!res.ok) {
    throw new Error(`PEL 드레인 실패 (HTTP ${res.status})`);
  }

  const body: ApiResponse<number> = await res.json();
  if (!body.success || body.data === null) {
    throw new Error(body.message ?? "PEL 드레인 실패");
  }

  return body.data;
}

export type CouponStatus = "READY" | "OPEN" | "CLOSE";

export interface CouponSummary {
  id: number;
  name: string;
  status: CouponStatus;
  totalQuantity: number;
  /** 오픈 예약 시각 - 수동으로만 오픈/클로즈해온 쿠폰이면 null. */
  startAt: string | null;
  /** 마감 예약 시각 - 수동으로만 오픈/클로즈해온 쿠폰이면 null. */
  endAt: string | null;
}

/**
 * status를 안 주면 전체, 주면 그 상태만 - 쿠폰이 아무리 많아도 OPEN/READY는 소수라 필터를 걸면
 * 응답 크기가 전체 쿠폰 수와 무관하게 작게 유지된다(대시보드 선택지/오픈 대상 목록에서 이 필터를 씀).
 */
export async function fetchCoupons(status?: CouponStatus): Promise<CouponSummary[]> {
  const qs = status ? `?status=${status}` : "";
  const res = await fetch(`${API_BASE}/api/admin/coupons${qs}`);
  if (!res.ok) {
    throw new Error(`쿠폰 목록 조회 실패 (HTTP ${res.status})`);
  }

  const body: ApiResponse<CouponSummary[]> = await res.json();
  if (!body.success || !body.data) {
    throw new Error(body.message ?? "쿠폰 목록 조회 실패");
  }

  return body.data;
}

/** backend CouponResponse(domain/coupon/dto)와 1:1로 대응한다. */
export interface CouponDetail {
  id: number;
  name: string;
  totalQuantity: number;
  startAt: string | null;
  endAt: string | null;
  status: CouponStatus;
}

/**
 * 관리자 쿠폰 생성 - AdminCouponController.create(). READY로만 등록되고 Redis 재고는 안 건드린다
 * (initStock은 openCoupon 시점에 별도로 일어남 - 아래 openCoupon 참고). startAt/endAt을 채워서
 * "예약"해두면 S010/S011 스케줄러가 그 시각에 맞춰 자동으로 오픈/클로즈한다.
 */
export async function createCoupon(
  name: string,
  totalQuantity: number,
  startAt?: string | null,
  endAt?: string | null,
): Promise<CouponDetail> {
  const res = await fetch(`${API_BASE}/admin/coupons`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ name, totalQuantity, startAt: startAt || null, endAt: endAt || null }),
  });
  return parseApiResponse<CouponDetail>(res, "쿠폰 생성 실패");
}

/** 관리자 쿠폰 수동 오픈 - CouponController.openCoupon(). READY→OPEN 전환 + Redis 재고 초기화까지 여기서 됨. */
export async function openCoupon(couponId: number): Promise<void> {
  const res = await fetch(`${API_BASE}/api/admin/coupons/${couponId}/open`, { method: "POST" });
  if (!res.ok) {
    const body = await res.json().catch(() => null);
    throw new Error(body?.message ?? `쿠폰 오픈 실패 (HTTP ${res.status})`);
  }
}

/** 관리자 쿠폰 수동 클로즈 - CouponController.closeCoupon(). OPEN→CLOSE 전환 + Redis 재고/발급 SET 정리. */
export async function closeCoupon(couponId: number): Promise<void> {
  const res = await fetch(`${API_BASE}/api/admin/coupons/${couponId}/close`, { method: "POST" });
  if (!res.ok) {
    const body = await res.json().catch(() => null);
    throw new Error(body?.message ?? `쿠폰 클로즈 실패 (HTTP ${res.status})`);
  }
}

/**
 * AdminCouponController.update() - A004. READY 쿠폰은 startAt(오픈 예약)까지 자유롭게 수정 가능하고,
 * OPEN 쿠폰은 endAt(마감 예약)만 허용된다(백엔드에서 강제). 지금은 "예약 오픈"/"예약 마감" 용도로만
 * 쓴다 - totalQuantity는 안 건드린다.
 */
export async function updateCouponPeriod(
  couponId: number,
  period: { startAt?: string; endAt?: string },
): Promise<CouponDetail> {
  const res = await fetch(`${API_BASE}/admin/coupons/${couponId}`, {
    method: "PATCH",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ totalQuantity: null, startAt: period.startAt ?? null, endAt: period.endAt ?? null }),
  });
  return parseApiResponse<CouponDetail>(res, "쿠폰 예약 수정 실패");
}

/** backend K6ScenarioResponse(domain/k6test/dto)와 1:1로 대응한다. backend K6Scenario enum이 유일한 소스. */
export interface K6ScenarioDto {
  id: string;
  file: string;
  name: string;
  description: string;
  /** 값 기준 안내 - 설명 아래에 목록으로 표시한다. */
  guides: string[];
  rampUp: string;
  hold: string;
  targetVus: string;
  /** true면 실행 전에 재고(stock)/동시접속(maxVus)을 숫자로 입력받아야 한다. */
  configurable: boolean;
  /** true면 요청배수·유입방식·연타 같은 추가 조건까지 입력받는다(main_test.js). */
  advanced: boolean;
}

/** k6 실행 옵션 - 시나리오가 실제로 읽는 것만 채워 보내면 되고, 안 채운 건 스크립트 기본값이 쓰인다. */
export interface K6RunOptions {
  stock?: number;
  maxVus?: number;
  requestRatio?: number;
  /** 배수 대신 총 요청 수를 직접 지정한다. requestRatio와 함께 오면 이 값이 우선한다(백엔드/스크립트와 동일 규칙). */
  requestCount?: number;
  arrival?: "burst" | "even" | "ramp";
  duration?: number;
  spamRatio?: number;
  spamClicks?: number;
}

/** backend K6StatusResponse(domain/k6test/dto)와 1:1로 대응한다. */
export interface K6StatusResponse {
  running: boolean;
  scenarioId: string | null;
  scenarioFile: string | null;
  couponId: number | null;
  startedAt: string | null;
  exitCode: number | null;
}

/**
 * 성공 시 ApiResponse<T>, 실패 시 ErrorResponse(code/message/timestamp) 둘 중 하나가 온다 - 두 경우 모두
 * message 필드는 있으므로 그것만 읽어서 에러 메시지로 쓴다.
 */
async function parseApiResponse<T>(res: Response, fallbackMessage: string): Promise<T> {
  const body = await res.json().catch(() => null);
  if (!res.ok || !body?.success || body.data === null || body.data === undefined) {
    throw new Error(body?.message ?? `${fallbackMessage} (HTTP ${res.status})`);
  }
  return body.data as T;
}

export async function fetchK6Scenarios(): Promise<K6ScenarioDto[]> {
  const res = await fetch(`${API_BASE}/api/admin/k6/scenarios`);
  return parseApiResponse<K6ScenarioDto[]>(res, "k6 시나리오 목록 조회 실패");
}

/** stock/maxVus는 configurable 시나리오(동시성 정합성 검증)에서만 의미가 있다 - 그 외엔 생략해도 된다. */
export async function runK6Scenario(
  scenarioId: string,
  couponId: number,
  options: K6RunOptions = {}
): Promise<K6StatusResponse> {
  const res = await fetch(`${API_BASE}/api/admin/k6/run`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ scenarioId, couponId, ...options }),
  });
  return parseApiResponse<K6StatusResponse>(res, "k6 실행 실패");
}

export async function stopK6Scenario(): Promise<K6StatusResponse> {
  const res = await fetch(`${API_BASE}/api/admin/k6/stop`, { method: "POST" });
  return parseApiResponse<K6StatusResponse>(res, "k6 중지 실패");
}

export async function fetchK6Status(): Promise<K6StatusResponse> {
  const res = await fetch(`${API_BASE}/api/admin/k6/status`);
  return parseApiResponse<K6StatusResponse>(res, "k6 상태 조회 실패");
}

/** backend MyCouponResponse(domain/couponissue/dto)와 1:1로 대응한다. */
export interface MyCouponResponse {
  issueId: number;
  couponId: number;
  couponName: string;
  status: "ISSUED" | "USED" | "CANCELED" | "EXPIRED";
  rank: number;
  issuedAt: string;
  usedAt: string | null;
  cancelAt: string | null;
  expiredAt: string | null;
}

/** CouponIssueController.getMyCoupons() - 최근 발급 순. */
export async function fetchMyCoupons(userId: number): Promise<MyCouponResponse[]> {
  const res = await fetch(`${API_BASE}/api/my/coupons?userId=${userId}`);
  return parseApiResponse<MyCouponResponse[]>(res, "발급 이력 조회 실패");
}

/**
 * 내 쿠폰 사용 - CouponIssueController.useCoupon(). 본인 소유가 아니거나(CI002) 이미
 * USED/CANCELED/EXPIRED 상태면(CI003, 409) 거부된다.
 */
export async function useMyCoupon(issueId: number, userId: number): Promise<void> {
  const res = await fetch(`${API_BASE}/api/my/coupons/${issueId}/use?userId=${userId}`, { method: "POST" });
  if (!res.ok) {
    const body = await res.json().catch(() => null);
    throw new Error(body?.message ?? `쿠폰 사용 실패 (HTTP ${res.status})`);
  }
}

/**
 * 내 쿠폰 취소 - CouponIssueController.cancelCoupon(). 본인 소유가 아니거나(CI002) 이미
 * USED/CANCELED/EXPIRED 상태면(CI003, 409) 거부된다.
 */
export async function cancelMyCoupon(issueId: number, userId: number): Promise<void> {
  const res = await fetch(`${API_BASE}/api/my/coupons/${issueId}/cancel?userId=${userId}`, { method: "POST" });
  if (!res.ok) {
    const body = await res.json().catch(() => null);
    throw new Error(body?.message ?? `쿠폰 취소 실패 (HTTP ${res.status})`);
  }
}

/**
 * 쿠폰 발급 신청 - CouponController.requestIssue() (/coupons/{id}/issue).
 */
export async function requestCouponIssue(couponId: number, userId: number): Promise<void> {
  const res = await fetch(`${API_BASE}/coupons/${couponId}/issue?userId=${userId}`, { method: "POST" });
  if (!res.ok) {
    const body = await res.json().catch(() => null);
    throw new Error(body?.message ?? `발급 신청 실패 (HTTP ${res.status})`);
  }
}

/** 시나리오 7: 관리자 — 특정 쿠폰의 전체 발급 이력. */
export interface CouponIssueHistoryResponse {
  issueId: number;
  userId: number;
  userEmail: string | null;
  userName: string | null;
  couponId: number;
  status: "ISSUED" | "USED" | "CANCELED" | "EXPIRED";
  issuedAt: string;
  usedAt: string | null;
  canceledAt: string | null;
  expiredAt: string | null;
}

export interface CouponIssueHistoryPage {
  items: CouponIssueHistoryResponse[];
  page: number;
  totalPages: number;
  totalElements: number;
}

export async function fetchCouponIssues(
  couponId: number,
  page: number,
  size: number,
): Promise<CouponIssueHistoryPage> {
  const res = await fetch(`${API_BASE}/api/admin/coupons/${couponId}/issues?page=${page}&size=${size}`);
  return parseApiResponse<CouponIssueHistoryPage>(res, "발급 이력 조회 실패");
}

/**
 * backend CouponFairnessTimelineEntry(domain/couponissue/dto)와 1:1로 대응한다.
 * rank는 Redis fairness-log의 원자적 처리 순번 - DB issued_at(비동기 배치 반영)보다 실제 처리
 * 순서를 정확히 반영한다. outcome이 SUCCESS가 아니면(SOLDOUT/DUPLICATE) DB 행 자체가 없어서
 * status/issuedAt이 null이다.
 */
export interface CouponFairnessTimelineEntry {
  rank: number;
  userId: number;
  outcome: "SUCCESS" | "SOLDOUT" | "DUPLICATE";
  status: "ISSUED" | "USED" | "CANCELED" | "EXPIRED" | null;
  issuedAt: string | null;
  /** 컨트롤러 도달 시각(epoch ms). 레거시 항목이면 null. */
  controllerEnteredAtMs: number | null;
  /** Redis 게이트 진입 시각(epoch ms). 레거시 항목이면 null. */
  gateEnteredAtMs: number | null;
  /** Redis 서버가 TIME으로 찍은 처리 시각(epoch microseconds, Redis TIME 그대로). 레거시 항목이면 null. */
  redisTimeMicros: number | null;
  /** 컨트롤러 도달 → Redis 게이트 진입 소요(ms). 레거시 항목이면 null. */
  gateWaitMs: number | null;
  /** Redis 게이트 진입 → Lua 처리 소요(ms). 서버 간 시계 차이로 음수가 나올 수 있다. 레거시 항목이면 null. */
  redisWaitMs: number | null;
  /** 마스킹된 사용자 이름. users에 없으면 null. */
  userName: string | null;
  /** 마스킹된 사용자 이메일. users에 없으면 null. */
  userEmail: string | null;
}

/**
 * backend CouponFairnessTimelinePage(domain/couponissue/dto)와 1:1로 대응한다. page는 1부터
 * 시작하고, totalPages는 totalElements를 size로 올림 나눗셈한 값이다(로그가 0건이면 0).
 */
export interface CouponFairnessTimelinePage {
  items: CouponFairnessTimelineEntry[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

/**
 * backend CouponFairnessOutcomeFilter(domain/couponissue/dto)와 1:1로 대응한다. FAILURE는
 * SOLDOUT/DUPLICATE를 합쳐 보여주는 편의 필터 - 백엔드가 발급 시도 시점에 outcome별 ZSET을 함께
 * 채워두므로, 어떤 필터로 조회하든 전체 로그 스캔 없이 offset/size만으로 응답한다.
 */
export type CouponFairnessOutcomeFilter = "ALL" | "SUCCESS" | "FAILURE" | "DUPLICATE" | "SOLDOUT";

/**
 * AdminCouponController.getFairnessTimeline() - 1-based page/size 오프셋 페이지네이션. 전체를
 * 한 번에 안 받아오므로 로그가 아무리 쌓여도 요청 하나의 비용은 size에만 비례한다. outcome 필터가
 * 걸리면 totalElements/totalPages도 그 필터 기준으로 계산돼서 내려온다.
 */
export async function fetchFairnessTimeline(
  couponId: number,
  page: number,
  size: number,
  outcome: CouponFairnessOutcomeFilter = "ALL",
): Promise<CouponFairnessTimelinePage> {
  const res = await fetch(
    `${API_BASE}/api/admin/coupons/${couponId}/fairness/timeline?page=${page}&size=${size}&outcome=${outcome}`,
  );
  return parseApiResponse<CouponFairnessTimelinePage>(res, "선착순 타임라인 조회 실패");
}

/**
 * backend CouponFairnessReport(domain/coupon/dto)와 1:1로 대응한다.
 * fairness-log 전체를 훑어서, 품절 판정 이후에 성공이 끼어든 적(inversion, 새치기)이 있는지 센다.
 * inversionCount === 0이면 fair === true.
 */
export interface CouponFairnessReport {
  couponId: number;
  totalAttempts: number;
  inversionCount: number;
  fair: boolean;
}

/** AdminCouponController.getCouponFairness() - analyzeFairness()를 그대로 노출. */
export async function fetchCouponFairness(couponId: number): Promise<CouponFairnessReport> {
  const res = await fetch(`${API_BASE}/api/admin/coupons/${couponId}/fairness`);
  return parseApiResponse<CouponFairnessReport>(res, "선착순 공정성 검증 조회 실패");
}

