// Real Edge acceptance on a newly created, dedicated local PostgreSQL database.
import {spawn, execFileSync} from 'node:child_process';
import {createWriteStream, mkdirSync, writeFileSync} from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';
import {launch} from './browser-cdp.mjs';
import {releaseIdentity} from './release-evidence.mjs';
const root=path.resolve(path.dirname(fileURLToPath(import.meta.url)),'..');
const db=process.env.ROLE_ACCEPTANCE_DB_URL;
if(!/^jdbc:postgresql:\/\/(127\.0\.0\.1|localhost):\d+\/smartfix_role_acceptance_test$/.test(db||'')) throw Error('A dedicated local smartfix_role_acceptance_test database is required.');
const out=path.resolve(root,process.env.ROLE_ACCEPTANCE_OUTPUT||'docs/sprint3/evidence/role-workspaces'); mkdirSync(out,{recursive:true});
const origin='http://127.0.0.1:18081', password='TestPassword9', changed='ChangedPassword8';
const release=releaseIdentity(root,process.env.ROLE_ACCEPTANCE_JAR||path.join(root,'target/smartfix-0.0.1-SNAPSHOT.jar'));
const run=Date.now(); const engineer="role.qa.engineer."+run, requester="role.qa.requester."+run; const checks=[]; let browser,app,engineerId,completed=false;
const wait=ms=>new Promise(r=>setTimeout(r,ms));
function check(label,value){checks.push({label,passed:!!value});if(!value)throw Error(label);console.log('PASS '+label);}
async function evaluate(expression){return browser.evaluate("return "+expression+";");}
async function go(route){await browser.goto(origin+route);await wait(150);check('HTTP '+route,await browser.lastDocumentStatus()===200);}
async function tabTo(selector){for(let i=0;i<200;i++){if(await evaluate(`document.activeElement?.matches(${JSON.stringify(selector)})`))return;await browser.press('Tab');}throw Error('Keyboard reachability: '+selector);}
async function type(selector,value){await tabTo(selector);await browser.send('Input.insertText',{text:value});}
async function activate(selector,confirm=false){await tabTo(selector);if(confirm){const pending=browser.press('Enter');await wait(300);await browser.send('Page.handleJavaScriptDialog',{accept:true});await pending;}else await browser.press('Enter');await browser.settle();await wait(150);}
async function login(name,role,secret=password){await browser.clearCookies();await go('/login?accountType='+role);await type('#username',name);await type('#password',secret);await activate('main button[type=submit]');}
async function layout(label){for(const width of [1440,1000,768,390,320]){await browser.setViewport(width,960);const data=await evaluate(`(()=>{const ids=[...document.querySelectorAll('[id]')].map(e=>e.id);return {overflow:document.documentElement.scrollWidth>innerWidth+1, duplicateIds:ids.filter((id,i)=>ids.indexOf(id)!==i),h1:document.querySelectorAll('h1').length};})()`);if(data.overflow||data.duplicateIds.length||data.h1!==1){await browser.screenshot(path.join(out,label+'-failed-'+width+'.png'));console.log(JSON.stringify(data));console.log(await evaluate(`JSON.stringify([...document.querySelectorAll('*')].filter(e=>e.getBoundingClientRect().right>innerWidth+1).map(e=>({tag:e.tagName,cls:e.className,right:e.getBoundingClientRect().right})).slice(0,50))`));}check(label+' layout '+width,!data.overflow&&data.duplicateIds.length===0&&data.h1===1);if(width===1440||width===390)await browser.screenshot(path.join(out,label+'-'+width+'.png'));}await browser.setViewport(1440,960);}
try{
  try{await fetch(origin+'/actuator/health');throw Error('Acceptance port is already in use.');}catch(e){if(e.message.includes('already in use'))throw e;}
  const log=createWriteStream(path.join(root,'target/role-browser-app.log'));
  app=spawn('java',['-jar',process.env.ROLE_ACCEPTANCE_JAR||path.join(root,'target/smartfix-0.0.1-SNAPSHOT.jar'),'--server.address=127.0.0.1','--server.port=18081','--spring.jpa.hibernate.ddl-auto=validate','--spring.thymeleaf.prefix=file:'+path.join(root,'src/main/resources/templates/').replaceAll('\\','/'),'--spring.web.resources.static-locations=file:'+path.join(root,'src/main/resources/static/').replaceAll('\\','/')],{cwd:root,windowsHide:true,env:{...process.env,DB_URL:db,DB_USERNAME:process.env.TEST_DB_USERNAME||'smartfix',DB_PASSWORD:process.env.TEST_DB_PASSWORD||'',SMARTFIX_BOOTSTRAP_ADMIN_ENABLED:'true',SMARTFIX_BOOTSTRAP_ADMIN_USERNAME:'role.qa.admin',SMARTFIX_BOOTSTRAP_ADMIN_PASSWORD:password},stdio:['ignore','pipe','pipe']});app.stdout.pipe(log);app.stderr.pipe(log);
  let ready=false;for(let i=0;i<160;i++){if(app.exitCode!==null)throw Error('Application startup failed; inspect target/role-browser-app.log.');try{if((await fetch(origin+'/actuator/health')).ok){ready=true;break;}}catch{}await wait(500);}check('Flyway and Hibernate validate startup',ready);
  execFileSync('C:\\Program Files\\PostgreSQL\\16\\bin\\psql.exe',['-h','127.0.0.1','-p',new URL(db.slice(5)).port,'-U',process.env.TEST_DB_USERNAME||'smartfix','-d','smartfix_role_acceptance_test','-v','ON_ERROR_STOP=1','-c',"INSERT INTO locations(location_code,display_name) VALUES ('ROLE-QA','Role QA Lab') ON CONFLICT DO NOTHING"],{windowsHide:true,env:{...process.env,PGPASSWORD:process.env.TEST_DB_PASSWORD||''}});
  browser=await launch({port:9341,width:1440,height:960});
  await login('role.qa.admin','ADMINISTRATOR');check('Administrator lands on real overview',await evaluate('location.pathname==="/admin"'));await layout('admin-overview');
  for(const [route,label] of [['/admin/requests','admin-queue'],['/admin/technicians','engineer-directory'],['/admin/facilities','facilities'],['/admin/users','accounts'],['/admin/reports','reports'],['/admin/announcements','announcements'],['/notifications','notifications'],['/campus-map','campus-map']]){await go(route);await layout(label);}
  if (process.env.ROLE_ACCEPTANCE_USERD === 'true') {
    const measurements = await evaluate(`(async()=>{const rows=[];for(const route of ['/admin/reports','/admin/reports/export.csv?start=2000-01-01&end=2000-01-02','/campus-map','/campus-map/status','/admin/facilities','/admin/announcements']){const samples=[];for(let i=0;i<5;i++){const start=performance.now();const response=await fetch(route,{cache:'no-store'});const body=await response.text();samples.push({milliseconds:performance.now()-start,status:response.status,bytes:new TextEncoder().encode(body).length});}rows.push({route,samples});}return rows;})()`);
    check('UserD real endpoint measurement requests succeed',measurements.every(row=>row.samples.every(sample=>sample.status===200)));
    writeFileSync(path.join(out,'endpoint-measurements.json'),JSON.stringify({checkedAt:new Date().toISOString(),release,environment:'Dedicated PostgreSQL; local browser fetch; five sequential samples per route; empty business records with migrated location catalogue',measurements},null,2));
    await go('/admin/reports?start=2026-10-31&end=2026-10-01');
    check('Invalid report interval stays in accessible form', await evaluate(`document.querySelector('#report-date-error')?.textContent.includes('End date') && document.querySelector('#report-end')?.getAttribute('aria-invalid')==='true' && !document.querySelector('a[href*="export.csv"]')`));
    await layout('reports-invalid');
    await go('/admin/reports?start=2000-01-01&end=2000-01-02');
    check('Report explicit empty state and no invented SLA',await evaluate(`document.body.innerText.includes('No requests were created') && document.body.innerText.includes('SLA compliance is not available')`));
    const csv=await evaluate(`fetch('/admin/reports/export.csv?start=2000-01-01&end=2000-01-02').then(async r=>({status:r.status,text:await r.text(),type:r.headers.get('content-type')}))`);
    check('Real report CSV retains summary contract',csv.status===200 && csv.type.includes('text/csv') && csv.text.includes('Average Resolution Hours') && csv.text.includes('Category,Requests'));
    check('Reversed CSV interval is a client error',await evaluate(`fetch('/admin/reports/export.csv?start=2026-10-31&end=2026-10-01').then(r=>r.status)` )===400);
    await browser.send('Network.enable');
    await browser.send('Network.setBlockedURLs',{urls:['*js/vendor/leaflet/leaflet.js*']});
    await go('/campus-map');
    check('Map library failure keeps textual directory',await evaluate(`!document.querySelector('#atlas-map-notice').hidden && document.querySelector('#atlas-map-notice').textContent.includes('could not load') && !!document.querySelector('.campus-building-directory')`));
    await activate('[data-campus="UTOWN"]');
    check('Map failure message persists after campus switch',await evaluate(`document.querySelector('#atlas-map-notice').textContent.includes('could not load') && !document.querySelector('#atlas-map-notice').hidden`));
    await layout('campus-map-offline');
    await browser.send('Network.setBlockedURLs',{urls:[]});
    await go('/admin/announcements');
    check('Announcement form identifies timezone and counters',await evaluate(`document.body.innerText.includes('Singapore time') && !!document.querySelector('#announcement-content-count')`));
    const announcementTitle='UserD QA announcement '+run;
    await type('#announcement-title',announcementTitle);
    await type('#announcement-content','Synthetic campus maintenance notice for isolated acceptance.');
    await browser.evaluate(`document.querySelector('#announcement-valid-from').value='2000-01-01T00:00';`);
    await activate('form[action="/admin/announcements"] button[type=submit]');
    check('Real announcement draft creation has success feedback',await evaluate(`document.body.innerText.includes('Announcement draft created.')`));
    const announcementId=await evaluate(`([...document.querySelectorAll('tbody tr')].find(row=>row.innerText.includes(${JSON.stringify(announcementTitle)})))?.querySelector('form[action$="/publish"]')?.getAttribute('action')?.split('/').at(-2)`);
    check('Created announcement has a real draft action',/^\d+$/.test(announcementId||''));
    await activate('form[action="/admin/announcements/'+announcementId+'/publish"] button',true);
    check('Publish action has feedback',await evaluate(`document.body.innerText.includes('Announcement published.')`));
    await go('/announcements');
    check('Published announcement appears in real public list',await evaluate(`document.body.innerText.includes(${JSON.stringify(announcementTitle)})`));
    await go('/admin/announcements');
    await activate('form[action="/admin/announcements/'+announcementId+'/withdraw"] button',true);
    check('Withdraw action has feedback',await evaluate(`document.body.innerText.includes('Announcement withdrawn.')`));
    await go('/announcements');
    check('Withdrawn announcement is absent from public list',await evaluate(`!document.body.innerText.includes(${JSON.stringify(announcementTitle)})`));
  }
  if (process.env.ROLE_ACCEPTANCE_LAYOUT_ONLY !== 'true') {
  await go('/admin/users/new?role=TECHNICIAN');await type('#username',engineer);await type('#displayName','QA Engineer');await type('#password',password);await activate('form[action="/admin/users"] button[type=submit]');check('Real managed account creation',await evaluate('location.pathname==="/admin/users"'));engineerId=await evaluate(`([...document.querySelectorAll('tbody tr')].find(row=>row.innerText.includes(${JSON.stringify(engineer)})))?.querySelector('td.data-table__id')?.innerText`);
  await login(engineer,'TECHNICIAN');check('Temporary password forces setup',await evaluate('location.pathname==="/account/password"'));await layout('password-setup');
  await type('#current-password',password);await type('#new-password',changed);await type('#confirm-password',changed);await activate('main button[type=submit]');check('Password replacement signs out',await evaluate('location.pathname==="/login"&&location.search.includes("passwordChanged")'));
  await login(engineer,'TECHNICIAN',changed);check('Engineer lands on own workspace',await evaluate('location.pathname==="/technician"'));await layout('engineer-empty');
  await go('/technician/profile');await layout('engineer-profile');await tabTo('input[name=skills][value=ELECTRICAL]');await browser.press('Space');await tabTo('input[name=serviceAreaIds]');await browser.press('Space');await tabTo('input[name=availabilityStatus][value=AVAILABLE]');await browser.press('Space');await activate('main button[type=submit]');check('Engineer saves real coverage and availability',await evaluate('document.body.innerText.includes("Your technician profile was saved.")'));
  if (process.env.ROLE_ACCEPTANCE_USERB === 'true') {
    for (const route of ['/admin','/admin/users','/admin/requests']) {
      check('Engineer directly rejected from '+route,await evaluate(`(async()=> (await fetch(${JSON.stringify(route)})).status)()` )===403);
    }
    await go('/technician/profile');
    check('Profile descriptions reference existing elements',await evaluate(`Array.from(document.querySelectorAll('[aria-describedby]')).every(element=>element.getAttribute('aria-describedby').split(/\\s+/).every(id=>document.getElementById(id)))`));
    const invalid=await evaluate(`(async()=>{const token=document.querySelector('input[name="_csrf"]').value;const response=await fetch('/technician/profile',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:new URLSearchParams({_csrf:token})});return {status:response.status,html:await response.text()};})()`);
    check('Profile empty selection rejected by server',invalid.status===400);
    const frame=await browser.send('Page.getFrameTree');
    await browser.send('Page.setDocumentContent',{frameId:frame.frameTree.frame.id,html:invalid.html});await wait(350);
    check('Profile first invalid group receives keyboard focus',await evaluate('document.activeElement.matches("fieldset[aria-invalid=true]")'));
    check('Invalid profile descriptions reference existing errors',await evaluate(`Array.from(document.querySelectorAll('[aria-describedby]')).every(element=>element.getAttribute('aria-describedby').split(/\\s+/).every(id=>document.getElementById(id)))`));
    await layout('engineer-profile-invalid');
  }
  await browser.clearCookies();await go('/register');await type('#username',requester);await type('#displayName','QA Requester');await type('#password',password);await type('#confirmPassword',password);await activate('main button[type=submit]');await login(requester,'REQUESTER');
  await go('/requests/new');for(const selector of ['#locationId','#category','#urgencyLevel']){await tabTo(selector);await browser.press('ArrowDown');await browser.press('Enter');}await type('#title','Role QA electrical repair');await type('#description','Synthetic acceptance repair; no real campus incident.');await activate('main button[type=submit]');const ticket=await evaluate('location.pathname.split("/").at(-1)');check('Requester creates persisted repair',ticket.startsWith('SF-'));
  await login('role.qa.admin','ADMINISTRATOR');await go('/requests/'+ticket+'/review');await layout('review');await activate('main button[type=submit]',true);await go('/admin/requests/'+ticket+'/dispatch');await layout('dispatch');await activate(`button[name=technicianId][value="${engineerId}"]`);check('Assignment result visible',await evaluate('document.body.innerText.includes("QA Engineer")'));
  await login(engineer,'TECHNICIAN',changed);await layout('engineer-assigned');const order=await evaluate('document.querySelector("main a[href^=\\"/workorders/\\"]")?.getAttribute("href")');check('Assigned work order available',!!order);await go(order);await layout('work-order-created');await activate('form[action$="/accept"] button');
  if (process.env.ROLE_ACCEPTANCE_USERC === 'true') {
    await activate('.skip-link');check('Work order keyboard skip focuses main',await evaluate('document.activeElement.id==="main-content"'));
    // Clear the fragment navigation before testing focus in a replacement document.
    await go(order);
    const invalidRepair=await evaluate(`(async()=>{const form=document.querySelector('form[action$="/records"]');const response=await fetch(form.action,{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded'},body:new URLSearchParams({_csrf:form.querySelector('input[name="_csrf"]').value})});return {status:response.status,html:await response.text()};})()`);
    check('Work order server validation renders errors',invalidRepair.status===200&&invalidRepair.html.includes('aria-invalid="true"'));
    const frame=await browser.send('Page.getFrameTree');await browser.send('Page.setDocumentContent',{frameId:frame.frameTree.frame.id,html:invalidRepair.html});await wait(350);
    check('Work order validation focuses diagnosis',await evaluate('document.activeElement.id==="diagnosis"'));
    check('Diagnosis validation explains the field',await evaluate('document.querySelector("#diagnosis-error").textContent.includes("Describe the diagnosis.")'));
    check('Time validation identifies the missing value',await evaluate('document.querySelector("#minutesSpent-error").textContent.includes("Enter time spent in minutes.")'));
    check('Work order field describes its actual error',await evaluate('document.querySelector("#diagnosis").getAttribute("aria-describedby").split(/\\s+/).includes("diagnosis-error")'));
    check('Work order description links exist',await evaluate(`Array.from(document.querySelectorAll('[aria-describedby]')).every(element=>element.getAttribute('aria-describedby').split(/\\s+/).every(id=>document.getElementById(id)))`));
    await layout('work-order-invalid');
  }
  await type('#diagnosis','Electrical fixture failed.');await type('#actionTaken','Replaced the test fixture and verified operation.');await type('#minutesSpent','15');await activate('form[action$="/records"] button');await layout('work-order-recorded');await type('#resolutionNote','Synthetic repair completed and checked.');await activate('form[action$="/complete"] button',true);check('Solution awaits requester confirmation',await evaluate('document.body.innerText.includes("Solution submitted")'));
  await login(requester,'REQUESTER');await go('/requests/'+ticket);await activate('form[action$="/confirm"] button');await login('role.qa.admin','ADMINISTRATOR');await go('/requests/'+ticket);await activate('form[action$="/close"] button');check('Full role lifecycle reaches CLOSED',await evaluate('document.body.innerText.includes("Closed")||document.body.innerText.includes("CLOSED")'));
  }
  completed=true;
}finally{writeFileSync(path.join(out,process.env.ROLE_ACCEPTANCE_LAYOUT_ONLY === 'true' ? 'results-layout.json' : 'results.json'),JSON.stringify({checkedAt:new Date().toISOString(),release,passed:completed&&checks.every(check=>check.passed),checks},null,2));if(browser)await browser.close();if(app&&app.exitCode===null){const ended=new Promise(r=>app.once('exit',r));app.kill();await ended;}}
