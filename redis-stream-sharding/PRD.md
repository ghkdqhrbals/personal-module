# Redis Stream Sharded Consumer Module PRD

이 문서는 Redis Stream sharding PRD의 entrypoint이다. 전체 요구사항은 크기 때문에 상세 문서를 lazy loading 방식으로 나누어 관리한다. 필요한 섹션만 열어 읽고 수정한다.

## Source

* 기준 설계: [`../REDIS_STREAM_SHAR_PLANNING.md`](../REDIS_STREAM_SHAR_PLANNING.md)
* 사용자 요구사항 정리: [`USER_REQUIREMENTS.md`](USER_REQUIREMENTS.md)
* 폴더 작업 지침: [`AGENTS.md`](AGENTS.md)

## Lazy Loading Index

1. [Context, Goals, Non-Goals](prd/01-context-goals.md)
2. [Proposed Architecture](prd/02-proposed-architecture.md)
3. [Producer Routing and Stream Version Migration](prd/03-routing-and-migration.md)
4. [Consumer Registry, Assignment, and Rebalance](prd/04-consumer-assignment-rebalance.md)
5. [Processing, Ordering, Retry, and Failure Handling](prd/05-processing-reliability.md)
6. [Metadata Store, Data Model, and Configuration](prd/06-metadata-data-config.md)
7. [Observability, Rollout, and Test Plan](prd/07-observability-rollout-test.md)
8. [MVP Scope, Tradeoffs, Risks, and Open Questions](prd/08-mvp-risks-open-questions.md)

## Product Summary

Spring Boot/Kotlin 공통 모듈로 Redis Stream을 여러 stream shard로 나누고, 여러 runtime instance가 shard를 중복 없이 나눠 읽게 한다. Redis Stream은 Kafka처럼 broker-managed partition assignment와 rebalance를 제공하지 않으므로, 이 모듈은 metadata store, deterministic assignment, shard lease, pending recovery, idempotency marker를 조합해 운영 가능한 sharded consumer experience를 제공한다.

## Core Decisions

* 중앙 Group Coordinator는 MVP 범위에 포함하지 않는다.
* Pod 안의 local consumer member는 UUID `consumerId`를 가지며 shard owner는 Rendezvous Hashing으로 consumerId 단위 계산한다.
* 실제 `XREADGROUP` 권한은 shard lease CAS를 통과한 worker에게만 준다.
* shard count 변경은 in-place modulo 변경이 아니라 `v1 -> v2 -> v3` stream version migration으로 처리한다.
* producer는 metadata store의 active write version에만 쓴다.
* consumer는 `ACTIVE`와 `DRAINING` read version을 dual-read할 수 있다.
* ordering guarantee는 `partitionKey` 단위로 정의한다.
* Redis Stream delivery는 at-least-once이고, 중복 side effect는 idempotency key와 processing marker로 막는다.

각 결정의 설계 근거는 [`USER_REQUIREMENTS.md`](USER_REQUIREMENTS.md)의 "설계 근거" 섹션에 정리한다.

## Current Implementation Baseline

현재 코드에는 `SummaryConfig.partitionNumbers`, `PartitionedStream.resolvePartition`, `summary:{partition}` stream key, `StreamMessageListenerContainer.receive(...)`, 수동 ACK 경로가 있다. 목표 PRD는 이 baseline을 대체/확장해 metadata-versioned routing, shard lease, coordinatorless assignment, key ordered processing을 추가하는 방향이다.

## Success Criteria

* consumer scale-out/in 시 전체 consumer stop 없이 shard ownership이 수렴한다.
* shard count 변경 시 producer write를 멈추지 않고 next stream version으로 전환한다.
* old `DRAINING` stream version은 unread lag, Redis PEL pending, local in-flight가 0이 된 뒤에만 `DEPRECATED`로 전환된다.
* rolling deploy 중 old/new yaml instance가 공존해도 metadata store의 active version과 canonical assignment config를 따른다.
* owner crash 이후 pending message는 새 owner가 reclaim하고 idempotency marker로 중복 side effect를 방지한다.
* operator가 active/draining version, owned shard, pending count, lag, lease loss, migration state를 dashboard에서 확인할 수 있다.
