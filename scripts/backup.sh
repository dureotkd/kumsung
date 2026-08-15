#!/bin/sh
set -eu
stamp="$(date -u +%Y%m%dT%H%M%SZ)"
mkdir -p /backups
pg_dump -h postgres -U kumsung -d kumsung_enc -Fc -f "/backups/database-${stamp}.dump"
tar -czf "/backups/uploads-${stamp}.tar.gz" -C /source uploads
find /backups -type f -mtime "+${BACKUP_RETENTION_DAYS:-30}" -delete
