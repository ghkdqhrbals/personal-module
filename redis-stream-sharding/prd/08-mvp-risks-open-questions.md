# MVP Scope, Tradeoffs, Risks, and Open Questions

## MVP Scope

Included:

* desired routing spec loading
* active metadata vs desired spec reconciliation
* `v1 -> v2 -> v3` stream version increment
* next version stream/group creation
* metadata CAS based active write version switch
* old version `DRAINING accepts_writes=false accepts_reads=true`
* producer active version routing
* consumer active/draining dual-read
* key ordered lane scheduler
* optional strict key fence for streams that require cross-version key ordering
* Rendezvous Hashing assignment
* shard lease fencing
* `XREADGROUP` + ACK after success
* pending recovery for owned shard
* Redis processing marker and message-level `XACK` policy
* core metrics and alerts

Excluded:

* central Group Coordinator
* leader election
* global ordering
* in-place shard count change
* stop-the-world migration
* Redis 8.6 `XADD IDMP` hard dependency
* shard별 contiguous ACK frontier 또는 `lastAckedStreamId` store
* advanced DLQ automation
* dead consumer cleanup automation
* Redis Cluster resharding automation
* hot shard auto split
* bounded-load rendezvous hashing
* multiple concurrent migrations per stream prefix

## Tradeoffs

### Coordinatorless Assignment

Benefits:

* no central leader
* smaller operational surface
* no assignment plan table required for MVP
* scale-out/in handled by consumer list plus deterministic hashing

Costs:

* snapshot drift can temporarily delay ownership convergence
* current target assignment is derived, not centrally stored
* shard distribution is probabilistic
* actual read safety depends on correct lease fencing

### Strict Ordering

Benefits:

* same key processing order is preserved in normal and migration paths
* old/new stream version interleaving is controlled

Costs:

* owner crash can delay a key until Redis PEL `min-idle-time` permits reclaim
* hot key can block itself regardless of available worker count
* key fence store becomes a dependency for migration ordering

### Message-Level ACK

Benefits:

* Redis Stream의 message별 `XACK` 모델을 그대로 사용한다.
* shard 전체 stream id 순서에 막히지 않고 성공한 message를 바로 ACK할 수 있다.
* 별도 ACK frontier store와 retention sizing을 MVP에서 제거한다.

Costs:

* shard 전체 serial ordering 또는 contiguous offset semantics를 제공하지 않는다.
* 중복 replay 방지는 ACK frontier가 아니라 processing marker retention에 의존한다.

## Risks

* Lease renew bug can allow duplicate read.
* Handler side effect may not be truly idempotent.
* Redis outage can stop new reads if fail-closed policy is selected.
* Large `DRAINING` backlog can keep old versions alive longer than expected.
* Low partition key cardinality can make `handler-threads` ineffective.
* Rolling deploy with assignment-affecting config mismatch can reduce active capacity.
* Redis PEL `min-idle-time` can make strict failover slower than operator expectation.

## Open Questions

* Should Redis metadata use HASH-only encoding or RedisJSON where available?
* Should registry consumer IDs also be stored in a SET, or should consumer discovery use SCAN over heartbeat keys?
* Does each stream prefix need a domain-specific publisher API for partition key extraction?
* What is the default idempotency retention window for summary generation?
* Should `FAILED` messages remain unacked in MVP, or move to DLQ and ACK?
* What is the acceptable per-key failover delay in `STRICT_ORDER` mode?
* Should producers fail closed when metadata store is unavailable, or use last active snapshot for a bounded TTL?
