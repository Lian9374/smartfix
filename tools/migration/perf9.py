"""Open-arrival business driver. Default SMOKE only; formal requires stage10 authorization.

No external packages. Scheduling is independent of completion; bounded in-flight work, no queue.
Raw response bodies/passwords/tokens are never emitted. Only the exact sandbox URLs are allowed.
"""
import argparse,concurrent.futures,hashlib,http.cookiejar,json,math,os,re,secrets,subprocess,threading,time,uuid
import urllib.request,urllib.parse,urllib.error
from html.parser import HTMLParser
from pathlib import Path
import stage9 as env
CORE='http://127.0.0.1:19111';API='http://127.0.0.1:19114'
ALLOWED={19111,19112,19113,19114,19115}
HTTP_LOCAL=threading.local()

def target(url):
    u=urllib.parse.urlsplit(url)
    assert u.scheme=='http' and u.hostname=='127.0.0.1' and u.port in ALLOWED and not u.username and not u.password and not u.fragment,'PERF_TARGET_REJECTED'
    assert u.netloc=='127.0.0.1:'+str(u.port),'PERF_TARGET_REJECTED'
    return url

class SafeRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self,req,fp,code,msg,headers,newurl):
        target(newurl)
        HTTP_LOCAL.redirects=getattr(HTTP_LOCAL,'redirects',0)+1
        return super().redirect_request(req,fp,code,msg,headers,newurl)

class Failure(Exception):
    def __init__(self,kind):self.kind=kind

class Inputs(HTMLParser):
    def __init__(self):super().__init__();self.fields={}
    def handle_starttag(self,tag,attrs):
        a=dict(attrs)
        if tag=='input' and 'name' in a:self.fields[a['name']]=a.get('value','')

def fields(html):p=Inputs();p.feed(html);return p.fields

def opener():
    # Only loopback HTTP is allowlisted. build_opener() also constructs an unused
    # HTTPS context and scans the Windows certificate store on EVERY new client.
    # Keep normal HTTP errors/redirects/cookies; do not construct unsupported transports.
    client=urllib.request.OpenerDirector()
    for handler in (urllib.request.HTTPHandler(),urllib.request.HTTPDefaultErrorHandler(),SafeRedirect(),urllib.request.HTTPErrorProcessor(),urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar())):client.add_handler(handler)
    return client

def endpoint(url):
    path=urllib.parse.urlsplit(url).path
    return re.sub(r'/\d+(?=/|$)','/{id}',re.sub(r'/SF-\d{4}-\d+(?=/|$)','/{ticket}',path))

class Driver:
    def __init__(self):
        env.load();self.saved=json.loads((env.R/'test-accounts-private.json').read_text());self.local=threading.local();self.sessions={};self.session_locks={};self.cache={};self.sign_lock=threading.Lock();self.instances={};self.count_lock=threading.Lock()
        self.expected_pages=json.loads(env.sql('community',"SELECT json_agg(id) FROM (SELECT id FROM community_questions WHERE status='VISIBLE' ORDER BY created_at DESC,id DESC LIMIT 100) q;"))
        self.signer=subprocess.Popen(['java',str(env.ROOT/'tools/migration/PerfTokenSigner.java'),str(env.R/'identity/core-private.pk8')],stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.DEVNULL,text=True,creationflags=subprocess.CREATE_NO_WINDOW)
        self.pid_file=env.R/f'generator-{os.getpid()}-{self.signer.pid}.json';self.pid_file.write_text(json.dumps({'driverPid':os.getpid(),'signerPid':self.signer.pid}))
    def close(self):self.signer.stdin.close();self.signer.wait(timeout=10);self.pid_file.unlink(missing_ok=True)
    def signer_cpu(self):
        import ctypes
        from ctypes import wintypes
        times=[wintypes.FILETIME() for _ in range(4)];f=ctypes.WinDLL('kernel32',use_last_error=True).GetProcessTimes
        f.argtypes=[wintypes.HANDLE,*([ctypes.POINTER(wintypes.FILETIME)]*4)];f.restype=wintypes.BOOL
        assert f(int(self.signer._handle),*[ctypes.byref(t) for t in times])
        return sum(t.dwHighDateTime*4294967296+t.dwLowDateTime for t in times[2:])/10000000
    def token(self,actor,role='REQUESTER'):
        with self.sign_lock:
            now=int(time.time());key=(actor,role)
            if key in self.cache and now-self.cache[key][0]<20:return self.cache[key][1]
            payload={'iss':'smartfix-core','aud':['smartfix-community'],'sub':str(actor),'iat':now,'nbf':now,'exp':now+30,'role':role,'securityVersion':0,'passwordChangeRequired':False}
            self.signer.stdin.write(json.dumps(payload)+'\n');self.signer.stdin.flush();t=self.signer.stdout.readline().strip();assert t.count('.')==2
            self.cache[key]=(now,t);return t
    def request(self,c,url,form=None,payload=None,headers=None,expected=200):
        target(url);h=dict(headers or {});data=None
        if form is not None:data=urllib.parse.urlencode(form).encode()
        if payload is not None:data=json.dumps(payload).encode();h['Content-Type']='application/json'
        start=time.perf_counter();code=0;body=b'';final=url;rh={};HTTP_LOCAL.redirects=0
        try:
            with c.open(urllib.request.Request(url,data=data,headers=h),timeout=5) as r:code=r.status;body=r.read();final=r.geturl();rh=dict(r.headers)
        except urllib.error.HTTPError as e:code=e.code;body=e.read()
        except (TimeoutError,OSError):
            if hasattr(self.local,'requests'):self.local.requests.append({'endpoint':endpoint(url),'seconds':time.perf_counter()-start,'status':0})
            raise Failure('timeout_or_transport')
        if hasattr(self.local,'requests'):self.local.requests.append({'endpoint':endpoint(url),'seconds':time.perf_counter()-start,'status':code,'redirectGets':HTTP_LOCAL.redirects,'responseBodyBytes':len(body),'requestBodyBytes':len(data or b'')})
        if 'X-Community-Instance' in rh:
            with self.count_lock:self.instances[rh['X-Community-Instance']]=self.instances.get(rh['X-Community-Instance'],0)+1
        if code!=expected:raise Failure('http_'+str(code))
        if '/login' in final and '/login' not in url:raise Failure('unexpected_login')
        return body.decode(),final
    def login(self,actor,username,role='REQUESTER'):
        c=opener();html,_=self.request(c,CORE+'/login');f=fields(html)
        _,url=self.request(c,CORE+'/login',form={'username':username,'password':self.saved['password'],'accountType':role,'_csrf':f['_csrf']})
        if '/login' in url:raise Failure('login_failed')
        self.sessions[actor]=c;self.session_locks[actor]=threading.Lock()
    def prepare(self,path,users):
        # Authentication/setup is outside the measured window and reported separately.
        start=time.perf_counter()
        base=900 if path=='repair' else 0
        if path in ('page','repair'):
            with concurrent.futures.ThreadPoolExecutor(8) as pool:list(pool.map(lambda i:self.login(1000000+i,'perf9_'+str(i)),range(base,base+users)))
        for i in range(base,base+users):self.token(1000000+i)
        return time.perf_counter()-start
    def api(self,actor,op,p):
        if not hasattr(self.local,'api_client'):self.local.api_client=opener()
        body,_=self.request(self.local.api_client,API+'/internal/community/v1/operations/'+op,payload=p,headers={'Authorization':'Bearer '+self.token(actor),'Idempotency-Key':str(uuid.uuid4())})
        return json.loads(body)
    def operation(self,path,index,users,hot=False,force_write=False):
        self.local.requests=[];actor=1000000+(900 if path=='repair' else 0)+(index*17+index//users)%users;q=1000000 if hot else 1000000+(index*7919)%10000
        # Every consecutive 100 iterations: 40 browse,30 detail,20 search,10 write.
        bucket=(index*37)%100;op='browse' if bucket<40 else 'detail' if bucket<70 else 'search' if bucket<90 else 'answer'
        if force_write:op='answer'
        marker='Perf9 runtime '+str(uuid.uuid4())
        if path=='api':
            if op in ('browse','search'):
                p={'page':index%5,'size':20};p.update({'query':'Perf9 question'} if op=='search' else {})
                r=self.api(actor,'browse',p)
                expected=list(range(1009999-(index%5)*20,1009999-(index%5)*20-20,-1)) if op=='search' else self.expected_pages[(index%5)*20:(index%5+1)*20]
                if [x['id'] for x in r.get('content',[])]!=expected or r.get('total',0)<10000:raise Failure('content')
            elif op=='detail':
                r=self.api(actor,'detail',{'id':q})
                if r.get('id')!=q or not r.get('title','').startswith('Perf9 question') or len(r.get('answers',[]))<3:raise Failure('content')
            else:
                a=self.api(actor,'answer',{'id':q,'command':{'body':marker}})
                r=self.api(actor,'ownAnswer',{'id':a})
                if r.get('body')!=marker or r.get('answerId')!=a or r.get('questionId')!=q:raise Failure('content')
        else:
            c=self.sessions[actor]
            # No concurrent use of one session/cookie jar. Busy actors cause an explicit drop.
            if not self.session_locks[actor].acquire(blocking=False):raise Failure('actor_busy')
            try:
                if path=='repair':
                    op='repair_create' if force_write else 'repair_read' if bucket<80 else 'repair_create'
                    if op=='repair_read':
                        html,_=self.request(c,CORE+'/requests/mine')
                        if 'login-form' in html or '/requests/new' not in html:raise Failure('content')
                    else:
                        html,_=self.request(c,CORE+'/requests/new');f=fields(html)
                        html,url=self.request(c,CORE+'/requests',form={'_csrf':f['_csrf'],'locationId':self.location,'title':marker,'description':'Perf9 synthetic maintenance request for workload isolation.','category':'ELECTRICAL','urgencyLevel':'LOW'})
                        if '/requests/SF-' not in url or marker not in html:raise Failure('content')
                elif op in ('browse','search'):
                    suffix='?size=20&page='+str(index%5)+('&q=Perf9+question' if op=='search' else '')
                    html,_=self.request(c,CORE+'/community'+suffix)
                    expected=list(range(1009999-(index%5)*20,1009999-(index%5)*20-20,-1)) if op=='search' else self.expected_pages[(index%5)*20:(index%5+1)*20]
                    if not all('href="/community/questions/'+str(qid)+'"' in html for qid in expected):raise Failure('content')
                else:
                    html,detail_url=self.request(c,CORE+'/community/questions/'+str(q))
                    expected_path='/community/questions/'+str(q)
                    if urllib.parse.urlsplit(detail_url).path!=expected_path or 'Perf9 question '+str(q-1000000) not in html:raise Failure('content')
                    if op=='answer':
                        f=fields(html)
                        html,url=self.request(c,CORE+f'/community/questions/{q}/answers',form={'body':marker,'_csrf':f['_csrf'],'communityCommandKey':f['communityCommandKey']})
                        # Validation errors may rerender a 200 form containing the submitted body.
                        # Success requires the committed thread GET, not the POST /answers URL.
                        if marker not in html or urllib.parse.urlsplit(url).path!=expected_path:raise Failure('content')
            finally:self.session_locks[actor].release()
        return op

def quantile(values,p):
    if not values:return None
    v=sorted(values);return v[max(0,math.ceil(len(v)*p)-1)]

def arrivals(driver,path,rate,duration,users,workers=64,hot=False,offset=0):
    permit=threading.BoundedSemaphore(workers);rows=[];lock=threading.Lock();planned=math.floor(rate*duration);dropped=0
    def work(i,due):
        start=time.perf_counter();error=None;op=None
        try:op=driver.operation(path,i+offset,users,hot)
        except Failure as e:error=e.kind
        except Exception as e:error='driver_'+type(e).__name__
        finally:
            with lock:rows.append({'iteration':i+offset,'scheduledOffsetSeconds':due-origin,'startedOffsetSeconds':start-origin,'completedOffsetSeconds':time.perf_counter()-origin,'operation':op,'error':error,'seconds':time.perf_counter()-start,'scheduleLagSeconds':start-due,'requests':getattr(driver.local,'requests',[])})
            permit.release()
    start=time.perf_counter();origin=start;started_unix=time.time();cpu_start=time.process_time();signer_start=driver.signer_cpu() if hasattr(driver,'signer_cpu') else 0
    with concurrent.futures.ThreadPoolExecutor(workers) as pool:
        for i in range(planned):
            due=start+i/rate;delay=due-time.perf_counter()
            if delay>0:time.sleep(delay)
            # No catch-up burst when the generator itself falls behind.
            if time.perf_counter()-due>max(.05,1/rate) or not permit.acquire(blocking=False):dropped+=1;continue
            pool.submit(work,i,due)
        remaining=start+duration-time.perf_counter()
        if remaining>0:time.sleep(remaining)
    elapsed=time.perf_counter()-start;success=[r for r in rows if not r['error']];errors={}
    for r in rows:
        if r['error']:errors[r['error']]=errors.get(r['error'],0)+1
    endpoint={}
    for r in rows:
        for request in r['requests']:
            key=request['endpoint'];s=endpoint.setdefault(key,[]);s.append(request['seconds'])
    request_count=sum(1+x.get('redirectGets',0) for r in rows for x in r['requests'])
    signer_cpu=(driver.signer_cpu()-signer_start) if hasattr(driver,'signer_cpu') else 0
    cpu=time.process_time()-cpu_start
    result={'path':path,'measurementStartedAtUnix':started_unix,'maxInflight':workers,'plannedIterations':planned,'startedIterations':len(rows),'successfulBusinessIterations':len(success),'dropped_iterations':dropped,'targetIterationsPerSecond':rate,'windowSeconds':duration,'elapsedIncludingDrainSeconds':elapsed,
       'successfulBusinessPerSecond':len(success)/elapsed,'completedIterationsPerSecond':len(rows)/elapsed,'actualMeasuredHttpRequests':request_count,'actualMeasuredHttpRps':request_count/elapsed,
       'generator':{'pid':os.getpid(),'pythonCpuSeconds':cpu,'signerCpuSeconds':signer_cpu,'cpuPercentOneCore':(cpu+signer_cpu)/elapsed*100,'logicalCpuCount':os.cpu_count(),'scheduleP99Seconds':quantile([r['scheduleLagSeconds'] for r in rows],.99)},
       'applicationPayloadBytes':{'sent':sum(x.get('requestBodyBytes',0) for r in rows for x in r['requests']),'received':sum(x.get('responseBodyBytes',0) for r in rows for x in r['requests']),'note':'Bodies of completed calls, not TCP wire bytes; host NIC counters collected separately'},
       'businessErrorRate':(len(rows)-len(success))/len(rows) if rows else 1,'errors':errors,'businessP95Seconds':quantile([r['seconds'] for r in rows],.95),'businessP99Seconds':quantile([r['seconds'] for r in rows],.99),
       'endpointLatency':{k:{'count':len(v),'p95':quantile(v,.95),'p99':quantile(v,.99)} for k,v in endpoint.items()},'raw':rows}
    return result

def run(args):
    target(CORE);target(API);env.load()
    assert args.rate>0 and args.duration>0
    if not args.formal_authorized:assert args.rate<=1 and args.duration<=20 and args.users<=8,'Only low smoke allowed in stage9'
    else:
        w=json.loads(env.stage3.STATE.read_text(encoding='utf-8'))
        assert w.get('stage9',{}).get('status')=='PASS' and w.get('stage10',{}).get('status')=='IN_PROGRESS','Stage10 explicit authorization and phase9 acceptance required'
        assert 1<=args.users<=1000 and args.rate<=160 and args.duration<=900
    driver=Driver();out=env.R/('run-'+datetime_tag());out.mkdir()
    try:
        driver.location=int(env.sql('core','SELECT min(id) FROM locations WHERE active;'))
        setup=driver.prepare(args.path,args.users)
        # A real form and signed API write are smoke-tested independently of the 90:10 mix.
        if not args.formal_authorized:
            driver.operation('api',0,args.users,force_write=True)
            if args.path=='page':driver.operation('page',1,args.users,force_write=True)
            if args.path=='repair':driver.operation('repair',1,args.users,force_write=True)
        result=arrivals(driver,args.path,args.rate,args.duration,args.users,hot=args.hot)
        raw=result.pop('raw');(out/'iterations-private.json').write_text(json.dumps(raw))
        plan_path=env.ROOT/'docs/migration/phase9/PLAN.json'
        result.update(smokeOnly=not args.formal_authorized,setupSeconds=setup,instances=driver.instances,rateUnit='business iterations/second',authenticationEnabled=True,runDirectory=out.name,planSha256=hashlib.sha256(plan_path.read_bytes()).hexdigest(),artifacts={k:v['sha256'] for k,v in env.state['services'].items()},snapshot=json.loads((env.R/'fixed-snapshot/manifest.json').read_text()))
        (out/'summary.json').write_text(json.dumps(result,indent=2));print(json.dumps(result))
        assert result['successfulBusinessIterations']>0 and not result['errors'] and not result['dropped_iterations'],'Smoke failed; do not advance'
    finally:driver.close()

def datetime_tag():
    import datetime
    return datetime.datetime.now().strftime('%Y%m%d-%H%M%S')+'-'+secrets.token_hex(2)

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--path',choices=['api','page','repair'],default='api');p.add_argument('--rate',type=float,default=.5);p.add_argument('--duration',type=float,default=10);p.add_argument('--users',type=int,default=4);p.add_argument('--hot',action='store_true');p.add_argument('--formal-authorized',action='store_true');run(p.parse_args())
