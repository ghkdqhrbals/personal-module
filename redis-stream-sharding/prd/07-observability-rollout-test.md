# Observability, Rollout, and Test Plan

## Metrics

MVP metric:

* `redis_stream_active_consumers`
* `redis_stream_local_consumers`
* `redis_stream_owned_shards`
* `redis_stream_active_version`
* `redis_stream_draining_versions`
* `redis_stream_migration_attempt_total`
* `redis_stream_migration_success_total`
* `redis_stream_migration_failed_total`
* `redis_stream_lease_acquired_total`
* `redis_stream_lease_lost_total`
* `redis_stream_lease_renew_failed_total`
* `redis_stream_key_lanes_active`
* `redis_stream_key_lanes_blocked`
* `redis_stream_messages_inflight`
* `redis_stream_key_fence_waiting`
* `redis_stream_messages_read_total`
* `redis_stream_messages_ack_total`
* `redis_stream_messages_failed_total`
* `redis_stream_pending_count`
* `redis_stream_lag`
* `redis_stream_processed_duplicate_total`
* `redis_stream_metadata_refresh_failed_total`

## Logs

Structured log fields:

* `streamPrefix`
* `streamVersion`
* `routingEpoch`
* `shardIndex`
* `consumerGroup`
* `consumerId`
* `podRunId`
* `localSlot`
* `consumerEpoch`
* `leaseToken`
* `messageId`
* `partitionKey`
* `idempotencyKey`
* `state`
* `attemptCount`

## Alerts

* active consumer count is 0
* owned shard count below expected for more than one lease TTL
* lease renew failure spike
* pending count increasing for a shard
* key fence waiting above threshold
* migration stuck in `PREPARING` or `DRAINING`
* metadata refresh failure above threshold
* duplicate processed marker count spike
* message in-flight age above threshold

## Rollout Plan

1. Add metadata schema without changing current stream processing.
2. Add producer metadata envelope while preserving current stream key path.
3. Add idempotency marker and processing state around existing handler.
4. Add shard lease acquisition in shadow mode and compare expected owner vs current listener behavior.
5. Replace all-shard listener registration with lease-owned shard fetch loop.
6. Enable pending recovery for owned shard only.
7. Enable Rendezvous assignment for scale-out/in.
8. Enable active/draining dual-read.
9. Enable startup-driven next version migration.
10. Enable strict key fence for streams requiring ordering.

## Rollback Plan

* If assignment causes instability, disable lease-owned fetch loop and fall back to existing fixed partition listener.
* If migration causes issue, stop new migration creation and keep current active version unchanged.
* If strict key fence causes unacceptable lag, switch affected stream to `FAST_FAILOVER` only after accepting ordering tradeoff.
* If metadata store is unavailable, consumers should fail closed for new read; producers may either fail closed or use last valid active snapshot within a configured TTL.

## Test Plan

Unit tests:

* routing hash is deterministic
* same metadata snapshot gives same shard index
* Rendezvous owner calculation is deterministic
* local consumer count is capped by max readable shard count
* consumerEpoch excludes local clock
* assignment diff computes keep/return/acquire correctly
* lease renew CAS rejects wrong token/epoch
* key lane scheduler serializes same key and parallelizes different keys

Integration tests:

* single Pod with local consumer UUIDs reads all shards
* second Pod joins and only moved shards transfer
* consumer graceful shutdown returns owned shards
* consumer crash leaves PEL and new owner recovers pending
* duplicate message idempotency marker prevents duplicate side effect
* `v1 -> v2` migration switches producer write target
* consumer dual-reads active and draining versions
* strict key fence blocks new version key until old key lane closes

Load tests:

* high key cardinality throughput
* hot key behavior
* lease churn under rolling deploy
* metadata refresh delay and registry staleness
* Redis PEL growth under handler failure
* in-flight and PEL growth under partial key failure
