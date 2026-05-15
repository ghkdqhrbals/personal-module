# Context, Goals, Non-Goals

## Context

기존 `redis-stream-sharding/` 설계는 중앙 Group Coordinator 없이 각 member가 active consumer snapshot을 보고 deterministic assignment를 계산한 뒤 shard lease CAS로 read 권한을 확정하는 구조이다. 이 방식은 단순하지만, 언제 누가 revoke를 완료했는지, 어떤 target assignment로 수렴해야 하는지, 운영자가 현재 rebalance 진행 상태를 어디서 봐야 하는지가 약하다.

KIP-848은 기존 Kafka consumer rebalance의 group-wide synchronization barrier를 줄이고, coordinator가 target assignment와 member reconciliation을 관리하는 방향으로 발전했다. Redis Stream에는 Kafka broker coordinator가 없으므로, 동일한 개념을 Redis-backed coordinator로 구현해야 한다.

## KIP-848 Mapping

이 설계에서 가져올 핵심은 client가 각자 owner를 계산하는 구조가 아니라 coordinator가 target assignment를 만들고, member가 heartbeat로 current assignment를 보고하며, coordinator가 revoke/assign dependency를 풀어 수렴시키는 구조이다.

| KIP-848 | Redis Stream Coordinator |
| --- | --- |
| Group Coordinator | Redis-backed active coordinator worker |
| Consumer Group | `{streamPrefix, consumerGroup}` |
| Member | member runtime이 생성한 UUID `memberId` |
| Topic Partition | `{streamVersion, shardIndex}` |
| Group Epoch | group metadata 변경 version |
| Assignment Epoch | target assignment 계산 기준 epoch |
| Member Epoch | member가 적용 중인 assignment epoch |
| Target Assignment | coordinator가 저장한 desired shard ownership |
| Current Assignment | member가 heartbeat로 보고한 실제 적용 state |
| ConsumerGroupHeartbeat | member heartbeat request/response command channel |

Redis에 맞게 달라지는 점:

* Kafka broker가 없으므로 active coordinator worker를 Redis lease로 선출한다.
* Kafka heartbeat RPC는 internal coordinator API 또는 Redis mailbox 기반 request/response로 대체한다.
* Kafka partition offset fencing은 Redis Stream shard lease와 member epoch 검증으로 대체한다.
* member id는 coordinator 발급이 아니라 member runtime 직접 UUID 생성으로 시작한다.
* shard count 변경은 Kafka partition expansion과 다르게 stream version migration으로 처리한다.

## Goals

* coordinator가 group metadata, target assignment, current assignment를 source of truth로 관리한다.
* member join/leave, metadata change, Coordinator Admin API로 요청된 shard count change를 group epoch 증가로 모델링한다.
* target assignment는 coordinator가 계산하고 assignment epoch으로 versioning한다.
* member는 heartbeat/reconciliation loop로 target assignment에 수렴한다.
* revoke 완료 전 같은 shard를 다른 member에게 assign하지 않는다.
* 영향 없는 member는 rebalance 중에도 기존 shard를 계속 consume한다.
* shard count 변경은 next stream version migration으로 처리하고 producer write를 중단하지 않는다.
* actual read authority는 shard lease와 member epoch fencing으로 제어한다.
* Redis Stream delivery는 at-least-once로 두고 idempotency marker로 duplicate side effect를 방지한다.
* operator가 group epoch, assignment epoch, member epoch, target/current assignment, stuck revoke를 볼 수 있게 한다.

## Non-Goals

* Kafka broker protocol을 그대로 재구현
* Redis Cluster resharding 자동 대응
* hot shard 자동 split
* shard 전체 serial ordering
* 외부 side effect까지 포함한 절대적 exactly-once
* 같은 stream version 안에서 shard count만 바꾸는 in-place resharding
* 여러 active coordinator 동시 허용

## Assumptions

* Redis는 stream data plane과 coordinator metadata store로 사용한다.
* coordinator metadata key는 Redis persistence 또는 운영 백업 정책으로 보호한다.
* 각 member는 runtime 시작 시 UUID `memberId`를 직접 생성한다.
* producer와 consumer는 metadata store의 active stream version을 source of truth로 사용한다.
* 한 `streamPrefix + consumerGroup`에는 동시에 하나의 active migration만 허용한다.
* member startup은 shard count 변경을 시작하지 않는다. startup은 coordinator metadata 조회와 heartbeat join만 수행한다.
