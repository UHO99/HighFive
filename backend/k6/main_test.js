// 메인 부하테스트 - 선착순 발급의 두 가지 보장을 여러 조건으로 바꿔가며 검증한다.
//   (1) 초과 발급 0건  : 성공 건수가 정확히 재고와 같은가
//   (2) 1인 최대 1매   : 같은 유저가 연타해도 한 번만 나가는가  ← SPAM_RATIO로 능동 검증
// 소규모 기능검증만 할 거면 concurrency_test.js를 쓰면 된다.
//
// ── 대시보드에서 조절하는 값 ─────────────────────────────────────────
//   대상 쿠폰    COUPON_ID                  다이얼로그에서 고른 쿠폰
//   재고         STOCK          (기본 20)   고른 쿠폰의 실제 재고가 자동으로 들어간다
//   동시접속     MAX_VUS        (기본 50)   본 규모는 요청 수와 같게(예: 20000)
//   요청 배수    REQUEST_RATIO  (기본 2)    요청 유저 수 = 재고 × 이 값
//                                             1 → 재고와 딱 맞음(전원 성공이 정상) / 10 → 극한 경쟁
//   유입 방식    ARRIVAL        (기본 burst) burst = 한꺼번에(오픈 직후) / even = DURATION초에 걸쳐
//   유입 시간    DURATION       (기본 10)   ARRIVAL=even 일 때만
//   연타 비율    SPAM_RATIO     (기본 0)    0.3 = 유저의 30%가 연타
//   연타 횟수    SPAM_CLICKS    (기본 3)    SPAM_RATIO > 0 일 때만
//
// ── CLI로만 조절하는 값 ─────────────────────────────────────────────
//   BASE_URL      (기본 http://localhost:8080)
//   USER_COUNT    (기본 100만)  users 테이블에 실제로 존재하는 id 범위(1 ~ 이 값)
//   MAX_DURATION  (기본 2m)     ARRIVAL=burst 일 때 전체 타임아웃
//
// ── 실행 ────────────────────────────────────────────────────────────
//   대시보드 : + → 테스트 → "메인 부하테스트"
//   CLI      : k6 run -e STOCK=100 -e MAX_VUS=100 backend/k6/main_test.js
//   ※ 대상 쿠폰을 STOCK과 같은 재고로 미리 OPEN해두고, 기존 발급 이력이 없어야 판정이 맞는다.

import http from 'k6/http';
import { Counter } from 'k6/metrics';
import exec from 'k6/execution';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const COUPON_ID = __ENV.COUPON_ID || '1';
const STOCK = Number(__ENV.STOCK || 20);
const REQUEST_RATIO = Number(__ENV.REQUEST_RATIO || 2);
const MAX_VUS = Number(__ENV.MAX_VUS || 50);
const ARRIVAL = __ENV.ARRIVAL || 'burst';
const DURATION = Number(__ENV.DURATION || 10);
const SPAM_RATIO = Number(__ENV.SPAM_RATIO || 0);
const SPAM_CLICKS = Number(__ENV.SPAM_CLICKS || 3);
const USER_COUNT = Number(__ENV.USER_COUNT || 1_000_000);

// 유저 수. 연타를 켜면 한 유저가 여러 번 누르므로 실제 HTTP 요청 수는 이보다 많다.
const ITERATIONS = STOCK * REQUEST_RATIO;

const issued     = new Counter('issue_success');      // 202
const soldOut    = new Counter('issue_sold_out');     // 409 RD002
const notStocked = new Counter('issue_not_stocked');  // 409 RD001 (쿠폰을 안 열고 실행한 경우)
const notOpen    = new Counter('issue_not_open');     // 400 CP002
const duplicate  = new Counter('issue_duplicate');    // 409 CI001 (1인 1매 거부)
const unexpected = new Counter('issue_unexpected');

export const options = {
  scenarios: ARRIVAL === 'even'
    ? {
        // rate를 올림하므로 총 요청이 ITERATIONS보다 약간 많을 수 있다 - 넘치는 건 전부 품절로
        // 잡히므로 "성공 == 재고" 판정에는 영향이 없다.
        even_arrival: {
          executor: 'constant-arrival-rate',
          rate: Math.max(1, Math.ceil(ITERATIONS / DURATION)),
          timeUnit: '1s',
          duration: `${DURATION}s`,
          preAllocatedVUs: Math.min(ITERATIONS, MAX_VUS),
          maxVUs: MAX_VUS,
        },
      }
    : {
        // 정확히 ITERATIONS번만 실행되도록 보장하면서 가능한 한 빨리 쏟아붓는다.
        burst_arrival: {
          executor: 'shared-iterations',
          vus: Math.min(ITERATIONS, MAX_VUS),
          iterations: ITERATIONS,
          maxDuration: __ENV.MAX_DURATION || '2m',
        },
      },
};

function classify(res) {
  switch (res.status) {
    case 202: issued.add(1); break;
    case 400: notOpen.add(1); break;
    case 409:
      if (res.body && res.body.includes('RD001')) notStocked.add(1);
      else if (res.body && res.body.includes('RD002')) soldOut.add(1);
      else duplicate.add(1);
      break;
    default:
      unexpected.add(1);
      console.error(`예상 못한 응답: ${res.status} ${res.body}`);
  }
}

export default function () {
  const iter = exec.scenario.iterationInTest;
  // VU 번호가 아니라 전체 반복 순번 기준 - VU가 여러 번 돌아도 의도치 않게 같은 사람이 되지 않는다.
  const userId = (iter % USER_COUNT) + 1;
  const url = `${BASE_URL}/coupons/${COUPON_ID}/issue?userId=${userId}`;

  // 난수 대신 순번 기준이라 같은 파라미터면 항상 같은 결과가 재현된다.
  const isSpammer = SPAM_RATIO > 0 && (iter % 100) < SPAM_RATIO * 100;

  if (!isSpammer) {
    classify(http.post(url));
    return;
  }

  // 같은 userId로 batch 동시 발사. 순차 요청이면 두 번째부터 SET에 이미 있어서 쉽게 걸리지만,
  // 동시 도착은 재고 차감의 원자성이 실제로 지켜지는지를 시험한다.
  const requests = [];
  for (let i = 0; i < SPAM_CLICKS; i++) requests.push(['POST', url]);
  http.batch(requests).forEach(classify);
}

export function teardown() {
  console.log('');
  console.log('== 실행 조건 ==');
  console.log(`  재고 ${STOCK} · 요청배수 ×${REQUEST_RATIO} (유저 ${ITERATIONS}명) · 동시접속 최대 ${MAX_VUS}`);
  console.log(`  유입 방식 ${ARRIVAL}${ARRIVAL === 'even' ? ` (${DURATION}초에 걸쳐)` : ' (최대한 빨리)'}`);
  console.log(`  연타 ${SPAM_RATIO > 0 ? `유저의 ${SPAM_RATIO * 100}%가 ${SPAM_CLICKS}회씩 동시 클릭` : '없음 (전원 1회)'}`);
  console.log('');
  console.log('== 판정 기준 ==');

  if (REQUEST_RATIO <= 1) {
    console.log(`  issue_success = ${ITERATIONS} (요청한 유저 전원 성공), issue_sold_out = 0 이어야 정상.`);
  } else {
    console.log(`  issue_success = ${STOCK} (정확히 재고만큼만), 나머지 요청은 issue_sold_out 이어야 정상.`);
  }

  if (SPAM_RATIO > 0) {
    console.log('  issue_duplicate > 0 이 정상이다 - 연타를 켰으므로 중복 거부가 나와야 한다.');
    console.log('  중요: 연타를 했는데도 issue_success가 재고를 넘지 않아야 "1인 최대 1매"가 지켜진 것.');
  } else {
    console.log('  issue_duplicate = 0 이어야 정상 (모든 유저가 서로 다르므로 중복이 나올 이유가 없다).');
  }
  console.log('  issue_unexpected 는 어떤 조건에서도 0 이어야 한다.');

  console.log('');
  console.log('실행 후 아래도 함께 확인:');
  console.log(`  redis-cli SCARD coupon:issued:${COUPON_ID}   -> 성공 건수와 같아야 함 (한 사람이 두 번 들어가지 않았는지)`);
  console.log(`  SELECT COUNT(*) FROM coupon_issue WHERE coupon_id=${COUPON_ID};  (비동기 반영, 몇 초 대기 후)`);
  console.log(`  GET /api/admin/monitoring/coupons/${COUPON_ID} -> overIssueMonitor.matched: true 여야 함`);
}
