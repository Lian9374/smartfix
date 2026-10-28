#!/bin/bash
set -euo pipefail

GET_CONFIG=/opt/elasticbeanstalk/bin/get-config
fail() { printf 'SmartFix EFS: %s\n' "$1" >&2; exit 1; }

FS_ID="$("$GET_CONFIG" environment -k SMARTFIX_EFS_ID)"
AP_ID="$("$GET_CONFIG" environment -k SMARTFIX_EFS_ACCESS_POINT)"
[[ "$FS_ID" =~ ^fs-[0-9a-f]+$ ]] || fail 'Set SMARTFIX_EFS_ID to the external EFS filesystem ID.'
[[ "$AP_ID" =~ ^fsap-[0-9a-f]+$ ]] || fail 'Set SMARTFIX_EFS_ACCESS_POINT to its access point ID.'

MOUNT_DIR=/mnt/smartfix-uploads
STATE_DIR=/var/lib/smartfix
STATE_FILE="$STATE_DIR/efs-identity"
EXPECTED_IDENTITY="${FS_ID}|${AP_ID}"

id webapp >/dev/null 2>&1 || fail 'Expected the Beanstalk Java SE webapp user.'
if ! command -v mount.efs >/dev/null 2>&1; then
  dnf install -y amazon-efs-utils
fi

# The EFS access point enforces UID/GID 10001; group write is required on its
# root (owner 10001:10001, mode 0770). Do not chmod/chown the mounted root.
if getent group 10001 >/dev/null; then
  UPLOAD_GROUP="$(getent group 10001 | cut -d: -f1)"
else
  groupadd --gid 10001 smartfixuploads
  UPLOAD_GROUP=smartfixuploads
fi
usermod -a -G "$UPLOAD_GROUP" webapp

mkdir -p "$MOUNT_DIR" "$STATE_DIR"
chmod 0700 "$STATE_DIR"
if mountpoint -q "$MOUNT_DIR"; then
  [[ -f "$STATE_FILE" ]] || fail 'An unmanaged mount already exists at the upload directory.'
  [[ "$(cat "$STATE_FILE")" == "$EXPECTED_IDENTITY" ]] \
    || fail 'EFS IDs changed. Perform a planned migration; the existing mount was not replaced.'
else
  # Fail deployment rather than silently storing private attachments on local disk.
  mount -t efs -o "tls,iam,accesspoint=${AP_ID}" "${FS_ID}:/" "$MOUNT_DIR"
  printf '%s\n' "$EXPECTED_IDENTITY" > "$STATE_FILE"
  chmod 0600 "$STATE_FILE"
fi
mountpoint -q "$MOUNT_DIR" || fail 'Upload directory is not mounted.'
runuser -u webapp -- sh -c \
  'f=$(mktemp /mnt/smartfix-uploads/.writecheck.XXXXXX) && rm -- "$f"'
printf 'SmartFix EFS: mounted and writable by the application.\n'
