#!/usr/bin/env bash
set -euo pipefail

HOST="127.0.0.1"
PORT="9002"
STREAM="summary:2"
GROUP="summary-cg"
CONSUMER="consumer-ack"
BLOCK_MS=1000
COUNT=${1:-100}

# 그룹이 없으면 생성
if ! redis-cli -h "$HOST" -p "$PORT" XINFO GROUPS "$STREAM" >/dev/null 2>&1; then
  echo "Creating group $GROUP on $STREAM"
  redis-cli -h "$HOST" -p "$PORT" XGROUP CREATE "$STREAM" "$GROUP" 0 MKSTREAM >/dev/null
fi

echo "Start consuming with ACK: stream=$STREAM group=$GROUP consumer=$CONSUMER count=$COUNT"

while true; do
  RESP=$(redis-cli -h "$HOST" -p "$PORT" \
    XREADGROUP GROUP "$GROUP" "$CONSUMER" COUNT "$COUNT" BLOCK "$BLOCK_MS" STREAMS "$STREAM" ">")

  # 응답이 없으면 계속
  if [[ -z "$RESP" ]]; then
    continue
  fi

  # redis-cli 기본 출력 파싱: id와 필드 추출
  # 예: 1) "stream:2" 2) 1) 1) "1767...-0" 2) 1) "field" 2) "value" ...
  echo "$RESP" | awk 'BEGIN{id=""} {
    if ($1 ~ /^"[0-9]+-[0-9]+"$/) { id=$1; sub(/"/,"",id); gsub(/"/,"",id); next } \
    if ($1 ~ /^"[a-zA-Z0-9:_-]+"$/) { key=$1; getline; val=$1; gsub(/"/,"",key); gsub(/"/,"",val); printf("id=%s %s=%s ", id, key, val) }
  } END { printf("\n") }'

  # 추출된 모든 id에 대해 ACK 수행
  IDS=$(echo "$RESP" | awk '{ if ($1 ~ /^"[0-9]+-[0-9]+"$/) { id=$1; gsub(/"/,"",id); print id } }')
  if [[ -n "$IDS" ]]; then
    for id in $IDS; do
      redis-cli -h "$HOST" -p "$PORT" XACK "$STREAM" "$GROUP" "$id" >/dev/null
      echo "ACK id=$id"
    done
  fi

done

