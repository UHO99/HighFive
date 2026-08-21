// 쿠폰 발급 컨트롤러(POST /{couponId}/issue) 의 "요청 수락" 속도를 측정하는 k6 스크립트.

// 실행 예:
//   k6 run k6/kafka_test.js
//   k6 run -e BASE_URL=http://localhost:8080 -e COUPON_ID=1 -e TARGET_VUS=500 k6/kafka_test.js

import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';
import exec from 'k6/execution';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const COUPON_ID = __ENV.COUPON_ID || '1';
const TARGET_VUS = Number(__ENV.TARGET_VUS || 20_000);

const accepted = new Counter('issue_accepted');
const rejected = new Counter('issue_rejected');

export const options = {
    scenarios: {
        coupon_issue_burst: {
            executor: 'ramping-vus',
            startVUs: 0,
            stages: [
                { duration: '10s', target: TARGET_VUS }, // 램프업
                { duration: '30s', target: TARGET_VUS },  // 부하 유지 (동시 접속 시뮬레이션)
                { duration: '5s', target: 0 },            // 램프다운
            ],
        },
    },
    thresholds: {
        http_req_failed: ['rate<0.01'],       // 실패율 1% 미만
        http_req_duration: ['p(95)<300'],     // 95%가 300ms 이내 응답
    },
};

export default function () {
    // (__VU * 1000 + __ITER) % 20000 was the old formula - VU가 20,000까지 올라가면
    // VU*1000 mod 20000의 경우의 수가 20개뿐이라 서로 다른 VU 수백~수천 개가 같은 userId로
    // 동시에 요청을 쏴서 "중복 발급" 거부가 실제 부하와 무관하게 대량으로 발생했다.
    // iterationInTest는 전체 실행 동안 VU를 넘나들며 절대 겹치지 않는 값이라 이 문제가 없다.
    const USER_POOL_SIZE = Number(__ENV.USER_POOL_SIZE || 1_000_000);
    const userId = (exec.scenario.iterationInTest % USER_POOL_SIZE) + 1;
    const res = http.post(`${BASE_URL}/${COUPON_ID}/issue?userId=${userId}`); // Kafka
    // const res = http.post(`${BASE_URL}/${COUPON_ID}/issue?userId=${userId}`); // Redis
    // const res = http.post(`${BASE_URL}/${COUPON_ID}/issue?userId=${userId}`); // CRUD

    const ok = check(res, {
        '202 Accepted': (r) => r.status === 202,
    });

    if (ok) {
        accepted.add(1);
    } else {
        rejected.add(1);
    }
}

export function teardown() {
    const res = http.get(`${BASE_URL}/${COUPON_ID}`);
    console.log(`[teardown] GET /${COUPON_ID} -> status=${res.status} body=${res.body}`);
}
