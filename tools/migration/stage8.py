"""Stage8 lifecycle. New resources only; stage7's restored monolith is a read-only source."""
import datetime,hashlib,importlib.util,json,os,secrets,shutil,socket,subprocess,time,urllib.request,zipfile
from pathlib import Path
import stage3,stage7 as pg
ROOT=Path(__file__).resolve().parents[2];R=ROOT/'.migration-runtime/stage8'
DBS={'core':'smartfix_stage8_core','community':'smartfix_stage8_community'}
ROLES={k:'sf_stage8_'+k for k in DBS};PORTS={'core':19101,'a':19102,'b':19103,'lb':19104}
resources={};private={}
def command(args,**kw):
    p=subprocess.run(args,capture_output=True,**kw)
    if p.returncode:(R/'last-error-private.log').write_bytes(p.stderr or p.stdout);raise RuntimeError('Stage8 command failed; inspect private log')
    return p.stdout
def save():(R/'resources-private.json').write_text(json.dumps(resources,indent=2))
def identity():
    d=json.loads(command(['docker','inspect',resources['container']]))[0]
    assert resources['container'].startswith('sf-migration-stage8-') and d['Id']==resources['containerId']
    assert resources['volume']==resources['container'][:-3]+'-data'
    assert d['Config']['Labels'].get('smartfix.migration.stage')=='8'
    assert len(d['Mounts'])==1 and d['Mounts'][0]['Name']==resources['volume'] and d['Mounts'][0]['Type']=='volume'
    assert d['HostConfig']['PortBindings']=={'5432/tcp':[{'HostIp':'127.0.0.1','HostPort':'15473'}]}
def sql(key,q,role='stage8_admin'):
    assert key in DBS and role in ('stage8_admin',*ROLES.values());identity()
    return command(['docker','exec','-i',resources['container'],'psql','-v','ON_ERROR_STOP=1','-U',role,'-d',DBS[key],'-At'],input=q.encode()).decode().strip()
def bind_helpers():
    # Reuse the field-preserving importer with an explicitly replaced SQL transport.
    # Its source files and the real stage7 resources are never modified.
    pg.R=R;pg.DBS=DBS;pg.ROLES=ROLES;pg.sql=sql;pg.identity=identity;pg.resources=resources
def source():
    spec=importlib.util.spec_from_file_location('stage8_read_source',ROOT/'tools/migration/stage7.py');s=importlib.util.module_from_spec(spec);spec.loader.exec_module(s)
    s.resources=json.loads((ROOT/'.migration-runtime/stage7/resources-private.json').read_text());s.identity();return s
def restore(key,path):
    assert sql(key,"SELECT count(*) FROM information_schema.tables WHERE table_schema='public';")=='0'
    with path.open('rb') as f:command(['docker','exec','-i',resources['container'],'pg_restore','-U','stage8_admin','-d',DBS[key],'--no-owner','--no-acl','--role='+ROLES[key],'--exit-on-error'],stdin=f)
def provision():
    global resources,private
    if (R/'resources-private.json').exists():
        load()
        if not resources.get('provisioned'):finish_provision()
        return
    for port in (15473,*PORTS.values()):
        with socket.socket() as sock:sock.bind(('127.0.0.1',port))
    s=source();assert s.sql('restore_delta','SELECT enabled FROM stage7_write_control;')=='t'
    tag='sf-migration-stage8-'+datetime.datetime.now().strftime('%Y%m%d-%H%M%S');volume=tag+'-data';container=tag+'-db'
    private={k:secrets.token_hex(24) for k in ('admin','core','community')}
    (R/'credentials-private.json').write_text(json.dumps(private));(R/'postgres-private.env').write_text('POSTGRES_USER=stage8_admin\nPOSTGRES_DB=postgres\nPOSTGRES_PASSWORD='+private['admin']+'\n')
    command(['docker','volume','create','--label','smartfix.migration.stage=8',volume]);command(['docker','run','-d','--name',container,'--label','smartfix.migration.stage=8','--env-file',str(R/'postgres-private.env'),'-p','127.0.0.1:15473:5432','--mount','type=volume,source='+volume+',target=/var/lib/postgresql/data','postgres:16-alpine'])
    resources={'container':container,'containerId':json.loads(command(['docker','inspect',container]))[0]['Id'],'volume':volume,'services':{}};save()
    for _ in range(40):
        if subprocess.run(['docker','exec',container,'pg_isready','-U','stage8_admin'],capture_output=True).returncode==0:break
        time.sleep(1)
    identity()
    for key in DBS:
        command(['docker','exec','-i',container,'psql','-v','ON_ERROR_STOP=1','-U','stage8_admin','-d','postgres'],input=(f"CREATE ROLE {ROLES[key]} LOGIN PASSWORD '{private[key]}'; CREATE DATABASE {DBS[key]} OWNER {ROLES[key]}; REVOKE CONNECT ON DATABASE {DBS[key]} FROM PUBLIC; GRANT CONNECT ON DATABASE {DBS[key]} TO {ROLES[key]};").encode())
    for key,source_key in [('core','restore_delta'),('community','community')]:
        path=R/(key+'-source-private.dump')
        with path.open('xb') as f:
            p=subprocess.run(['docker','exec',s.resources['container'],'pg_dump','-U','stage7_admin','-d',s.DBS[source_key],'-Fc','--no-owner','--no-acl'],stdout=f,stderr=subprocess.PIPE)
        assert p.returncode==0
        resources[key+'SourceSha256']=hashlib.sha256(path.read_bytes()).hexdigest();save();restore(key,path)
    finish_provision()

def finish_provision():
    bind_helpers();s=source();sql('community','UPDATE stage7_write_control SET enabled=true;')
    latest=s.snapshot('restore_delta');pg.import_community('community',latest,include_audit=False)
    # Preserve authoritative legacy audit created after stage7's rollback, without ID collisions.
    for row in latest['community_audit']:
        if row.get('source')=='COMMUNITY':continue
        old=pg.rows('community','community_audit','WHERE id='+str(row['id']))
        clean={k:row[k] for k in pg.columns('community','community_audit')}
        if old and old[0]!=clean:clean['id']=int(sql('community','SELECT COALESCE(max(id),0)+1 FROM community_audit;'))
        pg.upsert('community','community_audit',[clean])
    pg.sequences('community',['community_audit']);sql('community','UPDATE stage7_write_control SET enabled=true;')
    for t in pg.TABLES:sql('core',f'ALTER TABLE {t} OWNER TO stage8_admin; REVOKE INSERT,UPDATE,DELETE ON {t} FROM {ROLES["core"]}; GRANT SELECT ON {t} TO {ROLES["core"]};')
    remove_legacy_write_barriers()
    for key in DBS:
        target=R/('uploads-'+key)
        if not target.exists():pg.copy_tree(ROOT/'.migration-runtime/stage7/uploads-restore_delta',target)
        else:assert not any(target.iterdir()),'Unexpected existing attachment content'
    for prefix in ('core','community'):command(['java',str(ROOT/'tools/migration/GenerateServiceKeys.java'),str(R/'identity'),prefix])
    shutil.copy2(ROOT/'.migration-runtime/stage7/test-accounts-private.json',R/'source-accounts-private.json')
    resources['provisioned']=True;resources['sourceCommunityDigest']=pg.digest(latest);save()
def remove_legacy_write_barriers():
    # These were cutover controls for stage7, not production concurrency primitives.
    # Remove them ONLY from the new stage8 clones so they cannot serialize every writer
    # and hide a bug in the service's actor/question/report locks.
    for key in DBS:
        for table in (*pg.TABLES,*(['community_audit'] if key=='community' else [])):
            sql(key,f'DROP TRIGGER IF EXISTS stage7_writer_{table} ON {table};')
def load():
    global resources,private
    resources=json.loads((R/'resources-private.json').read_text());private=json.loads((R/'credentials-private.json').read_text());identity();bind_helpers()
def start(service,variant=False):
    assert service in PORTS;identity();assert resources.get('provisioned'),'Provisioning not complete';assert service not in resources['services']
    with socket.socket() as sock:sock.bind(('127.0.0.1',PORTS[service]))
    env={k:v for k,v in os.environ.items() if not k.upper().startswith(('DB_','SMARTFIX_','SPRING_','POSTGRES_','JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS'))}
    if service=='lb':args=['python',str(ROOT/'tools/migration/stage8_lb.py')];artifact=args[1]
    else:
        key='core' if service=='core' else 'community';artifact=str(R/('build-core/smartfix-0.0.1-SNAPSHOT.jar' if key=='core' else 'community-r2.jar' if variant else 'build-community/smartfix-community-0.0.1-SNAPSHOT.jar'));assert Path(artifact).is_file()
        env.update(DB_URL='jdbc:postgresql://127.0.0.1:15473/'+DBS[key],DB_USERNAME=ROLES[key],DB_PASSWORD=private[key])
        args=['java','-Xmx384m','-jar',artifact,'--server.address=127.0.0.1','--server.port='+str(PORTS[service]),'--spring.datasource.url='+env['DB_URL'],'--spring.datasource.username='+ROLES[key],'--spring.datasource.hikari.maximum-pool-size=5','--spring.datasource.hikari.minimum-idle=1','--server.servlet.session.cookie.name=SMARTFIX_STAGE8_'+key.upper(),'--smartfix.migration.isolated=true','--smartfix.migration.scenario=stage8','--smartfix.migration.root='+str(ROOT),'--smartfix.uploads.dir='+str(R/('uploads-'+key)),'--smartfix.notification.email.enabled=false','--smartfix.bootstrap-admin.enabled=false','--smartfix.notification.delivery.scheduling-enabled=false','--smartfix.notification.delivery.eager-enabled=false','--spring.jpa.hibernate.ddl-auto=validate','--spring.jpa.open-in-view=false','--spring.profiles.active='+('community-remote' if key=='core' else 'community-full'),'--smartfix.community.identity.enabled=true','--smartfix.community.identity.public-key='+str(R/'identity/core-public.der'),'--smartfix.community.events.enabled=true','--smartfix.community.events.public-key='+str(R/'identity/community-public.der')]
        if key=='core':args+=['--smartfix.community.base-url=http://127.0.0.1:19104','--smartfix.community.identity.private-key='+str(R/'identity/core-private.pk8')]
        else:args+=['--smartfix.community.events.core-base-url=http://127.0.0.1:19101','--smartfix.community.events.private-key='+str(R/'identity/community-private.pk8'),'--smartfix.community.instance-id='+service]
    with (R/(service+'-private.log')).open('wb') as out:p=subprocess.Popen(args,cwd=ROOT,env=env,stdout=out,stderr=out,creationflags=subprocess.CREATE_NO_WINDOW)
    resources['services'][service]={'pid':p.pid,'artifact':artifact,'sha256':hashlib.sha256(Path(artifact).read_bytes()).hexdigest()};save()
    for _ in range(70):
        if p.poll() is not None:raise RuntimeError('Stage8 process stopped; inspect private log')
        try:
            with urllib.request.urlopen(f'http://127.0.0.1:{PORTS[service]}/actuator/health',timeout=2) as r:
                if json.load(r)['status']=='UP':return
        except Exception:pass
        time.sleep(1)
    raise RuntimeError('Stage8 startup timed out')
def stop(service):
    row=resources['services'][service];pid=int(row['pid']);artifact=Path(row['artifact']);assert R in artifact.parents or artifact==ROOT/'tools/migration/stage8_lb.py'
    result=command(['powershell','-NoProfile','-Command',f"$p=Get-CimInstance Win32_Process -Filter 'ProcessId={pid}'; if($p){{$p.CommandLine|ConvertTo-Json -Compress}}"]).decode().strip()
    if result:assert str(artifact).lower() in json.loads(result).lower();command(['taskkill','/PID',str(pid),'/T','/F'])
    del resources['services'][service];save();time.sleep(1)
def variant():
    path=R/'community-r2.jar'
    if path.exists():
        with zipfile.ZipFile(path) as existing:assert existing.read('BOOT-INF/classes/community-release.txt')==b'stage8-compatible-r2'
        return
    with zipfile.ZipFile(R/'build-community/smartfix-community-0.0.1-SNAPSHOT.jar') as src,zipfile.ZipFile(path,'w') as dst:
        for entry in src.infolist():dst.writestr(entry,src.read(entry.filename))
        dst.writestr('BOOT-INF/classes/community-release.txt','stage8-compatible-r2')
if __name__=='__main__':
    import argparse
    p=argparse.ArgumentParser();p.add_argument('action',choices=['provision','start','stop','variant']);p.add_argument('service',nargs='?',choices=PORTS);p.add_argument('--variant',action='store_true');args=p.parse_args()
    stage3.safety();w=json.loads(stage3.STATE.read_text(encoding='utf-8'));assert w['stage7']['browserAcceptance']=='PASS_USER_CONFIRMED'
    assert R.resolve()==ROOT/'.migration-runtime/stage8';R.mkdir(exist_ok=True)
    if args.action=='provision':provision()
    else:
        load()
        if args.action=='start':start(args.service,args.variant)
        elif args.action=='stop':stop(args.service)
        else:variant()
