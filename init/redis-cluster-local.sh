#!/usr/bin/env bash
set -e

BASE="$(cd "$(dirname "$0")" && pwd)"
PORTS=(9001 9002 9003 9004 9005 9006)
CONF_DIR="$BASE/conf"
DATA_DIR="$BASE/data"

mkdir -p "$CONF_DIR" "$DATA_DIR"

is_running() {
  lsof -iTCP:"$1" -sTCP:LISTEN >/dev/null 2>&1
}

for p in "${PORTS[@]}"; do
  if is_running "$p"; then
    echo "[SKIP] redis $p already running"
    continue
  fi

  NODE_DATA_DIR="$DATA_DIR/$p"
  mkdir -p "$NODE_DATA_DIR"

  CONF="$CONF_DIR/redis-$p.conf"
cat > "$CONF" <<EOF
port $p
bind 127.0.0.1
protected-mode no

cluster-enabled yes
cluster-config-file nodes-$p.conf
cluster-node-timeout 5000

appendonly yes
appendfsync everysec
aof-use-rdb-preamble yes

dir $NODE_DATA_DIR
save ""

maxmemory 1gb
maxmemory-policy allkeys-lru

stream-node-max-bytes 4096
stream-node-max-entries 100
EOF

  echo "[START] redis $p"
  redis-server "$CONF" >"$NODE_DATA_DIR/redis.log" 2>&1 &
done

echo "[WAIT] redis startup"
sleep 5

echo "[INIT] cluster create"
redis-cli --cluster create \
127.0.0.1:9001 127.0.0.1:9002 127.0.0.1:9003 \
127.0.0.1:9004 127.0.0.1:9005 127.0.0.1:9006 \
--cluster-replicas 1 --cluster-yes