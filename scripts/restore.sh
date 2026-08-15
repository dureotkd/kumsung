#!/bin/sh
set -eu
if [ "$#" -ne 2 ]; then
  echo "usage: restore.sh /backups/database-TIMESTAMP.dump /backups/uploads-TIMESTAMP.tar.gz" >&2
  exit 1
fi
pg_restore -h postgres -U kumsung -d kumsung_enc --clean --if-exists "$1"
restore_dir="/restore/$(date -u +%Y%m%dT%H%M%SZ)"
mkdir -p "$restore_dir"
tar -xzf "$2" -C "$restore_dir"
echo "Restore completed. Copy $restore_dir/uploads into the uploads volume after validation."
