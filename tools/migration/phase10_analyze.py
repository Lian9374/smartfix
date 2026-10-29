"""Safe aggregates only. No passwords, payloads, usernames, SQL text or raw logs in Git.
Missing/failed/incomplete runs never become zeros or certified capacity.
"""
import csv,hashlib,json,math,statistics
from pathlib import Path
import stage9 as env
import perf9_suite as suite
from phase10 import telemetry_goals
OUT=env.ROOT/'docs/migration/phase10';R=env.R/'phase10'

def gauge(app,name,stat='VALUE'):
    values=app.get(name)
    if not isinstance(values,list):return None
    return next((v['value'] for v in values if v['statistic']==stat),None)

def summarize(index,folder,plan):
    record=json.loads((folder/'summary.json').read_text());samples=json.loads((folder/'observations-private.json').read_text());spec=record['spec']
    telemetry=telemetry_goals(samples,plan)
    expected={'core',*('a','b','c')[:spec['replicas']]}
    if any(set(s['applications'])!=expected for s in samples):telemetry.append('WRONG_PROCESS_COUNT')
    if len(samples)<math.floor(plan['timing']['measureSeconds']/plan['timing']['sampleIntervalSeconds'])-1:telemetry.append('TELEMETRY_COVERAGE')
    streams={}
    for name in ('community','repair'):
        if name not in record:continue
        v=record[name];accepted=suite.quality(v,plan) and not telemetry and record['telemetryComplete']
        streams[name]={'successPerSecond':v['successfulBusinessPerSecond'],'p95Seconds':v['businessP95Seconds'],'p99Seconds':v['businessP99Seconds'],'errorRate':v['businessErrorRate'],'errors':v['errors'],'dropped':v['dropped_iterations'],'targetRate':v['targetIterationsPerSecond'],'started':v['startedIterations'],'success':v['successfulBusinessIterations'],'httpRequests':v['actualMeasuredHttpRequests'],'httpRps':v['actualMeasuredHttpRps'],'generator':v['generator'],'passesQuality':bool(accepted)}
    apps={}
    for name in sorted(expected):
        points=[s['applications'][name] for s in samples if name in s['applications']]
        cpu=[gauge(x,'process.cpu.usage') for x in points];cpu=[x for x in cpu if x is not None]
        active=[gauge(x,'hikaricp.connections.active') for x in points];active=[x for x in active if x is not None]
        waiting=[gauge(x,'hikaricp.connections.pending') for x in points];waiting=[x for x in waiting if x is not None]
        apps[name]={'sampledCpuMean':statistics.mean(cpu) if cpu else None,'sampledCpuMax':max(cpu) if cpu else None,'observedPoolActiveMax':max(active) if active else None,'observedPoolWaitersMax':max(waiting) if waiting else None}
    receipt=[s['eventLatency']['receiptP95Seconds'] for s in samples if s.get('eventLatency') and s['eventLatency'].get('receiptP95Seconds') is not None]
    return {'index':int(index),'folder':folder.name,'spec':spec,'streams':streams,'telemetryFailures':sorted(set(telemetry)),'observedReceiptP95MaxSeconds':max(receipt) if receipt else None,'outboxOldestPendingMaxSeconds':max(s['database']['outbox']['oldestPendingSeconds'] for s in samples) if samples else None,'samples':len(samples),'applications':apps,'planSha256':record['planSha256'],'scriptSha256':record.get('scriptSha256',{}),'builds':{k:v['sha256'] for k,v in record['environment']['services'].items()},'snapshot':record['snapshot'],'passesQuality':all(v['passesQuality'] for v in streams.values()) and bool(streams)}

def analyze():
    env.stage3.safety();plan_path=env.ROOT/'docs/migration/phase9/PLAN.json';plan=json.loads(plan_path.read_text(encoding='utf-8'));progress=json.loads((R/'progress.json').read_text());digest=hashlib.sha256(plan_path.read_bytes()).hexdigest();assert digest==progress['planSha256']
    rows=[summarize(index,env.R/item['folder'],plan) for index,item in progress['completed'].items()];rows.sort(key=lambda r:r['index'])
    assert all(r['planSha256']==digest for r in rows)
    for row in rows:
        s=row['spec']
        if s['group']!='mixed' or s['rate']==0:continue
        base=next((x for x in rows if x['spec']==dict(s,rate=0)),None)
        row['repairBaselineIndex']=base['index'] if base else None
        if not base or not base['streams']['repair']['passesQuality']:row['repairP95Degradation']=None;row['passesQuality']=False
        else:
            b=base['streams']['repair']['p95Seconds'];v=row['streams']['repair']['p95Seconds'];row['repairP95Degradation']=v/b-1 if b else None
            row['passesQuality']=row['passesQuality'] and row['repairP95Degradation'] is not None and row['repairP95Degradation']<=plan['quality']['repairP95DegradationMax']
    capacities=[]
    for path in ('api','page'):
        result=[]
        for replicas in (1,2,3):
            accepted=[]
            for rate in plan['experiments']['sustainedCapacity']['rates']:
                candidates=[x for x in rows if x['spec']['group']=='sustainedCapacity' and x['spec']['path']==path and x['spec']['replicas']==replicas and x['spec']['rate']==rate]
                if len(candidates)==3 and {c['spec']['repeat'] for c in candidates}=={1,2,3} and all(c['passesQuality'] for c in candidates):accepted.append((rate,candidates))
            point={'path':path,'replicas':replicas,'Qn':None,'Sn':None,'En':None,'status':'NO_THREE_REPEAT_QUALIFIED_LEVEL'}
            if accepted:
                rate,candidates=max(accepted,key=lambda x:x[0]);throughput=[c['streams']['community']['successPerSecond'] for c in candidates]
                point.update(Qn=statistics.median(throughput),min=min(throughput),max=max(throughput),offeredRate=rate,repeats=3,status='QUALIFIED_OBSERVED_LADDER_LEVEL',upperCapacityNotEstablished=rate==max(plan['experiments']['sustainedCapacity']['rates']))
            result.append(point)
        q1=result[0]['Qn']
        if q1:
            for v in result:
                if v['Qn'] is not None:v['Sn']=v['Qn']/q1;v['En']=v['Qn']/(v['replicas']*q1)
        capacities.extend(result)
    OUT.mkdir(exist_ok=True)
    safe={'date':'2026-10-29','environment':'LOCAL_SAME_HOST_STAGE9','planSha256':digest,'plannedRuns':234,'completedRuns':len(rows),'completeMatrix':len(rows)==234 and not progress.get('active'),'allAttemptsRetained':True,'supersededAttempts':progress.get('superseded',[]),'infrastructureFailures':progress['failures'],'runs':rows,'capacity':capacities,'capacityDefinition':'Qn = median successful business iterations/s at highest unchanged ladder level passing all goals in all 3 repeats; range retained. No qualifying level => undefined, never zero.','scaling':{'horizontal':'1/2/3 equal Community JVM configurations; formal matrix status above','vertical':'NOT_RUN; Core/DB/individual JVM specs fixed','automatic':'NOT_IMPLEMENTED_NOT_RUN','AWS':'NOT_RUN'},'nextStageAllowed':False}
    (OUT/'RESULTS.json').write_text(json.dumps(safe,ensure_ascii=False,indent=2),encoding='utf-8')
    with (OUT/'runs.csv').open('w',newline='',encoding='utf-8') as file:
        columns=['index','repeat','group','replicas','path','rate','stream','successPerSecond','p95Seconds','p99Seconds','errorRate','dropped','httpRps','passesQuality'];writer=csv.DictWriter(file,fieldnames=columns);writer.writeheader()
        for row in rows:
            for stream,v in row['streams'].items():writer.writerow(dict(index=row['index'],stream=stream,**row['spec'],**{k:v[k] for k in ('successPerSecond','p95Seconds','p99Seconds','errorRate','dropped','httpRps')},passesQuality=row['passesQuality']))
    print(json.dumps({'completed':len(rows),'planned':234,'infrastructureFailures':len(progress['failures']),'qualifiedCapacityPoints':sum(x['Qn'] is not None for x in capacities)}))
if __name__=='__main__':analyze()
