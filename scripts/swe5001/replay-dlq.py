#!/usr/bin/env python3
"""Operator-controlled DLQ replay after fixing the processing failure; messages retain identity."""
import argparse
import boto3
p=argparse.ArgumentParser();p.add_argument('--source',required=True,help='DLQ URL');p.add_argument('--destination',required=True,help='Original queue URL');p.add_argument('--limit',type=int,default=10);p.add_argument('--region',default='ap-southeast-1');args=p.parse_args()
sqs=boto3.client('sqs',region_name=args.region)
for _ in range(args.limit):
    messages=sqs.receive_message(QueueUrl=args.source,MaxNumberOfMessages=1,WaitTimeSeconds=1,VisibilityTimeout=60).get('Messages',[])
    if not messages:break
    message=messages[0]
    sqs.send_message(QueueUrl=args.destination,MessageBody=message['Body'])
    sqs.delete_message(QueueUrl=args.source,ReceiptHandle=message['ReceiptHandle'])
    print('Replayed one message; payload omitted.')
