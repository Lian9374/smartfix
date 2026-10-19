"""Independent notification delivery service. No Core database access."""
import hashlib
import json
import logging
import os
import signal
import smtplib
import threading
import time
from contextlib import contextmanager
from datetime import datetime
from email.message import EmailMessage
from urllib.request import Request, urlopen

log = logging.getLogger('smartfix.worker')


def validate_event(body):
    try:
        event = json.loads(body)
        if not isinstance(event, dict): raise ValueError('Object required')
        if type(event.get('schemaVersion')) is not int or event['schemaVersion'] != 1: raise ValueError('Unsupported version')
        if event.get('eventType') != 'notification.requested.v1': raise ValueError('Unsupported event')
        for field in ('taskId', 'recipientId'):
            if type(event.get(field)) is not int or event[field] < 1: raise ValueError('Invalid identity')
        if event.get('referenceId') is not None and (type(event['referenceId']) is not int or event['referenceId'] < 1):
            raise ValueError('Invalid reference')
        for field, limit in [('dedupKey', 200), ('notificationType', 80), ('title', 150), ('message', 1000)]:
            value = event.get(field)
            if not isinstance(value, str) or not value.strip() or len(value) > limit: raise ValueError('Invalid text')
        if event.get('channel') not in ('IN_APP', 'EMAIL'): raise ValueError('Invalid channel')
        address = event.get('email')
        if address is not None and (not isinstance(address, str) or len(address) > 254 or '\r' in address or '\n' in address or '@' not in address):
            raise ValueError('Invalid address')
        when = datetime.fromisoformat(event['occurredAt'].replace('Z', '+00:00'))
        if when.tzinfo is None: raise ValueError('Timezone required')
        return event
    except (KeyError, TypeError, AttributeError, json.JSONDecodeError) as exc:
        raise ValueError('Invalid envelope') from exc


class Processor:
    def __init__(self, inbox, complete, send_email):
        self.inbox, self.complete, self.send_email = inbox, complete, send_email

    def handle(self, event):
        digest = hashlib.sha256(json.dumps(event, sort_keys=True, separators=(',', ':'), ensure_ascii=False).encode()).hexdigest()
        with self.inbox.claim(event, digest) as done:
            if done: return False
            outcome = 'DELIVERED'
            if event['channel'] == 'EMAIL':
                if event.get('email'): self.send_email(event)
                else: outcome = 'SKIPPED'
            self.complete(event, outcome)
        return True


class PostgresInbox:
    def __init__(self, dsn):
        import psycopg
        self.connect = lambda: psycopg.connect(dsn, connect_timeout=5)
        with self.connect() as conn:
            conn.execute('''CREATE TABLE IF NOT EXISTS notification_inbox (
                task_id BIGINT PRIMARY KEY, dedup_key VARCHAR(200) UNIQUE NOT NULL,
                payload_hash CHAR(64) NOT NULL, processed_at TIMESTAMPTZ,
                created_at TIMESTAMPTZ NOT NULL DEFAULT now())''')

    @contextmanager
    def claim(self, event, digest):
        with self.connect() as conn:
            conn.execute('INSERT INTO notification_inbox(task_id,dedup_key,payload_hash) VALUES(%s,%s,%s) ON CONFLICT(task_id) DO NOTHING',
                         (event['taskId'], event['dedupKey'], digest))
            row = conn.execute('SELECT payload_hash,processed_at FROM notification_inbox WHERE task_id=%s FOR UPDATE',
                               (event['taskId'],)).fetchone()
            if row[0] != digest: raise ValueError('Payload identity collision')
            yield row[1] is not None
            if row[1] is None:
                conn.execute('UPDATE notification_inbox SET processed_at=now() WHERE task_id=%s', (event['taskId'],))


def callback(base, token):
    if len(token) < 32: raise ValueError('Internal token must have at least 32 characters')
    def complete(event, outcome):
        body = json.dumps(dict(dedupKey=event['dedupKey'], outcome=outcome)).encode()
        request = Request(f"{base.rstrip('/')}/internal/deliveries/{event['taskId']}/complete", body,
                          headers={'Authorization': f'Bearer {token}', 'Content-Type': 'application/json'}, method='POST')
        with urlopen(request, timeout=10) as response:
            if response.status != 204: raise OSError('Core completion refused')
    return complete


def smtp_delivery(event):
    message = EmailMessage()
    message['From'] = os.environ.get('SMTP_FROM', 'smartfix@localhost')
    message['To'] = event['email']
    message['Subject'] = event['title']
    identity = hashlib.sha256(event['dedupKey'].encode()).hexdigest()
    message['Message-ID'] = f'<{identity}@smartfix.local>'
    message.set_content(event['message'])
    with smtplib.SMTP(os.environ.get('SMTP_HOST', 'mailpit'), int(os.environ.get('SMTP_PORT', '1025')), timeout=10) as smtp:
        if os.environ.get('SMTP_STARTTLS', 'false').lower() == 'true': smtp.starttls()
        if os.environ.get('SMTP_USERNAME'): smtp.login(os.environ['SMTP_USERNAME'], os.environ['SMTP_PASSWORD'])
        smtp.send_message(message)


def consume_once(sqs, queue_url, processor):
    messages = sqs.receive_message(QueueUrl=queue_url, MaxNumberOfMessages=1, WaitTimeSeconds=20,
                                  VisibilityTimeout=120).get('Messages', [])
    for message in messages:
        # Only delete after callback and Worker database commit both succeed.
        processor.handle(validate_event(message['Body']))
        sqs.delete_message(QueueUrl=queue_url, ReceiptHandle=message['ReceiptHandle'])
    return len(messages)


def main():
    import boto3
    from botocore.config import Config
    from prometheus_client import Counter, Gauge, start_http_server
    logging.basicConfig(level=logging.INFO, format='%(asctime)s %(levelname)s %(message)s')
    stopped = threading.Event()
    for sig in (signal.SIGTERM, signal.SIGINT): signal.signal(sig, lambda *_: stopped.set())
    dsn = os.environ.get('WORKER_DB_URL')
    if not dsn:
        from psycopg.conninfo import make_conninfo
        dsn = make_conninfo(host=os.environ['WORKER_DB_HOST'], dbname=os.environ.get('WORKER_DB_NAME', 'notifications'),
                            user=os.environ['WORKER_DB_USER'], password=os.environ['WORKER_DB_PASSWORD'],
                            sslmode=os.environ.get('WORKER_DB_SSLMODE', 'require'))
    inbox = PostgresInbox(dsn)
    processor = Processor(inbox, callback(os.environ['CORE_URL'], os.environ['SMARTFIX_INTERNAL_TOKEN']), smtp_delivery)
    sqs = boto3.client('sqs', region_name=os.environ.get('AWS_REGION', 'ap-southeast-1'),
                       endpoint_url=os.environ.get('SQS_ENDPOINT') or None,
                       config=Config(connect_timeout=3, read_timeout=25, retries={'max_attempts': 2}))
    start_http_server(9090)
    received = Counter('smartfix_worker_messages', 'Acknowledged messages, including duplicates')
    failures = Counter('smartfix_worker_failures', 'Failed receives or processing attempts')
    heartbeat = Gauge('smartfix_worker_poll_timestamp', 'Most recent successful queue poll')
    while not stopped.is_set():
        try:
            count = consume_once(sqs, os.environ['SMARTFIX_QUEUE_URL'], processor)
            received.inc(count); heartbeat.set(time.time())
            if count: log.info('delivery_batch_acknowledged count=%s', count)
        except Exception as exc:
            failures.inc()
            # No body, address, token, DSN or exception message in logs.
            log.warning('delivery_attempt_failed failureType=%s', type(exc).__name__)
            stopped.wait(2)

if __name__ == '__main__': main()
