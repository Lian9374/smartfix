#!/bin/bash
set -euo pipefail

GET_CONFIG=/opt/elasticbeanstalk/bin/get-config
fail() { printf 'SmartFix deployment: %s\n' "$1" >&2; exit 1; }
read_property() { "$GET_CONFIG" environment -k "$1"; }

DB_URL="$(read_property DB_URL)"
[[ "$DB_URL" == jdbc:postgresql://* ]] || fail 'Set DB_URL to the RDS PostgreSQL JDBC URL.'
[[ "$DB_URL" == *sslmode=verify-full* ]] || fail 'DB_URL must enable sslmode=verify-full.'
[[ "$DB_URL" == *sslrootcert=/var/app/current/certs/rds-global-bundle.pem* ]] \
  || fail 'DB_URL must reference /var/app/current/certs/rds-global-bundle.pem.'
[[ "$DB_URL" != *localhost* && "$DB_URL" != *127.0.0.1* ]] \
  || fail 'Production DB_URL must point to RDS, not this instance.'

test -s certs/rds-global-bundle.pem || fail 'RDS public CA bundle is missing.'
test -s config/application-prod.yml || fail 'Production configuration is missing.'
# DB_USERNAME and DB_PASSWORD can be environment secrets. Do not read or log them here.
printf 'SmartFix deployment: production configuration checks passed.\n'
