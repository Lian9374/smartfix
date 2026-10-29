"""Safety and open-arrival semantics; no network or real business pressure."""
import threading,time,unittest
from perf9 import target,arrivals

class PerformanceSafetyTest(unittest.TestCase):
    def test_protected_and_remote_targets_rejected(self):
        for url in ['http://127.0.0.1:19090','http://127.0.0.1:19101','http://127.0.0.1:8080','http://localhost:19111','https://127.0.0.1:19111','http://aws.example:19111','http://127.0.0.1:19111@evil.example:19111','http://127.0.0.1:19111.evil.example:19111']:
            with self.subTest(url=url),self.assertRaises((AssertionError,ValueError)):target(url)
    def test_exact_sandbox_targets_accepted(self):
        for port in [19111,19112,19113,19114,19115]:self.assertEqual(target(f'http://127.0.0.1:{port}/x'),f'http://127.0.0.1:{port}/x')
    def test_slow_workers_drop_arrivals_instead_of_queueing(self):
        class Fake:
            local=threading.local()
            def operation(self,*args):self.local.requests=[];time.sleep(.12);return 'fake'
        r=arrivals(Fake(),'api',50,.2,1,workers=1)
        self.assertEqual(r['plannedIterations'],10)
        self.assertEqual(r['startedIterations']+r['dropped_iterations'],10)
        self.assertGreater(r['dropped_iterations'],0)
        self.assertLessEqual(r['startedIterations'],2)
    def test_failed_content_is_not_successful_throughput(self):
        from perf9 import Failure
        class Fake:
            local=threading.local()
            def operation(self,*args):self.local.requests=[];raise Failure('content')
        r=arrivals(Fake(),'api',10,.1,1)
        self.assertEqual(r['successfulBusinessIterations'],0)
        self.assertEqual(r['errors'],{'content':1})
if __name__=='__main__':unittest.main()
