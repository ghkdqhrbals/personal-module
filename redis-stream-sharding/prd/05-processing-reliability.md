# Processing, Ordering, Retry, and Failure Handling

## Delivery Semantics

Redis Stream consumer delivery는 at-least-once이다.

* consumer는 `XREADGROUP`으로 message를 읽는다.
* handler가 정상 완료된 뒤 processing marker를 기록한다.
* processing marker 기록이 성공한 message를 개별 `XACK`한다.
* `NOACK`은 사용하지 않는다.

외부 side effect까지 포함한 exactly-once는 보장하지 않는다. MVP의 중복 side effect 방지는 idempotency key와 processing marker로 제공한다.

## Key Ordered Parallel Processing

Shard fetch loop와 handler executor를 분리한다.

```text
Shard fetch loop
  -> XREADGROUP
  -> partitionKey 추출
  -> key lane queue enqueue

Key lane worker
  -> lane head 처리
  -> processing marker 기록
  -> XACK
  -> 다음 message 처리
```

보장:

* 같은 `streamPrefix + streamVersion + routingEpoch + partitionKey` message는 stream id 순서대로 handler가 실행된다.
* 서로 다른 `partitionKey`는 같은 shard 안에서도 병렬 처리할 수 있다.
* shard 간 순서는 보장하지 않는다.

## ACK Policy

Redis Stream consumer group은 pending message를 message id 단위로 `XACK`할 수 있다. MVP는 Kafka offset commit처럼 shard별 `lastAckedStreamId` 또는 contiguous ACK frontier를 별도 source of truth로 관리하지 않는다.

정책:

* handler 성공과 processing marker 기록이 모두 끝난 message만 `XACK`한다.
* handler 실패 또는 retry 대상 message는 `XACK`하지 않고 Redis PEL에 남긴다.
* pending recovery 중 이미 `PROCESSED` marker가 있는 message는 handler를 재실행하지 않고 `XACK`한다.
* 같은 `partitionKey` 순서는 lane scheduler가 보장하며, shard 전체 stream id 순서대로 ACK할 필요는 없다.

## Pending Recovery

새 shard owner는 신규 read 전에 pending recovery를 수행한다.

```redis
XAUTOCLAIM {streamKey} {groupName} {consumerName} {minIdleTime} {startId} COUNT {count}
```

정책:

* shard owner만 pending recovery를 수행한다.
* recovery 중에는 해당 shard의 신규 `XREADGROUP ... >`를 멈춘다.
* pending message를 stream id 순서로 lane scheduler에 복원한다.
* 이미 `PROCESSED` marker가 있으면 handler를 재실행하지 않고 ACK한다.
* max attempts 초과 시 `FAILED`로 표시한다.

## Ordering vs Failover Tradeoff

Redis PEL은 `min-idle-time`을 만족한 pending message만 reclaim할 수 있다. owner가 message를 읽고 ACK 전에 죽으면 새 owner는 즉시 그 message를 reclaim하지 못할 수 있다.

운영 모드:

```text
STRICT_ORDER
  같은 key의 pending recovery 완료 전 신규 lane 실행 금지
  failover 시 해당 key 처리 지연 가능

FAST_FAILOVER
  pending reclaim 가능 여부와 무관하게 신규 lane 실행 가능
  key 순서 보장은 포기
```

MVP 기본값은 `STRICT_ORDER`이다.

## Retry and Failure

* handler exception은 retry count를 증가시킨다.
* retry 가능한 failure는 같은 key lane을 block한다.
* max attempts 초과 시 `FAILED` marker를 남긴다.
* MVP에서는 `FAILED` message를 자동 ACK하지 않는다.
* DLQ 이동 후 ACK는 후속 고도화 범위로 둔다.

## Idempotency

Producer는 business event 단위 idempotency key를 생성한다. 같은 event retry는 같은 key를 재사용한다.

MVP 필수 경로:

```text
redis-stream:processing:{consumerGroup}:idempotency:{idempotencyKey}
  claim with SET NX or Lua CAS
```

Redis 8.6+ `XADD IDMP`는 producer retry 중복 stream entry 생성을 줄이는 최적화로 둔다.
