"""Disposable historical-data migration and rollback rehearsal. Never uses protected DBs.

Run from the fixed migration workspace with `python tools/migration/stage7.py run`.
Checkpoints and immutable archives stay in the private ignored runtime. Re-running resumes
completed checkpoints; it never resets a database or replaces an existing archive.
"""
import argparse, concurrent.futures, datetime, hashlib, http.cookiejar, json, os, re
import secrets, shutil, socket, subprocess, time, urllib.request, urllib.parse, urllib.error
from pathlib import Path
from html.parser import HTMLParser
import stage3

ROOT=Path(__file__).resolve().parents[2]; R=ROOT/'.migration-runtime/stage7'
DBS={k:'smartfix_stage7_'+k for k in ('source','core','community','restore_delta')}
ROLES={k:'sf_stage7_'+k for k in DBS}
PORTS={'source':19093,'community':19094,'core':19095,'restore_delta':19096}
TABLES=('community_questions','community_answers','community_reports')
state={}; resources={}; private={}

def save():
    (R/'rehearsal-private.json').write_text(json.dumps(state,ensure_ascii=False,indent=2),encoding='utf-8')

def command(args,**kwargs):
    p=subprocess.run(args,capture_output=True,**kwargs)
    if p.returncode:
        (R/'last-error-private.log').write_bytes(p.stderr or p.stdout)
        raise RuntimeError('Stage7 command failed; inspect private diagnostics: '+args[0])
    return p.stdout

def identity():
    info=json.loads(command(['docker','inspect',resources['container']]))[0]
    assert re.fullmatch(r'sf-migration-stage7-\d{8}-\d{6}-db',resources['container'])
    assert info['Id']==resources['containerId']
    assert info['Config']['Labels'].get('smartfix.migration.stage')=='7'
    assert len(info['Mounts'])==1 and info['Mounts'][0]['Type']=='volume' and info['Mounts'][0]['Name']==resources['volume']
    assert resources['volume']==resources['container'][:-3]+'-data'
    assert info['HostConfig']['PortBindings']=={'5432/tcp':[{'HostIp':'127.0.0.1','HostPort':'15463'}]}

def sql(key,query,role='stage7_admin'):
    assert key in DBS and role in {'stage7_admin',*ROLES.values()}
    identity()
    return command(['docker','exec','-i',resources['container'],'psql','-v','ON_ERROR_STOP=1','-U',role,'-d',DBS[key],'-At'],input=query.encode('utf-8')).decode('utf-8').strip()

def literal(value):return "'"+str(value).replace("'","''")+"'"

def rows(key,table,where=''):
    assert table in {*TABLES,'audit_entries','community_audit','notifications','delivery_tasks','users','request_attachments'}
    return json.loads(sql(key,"SELECT COALESCE(json_agg(row_to_json(t) ORDER BY t.id),'[]') FROM (SELECT * FROM "+table+' '+where+') t;'))

def digest(value):return hashlib.sha256(json.dumps(value,sort_keys=True,ensure_ascii=False,separators=(',',':')).encode()).hexdigest()

def snapshot(key):
    result={t:rows(key,t) for t in TABLES}
    result['community_audit']=rows(key,'community_audit') if key=='community' else rows(key,'audit_entries',"WHERE target_type IN ('QUESTION','ANSWER','REPORT')")
    return result

def archive_json(name,value):
    path=R/'archives'/name;path.parent.mkdir(exist_ok=True)
    content=json.dumps(value,ensure_ascii=False,indent=2).encode('utf-8')
    if path.exists():assert path.read_bytes()==content,'Refuse to replace a different archive'
    else:
        with path.open('xb') as f:f.write(content)
    return {'path':str(path),'sha256':hashlib.sha256(content).hexdigest(),'bytes':len(content)}

def dump(key,name):
    identity();path=R/'archives'/name;path.parent.mkdir(exist_ok=True)
    assert not path.exists(),'Refuse to replace a database archive'
    with path.open('xb') as f:
        p=subprocess.run(['docker','exec',resources['container'],'pg_dump','-U','stage7_admin','-d',DBS[key],'-Fc','--no-owner','--no-acl'],stdout=f,stderr=subprocess.PIPE)
    if p.returncode:(R/'dump-error-private.log').write_bytes(p.stderr);raise RuntimeError('Stage7 archive failed')
    return {'path':str(path),'sha256':hashlib.sha256(path.read_bytes()).hexdigest(),'bytes':path.stat().st_size}

def restore(key,archive):
    assert key in DBS and key!='community'
    path=Path(archive['path']);assert hashlib.sha256(path.read_bytes()).hexdigest()==archive['sha256']
    assert sql(key,"SELECT count(*) FROM information_schema.tables WHERE table_schema='public';")=='0','Restore target must be empty'
    identity()
    with path.open('rb') as f:
        command(['docker','exec','-i',resources['container'],'pg_restore','-U','stage7_admin','-d',DBS[key],'--no-owner','--no-acl','--role='+ROLES[key],'--exit-on-error'],stdin=f)

def columns(key,table):
    return sql(key,"SELECT column_name FROM information_schema.columns WHERE table_schema='public' AND table_name="+literal(table)+' ORDER BY ordinal_position;').splitlines()

def upsert(key,table,data):
    assert table in {*TABLES,'community_audit','audit_entries','notifications','delivery_tasks'}
    if not data:return
    cols=[c for c in columns(key,table) if c in data[0]]
    assert all(re.fullmatch('[a-z_]+',c) for c in cols)
    text=literal(json.dumps(data,ensure_ascii=False));names=','.join(cols)
    assignments=','.join(c+'=EXCLUDED.'+c for c in cols if c!='id')
    sql(key,f'INSERT INTO {table}({names}) SELECT {names} FROM json_populate_recordset(NULL::{table},{text}::json) ON CONFLICT(id) DO UPDATE SET {assignments};')

def sequences(key,tables):
    for table in tables:
        assert table in {*TABLES,'community_audit','audit_entries','notifications','delivery_tasks','users'}
        sql(key,f"SELECT setval(pg_get_serial_sequence('{table}','id'),COALESCE(max(id),1),max(id) IS NOT NULL) FROM {table};")

def import_community(key,data,include_audit=True):
    # Break the accepted-answer cycle only inside the paused/import target.
    question=[dict(q,accepted_answer_id=None) for q in data['community_questions']]
    upsert(key,'community_questions',question);upsert(key,'community_answers',data['community_answers']);upsert(key,'community_reports',data['community_reports'])
    for q in data['community_questions']:
        sql(key,'UPDATE community_questions SET accepted_answer_id='+('NULL' if q['accepted_answer_id'] is None else str(int(q['accepted_answer_id'])))+' WHERE id='+str(int(q['id']))+';')
    if include_audit:
        assert key=='community';upsert(key,'community_audit',data['community_audit'])
    sequences(key,[*TABLES]+(['community_audit'] if include_audit else []))
    for table in TABLES:assert rows(key,table)==data[table],table+' field/ID/time/state mismatch'
    if include_audit:assert rows(key,'community_audit')==data['community_audit']
    assert sql(key,"SELECT count(*) FROM community_questions q JOIN community_answers a ON a.id=q.accepted_answer_id WHERE a.question_id<>q.id;")=='0'

def gate_install(key):
    # The flag is admin-owned. Every business DML checks it under the same transaction lock.
    sql(key,"""CREATE TABLE IF NOT EXISTS stage7_write_control(id INTEGER PRIMARY KEY CHECK(id=1),enabled BOOLEAN NOT NULL);
    INSERT INTO stage7_write_control VALUES(1,false) ON CONFLICT DO NOTHING;
    REVOKE ALL ON stage7_write_control FROM PUBLIC;
    CREATE OR REPLACE FUNCTION stage7_require_writer() RETURNS trigger LANGUAGE plpgsql SECURITY DEFINER AS $$
    BEGIN
      IF session_user <> 'stage7_admin' THEN
        PERFORM pg_advisory_xact_lock(770007);
        IF NOT (SELECT enabled FROM stage7_write_control WHERE id=1) THEN RAISE EXCEPTION 'Stage7 business writes paused' USING ERRCODE='55000'; END IF;
      END IF;
      IF TG_OP='DELETE' THEN RETURN OLD; END IF; RETURN NEW;
    END$$;
    """)
    sql(key,'ALTER TABLE stage7_write_control OWNER TO stage7_admin; ALTER FUNCTION stage7_require_writer() OWNER TO stage7_admin; REVOKE ALL ON stage7_write_control FROM '+ROLES[key]+'; UPDATE stage7_write_control SET enabled=false WHERE id=1;')
    for t in (*TABLES,*(['community_audit'] if key=='community' else [])):
        sql(key,f'ALTER TABLE {t} OWNER TO stage7_admin; GRANT SELECT,INSERT,UPDATE,DELETE ON {t} TO {ROLES[key]};')
        sequence=sql(key,f"SELECT pg_get_serial_sequence('{t}','id');")
        if sequence:sql(key,'GRANT USAGE,SELECT ON SEQUENCE '+sequence+' TO '+ROLES[key]+';')
        if sql(key,"SELECT count(*) FROM pg_trigger WHERE tgname='stage7_writer_"+t+"';")=='0':
            sql(key,f'CREATE TRIGGER stage7_writer_{t} BEFORE INSERT OR UPDATE OR DELETE ON {t} FOR EACH ROW EXECUTE FUNCTION stage7_require_writer();')

def gate(key,enabled):
    sql(key,'BEGIN; SELECT pg_advisory_xact_lock(770007); UPDATE stage7_write_control SET enabled='+str(enabled).lower()+' WHERE id=1; COMMIT;')

def authority(key):
    active=[]
    for name in ('source','community','restore_delta'):
        if name=='restore_delta' and not state.get('restoreCreated'):continue
        if sql(name,"SELECT to_regclass('public.stage7_write_control') IS NOT NULL;")=='f':
            assert name=='community' and sql(name,"SELECT count(*) FROM information_schema.tables WHERE table_schema='public';")=='0','Uncontrolled writer database'
            continue
        if sql(name,'SELECT enabled FROM stage7_write_control WHERE id=1;')=='t':active.append(name)
    assert active==([key] if key else []),('Multiple or wrong writers',active,key)
    state.setdefault('authorityTransitions',[]).append({'active':key,'timeUtc':datetime.datetime.now(datetime.timezone.utc).isoformat()});state['authority']=key;save()

def reject_write(key):
    identity();p=subprocess.run(['docker','exec','-i',resources['container'],'psql','-v','ON_ERROR_STOP=1','-U',ROLES[key],'-d',DBS[key],'-At'],input=b'UPDATE community_questions SET title=title;',capture_output=True)
    assert p.returncode!=0 and b'Stage7 business writes paused' in p.stderr,'Inactive writer was not rejected'

def client():return urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))

def request(c,service,path,fields=None):
    data=urllib.parse.urlencode(fields).encode() if fields is not None else None
    try:
        with c.open(urllib.request.Request(f'http://127.0.0.1:{PORTS[service]}'+path,data=data),timeout=15) as response:return response.status,response.geturl(),response.read().decode()
    except urllib.error.HTTPError as error:return error.code,error.geturl(),error.read().decode()

class Inputs(HTMLParser):
    def __init__(self):super().__init__();self.fields={}
    def handle_starttag(self,t,attrs):
        attrs=dict(attrs)
        if t=='input' and 'name' in attrs:self.fields[attrs['name']]=attrs.get('value','')

def page(c,service,path):
    status,url,html=request(c,service,path);assert status==200,(service,path,status)
    parser=Inputs();parser.feed(html);return parser.fields,html

def login(service,who):
    account=private['accounts'][who];c=client();f,_=page(c,service,'/login')
    status,url,_=request(c,service,'/login',dict(username=account['username'],password=private['accountPassword'],accountType=account['role'],_csrf=f['_csrf']))
    assert status==200 and '/login' not in url,(service,who,status)
    cookie_names={cookie.name for handler in c.handlers if hasattr(handler,'cookiejar') for cookie in handler.cookiejar}
    assert 'SMARTFIX_STAGE7_'+service.upper() in cookie_names and 'JSESSIONID' not in cookie_names
    return c

def post(c,service,form_path,path,values):
    f,_=page(c,service,form_path);values=dict(values,_csrf=f['_csrf'])
    if 'communityCommandKey' in f:values['communityCommandKey']=f['communityCommandKey']
    status,url,html=request(c,service,path,values);assert status==200,(service,path,status);return url,html

def copy_tree(source,destination):
    assert source.is_dir() and not destination.exists()
    for p in [source,*source.rglob('*')]:assert not p.is_symlink() and not p.is_junction() and (p.resolve()==source.resolve() or source.resolve() in p.resolve().parents)
    shutil.copytree(source,destination)
    a={str(p.relative_to(source)):hashlib.sha256(p.read_bytes()).hexdigest() for p in source.rglob('*') if p.is_file()}
    b={str(p.relative_to(destination)):hashlib.sha256(p.read_bytes()).hexdigest() for p in destination.rglob('*') if p.is_file()}
    assert a==b;return a

def start(service):
    identity();assert service in PORTS
    if service in resources['services']:
        try:
            with urllib.request.urlopen(f'http://127.0.0.1:{PORTS[service]}/actuator/health',timeout=3) as r:assert json.load(r)['status']=='UP';return
        except Exception:raise RuntimeError('Recorded stage7 process requires diagnosis; not starting a duplicate')
    with socket.socket() as sock:sock.bind(('127.0.0.1',PORTS[service]))
    jar=R/('build-core/smartfix-0.0.1-SNAPSHOT.jar' if service=='core' else 'build-community/smartfix-community-0.0.1-SNAPSHOT.jar' if service=='community' else 'artifacts/baseline.jar')
    assert jar.is_file();uploads=R/('uploads-'+service);assert uploads.is_dir() and uploads.resolve()==uploads
    env={k:v for k,v in os.environ.items() if not k.upper().startswith(('DB_','SMARTFIX_','SPRING_','POSTGRES_','JAVA_TOOL_OPTIONS','JDK_JAVA_OPTIONS'))}
    env.update(DB_URL='jdbc:postgresql://127.0.0.1:15463/'+DBS[service],DB_USERNAME=ROLES[service],DB_PASSWORD=private[service])
    cookie='SMARTFIX_STAGE7_'+service.upper()
    args=['java','-Xmx384m','-jar',str(jar),'--server.address=127.0.0.1','--server.port='+str(PORTS[service]),'--server.servlet.session.cookie.name='+cookie,'--smartfix.uploads.dir='+str(uploads),'--smartfix.notification.email.enabled=false','--smartfix.bootstrap-admin.enabled=false','--smartfix.notification.delivery.scheduling-enabled=false','--smartfix.notification.delivery.eager-enabled=false','--spring.mail.host=127.0.0.1','--spring.mail.port=1','--spring.datasource.hikari.maximum-pool-size=5','--spring.datasource.hikari.minimum-idle=1','--spring.jpa.hibernate.ddl-auto=validate','--spring.jpa.open-in-view=false']
    args+=['--spring.datasource.url=jdbc:postgresql://127.0.0.1:15463/'+DBS[service],'--spring.datasource.username='+ROLES[service]]
    if service in ('core','community'):
        args+=['--smartfix.migration.isolated=true','--smartfix.migration.scenario=stage7','--smartfix.migration.root='+str(ROOT),'--spring.profiles.active='+('community-remote' if service=='core' else 'community-full'),'--smartfix.community.identity.enabled=true','--smartfix.community.identity.public-key='+str(R/'identity/core-public.der'),'--smartfix.community.events.enabled=true','--smartfix.community.events.public-key='+str(R/'identity/community-public.der')]
        if service=='core':args+=['--smartfix.community.identity.private-key='+str(R/'identity/core-private.pk8'),'--smartfix.community.base-url=http://127.0.0.1:19094']
        else:args+=['--smartfix.community.events.private-key='+str(R/'identity/community-private.pk8'),'--smartfix.community.events.core-base-url=http://127.0.0.1:19095']
    with (R/(service+'-private.log')).open('wb') as output:
        proc=subprocess.Popen(args,cwd=ROOT,env=env,stdout=output,stderr=output,creationflags=subprocess.CREATE_NO_WINDOW)
    resources['services'][service]={'pid':proc.pid,'jar':str(jar),'jarSha256':hashlib.sha256(jar.read_bytes()).hexdigest(),'port':PORTS[service]};(R/'resources-private.json').write_text(json.dumps(resources,indent=2))
    for _ in range(75):
        if proc.poll() is not None:raise RuntimeError('Stage7 '+service+' stopped; inspect private log')
        try:
            with urllib.request.urlopen(f'http://127.0.0.1:{PORTS[service]}/actuator/health',timeout=2) as response:
                if json.load(response)['status']=='UP':return
        except Exception:pass
        time.sleep(1)
    raise RuntimeError('Stage7 startup timeout: '+service)

def provision():
    global resources,private
    if (R/'resources-private.json').exists():
        resources=json.loads((R/'resources-private.json').read_text());private=json.loads((R/'credentials-private.json').read_text());identity()
        assert sql('source',"SELECT count(*) FROM information_schema.tables WHERE table_schema='public' AND table_name='community_questions';")=='1'
        assert all((R/('uploads-'+k)).is_dir() for k in DBS) and (R/'artifacts/baseline.jar').is_file()
        assert (R/'identity/core-private.pk8').is_file() and (R/'identity/community-private.pk8').is_file()
        state['originalBackupSha256']=stage3.load()['sourceDumpSha256'];state['baselineJarSha256']=hashlib.sha256((R/'artifacts/baseline.jar').read_bytes()).hexdigest()
        files={str(p.relative_to(R/'uploads-source')):hashlib.sha256(p.read_bytes()).hexdigest() for p in (R/'uploads-source').rglob('*') if p.is_file()}
        state['attachmentFiles']=len(files);state['attachmentDigest']=digest(files)
        authority('source');start('source');save();return
    for port in (15463,*PORTS.values()):
        with socket.socket() as sock:sock.bind(('127.0.0.1',port))
    w=json.loads(stage3.STATE.read_text(encoding='utf-8'));backup=Path(w['resources']['databaseBackupRoot'])/'recovered-current.dump'
    expected=stage3.load()['sourceDumpSha256'];assert hashlib.sha256(backup.read_bytes()).hexdigest()==expected
    private={key:secrets.token_hex(24) for key in ('admin',*DBS)};private.update(accountPassword='Stage7!9'+secrets.token_hex(10),accounts={})
    (R/'credentials-private.json').write_text(json.dumps(private,indent=2));(R/'postgres-private.env').write_text('POSTGRES_USER=stage7_admin\nPOSTGRES_DB=postgres\nPOSTGRES_PASSWORD='+private['admin']+'\n')
    tag='sf-migration-stage7-'+datetime.datetime.now().strftime('%Y%m%d-%H%M%S');volume=tag+'-data';container=tag+'-db'
    command(['docker','volume','create','--label','smartfix.migration.stage=7',volume])
    command(['docker','run','-d','--name',container,'--label','smartfix.migration.stage=7','--env-file',str(R/'postgres-private.env'),'-p','127.0.0.1:15463:5432','--mount','type=volume,source='+volume+',target=/var/lib/postgresql/data','postgres:16-alpine'])
    resources={'container':container,'containerId':json.loads(command(['docker','inspect',container]))[0]['Id'],'volume':volume,'dbPort':15463,'services':{}}
    (R/'resources-private.json').write_text(json.dumps(resources,indent=2))
    for _ in range(45):
        if subprocess.run(['docker','exec',container,'pg_isready','-U','stage7_admin'],capture_output=True).returncode==0:break
        time.sleep(1)
    identity()
    for key in DBS:
        command(['docker','exec','-i',container,'psql','-v','ON_ERROR_STOP=1','-U','stage7_admin','-d','postgres'],input=(f"CREATE ROLE {ROLES[key]} LOGIN PASSWORD '{private[key]}'; CREATE DATABASE {DBS[key]} OWNER {ROLES[key]}; REVOKE CONNECT ON DATABASE {DBS[key]} FROM PUBLIC; GRANT CONNECT ON DATABASE {DBS[key]} TO {ROLES[key]};").encode())
    restore('source',{'path':str(backup),'sha256':expected})
    (R/'artifacts').mkdir(exist_ok=True);baseline=Path(w['baselineVerifyRoot'])/'target/smartfix-0.0.1-SNAPSHOT.jar';assert baseline.is_file();shutil.copy2(baseline,R/'artifacts/baseline.jar')
    files=copy_tree(Path(w['baselineVerifyRoot'])/'stage1-private-uploads',R/'uploads-source')
    for key in ('core','community','restore_delta'):copy_tree(R/'uploads-source',R/('uploads-'+key))
    for prefix in ('core','community'):command(['java',str(ROOT/'tools/migration/GenerateServiceKeys.java'),str(R/'identity'),prefix])
    state['originalBackupSha256']=expected;state['baselineJarSha256']=hashlib.sha256(baseline.read_bytes()).hexdigest();state['attachmentFiles']=len(files);state['attachmentDigest']=digest(files)
    gate_install('source');gate('source',True);authority('source');start('source');save()

def seed():
    start('source')
    for who in ('author','responder','admin'):
        if who in private['accounts']:continue
        name='s7_'+who+'_'+secrets.token_hex(4);c=client();f,_=page(c,'source','/register')
        status,url,_=request(c,'source','/register',dict(username=name,displayName='Stage7 '+who,password=private['accountPassword'],confirmPassword=private['accountPassword'],_csrf=f['_csrf']))
        assert status==200 and '/login' in url
        actor=int(sql('source','SELECT id FROM users WHERE username='+literal(name)+';'));role='ADMINISTRATOR' if who=='admin' else 'REQUESTER'
        if who=='admin':sql('source',"UPDATE users SET role='ADMINISTRATOR' WHERE id="+str(actor)+';')
        private['accounts'][who]={'username':name,'id':actor,'role':role};(R/'credentials-private.json').write_text(json.dumps(private,indent=2))
    a=login('source','author');b=login('source','responder');admin=login('source','admin')
    if 'fixtureQuestion' not in state:
        url,_=post(a,'source','/community/questions/new','/community/questions',{'title':'Stage7 historical fixture','body':'Historical data keeps its identifiers and timestamps.','category':'OTHER'});state['fixtureQuestion']=int(url.rsplit('/',1)[1]);save()
    q=state['fixtureQuestion']
    if 'fixtureAnswer' not in state:
        post(b,'source',f'/community/questions/{q}',f'/community/questions/{q}/answers',{'body':'Historical answer before migration and rollback.'});state['fixtureAnswer']=int(sql('source',f'SELECT max(id) FROM community_answers WHERE question_id={q};'));save()
        post(a,'source',f'/community/questions/{q}',f'/community/questions/{q}/answers/{state["fixtureAnswer"]}/accept',{})
    if 'fixtureReport' not in state:
        post(b,'source',f'/community/questions/{q}',f'/community/questions/{q}/reports',{'reason':'OTHER','detail':'Stage7 historical moderation fixture'})
        state['fixtureReport']=int(sql('source',f'SELECT max(id) FROM community_reports WHERE question_id={q};'));save()
        post(admin,'source','/admin/community/reports',f'/admin/community/reports/{state["fixtureReport"]}/resolve',{'decision':'DISMISSED','note':'Historical decision preserved','hideContent':'false'})
        post(admin,'source','/admin/community/reports?view=ALL',f'/admin/community/questions/{q}/hide',{})
        post(admin,'source','/admin/community/reports?view=ALL',f'/admin/community/questions/{q}/restore',{})
    state['initialSnapshot']=snapshot('source');state['initialSnapshotEvidence']=archive_json('01-initial-community-private.json',state['initialSnapshot'])
    state['initialMonolithArchive']=dump('source','02-initial-monolith.dump');save();restore('core',state['initialMonolithArchive']);state['coreRestored']=True;save()
    start('community');gate_install('community');import_community('community',state['initialSnapshot']);reject_write('community');start('core');gate_install('core')
    state.setdefault('checks',{})['backup_restore_fixture_and_initial_copy']=True;save()

def drain_in_app(key):
    before=sql(key,"SELECT count(*) FROM delivery_tasks WHERE channel='IN_APP' AND status='PENDING';")
    email_before=sql(key,"SELECT COALESCE(json_agg(row_to_json(t) ORDER BY t.id),'[]') FROM (SELECT * FROM delivery_tasks WHERE channel='EMAIL') t;")
    sql(key,"""BEGIN;
    LOCK TABLE delivery_tasks IN SHARE ROW EXCLUSIVE MODE;
    INSERT INTO notifications(recipient_id,event_type,dedup_key,title,message,reference_id,created_at)
      SELECT recipient_id,event_type,dedup_key,title,message,reference_id,created_at FROM delivery_tasks WHERE channel='IN_APP' AND status='PENDING'
      ON CONFLICT(dedup_key) DO NOTHING;
    UPDATE delivery_tasks SET status='DELIVERED',attempts=attempts+1,delivered_at=CURRENT_TIMESTAMP,last_error=NULL WHERE channel='IN_APP' AND status='PENDING';
    COMMIT;""")
    assert sql(key,"SELECT count(*) FROM delivery_tasks WHERE channel='IN_APP' AND status='PENDING';")=='0'
    assert sql(key,"SELECT COALESCE(json_agg(row_to_json(t) ORDER BY t.id),'[]') FROM (SELECT * FROM delivery_tasks WHERE channel='EMAIL') t;")==email_before
    state.setdefault('legacyInAppTasksDrained',[]).append({'database':key,'count':int(before),'emailUnchanged':True});save()

def final_sync():
    q=state['fixtureQuestion']
    if 'finalSnapshot' not in state:
        b=login('source','responder')
        if not state.get('sourceFinalReply'):
            post(b,'source',f'/community/questions/{q}',f'/community/questions/{q}/answers',{'body':'Final source reply arriving after the initial copy.'});state['sourceFinalReply']=True;save()
        args=['docker','exec','-i',resources['container'],'psql','-v','ON_ERROR_STOP=1','-U',ROLES['source'],'-d',DBS['source'],'-At']
        with (R/'drain-probe-private.log').open('wb') as out:
            probe=subprocess.Popen(args,stdin=subprocess.PIPE,stdout=out,stderr=out)
            probe.stdin.write((f"SET application_name='stage7-drain-probe'; BEGIN; UPDATE community_questions SET title=title WHERE id={q}; SELECT pg_sleep(4); COMMIT;").encode());probe.stdin.close()
            end=time.time()+10
            while time.time()<end:
                if int(sql('source',"SELECT count(*) FROM pg_stat_activity WHERE application_name='stage7-drain-probe' AND wait_event='PgSleep';"))==1:break
                time.sleep(.1)
            else:raise AssertionError('Drain probe was not observed')
            started=time.monotonic();gate('source',False);elapsed=time.monotonic()-started;assert probe.wait(timeout=10)==0 and elapsed>=1
        state['drainedTransactionSeconds']=round(elapsed,3);authority(None);reject_write('source')
        final=snapshot('source');assert len(final['community_answers'])>len(state['initialSnapshot']['community_answers'])
        state['finalSnapshot']=final;state['finalSnapshotEvidence']=archive_json('03-final-community-private.json',final);save()
    else:
        gate('community',False);authority(None)
    final=state['finalSnapshot'];assert snapshot('source')==final
    assert sql('community','SELECT count(*) FROM community_outbox;')=='0','Historical events must not be replayed'
    drain_in_app('source')
    import_community('community',final)
    for table in ('delivery_tasks','notifications','audit_entries'):upsert('core',table,rows('source',table));sequences('core',[table])
    state['notificationWatermark']=int(sql('source','SELECT COALESCE(max(id),0) FROM notifications;'))
    gate('community',True);authority('community')
    a=login('core','author');page(a,'core',f'/community/questions/{q}');_,notifications=page(a,'core','/notifications');assert f'/community/questions/{q}' in notifications
    for row in final['community_questions']:
        if row['status']=='VISIBLE':page(a,'core','/community/questions/'+str(row['id']))
    state['cutoverCommunityDigest']=digest(snapshot('community'));state['checks']['paused_drained_final_sync_single_writer_and_historical_links']=True;save()

class StaleRollbackRefused(RuntimeError): pass

def activate_original_test_source():
    if digest(snapshot('community'))!=state['cutoverCommunityDigest']:
        raise StaleRollbackRefused('Service data changed; a new restored database is required')
    gate('community',False);authority(None);reject_write('community');gate('source',True);authority('source')

def rollback_without_new_writes():
    activate_original_test_source()
    a=login('source','author');page(a,'source',f'/community/questions/{state["fixtureQuestion"]}');page(a,'source','/notifications')
    assert snapshot('source')==state['finalSnapshot']
    gate('source',False);authority(None);reject_write('source');gate('community',True);authority('community')
    state['checks']['no_new_write_rollback_to_disposable_monolith']=True;save()

def new_writes():
    a=login('core','author');b=login('core','responder');admin=login('core','admin')
    if 'newQuestion' not in state:
        url,_=post(a,'core','/community/questions/new','/community/questions',{'title':'Stage7 after cutover','body':'This new question must survive incremental monolith recovery.','category':'OTHER'});state['newQuestion']=int(url.rsplit('/',1)[1]);save()
    q=state['newQuestion']
    if 'newAnswer' not in state:
        post(b,'core',f'/community/questions/{q}',f'/community/questions/{q}/answers',{'body':'New service-owned answer preserved during recovery.'});state['newAnswer']=int(sql('community',f'SELECT max(id) FROM community_answers WHERE question_id={q};'));save()
        post(a,'core',f'/community/questions/{q}',f'/community/questions/{q}/answers/{state["newAnswer"]}/accept',{})
        post(b,'core',f'/community/questions/{q}',f'/community/questions/{q}/reports',{'reason':'OTHER','detail':'Incremental report and audit preserved'})
        report=int(sql('community',f'SELECT max(id) FROM community_reports WHERE question_id={q};'))
        post(admin,'core','/admin/community/reports',f'/admin/community/reports/{report}/resolve',{'decision':'ACTIONED','hideContent':'true','note':'New service moderation'})
        post(admin,'core','/admin/community/reports?view=ALL',f'/admin/community/questions/{q}/restore',{})
    end=time.time()+90
    while time.time()<end:
        if sql('community',"SELECT count(*) FROM community_outbox WHERE status<>'SENT';")=='0':break
        time.sleep(1)
    else:raise AssertionError('New event backlog did not drain')
    _,html=page(a,'core','/notifications');assert f'/community/questions/{q}' in html
    assert digest(snapshot('community'))!=state['cutoverCommunityDigest']
    # The old source is stale: this branch deliberately refuses direct reactivation.
    assert sql('source','SELECT count(*) FROM community_questions WHERE id='+str(q)+';')=='0'
    try:activate_original_test_source()
    except StaleRollbackRefused:state['staleSourceRollback']='REFUSED_NEW_SERVICE_WRITES_EXIST'
    else:raise AssertionError('Stale rollback was allowed')
    reject_write('source')
    state['checks']['new_question_answer_acceptance_report_audit_and_stale_rollback_refused']=True;save()

def recover_increment():
    gate('community',False);authority(None);reject_write('community')
    assert sql('community',"SELECT count(*) FROM community_outbox WHERE status<>'SENT';")=='0'
    delta=snapshot('community');state['deltaSnapshotEvidence']=archive_json('04-service-final-private.json',delta)
    state['communityArchive']=dump('community','05-community-after-new-writes.dump');state['coreArchive']=dump('core','06-core-after-new-writes.dump')
    restore('restore_delta',state['coreArchive']);state['restoreCreated']=True;gate_install('restore_delta');gate('restore_delta',False)
    # New audit is already projected into the full Core archive with source/id intact.
    # Never overwrite global USER/DELIVERY/SLA audit IDs with Community-local IDs.
    import_community('restore_delta',delta,include_audit=False);sequences('restore_delta',['audit_entries','notifications','users'])
    assert rows('restore_delta','notifications')==rows('core','notifications')
    assert rows('restore_delta','audit_entries')==rows('core','audit_entries')
    assert rows('restore_delta','users')==rows('core','users')
    gate('restore_delta',True);authority('restore_delta');start('restore_delta')
    a=login('restore_delta','author');b=login('restore_delta','responder');admin=login('restore_delta','admin')
    for q in (state['fixtureQuestion'],state['newQuestion']):page(a,'restore_delta',f'/community/questions/{q}')
    _,html=page(a,'restore_delta',f'/community/questions/{state["newQuestion"]}');assert 'New service-owned answer preserved during recovery.' in html
    for who in (a,b):
        _,html=page(who,'restore_delta','/notifications');assert f'/community/questions/{state["newQuestion"]}' in html
    for path in ('/admin/community/reports?view=ALL','/admin/community/audit','/admin/requests'):page(admin,'restore_delta',path)
    assert sql('restore_delta',f'SELECT accepted_answer_id FROM community_questions WHERE id={state["newQuestion"]};')==str(state['newAnswer'])
    attachments=rows('restore_delta','request_attachments');state['historicalAttachments']='N/A: source backup has zero attachment rows' if not attachments else 'Restored rows require manual file read check'
    assert rows('restore_delta','request_attachments')==rows('source','request_attachments')
    state['restoredCommunityDigest']=digest({t:rows('restore_delta',t) for t in TABLES});state['serviceCommunityDigest']=digest({t:delta[t] for t in TABLES});assert state['restoredCommunityDigest']==state['serviceCommunityDigest']
    state['finalCounts']={t:len(delta[t]) for t in (*TABLES,'community_audit')};state['checks']['new_database_incremental_restore_rows_notifications_audit_accounts_sequences_and_pages']=True;save()

def final_guards_and_sequences():
    before=state['authority']
    try:activate_original_test_source()
    except StaleRollbackRefused:pass
    else:raise AssertionError('Stale source activation was permitted')
    authority(before)
    for key in ('source','community','core'):reject_write(key)
    state['checks']['stale_activation_guard_and_all_inactive_writer_roles_rejected']=True
    if 'sequenceProbes' not in state:
        proof={}
        statements={
            'community_questions':"INSERT INTO community_questions(author_id,title,body,category) VALUES ("+str(private['accounts']['author']['id'])+",'Stage7 sequence probe','This sequence probe is rolled back.','OTHER') RETURNING id;",
            'community_answers':"INSERT INTO community_answers(question_id,author_id,body) VALUES ("+str(state['newQuestion'])+","+str(private['accounts']['responder']['id'])+",'This answer sequence probe is rolled back.') RETURNING id;"
        }
        for table,statement in statements.items():
            maximum=int(sql('restore_delta','SELECT max(id) FROM '+table+';'))
            result=sql('restore_delta','BEGIN; '+statement+' ROLLBACK;',ROLES['restore_delta'])
            value=next(int(v) for v in result.splitlines() if v.isdigit());assert value>maximum
            proof[table]={'maxBefore':maximum,'generatedAndRolledBack':value}
        state['sequenceProbes']=proof
    assert digest({t:rows('restore_delta',t) for t in TABLES})==state['restoredCommunityDigest']
    assert sql('core','SELECT count(*) FROM community_questions WHERE id='+str(state['newQuestion'])+';')=='0'
    state['checks']['restored_sequences_generate_higher_ids_without_committed_probe_data']=True;save()

def protected_checks():
    w=json.loads(stage3.STATE.read_text(encoding='utf-8'))
    for item in json.loads((Path(w['backupRoot'])/'file-manifest.json').read_text(encoding='utf-8')):
        p=Path(w['originalRoot'])/item['path'];assert p.is_file() and hashlib.sha256(p.read_bytes()).hexdigest()==item['sha256']
    for name,old in json.loads((ROOT/'.migration-runtime/protected-before.json').read_text()).items():
        now=stage3.inspect(name);assert now['Id']==old['id'] and now['State']==old['state'] and now['Mounts']==old['mounts']
    for port in (19090,19091,19092,*PORTS.values()):
        with urllib.request.urlopen(f'http://127.0.0.1:{port}/actuator/health',timeout=5) as r:assert json.load(r)['status']=='UP'
    for key,other in (('source','core'),('core','community'),('community','core'),('restore_delta','source')):
        assert sql(key,'SELECT current_user;',ROLES[key])==ROLES[key]
        result=subprocess.run(['docker','exec',resources['container'],'psql','-U',ROLES[key],'-d',DBS[other],'-Atc','SELECT 1;'],capture_output=True)
        assert result.returncode!=0 and b'permission denied' in result.stderr
    files=[]
    for key in DBS:
        root=R/('uploads-'+key);assert root.resolve()==root
        files.append({str(p.relative_to(root)):hashlib.sha256(p.read_bytes()).hexdigest() for p in root.rglob('*') if p.is_file()})
    assert all(f==files[0] for f in files)
    state['checks']['separate_database_roles_and_attachment_copies_verified']=True
    (R/'test-accounts-private.json').write_text(json.dumps({'password':private['accountPassword'],'accounts':private['accounts'],'restoredUrl':'http://127.0.0.1:19096/login'},indent=2))
    identity();state['checks']['original_fingerprints_protected_containers_and_all_seven_entries_healthy']=True;save()

def stop(service):
    identity();assert service in PORTS
    recorded=resources['services'].get(service)
    if not recorded:return
    pid=int(recorded['pid']);jar=Path(recorded['jar']);assert R in jar.parents and jar.resolve()==jar
    script=f"$p=Get-CimInstance Win32_Process -Filter 'ProcessId={pid}'; if($null -ne $p){{$p.CommandLine | ConvertTo-Json -Compress}}"
    result=command(['powershell','-NoProfile','-Command',script]).decode().strip()
    if result:
        line=json.loads(result);assert str(jar).lower() in line.lower() and '--server.port='+str(PORTS[service]) in line,'Refuse unrelated process'
        command(['taskkill','/PID',str(pid),'/T','/F'])
    del resources['services'][service];(R/'resources-private.json').write_text(json.dumps(resources,indent=2))

def run():
    global state,resources,private
    stage3.safety();w=json.loads(stage3.STATE.read_text(encoding='utf-8'));assert w['stage6']['status']=='PASS' and w['stage6']['browserAcceptance']=='PASS_USER_CONFIRMED'
    assert R.resolve()==ROOT/'.migration-runtime/stage7';R.mkdir(exist_ok=True)
    path=R/'rehearsal-private.json';state=json.loads(path.read_text(encoding='utf-8')) if path.exists() else {'completed':[],'checks':{}}
    if (R/'resources-private.json').exists():resources=json.loads((R/'resources-private.json').read_text());private=json.loads((R/'credentials-private.json').read_text());identity()
    for operation in (provision,seed,final_sync,rollback_without_new_writes,new_writes,recover_increment,final_guards_and_sequences,protected_checks):
        if operation.__name__ in state['completed']:continue
        operation();state['completed'].append(operation.__name__);save();print('Stage7 checkpoint: '+operation.__name__,flush=True)
    print(json.dumps({'checks':state['checks'],'finalCounts':state['finalCounts'],'authority':state['authority'],'manualUrl':'http://127.0.0.1:19096/login','nextStageAllowed':False}))

if __name__=='__main__':
    parser=argparse.ArgumentParser();parser.add_argument('action',choices=['run','status','stop']);parser.add_argument('service',nargs='?',choices=PORTS);args=parser.parse_args()
    stage3.safety();assert R.resolve()==ROOT/'.migration-runtime/stage7';R.mkdir(exist_ok=True)
    import msvcrt
    with (R/'rehearsal.lock').open('a+b') as lock:
        lock.seek(0);lock.write(b'0');lock.flush();lock.seek(0);msvcrt.locking(lock.fileno(),msvcrt.LK_NBLCK,1)
        if args.action=='run':run()
        else:
            resources=json.loads((R/'resources-private.json').read_text());state=json.loads((R/'rehearsal-private.json').read_text());identity()
            if args.action=='stop':
                if args.service is None:parser.error('stop requires an explicit stage7 service')
                stop(args.service)
            else:print(json.dumps({'completed':state['completed'],'checks':state['checks'],'authority':state.get('authority'),'ports':PORTS}))
