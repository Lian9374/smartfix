"""Safety and open-arrival semantics; no network or real business pressure."""
import threading,time,unittest
from perf9 import target,arrivals

class PerformanceSafetyTest(unittest.TestCase):
    def page_driver(self,post_path):
        from perf9 import Driver,CORE
        from unittest.mock import Mock
        d=Driver.__new__(Driver);d.local=threading.local();d.sessions={1000000:object()};d.session_locks={1000000:threading.Lock()}
        page='<h1>Perf9 question 0</h1><input name="_csrf" value="test"><input name="communityCommandKey" value="key">'
        d.request=Mock(side_effect=[(page,CORE+'/community/questions/1000000'),('<textarea>Perf9 runtime unit-marker</textarea>',CORE+post_path)])
        return d
    def test_200_form_error_is_not_committed_page_success(self):
        from unittest.mock import patch
        from perf9 import Failure
        d=self.page_driver('/community/questions/1000000/answers')
        with patch('perf9.uuid.uuid4',return_value='unit-marker'),self.assertRaises(Failure) as e:d.operation('page',0,1,force_write=True)
        self.assertEqual(e.exception.kind,'content')
    def test_committed_thread_redirect_is_page_success(self):
        from unittest.mock import patch
        d=self.page_driver('/community/questions/1000000')
        with patch('perf9.uuid.uuid4',return_value='unit-marker'):self.assertEqual(d.operation('page',0,1,force_write=True),'answer')
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
