"""Loopback-only round-robin proxy. Never retries an uncertain POST on another backend."""
import http.client,json,threading,time
from http.server import ThreadingHTTPServer,BaseHTTPRequestHandler
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2];R=ROOT/'.migration-runtime/stage8'
BACKENDS=(19102,19103);healthy=set();lock=threading.Lock();counter=0
def probe():
    while True:
        live=set()
        for port in BACKENDS:
            try:
                c=http.client.HTTPConnection('127.0.0.1',port,timeout=.5);c.request('GET','/actuator/health');r=c.getresponse()
                if r.status==200 and json.loads(r.read()).get('status')=='UP':live.add(port)
                c.close()
            except Exception:pass
        with lock:healthy.clear();healthy.update(live)
        time.sleep(.5)
class Handler(BaseHTTPRequestHandler):
    def log_message(self,*args):pass
    def run_request(self):
        global counter
        if self.path=='/actuator/health':
            with lock:body=json.dumps({'status':'UP' if healthy else 'DOWN','backends':sorted(healthy)}).encode()
            self.send_response(200 if healthy else 503);self.send_header('Content-Type','application/json');self.end_headers();self.wfile.write(body);return
        if not self.path.startswith('/internal/community/v1/'):
            self.send_error(404);return
        with lock:
            choices=sorted(healthy)
            if choices:port=choices[counter%len(choices)];counter+=1
            else:port=None
        if port is None:self.send_error(503);return
        if 'chunked' in self.headers.get('Transfer-Encoding','').lower():
            chunks=[];total=0
            while True:
                size=int(self.rfile.readline().split(b';',1)[0].strip(),16)
                if size==0:
                    while self.rfile.readline() not in (b'\r\n',b'\n',b''):pass
                    break
                total+=size
                if total>1048576:self.send_error(413);return
                chunks.append(self.rfile.read(size));assert self.rfile.read(2)==b'\r\n'
            body=b''.join(chunks)
        else:body=self.rfile.read(int(self.headers.get('Content-Length',0)))
        headers={k:v for k,v in self.headers.items() if k.lower() not in ('host','connection','content-length','transfer-encoding')}
        try:
            c=http.client.HTTPConnection('127.0.0.1',port,timeout=4);c.request(self.command,self.path,body,headers);r=c.getresponse();data=r.read()
            self.send_response(r.status)
            for k,v in r.getheaders():
                if k.lower() not in ('connection','transfer-encoding','content-length'):self.send_header(k,v)
            self.send_header('Content-Length',str(len(data)));self.end_headers();self.wfile.write(data);c.close()
        except Exception:
            with lock:healthy.discard(port)
            self.send_error(503)
    do_GET=run_request;do_POST=run_request
if __name__=='__main__':
    assert Path.cwd().resolve()==ROOT
    threading.Thread(target=probe,daemon=True).start();ThreadingHTTPServer(('127.0.0.1',19104),Handler).serve_forever()
