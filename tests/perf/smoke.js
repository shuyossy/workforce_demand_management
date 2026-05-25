import http from 'k6/http';
import { check, sleep } from 'k6';

export const options = {
  vus: 5,
  duration: '30s',
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<1000'],
  },
};

const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080/rcb';

export default function () {
  // ログイン画面（公開ページ）への到達確認
  const res = http.get(`${BASE_URL}/login.xhtml`);
  check(res, {
    'login.xhtml status is 200': (r) => r.status === 200,
    'login.xhtml has form': (r) => r.body.includes('loginForm'),
  });
  sleep(1);
}
