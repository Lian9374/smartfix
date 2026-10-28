"""Additional real acceptance/hide race and receiver outage recovery."""
import concurrent.futures,json,secrets,time
import verify_stage8 as v
def run():
    saved=v.setup();a=saved['accounts']['author'];b=saved['accounts']['responder'];admin=saved['accounts']['admin']
    q=v.ok(a,19102,'ask',{'command':{'title':'Stage8 hide race '+secrets.token_hex(4),'body':'Acceptance must never point to a hidden answer.','category':'OTHER'}})
    answer=v.ok(b,19103,'answer',{'id':q,'command':{'body':'An answer raced against a moderator hiding it.'}})
    v.http.token(a);v.http.token(admin)
    with concurrent.futures.ThreadPoolExecutor(2) as pool:
        accepted=pool.submit(v.api,a,19102,'accept',{'id':q,'answerId':answer});hidden=pool.submit(v.api,admin,19103,'hideAnswer',{'id':answer})
        assert accepted.result()[0] in (200,409) and hidden.result()[0]==200
    assert v.sql(f'SELECT accepted_answer_id IS NULL FROM community_questions WHERE id={q};',True)=='t'
    v.wait_events()
    v.env.load();core_before=v.env.resources['services']['core'];v.operate('stop','core')
    try:
        v.ok(b,19102,'answer',{'id':q,'command':{'body':'Persisted event while the Core receiver is unavailable.'}})
        time.sleep(4)
        assert int(v.sql(f"SELECT count(*) FROM community_outbox WHERE question_id={q} AND status='PENDING' AND attempts>0;",True))>=1
    finally:v.operate('start','core')
    v.wait_events();assert v.sql(f"SELECT count(*) FROM community_outbox WHERE question_id={q} AND status<>'SENT';",True)=='0'
    tech=v.http.login(saved['accounts']['tech'],saved['password'])
    for path in ('/community','/community/mine','/notifications','/workorders/mine'):assert v.http.req(tech,v.http.CORE+path)[0]==200
    stranger=v.http.login(saved['accounts']['quota'],saved['password'])
    repair=json.loads((v.R/'repair-results-private.json').read_text())
    assert v.http.req(stranger,v.http.CORE+f"/requests/{repair['ticket']}/attachments/{repair['attachmentId']}")[0]==404
    assert v.http.req(stranger,v.http.CORE+'/admin/community/reports')[0]==403
    out={'acceptVsHideRace':True,'receiverOutageDurableRetryAndRecovery':True,'technicianCommunityAndWorkspace':True,'strangerAttachmentAndAdminDenied':True,'coreArtifactUnchangedAfterRestart':v.env.resources['services']['core']['sha256']==core_before['sha256'],'coreBeforeReceiverOutage':core_before,'coreAfterReceiverRecovery':v.env.resources['services']['core']}
    (v.R/'extra-results-private.json').write_text(json.dumps(out,indent=2));print('Acceptance/hide and receiver recovery passed')
if __name__=='__main__':run()
