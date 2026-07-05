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
  // タスク一覧画面（認証なし・公開ページ）への到達確認
  const res = http.get(`${BASE_URL}/tasks/list.xhtml`);
  check(res, {
    'tasks/list.xhtml status is 200': (r) => r.status === 200,
    'tasks/list.xhtml has table': (r) => r.body.includes('listForm'),
  });
  sleep(1);
}
