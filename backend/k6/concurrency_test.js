// 동시 요청 정합성 검증 - 한정된 재고에 여러 유저가 동시에 발급을 요청했을 때 초과 발급이
// 없는지 확인한다. "성공 건수 == 재고 수량"만 보는 정합성 테스트라, 성능 측정이 목적일 땐
// MAX_VUS를 키워서 동시성을 실제 부하 수준으로 올려야 의미가 있다.
//
// 사전 준비
//   1) 서버 실행
//   2) 테스트용 쿠폰을 STOCK 값과 동일한 재고로 OPEN
//        POST /api/admin/coupons/{COUPON_ID}/open
//   3) coupon_issue 에 해당 쿠폰의 기존 발급 이력이 없는지 확인
//        (있으면 SISMEMBER 중복 체크에 걸려 결과가 왜곡됨)
//
// 실행 (규모는 STOCK/MAX_VUS로 조절 - 재고10,000에 20,000명이 "동시에" 요청하는 본 규모까지 그대로 확장 가능)
//   k6 run backend/k6/concurrency_test.js
//   k6 run -e COUPON_ID=5 -e STOCK=20 -e MAX_VUS=50 backend/k6/concurrency_test.js
//   k6 run -e COUPON_ID=5 -e STOCK=10000 -e MAX_VUS=20000 backend/k6/concurrency_test.js

import http from 'k6/http';
import { Counter } from 'k6/metrics';
import exec from 'k6/execution';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const COUPON_ID = __ENV.COUPON_ID || '1';
const STOCK = Number(__ENV.STOCK || 20);
// 더미 유저 수. users 테이블에 실제로 존재하는 id 범위(1 ~ USER_COUNT)
const USER_COUNT = Number(__ENV.USER_COUNT || 1_000_000);
// 동시 접속(VU) 상한. 기본값은 소규모 검증용 - 본 규모 성능측정 땐 REQUEST_COUNT까지 올려야 "동시에" 재현된다.
const MAX_VUS = Number(__ENV.MAX_VUS || 50);

// 재고의 2배를 요청해 "초과 요청 상황"을 실제로 만든다.
const REQUEST_COUNT = STOCK * 2;

const issued    = new Counter('issue_success');    // 202
const soldOut   = new Counter('issue_sold_out');    // 409 RD002
const notStocked = new Counter('issue_not_stocked'); // 409 RD001 (오픈 안 하고 실행한 경우)
const notOpen   = new Counter('issue_not_open');    // 400 CP002
const duplicate = new Counter('issue_duplicate');   // 409 CI001 (원래는 안 나와야 함 - 유저가 전부 다르므로)
const unexpected = new Counter('issue_unexpected');

export const options = {
  scenarios: {
    concurrency_test: {
      executor: 'shared-iterations',   // REQUEST_COUNT 번이 정확히 실행되도록 보장
      vus: Math.min(REQUEST_COUNT, MAX_VUS),
      iterations: REQUEST_COUNT,
      // 본 규모에서는 병목 때문에 응답이 느려지는 것 자체가 관찰 대상이므로 넉넉하게 잡는다.
      maxDuration: __ENV.MAX_DURATION || '2m',
    },
  },
};

export default function () {
  // 매 요청마다 서로 다른 유저 (users id: 1 ~ USER_COUNT)
  const userId = (exec.scenario.iterationInTest % USER_COUNT) + 1;

  const res = http.post(`${BASE_URL}/coupons/${COUPON_ID}/issue?userId=${userId}`);

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

export function teardown() {
  console.log('');
  console.log('== 판정 기준 ==');
  console.log(`재고(STOCK) = ${STOCK} 이면, issue_success 카운터가 정확히 ${STOCK} 이어야 정상.`);
  console.log('그 외 요청은 전부 issue_sold_out 이어야 하고, issue_duplicate / issue_unexpected 는 0이어야 함.');
  console.log('실행 후 아래도 함께 확인:');
  console.log(`  redis-cli GET coupon:stock:${COUPON_ID}         -> 0 이어야 함`);
  console.log(`  redis-cli SMEMBERS coupon:issued:${COUPON_ID}   -> ${STOCK}명이어야 함`);
  console.log(`  SELECT COUNT(*) FROM coupon_issue WHERE coupon_id=${COUPON_ID}; -> ${STOCK} (비동기 반영, 몇 초 대기 후 확인)`);
  console.log(`  GET /api/admin/monitoring/coupons/${COUPON_ID} -> overIssueMonitor.matched: true 여야 함`);
}
