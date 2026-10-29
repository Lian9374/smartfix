"""Final read-only protection checks and a NEW original-backup restore.

No original database connection, no replacement of the 19090/15443 baseline.
Private credentials/results remain under ignored .migration-runtime/stage11.
"""
import hashlib,json,os,secrets,shutil,socket,subprocess,time,urllib.request
from pathlib import Path
import stage3,stage9
import verify_stage6 as web

ROOT=stage3.ROOT
R=ROOT/'.migration-runtime/stage11'
PORT=19121
DBPORT=15493

def digest(p):return hashlib.sha256(p.read_bytes()).hexdigest()
def health(port):
    with urllib.request.urlopen(f'http://127.0.0.1:{port}/actuator/health',timeout=8) as r:
        assert json.load(r)['status']=='UP'
def inspect(name):return json.loads(stage3.command(['docker','inspect',name]))[0]
def protected():
    return {name:{'id':d['Id'],'state':d['State'],'mounts':d['Mounts'],'ports':d['HostConfig']['PortBindings']}
            for name in ('smartfix-db','sf-stage1-20261028-105721-db') for d in [inspect(name)]}
def load():
    s=json.loads((R/'resources-private.json').read_text(encoding='utf-8'))
    d=inspect(s['container'])
    assert s['container'].startswith('sf-migration-stage11-') and d['Id']==s['containerId']
    assert d['Config']['Labels']['smartfix.migration.stage']=='11'
    assert len(d['Mounts'])==1 and d['Mounts'][0]['Type']=='volume' and d['Mounts'][0]['Name']==s['volume']
    assert d['HostConfig']['PortBindings']=={'5432/tcp':[{'HostIp':'127.0.0.1','HostPort':str(DBPORT)}]}
    assert Path(s['root']).resolve()==R/'restored-original'
    return s
def save(s):(R/'resources-private.json').write_text(json.dumps(s,indent=2),encoding='utf-8')
def sql(s,q):
    load()
    return stage3.command(['docker','exec','-i',s['container'],'psql','-v','ON_ERROR_STOP=1','-U','stage11_admin','-d','smartfix_stage11_original','-At'],input=q.encode()).decode().strip()

def restore():
    stage3.safety();R.mkdir(exist_ok=True)
    w=json.loads(stage3.STATE.read_text(encoding='utf-8'));backup=Path(w['backupRoot'])
    manifest=json.loads((backup/'file-manifest.json').read_text(encoding='utf-8'))
    evidence={'startCommit':stage3.command(['git','rev-parse','HEAD']).decode().strip(),'performanceRun':False}
    before=protected();(R/'protected-before-private.json').write_text(json.dumps(before,indent=2))
    for root in (Path(w['originalRoot']),backup/'project-snapshot'):
        for row in manifest:
            f=root/row['path'];assert f.is_file() and not f.is_symlink() and digest(f)==row['sha256'],'Source/backup fingerprint mismatch'
    evidence['originalAndBackupFingerprintFiles']=len(manifest)
    dump=Path(w['resources']['databaseBackupRoot'])/'recovered-current.dump'
    dump_sha=digest(dump);evidence['originalDumpSha256']=dump_sha
    if not (R/'resources-private.json').exists():
        for port in (PORT,DBPORT):
            with socket.socket() as sock:sock.bind(('127.0.0.1',port))
        assert not (R/'restored-original').exists()
        # Copy every backed-up file, including ignored runtime/config, except Git metadata.
        shutil.copytree(backup/'project-snapshot',R/'restored-original',ignore=shutil.ignore_patterns('.git'))
        root=R/'restored-original'
        jar=root/'stage11-original.jar'
        shutil.copy2(Path(w['baselineVerifyRoot'])/'target/smartfix-0.0.1-SNAPSHOT.jar',jar)
        tag='sf-migration-stage11-'+time.strftime('%Y%m%d-%H%M%S')
        pw=secrets.token_hex(24);envfile=R/'postgres-private.env'
        envfile.write_text('POSTGRES_USER=stage11_admin\nPOSTGRES_DB=smartfix_stage11_original\nPOSTGRES_PASSWORD='+pw+'\n')
        stage3.command(['docker','volume','create','--label','smartfix.migration.stage=11',tag+'-data'])
        stage3.command(['docker','run','-d','--name',tag+'-db','--label','smartfix.migration.stage=11','--env-file',str(envfile),'-p',f'127.0.0.1:{DBPORT}:5432','--mount','type=volume,source='+tag+'-data,target=/var/lib/postgresql/data','postgres:16-alpine'])
        s={'container':tag+'-db','containerId':inspect(tag+'-db')['Id'],'volume':tag+'-data','root':str(root),'password':pw,'jar':str(jar),'jarSha256':digest(jar),'restored':False};save(s)
    s=load();root=Path(s['root'])
    if not s['restored']:
        for _ in range(45):
            if subprocess.run(['docker','exec',s['container'],'pg_isready','-U','stage11_admin'],capture_output=True).returncode==0:break
            time.sleep(1)
        assert sql(s,"SELECT count(*) FROM information_schema.tables WHERE table_schema='public';")=='0','Partial restore: diagnose; never overwrite'
        with dump.open('rb') as inp:stage3.command(['docker','exec','-i',s['container'],'pg_restore','-U','stage11_admin','-d','smartfix_stage11_original','--no-owner','--no-acl','--exit-on-error'],stdin=inp)
        expected=json.loads((dump.parent/'table-counts.json').read_text())
        actual={t:int(sql(s,'SELECT count(*) FROM "'+t+'";')) for t in expected}
        assert actual==expected,'Backup table counts differ'
        s['restoredCounts']=actual;s['restored']=True;save(s)
    if not s.get('appPid'):
        uploads=root/'stage11-uploads';uploads.mkdir(exist_ok=True)
        source_uploads=backup/'project-snapshot/uploads'
        if source_uploads.exists():shutil.copytree(source_uploads,uploads,dirs_exist_ok=True)
        env={k:v for k,v in os.environ.items() if not k.upper().startswith(('DB_','SPRING_','SMARTFIX_','POSTGRES_','JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS'))}
        env.update(DB_URL=f'jdbc:postgresql://127.0.0.1:{DBPORT}/smartfix_stage11_original',DB_USERNAME='stage11_admin',DB_PASSWORD=s['password'],SMARTFIX_UPLOAD_DIR=str(uploads),SMARTFIX_NOTIFICATION_EMAIL_ENABLED='false',SMARTFIX_BOOTSTRAP_ADMIN_ENABLED='false',SMARTFIX_DELIVERY_SCHEDULING_ENABLED='false',SMARTFIX_DELIVERY_EAGER_ENABLED='false',SMARTFIX_SESSION_COOKIE_SECURE='false')
        with (R/'restored-app-private.log').open('xb') as out:
            p=subprocess.Popen(['java','-Xmx384m','-jar',s['jar'],'--server.address=127.0.0.1',f'--server.port={PORT}','--server.servlet.session.cookie.name=SMARTFIX_STAGE11_ORIGINAL','--spring.jpa.hibernate.ddl-auto=validate'],cwd=root,env=env,stdout=out,stderr=out,creationflags=subprocess.CREATE_NO_WINDOW)
        s['appPid']=p.pid;save(s)
    for _ in range(80):
        try:health(PORT);break
        except Exception:time.sleep(1)
    else:raise RuntimeError('New original restore failed startup; inspect private log')
    web.CORE=f'http://127.0.0.1:{PORT}'
    if not s.get('accounts'):
        password='Restore11!'+secrets.token_hex(12);suffix=secrets.token_hex(4)
        accounts={k:{'username':'s11_'+k+'_'+suffix,'role':role} for k,role in [('requester','REQUESTER'),('tech','TECHNICIAN'),('admin','ADMINISTRATOR')]}
        c=web.client();fields,_=web.page(c,'/register');a=accounts['requester']
        status,url,_=web.req(c,web.CORE+'/register',fields=dict(username=a['username'],displayName='Stage11 backup verification',password=password,confirmPassword=password,_csrf=fields['_csrf']))
        assert status==200 and '/login' in url
        for key in ('tech','admin'):
            a=accounts[key];sql(s,"INSERT INTO users(username,display_name,password_hash,role,account_status,security_version,created_at,updated_at,password_change_required) SELECT '"+a['username']+"','Stage11 synthetic "+key+"',password_hash,'"+a['role']+"','ACTIVE',0,now(),now(),false FROM users WHERE username='"+accounts['requester']['username']+"';")
        s['accounts']=accounts;s['accountPassword']=password;save(s)
        (R/'test-accounts-private.json').write_text(json.dumps({'url':web.CORE,'accounts':accounts,'password':password},indent=2))
    checks={}
    for key,route in [('requester','/requests/mine'),('tech','/workorders/mine'),('admin','/admin/requests')]:
        c=web.login(s['accounts'][key],s['accountPassword'])
        for path in ('/community','/notifications',route):
            status,url,body=web.req(c,web.CORE+path);assert status==200 and '/login' not in url and len(body)>500
            checks[key+path]=True
    evidence.update(newRestoreUrl=web.CORE+'/login',restoredCounts=s['restoredCounts'],restoredJarSha256=s['jarSha256'],restoreChecks=checks,historicalAttachmentRead='N/A: original backup has zero attachment rows',restoreContainer=s['container'],restoreVolume=s['volume'])
    assert protected()==before,'Protected resource changed; investigate without altering it'
    assert digest(dump)==dump_sha
    health(19090);evidence['protectedResourcesUnchanged']=True
    (R/'restore-evidence.json').write_text(json.dumps(evidence,indent=2))
    print(json.dumps(evidence))

def stop_experiment_rehearsal():
    stage3.safety();stage9.load();before=protected();health(19090)
    services=set(stage9.state['services']);assert services=={'core','a','b','lb'}
    for service in ('core','lb','a','b'):stage9.stop(service)
    health(19090)
    w=json.loads(stage3.STATE.read_text(encoding='utf-8'))
    account_lines=(Path(w['resources']['databaseBackupRoot'])/'test-accounts-private.txt').read_text(encoding='utf-8').splitlines()
    account_fields=dict(line.split(': ',1) for line in account_lines if ': ' in line)
    web.CORE='http://127.0.0.1:19090'
    c=web.login({'username':account_fields['REQUESTER'],'role':'REQUESTER'},account_fields['Password'])
    status,url,body=web.req(c,web.CORE+'/requests/mine')
    assert status==200 and '/login' not in url and len(body)>500
    for port in (19111,19112,19113,19114):
        with socket.socket() as sock:assert sock.connect_ex(('127.0.0.1',port))!=0
    assert protected()==before
    (R/'stop-evidence.json').write_text(json.dumps({'onlyExperimentalAppsStopped':True,'allFourPortsClosed':True,'protected19090UpWhileExperimentStopped':True,'protected19090RealLoginAndWorkspaceWhileStopped':True,'protectedContainersUnchanged':True,'databaseAndUploadsRetained':True,'experimentRestarted':False},indent=2))
    for service in ('a','b','lb','core'):stage9.start(service)
    assert protected()==before
    e=json.loads((R/'stop-evidence.json').read_text());e['experimentRestarted']=True
    (R/'stop-evidence.json').write_text(json.dumps(e,indent=2));print(json.dumps(e))

def final_checks():
    import xml.etree.ElementTree as ET
    import tarfile
    stage3.safety();stage9.load();checks={}
    for service,row in stage9.state['services'].items():
        assert digest(Path(row['artifact']))==row['sha256']
        line=stage3.command(['powershell','-NoProfile','-Command',f"$p=Get-CimInstance Win32_Process -Filter 'ProcessId={int(row['pid'])}';if($p){{$p.CommandLine|ConvertTo-Json -Compress}}"])
        assert str(row['artifact']).lower() in json.loads(line).lower()
        with urllib.request.urlopen(f'http://127.0.0.1:{stage9.PORTS[service]}/actuator/health',timeout=8) as response:
            assert json.load(response)['status']=='UP'
            if service in ('a','b'):assert response.headers['X-Community-Instance']==service+':'+str(row['pid'])
        checks[service+'ActualProcessArtifactAndHealth']=True
    saved=json.loads((stage9.R/'test-accounts-private.json').read_text())
    web.CORE='http://127.0.0.1:19111'
    for key,role,path in [('author','REQUESTER','/requests/mine'),('tech','TECHNICIAN','/workorders/mine'),('admin','ADMINISTRATOR','/admin/requests')]:
        c=web.login(dict(saved['accounts'][key],role=role),saved['password'])
        for route in ('/community','/notifications',path):
            status,url,body=web.req(c,web.CORE+route);assert status==200 and '/login' not in url and len(body)>500
            checks[role+route]=True
    w=json.loads(stage3.STATE.read_text(encoding='utf-8'))
    private=Path(w['resources']['databaseBackupRoot'])/'test-accounts-private.txt'
    fields={}
    for line in private.read_text(encoding='utf-8').splitlines():
        if ': ' in line:
            k,v=line.split(': ',1);fields[k]=v
    web.CORE='http://127.0.0.1:19090'
    for role,path in [('REQUESTER','/requests/mine'),('TECHNICIAN','/workorders/mine'),('ADMINISTRATOR','/admin/requests')]:
        c=web.login({'username':fields[role],'role':role},fields['Password'])
        status,url,body=web.req(c,web.CORE+path);assert status==200 and '/login' not in url and len(body)>500
        checks['protected19090'+role+'LoginAndWorkspace']=True
    assert protected()==json.loads((R/'protected-before-private.json').read_text())
    checks['protectedResourcesUnchangedAtEnd']=True
    recovery=json.loads((R/'restore-evidence.json').read_text())
    cold=Path(w['resources']['databaseBackupRoot'])/'original-stopped-pgdata.tar'
    cold_sha=digest(cold);assert cold_sha==w['checks']['databaseColdBackup']['archiveSha256']
    with tarfile.open(cold) as archive:
        members=archive.getmembers();assert any(m.name.endswith('PG_VERSION') for m in members)
    recovery['coldBackup']={'sha256':cold_sha,'bytes':cold.stat().st_size,'tarMemberCount':len(members),'readable':True}
    assert digest(Path(w['resources']['databaseBackupRoot'])/'recovered-current.dump')==recovery['originalDumpSha256']
    stop=json.loads((R/'stop-evidence.json').read_text());assert all(stop.values())
    testreports=[]
    for directory in ('build-core-v2','build-community-v2'):
        for path in (stage9.R/directory/'surefire-reports').glob('TEST-*.xml'):
            suite=ET.parse(path).getroot()
            row={k:suite.attrib[k] for k in ('name','tests','failures','errors','skipped')}
            assert row['failures']==row['errors']==row['skipped']=='0'
            testreports.append(row)
    assert sum(int(r['tests']) for r in testreports)==120
    phase_hashes={}
    for path in sorted((ROOT/'docs/migration').glob('phase*/*')):
        if path.parent.name!='phase11' and path.is_file() and path.suffix in ('.json','.md'):phase_hashes[str(path.relative_to(ROOT))]=digest(path)
    prior_restore=json.loads((ROOT/'docs/migration/phase7/EVIDENCE.json').read_text(encoding='utf-8'))
    archives={}
    for name,row in prior_restore['archives'].items():
        p=Path(row['path']);assert (ROOT/'.migration-runtime/stage7/archives') in p.parents
        assert p.stat().st_size==row['bytes'] and digest(p)==row['sha256']
        archives[name]={'sha256':row['sha256'],'bytes':row['bytes'],'matchesPriorEvidence':True}
    assert recovery['restoredJarSha256']==prior_restore['baselineJarSha256']
    commits=stage3.command(['git','log','--format=%H %s','baseline/ready-for-migration..HEAD']).decode().splitlines()
    with socket.socket() as sock:original8080='LISTENING_NOT_AUTHENTICATED' if sock.connect_ex(('127.0.0.1',8080))==0 else 'NOT_LISTENING'
    result={'date':'2026-10-29 Asia/Shanghai','startCommit':recovery['startCommit'],'branch':'feature/community-microservice','main':'6cb329f4fe9b1f7e2e708b9ee00d81c456740fc0','baseline':'ea102f85398e268ef76eb091ebc73bc52328cec1','restore':recovery,'stopRehearsal':stop,'currentChecks':checks,'currentServices':stage9.state['services'],'protectedContainerIdentities':{k:{'id':v['id'],'status':v['state']['Status'],'mounts':[{'type':m['Type'],'name':m.get('Name'),'destination':m['Destination']} for m in v['mounts']]} for k,v in protected().items()},'original8080':original8080,'javaReportsPreviouslyRunAndRechecked':testreports,'pythonTestsPreviouslyPassed':6,'priorReportsSha256':phase_hashes,'implementationCommits':commits,'architecture':'IMPLEMENTED_LOCAL_CORE_COMMUNITY','functional':'AUTOMATED_PASS_BROWSER_PENDING_USER','dataRecovery':'NEW_ORIGINAL_RESTORE_AND_PRIOR_INCREMENTAL_REHEARSAL_PASS','scalabilityPerformance':'INCOMPLETE_CANCELLED_BY_USER_NO_SCALING_CLAIM','aws':'NOT_RUN_UNCHANGED','autoScaling':'NOT_IMPLEMENTED','verticalScalingExperiment':'NOT_RUN','allRepositoryTestsThisStage':'NOT_RUN_NO_BUSINESS_CODE_CHANGE','historicalAttachmentRead':'N/A_ZERO_ORIGINAL_ROWS','browserAcceptance':'PENDING_USER','nextStageAllowed':False}
    result['priorMigrationAndIncrementalArchivesVerified']=archives
    out=ROOT/'docs/migration/phase11/EVIDENCE.json';out.write_text(json.dumps(result,ensure_ascii=False,indent=2)+'\n',encoding='utf-8',newline='\n')
    print(json.dumps({'finalChecks':checks,'newRestore':recovery['newRestoreUrl'],'stopRehearsal':stop,'original8080':original8080,'javaReports':120,'evidence':str(out)}))

if __name__=='__main__':
    import argparse
    p=argparse.ArgumentParser();p.add_argument('action',choices=['restore','stop-rehearsal','final']);a=p.parse_args()
    if a.action=='restore':restore()
    elif a.action=='stop-rehearsal':stop_experiment_rehearsal()
    else:final_checks()
