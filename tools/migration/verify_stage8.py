"""Live two-JVM correctness and outage checks. Only stage8 resources are mutated."""
import concurrent.futures,json,time,uuid,secrets,urllib.request,urllib.error,subprocess,ctypes
from pathlib import Path
import stage8 as env,verify_stage6 as http
R=env.R;checks={};proof={}
def sql(q,community=False):return env.sql('community' if community else 'core',q)
def api(account,port,op,data=None,key=None):
    request=urllib.request.Request(f'http://127.0.0.1:{port}/internal/community/v1/operations/'+op,data=json.dumps(data or {}).encode(),headers={'Authorization':'Bearer '+http.token(account),'Idempotency-Key':key or str(uuid.uuid4()),'Content-Type':'application/json'})
    try:
        with urllib.request.urlopen(request,timeout=12) as r:return r.status,json.load(r),dict(r.headers)
    except urllib.error.HTTPError as e:return e.code,None,dict(e.headers)
def ok(account,port,op,data=None,key=None,status=200):
    code,result,headers=api(account,port,op,data,key);assert code==status,(op,code,status);return result
def wait_events():
    end=time.time()+70
    while time.time()<end:
        if sql("SELECT count(*) FROM community_outbox WHERE status<>'SENT';",True)=='0':return
        time.sleep(1)
    raise AssertionError('Outbox failed to drain')
def operate(action,name,variant=False):
    env.load()
    if action=='stop':env.stop(name)
    else:env.start(name,variant)
def lb_count(n):
    end=time.time()+12
    while time.time()<end:
        try:
            with urllib.request.urlopen('http://127.0.0.1:19104/actuator/health') as r:count=len(json.load(r)['backends'])
        except urllib.error.HTTPError as e:count=len(json.load(e)['backends'])
        if count==n:return
        time.sleep(.5)
    raise AssertionError('Load balancer did not converge')
def form(c,path,values):
    f,_=http.page(c,path);return dict(values,_csrf=f['_csrf'],**({'communityCommandKey':f['communityCommandKey']} if 'communityCommandKey' in f else {}))
def post(c,source,target,values):
    code,url,html=http.req(c,http.CORE+target,fields=form(c,source,values));assert code==200,(target,code);return url,html
def setup():
    env.stage3.safety();env.load();http.R=R;http.CORE='http://127.0.0.1:19101';http.sql=sql
    base=json.loads((R/'source-accounts-private.json').read_text());password=base['password'];accounts=base['accounts']
    path=R/'test-accounts-private.json'
    if path.exists():return json.loads(path.read_text())
    for name,role in [('admin2','ADMINISTRATOR'),('tech','TECHNICIAN'),('quota','REQUESTER')]:
        c=http.client();f,_=http.page(c,'/register');username='s8_'+name+'_'+secrets.token_hex(4)
        code,url,_=http.req(c,http.CORE+'/register',fields=dict(username=username,displayName='Stage8 '+name,password=password,confirmPassword=password,_csrf=f['_csrf']));assert code==200 and '/login' in url
        actor=int(sql("SELECT id FROM users WHERE username='"+username+"';"));sql("UPDATE users SET role='"+role+"' WHERE id="+str(actor)+';');accounts[name]={'id':actor,'username':username,'role':role}
    saved={'password':password,'accounts':accounts,'url':http.CORE+'/login'};path.write_text(json.dumps(saved,indent=2));return saved
def run():
    saved=setup();accounts=saved['accounts'];a=accounts['author'];b=accounts['responder'];admin=accounts['admin'];admin2=accounts['admin2'];quota=accounts['quota'];password=saved['password']
    for x in (a,b):x.setdefault('role','REQUESTER')
    c=http.client();f,_=http.page(c,'/register');name='s8_responder_'+secrets.token_hex(5)
    code,url,_=http.req(c,http.CORE+'/register',fields=dict(username=name,displayName='Stage8 concurrency responder',password=password,confirmPassword=password,_csrf=f['_csrf']));assert code==200 and '/login' in url
    b={'id':int(sql("SELECT id FROM users WHERE username='"+name+"';")),'username':name,'role':'REQUESTER'}
    saved['accounts']['responder']=b;(R/'test-accounts-private.json').write_text(json.dumps(saved,indent=2))
    # Each repeat uses a new synthetic quota actor, never resets an existing user's posts.
    c=http.client();f,_=http.page(c,'/register');name='s8_quota_'+secrets.token_hex(5)
    code,url,_=http.req(c,http.CORE+'/register',fields=dict(username=name,displayName='Stage8 quota repeat',password=password,confirmPassword=password,_csrf=f['_csrf']));assert code==200 and '/login' in url
    quota={'id':int(sql("SELECT id FROM users WHERE username='"+name+"';")),'username':name,'role':'REQUESTER'}
    author=http.login(a,password);responder=http.login(b,password);administrator=http.login(admin,password)
    headers=[api(a,19104,'browse',{})[2] for _ in range(8)];owners={h['X-Community-Instance'] for h in headers};assert len(owners)==2
    env.load();assert {int(x.split(':')[1]) for x in owners}=={env.resources['services'][x]['pid'] for x in ('a','b')};proof['servingJvms']=sorted(owners);checks['lb_reaches_two_real_distinct_jvms']=True
    title='Stage8 concurrent '+secrets.token_hex(4)
    q=ok(a,19102,'ask',{'command':{'title':title,'body':'Concurrent acceptance and moderation test thread.','category':'OTHER'}})
    one=ok(b,19102,'answer',{'id':q,'command':{'body':'First answer from an independent process'}});two=ok(admin,19103,'answer',{'id':q,'command':{'body':'Second answer from the other process'}})
    http.token(a)
    with concurrent.futures.ThreadPoolExecutor(2) as pool:r=list(pool.map(lambda item:api(a,item[0],'accept',{'id':q,'answerId':item[1]})[0],[(19102,one),(19103,two)]))
    assert sorted(r)==[200,409];ok(a,19103,'removeAcceptance',{'id':q});checks['cross_jvm_acceptance_competition']=True
    key=str(uuid.uuid4());payload={'id':q,'command':{'body':'Exactly one effect for a shared idempotency command'}};http.token(b)
    with concurrent.futures.ThreadPoolExecutor(2) as pool:r=list(pool.map(lambda port:ok(b,port,'answer',payload,key),[19102,19103]))
    assert r[0]==r[1];assert sql("SELECT count(*) FROM community_commands WHERE command_key='"+key+"';",True)=='1';ok(b,19102,'answer',{'id':q,'command':{'body':'Conflicting content with the same key'}},key,status=409);checks['cross_jvm_command_idempotency_and_conflict']=True
    data={'command':{'title':'First creation '+secrets.token_hex(3),'body':'Duplicate first posting guard creation competition.','category':'OTHER'}};http.token(quota)
    with concurrent.futures.ThreadPoolExecutor(2) as pool:r=list(pool.map(lambda port:api(quota,port,'ask',data)[0],[19102,19103]))
    assert sorted(r)==[200,400];assert sql('SELECT count(*) FROM community_posting_guards WHERE user_id='+str(quota['id'])+';',True)=='1'
    # Fill to the configured limit with distinct content, then race the last permitted slot.
    for i in range(18):ok(quota,19102 if i%2 else 19103,'ask',{'command':{'title':'Quota '+str(i)+' '+secrets.token_hex(3),'body':'Distinct posting quota verification content '+str(i),'category':'OTHER'}})
    http.token(quota)
    with concurrent.futures.ThreadPoolExecutor(2) as pool:r=list(pool.map(lambda port:api(quota,port,'ask',{'command':{'title':'Final slot '+str(port)+secrets.token_hex(3),'body':'Only one final allowed question can commit.','category':'OTHER'}})[0],[19102,19103]))
    assert sorted(r)==[200,400];checks['cross_jvm_first_guard_duplicate_and_rolling_quota']=True
    report=ok(b,19102,'reportQuestion',{'id':q,'command':{'reason':'OTHER','detail':'Cross process report'}});http.token(admin);http.token(admin2)
    with concurrent.futures.ThreadPoolExecutor(2) as pool:r=list(pool.map(lambda item:ok(item[0],item[1],'resolve',{'id':report,'command':{'decision':'ACTIONED','hideContent':True}}),[(admin,19102),(admin2,19103)]))
    assert sorted(r)==[False,True];ok(admin,19103,'restoreQuestion',{'id':q});checks['cross_jvm_moderation_competition_and_audit']=True
    wait_events();event=sql('SELECT event_id FROM community_outbox WHERE question_id='+str(q)+' ORDER BY aggregate_sequence LIMIT 1;',True)
    before=int(sql("SELECT count(*) FROM notifications WHERE dedup_key='COMMUNITY_EVENT:"+event+"';"))
    sql("UPDATE community_outbox SET status='PENDING',lease_token=NULL,lease_until=NULL,next_attempt_at=CURRENT_TIMESTAMP WHERE event_id='"+event+"';",True);wait_events();assert int(sql("SELECT count(*) FROM notifications WHERE dedup_key='COMMUNITY_EVENT:"+event+"';"))==before
    checks['two_publishers_duplicate_event_has_one_notification']=True
    # Hold verified Windows process handles before the race. Termination then targets the
    # same process object, without a slow post-claim CIM query or PID-reuse ambiguity.
    from ctypes import wintypes
    kernel=ctypes.WinDLL('kernel32',use_last_error=True);kernel.OpenProcess.restype=wintypes.HANDLE
    kernel.OpenProcess.argtypes=[wintypes.DWORD,wintypes.BOOL,wintypes.DWORD]
    kernel.TerminateProcess.argtypes=[wintypes.HANDLE,wintypes.UINT];kernel.CloseHandle.argtypes=[wintypes.HANDLE]
    handles={};env.load()
    for name in ('a','b'):
        row=env.resources['services'][name];handle=kernel.OpenProcess(0x100001,False,row['pid']);assert handle
        line=json.loads(env.command(['powershell','-NoProfile','-Command',f"(Get-CimInstance Win32_Process -Filter 'ProcessId={row['pid']}').CommandLine|ConvertTo-Json -Compress"]).decode())
        assert row['artifact'].lower() in line.lower() and '--server.port='+str(env.PORTS[name]) in line
        handles[name]=(handle,row['pid'])
    # Block the actual Core transaction, observe its durable sender owner, then kill that JVM.
    lock=subprocess.Popen(['docker','exec','-i',env.resources['container'],'psql','-U','stage8_admin','-d',env.DBS['core'],'-At'],stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.PIPE)
    lock.stdin.write(f"BEGIN; SELECT pg_advisory_xact_lock({q}); SELECT pg_sleep(25); COMMIT;".encode());lock.stdin.close();time.sleep(2)
    ok(b,19102,'answer',{'id':q,'command':{'body':'Sender exits after a durable outbox claim transaction'}})
    fault_event=sql(f'SELECT event_id FROM community_outbox WHERE question_id={q} ORDER BY aggregate_sequence DESC LIMIT 1;',True)
    end=time.time()+12;owner=None
    while time.time()<end:
        owner=sql(f"SELECT COALESCE(lease_owner,'') FROM community_outbox WHERE question_id={q} AND status='PENDING' ORDER BY aggregate_sequence DESC LIMIT 1;",True)
        if owner:break
    assert owner;claimant=owner.split(':')[0];proof['killedClaimant']=owner
    handle,pid=handles[claimant];assert int(owner.split(':')[1])==pid and kernel.TerminateProcess(handle,1)
    for held,_ in handles.values():kernel.CloseHandle(held)
    env.load();del env.resources['services'][claimant];env.save()
    proof['durableLeaseAfterKill']=sql(f"SELECT count(*) FROM community_outbox WHERE question_id={q} AND status='PENDING' AND lease_owner='{owner}' AND lease_until>CURRENT_TIMESTAMP;",True)
    assert proof['durableLeaseAfterKill']=='1','Claim cleared before JVM exit; no abandoned-lease proof'
    lock.wait(timeout=35);wait_events()
    assert int(sql("SELECT attempts FROM community_outbox WHERE event_id='"+fault_event+"';",True))>=2
    assert sql("SELECT count(*) FROM notifications WHERE dedup_key='COMMUNITY_EVENT:"+fault_event+"';")=='1'
    proof['leaseRecoveryReceipt']={'eventId':fault_event,'attempts':int(sql("SELECT attempts FROM community_outbox WHERE event_id='"+fault_event+"';",True)),'notifications':1}
    operate('start',claimant);lb_count(2)
    checks['actual_claimant_jvm_exit_and_other_jvm_lease_recovery']=True
    operate('stop','a');lb_count(1);assert all(api(a,19104,'browse',{})[2]['X-Community-Instance'].startswith('b:') for _ in range(4));operate('start','a');lb_count(2);checks['lb_removes_and_restores_one_backend']=True
    operate('stop','a');operate('stop','b');lb_count(0)
    try:
        started=time.monotonic();assert http.req(author,http.CORE+'/community')[0]==503;proof['communityFailureSeconds']=round(time.monotonic()-started,3);assert proof['communityFailureSeconds']<4
        for _ in range(5):assert http.req(author,http.CORE+'/requests/mine')[0]==200
        # Real maintenance write while both community processes are unavailable.
        location=int(sql('SELECT min(id) FROM locations WHERE active;'))
        url,_=post(author,'/requests/new','/requests',dict(locationId=location,title='Community outage repair '+secrets.token_hex(3),description='Repair submission remains available during community outage.',category='ELECTRICAL',urgencyLevel='LOW'))
        assert '/requests/new' not in url;proof['outageTicket']=url.rsplit('/',1)[1]
    finally:operate('start','a');operate('start','b');lb_count(2)
    checks['all_community_down_core_handles_fixed_read_load_and_real_repair_write']=True
    env.load();core_before=dict(env.resources['services']['core']);operate('stop','a');env.variant();operate('start','a',True);lb_count(2)
    releases=[api(a,19104,'browse',{})[2]['X-Community-Release'] for _ in range(6)];assert set(releases)=={'stage8-r1','stage8-compatible-r2'};env.load();assert env.resources['services']['core']==core_before
    assert http.req(author,http.CORE+'/community')[0]==200;proof['releases']=sorted(set(releases));proof['unchangedCore']=core_before;checks['independent_compatible_community_artifact_core_not_redeployed']=True
    for c in (author,responder,administrator):
        for path in ('/community','/community/mine','/notifications'):assert http.req(c,http.CORE+path)[0]==200
    for path in ('/community/questions/2','/community/questions/3','/admin/community/reports?view=ALL','/admin/community/audit'):assert http.req(administrator,http.CORE+path)[0]==200
    proof['questionId']=q
    (R/'results-private.json').write_text(json.dumps({'checks':checks,'proof':proof},indent=2));print(json.dumps(checks))
if __name__=='__main__':
    try:run()
    finally:(R/'results-private.json').write_text(json.dumps({'checks':checks,'proof':proof},indent=2))
