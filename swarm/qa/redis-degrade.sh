#!/usr/bin/env bash
# SW-T06 测试3c:Redis 停机降级(仅 docker stop/start fusion-redis,测完必须恢复)
set -uo pipefail
cd "$(dirname "$0")"
BASE=http://localhost:8081
TOKEN=$(curl -s -X POST $BASE/api/auth/login -H "Content-Type: application/json" -d '{"username":"zhangyz","password":"zhangyz"}' | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p')
AUTH="Authorization: Bearer $TOKEN"

vq()  { curl -s -o /tmp/vq.json -w '%{http_code}' -H "$AUTH" $BASE/api/system/status/video-queue; }
hp()  { curl -s -o /tmp/hp.json -w '%{http_code}' -H "$AUTH" $BASE/api/system/status/health; }
st()  { curl -s -o /tmp/st.json -w '%{http_code}' -H "$AUTH" $BASE/api/system/status/storage; }

restore() {
  echo "[restore] docker start fusion-redis"
  docker start fusion-redis >/dev/null 2>&1
  for i in $(seq 1 30); do
    H=$(docker inspect -f '{{.State.Health.Status}}' fusion-redis 2>/dev/null)
    [ "$H" = "healthy" ] && break
    sleep 1
  done
  echo "[restore] redis health=$H"
}
trap restore EXIT

echo "== 基线(Redis 正常) =="
echo "video-queue HTTP $(vq) $(head -c 200 /tmp/vq.json)"
echo "health      HTTP $(hp) $(head -c 160 /tmp/hp.json)"
echo "storage     HTTP $(st) exists=$(node -e "console.log(JSON.parse(require('fs').readFileSync('/tmp/st.json')).data.exists)" 2>/dev/null)"

echo "== docker stop fusion-redis =="
docker stop fusion-redis >/dev/null
sleep 2
echo "video-queue HTTP $(vq) $(cat /tmp/vq.json)"
echo "health      HTTP $(hp) $(cat /tmp/hp.json)"
echo "storage     HTTP $(st) exists=$(node -e "console.log(JSON.parse(require('fs').readFileSync('/tmp/st.json')).data.exists)" 2>/dev/null)"
echo "login(无Redis) -> $(curl -s -o /tmp/lg.json -w '%{http_code}' -X POST $BASE/api/auth/login -H 'Content-Type: application/json' -d '{"username":"uitest","password":"Uitest#2026"}') $(head -c 60 /tmp/lg.json)"

echo "== docker start fusion-redis(恢复) =="
restore
sleep 1
echo "video-queue HTTP $(vq) $(cat /tmp/vq.json)"
echo "health      HTTP $(hp) $(head -c 120 /tmp/hp.json)"
echo "== 完成,Redis 已恢复 =="
