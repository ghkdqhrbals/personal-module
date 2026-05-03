# Redis Stream Sharded Consumer Module 설계

## 1. 목적

Redis Stream을 여러 stream shard로 나누고, 여러 runtime instance가 shard를 중복 없이 나눠 읽도록 지원하는 Spring Boot/Kotlin 공통 모듈을 만든다.

runtime instance는 실행 단위이다.

* Kubernetes 환경: Pod
* VM 환경: process
* bare-metal 환경: process
* local/dev 환경: application instance

Redis Stream은 Kafka처럼 partition assignment와 rebalance를 broker가 대신 해주지 않는다. 따라서 이 모듈은 다음 책임만 애플리케이션 레벨에서 제공한다.

* producer shard routing
* consumer group 초기화
* instance 간 shard assignment
* instance 내부 consumer thread 분배
* pending message recovery
* DLQ
* processing metadata 관리
* producer idempotency 선택
* consumer group join/rebalance
* shard 단위 순서 보장
* 기본 metric

Redis Stream의 중복 방지는 producer와 consumer를 분리해서 봐야 한다.

producer 측 중복 방지:

* Redis 8.6 이상에서는 `XADD`의 `IDMP` / `IDMPAUTO` 옵션을 사용한다.
* 같은 `producer-id`와 `idempotent-id` 조합이 이미 사용되었으면 새 entry를 만들지 않고 기존 entry ID를 반환한다.
* Redis 공식 문서 기준으로 이는 at-most-once production이다.
* producer가 Redis로 `XADD`를 보낸 뒤 응답을 받기 전에 네트워크가 끊겨 재시도하더라도 중복 entry를 만들지 않는다.

consumer 측 delivery:

* `XREADGROUP`으로 읽고 정상 처리 후 `XACK`한다.
* Redis는 message를 PEL(Pending Entries List)에 넣는다.
* consumer가 처리 후 `XACK`하기 전 죽으면 message는 pending으로 남아 reclaim될 수 있다.
* consumer delivery 관점에서는 at-least-once이다.
* `NOACK`은 사용하지 않는다.

따라서 producer retry로 인한 중복 stream entry를 막으려면 `XADD IDMP`를 사용한다.
consumer는 유실 방지를 우선해 `XACK` 기반 at-least-once 모드만 사용하고, handler는 idempotent해야 한다.

외부 시스템 side effect까지 포함한 "정확히 한 번 처리"는 Redis Stream 옵션만으로 만들 수 없다.
그 경우 외부 시스템도 idempotency key 또는 transaction을 지원해야 한다.

---

## 2. 핵심 설계 요약

```text
Producer
  -> shardKey hash
  -> stream:{version}:{shardIndex}
  -> XADD IDMP/IDMPAUTO

Group Coordinator
  -> active instance 목록 확인
  -> Sticky Balanced Assignment 생성
  -> assignment plan 저장

Consumer Instance
  -> 자기 instance에 배정된 shard 확인
  -> shard lease 획득
  -> Redis XREADGROUP
  -> handler 처리
  -> 성공 후 XACK
```

핵심 정책:

* shard는 runtime instance 단위로 먼저 나눈다.
* 하나의 shard는 하나의 instance만 owner가 될 수 있다.
* shard owner instance 안에서도 하나의 shard는 하나의 worker thread만 읽는다.
* 순서 보장은 shard 단위로만 제공한다.
* 순서 보장이 필요한 key는 같은 shard로 routing되어야 한다.
* instance 증감 시 기존 shard owner는 가능한 유지한다. Kafka `StickyAssignor`와 같은 방향이다.
* 실제 read 권한은 shard lease로 fencing한다.
* producer 중복 생성 방지는 `XADD IDMP` / `IDMPAUTO`로 선택한다.
* consumer는 정상 처리 후 `XACK`한다.

---

## 3. Stream Sharding

### 3.1 Stream Key

기본 형식:

```text
{stream-prefix}:{version}:{shard-index}
```

예:

```text
notification:v1:0
notification:v1:1
notification:v1:2
```

Redis Cluster에서는 같은 hash tag를 쓰지 않는다.

금지 예:

```text
notification:{v1}:0
notification:{v1}:1
```

위처럼 쓰면 모든 shard가 같은 Redis Cluster slot에 묶일 수 있다.

### 3.2 Producer Routing

```text
shardIndex = stableHash(shardKey) % shardCount
streamKey = "{streamPrefix}:{version}:{shardIndex}"
```

`stableHash`는 JVM `hashCode`에 의존하지 않는다.

권장:

* MurmurHash3
* xxHash
* CRC32C

순서 보장이 필요한 단위가 있다면 그 값을 `shardKey`로 사용한다.

예:

* userId
* orderId
* aggregateId
* brandId + userId

---

## 4. Consumer Group

모든 shard stream은 같은 consumer group name을 사용한다.

예:

```text
notification:v1:0 -> group: notification-consumer
notification:v1:1 -> group: notification-consumer
notification:v1:2 -> group: notification-consumer
```

단, Redis Stream consumer group은 stream key마다 따로 생성해야 한다.

```redis
XGROUP CREATE notification:v1:0 notification-consumer $ MKSTREAM
XGROUP CREATE notification:v1:1 notification-consumer $ MKSTREAM
XGROUP CREATE notification:v1:2 notification-consumer $ MKSTREAM
```

기본 start id는 `$`이다. 기존 backlog까지 읽어야 하는 경우에만 `0-0`을 사용한다.

### 4.1 Group Join

각 runtime instance는 시작 시 consumer group에 join한다.

join 절차:

1. instance identity를 만든다.
2. metadata store에 runtime instance heartbeat를 등록한다.
3. instance state를 `JOINING`으로 바꾼다.
4. coordinator가 active member snapshot을 읽는다.
5. coordinator가 generation을 증가시키고 shard assignment plan을 만든다.
6. instance는 자기 shard assignment를 받으면 `COMPLETING_REBALANCE`로 전환한다.
7. shard lease를 획득한 뒤 `STABLE`로 전환하고 consume을 시작한다.

Kafka처럼 broker가 group membership을 관리해주지 않으므로, 이 모듈이 metadata store와 coordinator lease로 group join/rebalance를 구현한다.

### 4.2 Partition Ownership

이 문서에서 partition은 Redis stream shard와 같다.

정책:

* 하나의 partition은 같은 generation 안에서 정확히 하나의 runtime instance에만 배정된다.
* 하나의 partition은 해당 owner instance 안에서 정확히 하나의 worker thread만 읽는다.
* worker는 assigned partition을 `XREADGROUP`으로 읽고, handler 정상 처리 후 `XACK`한다.
* 같은 partition에 대해 병렬 handler 실행을 하지 않는다.
* partition owner가 바뀔 때는 기존 owner가 `DRAINING` 후 shard lease를 놓고, 새 owner가 lease를 획득한 뒤 읽는다.

이 정책으로 Redis stream shard 내부의 message 순서를 보장한다.
전체 stream shard 간 global ordering은 보장하지 않는다.

---

## 5. Consumer Name

consumer name은 runtime instance와 thread를 추적할 수 있어야 한다.

```text
{application}:{instanceId}:thread-{threadIndex}
```

예:

```text
order-api:order-api-7f9c9d9d7b-x82kd:thread-0
order-api:order-api-7f9c9d9d7b-x82kd:thread-1
```

thread마다 consumer name을 다르게 둔다. 그래야 `XPENDING`, `XINFO CONSUMERS`, dead consumer cleanup을 운영할 수 있다.

instanceId 결정:

* Kubernetes: `podName` 또는 `podUid`
* VM/process: `{hostName}:{processId}`
* local/dev: `{application}:{randomInstanceId}`

중요한 점은 같은 시점에 살아 있는 instance끼리 ID가 겹치면 안 된다는 것이다.

---

## 6. Group Coordinator

### 6.1 왜 필요한가

모든 instance가 각자 assignment를 계산하면 순간적으로 서로 다른 결과를 볼 수 있다. Kafka처럼 rebalance를 일관되게 만들기 위해 Group Coordinator를 둔다.

Coordinator는 별도 서버가 아니다. 살아있는 instance 중 하나가 Redis lease를 획득해 coordinator 역할을 수행한다.

### 6.2 Coordinator Lease

```text
redis-stream:coordinator:{module}:{consumerGroup}
```

기본 설정:

```yaml
coordinator:
  ttl: 15s
  renew-interval: 5s
```

coordinator가 죽으면 lease 갱신이 멈춘다. TTL이 지나면 다른 instance가 coordinator lease를 획득한다.

### 6.3 Leader 선출 로직

모든 instance는 시작 후 coordinator lease 획득을 시도한다.

선출 규칙:

1. instance가 `SET coordinatorKey value NX PX ttl`을 실행한다.
2. 성공한 instance가 coordinator leader가 된다.
3. 실패한 instance는 follower가 된다.
4. leader는 `renew-interval`마다 lease를 갱신한다.
5. follower는 lease owner를 주기적으로 확인한다.
6. lease가 만료되면 follower들이 다시 `SET NX PX`를 시도한다.

leader value 예:

```json
{
  "instanceId": "host-a:12345",
  "identitySource": "HOST_PROCESS",
  "epoch": 4,
  "lastSeenAt": "2026-05-01T10:00:10Z"
}
```

갱신 규칙:

* leader는 자신이 owner일 때만 lease를 갱신한다.
* 갱신 전 `instanceId`와 `epoch`을 비교해 stale leader인지 확인한다.
* owner가 아니면 즉시 follower로 내려간다.
* 가능하면 `GET owner 확인 + PEXPIRE 갱신`은 Lua script로 원자 처리한다.

stale leader 방지:

* assignment plan에는 `coordinatorEpoch`을 포함한다.
* 새 leader는 이전 epoch보다 큰 epoch으로 assignment plan을 만든다.
* follower는 더 낮은 epoch의 assignment plan을 무시한다.

장애 처리:

* leader가 갑자기 죽으면 lease renew가 멈춘다.
* 최대 `coordinator.ttl` 이후 다른 instance가 leader가 된다.
* 기존 shard owner들은 shard lease가 유효한 동안 기존 assignment로 계속 consume한다.
* 새 leader가 assignment plan을 다시 저장하면 follower들이 새 generation을 적용한다.

### 6.4 Coordinator 책임

Coordinator만 assignment plan을 만든다.

책임:

* active instance 목록 조회
* Sticky Balanced Assignment 계산
* assignment generation 증가
* assignment plan 저장
* assignment 변경 event publish

assignment plan 예:

```json
{
  "generation": 17,
  "coordinatorEpoch": 4,
  "assignor": "STICKY_BALANCED",
  "assignments": {
    "instance-a": [0, 1, 2],
    "instance-b": [5, 6, 7],
    "instance-c": [3, 4, 8, 9]
  }
}
```

Follower instance는 assignment를 직접 확정하지 않는다. 저장된 assignment plan을 읽고 자기 shard만 처리한다.

---

## 7. Metadata Store

관리되는 metadata store가 필요하다.

이 저장소는 Redis Stream 자체가 제공하지 않는 실행 상태와 처리 상태를 관리한다.

권장 저장소:

* 운영 기본값: RDBMS(PostgreSQL/MySQL)
* 경량 환경: Redis Hash/JSON
* 강한 idempotency 요구: business DB와 같은 RDBMS

강한 idempotency 보장이 필요하면 business side effect와 metadata update가 같은 DB transaction 안에 있어야 한다.
따라서 신규 stream 기능을 사용하려면 metadata store를 먼저 구성해야 한다.

metadata store가 없는 모드는 단일 instance 개발 환경에서만 허용한다.
이 경우 shard assignment, consumer cleanup, group join/rebalance 보장은 제공하지 않는다.

관리할 metadata:

* consumer group
* runtime instance
* consumer thread
* shard assignment
* shard lease owner
* message processing state
* idempotency key
* retry count
* DLQ 기록

핵심 테이블 예:

```text
stream_runtime_instance
  instance_id
  identity_source
  application
  host
  process_id
  pod_name       # Kubernetes optional
  pod_uid        # Kubernetes optional
  state
  last_seen_at

stream_assignment
  group_name
  stream_prefix
  stream_version
  generation
  instance_id
  shard_index

stream_message_processing
  group_name
  stream_key
  message_id
  idempotency_key
  state          # CLAIMED, PROCESSED, FAILED, DLQ
  owner_instance_id
  attempt_count
  updated_at
```

message 처리 상태 전이:

```text
NEW
-> CLAIMED
-> PROCESSED

CLAIMED
-> FAILED
-> CLAIMED

FAILED
-> DLQ
```

---

## 8. Message Semantics

### 8.1 Producer Idempotency

Redis 8.6 이상에서는 `XADD`에 idempotent 옵션을 사용할 수 있다.
모듈은 producer idempotency가 켜져 있으면 Redis server version과 stream별 IDMP 설정을 시작 시 검증해야 한다.

```redis
XADD {streamKey} IDMP {producerId} {idempotentId} * field value
```

또는 Redis가 idempotent id를 자동 생성하게 할 수 있다.

```redis
XADD {streamKey} IDMPAUTO {producerId} * field value
```

특징:

* 같은 `producerId` + `idempotentId` 조합은 한 번만 stream entry를 만든다.
* 중복 `XADD`가 들어오면 새 entry를 만들지 않고 기존 entry ID를 반환한다.
* producer retry로 인한 중복 message 생성을 막는다.
* `IDMP` / `IDMPAUTO`는 entry ID가 `*`일 때만 사용할 수 있다.
* retention은 `XCFGSET`의 `IDMP-DURATION`, `IDMP-MAXSIZE`로 관리한다.

설정 예:

```redis
XCFGSET {streamKey} IDMP-DURATION 300 IDMP-MAXSIZE 1000
```

이 기능은 producer side의 at-most-once production을 만든다.
consumer가 읽은 뒤 재전달되는지 여부는 아래 consumer delivery 정책이 결정한다.

### 8.2 Consumer Delivery

Redis `XREADGROUP`을 `NOACK` 없이 사용하고, 처리 성공 후 `XACK`한다.

```redis
XREADGROUP GROUP {groupName} {consumerName} COUNT {count} BLOCK {blockMs} STREAMS {streamKey} >
```

특징:

* message를 PEL에 넣는다.
* 처리 성공 후 `XACK`해야 한다.
* consumer가 죽으면 message는 pending으로 남고 reclaim될 수 있다.
* 같은 message가 재전달될 수 있으므로 handler는 idempotent해야 한다.
* pending recovery, retry, DLQ를 사용할 수 있다.
* `NOACK`은 사용하지 않는다.

### 8.3 Idempotent Processing

consumer 재전달로 인한 중복 side effect를 막아야 하면 metadata store와 idempotency key를 사용한다.

처리 흐름:

1. `XREADGROUP`으로 메시지를 읽는다.
2. metadata store에 `idempotencyKey`로 processing row를 생성한다.
3. 이미 `PROCESSED`이면 handler를 실행하지 않고 `XACK`만 한다.
4. row가 없으면 `CLAIMED`로 insert한다.
5. 같은 transaction 안에서 business side effect를 수행한다.
6. 같은 transaction 안에서 processing state를 `PROCESSED`로 바꾼다.
7. transaction commit 이후 `XACK`한다.

RDBMS 사용 시 예:

```sql
INSERT INTO stream_message_processing(idempotency_key, state, owner_instance_id)
VALUES (?, 'CLAIMED', ?)
ON CONFLICT (idempotency_key) DO NOTHING;
```

처리 규칙:

* insert 성공: 이 consumer가 처리 권한을 가진다.
* 이미 `PROCESSED`: 중복 전달이므로 side effect 없이 ACK한다.
* 이미 `CLAIMED`이고 timeout 이전: 다른 consumer가 처리 중이므로 ACK하지 않는다.
* 이미 `CLAIMED`이고 timeout 초과: reclaim 정책에 따라 owner를 바꿀 수 있다.

이 구조에서 Redis ACK는 최종 commit 이후 수행한다.

장애별 결과:

* commit 전 consumer 죽음: metadata가 `CLAIMED`로 남고 나중에 reclaim된다.
* commit 후 ACK 전 consumer 죽음: Redis가 다시 전달하지만 metadata가 `PROCESSED`라 side effect 없이 ACK된다.
* 외부 API 호출 후 metadata commit 전 죽음: 외부 API가 idempotency key를 지원하지 않으면 중복 side effect 방지 불가.

---

## 9. Runtime Membership

### 9.1 Registry

각 runtime instance는 metadata store 또는 Redis에 heartbeat를 등록한다.

```text
redis-stream:registry:{module}:instances:{instanceId}
```

값 예:

```json
{
  "application": "order-api",
  "instanceId": "host-a:12345",
  "podName": "order-api-7f9c9d9d7b-x82kd",
  "podUid": "...",
  "state": "STABLE",
  "lastSeenAt": "2026-05-01T10:00:10Z"
}
```

`podName`, `podUid`는 Kubernetes 환경에서만 채운다. VM, bare-metal, local/dev 환경에서는 비워도 된다.

기본 TTL:

```text
heartbeat-ttl = heartbeat-interval * 3
```

### 9.2 상태 변경 감지

상태 변경은 두 방식으로 감지한다.

* Redis Pub/Sub event: 빠른 감지용
* Redis registry scan: fallback 및 source of truth

Pub/Sub은 유실될 수 있으므로 event만 보고 assignment를 바꾸지 않는다. event를 받으면 registry snapshot을 다시 읽는다.

기본 감지 지연:

```text
fast path    ~= pubsub latency + registry refresh latency
fallback path <= rebalance-interval + scan duration
```

### 9.3 Instance Identity

shard assignment의 기준은 Pod가 아니라 `instanceId`이다.

`instanceId` 생성 규칙:

* Kubernetes: `podUid` 우선, 없으면 `podName`
* VM/bare-metal: `{hostName}:{processId}:{startTime}`
* local/dev: `{application}:{randomUuid}`
* 수동 지정: `redis-stream.instance.id` 값 사용

수동 지정은 같은 consumer group 안에서 중복되면 안 된다.
중복 `instanceId`가 감지되면 나중에 등록한 instance를 `DEGRADED`로 두고 assignment 대상에서 제외한다.

Pod를 쓰지 않는 환경에서도 coordinator는 active `instanceId` 목록만 보고 shard를 배정한다.
따라서 Kubernetes API 의존성은 없다.

---

## 10. Instance State

Kafka consumer group 상태를 참고해 단순한 instance state를 사용한다.

```text
STARTING
JOINING
PREPARING_REBALANCE
COMPLETING_REBALANCE
STABLE
DRAINING
DEGRADED
STOPPED
```

의미:

* `STARTING`: registry 등록 전
* `JOINING`: registry 등록 후 첫 assignment 대기
* `PREPARING_REBALANCE`: 기존 shard 반납 준비
* `COMPLETING_REBALANCE`: 새 assignment 적용 중
* `STABLE`: 정상 consume 중
* `DRAINING`: 종료 또는 shard 반납 중
* `DEGRADED`: 설정 부족 등으로 새 shard를 맡을 수 없음
* `STOPPED`: 종료 또는 TTL 만료

assignment owner 후보:

* 포함: `JOINING`, `STABLE`, `PREPARING_REBALANCE`, `COMPLETING_REBALANCE`
* 제외: `STARTING`, `DRAINING`, `DEGRADED`, `STOPPED`

---

## 11. Sticky Balanced Assignment

### 11.1 목표

Kafka `StickyAssignor`처럼 기존 shard owner를 최대한 유지한다.

목표:

1. 모든 shard는 정확히 하나의 instance에만 배정된다.
2. instance별 shard 수는 최대한 균등해야 한다.
3. 기존 owner가 살아 있으면 가능한 그대로 유지한다.
4. instance 증감 시 이동하는 shard 수를 최소화한다.

### 11.2 균등 기준

```text
base = shardCount / instanceCount
remainder = shardCount % instanceCount
```

각 instance는 `base` 또는 `base + 1`개의 shard를 가진다.

예:

```text
10 shards / 3 instances = 3 / 3 / 4
```

### 11.3 Scale-out 예

기존:

```text
instance-0 -> 0, 1, 2, 3, 4
instance-1 -> 5, 6, 7, 8, 9
```

`instance-2` 추가 후:

```text
instance-0 -> 0, 1, 2
instance-1 -> 5, 6, 7
instance-2 -> 3, 4, 8, 9
```

range를 완전히 다시 나누지 않고, 기존 instance의 shard를 최대한 유지한다.

### 11.4 Scale-in 예

기존:

```text
instance-0 -> 0, 1, 2
instance-1 -> 3, 4, 5
instance-2 -> 6, 7, 8, 9
```

`instance-2` 제거 후:

```text
instance-0 -> 0, 1, 2, 6, 7
instance-1 -> 3, 4, 5, 8, 9
```

남아 있는 instance의 기존 shard는 유지하고, 사라진 instance의 shard만 나눠 가진다.

---

## 12. Shard Lease

Coordinator가 assignment plan을 만들더라도 실제 read 권한은 shard lease로 확인한다.

```text
redis-stream:lease:{module}:{streamPrefix}:{version}:{shardIndex}
```

정책:

* assignment plan상 owner인 instance만 shard lease를 획득한다.
* lease를 가진 instance의 worker만 `XREADGROUP`을 수행한다.
* lease 갱신에 실패하면 해당 shard read를 중단하고 `DRAINING`한다.
* lease는 중복 read 시간을 줄이기 위한 fencing 장치이다.
* 최종 중복 side effect 방지는 metadata store의 processing state와 idempotency key가 담당한다.

---

## 13. Instance 내부 Thread 분배

설정:

```yaml
consumer:
  active-threads: 4
```

`active-threads`는 instance 하나가 Redis Stream consume에 사용할 전체 thread 수이다.

규칙:

* instance가 shard를 하나도 받지 않으면 thread를 만들지 않는다.
* instance가 shard를 받으면 최대 `active-threads`개 worker thread를 만든다.
* 하나의 shard는 정확히 하나의 worker thread에만 배정한다.
* 한 worker thread는 여러 shard를 순차적으로 읽을 수 있다.
* `active-threads > assignedShardCount`이면 남는 thread는 만들지 않는다.
* shard 하나에 worker thread를 2개 이상 붙이지 않는다.

예:

```text
assigned shards = 6, 7, 8, 9
active-threads = 2

shard 6 -> thread-0
shard 7 -> thread-0
shard 8 -> thread-1
shard 9 -> thread-1
```

주의:

* 같은 shard의 message는 같은 worker thread에서 순차 처리한다.
* 한 worker가 여러 shard를 맡아도 shard별 처리 순서는 worker 내부에서 보장한다.
* shard 간 순서는 보장하지 않는다.
* shard별 handler 처리는 동시에 실행하지 않는다.

---

## 14. Pending Recovery / Retry / DLQ

Redis Stream은 consumer가 죽으면 message를 PEL에 남긴다. 모듈은 PEL recovery와 metadata store의 processing state를 함께 사용한다.

순서 보장 정책:

* pending recovery도 shard owner instance의 단일 worker만 수행한다.
* recovery 중에는 해당 shard의 신규 message read를 잠시 멈춘다.
* pending message를 먼저 처리하고 ACK한 뒤 신규 message read를 재개한다.
* 처리 실패 message를 건너뛰고 뒤 message를 먼저 ACK하면 shard 내부 순서 보장이 깨질 수 있다.

Recovery:

```redis
XAUTOCLAIM {streamKey} {groupName} {consumerName} {minIdleTime} 0-0 COUNT {count}
```

정책:

* shard owner instance의 worker만 해당 shard의 pending message를 reclaim한다.
* retry count가 `max-attempts`를 넘으면 DLQ로 이동한다.
* DLQ 이동 후 원본 message는 ACK한다.
* 이미 metadata store에 `PROCESSED`로 기록된 message는 handler를 다시 실행하지 않고 ACK한다.

DLQ key:

```text
{streamKey}:dlq
```

Dead consumer cleanup:

1. `XINFO CONSUMERS`로 idle consumer 확인
2. pending이 있으면 먼저 `XAUTOCLAIM`
3. pending이 0이면 `XGROUP DELCONSUMER`

---

## 15. Coordinator 장애 처리

Coordinator가 갑자기 죽어도 기존 shard owner는 즉시 멈추지 않는다.

흐름:

1. coordinator instance가 죽는다.
2. coordinator lease renew가 멈춘다.
3. 기존 shard owner들은 shard lease가 살아 있는 동안 계속 consume한다.
4. coordinator TTL이 지나면 다른 instance가 coordinator가 된다.
5. 새 coordinator가 이전 assignment plan을 읽는다.
6. Sticky Balanced Assignment로 새 generation을 만든다.
7. follower instance들이 새 assignment plan을 적용한다.

stale plan 방지:

* assignment plan에는 `coordinatorEpoch`과 `generation`을 포함한다.
* instance는 더 낮은 epoch/generation의 plan을 무시한다.
* coordinator lease owner가 아닌 instance는 assignment plan을 덮어쓰지 않는다.

---

## 16. Shard Count 변경

`shard-count`는 같은 prefix 안에서 바꾸면 안 된다.

예:

```text
hash(user-123) % 6  = 2
hash(user-123) % 12 = 8
```

같은 partition key가 다른 shard로 바뀌므로 기존 stream을 찾을 수 없게 된다.

정책:

* `shard-count`는 stream prefix version에 묶인 불변값이다.
* shard 수를 바꾸려면 새 prefix version을 만든다.

예:

```text
notification:v1:0 ~ notification:v1:5
notification:v2:0 ~ notification:v2:11
```

전환 절차:

1. v2 stream과 group 생성
2. producer dual-write 또는 v2 write 전환
3. consumer가 v1/v2를 함께 읽을 수 있게 배포
4. v1 backlog drain
5. v2만 사용
6. v1 제거

message에는 routing metadata를 넣는다.

```json
{
  "partitionKey": "user-123",
  "streamPrefix": "notification:v1",
  "shardIndex": 2,
  "hashAlgorithm": "murmur3",
  "idempotencyKey": "..."
}
```

---

## 17. 기본 설정

```yaml
redis-stream:
  enabled: true
  stream-prefix: notification
  stream-version: v1
  shard-count: 12
  consumer-group: notification-consumer

  group:
    start-id: "$"

  instance:
    id: null
    identity-source: AUTO

  consumer:
    active-threads: 4
    batch-size: 50
    block-timeout: 2s
    ack-mode: ACK_AFTER_SUCCESS
    ordering: SHARD

  producer:
    idempotency:
      enabled: true
      mode: IDMP
      producer-id: "${spring.application.name}"
      idmp-duration: 300s
      idmp-max-size: 1000

  metadata-store:
    type: RDBMS
    transaction-required: true
    claim-timeout: 120s

  coordinator:
    ttl: 15s
    renew-interval: 5s

  assignment:
    strategy: STICKY_BALANCED
    rebalance-interval: 5s

  shard-lease:
    ttl: 20s
    renew-interval: 5s

  pending:
    reclaim-enabled: true
    min-idle-time: 60s
    scan-interval: 30s
    batch-size: 100

  retry:
    max-attempts: 5

  dlq:
    enabled: true
    suffix: dlq
```

---

## 18. 필수 Metrics

최소 metric:

* `redis_stream_assigned_shards_per_instance`
* `redis_stream_active_threads_running`
* `redis_stream_instance_state`
* `redis_stream_coordinator_active`
* `redis_stream_assignment_generation`
* `redis_stream_shard_lease_lost_total`
* `redis_stream_messages_read_total`
* `redis_stream_messages_ack_total`
* `redis_stream_messages_failed_total`
* `redis_stream_messages_dlq_total`
* `redis_stream_pending_count`
* `redis_stream_lag`
* `redis_stream_reclaim_total`
* `redis_stream_processed_duplicate_total`
* `redis_stream_processing_claim_failed_total`

---

## 19. MVP 범위

포함:

* stream shard routing
* XADD IDMP / IDMPAUTO producer idempotency
* consumer group initialization
* consumer group join/rebalance
* runtime instance identity 관리
* Redis lease 기반 Group Coordinator
* metadata store 기반 processing state 관리
* XREADGROUP + ACK_AFTER_SUCCESS consumer processing
* Sticky Balanced Assignment
* shard lease fencing
* shard 단위 순서 보장
* `active-threads` 기반 instance 내부 worker thread 분배
* XADD IDMP / XREADGROUP / XACK
* XAUTOCLAIM pending recovery
* DLQ
* dead consumer cleanup
* Micrometer metric

제외:

* global ordering
* 동적 shard-count 변경
* Redis Cluster resharding 자동 대응
* hot shard 자동 split
