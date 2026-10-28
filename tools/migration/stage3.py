"""Local stage3 provision/start/verify. Never operates on original or baseline resources."""
import argparse, datetime, hashlib, http.cookiejar, json, os, secrets, socket, subprocess, time, urllib.request, urllib.parse
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
RUNTIME = ROOT / '.migration-runtime'
STATE = Path.home() / 'SmartFix-Migration-Workspace.json'
ROLES = {'core': 'sf_migration_core', 'community': 'sf_migration_community'}
PORTS = {'core': 19091, 'community': 19092}

def command(args, **kw):
    result = subprocess.run(args, capture_output=True, **kw)
    if result.returncode:
        # Raw diagnostics remain private; never echo commands or credentials.
        if RUNTIME.exists():
            (RUNTIME / 'last-error-private.log').write_bytes(result.stderr or result.stdout or b'failed')
        raise RuntimeError('Command failed; see private runtime diagnostics: ' + args[0])
    return result.stdout

def safety():
    assert ROOT == Path(r'C:\Users\zhour\smartfix-microservices')
    assert Path.cwd().resolve() == ROOT
    assert command(['git','branch','--show-current']).decode().strip() == 'feature/community-microservice'
    assert command(['git','rev-parse','baseline/ready-for-migration']).decode().strip() == 'ea102f85398e268ef76eb091ebc73bc52328cec1'
    assert command(['git','rev-parse','main']).decode().strip() == '6cb329f4fe9b1f7e2e708b9ee00d81c456740fc0'
    assert json.loads(STATE.read_text(encoding='utf-8'))['workRoot'] == str(ROOT)
    assert RUNTIME.resolve() == ROOT / '.migration-runtime', 'Runtime must not be a link'

def load():
    data = json.loads((RUNTIME / 'resources.json').read_text())
    assert data['container'].startswith('sf-migration-stage3-') and data['volume'].startswith('sf-migration-stage3-')
    assert data['dbPort'] == 15453
    return data

def inspect(name):
    return json.loads(command(['docker','inspect',name]))[0]

def check_container(data):
    info = inspect(data['container'])
    assert info['Config']['Labels'].get('smartfix.migration.stage') == '3'
    assert info['Id'] == data['containerId']
    mounts = info['Mounts']
    assert len(mounts) == 1 and mounts[0]['Type'] == 'volume' and mounts[0]['Name'] == data['volume']
    assert info['HostConfig']['PortBindings'] == {'5432/tcp':[{'HostIp':'127.0.0.1','HostPort':'15453'}]}

def sql(data, query, database='postgres', role='migration_admin'):
    return command(['docker','exec','-i',data['container'],'psql','-v','ON_ERROR_STOP=1','-U',role,'-d',database,'-At'],input=query.encode()).decode().strip()

def provision():
    assert not RUNTIME.exists(), 'Existing runtime: inspect; never overwrite'
    for port in (15453,19091,19092):
        with socket.socket() as sock: sock.bind(('127.0.0.1',port))
    state=json.loads(STATE.read_text(encoding='utf-8'))
    dump=Path(state['resources']['databaseBackupRoot'])/'recovered-current.dump'
    assert dump.is_file() and Path(state['backupRoot']) in dump.parents
    RUNTIME.mkdir()
    user=command(['whoami']).decode().strip()
    command(['icacls',str(RUNTIME),'/inheritance:r','/grant:r',user+':(OI)(CI)F','*S-1-5-18:(OI)(CI)F'])
    tag='sf-migration-stage3-'+datetime.datetime.now().strftime('%Y%m%d-%H%M%S')
    protected={}
    for name in ('smartfix-db',state['resources']['cloneDatabaseContainer']):
        m=inspect(name); protected[name]={'id':m['Id'],'state':m['State'],'mounts':m['Mounts']}
    (RUNTIME/'protected-before.json').write_text(json.dumps(protected),encoding='utf-8')
    private={k:secrets.token_hex(24) for k in ('admin','core','community')}
    (RUNTIME/'credentials-private.json').write_text(json.dumps(private),encoding='utf-8')
    envfile=RUNTIME/'postgres-private.env'
    envfile.write_text('POSTGRES_USER=migration_admin\nPOSTGRES_DB=postgres\nPOSTGRES_PASSWORD='+private['admin']+'\n')
    volume=tag+'-data';container=tag+'-db'
    command(['docker','volume','create','--label','smartfix.migration.stage=3',volume])
    command(['docker','run','-d','--name',container,'--label','smartfix.migration.stage=3','--env-file',str(envfile),'-p','127.0.0.1:15453:5432','--mount','type=volume,source='+volume+',target=/var/lib/postgresql/data','postgres:16-alpine'])
    data={'container':container,'containerId':inspect(container)['Id'],'volume':volume,'dbPort':15453,'services':{},'sourceDumpSha256':hashlib.sha256(dump.read_bytes()).hexdigest()}
    (RUNTIME/'resources.json').write_text(json.dumps(data,indent=2))
    for _ in range(45):
        r=subprocess.run(['docker','exec',container,'pg_isready','-U','migration_admin','-d','postgres'],capture_output=True)
        if r.returncode==0:break
        time.sleep(1)
    else:raise RuntimeError('New database not ready')
    check_container(data)
    for service,role in ROLES.items():
        db='smartfix_migration_'+service
        sql(data,"CREATE ROLE "+role+" LOGIN PASSWORD '"+private[service]+"'; CREATE DATABASE "+db+" OWNER "+role+";")
        sql(data,'REVOKE CONNECT ON DATABASE '+db+' FROM PUBLIC; GRANT CONNECT ON DATABASE '+db+' TO '+role+';')
        (RUNTIME/('uploads-'+role)).mkdir()
    with dump.open('rb') as stream:
        r=subprocess.run(['docker','exec','-i',container,'pg_restore','-U','migration_admin','-d','smartfix_migration_core','--no-owner','--no-acl','--role=sf_migration_core','--exit-on-error'],stdin=stream,capture_output=True)
    if r.returncode:
        (RUNTIME/'restore-private.log').write_bytes(r.stderr);raise RuntimeError('Isolated Core restore failed')
    print('New isolated database ready; Core restored from backup; Community empty pending own Flyway.')

def start(service):
    data=load();check_container(data)
    with socket.socket() as sock:sock.bind(('127.0.0.1',PORTS[service]))
    private=json.loads((RUNTIME/'credentials-private.json').read_text())
    role=ROLES[service];db='smartfix_migration_'+service
    jar=ROOT/('target/smartfix-0.0.1-SNAPSHOT.jar' if service=='core' else 'community-service/target/smartfix-community-0.0.1-SNAPSHOT.jar')
    assert jar.is_file()
    uploads=RUNTIME/('uploads-'+role);assert uploads.resolve()==uploads
    env={k:v for k,v in os.environ.items() if not k.startswith(('DB_','SMARTFIX_','SPRING_','POSTGRES_','JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS'))}
    env.update(DB_URL='jdbc:postgresql://127.0.0.1:15453/'+db,DB_USERNAME=role,DB_PASSWORD=private[service])
    args=['java','-jar',str(jar),'--smartfix.migration.isolated=true','--smartfix.migration.root='+str(ROOT),'--server.address=127.0.0.1','--server.port='+str(PORTS[service]),'--server.servlet.session.cookie.name=SMARTFIX_MIGRATION_'+role,'--smartfix.uploads.dir='+str(uploads),'--smartfix.notification.email.enabled=false','--smartfix.bootstrap-admin.enabled=false','--smartfix.notification.delivery.scheduling-enabled=false','--smartfix.notification.delivery.eager-enabled=false','--spring.jpa.hibernate.ddl-auto=validate']
    with (RUNTIME/(service+'-private.log')).open('wb') as out:
        app=subprocess.Popen(args,cwd=ROOT,env=env,stdout=out,stderr=out,creationflags=subprocess.CREATE_NO_WINDOW)
    data['services'][service]={'pid':app.pid,'jar':str(jar),'jarSha256':hashlib.sha256(jar.read_bytes()).hexdigest(),'url':'http://127.0.0.1:'+str(PORTS[service])}
    (RUNTIME/'resources.json').write_text(json.dumps(data,indent=2))
    for _ in range(75):
        if app.poll() is not None:raise RuntimeError(service+' exited; see private runtime log')
        try:
            with urllib.request.urlopen(data['services'][service]['url']+'/actuator/health',timeout=2) as r:
                assert json.loads(r.read())['status']=='UP'
            print(service+' ready at '+data['services'][service]['url']);return
        except Exception:time.sleep(1)
    raise RuntimeError(service+' readiness timeout')

def stop(service):
    data=load();item=data['services'][service]
    # Check PID command line before any termination to avoid stale PID reuse.
    ps="(Get-CimInstance Win32_Process -Filter 'ProcessId="+str(int(item['pid']))+"').CommandLine"
    current=command(['powershell','-NoProfile','-Command',ps]).decode(errors='replace')
    assert item['jar'].lower() in current.lower() and '-jar' in current
    command(['taskkill','/PID',str(item['pid']),'/T','/F'])
    print('Stopped only migration '+service)

def verify():
    data=load();check_container(data);results={}
    private=json.loads((RUNTIME/'credentials-private.json').read_text())
    for service,role in ROLES.items():
        own='smartfix_migration_'+service;other='smartfix_migration_'+('community' if service=='core' else 'core')
        env=os.environ.copy();env['PGPASSWORD']=private[service]
        for db,expected in ((own,True),(other,False)):
            r=subprocess.run(['docker','exec','-e','PGPASSWORD',data['container'],'psql','-h','127.0.0.1','-U',role,'-d',db,'-At','-c','SELECT current_user;'],env=env,capture_output=True)
            assert (r.returncode==0)==expected
            results[service+'_'+('own_db_allowed' if expected else 'cross_db_denied')]=True
        with urllib.request.urlopen(data['services'][service]['url']+'/actuator/health',timeout=5) as r:assert json.loads(r.read())['status']=='UP'
        results[service+'_health']=True
    public_tables=sql(data,"SELECT tablename FROM pg_tables WHERE schemaname='public' ORDER BY tablename;",'smartfix_migration_community').splitlines()
    assert set(public_tables)=={'community_questions','community_answers','community_reports','flyway_schema_history'}
    assert sql(data,"SELECT count(*) FROM flyway_schema_history WHERE success;",'smartfix_migration_community')=='3'
    results['community_own_flyway_and_only_owned_tables']=True
    constraints=sql(data,"SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE connamespace='public'::regnamespace;",'smartfix_migration_community')
    assert 'REFERENCES users' not in constraints and 'REFERENCES community_answers(id, question_id)' in constraints
    results['internal_fk_kept_external_users_fk_absent']=True
    sql(data,"""BEGIN;
    INSERT INTO community_questions(id,author_id,title,body,category) VALUES
      (900001,999999,'Verification question one','Verification body content','OTHER'),
      (900002,999999,'Verification question two','Verification body content','OTHER');
    INSERT INTO community_answers(id,question_id,author_id,body) VALUES(900001,900002,999999,'Verification answer content');
    DO $$ BEGIN
      BEGIN UPDATE community_questions SET accepted_answer_id=900001 WHERE id=900001;
        RAISE EXCEPTION 'Cross-question acceptance was allowed';
      EXCEPTION WHEN foreign_key_violation THEN NULL; END;
      BEGIN INSERT INTO community_reports(question_id,answer_id,reporter_id,reason) VALUES(900001,900001,999999,'OTHER');
        RAISE EXCEPTION 'Dual report targets were allowed';
      EXCEPTION WHEN check_violation THEN NULL; END;
    END $$;
    ROLLBACK;""",'smartfix_migration_community',ROLES['community'])
    results['postgres_rejects_wrong_acceptance_and_dual_report_target']=True
    try:urllib.request.urlopen(data['services']['community']['url']+'/internal/community/v1/questions',timeout=5);raise AssertionError('Skeleton exposed business API')
    except urllib.error.HTTPError as e:assert e.code==403
    results['community_business_api_denied_until_stage4']=True
    # Login with cloned synthetic data; never change original or baseline credentials.
    import re
    jar=http.cookiejar.CookieJar();opener=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(jar))
    url=data['services']['core']['url']
    page=opener.open(url+'/register').read().decode()
    from html.parser import HTMLParser
    class Inputs(HTMLParser):
        def __init__(self):super().__init__();self.fields={}
        def handle_starttag(self,t,a):
            a=dict(a)
            if t=='input' and 'name' in a:self.fields[a['name']]=a.get('value','')
    inp=Inputs();inp.feed(page);pw='Migration9!'+secrets.token_hex(8);username='stage3_'+secrets.token_hex(4)
    opener.open(url+'/register',data=urllib.parse.urlencode({'_csrf':inp.fields['_csrf'],'username':username,'displayName':'Stage3 Test','password':pw,'confirmPassword':pw}).encode()).read()
    inp=Inputs();inp.feed(opener.open(url+'/login').read().decode())
    r=opener.open(url+'/login',data=urllib.parse.urlencode({'_csrf':inp.fields['_csrf'],'username':username,'password':pw,'accountType':'REQUESTER'}).encode());assert '/login' not in r.geturl()
    assert any(c.name=='SMARTFIX_MIGRATION_sf_migration_core' for c in jar)
    for path in ('/community','/notifications','/requests/mine','/campus-map'):
        r=opener.open(url+path);assert r.status==200 and '/login' not in r.geturl()
    (RUNTIME/'test-account-private.txt').write_text('URL: '+url+'/login\nUsername: '+username+'\nPassword: '+pw+'\nRole: REQUESTER\n')
    results['core_real_registration_login_cookie_and_pages']=True
    before=json.loads((RUNTIME/'protected-before.json').read_text())
    for name,old in before.items():
        m=inspect(name);assert m['Id']==old['id'] and m['State']==old['state'] and m['Mounts']==old['mounts']
    results['protected_containers_unchanged']=True
    (RUNTIME/'verification.json').write_text(json.dumps(results,indent=2))
    print(json.dumps(results))

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('action',choices=['provision','start','stop','verify']);parser.add_argument('service',nargs='?',choices=ROLES);a=parser.parse_args();safety()
    if a.action=='provision':provision()
    else:
        import msvcrt
        with (RUNTIME/'operation.lock').open('a+b') as lock:
            lock.seek(0);lock.write(b'0');lock.flush();lock.seek(0)
            msvcrt.locking(lock.fileno(),msvcrt.LK_NBLCK,1)
            if a.action=='verify':verify()
            elif a.action=='start':start(a.service)
            else:stop(a.service)
