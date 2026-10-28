/** Requester home: real MockMvc-rendered templates, static assets, isolated Edge.
 * Run focused tests with -Dsmartfix.ui.captureDir=target/requester-home-preview first.
 * Does not access a business database or authenticate real accounts.
 */
import http from 'node:http';
import { readFile, mkdir, writeFile } from 'node:fs/promises';
import path from 'node:path';
import { launch } from './browser-cdp.mjs';

const output = path.resolve(process.env.SMARTFIX_UI_PREVIEW_DIR || 'target/requester-home-preview');
const staticRoot = path.resolve('src/main/resources/static');
const pages = new Set(['home.html','home-empty.html','home-one.html','home-populated.html','announcements.html']);
const checks = [];
const check = (name, value) => { checks.push({ name, passed:Boolean(value) }); if (!value) throw new Error(name); };
await mkdir(output, { recursive:true });
await readFile(path.join(output,'home.html'));
const server = http.createServer(async (request,response) => {
  try {
    const url = new URL(request.url,'http://localhost');
    const page = url.pathname==='/' ? 'home.html' : url.pathname==='/announcements' ? 'announcements.html' : url.pathname.slice(1);
    const file = pages.has(page) ? path.join(output,page) : path.resolve(staticRoot,'.'+decodeURIComponent(url.pathname));
    if (!pages.has(page) && !file.startsWith(staticRoot+path.sep)) { response.writeHead(403).end();return; }
    const body = await readFile(file);
    const types = { '.html':'text/html; charset=utf-8','.css':'text/css','.js':'application/javascript','.svg':'image/svg+xml','.png':'image/png' };
    response.writeHead(200,{'Content-Type':types[path.extname(file)] || 'application/octet-stream'});response.end(body);
  } catch { response.writeHead(404).end(); }
});
await new Promise(resolve=>server.listen(17884,'127.0.0.1',resolve));
let browser;
try {
  browser = await launch({port:9346,height:1100});
  const evaluate = code=>browser.evaluate(`return (${code});`);
  const go = suffix=>browser.goto('http://127.0.0.1:17884'+suffix);
  const waitFor = async source=>{
    for(let attempt=0;attempt<40;attempt++) { if(await evaluate(source)) return true;await browser.sleep(100); }
    return false;
  };
  const key = async (key,code)=>{
    await browser.send('Input.dispatchKeyEvent',{type:'keyDown',key,code:key,windowsVirtualKeyCode:code});
    await browser.send('Input.dispatchKeyEvent',{type:'keyUp',key,code:key,windowsVirtualKeyCode:code});
  };
  await browser.send('Page.addScriptToEvaluateOnNewDocument',{source:'window.uiErrors=[];window.addEventListener("error",e=>window.uiErrors.push(e.message));'});
  await go('/');await browser.sleep(700);
  check('Welcome is the first of seven real highlights',await evaluate("document.querySelectorAll('[data-home-slide]').length===7 && document.querySelector('[data-home-slide] h1').textContent.includes('Welcome back')"));
  check('Redundant standalone notice and instruction cards removed',await evaluate("!document.querySelector('.announcement-carousel') && !document.querySelector('.dashboard-how') && !document.body.innerText.includes('You’re all caught up')"));
  check('Initial navigation state',await evaluate("document.querySelector('[data-home-prev]').disabled && !document.querySelector('[data-home-next]').disabled && document.querySelector('[data-home-position]').textContent==='1 / 7'"));
  await browser.click('[data-home-next]');
  check('Next moves into first real announcement',await waitFor("document.querySelector('[data-home-track]').scrollLeft>document.querySelector('[data-home-track]').clientWidth*.99 && document.querySelector('[data-home-position]').textContent==='2 / 7'"));
  check('Generated notice PNG loads',await waitFor("document.querySelector('.home-slide__art--notice').naturalWidth>0"));
  await browser.screenshot(path.join(output,'home-announcement-1440.png'));
  await browser.click('[data-home-go="3"]');
  check('Pagination selects the fourth highlight',await waitFor("document.querySelector('[data-home-position]').textContent==='4 / 7' && document.querySelector('[data-home-go=\"3\"]').getAttribute('aria-pressed')==='true'"));
  await evaluate("document.querySelector('[data-home-track]').focus()");await key('End',35);
  check('Keyboard End reaches last highlight',await waitFor("document.querySelector('[data-home-position]').textContent==='7 / 7' && document.querySelector('[data-home-next]').disabled"));
  await key('Home',36);await key('ArrowRight',39);
  check('Keyboard Home and ArrowRight navigate',await waitFor("document.querySelector('[data-home-position]').textContent==='2 / 7'"));
  await evaluate("document.querySelector('[data-home-track]').scrollTo({left:document.querySelector('[data-home-track]').clientWidth*2,behavior:'instant'})");
  check('Native swipe or trackpad scroll synchronizes controls',await waitFor("document.querySelector('[data-home-position]').textContent==='3 / 7'"));
  await browser.setViewport(1000,1000);
  check('Resize keeps current highlight aligned',await waitFor("Math.abs(document.querySelector('[data-home-track]').scrollLeft-document.querySelector('[data-home-track]').clientWidth*2)<2"));
  await browser.send('Emulation.setEmulatedMedia',{features:[{name:'prefers-reduced-motion',value:'reduce'}]});
  await browser.click('[data-home-next]');
  check('Reduced motion retains navigation',await waitFor("document.querySelector('[data-home-position]').textContent==='4 / 7'"));
  for(const route of ['/home-empty.html','/home-one.html','/home-populated.html','/']) {
    await go(route);
    for(const width of [1440,1200,1000,901,768,390,320]) {
      await browser.setViewport(width,1100);await browser.evaluate('window.scrollTo(0,0);');await browser.sleep(150);
      check(`${route} ${width}px has no document overflow`,await evaluate('document.documentElement.scrollWidth<=innerWidth+1'));
      check(`${route} ${width}px has unique IDs and one h1`,await evaluate("(()=>{const ids=[...document.querySelectorAll('[id]')].map(e=>e.id);return new Set(ids).size===ids.length && document.querySelectorAll('h1').length===1})()"));
      check(`${route} ${width}px modules share left and right edges`,await evaluate("(()=>{const hero=document.querySelector('.home-feature').getBoundingClientRect(),work=document.querySelector('.home-workspace').getBoundingClientRect(),community=document.querySelector('.home-community').getBoundingClientRect();return [work,community].every(r=>Math.abs(r.left-hero.left)<1 && Math.abs(r.right-hero.right)<1)})()"));
      if(width>900) check(`${route} ${width}px brand, navigation and account align vertically`,await evaluate("(()=>{const r=['.appbar__brand','.appbar__links','.appbar--requester .userbox'].map(s=>document.querySelector(s).getBoundingClientRect());return r.every(b=>Math.abs(b.top-r[0].top)<1 && Math.abs(b.height-r[0].height)<1)})()"));
      if(width<=520) check(`${route} ${width}px welcome heading and PNG do not overlap`,await evaluate("(()=>{const a=document.querySelector('.home-slide h1').getBoundingClientRect(),b=document.querySelector('.home-slide__art').getBoundingClientRect();return a.right<=b.left+1 || a.top>=b.bottom || a.bottom<=b.top})()"));
      if(width>760) check(`${route} ${width}px request and map cards align top and bottom`,await evaluate("(()=>{const a=document.querySelector('.home-requests').getBoundingClientRect(),b=document.querySelector('.home-map').getBoundingClientRect();return Math.abs(a.top-b.top)<1 && Math.abs(a.bottom-b.bottom)<1})()"));
      if([1440,390,320].includes(width)) await browser.screenshot(path.join(output,`${route==='/'?'home':route.slice(1,-5)}-${width}.png`));
    }
    check(`${route} has no runtime errors`,await evaluate('window.uiErrors.length===0'));
    if(route==='/home-empty.html') {
      check('No announcements still has two usable slides and controls',await evaluate("document.querySelectorAll('[data-home-slide]').length===2 && !document.querySelector('[data-home-controls]').hidden && document.querySelector('[data-home-track]').hasAttribute('tabindex')"));
      await browser.click('[data-home-next]');
      check('Empty announcement page is reachable with next',await waitFor("document.querySelector('[data-home-position]').textContent==='2 / 2' && document.querySelector('[data-home-track]').scrollLeft>document.querySelector('[data-home-track]').clientWidth*.99"));
      check('Empty page accurately states no current notices',await evaluate("document.querySelector('.home-slide--notice').innerText.includes('No current announcements.') && !!document.querySelector('.home-slide--notice a[href=\"/announcements\"]')"));
    }
    if(route==='/home-one.html') check('One announcement becomes welcome plus notice; unsafe text stays text',await evaluate("document.querySelectorAll('[data-home-slide]').length===2 && document.querySelector('.home-slide--notice p').textContent.includes('<script>') && !window.uiErrors.length"));
    if(route==='/home-populated.html') check('Three real recent request links; no invented metrics',await evaluate("document.querySelectorAll('.home-request').length===3 && !!document.querySelector('a[href=\"/requests/SF-2026-000001\"]') && document.querySelector('.home-request time').textContent.includes('09:00')"));
  }
  await browser.setViewport(1440,1100);await go('/home-empty.html');
  const drag = async (from,to) => {
    const box=await evaluate("(()=>{const r=document.querySelector('[data-home-track]').getBoundingClientRect();return {x:r.left,y:r.top,w:r.width,h:r.height}})()");
    const y=box.y+box.h-20;
    await browser.send('Input.dispatchMouseEvent',{type:'mouseMoved',x:box.x+box.w*from,y});
    await browser.send('Input.dispatchMouseEvent',{type:'mousePressed',x:box.x+box.w*from,y,button:'left',buttons:1,clickCount:1});
    for(let i=1;i<=12;i++) { await browser.send('Input.dispatchMouseEvent',{type:'mouseMoved',x:box.x+box.w*(from+(to-from)*i/12),y,button:'left',buttons:1});await browser.sleep(15); }
    await browser.send('Input.dispatchMouseEvent',{type:'mouseReleased',x:box.x+box.w*to,y,button:'left',buttons:0,clickCount:1});
  };
  await drag(.75,.25);
  check('Desktop mouse drag reaches empty notice slide',await waitFor("document.querySelector('[data-home-position]').textContent==='2 / 2' && document.querySelector('[data-home-track]').scrollLeft>document.querySelector('[data-home-track]').clientWidth*.99"));
  await drag(.25,.75);
  check('Desktop drag back restores welcome and exits dragging state',await waitFor("document.querySelector('[data-home-position]').textContent==='1 / 2' && !document.querySelector('.is-dragging') && document.querySelector('[data-home-track]').scrollLeft<2"));
  check('Navigation has four real destinations with one current page',await evaluate("document.querySelectorAll('.appbar--requester .appbar__link').length===4 && document.querySelectorAll('.appbar--requester [aria-current=page]').length===1 && document.querySelector('.appbar--requester [aria-current=page]').getAttribute('href')==='/'"));
  await evaluate('window.scrollTo(0,300)');
  check('Sticky header gains separation after scrolling',await waitFor("document.querySelector('.appbar--requester').classList.contains('is-scrolled')"));
  await browser.setViewport(390,1100);await go('/home-empty.html');
  await browser.click('[data-nav-toggle]');
  check('Mobile menu opens with accurate ARIA and two-column links',await evaluate("document.querySelector('[data-nav-toggle]').getAttribute('aria-expanded')==='true' && document.querySelector('[data-nav-toggle]').getAttribute('aria-label')==='Close navigation menu' && getComputedStyle(document.querySelector('.appbar__links')).gridTemplateColumns.split(' ').length===2"));
  check('Mobile menu has no horizontal overflow',await evaluate('document.documentElement.scrollWidth<=innerWidth+1'));
  await key('Escape',27);
  check('Escape closes menu and returns focus',await evaluate("document.querySelector('[data-nav-toggle]').getAttribute('aria-expanded')==='false' && document.activeElement===document.querySelector('[data-nav-toggle]')"));
  await browser.click('[data-nav-toggle]');await browser.screenshot(path.join(output,'navigation-mobile-open.png'));
  await browser.click('[data-home-track]');
  check('Clicking outside closes mobile menu',await evaluate("document.querySelector('[data-nav-toggle]').getAttribute('aria-expanded')==='false'"));
  await browser.click('[data-nav-toggle]');await browser.setViewport(1440,1100);await browser.sleep(200);
  check('Desktop resize resets mobile menu state',await evaluate("document.querySelector('[data-nav-toggle]').getAttribute('aria-expanded')==='false' && !document.querySelector('[data-nav-panel]').classList.contains('is-open')"));
  await browser.setViewport(390,1100);await go('/home-empty.html');
  await browser.send('Emulation.setTouchEmulationEnabled',{enabled:true,maxTouchPoints:1});
  const touchBox=await evaluate("(()=>{const r=document.querySelector('[data-home-track]').getBoundingClientRect();return {x:r.left,y:r.top,w:r.width}})()");
  const touchY=touchBox.y+150;
  await browser.send('Input.dispatchTouchEvent',{type:'touchStart',touchPoints:[{x:touchBox.x+touchBox.w*.85,y:touchY,id:1}]});
  for(let i=1;i<=10;i++) { await browser.send('Input.dispatchTouchEvent',{type:'touchMove',touchPoints:[{x:touchBox.x+touchBox.w*(.85-.7*i/10),y:touchY,id:1}]});await browser.sleep(25); }
  await browser.send('Input.dispatchTouchEvent',{type:'touchEnd',touchPoints:[]});
  check('Emulated native touch swipe reaches empty announcement slide',await waitFor("document.querySelector('[data-home-position]').textContent==='2 / 2' && document.querySelector('[data-home-track]').scrollLeft>document.querySelector('[data-home-track]').clientWidth*.99"));
  await browser.send('Emulation.setTouchEmulationEnabled',{enabled:false});
  await browser.send('Emulation.setScriptExecutionDisabled',{value:true});await go('/');
  check('Without JS, native scrolling and real announcement links remain',await evaluate("getComputedStyle(document.querySelector('[data-home-track]')).overflowX==='auto' && document.querySelector('[data-home-controls]').hidden && document.querySelectorAll('.home-slide--notice a').length===6"));
  console.log(`${checks.length} requester-home browser checks passed (rendered templates; no business DB touched).`);
} finally {
  await writeFile(path.join(output,'browser-results.json'),JSON.stringify({scope:'Real MockMvc templates with synthetic service data, isolated Edge',checks},null,2));
  if(browser) await browser.close();await new Promise(resolve=>server.close(resolve));
}
