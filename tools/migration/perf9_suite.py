"""Formal matrix orchestrator, LOCKED until stage10. --list only is safe in stage9.
Each run restores a new database generation and prewarms; no best-run selection.
"""
import argparse,concurrent.futures,json,time
import stage9 as env
import perf9 as load
from perf9_observe import capture

def matrix(plan):
    for repeat,order in enumerate(plan['timing']['orders'],1):
        for n in order:
            for path in ('api','page'):
                for group,rates in [('equalArrival',plan['experiments']['equalArrival']['rates']),('sustainedCapacity',plan['experiments']['sustainedCapacity']['rates']),('mixed',plan['experiments']['mixed']['communityRates']),('hotContention',[5])]:
                    for rate in rates:yield {'repeat':repeat,'replicas':n,'path':path,'group':group,'rate':rate}

def configure(n):
    env.load()
    for name in list(env.state['services']):env.stop(name)
    env.restore_snapshot();env.start('core')
    for name in ('a','b','c')[:n]:env.start(name)
    env.start('lb')
    for _ in range(20):
        import urllib.request
        with urllib.request.urlopen('http://127.0.0.1:19114/actuator/health',timeout=3) as r:healthy=json.load(r)['backends']
        if len(healthy)==n:return
        time.sleep(.5)
    raise RuntimeError('Unexpected number of healthy backends')

def quality(result,plan):
    q=plan['quality'];e=result['errors'];endpoint=result['endpointLatency'].values()
    return bool(result['successfulBusinessIterations'] and result['businessP95Seconds']<=q['businessP95Seconds'] and result['businessP99Seconds']<=q['businessP99Seconds'] and result['businessErrorRate']<=q['businessErrorRateMax'] and not e.get('content') and not e.get('unexpected_login') and not e.get('http_401') and not e.get('http_403') and not result['dropped_iterations'] and result['startedIterations']/result['windowSeconds']/result['targetIterationsPerSecond']>=q['startedToTargetRateMin'] and all(x['p95']<=q['endpointP95Seconds'] and x['p99']<=q['endpointP99Seconds'] for x in endpoint) and result['generator']['cpuPercentOneCore']<=q['generatorCpuPercentMax'] and result['generator']['scheduleP99Seconds']<=q['generatorScheduleP99SecondsMax'])

def one(spec,plan):
    configure(spec['replicas']);driver=load.Driver();observer=load.Driver();samples=[];stop=__import__('threading').Event();monitor_errors=[]
    folder=env.R/('formal-'+load.datetime_tag());folder.mkdir()
    def monitor():
        try:
            while not stop.is_set():
                t=time.perf_counter();sample=capture(observer);sample['captureSeconds']=time.perf_counter()-t;samples.append(sample)
                (folder/'observations-private.json').write_text(json.dumps(samples))
                stop.wait(max(0,plan['timing']['sampleIntervalSeconds']-sample['captureSeconds']))
        except Exception as e:monitor_errors.append(type(e).__name__)
    try:
        driver.location=int(env.sql('core','SELECT min(id) FROM locations WHERE active;'));users=900 if spec['group']=='mixed' else 1000
        if spec['rate']>0:driver.prepare(spec['path'],users)
        if spec['group']=='mixed':driver.prepare('repair',100)
        # Warm every active stream for the SAME predeclared window, including repair-only baselines.
        # Previously only Community was warmed, making mixed baselines incomparable.
        warmup={}
        with concurrent.futures.ThreadPoolExecutor(2) as pool:
            jobs={}
            if spec['rate']>0:jobs['community']=pool.submit(load.arrivals,driver,spec['path'],plan['timing']['warmupBusinessRate'],plan['timing']['warmupSeconds'],users,64,spec['group']=='hotContention',100000)
            if spec['group']=='mixed':jobs['repair']=pool.submit(load.arrivals,driver,'repair',2,plan['timing']['warmupSeconds'],100,64,False,100000)
            for name,f in jobs.items():warmup[name]=f.result()
        observer.observe_since=time.time();thread=__import__('threading').Thread(target=monitor);thread.start()
        time.sleep(1);result={};duration=plan['timing']['measureSeconds']
        with concurrent.futures.ThreadPoolExecutor(2) as pool:
            jobs={}
            if spec['rate']>0:jobs['community']=pool.submit(load.arrivals,driver,spec['path'],spec['rate'],duration,users,64,spec['group']=='hotContention')
            if spec['group']=='mixed':jobs['repair']=pool.submit(load.arrivals,driver,'repair',2,duration,100)
            for name,f in jobs.items():result[name]=f.result()
        # Allow bounded event drain, still sampled. Not part of offered-load window.
        end=time.perf_counter()+plan['timing']['drainSecondsMax']
        while time.perf_counter()<end and env.sql('community',"SELECT count(*) FROM community_outbox WHERE status<>'SENT';")!='0':time.sleep(1)
        stop.set();thread.join(timeout=60);assert not thread.is_alive()
        # A final sample is required after drain; periodic snapshots alone can miss an ending backlog.
        samples.append(capture(observer))
        for name,row in warmup.items():(folder/(name+'-warmup-private.json')).write_text(json.dumps(row))
        for name,row in result.items():
            (folder/(name+'-iterations-private.json')).write_text(json.dumps(row.pop('raw')))
        result['spec']=spec;result['applicationGoalsPass']=all(quality(v,plan) for k,v in result.items() if k in ('community','repair'))
        result['monitorErrors']=monitor_errors;result['telemetryComplete']=bool(samples) and not monitor_errors
        result['capacityAccepted']=False  # Requires telemetry/lag/generator and mixed-baseline analysis, never just HTTP.
        result['analysisPending']='Evaluate all per-sample thresholds, mixed repair-only p95 baseline and all three repeats before capacity acceptance.'
        result['planSha256']=__import__('hashlib').sha256((env.ROOT/'docs/migration/phase9/PLAN.json').read_bytes()).hexdigest()
        result['environment']=env.state;result['snapshot']=json.loads((env.R/'fixed-snapshot/manifest.json').read_text())
        (folder/'observations-private.json').write_text(json.dumps(samples));(folder/'summary.json').write_text(json.dumps(result,indent=2));return folder
    finally:
        stop.set()
        if 'thread' in locals():thread.join(timeout=60)
        (folder/'observations-private.json').write_text(json.dumps(samples))
        driver.close();observer.close()

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--list',action='store_true');p.add_argument('--execute',action='store_true');p.add_argument('--index',type=int);a=p.parse_args()
    plan=json.loads((env.ROOT/'docs/migration/phase9/PLAN.json').read_text());runs=list(matrix(plan))
    if a.list:print(json.dumps({'runs':len(runs),'matrix':runs},indent=2))
    elif a.execute:
        env.stage3.safety();w=json.loads(env.stage3.STATE.read_text(encoding='utf-8'))
        assert w.get('stage9',{}).get('status')=='PASS' and w.get('stage10',{}).get('status')=='IN_PROGRESS','Formal execution requires explicit stage10 authorization'
        assert a.index is not None and 0<=a.index<len(runs),'Explicit bounded run index required'
        one(runs[a.index],plan)
    else:p.error('Use --list; formal --execute requires phase10')
