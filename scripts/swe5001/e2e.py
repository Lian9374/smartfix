#!/usr/bin/env python3
"""Real browser-form workflow through gateway, including CSRF and distributed sessions.
Uses only local/demo accounts. Set BASE_URL and DEMO_ADMIN_PASSWORD for an approved demo.
"""
import html
import base64
import http.cookiejar
import json
import os
import re
import time
from pathlib import Path
from urllib.error import HTTPError
from urllib.parse import urlencode, urlparse
from urllib.request import build_opener, HTTPCookieProcessor, Request

BASE = os.environ.get('BASE_URL', 'http://127.0.0.1:8080').rstrip('/')
PASSWORD = os.environ.get('DEMO_USER_PASSWORD', 'SmartFixUser2!')
INITIAL = 'SmartFixInitial1!'

class Browser:
    def __init__(self):
        self.opener = build_opener(HTTPCookieProcessor(http.cookiejar.CookieJar()))
        self.upstreams=set()
    def request(self, path, data=None, headers=None):
        payload = data if isinstance(data, bytes) else None if data is None else urlencode(data,doseq=True).encode()
        request=Request(BASE+path, payload, headers=headers or {})
        try: response=self.opener.open(request,timeout=30)
        except HTTPError as exc: response=exc
        body=response.read().decode()
        upstream=response.headers.get('X-SmartFix-Upstream')
        if upstream:self.upstreams.add(upstream)
        return response.status, body, urlparse(response.url).path
    def get(self,path):
        status,body,_=self.request(path)
        assert status==200, f'GET {path} -> {status}'
        return body
    def post(self,path,data,form=None,allowed=(200,)):
        page=self.get(form or path)
        token=re.search(r'name="_csrf"[^>]*value="([^"]+)"',page)
        assert token,f'No CSRF token: {path}'
        status,body,target=self.request(path, data | {'_csrf':html.unescape(token[1])})
        assert status in allowed, f'POST {path} -> {status} ({target})'
        return body,target
    def upload_request(self, fields):
        page=self.get('/requests/new')
        token=re.search(r'name="_csrf"[^>]*value="([^"]+)"',page)[1]
        boundary='SmartFixBoundary'+str(time.time_ns())
        parts=[]
        for key,value in (fields | {'_csrf':html.unescape(token)}).items():
            parts.append(f'--{boundary}\r\nContent-Disposition: form-data; name="{key}"\r\n\r\n{value}\r\n'.encode())
        self.image=base64.b64decode('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jRZkAAAAASUVORK5CYII=')
        parts.append(f'--{boundary}\r\nContent-Disposition: form-data; name="files"; filename="evidence.png"\r\nContent-Type: image/png\r\n\r\n'.encode()+self.image+b'\r\n')
        parts.append(f'--{boundary}--\r\n'.encode())
        status,body,path=self.request('/requests',b''.join(parts),{'Content-Type':f'multipart/form-data; boundary={boundary}'})
        assert status==200 and path.startswith('/requests/SF-'),f'Attachment submission failed ({status}, {path})'
        return body,path
    def login(self,username,password,role):
        body,target=self.post('/login',dict(username=username,password=password,accountType=role))
        assert target!='/login',f'Login refused: {username}'
        return target


def main():
    admin=Browser();admin.login('demo.admin',os.environ.get('DEMO_ADMIN_PASSWORD','SmartFixDemo1!'),'ADMINISTRATOR')
    for username,role in [('swe5001.requester','REQUESTER'),('swe5001.tech','TECHNICIAN')]:
        admin.post('/admin/users',dict(username=username,displayName=username,password=INITIAL,role=role),allowed=(200,409))
    requester=Browser();tech=Browser()
    for browser,username,role in [(requester,'swe5001.requester','REQUESTER'),(tech,'swe5001.tech','TECHNICIAN')]:
        try: browser.login(username,PASSWORD,role)
        except AssertionError:
            browser.login(username,INITIAL,role)
            browser.post('/account/password',dict(currentPassword=INITIAL,newPassword=PASSWORD,confirmPassword=PASSWORD))
            browser.login(username,PASSWORD,role)
    form=requester.get('/requests/new')
    match=re.search(r'<option[^>]*value="(\d+)"',form)
    assert match,'No active location'
    location=match[1]
    profile=tech.get('/technician/profile')
    version=re.search(r'name="version"[^>]*value="([^"]*)"',profile)
    values=dict(skills='ELECTRICAL',serviceAreaIds=location,availabilityStatus='AVAILABLE')
    if version and version[1]:values['version']=version[1]
    tech.post('/technician/profile',values)
    title='SWE5001 E2E '+str(time.time_ns())
    _,path=requester.upload_request(dict(locationId=location,title=title,description='Repeatable distributed notification demonstration',category='ELECTRICAL',urgencyLevel='MEDIUM'))
    ticket=path.rsplit('/',1)[-1]
    assert ticket.startswith('SF-'),f'Submission did not create ticket: {path}'
    admin.post(f'/requests/{ticket}/review',dict(finalUrgencyLevel='MEDIUM',reject='false',comment='SWE5001 smoke'))
    dispatch=admin.get(f'/admin/requests/{ticket}/dispatch')
    # Each radio sits inside a label containing the candidate username.
    candidates=re.findall(r'<label class="engineer-option[\s\S]*?</label>',dispatch)
    chosen=next((block for block in candidates if 'swe5001.tech' in block),None)
    assert chosen,'Demo technician missing from dispatch candidates'
    technician=re.search(r'name="technicianId"[^>]*value="(\d+)"',chosen)[1]
    admin.post(f'/admin/requests/{ticket}/assign',dict(technicianId=technician,finalUrgencyLevel='MEDIUM'),form=f'/admin/requests/{ticket}/dispatch')
    mine=tech.get('/workorders/mine')
    # Locate the work order by its request ticket in the corresponding row.
    blocks=re.findall(r'<(?:tr|article)[\s\S]*?</(?:tr|article)>',mine)
    block=next((b for b in blocks if ticket in b),mine)
    orders=re.findall(r'href="/workorders/(\d+)"',block)
    assert orders,'No assigned work order'
    order=orders[0]
    tech.post(f'/workorders/{order}/accept',{},form=f'/workorders/{order}')
    tech.post(f'/workorders/{order}/records',dict(diagnosis='Loose electrical connection',actionTaken='Secured and verified the connection',minutesSpent='15'),form=f'/workorders/{order}')
    completion,_=tech.post(f'/workorders/{order}/complete',dict(resolutionNote='Verified repair through SWE5001 distributed demo'),form=f'/workorders/{order}')
    assert 'Solution submitted' in completion, 'Work order completion was refused'
    deadline=time.monotonic()+60
    while time.monotonic()<deadline:
        notifications=requester.get('/notifications')
        if f'Maintenance work for request {ticket} has been completed.' in notifications:break
        time.sleep(1)
    else:raise AssertionError('Worker did not project completion notification within 60 seconds')
    detail=requester.get(f'/requests/{ticket}')
    attachment=re.search(r'href="(/requests/[^" ]+/attachments/\d+)"',detail)
    assert attachment,'Uploaded evidence is missing'
    for _ in range(10):
        requester.get('/requests/mine')
        with requester.opener.open(BASE+attachment[1],timeout=10) as response:
            assert response.read()==requester.image,'Shared attachment is inconsistent across replicas'
            requester.upstreams.add(response.headers.get('X-SmartFix-Upstream'))
    expected=int(os.environ.get('EXPECTED_CORE_COUNT','1'))
    assert len(requester.upstreams)>=expected,f'Expected {expected} Core instances, saw {requester.upstreams}'
    anonymous=Browser()
    assert anonymous.request('/internal/deliveries/1/complete')[0]==403
    assert anonymous.request('/actuator/prometheus')[0]==403
    record=dict(timestamp=time.strftime('%Y-%m-%dT%H:%M:%SZ',time.gmtime()),ticket=ticket,workOrder=order,
                result='PASS',sharedAttachment='PASS',coreUpstreams=sorted(requester.upstreams),workflow='submit -> review -> assign -> accept -> complete -> async notification')
    Path('results').mkdir(exist_ok=True)
    Path('results/e2e-latest.json').write_text(json.dumps(record,indent=2)+'\n')
    print(json.dumps(record,indent=2))

if __name__=='__main__':main()
