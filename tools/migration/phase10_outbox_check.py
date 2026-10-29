"""Live PostgreSQL regression for bounded publisher; stage9 clones ONLY, never formal metrics.
Checks rollback, ordering, duplicate delivery, receiver restart and a real abandoned lease.
"""
import concurrent.futures,ctypes,json,subprocess,time,uuid
from ctypes import wintypes
import stage9 as env
from perf9 import Driver,API,CORE,opener,fields,Failure

def main():
    env.load();assert env.state.get('communityArtifactVersion')=='stage10-v2'
    d=Driver();proof={};owner=1000100;nonce=uuid.uuid4().hex
    def sql(q,core=False):return env.sql('core' if core else 'community',q)
    def api(actor,op,data,key=None,expected=200):
        raw,_=d.request(opener(),API+'/internal/community/v1/operations/'+op,payload=data,headers={'Authorization':'Bearer '+d.token(actor),'Idempotency-Key':key or str(uuid.uuid4())},expected=expected)
        return json.loads(raw) if expected==200 else None
    def drain(seconds=70):
        end=time.perf_counter()+seconds
        while time.perf_counter()<end:
            if sql("SELECT count(*) FROM community_outbox WHERE status<>'SENT';")=='0':return
            time.sleep(.25)
        raise AssertionError('Outbox did not drain; preserve private diagnostics')
    try:
        title='Phase10 publisher '+nonce;q=api(owner,'ask',{'command':{'title':title,'body':'Publisher correctness on the isolated performance clone.','category':'OTHER'}})
        # A ready burst on one aggregate also checks sequence/order across two publishers.
        with concurrent.futures.ThreadPoolExecutor(8) as pool:
            answers=list(pool.map(lambda i:api(1000101+i%16,'answer',{'id':q,'command':{'body':'Burst reply '+nonce+' '+str(i)}}),range(30)))
        assert len(set(answers))==30;drain()
        assert int(sql(f'SELECT count(*) FROM core_community_inbox WHERE question_id={q};',True))==30
        assert sql(f"SELECT count(*) FROM community_outbox WHERE question_id={q} AND status='SENT';")=='30'
        assert sql(f'SELECT max(aggregate_sequence)-min(aggregate_sequence)+1 FROM community_outbox WHERE question_id={q};')=='30'
        proof['burst30OrderedEventsTwoPublishers']=True
        # Business write + command + event must still roll back together.
        marker='Rollback reply '+nonce;key=str(uuid.uuid4())
        sql("CREATE FUNCTION phase10_fail_outbox() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'phase10 expected rollback'; END $$; CREATE TRIGGER phase10_fail_outbox BEFORE INSERT ON community_outbox FOR EACH ROW EXECUTE FUNCTION phase10_fail_outbox();")
        try:
            try:api(1000120,'answer',{'id':q,'command':{'body':marker}},key);raise AssertionError('Expected transaction failure')
            except Failure as e:assert e.kind=='http_500'
            assert sql("SELECT count(*) FROM community_answers WHERE body='"+marker+"';")=='0'
            assert sql("SELECT count(*) FROM community_commands WHERE command_key='"+key+"';")=='0'
        finally:sql('DROP TRIGGER phase10_fail_outbox ON community_outbox; DROP FUNCTION phase10_fail_outbox();')
        proof['answerCommandAndOutboxRollbackTogether']=True
        env.load();env.stop('core')
        try:
            api(1000121,'answer',{'id':q,'command':{'body':'Receiver outage '+nonce}})
            end=time.perf_counter()+12
            while time.perf_counter()<end and int(sql(f"SELECT coalesce(max(attempts),0) FROM community_outbox WHERE question_id={q} AND status='PENDING';"))==0:time.sleep(.25)
            assert int(sql(f"SELECT count(*) FROM community_outbox WHERE question_id={q} AND status='PENDING' AND attempts>0;"))>0
        finally:env.load();env.start('core')
        drain();proof['receiverOutagePersistentRetryAndRecovery']=True
        event=sql(f'SELECT event_id FROM community_outbox WHERE question_id={q} ORDER BY aggregate_sequence LIMIT 1;')
        end=time.perf_counter()+15
        while time.perf_counter()<end and sql("SELECT count(*) FROM notifications WHERE dedup_key='COMMUNITY_EVENT:"+event+"';",True)!='1':time.sleep(.25)
        assert sql("SELECT count(*) FROM notifications WHERE dedup_key='COMMUNITY_EVENT:"+event+"';",True)=='1'
        inbox_before=sql(f'SELECT count(*) FROM core_community_inbox WHERE question_id={q};',True)
        sql("UPDATE community_outbox SET status='PENDING',lease_until=NULL,lease_token=NULL,lease_owner=NULL,next_attempt_at=CURRENT_TIMESTAMP WHERE event_id='"+event+"';")
        drain();assert sql("SELECT count(*) FROM notifications WHERE dedup_key='COMMUNITY_EVENT:"+event+"';",True)=='1'
        assert sql(f'SELECT count(*) FROM core_community_inbox WHERE question_id={q};',True)==inbox_before
        proof['duplicateAcknowledgedEventDoesNotDuplicateEffects']=True
        # Hold real process handles BEFORE claiming to avoid PID reuse and a slow CIM race.
        env.load();kernel=ctypes.WinDLL('kernel32',use_last_error=True)
        kernel.OpenProcess.argtypes=[wintypes.DWORD,wintypes.BOOL,wintypes.DWORD];kernel.OpenProcess.restype=wintypes.HANDLE
        kernel.TerminateProcess.argtypes=[wintypes.HANDLE,wintypes.UINT];kernel.CloseHandle.argtypes=[wintypes.HANDLE]
        handles={}
        for name in ('a','b'):
            row=env.state['services'][name];handle=kernel.OpenProcess(0x101001,False,row['pid']);assert handle
            line=json.loads(env.command(['powershell','-NoProfile','-Command',f"(Get-CimInstance Win32_Process -Filter 'ProcessId={row['pid']}').CommandLine|ConvertTo-Json -Compress"]).decode())
            assert row['artifact'].lower() in line.lower() and '--server.port='+str(env.PORTS[name]) in line
            handles[name]=(handle,row['pid'])
        locker=subprocess.Popen(['docker','exec','-i',env.state['container'],'psql','-U','stage9_admin','-d',env.DBS['core'],'-At'],stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.PIPE)
        locker.stdin.write(f'BEGIN; SELECT pg_advisory_xact_lock({q}); SELECT pg_sleep(25); COMMIT;'.encode());locker.stdin.close();time.sleep(2)
        try:
            api(1000122,'answer',{'id':q,'command':{'body':'Abandoned committed lease '+nonce}})
            end=time.perf_counter()+12;claim=''
            while time.perf_counter()<end:
                claim=sql(f"SELECT coalesce(lease_owner,'') FROM community_outbox WHERE question_id={q} AND status='PENDING' AND lease_until>CURRENT_TIMESTAMP ORDER BY aggregate_sequence DESC LIMIT 1;")
                if claim:break
            assert claim;name,pid=claim.split(':');handle,known=handles[name];assert int(pid)==known
            assert kernel.TerminateProcess(handle,1)
            assert sql(f"SELECT count(*) FROM community_outbox WHERE question_id={q} AND status='PENDING' AND lease_owner='{claim}' AND lease_until>CURRENT_TIMESTAMP;")=='1'
            del env.state['services'][name];env.save()
        finally:
            for handle,_ in handles.values():kernel.CloseHandle(handle)
            locker.wait(timeout=40)
        try:
            drain();assert int(sql(f'SELECT attempts FROM community_outbox WHERE question_id={q} ORDER BY aggregate_sequence DESC LIMIT 1;'))>=2
        finally:env.load();env.start(name)
        proof['realClaimantExitOtherProcessLeaseRecovery']=True
        d.login(owner,'perf9_100');html,_=d.request(d.sessions[owner],CORE+'/notifications');assert '/community/questions/'+str(q) in html
        actor=1000102;d.login(actor,'perf9_102');html,_=d.request(d.sessions[actor],CORE+'/community/questions/'+str(q));f=fields(html);reply='Original form '+nonce
        html,url=d.request(d.sessions[actor],CORE+f'/community/questions/{q}/answers',form={'_csrf':f['_csrf'],'communityCommandKey':f['communityCommandKey'],'body':reply});assert reply in html and '/community/questions/'+str(q) in url
        drain();proof['originalCoreFormAndNotificationLinks']=True
        d.location=int(sql('SELECT min(id) FROM locations WHERE active;',True));d.prepare('repair',2);d.operation('repair',1,2,force_write=True);proof['coreRepairSubmissionPreserved']=True
        assert sql("SELECT count(*) FROM pg_trigger WHERE tgname='phase10_fail_outbox';")=='0'
        (env.R/'phase10/publisher-v2-correctness.json').write_text(json.dumps(proof,indent=2));print(json.dumps(proof))
    finally:d.close()
if __name__=='__main__':main()
