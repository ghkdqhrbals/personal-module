#!/usr/bin/env bash
set -euo pipefail

HOST="127.0.0.1"
PORT="9001"
STREAM="summary:1"
COUNT=${1:-1000}
MAXLEN=${MAXLEN:-10000}  # 환경변수로 조정 가능, 기본 10000

echo "Adding ${COUNT} messages to ${STREAM} on ${HOST}:${PORT} (MAXLEN ~ ${MAXLEN})"
for i in $(seq 1 "${COUNT}"); do
  redis-cli -c -p "$PORT" \
    XADD "$STREAM" MAXLEN "~" "$MAXLEN" "*" \
    msg "hello-$i" \
    ts "$(date +%s%3N)" >/dev/null
  if (( i % 100 == 0 )); then
    echo "  -> $i messages sent"
  fi
done

echo "Done. Total messages: ${COUNT}"
