# Metadata Store, Data Model, and Configuration

## Metadata Store

Production 기본값은 Redis이다. stream metadata, migration state, consumer registry, shard lease, processing marker, key fence는 Redis keyspace에 저장한다.

metadata store는 다음 상태의 source of truth이다.

* stream metadata
* migration plan
* local consumer registry 또는 registry mirror
* message processing state
* key migration fence
* idempotency key
* retry/failure state

장기 감사, analytics, 운영 이력 조회가 필요하면 Redis event log 또는 별도 sink를 후속으로 붙인다. MVP는 Redis만 사용한다.

## Redis Key Model

```text
redis-stream:metadata:{streamPrefix}:versions
  type: ZSET
  member: {streamVersion}
  score: metadata_version 또는 created_at epoch millis

redis-stream:metadata:{streamPrefix}:{streamVersion}
  type: HASH or JSON
  fields:
    stream_prefix
    stream_version
    routing_epoch
    shard_count
    routing_spec_hash
    partition_hash_algorithm
    partition_hash_seed
    assignment_algorithm
    assignment_hash_seed
    stream_key_format
    metadata_version
    state
    accepts_writes
    accepts_reads
    previous_stream_version
    created_by_consumer_id
    created_at
    updated_at
```

```text
redis-stream:metadata:{streamPrefix}:active-write
  type: STRING
  value: {streamVersion}

redis-stream:metadata:{streamPrefix}:readable-versions
  type: SET
  members: ACTIVE and DRAINING stream versions
```

```text
redis-stream:migration:{streamPrefix}:active
  type: HASH or JSON
  fields:
    from_stream_version
    to_stream_version
    desired_routing_spec_hash
    state
    created_by_consumer_id
    updated_at
```

```text
redis-stream:registry:{streamPrefix}:{consumerGroup}:consumers
  type: SET
  members: {consumerId}

redis-stream:registry:{streamPrefix}:{consumerGroup}:consumer:{consumerId}
  type: STRING JSON, PX heartbeat_ttl
  value fields:
    consumer_group
    consumer_id
    pod_name
    pod_run_id
    local_slot
    application
    host
    process_id
    state
    readable_versions
    metadata_version
    assignment_config_version
    config_version
    last_seen_at
```

```text
redis-stream:lease:{streamPrefix}:{consumerGroup}:{streamVersion}:{shardIndex}
  type: STRING JSON, PX lease_ttl
  value fields:
    consumer_id
    worker_id
    lease_token
    metadata_version
    assignment_config_version
    consumer_epoch
    pod_run_id
    state
    expires_at
```

```text
redis-stream:processing:{consumerGroup}:idempotency:{idempotencyKey}
  type: HASH or JSON, TTL processed_retention
  fields:
    group_name
    stream_key
    message_id
    partition_key
    idempotency_key
    state
    owner_consumer_id
    lease_token
    attempt_count
    updated_at

redis-stream:processing:{consumerGroup}:message:{streamKey}:{messageId}
  type: STRING
  value: {idempotencyKey}
  TTL: processed_retention
```

```text
redis-stream:key-fence:{streamPrefix}:{consumerGroup}:{partitionKey}
  type: HASH or JSON, TTL migration_retention
  fields:
    from_stream_version
    from_routing_epoch
    to_stream_version
    to_routing_epoch
    state
    owner_lease_token
    updated_at
```

## Constraints

Redis에는 RDBMS unique constraint가 없으므로 모든 불변성은 key naming, `SET NX`, Lua CAS, transaction-like script로 강제한다.

* `redis-stream:metadata:{streamPrefix}:{streamVersion}`는 `SET NX` 또는 Lua CAS로 한 번만 생성한다.
* active write version은 `redis-stream:metadata:{streamPrefix}:active-write` 하나만 둔다.
* active migration은 `redis-stream:migration:{streamPrefix}:active`를 `SET NX` 또는 Lua CAS로 한 개만 둔다.
* consumer heartbeat key는 `{consumerGroup, consumerId}` 단위로 하나만 존재한다.
* idempotency marker는 `redis-stream:processing:{consumerGroup}:idempotency:{idempotencyKey}`를 `SET NX`로 claim한다.
* message marker alias는 `redis-stream:processing:{consumerGroup}:message:{streamKey}:{messageId}`로 중복 처리 여부를 찾는다.
* key fence는 `{streamPrefix, consumerGroup, partitionKey}` 단위로 하나만 둔다.

## Configuration

```yaml
redis-stream:
  enabled: true
  stream-prefix: summary
  consumer-group: summary-group

  routing:
    desired-shard-count: 12
    hash-algorithm: murmur3
    hash-seed: default
    version-policy: AUTO_INCREMENT

  stream-metadata:
    source: REDIS
    refresh-interval: 30s
    fail-on-mismatch: true

  config:
    version: "2026-05-09.1"
    assignment-config-source: REDIS
    fail-on-assignment-config-mismatch: true

  consumer-registry:
    heartbeat-interval: 5s
    heartbeat-ttl: 15s
    refresh-interval: 5s
    max-stale-view: 20s

  rebalance:
    protocol: COOPERATIVE
    stabilization-window: 3s
    acquire-backoff-min: 100ms
    acquire-backoff-max: 1s
    handoff-timeout: 30s

  assignment:
    strategy: RENDEZVOUS_HASH
    hash-seed: default

  consumer:
    max-concurrency: 12
    concurrency-policy: CAP_BY_MAX_READABLE_SHARD_COUNT
    handler-threads: 4
    batch-size: 50
    block-timeout: 2s
    ack-mode: ACK_AFTER_SUCCESS
    ordering: KEY
    ordering-mode: STRICT_ORDER
    max-inflight-per-shard: 1000
    max-inflight-per-key: 1

  shard-lease:
    ttl: 20s
    renew-interval: 5s

  pending:
    reclaim-enabled: true
    min-idle-time: 60s
    scan-interval: 30s
    batch-size: 100

  metadata-store:
    type: REDIS
    key-prefix: redis-stream
    cas-script-sha-cache-enabled: true
    processed-retention: 7d
    migration-retention: 7d

  migration:
    dual-read-enabled: true
    key-fence-enabled: true
    auto-create-next-version: true
    allow-concurrent-migrations: false
    deprecated-after: 7d
```

`consumer.max-concurrency`는 Pod 안에 만들 local consumer member 수의 상한이다. 실제 생성 수는 `min(max-concurrency, maxReadableShardCount)`로 계산한다. `maxReadableShardCount`는 readable version 중 가장 큰 shard count이며, migration 중에도 old+new shard count 합계를 사용하지 않는다. `handler-threads`, batch size, block timeout은 local 처리 튜닝값이고 shard owner 계산에는 들어가지 않는다.

## Immutable Metadata

같은 `stream_prefix + stream_version + routing_epoch` 안에서 다음 값은 바꾸지 않는다.

* `shard_count`
* `partition_hash_algorithm`
* `partition_hash_seed`
* `assignment_algorithm`
* `assignment_hash_seed`

변경이 필요하면 새 stream version 또는 routing epoch을 만든다.
