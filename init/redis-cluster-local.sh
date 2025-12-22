#!/usr/bin/env bash
BASE="$(pwd)"
PORTS=(9001 9002 9003 9004 9005 9006)

mkdir -p $BASE/data/redis/conf $BASE/data/redis/data

for p in "${PORTS[@]}"; do
cat > $BASE/conf/redis-$p.conf <<EOF
port $p
bind 127.0.0.1
protected-mode no
cluster-enabled yes
cluster-config-file nodes-$p.conf
cluster-node-timeout 5000
appendonly yes
appendfsync everysec
aof-use-rdb-preamble yes
save 3600 1 300 100 60 10000
stream-node-max-bytes 4096
stream-node-max-entries 100
dir $BASE/data
EOF
redis-server $BASE/conf/redis-$p.conf &
done

sleep 3
redis-cli --cluster create \
127.0.0.1:9001 127.0.0.1:9002 127.0.0.1:9003 \
127.0.0.1:9004 127.0.0.1:9005 127.0.0.1:9006 \
--cluster-replicas 1 --cluster-yes