# Redis Stream Sharding 문서 작업 지침

이 폴더는 Redis Stream 기반 sharded consumer module의 제품 요구사항과 아키텍처 결정을 관리한다.

## 작업 원칙

* `PRD.md`는 큰 문서의 인덱스 역할만 한다.
* 상세 요구사항은 `prd/*.md`에 주제별로 나누어 둔다.
* 새 요구사항을 추가할 때는 `PRD.md`의 Lazy Loading Index에 링크를 먼저 추가한다.
* 원 설계 근거는 루트의 [`../REDIS_STREAM_SHAR_PLANNING.md`](../REDIS_STREAM_SHAR_PLANNING.md)를 기준으로 삼는다.
* 코드 구현과 다른 내용은 "현재 상태"와 "목표 상태"를 분리해서 쓴다.

## 아키텍처 기준

* 중앙 Group Coordinator를 만들지 않는다.
* shard owner 결정은 deterministic assignment, 실제 read 권한은 shard lease CAS로 분리한다.
* producer와 consumer는 metadata store의 stream metadata를 source of truth로 사용한다.
* shard count, hash algorithm, hash seed 변경은 기존 stream version을 수정하지 않고 새 stream version을 만든다.
* Redis Stream consumer delivery는 `XREADGROUP` + 정상 처리 후 `XACK`.
* 중복 side effect 방지는 idempotency key와 processing marker가 담당한다.
* 정상 경로의 ordering guarantee는 global/shard 단위가 아니라 `partitionKey` 단위이다.

## 문서 작성 규칙

* 보장 범위를 명시한다. 예: "exactly-once" 대신 "idempotency marker 기준 effectively-once side effect"처럼 쓴다.
* Redis PEL, lease TTL, heartbeat TTL, metadata version 같은 운영 제약을 숨기지 않는다.
* scale-out/in, rolling deploy, crash recovery, migration rollback을 요구사항에 포함한다.
* Mermaid diagram은 실제 flow를 설명할 때만 사용한다.
* 구현 세부가 확정되지 않은 항목은 `Open Questions` 또는 `Decision Needed`로 남긴다.

## 관련 코드 출발점

* 현재 partition 설정: [`../core/message/src/main/kotlin/org/ghkdqhrbals/message/redis/StreamConfig.kt`](../core/message/src/main/kotlin/org/ghkdqhrbals/message/redis/StreamConfig.kt)
* summary 기본 설정: [`../core/message/src/main/kotlin/org/ghkdqhrbals/message/redis/SummaryConfig.kt`](../core/message/src/main/kotlin/org/ghkdqhrbals/message/redis/SummaryConfig.kt)
* producer helper: [`../core/client/src/main/kotlin/org/ghkdqhrbals/client/domain/stream/StreamService.kt`](../core/client/src/main/kotlin/org/ghkdqhrbals/client/domain/stream/StreamService.kt)
* listener 등록: [`../core/client/src/main/kotlin/org/ghkdqhrbals/client/config/listener/RedisStreamConfiguration.kt`](../core/client/src/main/kotlin/org/ghkdqhrbals/client/config/listener/RedisStreamConfiguration.kt)
* ACK handler: [`../core/client/src/main/kotlin/org/ghkdqhrbals/client/config/listener/RedisStreamListener.kt`](../core/client/src/main/kotlin/org/ghkdqhrbals/client/config/listener/RedisStreamListener.kt)
