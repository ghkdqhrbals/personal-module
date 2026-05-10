# Consumer Registry, Assignment, and Rebalance

## Consumer Registry

각 Pod runtime은 시작 시 `podRunId` UUID를 만들고, Pod 내부에 여러 local consumer member를 생성한다. shard assignment의 최소 단위는 Pod가 아니라 local consumer member의 UUID `consumerId`이다.

```text
consumerId = UUID
podRunId = UUID
```

Registry heartbeat 값은 최소한 다음 정보를 포함한다.

```json
{
  "application": "client",
  "podName": "client-7d9f8f9c6b-x7n2k",
  "podRunId": "018f7fd2-7e9a-7b4e-9b1f-0a53e4d68f01",
  "consumerId": "018f7fd2-8a4e-79ac-b410-b84a21fd29d6",
  "localSlot": 0,
  "state": "ACTIVE",
  "streamPrefix": "summary",
  "consumerGroup": "summary-group",
  "readableVersions": ["v1", "v2"],
  "metadataVersion": 8,
  "assignmentConfigVersion": 3,
  "lastSeenAt": "2026-05-09T10:00:00Z"
}
```

Assignment candidate 조건:

* state가 `ACTIVE`
* heartbeat TTL 안에 갱신됨
* 같은 `streamPrefix`, `consumerGroup`
* metadata가 요구하는 readable stream version을 지원함
* metadata version 호환
* assignment config version 호환

## Local Consumer Concurrency

Pod는 metadata sync 이후 readable version의 shard count를 기준으로 local consumer member 수를 정한다.

```text
maxReadableShardCount =
  max(version.shardCount for version in readableVersions)

configuredMaxConcurrency =
  redis-stream.consumer.max-concurrency

effectiveLocalConsumerCount =
  min(configuredMaxConcurrency, maxReadableShardCount)
```

정책:

* `consumerId`는 local consumer member 생성 시 UUID로 만든다.
* metadata store는 consumer id를 발급하지 않는다. Pod가 UUID를 직접 만들고 metadata sync 이후 registry heartbeat로 공개한다.
* `configuredMaxConcurrency`가 없으면 `maxReadableShardCount`를 기본값으로 사용한다.
* migration 중 `ACTIVE`와 `DRAINING` version이 같이 있으면 두 version의 shard count 중 큰 값을 사용한다.
* old+new shard count 합계를 사용하지 않는다. migration 동안 local consumer 수가 일시적으로 두 배로 증가하지 않게 하기 위해서다.
* local consumer 수는 shard owner 계산의 입력인 active consumer UUID 수를 바꾼다.
* batch size, block timeout, handler thread count 같은 처리 튜닝 값은 owner 계산에 넣지 않는다.

## Synchronized Consumer View

Coordinator는 없지만 같은 snapshot을 본 consumer는 같은 owner를 계산해야 한다.

```text
consumerView =
  streamPrefix
  streamVersion
  metadataVersion
  assignmentConfigVersion
  consumerEpoch
  assignmentAlgorithm
  assignmentHashSeed
  activeConsumerIds(sorted UUIDs)
```

`consumerEpoch`은 consumer view epoch이며 local clock을 포함하지 않는다.

```text
consumerEpoch = hash(
  metadataVersion,
  assignmentConfigVersion,
  assignmentAlgorithm,
  assignmentHashSeed,
  sortedConsumerIds
)
```

## Assignment Algorithm

MVP 기본값은 Rendezvous Hashing이다.

```text
owner(streamVersion, shardIndex) =
  argmax(consumerId in activeConsumerIds) {
    hash(assignmentHashSeed, streamPrefix, streamVersion, shardIndex, consumerId)
  }
```

특성:

* 같은 active consumer UUID snapshot이면 같은 owner를 계산한다.
* 모든 consumer가 같은 순간에 같은 snapshot을 볼 필요는 없다.
* snapshot drift 중 실제 read 권한은 shard lease CAS가 결정한다.
* consumer 추가/제거 시 전체 shard가 아니라 일부 shard만 이동한다.
* Pod 단위 owner를 계산하지 않는다. 한 Pod 안의 여러 local consumer UUID가 서로 다른 shard를 맡을 수 있다.

## Rebalance Protocol

Rebalance는 coordinator command가 아니라 각 Pod의 local reconciliation이다. 각 Pod는 전체 active consumer UUID snapshot으로 owner map을 계산한 뒤, 그중 자기 Pod가 가진 local consumer UUID에 배정된 shard만 acquire한다.

```text
localConsumerIds = consumer UUIDs created by this Pod

targetOwnedShards =
  all shards whose owner is in localConsumerIds

currentOwnedShards =
  leases renewed by localConsumerIds

shardsToKeep = currentOwnedShards ∩ targetOwnedShards
shardsToReturn = currentOwnedShards - targetOwnedShards
shardsToAcquire = targetOwnedShards - currentOwnedShards
```

정책:

* `shardsToKeep`은 계속 read한다.
* `shardsToReturn`은 신규 `XREADGROUP ... >`를 중단하고 in-flight를 비운다.
* `shardsToAcquire`는 lease 획득 전까지 절대 read하지 않는다.
* owner 계산 결과가 같아도 lease가 없으면 read 권한이 없다.
* 같은 Pod 안에서도 consumer UUID별 target shard set을 따로 관리한다.

## Shard Return

Graceful return:

1. worker state를 `RETURNING`으로 바꾼다.
2. 해당 shard의 신규 read를 중단한다.
3. 이미 handler에 전달한 message를 timeout 안에서 완료한다.
4. 성공한 message는 processing marker 기록 후 `XACK`한다.
5. in-flight가 0이면 lease renew를 중단한다.
6. 가능하면 lease value를 `RELEASING`으로 갱신한다.

Forced return:

* process crash 또는 node failure에서는 graceful return이 불가능하다.
* heartbeat TTL과 lease TTL이 지난 뒤 새 owner가 acquire한다.
* 새 owner는 pending recovery를 먼저 수행한다.

## Shard Acquire

1. 현재 view에서 해당 local consumer UUID가 shard owner인지 확인한다.
2. `SET NX PX`로 lease를 획득한다.
3. lease value에 `consumerId`, `podRunId`, `leaseToken`, `metadataVersion`, `assignmentConfigVersion`, `consumerEpoch`을 기록한다.
4. shard worker를 `RECOVERING_PENDING`으로 전환한다.
5. `XPENDING`/`XAUTOCLAIM`으로 오래된 pending message를 회수한다.
6. recovery가 끝나면 `READING`으로 전환한다.
7. `XREADGROUP ... >`로 신규 message를 읽는다.

## Lease Fencing

Lease renew는 다음 값이 모두 일치할 때만 성공해야 한다.

* `consumerId`
* `leaseToken`
* `metadataVersion`
* `assignmentConfigVersion`
* `consumerEpoch`
* `podRunId`

`XREADGROUP` 직전, handler 실행 전, ACK 전에는 lease token이 여전히 유효한지 확인한다.
