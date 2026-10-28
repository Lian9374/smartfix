"""Real stage4 HTTP checks; tokens, passwords and SQL outputs are never printed."""
import json, subprocess, time, urllib.request, urllib.parse, urllib.error, http.cookiejar, secrets
from pathlib import Path
from html.parser import HTMLParser
ROOT=Path(__file__).resolve().parents[2];R=ROOT/'.migration-runtime'
assert Path.cwd().resolve()==ROOT
data=json.loads((R/'resources.json').read_text());private=json.loads((R/'credentials-private.json').read_text());assert data['container'].startswith('sf-migration-stage3-')
def sql(q,db='smartfix_migration_core'):
    p=subprocess.run(['docker','exec','-i',data['container'],'psql','-v','ON_ERROR_STOP=1','-U','migration_admin','-d',db,'-At'],input=q.encode(),capture_output=True,check=True);return p.stdout.decode().strip()
class Inputs(HTMLParser):
    def __init__(self):super().__init__();self.fields={}
    def handle_starttag(self,t,a):
        a=dict(a)
        if t=='input' and 'name'in a:self.fields[a['name']]=a.get('value','')
def request(opener,url,fields=None,headers=None):
    try:
        with opener.open(urllib.request.Request(url,data=None if fields is None else urllib.parse.urlencode(fields).encode(),headers=headers or {}),timeout=10) as r:return r.status,r.geturl(),r.read()
    except urllib.error.HTTPError as e:return e.code,e.geturl(),e.read()
def session():return urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
def login(name,password):
    c=session();_,_,html=request(c,'http://127.0.0.1:19091/login');f=Inputs();f.feed(html.decode());status,url,_=request(c,'http://127.0.0.1:19091/login',dict(username=name,password=password,accountType='REQUESTER',_csrf=f.fields['_csrf']));assert status==200 and '/login'not in url;return c
def token(actor,role='REQUESTER',**overrides):
    now=int(time.time());claims=dict(iss='smartfix-core',aud=['smartfix-community'],sub=str(actor),iat=now,nbf=now,exp=now+30,role=role,securityVersion=0,passwordChangeRequired=False);claims.update(overrides)
    header=json.dumps({'alg':'RS256','kid':'stage4-core-1'})
    p=subprocess.run(['java',str(ROOT/'tools/migration/MintTestToken.java'),str(R/'identity/core-private.pk8'),header],input=json.dumps(claims).encode(),capture_output=True,check=True);return p.stdout.decode()
checks={};core='http://127.0.0.1:19091';community='http://127.0.0.1:19092/internal/community/v1';account=(R/'test-account-private.txt').read_text();name=account.split('Username: ')[1].splitlines()[0];pw=account.split('Password: ')[1].splitlines()[0];uid=int(sql("SELECT id FROM users WHERE username='"+name+"';"));c=login(name,pw)
status,url,body=request(c,core+'/community/connection');assert status==200 and json.loads(body)['communityIdentity']['userId']==uid;checks['core_session_to_signed_community_call_and_batched_names']=True
assert request(session(),community+'/identity')[0]==401
assert request(session(),community+'/identity',headers={'X-User-Id':str(uid),'X-Role':'ADMINISTRATOR'})[0]==401;checks['anonymous_and_self_reported_headers_rejected']=True
good=token(uid)
for label,bad in [('expired',token(uid,iat=int(time.time())-120,nbf=int(time.time())-120,exp=int(time.time())-90)),('wrong_audience',token(uid,aud=['other'])),('wrong_issuer',token(uid,iss='other')),('password_required',token(uid,passwordChangeRequired=True)),('forged_signature',good[:-4]+'AAAA')]:
    assert request(session(),community+'/identity',headers={'Authorization':'Bearer '+bad})[0]==401;checks[label+'_rejected']=True
assert request(session(),community+'/moderation/access-check',headers={'Authorization':'Bearer '+token(uid)})[0]==403
assert request(session(),community+'/moderation/access-check',headers={'Authorization':'Bearer '+token(uid,'ADMINISTRATOR')})[0]==200;checks['administrator_role_enforced']=True
qid=int(sql("INSERT INTO community_questions(author_id,title,body,category) VALUES ("+str(uid)+",'Stage4 ownership verification','Ownership verification body','OTHER') RETURNING id;",'smartfix_migration_community').splitlines()[0])
try:
    path=community+'/questions/'+str(qid)+'/edit-context'
    assert request(session(),path,headers={'Authorization':'Bearer '+token(uid)})[0]==200
    assert request(session(),path,headers={'Authorization':'Bearer '+token(uid+1000000)})[0]==404
    assert request(session(),path,headers={'Authorization':'Bearer '+token(uid+1000000,'ADMINISTRATOR')})[0]==404
    sql('UPDATE community_questions SET status=\'HIDDEN\' WHERE id='+str(qid)+';','smartfix_migration_community')
    status,_,body=request(session(),path,headers={'Authorization':'Bearer '+token(uid)});assert status==200 and json.loads(body)['editable'] is False
    assert request(session(),path,headers={'Authorization':'Bearer '+token(uid+1000000)})[0]==404
    checks['ownership_and_hidden_content_rules']=True
finally:sql('DELETE FROM community_questions WHERE id='+str(qid)+';','smartfix_migration_community')
assert request(session(),core+'/login',dict(username=name,password=pw))[0]==403;checks['core_csrf_preserved']=True
for field,value,label in [('password_change_required','true','temporary_password'),('security_version','security_version+1','stale_security_version'),('account_status',"'DISABLED'",'disabled_account')]:
    c=login(name,pw);sql('UPDATE users SET '+field+'='+value+' WHERE id='+str(uid)+';')
    try:
        status,url,body=request(c,core+'/community/connection');assert '/login'in url or '/account/password'in url or status in (401,403);checks[label+'_blocked_at_core']=True
    finally:
        reset={'password_change_required':'false','security_version':'security_version-1','account_status':"'ACTIVE'"}[field];sql('UPDATE users SET '+field+'='+reset+' WHERE id='+str(uid)+';')
for port in (19090,19091,19092):assert request(session(),'http://127.0.0.1:'+str(port)+'/actuator/health')[0]==200
(R/'stage4-http-results.json').write_text(json.dumps(checks,indent=2));print(json.dumps(checks))
