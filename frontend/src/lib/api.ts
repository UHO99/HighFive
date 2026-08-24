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
 * couponId가 DB에 아예 없을 때(CouponErrorCode.COUPON_NOT_FOUND) 백엔드가 던지는 404 - 오픈된 쿠폰이
 * 하나도 없는 정상 상태에서도 발생하므로, 진짜 연결 실패(네트워크 오류/5xx)와 구분해서 다뤄야 한다.
 */
export class MonitoringCouponNotFoundError extends Error {}

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
 * (initStock은 openCoupon 시점에 별도로 일어남 - 아래 openCoupon 참고).
 */
export async function createCoupon(name: string, totalQuantity: number): Promise<CouponDetail> {
  const res = await fetch(`${API_BASE}/admin/coupons`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ name, totalQuantity }),
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

/** backend K6ScenarioResponse(domain/k6test/dto)와 1:1로 대응한다. backend K6Scenario enum이 유일한 소스. */
export interface K6ScenarioDto {
  id: string;
  file: string;
  name: string;
  description: string;
  rampUp: string;
  hold: string;
  targetVus: string;
  /** true면 실행 전에 재고(stock)/동시접속(maxVus)을 숫자로 입력받아야 한다. */
  configurable: boolean;
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
  stock?: number,
  maxVus?: number
): Promise<K6StatusResponse> {
  const res = await fetch(`${API_BASE}/api/admin/k6/run`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ scenarioId, couponId, stock, maxVus }),
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
\n
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
  couponId: number;
  status: "ISSUED" | "USED" | "CANCELED" | "EXPIRED";
  issuedAt: string;
  usedAt: string | null;
  canceledAt: string | null;
  expiredAt: string | null;
}

\n\n