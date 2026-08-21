import http from 'k6/http';
import { check, sleep } from 'k6';
import { uuidv4 } from 'https://jslib.k6.io/k6-utils/1.4.0/index.js';

export const options = {
  stages: [
    { duration: '30s', target: 10 },
    { duration: '1m', target: 50 },
    { duration: '30s', target: 0 },
  ],
  thresholds: {
    http_req_duration: ['p(95)<500'],
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const TOKEN = __ENV.TOKEN || '';
const FROM_ACCOUNT = __ENV.FROM_ACCOUNT || '';
const TO_ACCOUNT = __ENV.TO_ACCOUNT || '';

export default function () {
  const res = http.post(
    `${BASE_URL}/api/v1/transactions/transfer`,
    JSON.stringify({ fromAccountId: FROM_ACCOUNT, toAccountId: TO_ACCOUNT, amount: 1, description: 'Load test' }),
    {
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${TOKEN}`,
        'Idempotency-Key': uuidv4(),
      },
    }
  );
  check(res, { 'status is 202': (r) => r.status === 202 });
  sleep(2);
}
