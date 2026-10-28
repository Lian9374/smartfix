"""Real UI/API/database failure-recovery acceptance, only isolated stage3 resources."""
import json, subprocess, time, urllib.request, urllib.parse, urllib.error, http.cookiejar, secrets, uuid
from pathlib import Path
from html.parser import HTMLParser
ROOT=Path(__file__).resolve().parents[2];R=ROOT/'.migration-runtime';assert Path.cwd().resolve()==ROOT
resources=json.loads((R/'resources.json').read_text());assert resources['container'].startswith('sf-migration-stage3-')
CORE='http://127.0.0.1:19091';COMM='http://127.0.0.1:19092/internal/community/v1';Q=900000000001;PATH='/community/questions/'+str(Q)
checks={}
def sql(q,community=False):
    db='smartfix_migration_community' if community else 'smartfix_migration_core'
    p=subprocess.run(['docker','exec','-i',resources['container'],'psql','-v','ON_ERROR_STOP=1','-U','migration_admin','-d',db,'-At'],input=q.encode(),capture_output=True)
    if p.returncode:(R/'stage5-sql-private.log').write_bytes(p.stderr);raise RuntimeError('Isolated SQL check failed')
    return p.stdout.decode().strip()
class Inputs(HTMLParser):
    def __init__(self):super().__init__();self.fields={}
    def handle_starttag(self,t,a):
        a=dict(a)
        if t=='input' and 'name'in a:self.fields[a['name']]=a.get('value','')
def client():return urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
def req(c,url,fields=None,token=None,jsonbody=None):
    headers={};body=None
    if fields is not None:body=urllib.parse.urlencode(fields).encode()
    if jsonbody is not None:body=json.dumps(jsonbody).encode();headers['Content-Type']='application/json'
    if token:headers['Authorization']='Bearer '+token
    try:
        with c.open(urllib.request.Request(url,data=body,headers=headers),timeout=12) as r:return r.status,r.geturl(),r.read().decode()
    except urllib.error.HTTPError as e:return e.code,e.geturl(),e.read().decode()
def inputs(c,path):
    status,url,html=req(c,CORE+path);assert status==200,(status,path);p=Inputs();p.feed(html);return p.fields,html
def login(name,password):
    c=client();f,_=inputs(c,'/login');status,url,_=req(c,CORE+'/login',dict(username=name,password=password,accountType='REQUESTER',_csrf=f['_csrf']));assert status==200 and '/login'not in url;return c
def token(actor=None,service=False,**overrides):
    now=int(time.time());claims={'iss':'smartfix-community' if service else 'smartfix-core','aud':['smartfix-core-events' if service else 'smartfix-community'],'sub':'community-publisher' if service else str(actor),'iat':now,'nbf':now,'exp':now+30}
    if not service:claims.update(role='REQUESTER',securityVersion=0,passwordChangeRequired=False)
    claims.update(overrides)
    header=json.dumps({'alg':'RS256','kid':'stage5-community-1' if service else 'stage4-core-1'})
    p=subprocess.run(['java',str(ROOT/'tools/migration/MintTestToken.java'),str(R/('identity/community-private.pk8' if service else 'identity/core-private.pk8')),header],input=json.dumps(claims).encode(),capture_output=True,check=True);return p.stdout.decode()
def wait_sent(event,timeout=75):
    end=time.time()+timeout
    while time.time()<end:
        state=sql("SELECT status FROM community_outbox WHERE event_id='"+event+"';",True)
        if state=='SENT':return
        if state=='FAILED':raise AssertionError('Outbox FAILED; inspect private data')
        time.sleep(1)
    raise AssertionError('Outbox delivery timeout')
def post_api(actor,key,body):
    # Header has no identity fields controlled by the browser.
    request=urllib.request.Request(COMM+'/questions/'+str(Q)+'/answers',data=json.dumps({'body':body,'authorId':-1}).encode(),headers={'Authorization':'Bearer '+token(actor),'Idempotency-Key':key,'Content-Type':'application/json'})
    try:
        with urllib.request.urlopen(request,timeout=10) as r:return r.status,json.loads(r.read())
    except urllib.error.HTTPError as e:return e.code,{}
def run_app(action,service):subprocess.run(['python',str(ROOT/'tools/migration/stage3.py'),action,service],check=True)

try:
    credentials=R/'stage5-accounts-private.json'
    if not credentials.exists():
        saved={'password':'Stage5!9'+secrets.token_hex(8)}
        for role in ('author','responder'):
            name='s5_'+role+'_'+secrets.token_hex(3);c=client();f,_=inputs(c,'/register');status,url,_=req(c,CORE+'/register',dict(username=name,displayName='Stage5 '+role,password=saved['password'],confirmPassword=saved['password'],_csrf=f['_csrf']));assert status==200 and '/login'in url
            saved[role]={'username':name,'id':int(sql("SELECT id FROM users WHERE username='"+name+"';"))}
        credentials.write_text(json.dumps(saved,indent=2))
    saved=json.loads(credentials.read_text());author=saved['author']['id'];responder=saved['responder']['id'];password=saved['password']
    sql("INSERT INTO community_questions(id,author_id,title,body,category) VALUES ("+str(Q)+","+str(author)+",'Independent community test discussion','Post a reply here to verify discussion notifications.','OTHER') ON CONFLICT DO NOTHING;",True)
    assert int(sql('SELECT author_id FROM community_questions WHERE id='+str(Q)+';',True))==author
    assert sql('SELECT count(*) FROM community_questions WHERE id='+str(Q)+';')=='0';checks['selected_thread_exists_only_in_community_database']=True
    c=login(saved['responder']['username'],password);fields,html=inputs(c,PATH);assert 'Independent community test discussion'in html
    key=fields['commandKey'];body='Stage5 real page reply '+secrets.token_hex(5)
    status,url,html=req(c,CORE+PATH+'/answers',dict(body=body,commandKey=key,_csrf=fields['_csrf']));assert status==200 and body in html,(status,url)
    count=int(sql('SELECT count(*) FROM community_answers WHERE question_id='+str(Q)+';',True));event=sql("SELECT event_id FROM community_answer_commands WHERE actor_id="+str(responder)+" AND command_key='"+key+"';",True);wait_sent(event)
    assert sql("SELECT count(*) FROM notifications WHERE dedup_key='COMMUNITY_EVENT:"+event+"' AND recipient_id="+str(author)+';')=='1'
    recipient=login(saved['author']['username'],password);status,url,notices=req(recipient,CORE+'/notifications');assert status==200 and 'New answer to your question'in notices and PATH in notices
    assert req(recipient,CORE+PATH)[0]==200;checks['real_core_page_reply_community_commit_and_original_notification_link']=True
    status,url,html=req(c,CORE+PATH+'/answers',dict(body=body,commandKey=key,_csrf=fields['_csrf']));assert status==200
    assert int(sql('SELECT count(*) FROM community_answers WHERE question_id='+str(Q)+';',True))==count
    assert post_api(responder,key,'Different body with the same key')[0]==409
    checks['duplicate_browser_submission_and_changed_payload_key_conflict']=True
    lookup=req(client(),COMM+'/commands/lookup',token=token(responder),jsonbody={'operation':'ANSWER','key':key});assert lookup[0]==200 and json.loads(lookup[2])['status']=='COMPLETED'
    assert json.loads(req(client(),COMM+'/commands/lookup',token=token(author),jsonbody={'operation':'ANSWER','key':key})[2])['status']=='UNKNOWN';checks['uncertain_result_lookup_is_actor_scoped']=True
    payload=json.loads(sql("SELECT payload FROM community_outbox WHERE event_id='"+event+"';",True))
    assert req(client(),CORE+'/internal/community-events/v1/events',jsonbody=payload)[0]==401
    assert req(client(),CORE+'/internal/community-events/v1/events',token=token(responder),jsonbody=payload)[0]==401
    now=int(time.time())
    for bad in [token(service=True,iat=now-120,nbf=now-120,exp=now-90),token(service=True,aud=['wrong']),token(service=True,iss='wrong'),token(service=True,sub='wrong'),token(service=True,exp=now+120)]:
        assert req(client(),CORE+'/internal/community-events/v1/events',token=bad,jsonbody={})[0]==401
    checks['event_service_expiration_audience_issuer_subject_and_lifetime_rejected']=True
    repeated=req(client(),CORE+'/internal/community-events/v1/events',token=token(service=True),jsonbody=payload);assert repeated[0]==200 and json.loads(repeated[2])['duplicate'] is True
    changed=json.loads(json.dumps(payload));changed['payload']['answerId']+=1
    assert req(client(),CORE+'/internal/community-events/v1/events',token=token(service=True),jsonbody=changed)[0]==409
    checks['receiver_requires_service_identity_and_deduplicates_immutable_events']=True
    sql("UPDATE community_outbox SET status='PENDING',lease_until=NULL,lease_token=NULL,next_attempt_at=CURRENT_TIMESTAMP WHERE event_id='"+event+"';",True);wait_sent(event)
    assert sql("SELECT count(*) FROM notifications WHERE dedup_key='COMMUNITY_EVENT:"+event+"';")=='1';checks['commit_ack_loss_replay_does_not_duplicate_notification']=True
    sql("CREATE FUNCTION stage5_reject_outbox() RETURNS trigger LANGUAGE plpgsql AS $$BEGIN RAISE EXCEPTION 'Stage5 injected outbox insert failure'; END$$; CREATE TRIGGER stage5_rollback_test BEFORE INSERT ON community_outbox FOR EACH ROW EXECUTE FUNCTION stage5_reject_outbox();",True)
    rollback_key=str(uuid.uuid4())
    try:assert post_api(responder,rollback_key,'This write must roll back with its event')[0]>=500
    finally:sql('DROP TRIGGER stage5_rollback_test ON community_outbox; DROP FUNCTION stage5_reject_outbox();',True)
    assert sql("SELECT count(*) FROM community_answer_commands WHERE command_key='"+rollback_key+"';",True)=='0'
    assert int(sql('SELECT count(*) FROM community_answers WHERE question_id='+str(Q)+';',True))==count;checks['outbox_insert_failure_rolls_back_answer_command_and_sequence']=True
    sql("CREATE FUNCTION stage5_reject_notification() RETURNS trigger LANGUAGE plpgsql AS $$BEGIN IF NEW.dedup_key LIKE 'COMMUNITY_EVENT:%' THEN RAISE EXCEPTION 'Stage5 injected notification insert failure'; END IF; RETURN NEW; END$$; CREATE TRIGGER stage5_receiver_rollback BEFORE INSERT ON notifications FOR EACH ROW EXECUTE FUNCTION stage5_reject_notification();")
    try:
        status,result=post_api(responder,str(uuid.uuid4()),'Reply verifies receiver transaction rollback and later retry');assert status==200;receiver_failure=result['eventId'];time.sleep(4)
        assert sql("SELECT count(*) FROM core_community_inbox WHERE event_id='"+receiver_failure+"';")=='0'
        assert sql("SELECT count(*) FROM delivery_tasks WHERE dedup_key='COMMUNITY_EVENT:"+receiver_failure+"';")=='0'
    finally:sql('DROP TRIGGER stage5_receiver_rollback ON notifications; DROP FUNCTION stage5_reject_notification();')
    wait_sent(receiver_failure);checks['receiver_inbox_task_notification_roll_back_together_then_retry']=True
    status,self_result=post_api(author,str(uuid.uuid4()),'Author replying to own discussion does not self notify');assert status==200;wait_sent(self_result['eventId'])
    assert sql("SELECT count(*) FROM notifications WHERE dedup_key='COMMUNITY_EVENT:"+self_result['eventId']+"';")=='0';checks['self_reply_does_not_notify_self']=True
    run_app('stop','core')
    try:
        status,result=post_api(responder,str(uuid.uuid4()),'Reply persisted while notification receiver is stopped');assert status==200;pending=result['eventId'];time.sleep(4)
        assert sql("SELECT status FROM community_outbox WHERE event_id='"+pending+"';",True)=='PENDING'
        assert int(sql("SELECT attempts FROM community_outbox WHERE event_id='"+pending+"';",True))>0
    finally:run_app('start','core')
    wait_sent(pending);assert sql("SELECT count(*) FROM notifications WHERE dedup_key='COMMUNITY_EVENT:"+pending+"';")=='1';checks['receiver_stop_restart_persistent_retry_delivers_after_recovery']=True
    status,result=post_api(responder,str(uuid.uuid4()),'Reply for sender restart and lease recovery verification');assert status==200;restart_event=result['eventId'];wait_sent(restart_event)
    run_app('stop','community')
    try:sql("UPDATE community_outbox SET status='PENDING',lease_token='"+str(uuid.uuid4())+"',lease_until=CURRENT_TIMESTAMP+INTERVAL '2 seconds',next_attempt_at=CURRENT_TIMESTAMP WHERE event_id='"+restart_event+"';",True)
    finally:run_app('start','community')
    wait_sent(restart_event);assert sql("SELECT count(*) FROM notifications WHERE dedup_key='COMMUNITY_EVENT:"+restart_event+"';")=='1';checks['sender_restart_recovers_expired_lease_without_duplicate_effect']=True
    recipient=login(saved['author']['username'],password);assert PATH in req(recipient,CORE+'/notifications')[2];assert req(recipient,CORE+PATH)[0]==200
    assert sql('SELECT count(*) FROM community_questions WHERE id='+str(Q)+';')=='0'
    for port in (19090,19091,19092):assert req(client(),'http://127.0.0.1:'+str(port)+'/actuator/health')[0]==200
    checks['final_pages_health_and_no_core_thread_database_copy']=True
finally:
    (R/'stage5-results.json').write_text(json.dumps(checks,indent=2))
print(json.dumps(checks))
