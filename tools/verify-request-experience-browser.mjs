/** Isolated browser checks over real RequestPagesRenderingIT template captures. */
import http from 'node:http';
import path from 'node:path';
import { readFile, writeFile, mkdir } from 'node:fs/promises';
import { launch } from './browser-cdp.mjs';
const output = path.resolve('target/request-experience-preview');
const assets = path.resolve('src/main/resources/static');
const pages = new Set(['request-new.html','request-mine.html','request-empty.html','request-filtered.html','request-errors.html']);
await mkdir(output,{recursive:true});await readFile(path.join(output,'request-new.html'));
const checks=[];
const check=(name,value)=>{checks.push({name,passed:Boolean(value)});if(!value)throw new Error(name);};
const server=http.createServer(async(req,res)=>{
  try {
    const url=new URL(req.url,'http://localhost');const page=url.pathname.slice(1);
    const file=pages.has(page)?path.join(output,page):path.resolve(assets,'.'+decodeURIComponent(url.pathname));
    if(!pages.has(page)&&!file.startsWith(assets+path.sep)){res.writeHead(403).end();return;}
    const body=await readFile(file);const types={'.html':'text/html; charset=utf-8','.js':'application/javascript','.css':'text/css','.png':'image/png','.svg':'image/svg+xml'};
    res.writeHead(200,{'Content-Type':types[path.extname(file)]||'application/octet-stream'});res.end(body);
  }catch{res.writeHead(404).end();}
});
await new Promise(resolve=>server.listen(17886,'127.0.0.1',resolve));let browser;
try {
  browser=await launch({port:9348,height:1100});
  const evaluate=code=>browser.evaluate(`return (${code});`);
  const go=page=>browser.goto('http://127.0.0.1:17886/'+page);
  const waitFor=async(code)=>{for(let i=0;i<30;i++){if(await evaluate(code))return true;await browser.sleep(100);}return false;};
  await browser.send('Page.addScriptToEvaluateOnNewDocument',{source:'window.uiErrors=[];window.addEventListener("error",e=>window.uiErrors.push(e.message));'});
  await go('request-new.html');
  check('New artwork loads; decorative sidebar removed',await waitFor("document.querySelector('.request-heading-art').naturalWidth>0 && !document.querySelector('.request-guidance')"));
  check('Progress starts at zero of five real required fields',await evaluate("document.querySelector('.request-progress progress').value===0 && document.querySelector('.request-progress progress').max===5 && !document.querySelector('[data-request-progress]').hidden"));
  await browser.click('[data-request-form] button[type=submit]');
  check('Native validation leaves invalid form editable',await evaluate("!document.querySelector('[data-request-form] button[type=submit]').disabled && document.activeElement.id==='locationId'"));
  await browser.typeInto('#title','Leaking pantry tap');await browser.typeInto('#description','The tap drips constantly near the pantry entrance.');
  await evaluate("(()=>{for(const id of ['locationId','category','urgencyLevel']){const field=document.getElementById(id);field.selectedIndex=1;field.dispatchEvent(new Event('change',{bubbles:true}));}})()");
  check('Five completed fields update both required sections',await evaluate("document.querySelector('progress').value===5 && document.querySelector('[data-progress-step=problem]').dataset.complete==='true' && document.querySelector('[data-progress-step=priority]').dataset.complete==='true'"));
  check('Live character counters update',await evaluate("document.querySelector('[data-count-for=title]').textContent==='18 / 120'"));
  await evaluate("(()=>{const e=document.querySelector('#title');e.value='   ';e.dispatchEvent(new Event('input',{bubbles:true}));})()");
  check('Whitespace is not counted as a completed required field',await evaluate("document.querySelector('progress').value===4"));
  await evaluate("(()=>{const e=document.querySelector('#title');e.value='Leaking pantry tap';e.dispatchEvent(new Event('input',{bubbles:true}));})()");
  await browser.click('[data-progress-step=priority]');
  check('Section links reach real fieldsets below the sticky header',await evaluate("location.hash==='#request-priority' && document.querySelector('#request-priority').getBoundingClientRect().top>=document.querySelector('.appbar').getBoundingClientRect().height-1"));
  await browser.click('.request-inline-help summary');
  check('Urgency help expands alongside its actual field',await evaluate("document.querySelector('.request-inline-help').open"));
  await evaluate("(async()=>{const blob=await fetch('/images/request-camera.png').then(r=>r.blob());const transfer=new DataTransfer();transfer.items.add(new File([blob],'fixture-photo.png',{type:'image/png'}));document.querySelector('[data-photo-dropzone]').dispatchEvent(new DragEvent('drop',{bubbles:true,cancelable:true,dataTransfer:transfer}));})()");
  check('Dropping a valid image creates a preview and optional-step completion',await waitFor("document.querySelectorAll('.request-photo').length===1 && document.querySelector('.request-photo img').naturalWidth>0 && document.querySelector('[data-progress-step=photos]').dataset.complete==='true'"));
  await browser.click('.request-photo button');
  check('Removing a photo updates real file selection and optional progress',await evaluate("document.querySelector('#files').files.length===0 && !document.querySelector('.request-photo') && document.querySelector('[data-progress-step=photos]').dataset.complete==='false'"));
  await evaluate("(()=>{const t=new DataTransfer();t.items.add(new File(['x'],'invalid.svg',{type:'image/svg+xml'}));const f=document.querySelector('#files');f.files=t.files;f.dispatchEvent(new Event('change',{bubbles:true}));})()");
  check('Invalid MIME is rejected with a linked file error',await evaluate("!document.querySelector('#files').validity.valid && document.querySelector('#files-error').textContent.includes('PNG or JPEG')"));
  await evaluate("(()=>{const t=new DataTransfer();const f=document.querySelector('#files');for(let i=0;i<Number(f.dataset.maxFiles)+1;i++)t.items.add(new File(['x'],'photo'+i+'.png',{type:'image/png'}));f.files=t.files;f.dispatchEvent(new Event('change',{bubbles:true}));})()");
  check('Photo count limit remains enforced',await evaluate("!document.querySelector('#files').validity.valid && document.querySelector('#files-error').textContent.includes('Choose up to')"));
  await evaluate("(()=>{const f=document.querySelector('#files'),t=new DataTransfer();t.items.add(new File([new Uint8Array(Number(f.dataset.maxSize)+1)],'large.png',{type:'image/png'}));f.files=t.files;f.dispatchEvent(new Event('change',{bubbles:true}));})()");
  check('Per-photo size limit remains enforced',await evaluate("document.querySelector('#files-error').textContent.includes('per-image')"));
  await evaluate("(()=>{const f=document.querySelector('#files');f.files=new DataTransfer().files;f.dispatchEvent(new Event('change',{bubbles:true}));document.querySelector('[data-request-form]').addEventListener('submit',e=>e.preventDefault(),{once:true});})()");
  await browser.click('[data-request-form] button[type=submit]');
  check('Valid submit shows busy feedback without disabling form fields',await evaluate("document.querySelector('[data-request-form]').getAttribute('aria-busy')==='true' && document.querySelector('[data-request-form] button[type=submit]').disabled && !document.querySelector('#title').disabled"));
  await evaluate("window.dispatchEvent(new PageTransitionEvent('pageshow',{persisted:true}))");
  check('Back navigation resets submit state and keeps content',await evaluate("!document.querySelector('[data-request-form] button[type=submit]').disabled && document.querySelector('#title').value==='Leaking pantry tap'"));
  for(const page of pages){
    await go(page);
    for(const width of [1440,1000,768,390,320]){
      await browser.setViewport(width,1100);await browser.evaluate('window.scrollTo(0,0);');await browser.sleep(120);
      check(`${page} ${width}px no document overflow`,await evaluate('document.documentElement.scrollWidth<=innerWidth+1'));
      check(`${page} ${width}px unique IDs and one h1`,await evaluate("(()=>{const a=[...document.querySelectorAll('[id]')].map(e=>e.id);return new Set(a).size===a.length && document.querySelectorAll('h1').length===1})()"));
      if(page==='request-new.html')check(`${width}px heading and form cards share edges`,await evaluate("(()=>{const a=document.querySelector('.page-context').getBoundingClientRect();return [...document.querySelectorAll('.form-section')].every(e=>{const b=e.getBoundingClientRect();return Math.abs(a.left-b.left)<1 && Math.abs(a.right-b.right)<1})})()"));
      if([1440,390].includes(width))await browser.screenshot(path.join(output,page.slice(0,-5)+'-'+width+'.png'));
    }
    check(page+' has no runtime errors',await evaluate('window.uiErrors.length===0'));
  }
  await go('request-errors.html');
  check('Server validation preserves entered content and focuses summary',await evaluate("document.querySelector('#title').value==='Leaking pantry tap' && document.activeElement===document.querySelector('[data-error-summary]')"));
  await go('request-filtered.html');
  check('Filter retains real CLOSED value and clear link',await evaluate("document.querySelector('#request-status').value==='CLOSED' && document.querySelector('.request-clear-filter').getAttribute('href')==='/requests/mine'"));
  await browser.send('Emulation.setScriptExecutionDisabled',{value:true});await go('request-new.html');
  check('Without JS, native multipart form and fields remain usable',await evaluate("document.querySelector('[data-request-form]').enctype==='multipart/form-data' && document.querySelectorAll('form [required]').length===5 && document.querySelector('[data-request-progress]').hidden && !document.querySelector('#files').disabled"));
  console.log(`${checks.length} request-experience browser checks passed (isolated captures; no real submission).`);
}finally{
  await writeFile(path.join(output,'browser-results.json'),JSON.stringify({scope:'Real server-rendered test captures; synthetic local files; isolated browser',checks},null,2));
  if(browser)await browser.close();await new Promise(resolve=>server.close(resolve));
}
