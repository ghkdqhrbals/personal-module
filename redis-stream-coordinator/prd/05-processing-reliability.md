# Processing, Reliability, and Fencing

## Delivery Semantics

Redis Stream consumer delivery는 at-least-once이다.

* member는 shard lease가 있는 shard만 `XREADGROUP`한다.
* handler가 정상 완료된 뒤 processing marker를 기록한다.
* marker 기록이 성공한 message만 `XACK`한다.
* `NOACK`은 사용하지 않는다.

## Fencing

`XREADGROUP`, handler 실행, `XACK` 직전에는 다음 값을 검증한다.

* `memberId`
* `memberEpoch`
* `assignmentEpoch`
* `leaseToken`
* `streamVersion`
* `shardIndex`

검증 실패 시 신규 read와 ack를 중단하고 coordinator에 state를 보고한다.

## Pending Recovery

새 owner는 assign 후 바로 신규 read하지 않는다.

```text
assigned shard
  -> lease acquire
  -> XPENDING scan
  -> XAUTOCLAIM stale pending
  -> already PROCESSED면 XACK
  -> handler 재실행 필요하면 key lane에 enqueue
  -> recovery 완료 후 XREADGROUP ... >
```

## Key Ordered Processing

보장 범위는 `partitionKey` 단위이다.

* 같은 `partitionKey`는 stream id 순서대로 하나씩 처리한다.
* 서로 다른 key는 같은 shard 안에서도 병렬 처리할 수 있다.
* shard 전체 serial ordering은 보장하지 않는다.
* migration 중 cross-version key ordering이 필요한 stream은 key fence를 사용한다.

## Idempotency

Producer는 business event 단위 idempotency key를 제공한다. Consumer는 processing marker를 idempotency key 기준으로 claim한다.

```text
redis-stream:processing:{consumerGroup}:idempotency:{idempotencyKey}
```

상태:

```text
PROCESSING -> PROCESSED
PROCESSING -> FAILED
```

MVP는 external side effect와 marker write를 하나의 transaction으로 묶지 않는다. 따라서 exactly-once가 아니라 effectively-once side effect를 목표로 한다.

## Failure Cases

* member crash after read before ack: Redis PEL에 남고 새 owner가 `XAUTOCLAIM`한다.
* handler success before ack crash: marker가 있으면 새 owner는 handler를 재실행하지 않고 `XACK`한다.
* coordinator failover: 새 coordinator가 durable target/current assignment를 읽어 event loop를 재개한다.
* stale member resumes: member epoch 또는 lease token mismatch로 fenced된다.
