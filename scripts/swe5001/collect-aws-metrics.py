#!/usr/bin/env python3
"""Read-only CloudWatch evidence export. Requires configured AWS credentials."""
import argparse,json
from datetime import datetime,timedelta,timezone
import boto3
p=argparse.ArgumentParser();p.add_argument('--stack',required=True);p.add_argument('--minutes',type=int,default=15);p.add_argument('--region',default='ap-southeast-1');args=p.parse_args()
session=boto3.Session(region_name=args.region)
stack=session.client('cloudformation').describe_stacks(StackName=args.stack)['Stacks'][0]
out={x['OutputKey']:x['OutputValue'] for x in stack['Outputs']}
sqs=session.client('sqs');cloudwatch=session.client('cloudwatch');queries=[]
for service in ['CoreService','WorkerService']:
    dims=[{'Name':'ClusterName','Value':out['Cluster']},{'Name':'ServiceName','Value':out[service]}]
    for name in ['CPUUtilization','MemoryUtilization']:
        queries.append({'Id':f'm{len(queries)}','MetricStat':{'Metric':{'Namespace':'AWS/ECS','MetricName':name,'Dimensions':dims},'Period':60,'Stat':'Average'},'Label':f'{service}.{name}'})
    queries.append({'Id':f'm{len(queries)}','MetricStat':{'Metric':{'Namespace':'ECS/ContainerInsights','MetricName':'RunningTaskCount','Dimensions':dims},'Period':60,'Stat':'Average'},'Label':f'{service}.RunningTaskCount'})
for queue in ['QueueUrl','DlqUrl']:
    name=sqs.get_queue_attributes(QueueUrl=out[queue],AttributeNames=['QueueArn'])['Attributes']['QueueArn'].split(':')[-1]
    for metric in ['ApproximateNumberOfMessagesVisible','ApproximateNumberOfMessagesNotVisible','ApproximateAgeOfOldestMessage']:
        queries.append({'Id':f'm{len(queries)}','MetricStat':{'Metric':{'Namespace':'AWS/SQS','MetricName':metric,'Dimensions':[{'Name':'QueueName','Value':name}]},'Period':60,'Stat':'Maximum'},'Label':f'{queue}.{metric}'})
end=datetime.now(timezone.utc);result=cloudwatch.get_metric_data(MetricDataQueries=queries,StartTime=end-timedelta(minutes=args.minutes),EndTime=end)
print(json.dumps({'start':end-timedelta(minutes=args.minutes),'end':end,'metrics':result['MetricDataResults']},default=str,indent=2))
