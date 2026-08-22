import http from 'k6/http';
import { check, sleep } from 'k6';
import { Rate } from 'k6/metrics';

const errorRate = new Rate('errors');

export const options = {
  stages: [
    { duration: '30s', target: 20 },
    { duration: '1m', target: 100 },
    { duration: '30s', target: 0 },
  ],
  thresholds: {
    http_req_duration: ['p(95)<200'],
    errors: ['rate<0.01'],
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
const TOKEN = __ENV.TOKEN || '';
const ACCOUNT_ID = __ENV.ACCOUNT_ID || '';

export default function () {
  const res = http.get(`${BASE_URL}/api/v1/accounts/${ACCOUNT_ID}/balance`, {
    headers: { Authorization: `Bearer ${TOKEN}` },
  });
  const success = check(res, { 'status is 200': (r) => r.status === 200 });
  errorRate.add(!success);
  sleep(1);
}
