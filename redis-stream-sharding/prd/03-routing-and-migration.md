# Producer Routing and Stream Version Migration

## Routing Contract

Producer는 local shard count를 직접 사용하지 않는다. 항상 metadata store에서 가져온 immutable snapshot을 기준으로 routing한다.

```text
metadata = metadataCache.activeWrite(streamPrefix)
routingKey = message.partitionKey
shardIndex = hash(metadata.hashAlgorithm, metadata.hashSeed, routingKey) % metadata.shardCount
streamKey = format(metadata.streamKeyFormat, streamPrefix, metadata.streamVersion, shardIndex)
```

`partitionKey`는 routing key이자 key ordering 단위이다. 별도의 ordering 전용 message field는 두지 않는다.

## Message Metadata

```json
{
  "partitionKey": "user-123",
  "streamPrefix": "summary",
  "streamVersion": "v2",
  "routingEpoch": 2,
  "shardIndex": 3,
  "partitionHashAlgorithm": "murmur3",
  "partitionHashSeed": "default",
  "metadataVersion": 8,
  "idempotencyKey": "summary:paper:123:requested"
}
```

## Startup Reconciliation

1. Pod는 `application.yaml`의 desired routing spec을 읽는다.
2. `desired-shard-count`, `hash-algorithm`, `hash-seed`로 `routingSpecHash`를 계산한다.
3. metadata store에서 `ACTIVE accepts_writes=true` version을 읽는다.
4. active metadata가 없으면 `v1`을 CAS insert하고 stream/group을 만든다.
5. active metadata와 desired hash가 같으면 migration 없이 join한다.
6. 다르면 active version을 수정하지 않고 next version 생성을 CAS로 시도한다.
7. CAS 실패 Pod는 metadata를 refresh하고 이미 생성된 migration state를 따른다.

## Online Migration

shard count 변경은 `hash(key) % shardCount`만 바꾸는 방식으로 처리하지 않는다. 새 version을 만들고 write target을 전환한다.

```text
old ACTIVE    accepts_writes=true  accepts_reads=true
new PREPARING accepts_writes=false accepts_reads=false

전환 후

old DRAINING  accepts_writes=false accepts_reads=true
new ACTIVE    accepts_writes=true  accepts_reads=true
```

Consumer는 `ACTIVE`와 `DRAINING` version을 모두 읽을 수 있다. Producer는 `ACTIVE accepts_writes=true` version에만 쓴다.

## Key Fence

같은 `partitionKey`가 old version과 new version에 동시에 있을 수 있다. strict ordering mode에서는 key fence로 old lane이 닫히기 전 new lane 실행을 막는다.

```text
redis-stream:key-fence:{streamPrefix}:{consumerGroup}:{partitionKey}
```

상태:

```text
OLD_OPEN -> OLD_DRAINING -> NEW_OPEN
```

정책:

* producer write target 전환이 먼저다.
* old version에 새 write가 더 들어오지 않는다는 write fence가 있어야 한다.
* new version message를 만났을 때 old version의 같은 key pending/in-flight가 있으면 대기한다.
* old key lane이 비고 ack frontier가 old tail 이상이면 `NEW_OPEN`으로 전환한다.
* fence store 장애 시 strict mode는 해당 key의 new version processing을 멈춘다.

## Migration Completion

old version을 `DEPRECATED`로 바꾸려면 다음 조건을 만족해야 한다.

* old version의 unread message lag가 0이다.
* Redis PEL pending이 0이다. max attempts를 초과한 `FAILED` message는 DLQ 이동 또는 수동 격리 후 `XACK` 등으로 PEL에서 제거되어야 한다.
* local in-flight message가 없다.
* strict key fence를 사용하는 경우 모든 fence가 `NEW_OPEN`으로 수렴했다.
* 최소 retention window가 지났다.
