"""One-off ECS task: create isolated application databases/users using RDS master secret."""
import json
import os
import boto3
import psycopg
from psycopg import sql

secrets = boto3.client('secretsmanager')
def read(arn): return json.loads(secrets.get_secret_value(SecretId=arn)['SecretString'])
master = read(os.environ['MASTER_SECRET_ARN'])
with psycopg.connect(host=os.environ['DB_HOST'], dbname='postgres', user=master['username'],
                     password=master['password'], sslmode='require', autocommit=True) as conn:
    for secret_arn, database in [(os.environ['CORE_SECRET_ARN'], 'smartfix'), (os.environ['WORKER_SECRET_ARN'], 'notifications')]:
        credential = read(secret_arn)
        role = credential['username']
        if not conn.execute('SELECT 1 FROM pg_roles WHERE rolname=%s', (role,)).fetchone():
            conn.execute(sql.SQL('CREATE ROLE {} LOGIN PASSWORD {}').format(sql.Identifier(role), sql.Literal(credential['password'])))
        if not conn.execute('SELECT 1 FROM pg_database WHERE datname=%s', (database,)).fetchone():
            conn.execute(sql.SQL('CREATE DATABASE {} OWNER {}').format(sql.Identifier(database), sql.Identifier(role)))
        conn.execute(sql.SQL('REVOKE CONNECT ON DATABASE {} FROM PUBLIC').format(sql.Identifier(database)))
        conn.execute(sql.SQL('GRANT CONNECT ON DATABASE {} TO {}').format(sql.Identifier(database), sql.Identifier(role)))
print('Application databases initialized; no credentials printed.')
