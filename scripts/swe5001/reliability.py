#!/usr/bin/env python3
"""Local-only integration proof: replay a real delivered event and verify no second projection.
Requires boto3 and psycopg (Worker requirements). Never points at production.
"""
import hashlib,json,os,time
from pathlib import Path
import boto3,psycopg
core='postgresql://smartfix:'+os.environ.get('LOCAL_DB_PASSWORD','smartfix-local-only')+'@127.0.0.1:15432/smartfix'
worker='postgresql://notification_worker:'+os.environ.get('LOCAL_WORKER_PASSWORD','worker-local-only')+'@127.0.0.1:15433/notifications'
with psycopg.connect(core) as conn:
    row=conn.execute("SELECT id,recipient_id,channel,event_type,dedup_key,title,message,reference_id,created_at FROM delivery_tasks WHERE status='DELIVERED' AND channel='IN_APP' ORDER BY id DESC LIMIT 1").fetchone()
    assert row,'Run e2e.py first'
    when=row[8].astimezone(__import__('datetime').timezone.utc)
    fraction=f'{when.microsecond:06d}' if when.microsecond%1000 else f'{when.microsecond//1000:03d}' if when.microsecond else ''
    stamp=when.strftime('%Y-%m-%dT%H:%M:%S')+('.'+fraction if fraction else '')+'Z'
    event=dict(schemaVersion=1,eventType='notification.requested.v1',taskId=row[0],recipientId=row[1],channel=row[2],notificationType=row[3],dedupKey=row[4],title=row[5],message=row[6],referenceId=row[7],email=None,occurredAt=stamp)
    before=conn.execute('SELECT count(*) FROM notifications WHERE dedup_key=%s',(row[4],)).fetchone()[0]
body=json.dumps(event,sort_keys=True,separators=(',',':'),ensure_ascii=False)
with psycopg.connect(worker) as conn:
    recorded=conn.execute('SELECT payload_hash FROM notification_inbox WHERE task_id=%s',(row[0],)).fetchone()[0]
    assert recorded==hashlib.sha256(body.encode()).hexdigest(),'Fixture must reconstruct the exact already processed envelope'
sqs=boto3.client('sqs',endpoint_url='http://127.0.0.1:9324',region_name='ap-southeast-1',aws_access_key_id='local',aws_secret_access_key='local')
queue='http://sqs:9324/000000000000/smartfix-notifications'
for _ in range(3):sqs.send_message(QueueUrl=queue,MessageBody=body)
deadline=time.monotonic()+30
while time.monotonic()<deadline:
    attrs=sqs.get_queue_attributes(QueueUrl=queue,AttributeNames=['ApproximateNumberOfMessages','ApproximateNumberOfMessagesNotVisible'])['Attributes']
    if int(attrs.get('ApproximateNumberOfMessages',0))+int(attrs.get('ApproximateNumberOfMessagesNotVisible',0))==0:break
    time.sleep(1)
else:raise AssertionError('Queue did not drain')
with psycopg.connect(core) as conn:
    after=conn.execute('SELECT count(*) FROM notifications WHERE dedup_key=%s',(row[4],)).fetchone()[0]
assert before==after==1
result={'result':'PASS','taskId':row[0],'duplicateMessages':3,'notificationCountBefore':before,'notificationCountAfter':after}
Path('results/reliability.json').write_text(json.dumps(result,indent=2)+'\n')
print(json.dumps(result,indent=2))
