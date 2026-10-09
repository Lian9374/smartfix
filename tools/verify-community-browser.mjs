// End-to-end acceptance against a fresh, dedicated PostgreSQL test database.
// Every form and navigation action uses Tab/Enter/ArrowDown/Space and real input.
// DOM evaluation only reads evidence; it does not submit forms or move focus.
import {spawn} from 'node:child_process';
import {createWriteStream, existsSync, mkdirSync, writeFileSync} from 'node:fs';
import {fileURLToPath} from 'node:url';
import path from 'node:path';
import {launch} from './browser-cdp.mjs';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const output = path.join(root, 'docs/sprint3/evidence/community-quality');
const jdbc = process.env.COMMUNITY_ACCEPTANCE_DB_URL;
if (!/^jdbc:postgresql:\/\/(127\.0\.0\.1|localhost):\d+\/smartfix_community_acceptance_test$/.test(jdbc || '')) {
  throw new Error('Set COMMUNITY_ACCEPTANCE_DB_URL to a fresh local smartfix_community_acceptance_test database. Existing application databases are refused.');
}
const jar = process.env.COMMUNITY_ACCEPTANCE_JAR || path.join(root, 'target/smartfix-0.0.1-SNAPSHOT.jar');
if (!existsSync(jar)) throw new Error('Build the application with mvn verify first.');
mkdirSync(output, {recursive: true});
const origin = 'http://127.0.0.1:18080';
const password = 'TestPassword9'; // Synthetic credentials used only by this test.
const checks = [];
let app;
let browser;
let launches = 0;
let keyboardActions = 0;
function check(label, condition, detail) {
  checks.push({label, passed: !!condition, ...(detail === undefined ? {} : {detail})});
  if (!condition) throw new Error(label + (detail ? ': ' + JSON.stringify(detail) : ''));
  console.log('PASS ' + label);
}
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));
async function start() {
  try { await fetch(origin + '/actuator/health'); throw new Error('Port 18080 is already in use'); }
  catch (error) { if (error.message === 'Port 18080 is already in use') throw error; }
  launches++;
  const log = createWriteStream(path.join(root, `target/community-browser-app-${launches}.log`));
  app = spawn('java', ['-jar', jar, '--server.address=127.0.0.1', '--server.port=18080',
    '--spring.jpa.hibernate.ddl-auto=validate'], {
    cwd: root, windowsHide: true,
    env: {...process.env, DB_URL: jdbc, DB_USERNAME: process.env.COMMUNITY_ACCEPTANCE_DB_USERNAME || 'smartfix',
      DB_PASSWORD: process.env.COMMUNITY_ACCEPTANCE_DB_PASSWORD || '',
      SMARTFIX_BOOTSTRAP_ADMIN_ENABLED: 'true', SMARTFIX_BOOTSTRAP_ADMIN_USERNAME: 'qa.admin',
      SMARTFIX_BOOTSTRAP_ADMIN_PASSWORD: password, SMARTFIX_BOOTSTRAP_ADMIN_DISPLAY_NAME: 'QA Administrator'},
    stdio: ['ignore', 'pipe', 'pipe']
  });
  app.stdout.pipe(log); app.stderr.pipe(log);
  app.once('error', error => console.error(error.message));
  for (let i = 0; i < 160; i++) {
    if (app.exitCode !== null) throw new Error('Test application exited: see target/community-browser-app-' + launches + '.log');
    try { const response = await fetch(origin + '/actuator/health'); if (response.ok) return; } catch {}
    await sleep(500);
  }
  throw new Error('Test application did not become healthy');
}
async function stop() {
  if (!app || app.exitCode !== null) return;
  const exited = new Promise(resolve => app.once('exit', resolve));
  app.kill();
  await exited;
  app = null;
}
async function go(url) { await browser.goto(origin + url); await browser.sleep(100); }
async function tabTo(selector) {
  for (let i = 0; i < 150; i++) {
    if (await browser.evaluate(`!!document.activeElement?.matches(${JSON.stringify(selector)})`)) return;
    await browser.press('Tab'); keyboardActions++;
  }
  throw new Error('Keyboard cannot reach ' + selector);
}
async function type(selector, value) {
  await tabTo(selector);
  await browser.send('Input.insertText', {text: value}); keyboardActions++;
}
async function activate(selector, settle = true, key = 'Enter') {
  await tabTo(selector); await browser.press(key); keyboardActions++;
  if (settle) { await browser.settle(); await browser.sleep(150); }
}
async function selectFirst(selector) {
  await tabTo(selector); await browser.press('ArrowDown'); await browser.press('Enter'); keyboardActions += 2;
}
async function login(name, role = 'REQUESTER') {
  await browser.clearCookies();
  await go('/login?accountType=' + role);
  await type('#username', name); await type('#password', password);
  await activate('button[type=submit]');
  check('keyboard login ' + name, await browser.evaluate('location.pathname==="/"'));
}
async function register(name, displayName) {
  await browser.clearCookies(); await go('/register');
  await type('#username', name); await type('#displayName', displayName);
  await type('#password', password); await type('#confirmPassword', password);
  await activate('button[type=submit]');
  check('keyboard registration ' + name, await browser.evaluate('location.pathname==="/login"'));
}
const evidenceScreenshots = new Set([
  'question-form-1440', 'question-form-390', 'question-detail-empty-1440',
  'answer-edit-390', 'community-feed-1440', 'my-questions-390', 'my-answers-390',
  'report-queue-1440', 'handled-reports-390', 'restart-handled-reports-1440',
  'validation-errors-390'
]);
async function screenshot(name) {
  if (evidenceScreenshots.has(name)) await browser.screenshot(path.join(output, name + '.png'));
}
async function inspect(name) {
  const state = await browser.evaluate(`(() => {
    const ids=[...document.querySelectorAll('[id]')].map(e=>e.id);
    const missing=[...document.querySelectorAll('[aria-describedby]')].flatMap(e=>(e.getAttribute('aria-describedby')||'').split(/\\s+/).filter(id=>id&&!document.getElementById(id)));
    const labels=[...document.querySelectorAll('input:not([type=hidden]):not([type=submit]),textarea,select')].filter(e=>!e.labels?.length&&!e.getAttribute('aria-label')).map(e=>e.id||e.name);
    return {heading:document.querySelectorAll('h1').length,unique:ids.length===new Set(ids).size,missing,labels,errors:window.__qaErrors||[]};
  })()`);
  check(name + ' heading / unique IDs / labels / described-by / scripts',
    state.heading === 1 && state.unique && !state.missing.length && !state.labels.length && !state.errors.length, state);
  const contrast = await browser.evaluate(`(() => {
    const colors=c=>{const n=c.match(/[\\d.]+/g)?.map(Number);return n&&n.length>=3&&(n.length<4||n[3]===1)?n.slice(0,3):null;};
    const luminance=c=>c.map(v=>{v/=255;return v<=.04045?v/12.92:((v+.055)/1.055)**2.4;}).reduce((n,v,i)=>n+v*[.2126,.7152,.0722][i],0);
    const measured=[];
    document.querySelectorAll('.page-context .page-head__subtitle,.form-field__hint,.form-character-count,.discussion__meta,.btn--primary,.badge,.report-decision__meta').forEach(e=>{
      if(!e.getBoundingClientRect().width||!e.getBoundingClientRect().height)return;
      const style=getComputedStyle(e),fg=colors(style.color);let bg=null;
      for(let parent=e;parent;parent=parent.parentElement){const ps=getComputedStyle(parent);if(ps.backgroundImage!=='none')break;bg=colors(ps.backgroundColor);if(bg)break;}
      if(!fg||!bg)return;
      const ratio=(Math.max(luminance(fg),luminance(bg))+.05)/(Math.min(luminance(fg),luminance(bg))+.05);
      const required=parseFloat(style.fontSize)>=24||(parseFloat(style.fontSize)>=18.66&&parseFloat(style.fontWeight)>=700)?3:4.5;
      measured.push({selector:e.className,ratio:Number(ratio.toFixed(3)),required,passed:ratio>=required});
    });return measured;
  })()`);
  check(name + ' sampled rendered text contrast', contrast.length > 0 && contrast.every(item => item.passed), contrast);
  for (const width of [1440, 1000, 768, 390, 320]) {
    await browser.setViewport(width, 1000);
    check(name + ' no horizontal overflow at ' + width, await browser.evaluate('document.documentElement.scrollWidth<=innerWidth'));
    if (width === 1440 || width === 390) await screenshot(name + '-' + width);
  }
  await browser.setViewport(1440, 1000);
}
async function snapshot() {
  return browser.evaluate(`(() => {
    const title=document.querySelector('h1')?.textContent.trim();
    const accepted=[...document.querySelectorAll('.badge')].filter(e=>e.textContent.trim()==='Accepted').length;
    return {title,accepted,body:document.body.textContent.includes('Restart persistence synthetic answer.')};
  })()`);
}

try {
  await start();
  browser = await launch({port: 9363, width: 1440, height: 1000});
  const evaluate = browser.evaluate;
  browser.evaluate = expression => evaluate('return ' + expression);
  await browser.send('Page.addScriptToEvaluateOnNewDocument', {source: `window.__qaErrors=[];window.addEventListener('error',e=>window.__qaErrors.push(e.message));window.addEventListener('unhandledrejection',e=>window.__qaErrors.push(String(e.reason)));`});
  await register('qa.alice', 'Alice Campus');
  await register('qa.bob', 'Bob Campus');
  await login('qa.alice');
  await go('/community');
  await activate('a[href="/community/questions/new"]');
  await inspect('question-form');
  await type('#title', 'Community keyboard and restart acceptance');
  check('live title character count', await browser.evaluate('document.querySelector("#title-count").textContent.startsWith("41 / ")'));
  await type('#body', 'A synthetic campus question for keyboard, moderation and restart verification.');
  await selectFirst('#category');
  await activate('.community-editor button[type=submit]');
  check('keyboard question posted', await browser.evaluate('/^\\/community\\/questions\\/\\d+$/.test(location.pathname)'));
  const thread = await browser.evaluate('location.pathname');
  await inspect('question-detail-empty');
  await activate('a[href$="/edit"]');
  await inspect('question-edit');
  await activate('.community-editor button[type=submit]');
  await login('qa.bob'); await go(thread);
  await type('#answer-body', 'Restart persistence synthetic answer.');
  await activate('form[action$="/answers"] button[type=submit]');
  check('keyboard answer posted', await browser.evaluate('document.body.textContent.includes("Restart persistence synthetic answer.")'));
  const answerEdit = await browser.evaluate(`document.querySelector(${JSON.stringify('a[href^="/community/answers/"][href$="/edit"]')}).getAttribute("href")`);
  await activate('a[href=' + JSON.stringify(answerEdit) + ']');
  await inspect('answer-edit');
  await activate('.community-editor button[type=submit]');
  await login('qa.alice'); await go(thread);
  await activate('form[action$="/accept"] button[type=submit]');
  check('keyboard answer accepted', (await snapshot()).accepted === 1);
  await go('/community');
  check('feed displays nickname and one visible answer', await browser.evaluate('document.body.textContent.includes("Alice Campus")&&document.body.textContent.includes("1 answer")&&!document.querySelector(".discussion__meta").textContent.includes("Account #")'));
  await inspect('community-feed');
  await go('/community/mine'); await inspect('my-questions');
  check('my community has no duplicated shown count', await browser.evaluate('!document.body.textContent.includes(" shown")'));
  await login('qa.bob'); await go('/community/mine?tab=ANSWERS'); await inspect('my-answers');
  await go(thread);
  await activate('details.report-entry > summary', false);
  await selectFirst('#report-question-reason');
  await type('#report-question-detail', 'Synthetic moderation acceptance evidence.');
  await activate('form[action$="/reports"] button[type=submit]');
  check('keyboard report submitted', await browser.evaluate('document.body.textContent.includes("Thank you. A moderator will review this question.")'));
  await login('qa.admin', 'ADMINISTRATOR');
  await go('/admin/community/reports'); await inspect('report-queue');
  await activate('input[name=decision][value=ACTIONED]', false, 'Space');
  await activate('input[name=hideContent]', false, 'Space');
  await type('input[name=note]', 'Synthetic reviewed and hidden decision.');
  await activate('form[action$="/resolve"] button[type=submit]');
  await activate('.moderation-tabs a[href*=HANDLED]');
  check('handled view has stable restore entry', await browser.evaluate(`!!document.querySelector(${JSON.stringify('form[action$="/restore"]')})&&document.body.textContent.includes("Synthetic reviewed and hidden decision.")`));
  await inspect('handled-reports');
  await activate('form[action$="/restore"] button[type=submit]');
  check('restoration retains handled queue', await browser.evaluate('new URL(location.href).searchParams.get("view")==="HANDLED"'));
  await go('/admin/community/audit');
  check('real audit records hide and restore', await browser.evaluate('document.body.textContent.includes("Content hidden")&&document.body.textContent.includes("Content restored")'));
  await login('qa.alice'); await go(thread);
  const before = await snapshot();
  check('restored question retains its accepted answer', before.accepted === 1 && before.body);
  const firstPid = app.pid;
  await stop(); await start();
  check('application restarted as a different process', app.pid !== firstPid, {before: firstPid, after: app.pid});
  await login('qa.alice'); await go(thread);
  const after = await snapshot();
  check('question / answer / acceptance survive restart', JSON.stringify(before) === JSON.stringify(after), {before, after});
  await login('qa.admin', 'ADMINISTRATOR'); await go('/admin/community/reports?view=HANDLED');
  check('report / decision / restored visibility survive restart', await browser.evaluate(`document.body.textContent.includes("Synthetic reviewed and hidden decision.")&&!!document.querySelector(${JSON.stringify('form[action$="/hide"]')})`));
  await inspect('restart-handled-reports');
  await login('qa.bob'); await go('/notifications');
  check('acceptance notification survives restart', await browser.evaluate('document.body.textContent.toLowerCase().includes("accepted")'));
  await login('qa.alice'); await go('/notifications');
  check('answer notification survives restart', await browser.evaluate('document.body.textContent.toLowerCase().includes("answer")'));
  // Server-side validation remains authoritative: inject an invalid POST using the
  // real CSRF token solely for this negative case, then verify the resulting focus.
  await go('/community/questions/new');
  const invalid = await browser.evaluate(`(async()=>{
    const token=document.querySelector('input[name="_csrf"]').value;
    const response=await fetch('/community/questions',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:new URLSearchParams({_csrf:token,title:'x',body:'x',category:'OTHER'})});
    return {status:response.status,html:await response.text()};
  })()`);
  check('server rejects short fields and renders errors', invalid.status === 200
    && invalid.html.includes('aria-invalid="true"') && invalid.html.includes('form-field--invalid'), {status: invalid.status});
  // Navigation to a data URL is not used: retain origin and load the returned document
  // through CDP, so the production counter/error-focus script runs in the real app origin.
  const frame = await browser.send('Page.getFrameTree');
  await browser.send('Page.setDocumentContent', {frameId: frame.frameTree.frame.id, html: invalid.html});
  await browser.sleep(500);
  check('server error focuses first invalid field', await browser.evaluate('document.activeElement.id==="title"'));
  await inspect('validation-errors');
  writeFileSync(path.join(output, 'browser-results.json'), JSON.stringify({date:new Date().toISOString(), database:'isolated PostgreSQL', keyboardActions, appLaunches:launches, passed:true, checks}, null, 2) + '\n');
  console.log(JSON.stringify({passed:true, checks:checks.length, keyboardActions, appLaunches:launches}));
} catch (error) {
  if (browser) {
    await browser.screenshot(path.join(root, 'target/community-browser-failure.png')).catch(() => {});
  }
  writeFileSync(path.join(root, 'target/community-browser-failure.json'), JSON.stringify({error:error.message, checks}, null, 2));
  throw error;
} finally {
  if (browser) await browser.close();
  await stop();
}
