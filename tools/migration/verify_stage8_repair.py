"""Three-role real repair and photo round-trip on the isolated stage8 Core."""
import base64,json,secrets,urllib.request,urllib.parse,time
import verify_stage8 as v
def run():
    s=v.setup();accounts=s['accounts'];password=s['password'];h=v.http
    requester=h.login(accounts['author'],password);admin=h.login(accounts['admin'],password);tech=h.login(accounts['tech'],password)
    location=int(v.sql('SELECT min(id) FROM locations WHERE active;'))
    profile,_=h.page(tech,'/technician/profile')
    values=dict(skills='ELECTRICAL',serviceAreaIds=location,availabilityStatus='AVAILABLE')
    if profile.get('version'):values['version']=profile['version']
    v.post(tech,'/technician/profile','/technician/profile',values)
    assert int(v.sql('SELECT count(*) FROM technician_profiles WHERE user_id='+str(accounts['tech']['id'])+';'))==1
    f,_=h.page(requester,'/requests/new');boundary='Stage8Boundary'+secrets.token_hex(8)
    fields=dict(_csrf=f['_csrf'],locationId=location,title='Stage8 repair with image',description='Actual image and full repair lifecycle regression.',category='ELECTRICAL',urgencyLevel='LOW')
    # A tiny PNG fixture, not a historical attachment claim.
    png=base64.b64decode('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+jV1cAAAAASUVORK5CYII=')
    parts=[]
    for name,value in fields.items():parts.append(f'--{boundary}\r\nContent-Disposition: form-data; name="{name}"\r\n\r\n{value}\r\n'.encode())
    parts.append(f'--{boundary}\r\nContent-Disposition: form-data; name="files"; filename="stage8.png"\r\nContent-Type: image/png\r\n\r\n'.encode()+png+b'\r\n');parts.append(f'--{boundary}--\r\n'.encode())
    with requester.open(urllib.request.Request(h.CORE+'/requests',data=b''.join(parts),headers={'Content-Type':'multipart/form-data; boundary='+boundary}),timeout=15) as r:url=r.geturl();assert r.status==200
    ticket=url.rsplit('/',1)[1];assert ticket.startswith('SF-'),url
    v.post(admin,'/requests/'+ticket+'/review','/requests/'+ticket+'/review',dict(finalUrgencyLevel='LOW',comment='Reviewed by stage8 regression',reject='false'))
    v.post(admin,f'/admin/requests/{ticket}/dispatch',f'/admin/requests/{ticket}/assign',dict(technicianId=accounts['tech']['id'],finalUrgencyLevel='LOW'))
    order=int(v.sql("SELECT w.id FROM work_orders w JOIN maintenance_requests r ON r.id=w.request_id WHERE r.ticket_number='"+ticket+"';"))
    v.post(tech,f'/workorders/{order}',f'/workorders/{order}/accept',{})
    v.post(tech,f'/workorders/{order}',f'/workorders/{order}/records',dict(diagnosis='Loose electrical connection',actionTaken='Reconnected and tested',materialsUsed='None',minutesSpent=15))
    v.post(tech,f'/workorders/{order}',f'/workorders/{order}/complete',dict(resolutionNote='Electrical connection restored and tested.'))
    v.post(requester,'/requests/'+ticket,'/requests/'+ticket+'/confirm',{})
    v.post(admin,'/requests/'+ticket,'/requests/'+ticket+'/close',{})
    assert v.sql("SELECT status FROM maintenance_requests WHERE ticket_number='"+ticket+"';")=='CLOSED'
    request_id=int(v.sql("SELECT id FROM maintenance_requests WHERE ticket_number='"+ticket+"';"));end=time.time()+20;delivered=0
    while time.time()<end:
        delivered=int(v.sql(f"SELECT count(*) FROM delivery_tasks WHERE channel='IN_APP' AND status='DELIVERED' AND reference_id={request_id};"))
        if delivered:break
        time.sleep(1)
    assert delivered>0,'Repair notices must deliver automatically with mail disabled'
    attachment=int(v.sql("SELECT a.id FROM request_attachments a JOIN maintenance_requests r ON r.id=a.request_id WHERE r.ticket_number='"+ticket+"';"))
    with requester.open(h.CORE+f'/requests/{ticket}/attachments/{attachment}',timeout=10) as r:data=r.read();assert r.status==200 and data==png
    out={'threeRoleRepairClosed':True,'pngUploadAndAuthorizedRead':True,'automaticRepairInAppNotifications':delivered,'ticket':ticket,'attachmentId':attachment,'orderId':order}
    (v.R/'repair-results-private.json').write_text(json.dumps(out,indent=2));print(json.dumps(out))
if __name__=='__main__':run()
