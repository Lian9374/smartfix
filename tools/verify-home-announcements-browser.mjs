/** Verify real MockMvc-rendered templates, without changing a running app or business DB.
 * First run the three focused Maven test classes with
 * -Dsmartfix.ui.captureDir=target/ui-polish-preview, then run this file with Node.
 * This is template/interaction acceptance, not a live database acceptance test.
 */
import http from 'node:http';
import { readFile, mkdir, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { launch } from './browser-cdp.mjs';

const root = process.cwd();
const output = path.join(root, 'target/ui-polish-preview');
const staticRoot = path.join(root, 'src/main/resources/static');
const checks = [];
const check = (name, ok) => {
  checks.push({ name, ok: Boolean(ok) });
  if (!ok) throw new Error(name);
};
await mkdir(output, { recursive: true });
await readFile(path.join(output, 'home.html'));
const server = http.createServer(async (request, response) => {
  try {
    const url = new URL(request.url, 'http://localhost');
    const file = url.pathname === '/' ? path.join(output, 'home.html')
      : url.pathname === '/announcements' ? path.join(output, 'announcements.html')
      : path.resolve(staticRoot, '.' + decodeURIComponent(url.pathname));
    if (!file.startsWith(staticRoot + path.sep) && !['home.html', 'announcements.html'].some(name => file === path.join(output, name))) {
      response.writeHead(403).end(); return;
    }
    const types = { '.html': 'text/html; charset=utf-8', '.js': 'application/javascript', '.css': 'text/css', '.png': 'image/png', '.svg': 'image/svg+xml' };
    response.writeHead(200, { 'Content-Type': types[path.extname(file)] || 'application/octet-stream' });
    response.end(await readFile(file));
  } catch { response.writeHead(404).end(); }
});
await new Promise(resolve => server.listen(17884, '127.0.0.1', resolve));
let browser;
try {
  browser = await launch({ port: 9346 });
  const errors = [];
  // CDP events are also observable through the browser-side error handler below.
  await browser.send('Page.addScriptToEvaluateOnNewDocument', { source: "window.uiErrors=[];window.addEventListener('error',e=>window.uiErrors.push(e.message));" });
  await browser.goto('http://127.0.0.1:17884/');
  const evaluate = source => browser.evaluate(`return (${source});`);
  const settle = () => browser.sleep(1200);
  check('Real template exposes six slides', await evaluate("document.querySelectorAll('.announcement-slide').length===6"));
  check('Initial previous disabled; next available', await evaluate("document.querySelector('[data-announcement-prev]').disabled && !document.querySelector('[data-announcement-next]').disabled"));
  await browser.click('[data-announcement-next]'); await settle();
  check('Next changes slide and accessible position', await evaluate("document.querySelector('[data-announcement-position]').textContent==='2 / 6'"));
  await browser.click('[data-announcement-prev]'); await settle();
  check('Previous returns to first notice', await evaluate("document.querySelector('[data-announcement-position]').textContent==='1 / 6'"));
  await evaluate("document.querySelector('[data-announcement-track]').focus()");
  const key = async (key, code) => {
    await browser.send('Input.dispatchKeyEvent', { type: 'keyDown', key, code: key, windowsVirtualKeyCode: code });
    await browser.send('Input.dispatchKeyEvent', { type: 'keyUp', key, code: key, windowsVirtualKeyCode: code });
    await settle();
  };
  await key('End', 35);
  check('Keyboard End reaches final notice with next disabled', await evaluate("document.querySelector('[data-announcement-position]').textContent==='6 / 6' && document.querySelector('[data-announcement-next]').disabled"));
  await key('Home', 36); await key('ArrowRight', 39);
  check('Keyboard Home and ArrowRight navigate', await evaluate("document.querySelector('[data-announcement-position]').textContent==='2 / 6'"));
  await evaluate("document.querySelector('[data-announcement-track]').scrollLeft=document.querySelector('[data-announcement-track]').clientWidth*2"); await settle();
  check('Native horizontal scroll updates controls', await evaluate("document.querySelector('[data-announcement-position]').textContent==='3 / 6'"));
  await browser.send('Emulation.setEmulatedMedia', { features: [{ name: 'prefers-reduced-motion', value: 'reduce' }] });
  await browser.click('[data-announcement-next]'); await settle();
  check('Reduced-motion navigation still works', await evaluate("document.querySelector('[data-announcement-position]').textContent==='4 / 6'"));
  for (const route of ['/', '/announcements']) {
    await browser.goto('http://127.0.0.1:17884' + route);
    for (const width of [1440, 1000, 768, 390, 320]) {
      await browser.setViewport(width, 1000); await settle();
      check(`${route} ${width}px has no page overflow`, await evaluate('document.documentElement.scrollWidth<=innerWidth+1'));
      check(`${route} ${width}px has unique IDs and one main heading`, await evaluate("(()=>{const ids=[...document.querySelectorAll('[id]')].map(e=>e.id);return new Set(ids).size===ids.length && document.querySelectorAll('h1').length===1})()"));
      if ([1440, 390].includes(width)) await browser.screenshot(path.join(output, `${route === '/' ? 'home' : 'announcements'}-${width}.png`));
    }
    errors.push(...await evaluate('window.uiErrors'));
  }
  await browser.send('Emulation.setScriptExecutionDisabled', { value: true });
  await browser.goto('http://127.0.0.1:17884/');
  check('No-JS notices retain native overflow and full content links', await evaluate("getComputedStyle(document.querySelector('.announcement-track')).overflowX==='auto' && document.querySelectorAll('.announcement-slide a').length===6 && document.querySelector('[data-announcement-controls]').hidden"));
  check('No runtime script errors', errors.length === 0);
  console.log(`${checks.length} browser checks passed (rendered templates; no business DB touched).`);
} finally {
  await writeFile(path.join(output, 'browser-results.json'), JSON.stringify({ scope: 'MockMvc-rendered templates, synthetic service data, real Edge', checks }, null, 2));
  if (browser) await browser.close();
  await new Promise(resolve => server.close(resolve));
}
