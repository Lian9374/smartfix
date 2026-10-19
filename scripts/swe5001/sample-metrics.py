#!/usr/bin/env python3
import csv,json,subprocess,sys,time
from urllib.request import urlopen
out=csv.writer(sys.stdout);out.writerow(['utc','configured_core_count','container','cpu_percent','memory','queue_visible','queue_inflight'])
while True:
    rows=subprocess.check_output(['docker','stats','--no-stream','--format','{{json .}}'],text=True)
    try:
        request=__import__('urllib.request',fromlist=['Request']).Request('http://127.0.0.1:9324/',
          json.dumps({'QueueUrl':'http://sqs:9324/000000000000/smartfix-notifications','AttributeNames':['ApproximateNumberOfMessages','ApproximateNumberOfMessagesNotVisible']}).encode(),
          headers={'X-Amz-Target':'AmazonSQS.GetQueueAttributes','Content-Type':'application/x-amz-json-1.0'})
        attrs=json.load(urlopen(request,timeout=3)).get('Attributes',{})
    except Exception:attrs={}
    for row in rows.splitlines():
        data=json.loads(row)
        if data['Name'].startswith('smartfix-swe5001-'):
            out.writerow([time.strftime('%Y-%m-%dT%H:%M:%SZ',time.gmtime()),sys.argv[1],data['Name'],data['CPUPerc'],data['MemUsage'],attrs.get('ApproximateNumberOfMessages',''),attrs.get('ApproximateNumberOfMessagesNotVisible','')])
    sys.stdout.flush();time.sleep(3)
