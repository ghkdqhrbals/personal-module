# 사용자 요구사항 정리

이 문서는 Redis Stream sharding PRD 작성 과정에서 사용자가 명시한 요구사항과 문서화 결정사항을 정리한다. 상세 설계는 [`PRD.md`](PRD.md)와 [`prd/`](prd/) 하위 문서를 따른다.

## 요청 범위

* Redis Stream sharding 관련 문서를 담는 새 폴더를 만든다.
* 해당 폴더 내부에 `AGENTS.md`를 만든다.
* 해당 폴더 내부에 `PRD.md`를 만든다.
* `PRD.md`는 큰 문서가 될 예정이므로 단일 파일에 모든 내용을 넣지 않는다.
* `PRD.md`는 lazy loading 방식으로 상세 문서 링크를 참조하는 entrypoint가 되어야 한다.
* 상세 내용은 루트의 [`../REDIS_STREAM_SHAR_PLANNING.md`](../REDIS_STREAM_SHAR_PLANNING.md)를 참고한다.
* Metadata Store는 Redis로 설정한다.
* 사용자의 요구사항을 정리한 문서도 폴더에 포함한다.

## 문서 구조 요구사항

* `redis-stream-sharding/` 폴더를 문서 루트로 사용한다.
* `AGENTS.md`에는 이 폴더에서 문서를 유지보수할 때 따라야 할 작업 지침을 둔다.
* `PRD.md`는 다음 역할을 한다.
  * 전체 PRD entrypoint
  * lazy loading index
  * core decisions 요약
  * 상세 문서 링크 모음
* 상세 PRD는 `prd/*.md` 파일로 분리한다.
* 요구사항 정리 문서는 이 파일, [`USER_REQUIREMENTS.md`](USER_REQUIREMENTS.md), 로 관리한다.

## 설계 요구사항

* Redis Stream을 여러 stream shard로 나누어 처리한다.
  * 설계 근거: Redis Cluster 모드에서 단일 stream key 또는 모든 인스턴스가 모든 partition을 구독하는 구조는 처리량 확장, shard ownership, pending recovery 범위를 제어하기 어렵다.
* 중앙 Group Coordinator는 두지 않는다.
* 모든 consumer member가 같은 consumer view를 독립적으로 계산해 shard owner를 결정한다.
* shard owner 계산은 deterministic algorithm을 사용한다.
* 실제 read 권한은 shard lease CAS 검증으로 판단한다.
* shard count 또는 routing spec 변경은 기존 version을 직접 수정하지 않고 `v2`, `v3` 같은 다음 stream version을 생성한다.
* version 생성은 Redis metadata store에서 CAS로 한 Pod만 성공해야 한다.
* producer와 consumer는 Redis metadata store의 stream metadata를 기준으로 동작한다.
* hot path에서는 immutable metadata snapshot cache를 사용할 수 있다.

## Redis Metadata Store 요구사항

* Metadata Store는 Redis를 기본값으로 둔다.
* stream metadata, migration state, consumer registry, shard lease, processing marker, key fence는 Redis keyspace에 저장한다.
* ACK 상태는 Redis Stream consumer group의 PEL과 message-level `XACK`를 기본으로 사용하고, MVP에서 별도 ACK frontier store는 두지 않는다.
* RDBMS unique constraint 대신 Redis key naming, `SET NX`, Lua CAS, TTL을 사용해 불변성과 ownership을 강제한다.
* Redis 장애 시 producer/consumer fail-closed 또는 bounded stale snapshot 사용 여부는 별도 결정사항으로 둔다.

## 설계 근거

### 왜 Redis Stream Sharding 문서를 별도 폴더로 분리하는가

Redis Stream sharding은 단순한 listener 설정 변경이 아니라 routing, stream versioning, consumer registry, lease, pending recovery, idempotency, migration policy가 함께 움직이는 기능이다. 루트 설계 문서 하나에 계속 누적하면 구현자가 필요한 부분만 읽기 어렵고, 변경 영향 범위도 불명확해진다.

따라서 `redis-stream-sharding/` 폴더를 별도 문서 루트로 두고, `PRD.md`는 인덱스 역할만 하게 한다. 상세 요구사항은 주제별 파일로 나누어 lazy loading한다. 이렇게 해야 producer routing만 수정하는 사람, consumer rebalance를 구현하는 사람, observability를 붙이는 사람이 필요한 문서만 열어볼 수 있다.

### 왜 중앙 Group Coordinator를 두지 않는가

Redis Stream에는 Kafka broker처럼 partition assignment와 rebalance를 관리하는 coordinator가 없다. 애플리케이션 레벨에서 coordinator를 만들면 leader election, coordinator lease, assignment plan 저장, stale leader fencing, follower 적용 상태 추적까지 구현해야 한다.

이 설계의 목적은 Kafka coordinator를 다시 만드는 것이 아니라 Redis Stream 위에 운영 가능한 sharding layer를 얹는 것이다. 그래서 shard owner는 모든 consumer가 deterministic algorithm으로 독립 계산하고, 실제 read 권한은 shard lease CAS로 검증한다. 이 방식은 일시적인 consumer snapshot 차이를 허용하되, 중복 read 위험을 lease fencing으로 줄인다.

### 왜 Redis를 Metadata Store로 쓰는가

이 기능의 핵심 상태는 stream 처리와 매우 가까운 control-plane state이다. consumer heartbeat, shard lease, migration state, key fence, processing marker는 짧은 TTL, CAS, atomic claim이 중요하다. Redis는 이미 Stream data plane으로 사용되고 있고, `SET NX PX`, Lua CAS, TTL을 제공하므로 MVP에서 별도 RDBMS 의존성을 추가하지 않고 control-plane을 구성할 수 있다.

대신 Redis에는 RDBMS unique constraint와 장기 감사 기능이 없으므로 key naming, `SET NX`, Lua script, retention TTL을 명확히 설계해야 한다. 운영 감사나 장기 분석이 필요하면 Redis event log 또는 별도 sink를 후속으로 붙인다.

### 왜 shard count 변경을 in-place로 하지 않는가

`hash(key) % shardCount`에서 shard count만 바꾸면 같은 `partitionKey`의 old message와 new message가 서로 다른 stream shard로 갈 수 있다. 그러면 두 shard owner가 같은 key를 동시에 처리할 수 있고, key ordering이 깨진다.

그래서 shard count나 routing spec 변경은 기존 stream version을 직접 수정하지 않는다. 새 stream version을 만들고 producer write target만 새 version으로 전환한다. old version은 `DRAINING`으로 남겨 consumer가 계속 읽게 한다. strict ordering이 필요한 key는 key fence로 old lane이 닫힌 뒤 new lane을 열어야 한다.

### 왜 backlog 0 대기 방식이 아닌 online migration인가

서비스가 계속 동작해야 하는 상황에서 전체 lag/backlog가 0이 될 때까지 producer를 멈추거나 consumer 전환을 기다리는 방식은 운영상 허용하기 어렵다. 특정 hot key, 외부 API 지연, handler failure 하나만 있어도 migration이 무기한 지연될 수 있다.

online migration은 전체 backlog 0을 요구하지 않는다. producer는 active write version만 바꾸고, consumer는 active/draining version을 dual-read한다. strict ordering은 전체 shard가 아니라 key 단위 fence로 관리한다. 이 방식은 전체 서비스 가용성을 유지하면서 영향 범위를 key 단위로 줄인다.

### 왜 ordering 단위를 shard가 아니라 partitionKey로 잡는가

Redis Stream shard 전체를 serial하게 처리하면 느린 message 하나가 같은 shard의 모든 뒤 message를 막는다. 실제 요구사항은 대부분 global ordering이나 shard 전체 ordering이 아니라 userId, orderId, aggregateId 같은 business key 단위 ordering이다.

따라서 shard fetch loop는 message를 읽는 역할만 하고, handler 실행은 `partitionKey`별 lane scheduler가 담당한다. 같은 key는 순차 처리하고, 다른 key는 병렬 처리한다. 이 모델은 ordering guarantee를 필요한 범위로 제한하면서 처리량을 확보한다.

### 왜 idempotency marker가 필수인가

Redis Stream consumer group은 `XREADGROUP` 후 ACK 전에 consumer가 죽으면 message를 PEL에 남긴다. 새 owner가 `XAUTOCLAIM`으로 reclaim하면 같은 message가 다시 처리될 수 있다. lease는 중복 read 시간을 줄이는 fencing 장치이지, 외부 side effect의 exactly-once를 보장하지 않는다.

따라서 handler side effect는 idempotency key와 processing marker를 기준으로 재실행에 안전해야 한다. MVP는 Redis processing marker를 필수 경로로 두고, Redis 8.6+ `XADD IDMP`는 producer 중복 stream entry를 줄이는 후속 최적화로 둔다.

### 왜 local-only 설정을 assignment에서 제외하는가

`handler-threads`, batch size, block timeout 같은 값은 instance별 처리 능력과 latency tuning에 관한 설정이다. 이런 값이 shard owner 계산에 들어가면 rolling deploy 중 old/new 설정이 섞일 때 consumer마다 서로 다른 owner를 계산할 수 있다.

owner 계산에는 metadata version, assignment config version, assignment algorithm, assignment seed, active consumer list처럼 모든 consumer가 공유해야 하는 값만 들어간다. local-only 설정은 자신이 이미 소유한 shard 내부 처리 방식에만 영향을 준다.

## Migration 요구사항

* shard scale-out/in 시 서비스는 다운타임 없이 계속 동작해야 한다.
* lag/backlog가 0이 될 때까지 기다린 뒤 scale-out/in하는 방식은 허용하지 않는다.
* shard count 변경은 startup-driven online migration으로 처리한다.
* 새 version은 write 대상이 되고, 이전 version은 read-only `DRAINING`으로 남긴다.
* consumer는 `ACTIVE`와 `DRAINING` version을 dual-read할 수 있다.
* old version이 충분히 drain되면 `DEPRECATED`로 전환한다.
* 한 stream prefix에는 동시에 하나의 migration만 허용한다.

## Ordering and Processing 요구사항

* 정상 처리 경로의 순서 보장 단위는 shard 전체가 아니라 `partitionKey`이다.
* 같은 shard 안에서도 서로 다른 key는 병렬 처리할 수 있다.
* 같은 key의 message는 stream id 순서대로 하나씩 처리한다.
* Redis Stream delivery는 `XREADGROUP` 후 정상 처리 완료 시 `XACK`하는 at-least-once 방식이다.
* `NOACK`은 사용하지 않는다.
* pending recovery는 shard owner만 수행한다.
* pending message는 `partitionKey`별 lane에 먼저 복원한 뒤 신규 message를 처리한다.
* 중복 side effect 방지는 idempotency key와 processing marker로 처리한다.

## Consumer Assignment 요구사항

* consumer liveness은 heartbeat 기반으로 관리한다.
* Pod는 local consumer member를 UUID `consumerId`로 생성한다.
* Pod별 local consumer 수는 `min(max-concurrency, maxReadableShardCount)`로 정한다.
* active consumer UUID snapshot, metadata version, assignment config version, consumer epoch을 기준으로 consumer view를 만든다.
* shard owner는 Pod가 아니라 active consumer UUID 단위로 계산한다.
* `handler-threads`, batch size, block timeout 같은 local-only 설정은 shard owner 계산에 영향을 주면 안 된다.
* scale-in/out 시 이동 대상 shard만 반환/acquire한다.
* graceful handoff가 timeout 안에 끝나지 않으면 lease TTL 만료 후 forced acquire로 전환한다.
* forced acquire 이후에는 Redis PEL recovery와 idempotency로 복구한다.

## 현재 문서화된 산출물

* [`AGENTS.md`](AGENTS.md): 폴더 작업 지침
* [`PRD.md`](PRD.md): PRD entrypoint와 lazy loading index
* [`prd/01-context-goals.md`](prd/01-context-goals.md): context, goals, non-goals
* [`prd/02-proposed-architecture.md`](prd/02-proposed-architecture.md): proposed architecture
* [`prd/03-routing-and-migration.md`](prd/03-routing-and-migration.md): producer routing and migration
* [`prd/04-consumer-assignment-rebalance.md`](prd/04-consumer-assignment-rebalance.md): consumer assignment and rebalance
* [`prd/05-processing-reliability.md`](prd/05-processing-reliability.md): processing, ordering, retry, failure handling
* [`prd/06-metadata-data-config.md`](prd/06-metadata-data-config.md): Redis metadata store, data model, configuration
* [`prd/07-observability-rollout-test.md`](prd/07-observability-rollout-test.md): observability, rollout, test plan
* [`prd/08-mvp-risks-open-questions.md`](prd/08-mvp-risks-open-questions.md): MVP scope, risks, open questions
