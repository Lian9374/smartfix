"""Stage9/10 only. Reuse tested proxy framing; explicit three-backend allowlist.
No retry of an uncertain write. Metrics do not contain bodies, tokens, users or query text.
"""
import json,threading,time
from http.server import ThreadingHTTPServer
import stage8_lb as proxy
proxy.BACKENDS=(19112,19113,19115)
counts={};mutex=threading.Lock()
class Handler(proxy.Handler):
    def run_request(self):
        if self.path=='/internal/perf/lb':
            # Local observability listener is NOT routed outside this loopback-only proxy.
            with mutex:body=json.dumps(counts).encode()
            self.send_response(200);self.end_headers();self.wfile.write(body);return
        started=time.monotonic();self.backend=None;self.result=None
        try:super().run_request()
        finally:
            key=str(self.backend or 'none')+':'+str(self.result)
            with mutex:
                entry=counts.setdefault(key,{'requests':0,'durationSeconds':0});entry['requests']+=1;entry['durationSeconds']+=time.monotonic()-started
    def send_header(self,key,value):
        if key.lower()=='x-community-instance':self.backend=value
        super().send_header(key,value)
    def send_response(self,code,message=None):self.result=code;super().send_response(code,message)
    do_GET=run_request;do_POST=run_request
if __name__=='__main__':
    assert proxy.Path.cwd().resolve()==proxy.ROOT
    threading.Thread(target=proxy.probe,daemon=True).start()
    ThreadingHTTPServer(('127.0.0.1',19114),Handler).serve_forever()
