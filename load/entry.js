// 응모 API 부하 테스트. 100 → 500 → 1,000 RPS로 올린다.
// 앱은 응모 중(OPEN) 상태여야 한다: EVENT_START_AT은 과거, EVENT_END_AT은 테스트가 끝난 뒤로 둔다.
// 실행: docker compose -f monitoring/docker-compose.yml --profile load run --rm k6
//      (k6를 직접 설치했다면) BASE_URL=http://localhost:8081 k6 run load/entry.js
import http from 'k6/http';
import { check } from 'k6';

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8081';

export const options = {
  scenarios: {
    entries: {
      executor: 'ramping-arrival-rate',
      startRate: 50,
      timeUnit: '1s',
      preAllocatedVUs: 300,
      maxVUs: 3000,
      stages: [
        { target: 100, duration: '20s' },
        { target: 100, duration: '30s' },
        { target: 500, duration: '20s' },
        { target: 500, duration: '30s' },
        { target: 1000, duration: '20s' },
        { target: 1000, duration: '60s' },
        { target: 0, duration: '10s' },
      ],
      exec: 'enter',
    },
    status: {
      executor: 'constant-arrival-rate',
      rate: 20,
      timeUnit: '1s',
      duration: '190s',
      preAllocatedVUs: 20,
      exec: 'status',
    },
  },
  // 첫 기준선. 측정 결과를 보고 조정한다
  thresholds: {
    'http_req_failed{scenario:entries}': ['rate<0.01'],
    'http_req_duration{scenario:entries}': ['p(95)<200', 'p(99)<500'],
    'checks{scenario:entries}': ['rate>0.99'],
  },
};

function uuidv4() {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, (c) => {
    const r = (Math.random() * 16) | 0;
    return (c === 'x' ? r : (r & 0x3) | 0x8).toString(16);
  });
}

const clientIds = {};

export function enter() {
  // 반복마다 다른 아이디라 1초 제한(429)에 걸리지 않는다
  const instagramId = `k6_${__VU}_${__ITER}`.slice(0, 30);
  clientIds[__VU] = clientIds[__VU] || uuidv4();
  const res = http.post(
    `${BASE_URL}/api/event/entries`,
    JSON.stringify({ requestId: uuidv4(), instagramId, ageConfirmed: true, agreeInstagramCollection: true }),
    { headers: { 'Content-Type': 'application/json', 'X-Client-Id': clientIds[__VU] }, tags: { name: 'entry' } },
  );
  check(res, {
    'status 201': (r) => r.status === 201,
    'receiptNumber': (r) => r.status === 201 && r.json('receiptNumber') > 0,
  });
}

export function status() {
  const res = http.get(`${BASE_URL}/api/event/status`, { tags: { name: 'status' } });
  check(res, { 'status 200': (r) => r.status === 200 });
}
