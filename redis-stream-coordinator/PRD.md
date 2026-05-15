# Redis Stream Coordinator PRD

이 문서는 Redis Stream sharding을 KIP-848 스타일의 coordinator-managed protocol로 관리하기 위한 PRD entrypoint이다. 전체 요구사항은 lazy loading 방식으로 나누어 관리한다.

## Source

* KIP-848: [The Next Generation of the Consumer Rebalance Protocol](https://cwiki.apache.org/confluence/display/KAFKA/KIP-848%3A+The+Next+Generation+of+the+Consumer+Rebalance+Protocol)
* 사용자 정리 글: [Kafka KIP-848 는 왜 등장했는가](https://ghkdqhrbals.github.io/portfolios/docs/Java/51/)
* 기존 coordinatorless 설계: [`../redis-stream-sharding/PRD.md`](../redis-stream-sharding/PRD.md)
* 폴더 작업 지침: [`AGENTS.md`](AGENTS.md)

## Lazy Loading Index

1. [Context, Goals, Non-Goals](prd/01-context-goals.md)
2. [Coordinator Architecture](prd/02-coordinator-architecture.md)
3. [Group Metadata and Assignment Model](prd/03-group-assignment-model.md)
4. [Stream Version Migration, Routing, and Admin API](prd/04-stream-version-migration.md)
5. [Processing, Reliability, and Fencing](prd/05-processing-reliability.md)
6. [Data Model, Configuration, and Observability](prd/06-data-config-observability.md)
7. [MVP Scope, Tradeoffs, Risks, and Open Questions](prd/07-mvp-risks-open-questions.md)
8. [KIP-848 Implementation Coverage](prd/08-kip848-implementation-coverage.md)

## Product Summary

Spring Boot/Kotlin 공통 모듈로 Redis Stream shard ownership을 중앙 coordinator가 관리한다. 각 runtime member는 heartbeat를 통해 현재 상태를 보고하고, coordinator는 group metadata 변화에 따라 target assignment를 계산한다. member는 target assignment에 독립적으로 수렴하며, coordinator는 revoke가 완료되기 전 같은 shard를 다른 member에게 assign하지 않는다.

## Core Decisions

* 중앙 Group Coordinator를 둔다.
* coordinator는 Redis-backed lease로 active coordinator를 하나만 유지한다.
* member identity는 member runtime이 직접 만든 UUID를 사용하되, coordinator가 등록/epoch/fencing 상태를 관리한다.
* shard assignment는 sticky partition 방식으로 계산하고 target assignment로 저장한다.
* member는 heartbeat request로 current assignment와 revoke ack를 보고하고, coordinator는 heartbeat response로 revoke/assign/fence 명령을 내려보낸다.
* rebalance는 group-wide stop-the-world barrier가 아니라 member별 reconciliation loop로 진행한다.
* shard count 변경은 member startup YAML sync가 아니라 Coordinator Admin API로 요청하고, coordinator가 next stream version migration으로 처리한다.
* Redis Stream delivery는 at-least-once이고, 중복 side effect는 idempotency key와 processing marker로 막는다.

## Success Criteria

* 새 member join/leave 시 변경된 shard만 revoke/assign되고, 영향 없는 member는 계속 consume한다.
* shard owner handoff는 `revoke ack -> target install -> assign` 순서를 지킨다.
* shard count 변경 시 producer write를 멈추지 않고 next stream version으로 전환한다.
* old `DRAINING` version은 unread lag, Redis PEL pending, local in-flight가 0이 된 뒤 `DEPRECATED`로 전환된다.
* operator는 group epoch, assignment epoch, member epoch, target/current assignment, stuck revoke, pending, lag를 확인할 수 있다.
