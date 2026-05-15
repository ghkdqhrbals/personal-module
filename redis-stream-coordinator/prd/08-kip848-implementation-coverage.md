# KIP-848 Implementation Coverage

## Purpose

이 문서는 Redis Stream Coordinator가 KIP-848의 어떤 아이디어를 구현하고, 어떤 부분을 Redis Stream 제약에 맞게 바꾸거나 제외했는지 정리한다. 목표는 Kafka protocol 호환이 아니라 KIP-848의 coordinator-managed rebalance 모델을 Redis Stream shard ownership에 적용하는 것이다.

## Concept Mapping

| KIP-848 Concept | Redis Stream Coordinator |
| --- | --- |
| Group Coordinator | Redis-backed active coordinator worker |
| Consumer Group | `{streamPrefix, consumerGroup}` |
| Member | member runtime이 생성한 UUID `memberId` |
| Topic Partition | `{streamVersion, shardIndex}` |
| Target Assignment | coordinator가 저장한 desired shard ownership |
| Current Assignment | member가 heartbeat로 보고한 실제 적용 상태 |
| ConsumerGroupHeartbeat | member heartbeat request/response command channel |
| Group Epoch | group metadata 변경 세대 |
| Assignment Epoch | target assignment 계산 기준 세대 |
| Member Epoch | member fencing과 assignment 적용 세대 |

## Implemented From KIP-848

* Coordinator-driven rebalance: member가 owner를 최종 결정하지 않고 coordinator가 target assignment를 계산한다.
* Declarative target assignment: coordinator는 group이 수렴해야 할 desired shard ownership을 저장한다.
* Current assignment reporting: member는 heartbeat마다 실제 owned/revoking shard 상태를 보고한다.
* Heartbeat command channel: coordinator는 heartbeat response로 `REVOKE`, `ASSIGN`, `FENCE`, `SYNC_METADATA` command를 전달한다.
* Incremental reconciliation: group-wide stop-the-world barrier 없이 변경된 member/shard만 revoke/assign한다.
* Revoke-before-assign dependency: 기존 owner의 revoke ack 또는 expired lease 확인 전 새 owner에게 assign하지 않는다.
* Epoch model: `groupEpoch`, `assignmentEpoch`, `memberEpoch`으로 group metadata, target assignment, member fencing을 구분한다.
* Coordinator event loop: heartbeat, metadata change, lease event를 coordinator loop에서 순차적으로 평가한다.
* Session timeout and fencing: heartbeat TTL과 idle timeout으로 dead member를 fencing하고 shard를 재할당한다.
* Sticky movement minimization: assignment는 sticky partition 전제로 기존 owner를 최대한 유지한다.

## Redis-Specific Adaptations

* Kafka broker coordinator 대신 Redis lease로 active coordinator worker를 하나만 선출한다.
* Kafka topic partition 대신 Redis Stream shard key `{streamVersion, shardIndex}`를 assignment 단위로 사용한다.
* Kafka offset commit fencing 대신 Redis shard lease token과 `memberEpoch` 검증으로 `XREADGROUP`/`XACK`를 fence한다.
* Kafka partition expansion 대신 stream version migration으로 shard scale-out/in을 처리한다.
* Kafka `ConsumerGroupHeartbeat` RPC는 internal coordinator API 또는 Redis mailbox request/response로 구현한다.
* member id는 member runtime이 생성하고 coordinator가 epoch/fencing 상태를 관리한다.
* KIP-848의 server-side assignor 선택은 제공하지 않는다. sticky partition assignment는 설계 전제이다.

## Not Implemented

* Kafka wire protocol compatibility.
* Kafka `ConsumerGroupPrepareAssignment` / `ConsumerGroupInstallAssignment` client-side assignment delegation.
* assignor negotiation, assignor version probing, assignor metadata compatibility handling.
* regex topic subscription and server-side topic metadata resolution.
* Kafka static membership semantics such as temporary leave with instance id replacement.
* Kafka offset commit/fetch APIs and topic-id based offset handling.
* Kafka group dynamic config API. Redis Stream Coordinator uses its own Admin API for create/scale/rollback.

## Coverage Notes

KIP-848 says the new protocol moves complexity from clients to the group coordinator, removes a global synchronization barrier, stores target/current assignment, and uses heartbeat to carry member state and assignment commands. This design implements those parts directly, then replaces Kafka-specific broker, partition, offset, and protocol concerns with Redis metadata keys, shard leases, stream version migration, and idempotency markers.
