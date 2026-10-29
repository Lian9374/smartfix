"""Small functional delivery check, NOT a load/performance test. Stage9 clones only."""
import http.cookiejar,json,urllib.request
import stage9 as env
import verify_stage6 as h

def main():
    env.load();assert env.state.get('coreArtifactVersion')=='stage10-cookie-v2'
    h.CORE='http://127.0.0.1:19111';saved=json.loads((env.R/'test-accounts-private.json').read_text());checks={}
    for key,role,path in [('author','REQUESTER','/requests/mine'),('tech','TECHNICIAN','/workorders/mine'),('admin','ADMINISTRATOR','/admin/requests')]:
        account=dict(saved['accounts'][key],role=role);c=h.login(account,saved['password'])
        for route in ('/community','/notifications',path):
            status,url,body=h.req(c,h.CORE+route);assert status==200 and '/login' not in url and len(body)>500
            checks[role+route]=True
    # Synthetic sentinel in our own cookie jar, never read/change the user's browser cookies.
    c=h.login(dict(saved['accounts']['author'],role='REQUESTER'),saved['password'])
    jar=next(handler.cookiejar for handler in c.handlers if hasattr(handler,'cookiejar'))
    jar.set_cookie(http.cookiejar.Cookie(0,'JSESSIONID','original-cookie-sentinel',None,False,'127.0.0.1',False,False,'/',True,False,None,True,None,None,{},False))
    before=next(cookie.value for cookie in jar if cookie.name=='SMARTFIX_STAGE9_CORE')
    fields,_=h.page(c,'/community');status,url,_=h.req(c,h.CORE+'/logout',fields={'_csrf':fields['_csrf']});assert status==200 and '/login' in url
    assert any(cookie.name=='JSESSIONID' and cookie.value=='original-cookie-sentinel' for cookie in jar)
    after=next((cookie.value for cookie in jar if cookie.name=='SMARTFIX_STAGE9_CORE'),None);assert after!=before
    status,url,_=h.req(c,h.CORE+'/community');assert '/login' in url
    checks['logoutExpiresOnlyConfiguredExperimentalSession']=True
    for service,port in env.PORTS.items():
        if service not in env.state['services']:continue
        with urllib.request.urlopen(f'http://127.0.0.1:{port}/actuator/health',timeout=5) as response:
            body=json.load(response);assert body['status']=='UP'
            if service in ('a','b','c'):assert response.headers['X-Community-Instance']==service+':'+str(env.state['services'][service]['pid'])
        checks[service+'Healthy']=True
    with urllib.request.urlopen('http://127.0.0.1:19090/actuator/health',timeout=5) as response:assert json.load(response)['status']=='UP'
    checks['protected19090Healthy']=True
    result={'checks':checks,'performanceTest':False,'services':env.state['services'],'coreArtifactVersion':env.state['coreArtifactVersion'],'communityArtifactVersion':env.state['communityArtifactVersion']}
    (env.R/'phase10/delivery-checks.json').write_text(json.dumps(result,indent=2));print(json.dumps({'functionalChecks':checks}))
if __name__=='__main__':main()
