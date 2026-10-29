"""Performance sandbox lifecycle. Exact new identities; never resets an existing database.

Restore means a NEW generation of databases, never an in-place rollback. All dumps private.
"""
import argparse,datetime,hashlib,json,os,secrets,shutil,socket,subprocess,time,urllib.request
from pathlib import Path
import stage3,stage8
ROOT=stage3.ROOT; R=ROOT/'.migration-runtime/stage9'
PORTS={'core':19111,'a':19112,'b':19113,'c':19115,'lb':19114}
DBS={k:'smartfix_stage9_'+k for k in ('core','community')}
ROLES={k:'sf_stage9_'+k for k in DBS}
state={};credentials={}
command=stage3.command

def identity():
    stage3.safety();assert R.resolve()==ROOT/'.migration-runtime/stage9'
    d=json.loads(command(['docker','inspect',state['container']]))[0]
    assert state['container'].startswith('sf-migration-stage9-') and d['Id']==state['containerId']
    assert state['volume']==state['container'][:-3]+'-data'
    assert d['Config']['Labels'].get('smartfix.migration.stage')=='9'
    assert len(d['Mounts'])==1 and d['Mounts'][0]['Type']=='volume' and d['Mounts'][0]['Name']==state['volume']
    assert d['HostConfig']['PortBindings']=={'5432/tcp':[{'HostIp':'127.0.0.1','HostPort':'15483'}]}
    assert not any((R/x).is_symlink() for x in ('uploads-core','uploads-community','identity'))

def load():
    global state,credentials
    state=json.loads((R/'resources-private.json').read_text());credentials=json.loads((R/'credentials-private.json').read_text());identity()

def save(): (R/'resources-private.json').write_text(json.dumps(state,indent=2))

def sql(key,query):
    assert key in DBS;identity()
    return command(['docker','exec','-i',state['container'],'psql','-v','ON_ERROR_STOP=1','-U','stage9_admin','-d',DBS[key],'-At'],input=query.encode()).decode().strip()

def provision():
    global state,credentials
    stage3.safety();stage8.load()
    source_info=json.loads(command(['docker','inspect',stage8.resources['container']]))[0]
    assert source_info['State']['Running'],'Stage8 source database must already be available'
    if (R/'resources-private.json').exists():
        load()
        if not state['ready']:finish_provision()
        return
    assert json.loads(stage3.STATE.read_text(encoding='utf-8'))['stage8']['browserAcceptance']=='PASS_USER_CONFIRMED'
    for port in (15483,*PORTS.values()):
        with socket.socket() as s:s.bind(('127.0.0.1',port))
    R.mkdir(exist_ok=True)
    tag='sf-migration-stage9-'+datetime.datetime.now().strftime('%Y%m%d-%H%M%S')
    credentials={k:secrets.token_hex(24) for k in ('admin','core','community')}
    (R/'credentials-private.json').write_text(json.dumps(credentials))
    f=R/'postgres-private.env';f.write_text('POSTGRES_USER=stage9_admin\nPOSTGRES_DB=postgres\nPOSTGRES_PASSWORD='+credentials['admin']+'\n')
    command(['docker','volume','create','--label','smartfix.migration.stage=9',tag+'-data'])
    command(['docker','run','-d','--name',tag+'-db','--label','smartfix.migration.stage=9','--memory=1g','--cpus=2','--env-file',str(f),'-p','127.0.0.1:15483:5432','--mount','type=volume,source='+tag+'-data,target=/var/lib/postgresql/data','postgres:16-alpine','-c','shared_buffers=128MB','-c','max_connections=60','-c','track_io_timing=on','-c','shared_preload_libraries=pg_stat_statements'])
    state={'container':tag+'-db','containerId':json.loads(command(['docker','inspect',tag+'-db']))[0]['Id'],'volume':tag+'-data','services':{},'source':'stage8','ready':False};save()
    for _ in range(40):
        if subprocess.run(['docker','exec',state['container'],'pg_isready','-U','stage9_admin'],capture_output=True).returncode==0:break
        time.sleep(1)
    identity();finish_provision()

def finish_provision():
    stage8.load();identity()
    for key in DBS:
        exists=command(['docker','exec',state['container'],'psql','-U','stage9_admin','-d','postgres','-At','-c',"SELECT count(*) FROM pg_database WHERE datname='"+DBS[key]+"';"]).decode().strip()
        if exists=='0':command(['docker','exec','-i',state['container'],'psql','-v','ON_ERROR_STOP=1','-U','stage9_admin','-d','postgres'],input=(f"CREATE ROLE {ROLES[key]} LOGIN PASSWORD '{credentials[key]}'; CREATE DATABASE {DBS[key]} OWNER {ROLES[key]}; REVOKE CONNECT ON DATABASE {DBS[key]} FROM PUBLIC; GRANT CONNECT ON DATABASE {DBS[key]} TO {ROLES[key]};").encode())
        if state.get(key+'Restored'):continue
        assert sql(key,"SELECT count(*) FROM information_schema.tables WHERE table_schema='public';")=='0','Partial restore needs diagnosis; never overwrite'
        dump=R/(key+'-source-'+datetime.datetime.now().strftime('%Y%m%d-%H%M%S')+'-private.dump')
        with dump.open('xb') as f:
            p=subprocess.run(['docker','exec',stage8.resources['container'],'pg_dump','-U','stage8_admin','-d',stage8.DBS[key],'-Fc','--no-owner','--no-acl'],stdout=f,stderr=subprocess.PIPE)
        if p.returncode:(R/'snapshot-error-private.log').write_bytes(p.stderr);raise RuntimeError('Source snapshot failed; inspect private diagnostic')
        with dump.open('rb') as f:command(['docker','exec','-i',state['container'],'pg_restore','-U','stage9_admin','-d',DBS[key],'--no-owner','--no-acl','--role='+ROLES[key],'--exit-on-error'],stdin=f)
        state[key+'SourceSha256']=hashlib.sha256(dump.read_bytes()).hexdigest()
        sql(key,'CREATE EXTENSION IF NOT EXISTS pg_stat_statements;')
        shutil.copytree(stage8.R/('uploads-'+key),R/('uploads-'+key));state[key+'Restored']=True;save()
    # A cloned Core must retain its write prohibition on obsolete community tables.
    for t in stage8.pg.TABLES:
        sql('core',f'ALTER TABLE {t} OWNER TO stage9_admin; REVOKE INSERT,UPDATE,DELETE ON {t} FROM {ROLES["core"]}; GRANT SELECT ON {t} TO {ROLES["core"]};')
    for key in DBS:command(['java',str(ROOT/'tools/migration/GenerateServiceKeys.java'),str(R/'identity'),key])
    shutil.copy2(stage8.R/'test-accounts-private.json',R/'test-accounts-private.json')
    state['ready']=True;save()

def start(service):
    identity();assert state['ready'] and not state.get('restoreInProgress') and service in PORTS and service not in state['services']
    with socket.socket() as s:s.bind(('127.0.0.1',PORTS[service]))
    env={k:v for k,v in os.environ.items() if not k.upper().startswith(('DB_','SMARTFIX_','SPRING_','POSTGRES_','JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS'))}
    if service=='lb':
        artifact=ROOT/'tools/migration/stage9_lb.py';args=['python',str(artifact)]
    else:
        key='core' if service=='core' else 'community'
        version=state.get('communityArtifactVersion','stage9');assert version in ('stage9','stage10-v2')
        core_version=state.get('coreArtifactVersion','stage9');assert core_version in ('stage9','stage10-cookie-v2')
        artifact=R/((('build-core-v2/' if core_version=='stage10-cookie-v2' else 'build-core/')+'smartfix-0.0.1-SNAPSHOT.jar') if key=='core' else ('build-community-v2/' if version=='stage10-v2' else 'build-community/')+'smartfix-community-0.0.1-SNAPSHOT.jar')
        env['DB_PASSWORD']=credentials[key]
        args=['java','-Xms128m','-Xmx384m','-XX:ActiveProcessorCount=2','-XX:+UseG1GC','-Xlog:gc:file='+str(R/(service+'-gc-private.log')),'-jar',str(artifact),
          '--server.address=127.0.0.1','--server.port='+str(PORTS[service]),'--server.tomcat.threads.max=40','--server.tomcat.threads.min-spare=4','--server.tomcat.mbeanregistry.enabled=true',
          '--spring.datasource.url=jdbc:postgresql://127.0.0.1:15483/'+DBS[key],'--spring.datasource.username='+ROLES[key],'--spring.datasource.password='+ '${DB_PASSWORD}',
          '--spring.datasource.hikari.maximum-pool-size=5','--spring.datasource.hikari.minimum-idle=1','--spring.datasource.hikari.connection-timeout=2000',
          '--smartfix.migration.isolated=true','--smartfix.migration.scenario=stage9','--smartfix.migration.root='+str(ROOT),'--smartfix.uploads.dir='+str(R/('uploads-'+key)),
          '--server.servlet.session.cookie.name=SMARTFIX_STAGE9_'+key.upper(),'--smartfix.notification.email.enabled=false','--smartfix.bootstrap-admin.enabled=false',
          '--smartfix.notification.delivery.scheduling-enabled=false','--smartfix.notification.delivery.eager-enabled=false','--spring.jpa.hibernate.ddl-auto=validate','--spring.jpa.open-in-view=false',
          '--spring.profiles.active='+('community-remote' if key=='core' else 'community-full'),'--smartfix.community.identity.enabled=true','--smartfix.community.identity.public-key='+str(R/'identity/core-public.der'),
          '--smartfix.community.events.enabled=true','--smartfix.community.events.public-key='+str(R/'identity/community-public.der'),
          '--management.endpoints.web.exposure.include=health,info,metrics','--management.metrics.distribution.percentiles-histogram.http.server.requests=true']
        if key=='core':args+=['--smartfix.community.base-url=http://127.0.0.1:19114','--smartfix.community.identity.private-key='+str(R/'identity/core-private.pk8')]
        else:args+=['--smartfix.community.events.core-base-url=http://127.0.0.1:19111','--smartfix.community.events.private-key='+str(R/'identity/community-private.pk8'),'--smartfix.community.instance-id='+service]
    assert artifact.is_file()
    with (R/(service+'-private.log')).open('wb') as f:p=subprocess.Popen(args,cwd=ROOT,env=env,stdout=f,stderr=f,creationflags=subprocess.CREATE_NO_WINDOW)
    state['services'][service]={'pid':p.pid,'artifact':str(artifact),'sha256':hashlib.sha256(artifact.read_bytes()).hexdigest()};save()
    for _ in range(80):
        assert p.poll() is None,'Sandbox stopped: inspect private log'
        try:
            with urllib.request.urlopen(f'http://127.0.0.1:{PORTS[service]}/actuator/health',timeout=2) as r:
                if json.load(r)['status']=='UP':return
        except Exception:pass
        time.sleep(1)
    raise RuntimeError('Sandbox start timeout')

def stop(service):
    identity();row=state['services'][service];pid=int(row['pid']);artifact=Path(row['artifact'])
    assert R in artifact.parents or artifact==ROOT/'tools/migration/stage9_lb.py'
    line=command(['powershell','-NoProfile','-Command',f"$p=Get-CimInstance Win32_Process -Filter 'ProcessId={pid}';if($p){{$p.CommandLine|ConvertTo-Json -Compress}}"]).decode().strip()
    if line:assert str(artifact).lower() in json.loads(line).lower();command(['taskkill','/PID',str(pid),'/T','/F'])
    del state['services'][service];save()

def snapshot():
    identity();assert not state['services'],'Stop stage9 apps before creating the fixed snapshot'
    target=R/'fixed-snapshot';target.mkdir(exist_ok=False)
    manifest={}
    for key in DBS:
        dump=target/(key+'-private.dump')
        with dump.open('xb') as f:p=subprocess.run(['docker','exec',state['container'],'pg_dump','-U','stage9_admin','-d',DBS[key],'-Fc','--no-owner','--no-acl'],stdout=f,stderr=subprocess.PIPE)
        assert p.returncode==0;manifest[key]=hashlib.sha256(dump.read_bytes()).hexdigest()
    shutil.copytree(R/'uploads-core',target/'uploads-core')
    (target/'manifest.json').write_text(json.dumps(manifest));print(json.dumps({'fixedSnapshotSha256':manifest}))

def restore_snapshot():
    identity();assert not state['services'],'Stop ONLY stage9 apps before restore'
    target=R/'fixed-snapshot';manifest=json.loads((target/'manifest.json').read_text())
    state['restoreInProgress']=True;save()
    suffix=datetime.datetime.now().strftime('%Y%m%d%H%M%S')
    for key in DBS:
        dump=target/(key+'-private.dump');assert hashlib.sha256(dump.read_bytes()).hexdigest()==manifest[key]
        fresh=DBS[key]+'_new_'+suffix;archive=DBS[key]+'_archive_'+suffix
        def admin(q):return command(['docker','exec','-i',state['container'],'psql','-v','ON_ERROR_STOP=1','-U','stage9_admin','-d','postgres'],input=q.encode())
        admin(f'CREATE DATABASE {fresh} OWNER {ROLES[key]}; REVOKE CONNECT ON DATABASE {fresh} FROM PUBLIC; GRANT CONNECT ON DATABASE {fresh} TO {ROLES[key]};')
        # Extensions require the sandbox admin, never grant SUPERUSER to an app.
        with dump.open('rb') as f:command(['docker','exec','-i',state['container'],'pg_restore','-U','stage9_admin','-d',fresh,'--no-owner','--no-acl','--exit-on-error'],stdin=f)
        ownership="DO $$ DECLARE r record; BEGIN FOR r IN SELECT c.relname,c.relkind FROM pg_class c JOIN pg_namespace n ON n.oid=c.relnamespace WHERE n.nspname='public' AND c.relkind IN ('r','p','S') AND NOT EXISTS (SELECT 1 FROM pg_depend d WHERE d.objid=c.oid AND d.deptype='e') ORDER BY (c.relkind='S') LOOP EXECUTE format('ALTER %s public.%I OWNER TO "+ROLES[key]+"',CASE WHEN r.relkind='S' THEN 'SEQUENCE' ELSE 'TABLE' END,r.relname); END LOOP; END $$;"
        command(['docker','exec','-i',state['container'],'psql','-v','ON_ERROR_STOP=1','-U','stage9_admin','-d',fresh],input=ownership.encode())
        # Refuse active sessions rather than terminating unknown connections.
        assert sql(key,"SELECT count(*) FROM pg_stat_activity WHERE datname=current_database() AND pid<>pg_backend_pid();")=='0'
        admin(f'ALTER DATABASE {DBS[key]} RENAME TO {archive}; ALTER DATABASE {fresh} RENAME TO {DBS[key]};')
    for t in stage8.pg.TABLES:sql('core',f'ALTER TABLE {t} OWNER TO stage9_admin; REVOKE INSERT,UPDATE,DELETE ON {t} FROM {ROLES["core"]}; GRANT SELECT ON {t} TO {ROLES["core"]};')
    # Preserve current uploads, then copy the immutable snapshot into a fresh directory.
    (R/'uploads-core').rename(R/('uploads-core-archive-'+suffix))
    shutil.copytree(target/'uploads-core',R/'uploads-core')
    state.setdefault('restores',[]).append({'at':suffix,'snapshot':manifest});state['restoreInProgress']=False;save()
    print(json.dumps({'restoredIntoNewDatabases':True,'priorDatabasesPreserved':True,'snapshot':manifest}))

if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('action',choices=['provision','start','stop','snapshot','restore']);p.add_argument('service',nargs='?',choices=PORTS);a=p.parse_args()
    stage3.safety()
    if a.action=='provision':provision()
    else:
        load()
        if a.action=='start':start(a.service)
        elif a.action=='stop':stop(a.service)
        elif a.action=='snapshot':snapshot()
        else:restore_snapshot()
