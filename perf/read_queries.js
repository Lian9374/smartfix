import http from 'k6/http';
import { check, sleep } from 'k6';
import exec from 'k6/execution';
import { SharedArray } from 'k6/data';
import { Counter, Rate, Trend } from 'k6/metrics';

const cfg = JSON.parse(open(__ENV.PERF_CONFIG || '/input/config.json'));
const policy = JSON.parse(open(__ENV.PERF_POLICY || '/input/policy.json'));
const accounts = new SharedArray('isolated-test-accounts', () => JSON.parse(open(__ENV.PERF_ACCOUNTS || '/input/accounts.json')));
const base = cfg.base_url.replace(/\/$/, '');
const host = /^https?:\/\/([^/:]+|\[[^\]]+\])(?::\d+)?$/.exec(base);
if (!host || !cfg.allowed_hosts.includes(host[1]) || /amazonaws\.com|elasticbeanstalk\.com/i.test(base)) throw new Error('Only an explicitly allowed isolated target is permitted');
if (!['smoke', 'warmup', 'load'].includes(cfg.profile)) throw new Error('Unknown performance profile');
const vus = cfg.profile === 'smoke' ? policy.smoke_vus : cfg.profile === 'warmup' ? Math.min(5, cfg.vus) : cfg.vus;
if (accounts.length < vus) throw new Error('One isolated REQUESTER account per VU is required');

const businessRequests = new Counter('business_requests');
const businessLatency = new Trend('business_latency', true);
const businessErrors = new Rate('business_errors');
const authRequests = new Counter('auth_requests');
const authFailures = new Counter('auth_failures');
const reasons = {
  timeout: new Counter('errors_timeout'), transport: new Counter('errors_transport'),
  authentication_or_permission: new Counter('errors_authentication_or_permission'),
  http_4xx: new Counter('errors_http_4xx'), http_5xx: new Counter('errors_http_5xx'),
  unexpected_http: new Counter('errors_unexpected_http'), content: new Counter('errors_content'),
};
const thresholds = { auth_failures: ['count==0'], business_errors: [cfg.profile === 'smoke' ? 'rate==0' : `rate<${policy.max_error_rate_exclusive}`] };
thresholds['http_req_failed{kind:business}'] = [`rate<${policy.max_error_rate_exclusive}`];
if (cfg.profile === 'load') thresholds.business_latency = [`p(95)<=${policy.p95_ms}`, `p(99)<=${policy.p99_ms}`];
for (const endpoint of policy.endpoints) {
  thresholds[`business_requests{endpoint:${endpoint}}`] = [`count>=${cfg.profile === 'load' ? policy.min_load_samples_per_endpoint : 1}`];
  thresholds[`business_errors{endpoint:${endpoint}}`] = [cfg.profile === 'smoke' ? 'rate==0' : `rate<${policy.max_error_rate_exclusive}`];
  // Non-load profiles materialize percentiles for diagnostics, never SLA claims.
  thresholds[`business_latency{endpoint:${endpoint}}`] = cfg.profile === 'load'
    ? [`p(95)<=${policy.p95_ms}`, `p(99)<=${policy.p99_ms}`] : ['min>=0'];
}

export const options = {
  scenarios: { reads: cfg.profile === 'smoke'
    ? { executor: 'per-vu-iterations', vus, iterations: policy.smoke_iterations_per_vu, maxDuration: '30s', gracefulStop: '5s' }
    : { executor: 'constant-vus', vus, duration: `${cfg.profile === 'warmup' ? policy.warmup_seconds : cfg.duration_seconds}s`, gracefulStop: '10s' } },
  thresholds, noCookiesReset: true, maxRedirects: 0,
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(95)', 'p(99)', 'count'],
  systemTags: ['name', 'method', 'status', 'scenario', 'expected_response', 'error_code'],
};

function params(name, expected = 200, endpoint = '') {
  return { redirects: 0, timeout: `${policy.request_timeout_seconds}s`, responseCallback: http.expectedStatuses(expected), tags: { name, kind: endpoint ? 'business' : 'setup_or_auth', endpoint } };
}

export function setup() {
  authFailures.add(0);
  for (const reason of Object.values(reasons)) reason.add(0);
  const r = http.get(`${base}/actuator/health`, params('health'));
  let healthy = false;
  try { healthy = r.status === 200 && r.json().status === 'UP'; } catch (_) { /* no body logging */ }
  if (!healthy) exec.test.abort('Isolated application health prerequisite failed');
}

let authenticated = false;
function login() {
  const account = accounts[exec.vu.idInTest - 1];
  authRequests.add(1);
  const page = http.get(`${base}/login`, params('login_form'));
  const token = page.status === 200 ? page.html().find('input[name="_csrf"]').first().attr('value') : '';
  if (!token) { authFailures.add(1); exec.test.abort('Authentication form/CSRF prerequisite failed'); }
  authRequests.add(1);
  const r = http.post(`${base}/login`, { username: account.username, password: account.password, accountType: 'REQUESTER', _csrf: token }, params('requester_login', 302));
  const ok = r.status === 302 && ['/', `${base}/`].includes(r.headers.Location);
  check(r, { 'REQUESTER login succeeded': () => ok });
  if (!ok) { authFailures.add(1); exec.test.abort('Isolated REQUESTER login failed'); }
  authenticated = true;
}

function reasonFor(r, valid) {
  if (r.status === 0) return /timeout/i.test(r.error || '') ? 'timeout' : 'transport';
  if ([301, 302, 303, 307, 308, 401, 403].includes(r.status)) return 'authentication_or_permission';
  if (r.status >= 500) return 'http_5xx';
  if (r.status >= 400) return 'http_4xx';
  if (r.status !== 200) return 'unexpected_http';
  return valid ? '' : 'content';
}

function query(endpoint, path, validate) {
  const r = http.get(base + path, params(endpoint, 200, endpoint));
  let valid = false;
  try { valid = r.status === 200 && validate(r); } catch (_) { /* count parse failure, never body */ }
  const reason = reasonFor(r, valid);
  businessRequests.add(1, { endpoint });
  businessLatency.add(r.timings.duration, { endpoint });
  businessErrors.add(!!reason, { endpoint });
  if (reason) reasons[reason].add(1);
  check(r, { [`${endpoint}: HTTP200 and business content`]: () => !reason });
}

export default function () {
  if (!authenticated) login();
  query('map', '/campus-map/status', r => {
    const data = r.json();
    return Array.isArray(data.buildings) && data.buildings.length > 0 && typeof data.updatedAt === 'string'
      && typeof data.unmappedRequests === 'number' && data.buildings.every(b => typeof b.faultReports === 'number')
      && (!cfg.fixture_marker || data.unmappedRequests + data.buildings.reduce((n, b) => n + b.faultReports, 0) > 0);
  });
  query('requests', '/requests/mine?page=0&size=20', r => r.body.includes('id="my-requests-title"') && (!cfg.fixture_marker || r.body.includes(cfg.fixture_marker)));
  query('community', `/community?q=${encodeURIComponent(cfg.search_term)}&page=0&size=10`, r => r.body.includes('id="community-title"') && (!cfg.fixture_marker || r.body.includes(cfg.fixture_marker)));
  sleep(cfg.think_seconds);
}

export function handleSummary(data) {
  // The aggregate/raw metrics contain no request bodies, credential fields or cookies.
  return { '/results/summary.json': JSON.stringify(data, null, 2) };
}
