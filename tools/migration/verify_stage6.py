"""Real PostgreSQL/API/browser-route checks. Mutates only the allowlisted migration DBs.

Synthetic accounts and raw results stay in the private ignored runtime. No original DB access.
"""
import concurrent.futures, http.cookiejar, json, secrets, subprocess, time, uuid
import urllib.request, urllib.parse, urllib.error
from pathlib import Path
from html.parser import HTMLParser
import stage3

ROOT=Path(__file__).resolve().parents[2]; R=ROOT/'.migration-runtime'
CORE='http://127.0.0.1:19091'; API='http://127.0.0.1:19092/internal/community/v1'
checks={}; tokens={}

def sql(query,community=False):
    return stage3.sql(resources,query,'smartfix_migration_community' if community else 'smartfix_migration_core')

def client(): return urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))

def req(c,url,fields=None,body=None,headers=None):
    headers=dict(headers or {}); data=None
    if fields is not None:data=urllib.parse.urlencode(fields).encode()
    if body is not None:data=json.dumps(body).encode();headers['Content-Type']='application/json'
    try:
        with c.open(urllib.request.Request(url,data=data,headers=headers),timeout=12) as response:return response.status,response.geturl(),response.read().decode()
    except urllib.error.HTTPError as error:return error.code,error.geturl(),error.read().decode()

class Inputs(HTMLParser):
    def __init__(self): super().__init__(); self.fields={}
    def handle_starttag(self,tag,attrs):
        attrs=dict(attrs)
        if tag=='input' and 'name' in attrs:self.fields[attrs['name']]=attrs.get('value','')

def page(c,path):
    status,url,html=req(c,CORE+path);assert status==200,(path,status)
    parser=Inputs();parser.feed(html);return parser.fields,html

def login(account,password):
    c=client();fields,_=page(c,'/login')
    status,url,_=req(c,CORE+'/login',fields=dict(username=account['username'],password=password,accountType=account.get('role','REQUESTER'),_csrf=fields['_csrf']))
    assert status==200 and '/login' not in url,(status,url);return c

def token(account):
    actor=account['id'];now=int(time.time())
    if actor in tokens and now-tokens[actor][0]<20:return tokens[actor][1]
    claims={'iss':'smartfix-core','aud':['smartfix-community'],'sub':str(actor),'iat':now,'nbf':now,'exp':now+30,'role':account.get('role','REQUESTER'),'securityVersion':0,'passwordChangeRequired':False}
    header=json.dumps({'alg':'RS256','kid':'stage4-core-1'})
    result=subprocess.run(['java',str(ROOT/'tools/migration/MintTestToken.java'),str(R/'identity/core-private.pk8'),header],input=json.dumps(claims).encode(),capture_output=True,check=True).stdout.decode()
    tokens[actor]=(now,result);return result

def call(account,op,p=None,key=None,expected=200):
    headers={'Authorization':'Bearer '+token(account),'Idempotency-Key':key or str(uuid.uuid4())}
    status,_,body=req(client(),API+'/operations/'+op,body=p or {},headers=headers)
    assert status==expected,(op,status,expected)
    return json.loads(body) if status==200 else None

def wait_delivery():
    end=time.time()+90
    while time.time()<end:
        if sql("SELECT count(*) FROM community_outbox WHERE status<>'SENT';",True)=='0':return
        time.sleep(1)
    raise AssertionError('Community outbox not drained; inspect private state')

def app(action,service):subprocess.run(['python',str(ROOT/'tools/migration/stage3.py'),action,service],check=True)

if __name__=='__main__':
    stage3.safety();resources=stage3.load();stage3.check_container(resources)
    existing=json.loads((R/'stage5-accounts-private.json').read_text())
    author=existing['author'];responder=existing['responder'];password=existing['password']
    private=R/'stage6-accounts-private.json'
    if private.exists():saved=json.loads(private.read_text())
    else:
        saved={'password':password,'author':author,'responder':responder}
        for name in ('admin1','admin2'):
            username='s6_'+name+'_'+secrets.token_hex(4);c=client();fields,_=page(c,'/register')
            status,url,_=req(c,CORE+'/register',fields=dict(username=username,displayName='Stage6 '+name,password=password,confirmPassword=password,_csrf=fields['_csrf']))
            assert status==200 and '/login' in url
            actor=int(sql("SELECT id FROM users WHERE username='"+username+"';"))
            sql("UPDATE users SET role='ADMINISTRATOR' WHERE id="+str(actor)+";")
            saved[name]={'username':username,'id':actor,'role':'ADMINISTRATOR'}
        private.write_text(json.dumps(saved,indent=2))
    admin1=saved['admin1'];admin2=saved['admin2']
    try:
        sql("SELECT setval(pg_get_serial_sequence('community_questions','id'), GREATEST((SELECT COALESCE(max(id),1) FROM community_questions), (SELECT last_value FROM community_questions_id_seq)), true);",True)
        c=login(author,password);fields,_=page(c,'/community/questions/new')
        title='Stage6 literal %_'+secrets.token_hex(4);body='Community migration full regression discussion'
        submitted=dict(title=title,body=body,category='OTHER',_csrf=fields['_csrf'],communityCommandKey=fields['communityCommandKey'])
        status,url,html=req(c,CORE+'/community/questions',fields=submitted);assert status==200 and title in html,(status,url)
        q=int(url.rsplit('/',1)[1]); (R/'stage6-last-question.json').write_text(json.dumps({'questionId':q,'url':url}))
        assert sql('SELECT count(*) FROM community_questions WHERE id='+str(q)+';')=='0'
        assert call(author,'browse',{'query':title,'page':0,'size':1})['total']==1
        assert req(c,CORE+'/community/questions',fields=submitted)[0]==200
        assert call(author,'browse',{'query':title})['total']==1
        checks['real_original_question_form_api_write_no_core_copy_and_idempotency']=True
        assert call(author,'myQuestions',{'page':0,'size':1})['content'][0]['id']==q
        call(responder,'editQuestion',{'id':q,'command':{'title':title,'body':body,'category':'OTHER'}},expected=404)
        call(author,'editQuestion',{'id':q,'command':{'title':title+' edited','body':body+' edited','category':'OTHER'}})
        assert call(author,'detail',{'id':q})['edited']
        checks['literal_search_pagination_my_questions_edit_ownership']=True
        rc=login(responder,password);f,_=page(rc,'/community/questions/'+str(q))
        reply='Stage6 browser reply '+secrets.token_hex(4)
        status,_,html=req(rc,CORE+f'/community/questions/{q}/answers',fields=dict(body=reply,_csrf=f['_csrf'],communityCommandKey=f['communityCommandKey']))
        assert status==200 and reply in html
        detail=call(author,'detail',{'id':q});a=next(x['id'] for x in detail['answers'] if x['body']==reply)
        b=call(admin1,'answer',{'id':q,'command':{'body':'Second candidate answer for acceptance competition'}})
        call(responder,'editAnswer',{'id':a,'command':{'body':reply+' edited'}})
        assert call(responder,'ownAnswer',{'id':a})['body'].endswith('edited')
        assert call(responder,'thread',{'id':a})==q
        assert call(responder,'myAnswers',{'size':1})['content'][0]['id']==a
        call(author,'editAnswer',{'id':a,'command':{'body':'Unauthorized answer modification'}},expected=404)
        checks['browser_reply_my_answers_edit_and_thread_ownership']=True
        def accept(candidate):
            status,_,_=req(client(),API+'/operations/accept',body={'id':q,'answerId':candidate},headers={'Authorization':'Bearer '+token(author),'Idempotency-Key':str(uuid.uuid4())});return status
        token(author)
        with concurrent.futures.ThreadPoolExecutor(2) as pool:statuses=list(pool.map(accept,[a,b]))
        assert sorted(statuses)==[200,409],statuses
        call(author,'removeAcceptance',{'id':q});call(author,'accept',{'id':q,'answerId':a});call(author,'removeAcceptance',{'id':q});call(author,'accept',{'id':q,'answerId':a})
        call(responder,'withdrawAnswer',{'id':a});assert call(author,'detail',{'id':q})['acceptedAnswerId'] is None
        call(responder,'editAnswer',{'id':a,'command':{'body':'Cannot edit a withdrawn answer'}},expected=409)
        checks['postgres_accept_competition_reaccept_and_withdraw_clears_acceptance']=True
        report=call(responder,'reportQuestion',{'id':q,'command':{'reason':'OTHER','detail':'Moderation regression'}})
        call(responder,'reportQuestion',{'id':q,'command':{'reason':'OTHER'}},expected=409)
        call(responder,'reports',{'view':'ALL'},expected=403)
        call(responder,'hideQuestion',{'id':q},expected=403)
        def resolve(admin):return call(admin,'resolve',{'id':report,'command':{'decision':'ACTIONED','hideContent':True,'note':'Concurrent moderation test'}})
        token(admin1);token(admin2)
        with concurrent.futures.ThreadPoolExecutor(2) as pool:decisions=list(pool.map(resolve,[admin1,admin2]))
        assert sorted(decisions)==[False,True]
        call(responder,'detail',{'id':q},expected=404);assert call(author,'detail',{'id':q})['status']=='HIDDEN'
        call(admin1,'restoreQuestion',{'id':q});call(admin1,'hideQuestion',{'id':q});call(admin1,'restoreQuestion',{'id':q})
        call(admin1,'hideAnswer',{'id':b});call(admin1,'restoreAnswer',{'id':b});call(admin1,'hideAnswer',{'id':b});call(admin1,'restoreAnswer',{'id':b})
        assert any(x['id']==report for x in call(admin1,'reports',{'view':'HANDLED'})['content'])
        checks['reports_moderator_permissions_competition_history_hide_restore']=True
        # Other filters and report targets use the same state machine and visibility rules.
        call(author,'accept',{'id':q,'answerId':b})
        assert any(x['id']==q for x in call(responder,'browse',{'filter':'SOLVED','query':title})['content'])
        assert call(responder,'browse',{'filter':'UNANSWERED','query':title})['total']==0
        call(author,'removeAcceptance',{'id':q})
        ar=call(responder,'reportAnswer',{'id':b,'command':{'reason':'SPAM','detail':'Answer report coverage'}})
        call(admin2,'resolve',{'id':ar,'command':{'decision':'DISMISSED','hideContent':False}})
        assert any(x['id']==ar for x in call(admin1,'reports',{'view':'ALL'})['content'])
        checks['solved_unanswered_filters_and_answer_report_dismissal']=True
        # Database failure proves that the moderation, command, event and audit are atomic.
        sql("CREATE FUNCTION stage6_reject_audit() RETURNS trigger LANGUAGE plpgsql AS $$BEGIN RAISE EXCEPTION 'stage6 audit failure'; END$$; CREATE TRIGGER stage6_audit_failure BEFORE INSERT ON community_audit FOR EACH ROW EXECUTE FUNCTION stage6_reject_audit();",True)
        try:call(admin1,'hideQuestion',{'id':q},expected=500)
        finally:sql('DROP TRIGGER stage6_audit_failure ON community_audit; DROP FUNCTION stage6_reject_audit();',True)
        assert call(author,'detail',{'id':q})['status']=='VISIBLE'
        checks['moderation_audit_failure_rolls_back_business_and_events']=True
        wait_delivery()
        assert int(sql("SELECT count(*) FROM notifications WHERE event_type='COMMUNITY_ANSWER_ACCEPTED' AND reference_id="+str(q)+';'))>=2
        assert int(sql("SELECT count(*) FROM audit_entries WHERE source='COMMUNITY';"))==int(sql('SELECT count(*) FROM community_audit;',True))
        ac=login(admin1,password)
        for path in ('/admin/community/reports?view=ALL','/admin/community/reports?view=HANDLED','/admin/community/audit?size=1','/community/mine?tab=ANSWERS'):
            assert req(ac,CORE+path)[0]==200,path
        page(c,'/notifications');page(rc,'/notifications')
        assert sql('SELECT count(*) FROM core_community_inbox;')==sql('SELECT count(*) FROM community_outbox;',True)
        checks['notification_repeated_actions_audit_projection_original_pages']=True
        # All global audit sources share one stable local projection, never two concatenated pages.
        before=int(sql("SELECT count(*) FROM audit_entries WHERE source='CORE';"))
        for kind in ('USER','DELIVERY','SLA_POLICY'):
            sql("INSERT INTO audit_entries(actor_user_id,action,target_type,target_id,outcome,occurred_at) VALUES ("+str(admin1['id'])+",'S6_FIXTURE','"+kind+"',"+str(q)+",'TEST','2000-01-01T00:00:00Z');")
        _,audit_html=page(ac,'/admin/community/audit?size=50')
        assert 'CORE #' in audit_html and 'COMMUNITY #' in audit_html
        assert int(sql("SELECT count(*) FROM audit_entries WHERE source='CORE';"))==before+3
        first=page(ac,'/admin/community/audit?size=1&page=0')[1];second=page(ac,'/admin/community/audit?size=1&page=1')[1]
        assert first!=second
        checks['global_audit_keeps_user_delivery_sla_sources_and_pagination']=True
        # Original CSRF and ownership refusal remain at Core even with remote services.
        f,_=page(c,'/community/questions/'+str(q))
        assert req(c,CORE+f'/community/questions/{q}/withdraw',fields={'communityCommandKey':f['communityCommandKey']})[0]==403
        assert req(rc,CORE+f'/community/questions/{q}/edit')[0]==404
        call(author,'withdrawQuestion',{'id':q})
        assert call(author,'detail',{'id':q})['status']=='WITHDRAWN'
        call(responder,'detail',{'id':q},expected=404)
        assert not call(admin1,'restoreQuestion',{'id':q})
        checks['csrf_question_withdrawal_visibility_and_no_moderator_republish']=True
        # No remote call is required by the repair navigation; outage affects only community routes.
        app('stop','community')
        try:
            start=time.monotonic();assert req(c,CORE+'/requests/mine')[0]==200;assert time.monotonic()-start<4
            start=time.monotonic();assert req(c,CORE+'/community')[0]==503;assert time.monotonic()-start<4
            assert req(c,CORE+'/campus-map')[0]==200
        finally:app('start','community')
        checks['community_outage_bounded_error_repair_and_map_remain_available']=True
        for port in (19090,19091,19092):assert req(client(),f'http://127.0.0.1:{port}/actuator/health')[0]==200
        checks['all_services_and_protected_recovery_available']=True
    finally:
        (R/'stage6-results.json').write_text(json.dumps(checks,indent=2))
    print(json.dumps(checks))
