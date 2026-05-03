# Redis Stream Sharded Consumer Module 설계

## 1. 목적

Redis Stream을 여러 stream shard로 나누고, 여러 Pod가 shard를 중복 없이 나눠 읽도록 지원하는 Spring Boot/Kotlin 공통 모듈을 만든다.

Redis Stream은 Kafka처럼 partition assignment와 rebalance를 broker가 대신 해주지 않는다. 따라서 이 모듈은 다음 책임만 애플리케이션 레벨에서 제공한다.

* producer shard routing
* consumer group 초기화
* Pod 간 shard assignment
* Pod 내부 consumer thread 분배
* pending message recovery
* DLQ
* 기본 metric

본 모듈은 Redis Stream의 at-least-once delivery를 전제로 한다. 사용자 handler는 idempotent해야 한다.

---

## 2. 핵심 설계 요약

```text
Producer
  -> shardKey hash
  -> stream:{version}:{shardIndex}

Group Coordinator
  -> active Pod 목록 확인
  -> Sticky Balanced Assignment 생성
  -> assignment plan 저장

Consumer Pod
  -> 자기 Pod에 배정된 shard 확인
  -> shard lease 획득
  -> Redis XREADGROUP
  -> handler 처리
  -> XACK
```

핵심 정책:

* shard는 Pod 단위로 먼저 나눈다.
* 하나의 shard는 하나의 Pod만 owner가 될 수 있다.
* Pod 내부에서는 `active-threads`를 할당받은 shard들에 나눠 배정한다.
* shard마다 최소 1개 thread가 있어야 한다.
* Pod 증감 시 기존 shard owner는 가능한 유지한다. Kafka `StickyAssignor`와 같은 방향이다.
* 실제 read 권한은 shard lease로 fencing한다.

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

---

## 5. Consumer Name

consumer name은 Pod와 thread를 추적할 수 있어야 한다.

```text
{application}:{podName}:thread-{threadIndex}
```

예:

```text
order-api:order-api-7f9c9d9d7b-x82kd:thread-0
order-api:order-api-7f9c9d9d7b-x82kd:thread-1
```

thread마다 consumer name을 다르게 둔다. 그래야 `XPENDING`, `XINFO CONSUMERS`, dead consumer cleanup을 운영할 수 있다.

---

## 6. Group Coordinator

### 6.1 왜 필요한가

모든 Pod가 각자 assignment를 계산하면 순간적으로 서로 다른 결과를 볼 수 있다. Kafka처럼 rebalance를 일관되게 만들기 위해 Group Coordinator를 둔다.

Coordinator는 별도 서버가 아니다. 살아있는 Pod 중 하나가 Redis lease를 획득해 coordinator 역할을 수행한다.

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

coordinator가 죽으면 lease 갱신이 멈춘다. TTL이 지나면 다른 Pod가 coordinator lease를 획득한다.

### 6.3 Coordinator 책임

Coordinator만 assignment plan을 만든다.

책임:

* active Pod 목록 조회
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
    "pod-a": [0, 1, 2],
    "pod-b": [5, 6, 7],
    "pod-c": [3, 4, 8, 9]
  }
}
```

Follower Pod는 assignment를 직접 확정하지 않는다. 저장된 assignment plan을 읽고 자기 shard만 처리한다.

---

## 7. Pod Membership

### 7.1 Registry

각 Pod는 Redis에 heartbeat key를 등록한다.

```text
redis-stream:registry:{module}:pods:{podUid}
```

값 예:

```json
{
  "application": "order-api",
  "podName": "order-api-7f9c9d9d7b-x82kd",
  "podUid": "...",
  "state": "STABLE",
  "lastSeenAt": "2026-05-01T10:00:10Z"
}
```

기본 TTL:

```text
heartbeat-ttl = heartbeat-interval * 3
```

### 7.2 상태 변경 감지

상태 변경은 두 방식으로 감지한다.

* Redis Pub/Sub event: 빠른 감지용
* Redis registry scan: fallback 및 source of truth

Pub/Sub은 유실될 수 있으므로 event만 보고 assignment를 바꾸지 않는다. event를 받으면 registry snapshot을 다시 읽는다.

기본 감지 지연:

```text
fast path    ~= pubsub latency + registry refresh latency
fallback path <= rebalance-interval + scan duration
```

---

## 8. Pod State

Kafka consumer group 상태를 참고해 단순한 Pod state를 사용한다.

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

## 9. Sticky Balanced Assignment

### 9.1 목표

Kafka `StickyAssignor`처럼 기존 shard owner를 최대한 유지한다.

목표:

1. 모든 shard는 정확히 하나의 Pod에만 배정된다.
2. Pod별 shard 수는 최대한 균등해야 한다.
3. 기존 owner가 살아 있으면 가능한 그대로 유지한다.
4. Pod 증감 시 이동하는 shard 수를 최소화한다.

### 9.2 균등 기준

```text
base = shardCount / podCount
remainder = shardCount % podCount
```

각 Pod는 `base` 또는 `base + 1`개의 shard를 가진다.

예:

```text
10 shards / 3 pods = 3 / 3 / 4
```

### 9.3 Scale-out 예

기존:

```text
pod-0 -> 0, 1, 2, 3, 4
pod-1 -> 5, 6, 7, 8, 9
```

`pod-2` 추가 후:

```text
pod-0 -> 0, 1, 2
pod-1 -> 5, 6, 7
pod-2 -> 3, 4, 8, 9
```

range를 완전히 다시 나누지 않고, 기존 Pod의 shard를 최대한 유지한다.

### 9.4 Scale-in 예

기존:

```text
pod-0 -> 0, 1, 2
pod-1 -> 3, 4, 5
pod-2 -> 6, 7, 8, 9
```

`pod-2` 제거 후:

```text
pod-0 -> 0, 1, 2, 6, 7
pod-1 -> 3, 4, 5, 8, 9
```

남아 있는 Pod의 기존 shard는 유지하고, 사라진 Pod의 shard만 나눠 가진다.

---

## 10. Shard Lease

Coordinator가 assignment plan을 만들더라도 실제 read 권한은 shard lease로 확인한다.

```text
redis-stream:lease:{module}:{streamPrefix}:{version}:{shardIndex}
```

정책:

* assignment plan상 owner인 Pod만 shard lease를 획득한다.
* lease를 가진 Pod의 worker만 `XREADGROUP`을 수행한다.
* lease 갱신에 실패하면 해당 shard read를 중단하고 `DRAINING`한다.
* lease는 중복 read 시간을 줄이기 위한 fencing 장치이다. exactly-once를 보장하지 않는다.

---

## 11. Pod 내부 Thread 분배

설정:

```yaml
consumer:
  active-threads: 4
```

`active-threads`는 Pod 하나가 Redis Stream consume에 사용할 전체 thread 수이다.

규칙:

* Pod가 shard를 하나도 받지 않으면 thread를 만들지 않는다.
* Pod가 shard를 받으면 shard마다 최소 1개 thread를 배정한다.
* 따라서 `active-threads >= assignedShardCount`여야 한다.
* 남는 thread는 shard에 균등하게 추가 배정한다.

예:

```text
assigned shards = 6, 7, 8, 9
active-threads = 6

shard 6 -> thread-0
shard 7 -> thread-1
shard 8 -> thread-2, thread-3
shard 9 -> thread-4, thread-5
```

주의:

* 한 shard에 thread가 2개 이상이면 shard 내부 처리 순서는 보장되지 않는다.
* shard 내부 strict ordering이 필요하면 `active-threads == assignedShardCount`가 되도록 운영해야 한다.

---

## 12. Pending Recovery / Retry / DLQ

Redis Stream은 at-least-once이다. consumer가 죽으면 message는 PEL에 남는다.

Recovery:

```redis
XAUTOCLAIM {streamKey} {groupName} {consumerName} {minIdleTime} 0-0 COUNT {count}
```

정책:

* shard owner Pod의 worker만 해당 shard의 pending message를 reclaim한다.
* retry count가 `max-attempts`를 넘으면 DLQ로 이동한다.
* DLQ 이동 후 원본 message는 ACK한다.

DLQ key:

```text
{streamKey}:dlq
```

Dead consumer cleanup:

1. `XINFO CONSUMERS`로 idle consumer 확인
2. pending이 있으면 먼저 `XAUTOCLAIM`
3. pending이 0이면 `XGROUP DELCONSUMER`

---

## 13. Coordinator 장애 처리

Coordinator가 갑자기 죽어도 기존 shard owner는 즉시 멈추지 않는다.

흐름:

1. coordinator Pod가 죽는다.
2. coordinator lease renew가 멈춘다.
3. 기존 shard owner들은 shard lease가 살아 있는 동안 계속 consume한다.
4. coordinator TTL이 지나면 다른 Pod가 coordinator가 된다.
5. 새 coordinator가 이전 assignment plan을 읽는다.
6. Sticky Balanced Assignment로 새 generation을 만든다.
7. follower Pod들이 새 assignment plan을 적용한다.

stale plan 방지:

* assignment plan에는 `coordinatorEpoch`과 `generation`을 포함한다.
* Pod는 더 낮은 epoch/generation의 plan을 무시한다.
* coordinator lease owner가 아닌 Pod는 assignment plan을 덮어쓰지 않는다.

---

## 14. Shard Count 변경

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

## 15. 기본 설정

```yaml
redis-stream:
  enabled: true
  stream-prefix: notification
  stream-version: v1
  shard-count: 12
  consumer-group: notification-consumer

  group:
    start-id: "$"

  consumer:
    active-threads: 4
    batch-size: 50
    block-timeout: 2s

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

## 16. 필수 Metrics

최소 metric:

* `redis_stream_assigned_shards_per_pod`
* `redis_stream_active_threads_running`
* `redis_stream_pod_state`
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

---

## 17. MVP 범위

포함:

* stream shard routing
* consumer group initialization
* Redis lease 기반 Group Coordinator
* Sticky Balanced Assignment
* shard lease fencing
* `active-threads` 기반 Pod 내부 thread 분배
* XREADGROUP / XACK
* XAUTOCLAIM pending recovery
* DLQ
* dead consumer cleanup
* Micrometer metric

제외:

* exactly-once
* global ordering
* 동적 shard-count 변경
* Redis Cluster resharding 자동 대응
* hot shard 자동 split
