#!/usr/bin/env python3
"""Local-only fault tests with real PostgreSQL and the protected Core HTTP endpoint.
Run via the Compose fault-tests profile, never against shared/production data.
"""
import json,os,threading,time,uuid
from concurrent.futures import ThreadPoolExecutor
from contextlib import contextmanager
from pathlib import Path
from urllib.request import Request,urlopen
from urllib.error import HTTPError
import psycopg
from worker import PostgresInbox,Processor,callback

core=os.environ['CORE_TEST_DB_URL'];worker=os.environ['WORKER_DB_URL'];base=os.environ['CORE_URL']
inbox=PostgresInbox(worker);complete=callback(base,os.environ['SMARTFIX_INTERNAL_TOKEN'])

def fixture(status='QUEUED'):
    with psycopg.connect(core) as conn:
        user=conn.execute("SELECT id FROM users WHERE username='swe5001.requester'").fetchone()[0]
        key='FAULT:'+str(uuid.uuid4())
        task=conn.execute("INSERT INTO delivery_tasks(recipient_id,channel,event_type,dedup_key,title,message,status,attempts,next_attempt_at,created_at) VALUES(%s,'IN_APP','FAULT_TEST',%s,'Fault test','Local reliability fixture',%s,0,now()+interval '1 hour',now()) RETURNING id",(user,key,status)).fetchone()[0]
    return dict(schemaVersion=1,eventType='notification.requested.v1',taskId=task,dedupKey=key,recipientId=user,channel='IN_APP',notificationType='FAULT_TEST',title='Fault test',message='Local reliability fixture',referenceId=None,email=None,occurredAt='2026-10-19T00:00:00Z')

def count(event):
    with psycopg.connect(core) as conn:return conn.execute('SELECT count(*) FROM notifications WHERE dedup_key=%s',(event['dedupKey'],)).fetchone()[0]

checks=[]
# Real protected endpoint rejects no token, wrong token, and mismatched delivery identity.
event=fixture()
for token,body,expected in [('',dict(dedupKey=event['dedupKey'],outcome='DELIVERED'),403),('Bearer wrong',dict(dedupKey=event['dedupKey'],outcome='DELIVERED'),401),('Bearer '+os.environ['SMARTFIX_INTERNAL_TOKEN'],dict(dedupKey='wrong',outcome='DELIVERED'),400)]:
    request=Request(f"{base}/internal/deliveries/{event['taskId']}/complete",json.dumps(body).encode(),headers={'Content-Type':'application/json','Authorization':token})
    try:
        response=urlopen(request,timeout=10);actual=response.status
    except HTTPError as exc:actual=exc.code
    assert actual==expected,(actual,expected)
assert count(event)==0
checks.append('missing/wrong token and wrong dedup key rejected by real HTTP API')
# Model a send/commit gap: Core still PENDING, publication may already have occurred.
event=fixture('PENDING')
class CrashBeforeInboxCommit:
    @contextmanager
    def claim(self,event,digest):
        with inbox.claim(event,digest) as done:
            yield done
            raise OSError('Injected Worker crash after Core callback, before inbox commit')
try:Processor(CrashBeforeInboxCommit(),complete,lambda e:None).handle(event)
except OSError:pass
else:raise AssertionError('Crash injection did not execute')
assert count(event)==1
with psycopg.connect(worker) as conn:assert conn.execute('SELECT count(*) FROM notification_inbox WHERE task_id=%s',(event['taskId'],)).fetchone()[0]==0
Processor(inbox,complete,lambda e:None).handle(event)
assert count(event)==1
checks.append('PENDING Core task accepts callback; crash after callback rolls back inbox and safely retries')
# Concurrent independent Worker DB connections serialize on the same inbox identity.
event=fixture();calls=[];lock=threading.Lock()
def counting_callback(e,outcome):
    with lock:calls.append(e['taskId'])
    time.sleep(0.1);complete(e,outcome)
processor=Processor(inbox,counting_callback,lambda e:None)
with ThreadPoolExecutor(max_workers=6) as pool:list(pool.map(lambda _:processor.handle(event),range(6)))
assert len(calls)==1 and count(event)==1
checks.append('six concurrent PostgreSQL inbox consumers execute one callback and one notification')
result={'result':'PASS','checks':checks,'timestamp':time.strftime('%Y-%m-%dT%H:%M:%SZ',time.gmtime())}
Path('/results/fault-tests.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result,indent=2))
