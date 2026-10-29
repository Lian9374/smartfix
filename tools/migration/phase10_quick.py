"""User-authorized reduced comparison. 12 short windows, never a capacity certification.
Full 234-run protocol remains preserved and disabled by workspace status.
"""
import argparse,concurrent.futures,datetime,hashlib,json,shutil,threading,time,traceback
from pathlib import Path
import stage9 as env
import perf9 as load
import perf9_suite as suite
from perf9_observe import capture
from phase10 import lock,protected,telemetry_goals,write
ROOT=env.ROOT;R=env.R/'phase10/quick';PLAN=ROOT/'docs/migration/phase10/QUICK_PLAN.json'

def matrix():
    return [{'repeat':repeat,'replicas':n,'path':path} for repeat,order in [(1,(1,2,3)),(2,(3,2,1))] for n in order for path in ('api','page')]

def one(index,spec,plan,code_hashes,plan_digest):
    suite.configure(spec['replicas']);folder=R/('run-'+str(index)+'-'+load.datetime_tag());folder.mkdir()
    d=load.Driver();observer=load.Driver();samples=[];errors=[];stop=threading.Event();thread=None
    def sample():
        while not stop.is_set():
            t=time.perf_counter()
            try:
                point=capture(observer);point['captureSeconds']=time.perf_counter()-t;samples.append(point)
                write(folder/'observations-private.json',samples)
            except Exception as e:
                errors.append({'atUnix':time.time(),'type':type(e).__name__})
                (folder/'monitor-error-private.log').write_text(traceback.format_exc(),encoding='utf-8')
            stop.wait(max(0,plan['sampleIntervalSeconds']-(time.perf_counter()-t)))
    try:
        preparation=d.prepare(spec['path'],plan['activeUsers'])
        # Cold WMI/Docker telemetry can be slow immediately after a host reboot.
        # Establish collection readiness BEFORE the measurement window, never omit failed samples inside it.
        readiness=[]
        for attempt in range(3):
            try:
                point=capture(observer);readiness.append(point);break
            except Exception as e:
                (folder/('readiness-'+str(attempt)+'-private.log')).write_text(traceback.format_exc(),encoding='utf-8')
                if attempt==2:raise
                time.sleep(2)
        write(folder/'readiness-private.json',readiness)
        warm=suite.load.arrivals(d,spec['path'],plan['warmupRate'],plan['warmupSeconds'],plan['activeUsers'],offset=100000)
        write(folder/'warmup-private.json',warm)
        observer.observe_since=time.time();thread=threading.Thread(target=sample);thread.start()
        result=load.arrivals(d,spec['path'],plan['offeredBusinessIterationsPerSecond'],plan['measureSeconds'],plan['activeUsers'])
        end=time.perf_counter()+30
        while time.perf_counter()<end and env.sql('community',"SELECT count(*) FROM community_outbox WHERE status<>'SENT';")!='0':time.sleep(.5)
        stop.set();thread.join(timeout=60);assert not thread.is_alive()
        samples.append(capture(observer));write(folder/'observations-private.json',samples)
        write(folder/'iterations-private.json',result.pop('raw'))
        original=json.loads((ROOT/'docs/migration/phase9/PLAN.json').read_text(encoding='utf-8'))
        failures=telemetry_goals(samples,original)
        if errors:failures.append('COLLECTION_ERRORS')
        record={'index':index,'spec':spec,'business':result,'preparationSeconds':preparation,'qualityTargets':original['quality'],'businessGoalsPass':suite.quality(result,original),'telemetryFailures':failures,'monitorErrors':errors,'samples':len(samples),'shortWindowOnly':True,'capacityCertification':False,
          'planSha256':plan_digest,'snapshot':json.loads((env.R/'fixed-snapshot/manifest.json').read_text()),'services':env.state['services'],'scriptSha256':code_hashes}
        write(folder/'summary.json',record);return folder,record
    finally:
        stop.set()
        if thread:thread.join(timeout=60)
        d.close();observer.close()

def run():
    R.mkdir(exist_ok=True);guard=lock()
    try:
        env.stage3.safety();w=json.loads(env.stage3.STATE.read_text(encoding='utf-8'));assert w['stage10']['status']=='QUICK_IN_PROGRESS'
        plan=json.loads(PLAN.read_text(encoding='utf-8'));digest=hashlib.sha256(PLAN.read_bytes()).hexdigest();assert w['stage10']['quickPlanSha256']==digest
        code_hashes={name:hashlib.sha256((ROOT/'tools/migration'/name).read_bytes()).hexdigest() for name in ('phase10_quick.py','phase10.py','perf9.py','perf9_suite.py','perf9_observe.py','stage9.py','stage9_lb.py')}
        baseline=json.loads((env.R/'phase10/protected-before.json').read_text());assert protected()==baseline
        state_path=R/'progress.json';state=json.loads(state_path.read_text()) if state_path.exists() else {'planned':12,'completed':{},'failures':[],'planSha256':digest}
        assert state['planSha256']==digest
        for index,spec in enumerate(matrix()):
            w=json.loads(env.stage3.STATE.read_text(encoding='utf-8'));assert w['stage10']['status']=='QUICK_IN_PROGRESS'
            if str(index) in state['completed']:continue
            assert hashlib.sha256(PLAN.read_bytes()).hexdigest()==digest,'Quick plan changed during execution'
            assert protected()==baseline and shutil.disk_usage(ROOT).free>15*1024**3
            assert all(hashlib.sha256((ROOT/'tools/migration'/name).read_bytes()).hexdigest()==digest0 for name,digest0 in code_hashes.items()),'Do not change a running measurement engine'
            state['active']={'index':index,'spec':spec,'startedAt':time.time()};write(state_path,state)
            try:
                folder,record=one(index,spec,plan,code_hashes,digest)
                state['completed'][str(index)]={'folder':folder.name,'spec':spec,'businessGoalsPass':record['businessGoalsPass'],'telemetryFailures':record['telemetryFailures']};state.pop('active',None);write(state_path,state)
                print(json.dumps({'completed':len(state['completed']),'planned':12,'spec':spec,'businessGoalsPass':record['businessGoalsPass'],'telemetryFailures':record['telemetryFailures']}),flush=True)
            except Exception as e:
                state['failures'].append({'index':index,'type':type(e).__name__});write(state_path,state);(R/'last-failure-private.log').write_text(traceback.format_exc(),encoding='utf-8');raise
    finally:guard.close()
if __name__=='__main__':run()
