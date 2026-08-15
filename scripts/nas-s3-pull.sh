#!/bin/sh
set -eu

: "${S3_BUCKET:?S3_BUCKET is required}"
: "${NAS_BACKUP_DIR:?NAS_BACKUP_DIR is required}"
S3_PREFIX="${S3_PREFIX:-private}"

mkdir -p "${NAS_BACKUP_DIR}"
aws s3 sync "s3://${S3_BUCKET}/${S3_PREFIX}/" "${NAS_BACKUP_DIR}/" \
  --region "${AWS_REGION:-ap-northeast-2}" \
  --only-show-errors

date -Iseconds > "${NAS_BACKUP_DIR}/.last-successful-s3-pull"
