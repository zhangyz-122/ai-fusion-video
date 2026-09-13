#!/usr/bin/env bash
# SW-T06 探测:8081 平台端点存在性(登录+端点探测)
set -uo pipefail
BASE=http://localhost:8081
login() { curl -s -X POST "$BASE/api/auth/login" -H "Content-Type: application/json" -d "{\"username\":\"$1\",\"password\":\"$2\"}"; }
ADMIN_TOKEN=$(login zhangyz zhangyz | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p')
USER_TOKEN=$(login uitest 'Uitest#2026' | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p')
echo "ADMIN_TOKEN len=${#ADMIN_TOKEN} USER_TOKEN len=${#USER_TOKEN}"
for ep in /api/system/status/health /api/system/status/storage /api/system/status/video-queue; do
  echo "-- admin $ep -> $(curl -s -o /tmp/b -w '%{http_code}' -H "Authorization: Bearer $ADMIN_TOKEN" "$BASE$ep") $(head -c 200 /tmp/b)"
  echo "-- uitest $ep -> $(curl -s -o /tmp/b -w '%{http_code}' -H "Authorization: Bearer $USER_TOKEN" "$BASE$ep") $(head -c 200 /tmp/b)"
done
