# Context, Goals, Non-Goals

## Context

현재 Redis Stream 사용은 `summary:{partition}`처럼 stream key를 여러 개 만들고, consumer group을 stream key마다 생성한 뒤 모든 runtime instance가 모든 stream을 listen하는 구조에 가깝다. 이 구조는 초기 처리량 확장에는 단순하지만, consumer scale-out/in, shard ownership, owner failover, shard count 변경, rolling deploy compatibility를 명확하게 제어하기 어렵다.

목표 모듈은 Redis Stream을 Kafka partition처럼 쓰려는 것이 아니라, Redis Stream의 제약을 인정한 상태에서 운영 가능한 sharded consumer layer를 제공하는 것이다.

## Goals

* scale-out/in 시 shard owner 자동 재분배
* shard count 변경은 새 stream version 생성과 active write 전환으로 처리하고, producer write를 중단하지 않는다.
* consumer는 `ACTIVE`와 `DRAINING` version을 병행 read하며, old `DRAINING` stream의 lag/pending/in-flight를 0까지 처리한 뒤 `DEPRECATED`로 전환한다.
* `application.yaml` desired routing spec과 metadata store active metadata reconciliation
* rolling deploy 중 서로 다른 yaml을 가진 instance 공존 지원
* producer와 consumer는 metadata store의 immutable metadata version을 source of truth로 사용해 routing/validation 수행
* Pod 내부 local consumer UUID 단위 deterministic assignment와 shard lease로 read 권한 제어
* shard owner crash 또는 lease loss 이후 새 owner가 Redis PEL pending message를 회수해 처리 재개
* Redis Stream consumer delivery는 `XREADGROUP` 후 정상 처리 완료 시 `XACK`
* processing marker와 idempotency key로 duplicate side effect 방지
* operator가 이해할 수 있는 상태 모델, metric, rollback path 제공

## Non-Goals

* shard 전체 serial ordering
* Kafka 수준의 broker-managed consumer group 재구현
* 외부 side effect까지 포함한 절대적 exactly-once
* 같은 stream version 안에서 shard count만 바꾸는 in-place resharding
* 전체 backlog drain을 전제로 하는 stop-the-world migration
* Redis Cluster resharding 자동 대응
* hot shard 자동 split
* runtime 중 `application.yaml` 변경 감지
* 여러 stream version migration 동시 실행
* MVP에서 Redis 8.6 `XADD IDMP` 필수 적용
* MVP에서 Kafka offset commit처럼 shard별 contiguous ACK frontier를 별도 관리하는 것

## Assumptions

* Redis Stream은 at-least-once delivery로 사용하고 `NOACK`은 사용하지 않는다.
* metadata store는 production에서 Redis를 기본값으로 둔다.
* Redis는 stream data plane과 lease/registry fast path에 사용한다.
* 각 application은 message별 `partitionKey`, `idempotencyKey`를 제공할 수 있다.
* consumer handler는 idempotency key 기반 재실행에 안전해야 한다.
* 한 stream prefix에는 동시에 하나의 active migration만 허용한다.
* DRAINING 자동 완료의 `lag 0`은 unread message lag 0, Redis PEL pending 0, local in-flight 0을 모두 만족한다는 뜻이다.
* Redis 8.6+ `XADD IDMP`는 producer retry 중복 stream entry를 줄이는 optional optimization으로 둔다.
