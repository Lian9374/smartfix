"""Sequential, resumable execution of the unchanged phase9 formal matrix.
Only allowlisted phase9 processes/15483 DBs are mutable. Never operates AWS or original DB.
Raw logs and data remain under the already private ignored runtime.
"""
import argparse,contextlib,datetime,hashlib,json,msvcrt,os,shutil,subprocess,time,traceback,urllib.request
from pathlib import Path
import stage9 as env
import perf9_suite as suite
ROOT=env.ROOT; R=env.R/'phase10';PLAN=ROOT/'docs/migration/phase9/PLAN.json'

def write(path,value):
    tmp=path.with_suffix(path.suffix+'.tmp');tmp.write_text(json.dumps(value,indent=2),encoding='utf-8');os.replace(tmp,path)

def lock():
    R.mkdir(exist_ok=True);f=(R/'runner.lock').open('a+b');f.seek(0);f.write(b'0');f.flush();f.seek(0)
    msvcrt.locking(f.fileno(),msvcrt.LK_NBLCK,1);return f

def authorized():
    env.stage3.safety();w=json.loads(env.stage3.STATE.read_text(encoding='utf-8'))
    assert w['stage9']['status']=='PASS' and w['stage9']['browserAcceptance']=='PASS_USER_CONFIRMED'
    assert w['stage10']['status']=='IN_PROGRESS' and w['stage10']['planSha256']==hashlib.sha256(PLAN.read_bytes()).hexdigest()
    env.load();assert not env.state.get('restoreInProgress')

def protected():
    result={}
    for name in ('smartfix-db','sf-stage1-20261028-105721-db'):
        d=json.loads(env.command(['docker','inspect',name]))[0]
        result[name]={'id':d['Id'],'mounts':d['Mounts'],'running':d['State']['Running'],'startedAt':d['State']['StartedAt']}
    with urllib.request.urlopen('http://127.0.0.1:19090/actuator/health',timeout=3) as response:assert json.load(response)['status']=='UP'
    return result

def telemetry_goals(samples,plan):
    q=plan['quality'];problems=[]
    if len(samples)<3:return ['INSUFFICIENT_TELEMETRY']
    for s in samples:
        if not s['applications'] or s.get('errors'):problems.append('APPLICATION_TELEMETRY_MISSING')
        for name,app in s['applications'].items():
            if any(isinstance(v,dict) for v in app.values()):problems.append('METRIC_NOT_REGISTERED:'+name)
        box=s['database']['outbox']
        if box['failed']>q['failedOutboxMax']:problems.append('FAILED_OUTBOX')
        if box['oldestPendingSeconds']>q['outboxOldestPendingSecondsMax']:problems.append('OUTBOX_LAG')
        latency=s.get('eventLatency')
        if latency and latency.get('receiptP95Seconds') is not None and latency['receiptP95Seconds']>q['eventReceiptP95SecondsMax']:problems.append('EVENT_RECEIPT_LAG')
    final=samples[-1]
    if final['database']['outbox']['pending'] or final['database']['outbox']['leased']:problems.append('OUTBOX_NOT_DRAINED')
    return sorted(set(problems))

def execute(indices):
    guard=lock()
    try:
        authorized();plan=json.loads(PLAN.read_text(encoding='utf-8'));runs=list(suite.matrix(plan));baseline=json.loads((R/'protected-before.json').read_text())
        assert protected()==baseline,'Protected resources differ from phase10 preflight'
        progress_path=R/'progress.json';progress=json.loads(progress_path.read_text()) if progress_path.exists() else {'completed':{},'failures':[],'totalRuns':len(runs),'planSha256':hashlib.sha256(PLAN.read_bytes()).hexdigest()}
        assert progress['planSha256']==hashlib.sha256(PLAN.read_bytes()).hexdigest()
        for index in indices:
            authorized();assert 0<=index<len(runs)
            if str(index) in progress['completed']:continue
            assert shutil.disk_usage(ROOT).free>15*1024**3,'Insufficient free space; preserve original and all archives'
            assert protected()==baseline,'Protected environment changed: stop, diagnose without touching it'
            spec=runs[index];progress['active']={'index':index,'spec':spec,'startedAt':time.time()};write(progress_path,progress)
            attempt=R/('attempt-'+str(index)+'-'+datetime.datetime.now().strftime('%Y%m%d-%H%M%S'));attempt.mkdir()
            try:
                folder=suite.one(spec,plan);row=json.loads((folder/'summary.json').read_text());samples=json.loads((folder/'observations-private.json').read_text())
                row['telemetryGoalFailures']=telemetry_goals(samples,plan);row['capacityAccepted']=bool(row['applicationGoalsPass'] and row['telemetryComplete'] and not row['telemetryGoalFailures'])
                expected={'core',*('a','b','c')[:spec['replicas']]}
                if any(set(s['applications'])!=expected for s in samples):row['telemetryGoalFailures'].append('WRONG_PROCESS_COUNT');row['capacityAccepted']=False
                row['mixedBaselineComparisonPending']=spec['group']=='mixed'
                # Mixed business acceptance needs its own repair-only baseline; never label it final here.
                if row['mixedBaselineComparisonPending']:row['capacityAccepted']=False
                row['matrixIndex']=index;row['runnerHead']=subprocess.check_output(['git','rev-parse','HEAD']).decode().strip()
                row['scriptSha256']={p:hashlib.sha256((ROOT/'tools/migration'/p).read_bytes()).hexdigest() for p in ('perf9.py','perf9_suite.py','perf9_observe.py','phase10.py','stage9.py','stage9_lb.py')}
                write(folder/'summary.json',row)
                for pattern in ('*-private.log',):
                    for source in env.R.glob(pattern):shutil.copy2(source,attempt/source.name)
                progress['completed'][str(index)]={'folder':folder.name,'spec':spec,'applicationGoalsPass':row['applicationGoalsPass'],'telemetryComplete':row['telemetryComplete'],'capacityAccepted':row['capacityAccepted'],'telemetryGoalFailures':row['telemetryGoalFailures']}
                progress.pop('active',None);write(progress_path,progress)
                print(json.dumps({'completed':index,'of':len(runs),'spec':spec,'applicationGoalsPass':row['applicationGoalsPass'],'telemetryFailures':row['telemetryGoalFailures']}),flush=True)
            except Exception as error:
                (attempt/'failure-private.log').write_text(traceback.format_exc(),encoding='utf-8')
                for source in env.R.glob('*-private.log'):shutil.copy2(source,attempt/source.name)
                progress['failures'].append({'index':index,'spec':spec,'errorType':type(error).__name__,'attempt':attempt.name});write(progress_path,progress)
                raise
    finally:guard.close()

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('--first',type=int,default=0);p.add_argument('--last',type=int,default=0);a=p.parse_args()
    assert 0<=a.first<=a.last<234
    execute(range(a.first,a.last+1))
