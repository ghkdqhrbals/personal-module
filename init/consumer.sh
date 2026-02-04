#!/usr/bin/env bash
set -euo pipefail

HOST="127.0.0.1"
PORT="9002"
STREAM="summary:1"
GROUP="summary-cg"
CONSUMER="consumer-1"
BLOCK_MS=100   # read 대기
PROCESS_MS=100 # 처리 간 딜레이

# 그룹 생성 (없으면)
redis-cli -c -h "$HOST" -p "$PORT" \
  XGROUP CREATE "$STREAM" "$GROUP" "\$" MKSTREAM >/dev/null 2>&1 || true

processed=0
last_id="-"

while true; do
  RESP=$(redis-cli -c -h "$HOST" -p "$PORT" \
    XREADGROUP GROUP "$GROUP" "$CONSUMER" \
    COUNT 1 BLOCK "$BLOCK_MS" \
    STREAMS "$STREAM" ">" 2>&1)

  # 에러 출력
  if echo "$RESP" | grep -qE '^(ERR|MOVED|ASK)'; then
    echo
    echo "[error] $RESP"
    sleep 1
    continue
  fi

  # 메시지 없으면 루프
  [[ -z "$RESP" ]] && continue

  # ID 추출
  ID=$(echo "$RESP" | awk '
    $1 ~ /^"[0-9]+-[0-9]+"$/ {
      gsub(/"/,"",$1); print $1; exit
    }')

  [[ -z "$ID" ]] && continue

  # ---- 처리 ----
  processed=$((processed + 1))
  last_id="$ID"
  printf "\r[consume] total=%s last_id=%s" "$processed" "$last_id"

  # 즉시 ACK
  redis-cli -c -h "$HOST" -p "$PORT" \
    XACK "$STREAM" "$GROUP" "$ID" >/dev/null

  # 처리 간 딜레이 100ms
  sleep 0.1
done
