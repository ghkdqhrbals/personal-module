#!/bin/sh
STREAM="summary:1"; N=2000
for i in $(seq 1 $N); do
  redis-cli -c -p 9001 XADD "$STREAM" "*" eventType "SAGA_STARTED" sagaId "test-$i" "payload.title" "title-$i" "payload.abstract" "abs-$i" timestamp "$(date -u +"%Y-%m-%dT%H:%M:%S.%NZ")" >/dev/null
done
echo "Inserted $N messages into $STREAM (cluster :9001)"