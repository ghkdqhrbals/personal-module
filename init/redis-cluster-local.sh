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

echo "[STEP] start redis nodes if not running"
for p in "${PORTS[@]}"; do
  if is_running "$p"; then
    echo "[SKIP] redis $p already running"
    continue
  fi

  CONF="$CONF_DIR/redis-$p.conf"
  if [ ! -f "$CONF" ]; then
cat > "$CONF" <<EOF
port $p
bind 127.0.0.1
protected-mode no
cluster-enabled yes
cluster-config-file nodes-$p.conf
cluster-node-timeout 5000
appendonly yes
appendfsync everysec
maxmemory 1gb
maxmemory-policy allkeys-lru

aof-use-rdb-preamble yes
dir $DATA_DIR
save ""
stream-node-max-bytes 4096
stream-node-max-entries 100
EOF
  fi

  echo "[START] redis $p"
  redis-server "$CONF" &
done

sleep 2

echo "[STEP] check cluster state"
CLUSTER_OK=false
for p in "${PORTS[@]}"; do
  if redis-cli -p "$p" cluster info >/dev/null 2>&1; then
    if redis-cli -p "$p" cluster info | grep -q "cluster_state:ok"; then
      CLUSTER_OK=true
      break
    fi
  fi
done

if $CLUSTER_OK; then
  echo "[SKIP] cluster already initialized"
  exit 0
fi

echo "[INIT] cluster create"
redis-cli --cluster create \
127.0.0.1:9001 127.0.0.1:9002 127.0.0.1:9003 \
127.0.0.1:9004 127.0.0.1:9005 127.0.0.1:9006 \
--cluster-replicas 1 --cluster-yes

echo "[DONE] cluster initialized"