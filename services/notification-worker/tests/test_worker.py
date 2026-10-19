import copy
import json
import unittest
from contextlib import contextmanager
from worker import validate_event, Processor

EVENT = dict(schemaVersion=1, eventType='notification.requested.v1', taskId=1,
             dedupKey='event:1', recipientId=7, channel='IN_APP', notificationType='REQUEST_STATUS_CHANGED',
             title='Updated', message='Request resolved', referenceId=9, email=None, occurredAt='2026-10-19T08:00:00Z')

class MemoryInbox:
    def __init__(self): self.rows = {}
    @contextmanager
    def claim(self, event, digest):
        before = copy.deepcopy(self.rows)
        identity = str(event['taskId'])
        row = self.rows.setdefault(identity, dict(digest=digest, done=False))
        if row['digest'] != digest: raise ValueError('Payload identity collision')
        try: yield row['done']
        except Exception:
            self.rows = before
            raise
        else: row['done'] = True

class WorkerTests(unittest.TestCase):
    def test_unknown_schema_and_wrong_types_are_rejected(self):
        for patch in [dict(schemaVersion=2), dict(taskId=True), dict(taskId=-1), dict(channel='SMS'),
                      dict(message='x'*1001), dict(dedupKey=''), dict(title=None), dict(occurredAt='bad'),
                      dict(email='a@example.com\r\nBcc: x@example.com')]:
            with self.subTest(patch=patch), self.assertRaises(ValueError):
                validate_event(json.dumps(EVENT | patch))
    def test_duplicate_message_projects_once(self):
        inbox = MemoryInbox(); effects = []
        processor = Processor(inbox, lambda event, outcome: effects.append((event['taskId'], outcome)), lambda e: None)
        processor.handle(EVENT)
        processor.handle(EVENT)
        self.assertEqual([(1, 'DELIVERED')], effects)
        self.assertTrue(inbox.rows['1']['done'])
    def test_failed_callback_is_retryable(self):
        inbox = MemoryInbox()
        def unavailable(e, outcome): raise OSError('Core unavailable')
        processor = Processor(inbox, unavailable, lambda e: None)
        with self.assertRaises(OSError): processor.handle(EVENT)
        effects = []
        Processor(inbox, lambda e, outcome: effects.append(outcome), lambda e: None).handle(EVENT)
        self.assertEqual(['DELIVERED'], effects)
    def test_conflicting_payload_does_not_project(self):
        inbox = MemoryInbox(); effects=[]
        p = Processor(inbox, lambda e,o: effects.append(o), lambda e: None)
        p.handle(EVENT)
        with self.assertRaises(ValueError): p.handle(EVENT | dict(message='tampered'))
        self.assertEqual(['DELIVERED'], effects)
    def test_email_without_address_is_explicitly_skipped(self):
        outcomes=[]
        p=Processor(MemoryInbox(), lambda e,o: outcomes.append(o), lambda e: self.fail('No address'))
        p.handle(EVENT | dict(channel='EMAIL'))
        self.assertEqual(['SKIPPED'],outcomes)
    def test_smtp_failure_does_not_complete_delivery(self):
        inbox=MemoryInbox(); outcomes=[]
        def broken(e): raise OSError('SMTP unavailable')
        p=Processor(inbox,lambda e,o:outcomes.append(o),broken)
        with self.assertRaises(OSError): p.handle(EVENT | dict(channel='EMAIL',email='a@example.com'))
        self.assertEqual([],outcomes)
        self.assertEqual({},inbox.rows)

if __name__ == '__main__': unittest.main()
