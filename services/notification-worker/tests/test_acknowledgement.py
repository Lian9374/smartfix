import json
import unittest
from worker import consume_once
from test_worker import EVENT

class Queue:
    def __init__(self):self.deleted=[]
    def receive_message(self,**kw):return {'Messages':[{'Body':json.dumps(EVENT),'ReceiptHandle':'receipt'}]}
    def delete_message(self,**kw):self.deleted.append(kw['ReceiptHandle'])

class AcknowledgementTests(unittest.TestCase):
    def test_does_not_acknowledge_uncommitted_processing(self):
        q=Queue()
        class FailingProcessor:
            def handle(self,event):raise OSError('Database commit failed')
        with self.assertRaises(OSError):consume_once(q,'queue',FailingProcessor())
        self.assertEqual([],q.deleted)
    def test_acknowledges_after_processing_returns(self):
        q=Queue();sequence=[]
        class Processor:
            def handle(self,event):
                self.assertion=None
                sequence.append('committed')
        consume_once(q,'queue',Processor())
        self.assertEqual(['committed'],sequence)
        self.assertEqual(['receipt'],q.deleted)
