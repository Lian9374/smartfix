"""Stage9 bounded verification; never formal load. Private raw evidence only."""
import concurrent.futures,hashlib,json,subprocess,threading,time,urllib.request
from pathlib import Path
import stage9 as env
from perf9 import Driver,CORE,API,run
from perf9_observe import capture

def main():
    env.load();checks={};telemetry=[];telemetry_errors=[];stop=threading.Event();d=Driver();d.observe_since=time.time()
    def sample():
        while not stop.is_set():
            t=time.monotonic()
            try:
                row=capture(d);row['captureSeconds']=time.monotonic()-t;telemetry.append(row)
            except Exception as e:
                telemetry_errors.append(type(e).__name__);(env.R/'telemetry-error-private.log').write_text(__import__('traceback').format_exc());return
            stop.wait(max(0,15-(time.monotonic()-t)))
    try:
        for key in env.DBS:
            other='community' if key=='core' else 'core'
            p=subprocess.run(['docker','exec',env.state['container'],'psql','-U',env.ROLES[key],'-d',env.DBS[other],'-At','-c','SELECT 1;'],capture_output=True)
            assert p.returncode!=0;checks[key+'RoleCannotConnectToOtherDatabase']=True
        # Metrics preserve real session/JWT role checks.
        d.prepare('page',1)
        d.request(d.sessions[1000000],CORE+'/actuator/metrics',expected=403)
        d.request(__import__('perf9').opener(),'http://127.0.0.1:19112/actuator/metrics',headers={'Authorization':'Bearer '+d.token(1000000)},expected=403)
        d.request(__import__('perf9').opener(),'http://127.0.0.1:19112/actuator/metrics',expected=401)
        checks['metricsRejectAnonymousAndRequester']=True
        rows=env.state['services'];assert len({rows[k]['pid'] for k in ('a','b','c')})==3 and len({rows[k]['sha256'] for k in ('a','b','c')})==1
        checks['threeDistinctIdenticalCommunityProcesses']=True
        from types import SimpleNamespace
        thread=threading.Thread(target=sample);thread.start()
        for path in ('api','page','repair'):
            run(SimpleNamespace(path=path,rate=.5,duration=20,users=4,hot=False,formal_authorized=False));checks[path+'Smoke']=True
        stop.set();thread.join(timeout=60);assert not thread.is_alive() and telemetry and not telemetry_errors
        checks['realApplicationDatabaseLbOsTelemetry']=True
        assert all(s['database']['outbox']['failed']==0 for s in telemetry)
        checks['noFailedOutbox']=True
        # Recheck account sessions use only the dedicated stage9 cookie.
        jars=[h.cookiejar for h in d.sessions[1000000].handlers if hasattr(h,'cookiejar')]
        assert jars and any(c.name=='SMARTFIX_STAGE9_CORE' for c in jars[0]) and not any(c.name=='JSESSIONID' for c in jars[0])
        checks['isolatedSessionCookie']=True
        proof={'checks':checks,'telemetryErrors':telemetry_errors,'telemetrySamples':len(telemetry),'telemetryCaptureSeconds':[x['captureSeconds'] for x in telemetry],
               'databaseIdentity':{'containerId':env.state['containerId'],'volume':env.state['volume'],'port':15483},'services':env.state['services'],
               'snapshot':json.loads((env.R/'fixed-snapshot/manifest.json').read_text()),'planSha256':hashlib.sha256((env.ROOT/'docs/migration/phase9/PLAN.json').read_bytes()).hexdigest()}
        (env.R/'verification-results.json').write_text(json.dumps(proof,indent=2));(env.R/'final-observations-private.json').write_text(json.dumps(telemetry,indent=2));print(json.dumps({'stage9Checks':checks,'telemetrySamples':len(telemetry)}))
    finally:
        stop.set()
        if 'thread' in locals():thread.join(timeout=60)
        (env.R/'last-telemetry-status.json').write_text(json.dumps({'samples':len(telemetry),'errors':telemetry_errors}))
        d.close()
if __name__=='__main__':main()
