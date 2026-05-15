# Data Model, Configuration, and Observability

## Redis Key Model

```text
redis-stream:coord:{streamPrefix}:{consumerGroup}:coordinator-lease
redis-stream:coord:{streamPrefix}:{consumerGroup}:group
redis-stream:coord:{streamPrefix}:{consumerGroup}:members
redis-stream:coord:{streamPrefix}:{consumerGroup}:member:{memberId}
redis-stream:coord:{streamPrefix}:{consumerGroup}:target-assignment
redis-stream:coord:{streamPrefix}:{consumerGroup}:current-assignment:{memberId}
redis-stream:coord:{streamPrefix}:{consumerGroup}:migration:active
redis-stream:coord:{streamPrefix}:{consumerGroup}:migration:{migrationId}
redis-stream:coord:{streamPrefix}:{consumerGroup}:admin:idempotency:{idempotencyKey}
redis-stream:coord:{streamPrefix}:{consumerGroup}:admin:audit
```

Shard lease:

```text
redis-stream:lease:{streamPrefix}:{consumerGroup}:{streamVersion}:{shardIndex}
```

Processing marker:

```text
redis-stream:processing:{consumerGroup}:idempotency:{idempotencyKey}
```

## Configuration

```yaml
redis-stream-coordinator:
  enabled: true
  stream-prefix: summary
  consumer-group: summary-group

  metadata-cache:
    refresh-interval: 1s
    stale-read-ttl: 10s

  coordinator:
    mode: EMBEDDED_LEASED
    tick-interval: 1s
    lease-ttl: 10s
    lease-renew-interval: 3s
    rebalance-timeout: 60s
    min-shard-count: 1
    max-shard-count: 128
    version-policy: AUTO_INCREMENT

  member:
    heartbeat-interval: 3s
    heartbeat-ttl: 15s
    idle-timeout: 30s
    metadata-retention: 5m
    max-concurrency: 12
    handler-threads: 4

  shard-lease:
    ttl: 20s
    renew-interval: 5s

  stream:
    batch-size: 50
    block-timeout: 2s
    ack-mode: ACK_AFTER_SUCCESS
    ordering: KEY

  migration:
    dual-read-enabled: true
    key-fence-enabled: true
    allow-concurrent-migrations: false
    deprecated-after: 7d

  metadata-store:
    type: REDIS
    key-prefix: redis-stream
    processed-retention: 7d
    migration-retention: 7d
```

## Metrics

* `redis_stream_coord_active`
* `redis_stream_coord_coordinator_epoch`
* `redis_stream_coord_group_epoch`
* `redis_stream_coord_assignment_epoch`
* `redis_stream_coord_member_epoch`
* `redis_stream_coord_members_active`
* `redis_stream_coord_members_expired`
* `redis_stream_coord_members_idle_removed_total`
* `redis_stream_coord_rebalance_total`
* `redis_stream_coord_rebalance_duration`
* `redis_stream_coord_scale_request_total`
* `redis_stream_coord_scale_request_failed_total`
* `redis_stream_coord_migration_active`
* `redis_stream_coord_migration_duration`
* `redis_stream_coord_revoke_pending`
* `redis_stream_coord_assignment_stuck`
* `redis_stream_coord_insufficient_capacity`
* `redis_stream_coord_invariant_violation_total`
* `redis_stream_coord_metadata_cleanup_total`
* `redis_stream_coord_heartbeat_protocol_error_total`
* `redis_stream_owned_shards`
* `redis_stream_lease_lost_total`
* `redis_stream_pending_count`
* `redis_stream_lag`
* `redis_stream_messages_read_total`
* `redis_stream_messages_ack_total`
* `redis_stream_processed_duplicate_total`

## Logs

Structured log fields:

* `streamPrefix`
* `consumerGroup`
* `coordinatorId`
* `coordinatorEpoch`
* `groupEpoch`
* `assignmentEpoch`
* `memberId`
* `memberRunId`
* `memberEpoch`
* `streamVersion`
* `shardIndex`
* `leaseToken`
* `eventType`
* `reason`
* `requestedBy`
* `idempotencyKey`
* `migrationId`

## Alerts

* active coordinator 없음
* coordinator lease renew 실패 반복
* group epoch은 증가했지만 assignment epoch이 따라오지 않음
* revoke pending이 rebalance timeout 초과
* member heartbeat expired 급증
* idle member removal 급증
* assignment stuck
* insufficient capacity 지속
* invariant violation 발생
* active migration이 migration timeout 초과
* pending count 또는 lag 지속 증가
* duplicate processed marker spike
