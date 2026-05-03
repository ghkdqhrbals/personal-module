# Redis Stream Sharded Consumer Module 설계 문서

1. 목적

본 모듈은 Redis Stream을 N개의 shard stream으로 분산하고, Spring Boot/Kotlin 애플리케이션의 여러 Pod 및 Pod 내부 consumer thread들이 shard를 균등하게 읽도록 지원하는 공통 Gradle 모듈이다.

Kafka처럼 broker 레벨의 partition rebalance가 제공되지 않는 Redis Stream 환경에서, 애플리케이션 레벨에서 shard assignment, consumer lifecycle, pending message recovery, backpressure, observability를 일관되게 제공하는 것을 목표로 한다.

본 모듈은 oauth, time과 같은 공통 인프라 모듈처럼 각 서비스에서 의존성으로 추가하여 사용할 수 있어야 한다.

⸻

2. 설계 목표

2.1 기능 목표

* Redis Stream을 N개의 stream key로 샤딩한다.
* producer는 shard key 기반으로 특정 stream shard에 메시지를 발행한다.
* consumer group name은 서비스에서 직접 설정할 수 있어야 한다.
* 모든 shard는 동일한 consumer group name을 사용한다.
* 각 Pod는 할당받은 shard마다 설정된 수의 consumer thread를 생성한다.
* Pod 증감 시 shard assignment가 자동으로 재계산되어야 한다.
* 동일 shard를 여러 Pod가 중복 소유하지 않도록 한다.
* shard별 consumer thread 수는 설정 가능해야 하며 최소 1 이상이어야 한다.
* consumer 장애, Pod 종료, thread 중단 시 pending message를 회수할 수 있어야 한다.
* Redis Stream 관련 metric을 Micrometer 기반으로 노출한다.

2.2 비기능 목표

* Spring Boot auto-configuration 기반으로 쉽게 사용할 수 있어야 한다.
* Kotlin coroutine 또는 thread executor 기반 처리 방식을 선택할 수 있어야 한다.
* 처리 실패 시 retry, DLQ, pending reclaim 정책을 설정 가능해야 한다.
* Redis 장애 시 무한 busy loop를 방지해야 한다.
* 메시지 처리 로직은 사용자 코드로 분리되어야 한다.
* 모듈은 Redis Stream의 at-least-once 특성을 전제로 설계한다.

⸻

3. 전체 구조

application
└── redis-stream-shard-consumer-module
├── RedisStreamShardProperties
├── StreamShardResolver
├── StreamShardAssigner
├── GroupCoordinator
├── GroupAssignmentStore
├── StreamConsumerSupervisor
├── StreamConsumerWorker
├── StreamMessageHandler<T>
├── PendingMessageReclaimer
├── DeadConsumerCleaner
├── ShardLeaseManager
├── DeadLetterPublisher
└── RedisStreamMetricsCollector

⸻

4. Stream Sharding 모델

4.1 Stream Key 규칙

기본 stream key는 prefix와 shard index로 구성한다.

{stream-prefix}:{shard-index}

예시:

notification:0
notification:1
notification:2
notification:3
notification:4
notification:5

Redis Cluster를 사용할 경우 stream shard key가 서로 다른 hash slot에 분산되어야 한다.

권장:

notification:v1:0
notification:v1:1
notification:v1:2

주의:

notification:{v1}:0
notification:{v1}:1

위처럼 동일 hash tag를 사용하면 모든 shard가 같은 slot으로 묶여 샤딩 효과가 사라질 수 있다.

Stream key에는 hash tag를 사용하지 않는 것을 기본 원칙으로 한다. 단, Redis Cluster에서 Lua script나 transaction으로 여러 key를 함께 다뤄야 하는 요구가 있다면, 그 기능은 별도 설계로 분리한다.

4.2 Shard 수

Shard 수는 설정으로 주입한다.

redis-stream:
enabled: true
stream-prefix: notification
shard-count: 6
consumer-group: notification-consumer-group

4.3 Producer Shard 결정

Producer는 message key 또는 business key를 hash하여 shard를 결정한다.

shardIndex = hash(shardKey) % shardCount
streamKey = "$streamPrefix:$shardIndex"

권장 shard key:

* userId
* orderId
* brandId + userId
* multiLoginKey
* aggregateId

순서 보장이 필요한 단위가 있다면 해당 단위를 shard key로 사용한다.

Hash 함수는 JVM 기본 hashCode에 의존하지 않는다.

권장:

* MurmurHash3
* xxHash
* CRC32C

이유:

* JVM 버전이나 구현 차이에 영향을 받지 않아야 한다.
* producer/consumer가 여러 언어로 확장되어도 동일한 shard routing이 가능해야 한다.
* shard-count 변경 전략에서 v1/v2 dual-write를 검증하기 쉽다.

4.4 Consumer Group 생성 시점

모듈은 모든 shard stream에 대해 consumer group을 초기화해야 한다.

기본 정책:

XGROUP CREATE {streamKey} {groupName} $ MKSTREAM

의미:

* 모듈 도입 이후 새로 들어오는 메시지부터 처리한다.
* 이미 stream에 쌓여 있던 과거 메시지는 기본적으로 처리하지 않는다.

초기 적재분까지 처리해야 하는 서비스는 설정으로 시작 ID를 바꿀 수 있어야 한다.

redis-stream:
group:
start-id: "$" # "$" 또는 "0-0"

주의:

* "0-0"은 기존 backlog를 모두 읽기 때문에 운영 도입 시 의도치 않은 대량 재처리가 발생할 수 있다.
* 이미 존재하는 group에 대해서는 BUSYGROUP을 정상 상태로 간주한다.
* shard 일부만 group 생성에 실패하면 supervisor는 consume을 시작하지 않고 초기화 실패로 보고해야 한다.

⸻

5. Consumer Group 정책

5.1 동일 Consumer Group 사용

모든 shard stream은 동일한 consumer group name을 사용한다.

notification:0 -> group: notification-consumer-group
notification:1 -> group: notification-consumer-group
notification:2 -> group: notification-consumer-group

단, Redis Stream의 consumer group은 stream key별로 생성되므로, 모듈은 각 shard stream마다 동일한 group name을 생성해야 한다.

XGROUP CREATE notification:0 notification-consumer-group $ MKSTREAM
XGROUP CREATE notification:1 notification-consumer-group $ MKSTREAM
XGROUP CREATE notification:2 notification-consumer-group $ MKSTREAM

5.2 Group Name 설정

consumer group name은 모듈 내부에서 고정하지 않고 서비스 설정에서 직접 주입한다.

redis-stream:
consumer-group: order-notification-consumer

⸻

6. Consumer 이름 정책

Consumer name은 Pod, instance, thread 정보를 조합한다.

{application-name}:{pod-name}:{thread-index}

예시:

order-api:order-api-7f9c9d9d7b-x82kd:0
order-api:order-api-7f9c9d9d7b-x82kd:1
order-api:order-api-7f9c9d9d7b-x82kd:2

Consumer name은 Redis PEL에 남기 때문에, 추적 가능성과 운영 편의성을 위해 안정적인 식별자를 포함해야 한다.

주의:

* consumer name은 재시작마다 무작위 UUID만 사용하지 않는다.
* Pod 이름과 thread index를 포함해야 XINFO CONSUMERS, XPENDING 결과를 보고 어떤 실행 단위가 pending을 남겼는지 추적할 수 있다.
* Pod가 새로 뜨면 consumer name도 새로 생기는 것이 자연스럽다. 이전 Pod의 consumer metadata는 cleanup 절차로 제거한다.
* 하나의 process 안에서 여러 thread가 같은 consumer name을 공유하지 않는다. Redis PEL이 consumer name 기준으로 관리되기 때문에 thread 단위 추적이 불가능해진다.

⸻

7. Shard Assignment 설계

7.1 문제 정의

Redis Stream은 Kafka처럼 consumer group 내 partition rebalance를 자동으로 제공하지 않는다. 따라서 다음을 애플리케이션에서 직접 해결해야 한다.

* Pod 수 증가 시 shard 재분배
* Pod 수 감소 시 orphan shard 회수
* Pod 간 shard 중복 할당 방지
* Pod 간 shard 수 균등 분배
* Pod 내부 thread는 설정된 activeThreads를 할당받은 shard 수에 맞춰 분배
* 동일 shard를 여러 Pod가 중복 소비하는 상황 방지
* assignment 변경 중 race condition 방지

⸻

7.2 Assignment 단위

Assignment 단위는 shard이다.

Pod 간 assignment와 Pod 내부 thread assignment를 분리한다.

1단계: shard를 Pod에 할당한다.

shard 0 -> pod A
shard 1 -> pod A
shard 2 -> pod A
shard 3 -> pod B
shard 4 -> pod B
shard 5 -> pod B
shard 6 -> pod C
shard 7 -> pod C
shard 8 -> pod C
shard 9 -> pod C

2단계: 각 Pod가 자신에게 할당된 shard를 Pod 내부 thread에 나눈다.

pod A:
shard 0 -> thread-0
shard 1 -> thread-1
shard 2 -> thread-0

하나의 shard는 기본적으로 하나의 active Pod만 담당한다.

단, 해당 Pod 내부에서는 activeThreads 설정에 따라 shard 하나에 여러 consumer thread를 붙일 수 있다.

이 구조는 다음 장점이 있다.

* shard 단위 순서 보장이 비교적 단순하다.
* Pod 간 shard 소유권이 명확하다.
* 동일 shard를 여러 Pod가 중복 read하는 상황을 방지하기 쉽다.
* pending recovery 책임 범위가 명확하다.
* metric 집계가 쉽다.

⸻

7.3 Pod 간 Sticky Balanced Assignment 알고리즘

Pod들은 서로 중복 없이 stream shard를 나누어 읽어야 한다.

기본 정책:

* 모든 active Pod 목록을 안정적으로 정렬한다.
* shard index 목록을 오름차순으로 정렬한다.
* shard-count를 pod-count로 나누어 Pod별 target quota를 계산한다.
* 각 Pod는 floor(shard-count / pod-count) 또는 ceil(shard-count / pod-count)개의 shard만 담당한다.
* 이전 assignment plan에 남아 있는 shard 소유권은 가능한 유지한다.
* 균등성을 맞추기 위해 필요한 shard만 이동한다.
* coordinator가 이 알고리즘으로 assignment plan을 생성한다.
* follower Pod는 coordinator가 저장한 assignment plan을 적용한다.

예시:

shard-count = 10
pod-count = 3

base = 10 / 3 = 3
remainder = 10 % 3 = 1

할당 결과:

pod-0 -> shard 0, 1, 2
pod-1 -> shard 3, 4, 5
pod-2 -> shard 6, 7, 8, 9

즉 3/3/4로 나뉜다.

다른 예시:

shard-count = 12
pod-count = 3

pod-0 -> shard 0, 1, 2, 3
pod-1 -> shard 4, 5, 6, 7
pod-2 -> shard 8, 9, 10, 11

신규 assignment가 없는 첫 배치에서는 deterministic range assignment를 사용한다.

fun initialAssignShardsToPods(shardCount: Int, pods: List<PodId>): Map<PodId, List<Int>> {
val sortedPods = pods.sorted()
val base = shardCount / sortedPods.size
val remainder = shardCount % sortedPods.size
var nextShard = 0
val result = mutableMapOf<PodId, List<Int>>()

for ((index, pod) in sortedPods.withIndex()) {
val quota = base + if (index >= sortedPods.size - remainder) 1 else 0
val shards = (nextShard until nextShard + quota).toList()
result[pod] = shards
nextShard += quota
}

return result
}

Rebalance 시에는 Kafka StickyAssignor처럼 기존 assignment를 최대한 유지한다.

Sticky rebalance 목표:

1. 모든 shard는 정확히 하나의 active Pod에만 배정된다.
2. 각 Pod는 target quota를 만족한다.
3. 기존 active Pod가 가진 shard는 가능한 유지한다.
4. 이동해야 하는 shard 수를 최소화한다.
5. 동일 조건에서는 deterministic하게 같은 결과를 만든다.

Sticky rebalance 절차:

1. 이전 assignment plan을 읽는다.
2. active Pod 목록에서 사라진 Pod의 shard를 unassigned로 이동한다.
3. active Pod가 계속 살아 있고 quota를 초과하지 않는 한 기존 shard를 유지한다.
4. quota를 초과한 Pod에서 초과 shard만 unassigned로 이동한다.
5. quota보다 적게 가진 Pod에 unassigned shard를 채운다.
6. 그래도 imbalance가 남으면 가장 많이 가진 Pod에서 가장 적게 가진 Pod로 최소 개수만 이동한다.
7. 각 Pod의 shard list는 정렬해서 저장한다.

예시: scale-out

기존:

pod-0 -> shard 0, 1, 2, 3, 4
pod-1 -> shard 5, 6, 7, 8, 9

pod-2 추가 후 target quota는 3/3/4이다.

Sticky 결과 예시:

pod-0 -> shard 0, 1, 2
pod-1 -> shard 5, 6, 7
pod-2 -> shard 3, 4, 8, 9

이 결과는 range 재분배보다 기존 소유권을 더 많이 유지한다.

예시: scale-in

기존:

pod-0 -> shard 0, 1, 2
pod-1 -> shard 3, 4, 5
pod-2 -> shard 6, 7, 8, 9

pod-2 제거 후 target quota는 5/5이다.

Sticky 결과 예시:

pod-0 -> shard 0, 1, 2, 6, 7
pod-1 -> shard 3, 4, 5, 8, 9

pod-0과 pod-1이 기존에 갖고 있던 shard는 유지되고, 사라진 pod-2의 shard만 이동한다.

주의:

* 위 예시는 remainder를 뒤쪽 Pod에 배정해서 10 shard / 3 Pod일 때 3/3/4가 된다.
* remainder를 앞쪽 Pod에 배정해 4/3/3으로 만들 수도 있지만, 정책은 반드시 하나로 고정한다.
* shard owner 계산은 Pod 단위에서 먼저 끝나야 한다.
* Pod 내부 thread 수는 Pod 간 shard 소유권에 영향을 주지 않는다.
* sticky assignment는 이동량 최소화가 목적이지 hot shard 자동 분산이 목적은 아니다.
* 특정 shard hotspot이 심하면 shard key 또는 shard-count 변경 전략으로 해결해야 한다.

7.4 Pod 내부 Thread Assignment

각 Pod는 설정된 activeThreads를 자신에게 할당된 shard 수에 맞춰 분배한다.

activeThreads는 Pod 하나가 Redis Stream consume에 사용할 전체 consumer thread 수이다.

redis-stream:
consumer:
active-threads: 8

유효값:

* active-threads >= 1
* 0 이하 값은 설정 오류로 보고 애플리케이션 시작을 실패시킨다.

할당 규칙:

* Pod가 shard를 하나도 할당받지 못하면 실제 활성 thread 수는 0이다.
* Pod가 shard를 하나 이상 할당받으면 각 shard는 최소 1개 thread를 받아야 한다.
* 따라서 active-threads는 assignedShardCount 이상이어야 모든 shard에 최소 1개 thread를 배정할 수 있다.
* 초기 시작 시 active-threads가 assignedShardCount보다 작으면 설정 오류로 보고 consumer 시작을 실패시킨다.
* active-threads가 assignedShardCount보다 크면 남는 thread를 shard에 균등하게 추가 배정한다.
* Pod 증감으로 assignedShardCount가 바뀌면 assignment 재계산 후 active-threads 검증도 다시 수행한다.
* scale-in으로 Pod 수가 줄면 남은 Pod의 assignedShardCount가 증가할 수 있다. 이때 active-threads가 부족하면 해당 Pod는 새 shard lease를 획득하지 않고 DEGRADED 상태로 전환한다.
* scale-out으로 Pod 수가 늘면 Pod별 assignedShardCount가 감소할 수 있다. 남는 thread는 새 분배 결과에 따라 기존 shard에 추가 배정되거나 종료된다.

운영 권장:

active-threads >= ceil(shard-count / min-pod-count)

예를 들어 shard-count가 12이고 최소 Pod 수가 3이면 Pod 하나가 최대 4개 shard를 받을 수 있으므로 active-threads는 최소 4 이상으로 설정한다.

분배 공식:

baseThreadsPerShard = activeThreads / assignedShardCount
remainder = activeThreads % assignedShardCount

각 shard는 baseThreadsPerShard 또는 baseThreadsPerShard + 1개의 thread를 받는다.

예시:

pod-2 assigned shards = 6, 7, 8, 9
activeThreads = 4

shard 6 -> thread-0
shard 7 -> thread-1
shard 8 -> thread-2
shard 9 -> thread-3

예시:

pod-2 assigned shards = 6, 7, 8, 9
activeThreads = 8

shard 6 -> thread-0, thread-1
shard 7 -> thread-2, thread-3
shard 8 -> thread-4, thread-5
shard 9 -> thread-6, thread-7

예시:

pod-2 assigned shards = 6, 7, 8, 9
activeThreads = 6

baseThreadsPerShard = 6 / 4 = 1
remainder = 6 % 4 = 2

shard 6 -> thread-0
shard 7 -> thread-1
shard 8 -> thread-2, thread-3
shard 9 -> thread-4, thread-5

즉 전체 thread 수는 설정한 activeThreads로 고정되고, shard마다 최소 1개 이상 배정된다.

정책:

* thread assignment는 Pod 내부에서만 유효하다.
* thread가 늘거나 줄어도 다른 Pod의 shard 소유권은 바뀌지 않는다.
* thread 이름은 consumer name에 포함한다.
* Pod가 shard를 할당받지 못한 경우 실제 활성 thread 수는 0이 될 수 있다.
* Pod가 shard를 하나 이상 할당받은 경우 shard마다 최소 1개 thread가 활성화된다.
* activeThreads가 assignedShardCount보다 크면 같은 shard를 여러 consumer thread가 동시에 읽을 수 있다.
* 같은 shard에 여러 thread를 배정하면 Redis consumer group이 메시지를 분산하지만, shard 내부 strict ordering은 보장하지 않는다.
* shard 내부 순서 보장이 필요한 서비스는 activeThreads를 assignedShardCount와 같게 설정해야 한다.

7.5 Alternative Assignment 알고리즘

선택안 A: Consistent Hash 기반

각 consumer worker를 ring에 올리고 shard key를 hash하여 owner를 결정한다.

owner = consistentHashRing.getOwner("notification:3")

장점:

* Pod 증감 시 이동 shard 수가 적다.
* 대규모 shard 환경에서 재분배 비용이 낮다.

단점:

* 완전 균등 분배를 보장하려면 virtual node 튜닝이 필요하다.
* 구현 복잡도가 높다.

선택안 B: Rendezvous Hash 기반

각 shard에 대해 모든 consumer worker 중 가장 높은 score를 가진 worker를 owner로 선택한다.

owner = workers.maxBy { hash(shardKey + it.consumerId) }

장점:

* 구현이 단순하다.
* Pod 증감 시 변경 범위가 제한적이다.
* Consistent Hash보다 균등성이 좋은 편이다.
* Redis Stream shard assignment에 적합하다.

단점:

* worker 수와 shard 수가 매우 많으면 계산 비용이 증가한다.

권장안

본 모듈은 기본 assignment 알고리즘으로 Pod 간 Sticky Balanced Assignment를 사용한다.

Range Balanced와 Rendezvous Hash는 옵션으로 둘 수 있지만, 기본값으로 사용하지 않는다. 이유는 다음과 같다.

* Range Balanced는 Pod 증감 때 불필요한 shard 이동이 많다.
* Pod별 shard 개수를 정확히 floor/ceil로 제한하기 어렵다.
* 10 shard / 3 Pod에서 3/3/4 같은 명확한 운영 기대치를 보장하기 어렵다.
* Pod 내부 thread 수가 바뀔 때 Pod 간 소유권까지 흔들릴 수 있다.

7.6 Assignment Lease와 Fencing

Assignment 계산만으로는 assignment 변경 중 중복 read를 완전히 막을 수 없다.

예시:

1. Pod A가 shard 3을 읽고 있다.
2. Pod B가 새로 등록된다.
3. Pod B의 계산 결과 shard 3 owner가 B로 바뀐다.
4. Pod A가 아직 변경을 감지하지 못한 짧은 시간 동안 A와 B가 동시에 shard 3을 읽을 수 있다.

따라서 MVP에서도 shard owner lease를 둔다.

lease key:

redis-stream:lease:{module-name}:{stream-prefix}:{shard-index}

value:

{
"ownerPod": "order-api-7f9c9d9d7b-x82kd",
"ownerApplication": "order-api",
"epoch": 17,
"expiresAt": "2026-05-01T10:00:15Z"
}

정책:

* Pod는 자신이 Sticky Balanced Assignment plan 기준 shard owner일 때만 lease 획득을 시도한다.
* lease 획득은 SET key value NX PX ttl 또는 ownerPod token 비교 후 갱신으로 처리한다.
* Pod 내부 consumer thread들은 Pod가 lease를 보유한 shard만 XREADGROUP 한다.
* lease 갱신에 실패하면 해당 shard에 연결된 모든 read loop를 즉시 DRAINING으로 전환한다.
* handler 실행 중 lease를 잃어도 이미 읽은 메시지는 정상적으로 처리 후 ACK할 수 있다. 단, strict ordering 모드에서는 새 메시지 read를 중단하고 in-flight 처리를 drain한다.

lease-ttl은 heartbeat-ttl보다 길거나 같게 둔다.

redis-stream:
assignment:
lease-ttl: 20s
lease-renew-interval: 5s

이 lease는 exactly-once를 보장하기 위한 것이 아니라, 정상 운영 중 동일 shard를 여러 Pod가 적극적으로 읽는 시간을 줄이기 위한 fencing 장치이다.

⸻

8. Cluster Membership 관리

8.1 Pod / Worker Registry

각 Pod는 Redis에 Pod heartbeat key를 등록한다.

pod registry key:

redis-stream:registry:{module-name}:pods:{pod-id}

값 예시:

{
"application": "order-api",
"podName": "order-api-7f9c9d9d7b-x82kd",
"podUid": "8b7d...",
"startedAt": "2026-05-01T10:00:00Z",
"lastSeenAt": "2026-05-01T10:00:10Z",
"state": "STABLE"
}

각 consumer worker는 별도 worker heartbeat key를 등록한다.

worker registry key:

redis-stream:registry:{module-name}:workers:{consumer-id}

값 예시:

{
"application": "order-api",
"podName": "order-api-7f9c9d9d7b-x82kd",
"podUid": "8b7d...",
"consumerId": "order-api:pod-a:0",
"startedAt": "2026-05-01T10:00:00Z",
"lastSeenAt": "2026-05-01T10:00:10Z"
}

TTL:

heartbeat-ttl = heartbeat-interval * 3

예시:

redis-stream:
heartbeat-interval: 5s
heartbeat-ttl: 15s

Pod 간 shard assignment는 pod registry만 사용한다.

Worker registry는 consumer 상태, pending cleanup, metric label을 위해 사용한다.

8.2 Group Coordinator

Kafka처럼 일관된 rebalance를 위해 group coordinator 역할을 둔다.

본 모듈의 coordinator는 별도 서버가 아니라, 살아있는 Pod 중 하나가 Redis lease를 획득해 수행하는 lightweight coordinator이다.

coordinator lease key:

redis-stream:coordinator:{module-name}:{consumer-group}

value:

{
"coordinatorPod": "order-api-7f9c9d9d7b-x82kd",
"podUid": "8b7d...",
"epoch": 12,
"lastSeenAt": "2026-05-01T10:00:10Z"
}

정책:

* 모든 Pod는 coordinator lease 획득을 시도할 수 있다.
* SET key value NX PX coordinator-ttl로 coordinator가 선출된다.
* coordinator는 coordinator-renew-interval마다 lease를 갱신한다.
* coordinator lease가 만료되면 다른 Pod가 coordinator가 될 수 있다.
* coordinator epoch은 coordinator가 새로 선출될 때 증가한다.

설정:

redis-stream:
coordinator:
ttl: 15s
renew-interval: 5s

coordinator 책임:

* pod registry snapshot을 읽어 active Pod 목록 구성
* membershipFingerprint 계산
* rebalance generation 증가
* Sticky Balanced Assignment 계산
* assignment plan 저장
* membership event publish
* DEGRADED Pod 감지 및 metric 노출

assignment plan key:

redis-stream:assignment:{module-name}:{consumer-group}

assignment plan 예시:

{
"generation": 17,
"coordinatorEpoch": 12,
"membershipFingerprint": "sha256...",
"assignor": "STICKY_BALANCED",
"previousGeneration": 16,
"state": "STABLE",
"assignments": {
"pod-a": [0, 1, 2],
"pod-b": [3, 4, 5],
"pod-c": [6, 7, 8, 9]
},
"createdAt": "2026-05-01T10:00:10Z"
}

각 Pod는 assignment를 직접 확정하지 않는다.

각 Pod는 coordinator가 저장한 assignment plan을 읽고, 자기 Pod에 배정된 shard만 lease 획득 및 consume 대상으로 삼는다.

coordinator 장애:

1. coordinator lease 갱신이 중단된다.
2. coordinator-ttl 이후 다른 Pod가 coordinator lease를 획득한다.
3. 새 coordinator가 pod registry snapshot을 읽는다.
4. 새 coordinator가 coordinatorEpoch을 증가시킨다.
5. 새 assignment generation을 생성한다.
6. 모든 Pod는 새 assignment plan을 읽고 rebalance한다.

Group Coordinator를 두더라도 shard lease는 유지한다.

이유:

* coordinator 장애나 network pause 중 stale assignment를 가진 Pod가 읽는 것을 줄인다.
* assignment plan은 의도한 owner를 정하고, shard lease는 실제 read 권한을 fencing한다.

8.2.1 Coordinator 장애와 Failover

master coordinator가 갑자기 죽는 경우를 반드시 고려한다.

장애 예시:

1. coordinator Pod가 SIGKILL, node down, OOM 등으로 즉시 종료된다.
2. coordinator lease renew가 중단된다.
3. follower Pod들은 ASSIGNMENT_UPDATED event를 더 이상 받지 못한다.
4. 기존 shard owner Pod들은 이미 획득한 shard lease가 살아 있는 동안 기존 assignment로 계속 consume한다.
5. coordinator-ttl이 지나면 coordinator lease가 만료된다.
6. 살아 있는 Pod 중 하나가 coordinator lease를 획득한다.
7. 새 coordinator가 coordinatorEpoch을 증가시킨다.
8. 새 coordinator가 pod registry snapshot과 기존 assignment plan을 읽는다.
9. 새 coordinator가 Sticky Balanced Assignment를 재계산한다.
10. 새 assignment generation을 저장하고 ASSIGNMENT_UPDATED event를 publish한다.
11. follower Pod들은 새 generation을 읽고 rebalance를 수행한다.

중요한 원칙:

* coordinator가 죽어도 기존 shard owner가 즉시 consume을 멈출 필요는 없다.
* shard lease가 살아 있는 동안은 기존 owner가 read를 계속할 수 있다.
* coordinator failover 동안 새 assignment 생성만 일시 중단된다.
* 실제 read 권한은 shard lease가 fencing하므로 stale coordinator가 만든 오래된 assignment만으로는 read할 수 없다.

coordinator epoch fencing:

assignment plan에는 coordinatorEpoch과 generation을 반드시 포함한다.

Pod는 assignment plan을 적용하기 전에 다음을 확인한다.

* assignmentPlan.coordinatorEpoch >= localObservedCoordinatorEpoch
* assignmentPlan.generation >= localAppliedGeneration
* assignmentPlan.membershipFingerprint가 현재 registry snapshot과 호환됨

더 낮은 epoch 또는 generation의 assignment plan은 stale plan으로 보고 무시한다.

Split brain 방지:

* coordinator lease 갱신은 owner token을 비교한 뒤 수행한다.
* lease owner가 아닌 Pod는 assignment plan을 overwrite하지 않는다.
* 가능하면 coordinator lease 갱신과 assignment plan 저장은 Lua script로 owner token을 검증한 뒤 수행한다.

예시 Lua 조건:

if GET coordinatorKey.owner == currentPodUid then
  SET assignmentPlanKey newPlan
  PEXPIRE coordinatorKey ttl
else
  return STALE_COORDINATOR
end

Failover 시간:

coordinator-failover-delay <= coordinator-ttl + coordinator-election-latency

기본값이 coordinator.ttl = 15s이면 coordinator가 갑자기 죽어도 약 15초 + 선출 시간 후 새 coordinator가 assignment를 다시 생성한다.

Follower 동작:

* assignment plan이 오래되어도 shard lease가 유효하면 기존 shard consume을 계속한다.
* assignment plan age가 max-assignment-staleness를 초과하면 새 shard lease 획득은 중단한다.
* 기존 in-flight 처리는 ACK까지 마무리한다.
* coordinator failover가 끝나 새 generation을 받으면 정상 rebalance한다.

설정:

redis-stream:
coordinator:
ttl: 15s
renew-interval: 5s
max-assignment-staleness: 30s

8.3 Pod State Machine

Kafka consumer group 상태를 참고해 Pod 상태를 다음처럼 정의한다.

Kafka group state 대응:

* Empty: active Pod가 없는 상태
* PreparingRebalance: membership 변경을 감지하고 기존 assignment를 정리하는 상태
* CompletingRebalance: 새 assignment를 계산하고 lease 획득을 준비하는 상태
* Stable: assignment와 lease가 안정화된 상태
* Dead: group 또는 member가 종료된 상태

Pod 상태:

STARTING

* 프로세스 시작 직후
* pod registry 등록 전
* assignment 대상 아님

JOINING

* pod registry key를 등록한 상태
* 아직 첫 membership scan과 assignment 계산이 끝나지 않음
* Kafka의 member join 단계에 대응
* assignment 대상에 포함 가능하지만, 자기 자신은 아직 consume하지 않음

PREPARING_REBALANCE

* membershipFingerprint 변경을 감지한 상태
* 더 이상 소유하지 않을 shard의 lease renew를 중단
* 해당 shard worker를 DRAINING으로 전환
* Kafka의 PreparingRebalance에 대응

COMPLETING_REBALANCE

* 새 Sticky Balanced Assignment 계산 완료
* active-threads 검증 수행
* 새로 맡을 shard lease 획득 시도
* Kafka의 CompletingRebalance에 대응

STABLE

* assignment 적용 완료
* 필요한 shard lease 보유
* consumer thread가 정상 read 중
* Kafka의 Stable에 대응

DRAINING

* graceful shutdown 또는 shard 반납 중
* 신규 shard lease를 획득하지 않음
* 기존 in-flight 처리와 ACK만 마무리

DEGRADED

* membership에는 살아 있지만 정상 assignment를 수행할 수 없음
* 예: assignedShardCount > active-threads
* 신규 shard lease를 획득하지 않음
* metric과 log로 설정 부족을 노출

STOPPED

* pod registry key 삭제 또는 TTL 만료
* assignment 대상 아님
* Kafka의 Dead에 대응

상태 전이:

STARTING
-> JOINING
-> COMPLETING_REBALANCE
-> STABLE

STABLE
-> PREPARING_REBALANCE
-> COMPLETING_REBALANCE
-> STABLE

STABLE
-> DRAINING
-> STOPPED

COMPLETING_REBALANCE
-> DEGRADED

DEGRADED
-> PREPARING_REBALANCE
-> COMPLETING_REBALANCE
-> STABLE

상태별 assignment 정책:

* JOINING, STABLE, PREPARING_REBALANCE, COMPLETING_REBALANCE는 active Pod 목록에 포함한다.
* DRAINING은 기존 lease 정리 대상으로만 보고 신규 shard owner 후보에서는 제외한다.
* DEGRADED는 heartbeat는 유지하지만 신규 shard owner 후보에서는 제외한다.
* STARTING, STOPPED는 active Pod 목록에서 제외한다.

8.3 Active Pod 조회와 변경 감지

Pod들은 다른 Pod의 상태 변화를 실시간에 가깝게 알아야 한다.

이를 위해 membership 관리는 두 경로를 함께 사용한다.

1. Source of truth: Redis pod registry snapshot
2. Fast notification: Redis Pub/Sub membership event

Pub/Sub event는 빠른 감지를 위한 힌트이다. 메시지가 유실될 수 있으므로 assignment의 최종 근거로 사용하지 않는다.

최종 assignment는 항상 pod registry snapshot을 다시 읽어서 계산한다.

8.3.1 Registry Snapshot

각 Pod는 rebalance-interval마다 pod registry key를 scan하여 active Pod 목록을 구성한다.

SCAN redis-stream:registry:notification:pods:*

각 Pod는 이 결과를 local membership cache로 유지한다.

cache 항목:

* podName
* podUid
* state
* lastSeenAt
* membershipVersion
* assignedShards
* lastObservedAt

이 cache는 다음 용도로 사용한다.

* 현재 Pod가 알고 있는 다른 Pod 상태 조회
* coordinator가 만든 assignment plan 검증
* rebalance 필요 여부 판단
* metric/debug endpoint 노출

단, cache는 권위 있는 데이터가 아니다. 재분배 직전에는 항상 Redis pod registry snapshot을 다시 읽어 최신 상태를 확인한다.

active Pod 조건:

* registry key가 존재한다.
* state가 JOINING, STABLE, PREPARING_REBALANCE, COMPLETING_REBALANCE 중 하나이다.
* lastSeenAt이 heartbeat-ttl 범위 안에 있다.

각 Pod는 active Pod 목록을 안정적으로 정렬한 뒤 membership fingerprint를 계산한다.

fingerprint 입력:

podName + ":" + podUid

fingerprint 예시:

membershipFingerprint = sha256(sortedActivePods.joinToString("|"))

각 Pod는 마지막으로 적용한 fingerprint와 새 fingerprint를 비교한다.

* follower Pod에서 fingerprint가 같으면 기존 assignment plan을 유지한다.
* follower Pod에서 fingerprint가 달라지면 coordinator에게 rebalance 필요 상태를 알리고 PREPARING_REBALANCE로 전환한다.
* coordinator Pod에서 fingerprint가 달라지면 새 assignment generation을 생성한다.

8.3.2 Membership Event

각 Pod는 자신의 상태가 바뀔 때 membership event를 publish한다.

channel:

redis-stream:membership:{module-name}

event 예시:

{
"eventId": "01J...",
"eventType": "POD_JOINED",
"application": "order-api",
"podName": "order-api-7f9c9d9d7b-x82kd",
"podUid": "8b7d...",
"state": "JOINING",
"version": 3,
"createdAt": "2026-05-01T10:00:10Z"
}

eventType:

* POD_JOINED
* POD_STATE_CHANGED
* POD_LEAVING
* POD_STOPPED
* HEARTBEAT_RENEWED
* REBALANCE_REQUESTED
* ASSIGNMENT_UPDATED

각 Pod는 해당 channel을 subscribe한다.

event를 수신하면 즉시 registry snapshot refresh를 수행한다.

중요:

* event payload만 보고 assignment를 바꾸지 않는다.
* event 수신 후 pod registry를 다시 읽고 membershipFingerprint를 계산한다.
* Pub/Sub 유실에 대비해 rebalance-interval 기반 periodic scan은 계속 수행한다.
* HEARTBEAT_RENEWED는 너무 자주 publish하면 불필요한 부하가 크므로 기본 비활성화한다.

8.3.3 변경 감지 경로

Pod 추가 감지는 다음 두 경로 중 하나로 발생한다.

Fast path:

1. 신규 Pod가 pod registry key를 SET PX heartbeat-ttl로 등록한다.
2. 신규 Pod가 POD_JOINED event를 publish한다.
3. 기존 Pod들이 event를 수신한다.
4. 기존 Pod들이 즉시 pod registry snapshot을 refresh한다.
5. membershipFingerprint 변경을 감지한다.
6. PREPARING_REBALANCE로 전환한다.

Fallback path:

1. POD_JOINED event가 유실된다.
2. 기존 Pod들이 rebalance-interval마다 pod registry를 scan한다.
3. scan 결과에서 신규 Pod를 발견한다.
4. membershipFingerprint 변경을 감지한다.
5. PREPARING_REBALANCE로 전환한다.

감지 지연:

Fast path:

detection-delay ~= pubsub-delivery-latency + registry-refresh-latency

Fallback path:

detection-delay <= rebalance-interval + cluster-scan-duration

Pod 추가 감지 흐름:

1. 신규 Pod가 시작된다.
2. 신규 Pod가 pod registry key를 SET PX heartbeat-ttl로 등록한다.
3. 신규 Pod가 POD_JOINED event를 publish한다.
4. 기존 Pod들은 event 수신 또는 periodic scan으로 신규 Pod를 감지한다.
5. active Pod 목록이 달라지므로 membershipFingerprint가 변경된다.
6. coordinator는 동일한 active Pod 목록과 이전 assignment plan으로 Sticky Balanced Assignment를 계산한다.
7. coordinator는 새 assignment plan을 저장하고 ASSIGNMENT_UPDATED event를 publish한다.
8. follower Pod는 assignment plan을 읽는다.
9. 기존 Pod는 PREPARING_REBALANCE에서 자신이 더 이상 owner가 아닌 shard의 lease renew를 중단한다.
10. 기존 Pod는 해당 shard worker를 DRAINING으로 전환한다.
11. 각 Pod는 COMPLETING_REBALANCE에서 새 assignment를 적용한다.
12. 자신이 새 owner가 된 shard는 active-threads 검증 후 lease 획득을 시도한다.
13. 필요한 lease 획득이 끝나면 STABLE로 전환한다.

기본값:

redis-stream:
assignment:
rebalance-interval: 5s

즉 기본 설정에서는 Pub/Sub event가 정상 전달되면 거의 즉시 재분배가 시작되고, event가 유실되어도 대략 5초 + Redis scan 시간 안에 재분배가 시작된다.

그 후 coordinator가 동일한 Pod 목록과 shard 목록을 기반으로 Pod 간 assignment plan을 계산한다.

모든 follower Pod는 coordinator가 저장한 assignment plan을 읽고 적용한다.

Redis Cluster 사용 시 주의:

* 일반 SCAN은 접속한 Redis node의 key만 순회할 수 있다.
* Redis Cluster client가 모든 master node를 순회하는 scan을 지원하는지 확인해야 한다.
* 지원하지 않는 client를 사용할 경우 registry를 별도 Redis logical database 또는 단일 slot key 구조로 분리하는 방안을 검토한다.

MVP에서는 다음 중 하나를 명시적으로 선택한다.

선택안 A:

* Redis Cluster master node 전체를 순회하는 Cluster SCAN 구현을 제공한다.
* registry key는 stream shard key와 독립적으로 둔다.

선택안 B:

* membership registry는 별도 Redis standalone 또는 coordinator store를 사용한다.
* stream data plane과 membership control plane을 분리한다.

권장안:

초기 MVP는 선택안 A를 기본으로 한다. 단, 사용하는 Redis client가 cluster-wide scan을 안정적으로 제공하지 않으면 선택안 B로 전환한다.

8.4 Registry 데이터 정렬

모든 Pod가 같은 assignment 결과를 얻으려면 active Pod 목록과 worker 목록의 정렬 기준이 고정되어야 한다.

Pod 정렬 기준:

1. application
2. podName
3. podUid

Worker 정렬 기준:

1. application
2. podName
3. threadIndex
4. consumerId

동일 worker가 중복 조회되면 consumerId 기준으로 deduplicate한다.

8.5 Graceful Shutdown

Pod 종료 시 worker는 다음 순서로 종료한다.

1. readiness를 false로 내려 신규 트래픽과 신규 배포 진입을 막는다.
2. heartbeat 갱신을 중지하지 않고 DRAINING 상태로 전환한다.
3. lease renew를 중단하거나 lease를 명시적으로 release한다.
4. XREADGROUP 신규 read를 중단한다.
5. in-flight handler 완료를 기다린다.
6. 성공한 메시지를 ACK한다.
7. registry key를 삭제한다.
8. 종료한다.

terminationGracePeriodSeconds는 handler timeout과 batch 처리 시간을 고려해서 설정해야 한다.

8.6 Pod 수 증감과 Reassignment

Pod 수가 바뀌면 coordinator가 active Pod 목록을 기준으로 shard assignment plan을 다시 계산한다.

Rebalance trigger:

* membershipFingerprint 변경
* shard-count 또는 stream-prefix 설정 변경 감지
* manual rebalance 요청
* lease owner와 assignment plan owner 불일치 감지

Rebalance는 coordinator가 generation 단위로 수행한다. follower Pod는 coordinator가 저장한 assignment plan을 적용한다.

Scale-out 예시:

shard-count = 10

기존:

pod-0 -> shard 0, 1, 2, 3, 4
pod-1 -> shard 5, 6, 7, 8, 9

Pod 1개 추가 후:

pod-0 -> shard 0, 1, 2
pod-1 -> shard 3, 4, 5
pod-2 -> shard 6, 7, 8, 9

처리 순서:

1. 신규 Pod가 STARTING에서 JOINING으로 전환하며 pod registry에 heartbeat key를 등록한다.
2. 신규 Pod가 POD_JOINED event를 publish한다.
3. 기존 Pod들이 event를 수신하거나 rebalance-interval scan으로 신규 Pod를 발견한다.
4. 기존 Pod들이 pod registry snapshot을 refresh한다.
5. membershipFingerprint 변경을 감지한다.
6. coordinator가 아니면 REBALANCE_REQUESTED event를 publish한다.
7. coordinator는 PREPARING_REBALANCE generation을 시작한다.
8. 기존 STABLE Pod들은 PREPARING_REBALANCE로 전환한다.
9. coordinator는 active Pod 목록과 이전 assignment plan으로 Sticky Balanced Assignment를 계산한다.
10. coordinator는 assignment plan을 저장하고 ASSIGNMENT_UPDATED event를 publish한다.
11. 각 Pod는 assignment plan을 읽고 COMPLETING_REBALANCE로 전환한다.
12. 더 이상 소유하지 않는 shard는 lease renew를 중단하고 해당 shard worker를 DRAINING으로 전환한다.
13. 새로 소유하게 된 shard는 active-threads 검증을 통과한 뒤 lease 획득을 시도한다.
14. lease 획득 후 해당 shard의 consumer thread를 생성한다.
15. 필요하면 XAUTOCLAIM으로 이전 owner의 pending message를 회수한다.
16. 필요한 lease와 thread 구성이 끝나면 STABLE로 전환한다.

Scale-in 예시:

shard-count = 10

기존:

pod-0 -> shard 0, 1, 2
pod-1 -> shard 3, 4, 5
pod-2 -> shard 6, 7, 8, 9

pod-2 종료 후:

pod-0 -> shard 0, 1, 2, 3, 4
pod-1 -> shard 5, 6, 7, 8, 9

처리 순서:

1. 종료 Pod가 graceful shutdown이면 POD_LEAVING event를 publish한다.
2. 종료 Pod의 heartbeat TTL이 만료되거나 graceful shutdown으로 pod registry key가 삭제된다.
3. 남은 Pod들이 event를 수신하거나 rebalance-interval scan으로 상태 변화를 발견한다.
4. 남은 Pod들이 pod registry snapshot을 refresh한다.
5. membershipFingerprint 변경을 감지한다.
6. coordinator가 아니면 REBALANCE_REQUESTED event를 publish한다.
7. coordinator는 PREPARING_REBALANCE generation을 시작한다.
8. 남은 STABLE Pod들은 PREPARING_REBALANCE로 전환한다.
9. coordinator는 이전 assignment plan을 참고해 Sticky Balanced Assignment를 재계산한다.
10. coordinator는 assignment plan을 저장하고 ASSIGNMENT_UPDATED event를 publish한다.
11. 각 Pod는 assignment plan을 읽고 COMPLETING_REBALANCE로 전환한다.
12. 새 assignedShardCount가 active-threads보다 큰지 검증한다.
13. active-threads가 충분하면 새 shard lease를 획득하고 consumer thread를 재분배한다.
14. active-threads가 부족하면 새 shard lease를 획득하지 않고 DEGRADED 상태로 전환한다.
15. DEGRADED 상태에서는 metric과 log로 설정 부족을 노출하고, 해당 Pod는 기존 lease를 가진 shard만 계속 처리한다.
16. 정상 적용이 끝난 Pod는 STABLE로 전환한다.

중요:

* scale-in 시에는 Pod당 assignedShardCount가 증가한다.
* active-threads는 최소 Pod 수 기준의 최대 assignedShardCount를 감당할 수 있어야 한다.
* active-threads 부족 상태에서 무리하게 shard를 읽기 시작하면 shard별 최소 1 thread 보장 정책이 깨진다.
* DEGRADED 상태가 길어지면 일부 shard가 lease owner 없이 대기할 수 있으므로 alert가 필요하다.

⸻

9. Consumer Worker 동작

9.1 Worker Lifecycle

STARTING
-> REGISTERED
-> ASSIGNED
-> CONSUMING
-> DRAINING
-> STOPPED

9.2 기본 Read Loop

각 worker는 자신에게 할당되고, worker가 속한 Pod가 lease를 보유한 shard만 읽는다.

while (running) {
val assignedShards = shardAssigner.assignedShards(consumerId)
for (shard in assignedShards) {
if (!leaseManager.hasLease(shard)) {
continue
}
val records = redis.xreadgroup(
group = consumerGroup,
consumer = consumerName,
stream = shard.streamKey,
count = batchSize,
block = blockTimeout,
id = ">"
)
records.forEach { record ->
process(record)
ack(record)
}
}
}

9.3 Read 전략

단순 Round Robin

할당받은 shard를 순회하며 읽는다.

장점:

* 구현이 단순하다.
* shard 간 starvation을 줄인다.

단점:

* 특정 shard에 메시지가 많을 때 처리량이 제한될 수 있다.

Weighted Round Robin

lag가 큰 shard를 더 자주 읽는다.

장점:

* backlog가 큰 shard를 빠르게 따라잡을 수 있다.

단점:

* metric 조회 비용이 증가한다.

권장안

초기 버전은 Round Robin을 기본으로 제공하고, 추후 lag-aware read strategy를 확장 포인트로 둔다.

⸻

10. Thread 수와 Shard 수 관계

redis-stream:
shard-count: 12
consumer:
active-threads: 4

Pod 1개 기준:

active-threads는 Pod가 할당받은 shard 수 이상이어야 한다.

단, shard 소유권은 전체 worker 기준이 아니라 Pod 기준으로 먼저 결정한다.

12 shards, 3 Pods이면 Pod당 shard 4개가 먼저 할당된다.

pod-0 -> shard 0, 1, 2, 3
pod-1 -> shard 4, 5, 6, 7
pod-2 -> shard 8, 9, 10, 11

그 다음 각 Pod 내부에서 active-threads를 할당받은 shard에 분배한다.

예시:

12 shards, 3 Pods, active-threads = 4

Pod별 assignedShardCount는 4이다.

shard 0 -> thread-0
shard 1 -> thread-1
shard 2 -> thread-2
shard 3 -> thread-3

예시:

12 shards, 3 Pods, active-threads = 8

Pod별 assignedShardCount는 4이다.

shard 0 -> thread-0, thread-1
shard 1 -> thread-2, thread-3
shard 2 -> thread-4, thread-5
shard 3 -> thread-6, thread-7

예시:

12 shards, 10 Pods, active-threads = 4

Pod별 assignedShardCount는 1 또는 2가 된다.

* shard 1개를 받은 Pod: shard 하나에 thread 4개 배정
* shard 2개를 받은 Pod: shard마다 thread 2개씩 배정

Pod가 shard를 하나라도 받으면 shard마다 최소 1개 thread는 활성화된다. 이를 위해 active-threads는 예상되는 Pod당 최대 assignedShardCount 이상으로 잡는 것을 권장한다.

⸻

11. 메시지 처리 흐름

1. Producer가 shardKey 기반으로 stream shard 결정
2. XADD로 메시지 발행
3. Consumer worker가 assignment 기준으로 담당 shard 읽기
4. 사용자 handler 호출
5. 성공 시 XACK 또는 XACKDEL
6. 실패 시 retry 정책 적용
7. retry 초과 시 DLQ 발행 후 ACK

⸻

12. ACK / 삭제 정책

12.1 기본 정책

Redis Stream은 XACK만 수행하면 PEL에서는 제거되지만 Stream entry 자체는 남는다.

따라서 모듈은 다음 정책을 제공한다.

redis-stream:
ack:
mode: ACK_ONLY # ACK_ONLY, ACK_AND_DELETE, XACKDEL

12.2 ACK_ONLY

XACK stream group id

장점:

* 가장 안전하다.
* 다른 consumer group과 충돌하지 않는다.

단점:

* Stream 길이가 계속 증가할 수 있다.
* 별도 trim 정책이 필요하다.

12.3 ACK_AND_DELETE

XACK stream group id
XDEL stream id

장점:

* 처리 완료 후 즉시 메모리 회수 가능하다.

단점:

* 2 RTT 또는 pipeline 필요.
* atomic하지 않다.
* multi consumer group 환경에서는 다른 group이 읽기 전에 entry를 삭제할 수 있다.

12.4 XACKDEL

Redis 8.2 이상에서는 XACKDEL 사용을 지원한다.

XACKDEL stream group ACKED IDS 1 id

ACKED 옵션은 현재 consumer group에서는 ACK하지만, 모든 consumer group이 해당 entry를 읽고 ACK한 경우에만 entry를 삭제한다.

따라서 multi consumer group 환경에서는 XACK + XDEL보다 안전하다.

⸻

13. Pending Message Recovery

13.1 문제

Consumer가 메시지를 읽은 뒤 처리 중 죽으면 메시지는 PEL에 남는다.

XREADGROUP 성공
handler 처리 전 Pod kill
XACK 미수행
=> PEL에 pending message 잔류

13.2 Recovery 방식

Redis 6.2 이상에서는 XAUTOCLAIM을 사용한다.

XAUTOCLAIM stream group consumer min-idle-time 0-0 COUNT 100

모듈은 주기적으로 담당 shard의 pending message를 reclaim한다.

redis-stream:
pending:
reclaim-enabled: true
min-idle-time: 60s
scan-interval: 30s
batch-size: 100

13.3 Reclaim 책임

Reclaim도 shard owner Pod의 worker가 담당한다.

즉, 현재 assignment plan 기준으로 해당 shard를 담당하는 Pod의 worker만 그 shard의 pending message를 회수한다.

이렇게 해야 여러 worker가 동시에 같은 pending message를 claim하는 것을 줄일 수 있다.

13.4 XAUTOCLAIM Cursor 처리

XAUTOCLAIM은 한 번의 호출로 모든 pending message를 가져온다고 가정하지 않는다.

worker는 shard별 reclaim cursor를 유지한다.

흐름:

1. cursor를 "0-0"으로 시작한다.
2. XAUTOCLAIM 결과의 next cursor를 저장한다.
3. 반환된 message를 retry/DLQ 정책에 따라 처리한다.
4. next cursor가 "0-0"으로 돌아오면 한 scan cycle을 종료한다.
5. 다음 scan-interval에 다시 "0-0"부터 시작한다.

한 cycle에서 처리할 수 있는 최대 message 수를 제한한다.

redis-stream:
pending:
max-messages-per-scan: 1000

이 제한이 없으면 대량 pending 상황에서 reclaim 작업이 일반 consume을 장시간 방해할 수 있다.

13.5 Dead Consumer Cleanup

Redis Stream consumer metadata는 자동으로 정리되지 않는다.

모듈은 consumer cleanup 작업을 제공한다.

순서:

1. XINFO CONSUMERS {streamKey} {groupName} 조회
2. idle 시간이 consumer-ttl을 초과한 consumer를 후보로 선택
3. 후보 consumer의 pending count 확인
4. pending이 있으면 shard owner가 XAUTOCLAIM으로 먼저 회수
5. pending이 0이면 XGROUP DELCONSUMER 실행

설정:

redis-stream:
consumer-cleanup:
enabled: true
consumer-ttl: 10m
scan-interval: 1m

주의:

* idle만 보고 바로 DELCONSUMER를 호출하지 않는다.
* pending message가 남아 있는 consumer를 삭제하기 전에 반드시 reclaim을 먼저 수행한다.
* 처리 시간이 긴 handler가 있는 서비스는 consumer-ttl을 handler timeout보다 충분히 크게 잡아야 한다.

⸻

14. Retry / DLQ 정책

14.1 Retry Count 관리

Redis Stream의 delivery count는 XPENDING 또는 XAUTOCLAIM 결과로 확인할 수 있다.

모듈은 delivery count가 max retry를 초과하면 DLQ로 이동한다.

redis-stream:
retry:
max-attempts: 5
dlq:
enabled: true
suffix: dlq

DLQ stream key:

{stream-prefix}:{shard-index}:dlq

예시:

notification:3:dlq

14.2 실패 처리 흐름

handler 실패
-> retry 가능하면 ACK 하지 않음
-> PEL에 유지
-> min-idle-time 이후 XAUTOCLAIM으로 재처리
-> max-attempts 초과 시 DLQ XADD
-> 원본 메시지 XACK 또는 XACKDEL

⸻

15. Backpressure 설계

15.1 Batch Size

redis-stream:
consumer:
batch-size: 50
block-timeout: 2s

batch size를 너무 크게 잡으면 한 shard가 worker를 오래 점유할 수 있다.

15.2 Concurrent Handler 제한

각 worker 내부에서 병렬 처리를 허용할 경우 semaphore로 동시 처리량을 제한한다.

redis-stream:
consumer:
max-in-flight-per-worker: 100

15.3 처리 순서 보장

동일 shard 내 strict ordering이 필요하면 worker는 shard별 단일 처리 루프를 유지해야 한다.

병렬 handler를 사용하면 동일 shard 내 처리 완료 순서가 뒤집힐 수 있으므로 설정으로 명확히 분리한다.

redis-stream:
ordering:
strict-per-shard: true

strict-per-shard가 true이면 각 shard에 배정되는 thread 수는 반드시 1이어야 한다.

즉 strict-per-shard가 true인 서비스는 active-threads를 assignedShardCount와 같게 설정해야 한다.

특정 shard에 2개 이상의 thread가 배정되면 같은 shard의 여러 메시지가 병렬 처리될 수 있고, 처리 완료 및 ACK 순서가 뒤집힐 수 있다.

⸻

16. Configuration 설계

redis-stream:
enabled: true
stream-prefix: notification
shard-count: 6
consumer-group: notification-consumer-group
group:
start-id: "$"
consumer:
active-threads: 4
batch-size: 50
block-timeout: 2s
poll-interval-on-empty: 100ms
max-in-flight-per-worker: 100
assignment:
strategy: STICKY_BALANCED
heartbeat-interval: 5s
heartbeat-ttl: 15s
rebalance-interval: 5s
lease-ttl: 20s
lease-renew-interval: 5s
coordinator:
ttl: 15s
renew-interval: 5s
max-assignment-staleness: 30s
ack:
mode: ACK_ONLY
pending:
reclaim-enabled: true
min-idle-time: 60s
scan-interval: 30s
batch-size: 100
max-messages-per-scan: 1000
consumer-cleanup:
enabled: true
consumer-ttl: 10m
scan-interval: 1m
retry:
max-attempts: 5
dlq:
enabled: true
suffix: dlq
trim:
enabled: true
strategy: MAXLEN
max-len: 1000000
approximate: true

⸻

17. Public API 설계

17.1 Handler Interface

interface RedisStreamMessageHandler<T> {
suspend fun handle(message: RedisStreamMessage<T>)
}

또는 blocking handler도 지원한다.

fun interface BlockingRedisStreamMessageHandler<T> {
fun handle(message: RedisStreamMessage<T>)
}

17.2 Message Model

data class RedisStreamMessage<T>(
val streamKey: String,
val shardIndex: Int,
val messageId: String,
val payload: T,
val raw: Map<String, String>,
val deliveryCount: Long? = null
)

17.3 Producer API

interface ShardedRedisStreamProducer<T> {
fun publish(shardKey: String, payload: T): String
}

Coroutine 버전:

interface CoroutineShardedRedisStreamProducer<T> {
suspend fun publish(shardKey: String, payload: T): String
}

⸻

18. Auto Configuration

@ConfigurationProperties(prefix = "redis-stream")
data class RedisStreamShardProperties(
val enabled: Boolean = true,
val streamPrefix: String,
val shardCount: Int,
val consumerGroup: String,
val group: GroupProperties,
val consumer: ConsumerProperties,
val assignment: AssignmentProperties,
val ack: AckProperties,
val pending: PendingProperties,
val consumerCleanup: ConsumerCleanupProperties,
val retry: RetryProperties,
val dlq: DlqProperties,
val trim: TrimProperties
)
@AutoConfiguration
@EnableConfigurationProperties(RedisStreamShardProperties::class)
class RedisStreamShardAutoConfiguration {
@Bean
fun streamShardResolver(properties: RedisStreamShardProperties): StreamShardResolver {
return DefaultStreamShardResolver(properties.streamPrefix, properties.shardCount)
}
@Bean
fun streamShardAssigner(...): StreamShardAssigner {
return StickyBalancedStreamShardAssigner(...)
}
@Bean
fun groupCoordinator(...): GroupCoordinator {
return RedisLeaseGroupCoordinator(...)
}
@Bean
fun groupAssignmentStore(...): GroupAssignmentStore {
return RedisGroupAssignmentStore(...)
}
@Bean
fun shardLeaseManager(...): ShardLeaseManager {
return RedisShardLeaseManager(...)
}
@Bean
fun deadConsumerCleaner(...): DeadConsumerCleaner {
return DeadConsumerCleaner(...)
}
@Bean
fun streamConsumerSupervisor(...): StreamConsumerSupervisor {
return StreamConsumerSupervisor(...)
}
}

⸻

19. Gradle 모듈 구조

redis-stream-shard-spring-boot-starter
├── build.gradle.kts
├── src/main/kotlin
│   └── com.company.redisstream
│       ├── autoconfigure
│       ├── config
│       ├── producer
│       ├── consumer
│       ├── assignment
│       ├── coordinator
│       ├── lease
│       ├── pending
│       ├── cleanup
│       ├── dlq
│       └── metrics
└── src/main/resources
└── META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports

AutoConfiguration.imports:

com.company.redisstream.autoconfigure.RedisStreamShardAutoConfiguration

⸻

20. Redis 명령 사용 목록

Producer

XADD {streamKey} * field value ...

Optional:

XADD {streamKey} MAXLEN ~ 1000000 * field value ...

Consumer

XGROUP CREATE {streamKey} {groupName} $ MKSTREAM
XREADGROUP GROUP {groupName} {consumerName} COUNT {count} BLOCK {ms} STREAMS {streamKey} >
XACK {streamKey} {groupName} {id}
XINFO GROUPS {streamKey}
XINFO CONSUMERS {streamKey} {groupName}

Pending Recovery

XPENDING {streamKey} {groupName}
XAUTOCLAIM {streamKey} {groupName} {consumerName} {minIdleTime} 0-0 COUNT {count}

Consumer Cleanup

XGROUP DELCONSUMER {streamKey} {groupName} {consumerName}

Assignment Lease

SET {leaseKey} {ownerPayload} NX PX {leaseTtlMillis}
GET {leaseKey}
DEL {leaseKey}

Group Coordinator

SET {coordinatorKey} {coordinatorPayload} NX PX {coordinatorTtlMillis}
GET {coordinatorKey}
PEXPIRE {coordinatorKey} {coordinatorTtlMillis}

Assignment Store

SET {assignmentPlanKey} {assignmentPlanPayload}
GET {assignmentPlanKey}
PUBLISH redis-stream:membership:{moduleName} {eventPayload}

DLQ

XADD {streamKey}:dlq * originalStream {streamKey} originalId {id} payload {payload} reason {reason}

Trim

XTRIM {streamKey} MAXLEN ~ {maxLen}

⸻

21. Metric 설계

Micrometer metric:

redis_stream_shard_assigned
redis_stream_consumer_active
redis_stream_messages_read_total
redis_stream_messages_ack_total
redis_stream_messages_failed_total
redis_stream_messages_dlq_total
redis_stream_pending_count
redis_stream_lag
redis_stream_reclaim_total
redis_stream_handler_latency_seconds
redis_stream_consumer_idle_millis
redis_stream_consumer_cleanup_total
redis_stream_shard_lease_acquired
redis_stream_shard_lease_lost_total
redis_stream_assignment_generation
redis_stream_assignment_degraded
redis_stream_membership_generation
redis_stream_membership_changes_total
redis_stream_membership_scan_latency_seconds
redis_stream_membership_events_received_total
redis_stream_membership_events_published_total
redis_stream_membership_event_refresh_total
redis_stream_coordinator_active
redis_stream_coordinator_epoch
redis_stream_coordinator_changes_total
redis_stream_assigned_shards_per_pod
redis_stream_active_threads_configured
redis_stream_active_threads_running
redis_stream_pod_state

추천 label:

application
stream_prefix
stream_key
shard_index
consumer_group
consumer_name
pod_name
assignment_state
pod_state

⸻

22. 장애 시나리오

22.1 Pod Kill

1. Pod heartbeat key TTL 만료
2. 다른 Pod들이 active Pod 목록에서 제거 감지
3. assignment 재계산
4. shard lease가 만료되거나 새 owner가 lease 획득
5. orphan shard가 다른 Pod에 배정
6. 새 owner Pod가 active-threads 검증 후 consumer thread를 재분배
7. XAUTOCLAIM으로 pending message 회수

22.2 Handler Exception

1. handler 예외 발생
2. ACK 미수행
3. message는 PEL에 유지
4. min-idle-time 이후 reclaim
5. max retry 초과 시 DLQ 이동

22.3 Redis 장애

1. Redis command timeout 발생
2. worker는 exponential backoff 적용
3. heartbeat 실패 시 registry TTL 만료 가능
4. Redis 복구 후 group/consumer 재초기화
5. assignment 재계산 후 consume 재개

22.4 Shard Hotspot

1. 특정 shard lag 증가
2. metric alert 발생
3. shard key 재검토 또는 shard-count 증설 필요
4. shard-count 변경은 producer/consumer 동시 배포 전략 필요

22.5 Assignment Split Brain

1. registry scan 지연 또는 network pause로 Pod별 active Pod 목록이 잠시 달라진다.
2. 두 Pod가 같은 shard owner라고 판단할 수 있다.
3. lease 획득에 성공한 Pod의 worker만 XREADGROUP을 수행한다.
4. lease를 잃은 Pod의 해당 shard worker들은 DRAINING으로 전환한다.
5. 중복 처리 가능성을 완전히 제거할 수는 없으므로 handler idempotency는 필수이다.

22.6 Dead Consumer 누적

1. Pod 재시작이 반복되면 과거 consumer name이 XINFO CONSUMERS에 남을 수 있다.
2. consumer-cleanup job이 idle consumer를 찾는다.
3. pending이 있으면 XAUTOCLAIM으로 먼저 회수한다.
4. pending이 0이면 XGROUP DELCONSUMER로 metadata를 삭제한다.

22.7 Scale-in 후 active-threads 부족

1. Pod 수가 줄어 남은 Pod의 assignedShardCount가 증가한다.
2. assignment 재계산 결과 assignedShardCount > active-threads가 된다.
3. 해당 Pod는 새 shard lease를 획득하지 않고 DEGRADED 상태로 전환한다.
4. redis_stream_assignment_degraded metric과 log로 설정 부족을 노출한다.
5. 운영자는 Pod 수를 늘리거나 active-threads 설정을 증가시켜 재배포한다.

22.8 Coordinator 갑작스러운 종료

1. coordinator Pod가 OOM, node down, SIGKILL 등으로 즉시 죽는다.
2. coordinator lease renew가 중단된다.
3. 기존 shard owner들은 shard lease가 살아 있는 동안 기존 assignment로 consume을 계속한다.
4. coordinator-ttl이 지나 coordinator lease가 만료된다.
5. 살아 있는 Pod 중 하나가 새 coordinator로 선출된다.
6. 새 coordinator가 coordinatorEpoch을 증가시킨다.
7. 새 coordinator가 pod registry snapshot과 이전 assignment plan을 읽는다.
8. Sticky Balanced Assignment로 새 generation을 만든다.
9. follower Pod들이 새 assignment plan을 적용한다.
10. stale epoch/generation의 assignment plan은 무시한다.

⸻

23. Shard Count 변경 전략

Redis Stream shard 수를 변경하면 동일 partition key의 hash 결과가 달라진다.

예시:

partitionKey = user-123

v1 shard-count = 6
hash(user-123) % 6 = 2
stream = notification:v1:2

동일 prefix에서 shard-count만 12로 바꾸면:

hash(user-123) % 12 = 8
stream = notification:v1:8

이 경우 같은 partition key로 과거에 접근 가능하던 shard와 신규 계산 결과가 달라진다. 따라서 과거 메시지를 찾거나 drain할 때 같은 partition key로 기존 shard에 접근할 수 없게 된다.

결론:

* shard-count는 stream prefix version에 종속된 불변값이다.
* 동일 prefix에서 shard-count만 바꾸는 방식은 금지한다.
* shard-count 변경은 반드시 신규 stream prefix version을 만든다.
* producer/consumer는 prefix version과 shard-count를 하나의 routing manifest로 취급한다.

Routing manifest 예시:

{
"streamPrefix": "notification:v1",
"shardCount": 6,
"hashAlgorithm": "murmur3-128",
"hashVersion": "v1",
"state": "ACTIVE"
}

{
"streamPrefix": "notification:v2",
"shardCount": 12,
"hashAlgorithm": "murmur3-128",
"hashVersion": "v1",
"state": "ACTIVE"
}

producer는 메시지에 routing metadata를 포함한다.

{
"partitionKey": "user-123",
"streamPrefix": "notification:v1",
"shardIndex": "2",
"hashAlgorithm": "murmur3-128",
"hashVersion": "v1",
"idempotencyKey": "..."
}

이 metadata가 있어야 shard-count 변경 이후에도 메시지가 어떤 version과 shard에 기록됐는지 추적할 수 있다.

권장 전략:

1. 신규 stream prefix를 만든다.
2. 신규 prefix에 대응하는 routing manifest를 배포한다.
3. producer를 dual-write 또는 신규 prefix write로 전환한다.
4. consumer는 v1과 v2 prefix를 모두 읽을 수 있어야 한다.
5. 기존 stream backlog를 모두 drain한다.
6. consumer를 신규 prefix 기준으로 전환한다.
7. 기존 stream을 제거한다.

예시:

notification:v1:0 ~ notification:v1:5
notification:v2:0 ~ notification:v2:11

전환 단계:

Phase 1: Prepare

* v2 shard stream과 consumer group을 미리 생성한다.
* routing manifest에 v1과 v2를 함께 등록한다.
* v2 consumer를 배포하되 read를 비활성화하거나 shadow mode로 둔다.
* producer는 여전히 v1에만 write한다.

Phase 2: Dual Write

* producer가 v1과 v2에 모두 XADD한다.
* message idempotency key와 routing metadata를 payload에 포함한다.
* consumer는 v1만 business side effect를 수행한다.
* v2는 lag, shard 분포, memory 사용량만 검증한다.

Phase 3: Cutover

* consumer의 active prefix를 v2로 전환한다.
* producer는 v2 write만 유지한다.
* v1 consumer는 backlog drain만 수행한다.
* v1 drain은 v1 routing manifest의 shard-count=6 기준으로 수행한다.

Phase 4: Cleanup

* v1 lag가 0이고 pending이 0인지 확인한다.
* v1 consumer group과 stream key를 제거한다.

금지:

* 동일 prefix에서 shard-count만 6에서 12로 변경하는 방식
* producer와 consumer가 서로 다른 shard-count를 바라보는 배포
* idempotency key 없이 dual-write하는 방식
* message가 기록된 prefix version 없이 partition key만으로 과거 shard를 재계산하는 방식

⸻

24. 설계상 중요한 제약

* Redis Stream은 Kafka partition처럼 broker가 consumer assignment를 자동 관리하지 않는다.
* shard assignment는 애플리케이션 레벨 책임이다.
* Redis Stream은 기본적으로 at-least-once delivery이다.
* handler는 반드시 idempotent하게 작성해야 한다.
* consumer group은 stream key별로 생성되므로 모든 shard에 group을 생성해야 한다.
* ACK와 business side effect 사이의 원자성은 Redis만으로 보장되지 않는다.
* shard 수 변경은 운영 중 단순 변경하면 안 된다.

⸻

25. 권장 기본값

redis-stream:
shard-count: 6
group:
start-id: "$"
consumer:
active-threads: 4
batch-size: 50
block-timeout: 2000ms
assignment:
strategy: STICKY_BALANCED
heartbeat-interval: 5s
heartbeat-ttl: 15s
rebalance-interval: 5s
lease-ttl: 20s
lease-renew-interval: 5s
ack:
mode: ACK_ONLY
pending:
reclaim-enabled: true
min-idle-time: 60s
scan-interval: 30s
max-messages-per-scan: 1000
consumer-cleanup:
enabled: true
consumer-ttl: 10m
scan-interval: 1m
retry:
max-attempts: 5
dlq:
enabled: true
trim:
enabled: true
strategy: MAXLEN
max-len: 1000000

⸻

26. MVP 범위

포함

* N개 stream shard 생성 및 group 생성
* 설정 기반 consumer group name
* producer shard routing
* Pod 내부 thread worker 생성
* Balanced 기반 Pod 간 shard assignment
* Redis heartbeat 기반 pod/worker registry
* Redis lease 기반 group coordinator
* shard owner lease 기반 중복 read 완화
* XREADGROUP 기반 consume
* XACK 처리
* XAUTOCLAIM 기반 pending recovery
* XGROUP DELCONSUMER 기반 dead consumer cleanup
* DLQ 발행
* Micrometer metric

제외

* shard-count 동적 변경
* strict global ordering
* exactly-once processing
* Redis Cluster resharding 자동 대응
* Redis Cluster client별 scan 차이 자동 추상화
* Redis 8.2+ 전용 XACKDEL 기본 사용
* Redis 8.8 milestone XNACK 기반 retry

⸻

27. 구현 플래닝

27.1 Milestone 1: Core Sharding

목표:

* stream key resolver 구현
* stable hash 기반 shard routing 구현
* producer API 구현
* 모든 shard에 consumer group 생성

완료 기준:

* 동일 shardKey는 항상 같은 shard로 routing된다.
* shard-count, stream-prefix, consumer-group 설정 검증이 동작한다.
* BUSYGROUP은 정상 상태로 처리된다.

27.2 Milestone 2: Single Pod Consumer

목표:

* Pod 내부 thread worker 생성
* thread별 consumer name 생성
* XREADGROUP read loop 구현
* handler 성공 시 XACK 처리

완료 기준:

* 1 Pod N thread 환경에서 모든 shard를 중복 없이 읽는다.
* handler exception 시 ACK하지 않는다.
* graceful shutdown 시 신규 read를 멈추고 in-flight 처리를 drain한다.

27.3 Milestone 3: Multi Pod Assignment

목표:

* Redis heartbeat pod/worker registry 구현
* group coordinator election 구현
* active Pod 조회 구현
* Sticky Balanced assignment 구현
* shard lease manager 구현

완료 기준:

* Pod 추가/삭제 시 assignment가 재계산된다.
* lease를 보유한 worker만 shard를 읽는다.
* lease 상실 시 worker가 DRAINING으로 전환된다.

27.4 Milestone 4: Recovery

목표:

* XAUTOCLAIM 기반 pending recovery 구현
* retry count 기준 DLQ 이동 구현
* dead consumer cleanup 구현

완료 기준:

* 강제 종료된 worker의 pending message가 다른 owner에게 회수된다.
* max-attempts 초과 메시지는 DLQ로 이동 후 원본 ACK 처리된다.
* pending이 0인 idle consumer는 DELCONSUMER로 제거된다.

27.5 Milestone 5: Observability and Operations

목표:

* Micrometer metric 노출
* lag/pending/lease/cleanup metric 구현
* 기본 alert rule 샘플 제공
* 운영용 actuator endpoint 또는 debug log 제공

완료 기준:

* shard별 lag와 pending count를 확인할 수 있다.
* shard owner와 lease owner를 확인할 수 있다.
* DLQ 증가, reclaim 증가, lease lost를 alert로 감지할 수 있다.

27.6 Milestone 6: Production Hardening

목표:

* Redis 장애 backoff
* Redis Cluster scan 전략 검증
* load test
* rolling deploy 시나리오 검증
* shard-count v1/v2 전환 runbook 작성

완료 기준:

* Redis 일시 장애 후 consumer가 재초기화된다.
* rolling deploy 중 메시지가 유실되지 않는다.
* 중복 처리 가능성을 idempotency test로 검증한다.

⸻

28. 최종 요약

본 모듈은 Redis Stream을 Kafka처럼 직접 대체하려는 목적이 아니라, 이미 Redis를 사용 중인 Spring Boot/Kotlin 서비스에서 lightweight message queue를 안정적으로 운영하기 위한 공통 모듈이다.

핵심 설계는 다음과 같다.

* Stream을 N개 shard key로 분리한다.
* 모든 shard는 동일 consumer group name을 사용한다.
* consumer group name은 서비스 설정으로 주입한다.
* Pod 내부 thread를 worker로 모델링한다.
* Redis heartbeat registry로 active Pod 목록을 구성한다.
* Group Coordinator가 Sticky Balanced assignment로 Pod 간 shard owner를 결정한다.
* owner Pod의 worker가 shard lease를 획득한 뒤 해당 shard를 읽는다.
* pending message는 shard owner가 XAUTOCLAIM으로 회수한다.
* 죽은 consumer는 pending 회수 후 DELCONSUMER로 정리한다.
* 실패 메시지는 retry 후 DLQ로 이동한다.
* 처리 로직은 idempotent해야 한다.
