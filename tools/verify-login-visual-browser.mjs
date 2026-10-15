/** Real MockMvc template + static assets, isolated Edge; no app accounts or DB changes.
 * Generate captures with SecurityConfigTest and -Dsmartfix.login.captureDir=target/login-visual-preview.
 */
import http from 'node:http';
import path from 'node:path';
import { readFile, mkdir, writeFile } from 'node:fs/promises';
import { launch } from './browser-cdp.mjs';

const output = path.resolve('target/login-visual-preview');
const assets = path.resolve('src/main/resources/static');
await mkdir(output, { recursive: true });
await readFile(path.join(output, 'login.html'));
const checks = [];
const check = (name, value) => { checks.push({ name, passed: Boolean(value) }); if (!value) throw new Error(name); };
const server = http.createServer(async (request, response) => {
  try {
    const url = new URL(request.url, 'http://localhost');
    const page = url.pathname === '/login' ? path.join(output, url.searchParams.has('error') ? 'login-error.html' : 'login.html') : null;
    const file = page || path.resolve(assets, '.' + decodeURIComponent(url.pathname));
    if (!page && !file.startsWith(assets + path.sep)) { response.writeHead(403).end(); return; }
    const body = await readFile(file);
    const types = { '.html': 'text/html; charset=utf-8', '.css': 'text/css', '.js': 'application/javascript', '.png': 'image/png', '.svg': 'image/svg+xml' };
    response.writeHead(200, { 'Content-Type': types[path.extname(file)] || 'application/octet-stream' }); response.end(body);
  } catch { response.writeHead(404).end(); }
});
await new Promise(resolve => server.listen(17885, '127.0.0.1', resolve));
let browser;
try {
  browser = await launch({ port: 9347, height: 1050 });
  const evaluate = code => browser.evaluate(`return (${code});`);
  const go = suffix => browser.goto('http://127.0.0.1:17885/login' + suffix);
  await browser.send('Page.addScriptToEvaluateOnNewDocument', { source: 'window.uiErrors=[];window.addEventListener("error",e=>window.uiErrors.push(e.message));' });
  await go('');
  check('PNG loads with real dimensions', await evaluate("document.querySelector('.login-scene__image').naturalWidth>0"));
  check('Password remains hidden and native role selection exists', await evaluate("document.querySelector('#password').type==='password' && document.querySelectorAll('.account-type input[type=radio]').length===3"));
  check('Removed account/contact paragraphs absent', await evaluate("!document.body.innerText.includes('Contact your administrator') && !document.body.innerText.includes('Technician and administrator accounts are created')"));
  for (const feature of ['track', 'connect', 'report']) {
    await browser.click(`[data-login-feature=${feature}]`);
    check(`Feature ${feature} updates exclusive pressed state`, await evaluate(`document.querySelectorAll('.login-feature[aria-pressed=true]').length===1 && document.querySelector('[data-login-feature=${feature}]').getAttribute('aria-pressed')==='true'`));
  }
  await evaluate("document.querySelector('[data-login-feature=connect]').focus()"); await browser.press('Enter');
  check('Keyboard activates feature copy', await evaluate("document.querySelector('#login-feature-copy').textContent.includes('community')"));
  await browser.click('#password-toggle');
  check('Reveal control shows password without submitting', await evaluate("document.querySelector('#password').type==='text' && document.querySelector('#password-toggle').getAttribute('aria-pressed')==='true' && !document.querySelector('.login-submit').disabled"));
  await browser.click('#password-toggle');
  check('Password can be concealed again', await evaluate("document.querySelector('#password').type==='password'"));
  await browser.click('label[for=accountType-technician]');
  check('Role choice preserves actual submitted field', await evaluate("[...new FormData(document.querySelector('.auth__form-fields')).entries()].some(([name,value])=>name==='accountType' && value==='TECHNICIAN')"));
  await evaluate("document.querySelector('#password').dispatchEvent(new KeyboardEvent('keyup',{key:'a',modifierCapsLock:true}))");
  check('Caps Lock hint appears when enabled', await evaluate("!document.querySelector('#login-caps-lock').hidden"));
  await evaluate("document.querySelector('#password').dispatchEvent(new Event('blur'))");
  check('Caps Lock hint clears on leaving field', await evaluate("document.querySelector('#login-caps-lock').hidden"));
  await browser.click('.login-submit');
  check('Empty form validation does not enter pending state', await evaluate("!document.querySelector('.login-submit').disabled && !document.querySelector('form').checkValidity()"));
  await browser.typeInto('#username', 'visual.fixture'); await browser.typeInto('#password', 'SyntheticOnly9!');
  await evaluate("document.querySelector('form').addEventListener('submit',event=>event.preventDefault(),{once:true})");
  await browser.click('.login-submit');
  check('Valid submit gives busy feedback without duplicate submission', await evaluate("document.querySelector('.login-submit').disabled && document.querySelector('form').getAttribute('aria-busy')==='true' && document.querySelector('[data-login-submit-status]').textContent.includes('Signing in')"));
  await evaluate("window.dispatchEvent(new PageTransitionEvent('pageshow',{persisted:true}))");
  check('Back-forward restoration resets pending and password visibility', await evaluate("!document.querySelector('.login-submit').disabled && document.querySelector('#password').type==='password' && !document.querySelector('form').hasAttribute('aria-busy')"));
  await go('');
  for (const [width, height] of [[1920,1080],[1440,1000],[1024,900],[768,1000],[390,1000],[320,900]]) {
    await browser.setViewport(width,height); await browser.sleep(700);
    check(`${width}px has no page overflow`, await evaluate('document.documentElement.scrollWidth<=innerWidth+1'));
    check(`${width}px IDs unique and one main heading`, await evaluate("(()=>{const ids=[...document.querySelectorAll('[id]')].map(e=>e.id);return new Set(ids).size===ids.length && document.querySelectorAll('h1').length===1})()"));
    check(`${width}px role labels stay within their controls`, await evaluate("[...document.querySelectorAll('.account-type__face')].every(e=>e.scrollWidth<=e.clientWidth+1)"));
    if (width <= 900) check(`${width}px decorative image does not cover the heading`, await evaluate("(()=>{const copy=document.querySelector('.auth__intro-body').getBoundingClientRect(),image=document.querySelector('.login-scene').getBoundingClientRect();return image.left>=copy.right || image.top>=copy.bottom})()"));
    await browser.screenshot(path.join(output, `login-${width}.png`));
  }
  await browser.setViewport(1440,1000);
  await browser.send('Emulation.setEmulatedMedia', { features: [{ name:'prefers-reduced-motion',value:'reduce' }] });
  check('Reduced motion removes artwork and form animation', await evaluate("getComputedStyle(document.querySelector('.login-scene')).animationName==='none' && getComputedStyle(document.querySelector('.auth__form')).animationName==='none'"));
  await go('?error=type&accountType=TECHNICIAN');
  check('Error page retains generic message and selected role', await evaluate("document.body.innerText.includes('Unable to sign in with the selected account type') && document.querySelector('#accountType-technician').checked"));
  await browser.screenshot(path.join(output,'login-error.png'));
  check('No JavaScript runtime errors', await evaluate('window.uiErrors.length===0'));
  await browser.send('Emulation.setScriptExecutionDisabled',{ value:true }); await go('');
  check('Without JavaScript real form and image remain; enhancement buttons stay hidden', await evaluate("document.querySelector('form').method==='post' && document.querySelector('[data-login-explorer]').hidden && document.querySelector('#password-toggle').hidden && document.querySelector('.login-scene__image').naturalWidth>0"));
  console.log(`${checks.length} login browser checks passed (rendered templates, isolated fixtures).`);
} finally {
  await writeFile(path.join(output,'browser-results.json'),JSON.stringify({ scope:'Real rendered templates; synthetic form input; isolated Edge; no live login/DB',checks },null,2));
  if (browser) await browser.close();
  await new Promise(resolve => server.close(resolve));
}
