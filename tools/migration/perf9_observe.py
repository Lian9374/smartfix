"""Private local telemetry: authenticated Micrometer, PG, LB, OS/JVM and load generator.
No public metrics, SQL text, credentials or response bodies. Deltas derived at analysis time.
"""
import argparse,json,subprocess,time,urllib.request
import stage9 as env
from perf9 import Driver,opener

def capture(driver):
    env.load();sample={'atUnix':time.time(),'applications':{},'database':{},'errors':[]}
    admin=driver.saved['accounts']['admin'];actor=admin['id']
    if actor not in driver.sessions:driver.login(actor,admin['username'],'ADMINISTRATOR')
    metrics=['process.cpu.usage','system.cpu.usage','jvm.memory.used','jvm.gc.pause','jvm.gc.memory.allocated','jvm.threads.live','hikaricp.connections.active','hikaricp.connections.idle','hikaricp.connections.pending','hikaricp.connections.timeout','tomcat.threads.busy','http.server.requests']
    for name,row in env.state['services'].items():
        if name=='lb':continue
        app={};base='http://127.0.0.1:'+str(env.PORTS[name]);c=driver.sessions[actor] if name=='core' else opener();headers={} if name=='core' else {'Authorization':'Bearer '+driver.token(actor,'ADMINISTRATOR')}
        body,_=driver.request(c,base+'/actuator/metrics',headers=headers);available=json.loads(body)['names']
        for metric in metrics:
            if metric not in available:app[metric]={'status':'NOT_YET_REGISTERED'};continue
            body,_=driver.request(c,base+'/actuator/metrics/'+metric,headers=headers);app[metric]=json.loads(body)['measurements']
        app['pid']=row['pid'];sample['applications'][name]=app
    queries={
      'database':"SELECT json_agg(x) FROM (SELECT datname,numbackends,xact_commit,xact_rollback,blks_read,blks_hit,tup_returned,tup_fetched,tup_inserted,tup_updated,deadlocks,blk_read_time,blk_write_time FROM pg_stat_database WHERE datname IN ('smartfix_stage9_core','smartfix_stage9_community')) x;",
      'activity':"SELECT coalesce(json_agg(x),'[]') FROM (SELECT datname,state,wait_event_type,wait_event,count(*) FROM pg_stat_activity WHERE datname LIKE 'smartfix_stage9_%' GROUP BY 1,2,3,4) x;",
      'locks':"SELECT coalesce(json_agg(x),'[]') FROM (SELECT locktype,mode,granted,count(*) FROM pg_locks GROUP BY 1,2,3) x;",
      'io':"SELECT json_agg(x) FROM (SELECT backend_type,object,context,reads,writes,read_time,write_time,hits,evictions FROM pg_stat_io) x;",
      'statements':"SELECT coalesce(json_agg(x),'[]') FROM (SELECT queryid,calls,total_exec_time,mean_exec_time,rows,shared_blks_hit,shared_blks_read,blk_read_time,blk_write_time FROM pg_stat_statements WHERE dbid=(SELECT oid FROM pg_database WHERE datname=current_database()) ORDER BY total_exec_time DESC LIMIT 10) x;",
      'outbox':"SELECT json_build_object('pending',count(*) FILTER(WHERE status='PENDING'),'failed',count(*) FILTER(WHERE status='FAILED'),'leased',count(*) FILTER(WHERE lease_until>now()),'oldestPendingSeconds',coalesce(max(extract(epoch FROM now()-created_at)) FILTER(WHERE status='PENDING'),0),'attempts',coalesce(sum(attempts),0)) FROM community_outbox;"
    }
    def checked_sql(key,q):
        # identity() has just validated immutable container id, volume and port for this sample.
        return env.command(['docker','exec','-i',env.state['container'],'psql','-q','-v','ON_ERROR_STOP=1','-U','stage9_admin','-d',env.DBS[key],'-At'],input=q.encode(),timeout=15).decode().strip()
    combined='\n'.join('SELECT to_jsonb(q) FROM ('+q.rstrip(';')+') q;' for q in queries.values())
    values=checked_sql('community',combined).splitlines()
    assert len(values)==len(queries)
    for key,value in zip(queries,values):sample['database'][key]=next(iter(json.loads(value).values()))
    sample['database']['coreStatements']=json.loads(checked_sql('core',queries['statements']))
    # Community clock to Core receipt/visible notification clocks; latest1000 events, no payload.
    since=getattr(driver,'observe_since',time.time()-60)
    events=json.loads(checked_sql('community',"SELECT coalesce(json_agg(x),'[]') FROM (SELECT event_id,extract(epoch FROM created_at) created FROM community_outbox WHERE created_at>=to_timestamp("+str(since)+") ORDER BY created_at DESC LIMIT 1000) x;"))
    if events:
        values=','.join("('"+str(x['event_id'])+"'::uuid,"+str(x['created'])+')' for x in events)
        sample['eventLatency']=json.loads(checked_sql('core',"WITH e(id,created) AS (VALUES "+values+") SELECT json_build_object('count',count(*),'receiptP95Seconds',percentile_cont(.95) WITHIN GROUP(ORDER BY extract(epoch FROM i.received_at)-e.created),'visibleNotificationP95Seconds',percentile_cont(.95) WITHIN GROUP(ORDER BY extract(epoch FROM n.created_at)-e.created)) FROM e JOIN core_community_inbox i ON i.event_id=e.id LEFT JOIN notifications n ON n.dedup_key='COMMUNITY_EVENT:'||e.id;"))
    sample['eventWindowStartUnix']=since
    with urllib.request.urlopen('http://127.0.0.1:19114/internal/perf/lb',timeout=3) as r:sample['loadBalancer']=json.load(r)
    all_pids={v['pid'] for v in env.state['services'].values()}
    for p in env.R.glob('generator-*.json'):
        try:row=json.loads(p.read_text())
        except FileNotFoundError:continue  # A bounded smoke driver may have just exited.
        all_pids.update([int(row['driverPid']),int(row['signerPid'])])
    pids=','.join(str(p) for p in sorted(all_pids))
    # GetProcess CPU seconds are cumulative (not a fabricated instantaneous percentage).
    ps=f"$ids=@({pids},{__import__('os').getpid()}); $procs=Get-Process -Id $ids -ErrorAction SilentlyContinue | Select-Object Id,CPU,WorkingSet64,@{{n='Threads';e={{$_.Threads.Count}}}}; $net=Get-NetAdapterStatistics | Select-Object Name,ReceivedBytes,SentBytes; @{{processes=@($procs);network=@($net);hostCpu=(Get-CimInstance Win32_PerfFormattedData_PerfOS_Processor -Filter \"Name='_Total'\").PercentProcessorTime;logicalCpus=[Environment]::ProcessorCount}} | ConvertTo-Json -Depth 5 -Compress"
    sample['os']=json.loads(env.command(['powershell','-NoProfile','-Command',ps],timeout=15).decode('utf-8-sig'))
    sample['docker']=json.loads(env.command(['docker','stats','--no-stream','--format','{{json .}}',env.state['container']],timeout=10))
    return sample

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--samples',type=int,default=2);p.add_argument('--interval',type=float,default=15);p.add_argument('--output',default='observations-private.json');a=p.parse_args()
    assert 1<=a.samples<=200 and a.interval>=5 and '/' not in a.output and '\\' not in a.output
    d=Driver();rows=[]
    try:
        for i in range(a.samples):
            start=time.perf_counter();row=capture(d);row['captureSeconds']=time.perf_counter()-start;rows.append(row)
            if i+1<a.samples:time.sleep(max(0,a.interval-row['captureSeconds']))
        (env.R/a.output).write_text(json.dumps(rows,indent=2));print(json.dumps({'samples':len(rows),'applications':list(rows[-1]['applications']),'outbox':rows[-1]['database']['outbox'],'eventLatency':rows[-1].get('eventLatency'),'output':a.output}))
    finally:d.close()
