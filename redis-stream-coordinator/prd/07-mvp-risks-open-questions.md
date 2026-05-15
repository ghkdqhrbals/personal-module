# MVP Scope, Tradeoffs, Risks, and Open Questions

## MVP Scope

Included:

* embedded coordinator worker
* Coordinator Admin API create/scale
* Redis coordinator lease
* member heartbeat record
* member scale-out/scale-in sequence
* idle member expiration and cleanup
* group epoch, assignment epoch, member epoch
* server-side `STICKY_PARTITION` assignor
* target assignment store
* current assignment reporting
* revoke before assign dependency handling
* shard lease fencing
* active/draining dual-read
* stream version migration
* pending recovery
* idempotency processing marker
* coordinator/member metrics

Excluded:

* Kafka protocol compatibility
* external coordinator service deployment
* client-side assignor delegation
* multiple concurrent migrations per stream prefix
* admin UI
* bounded-load assignor
* hot shard auto split
* Redis Cluster resharding automation
* exactly-once external side effects

## Tradeoffs

### Coordinator-Managed Assignment

Benefits:

* 운영자가 target/current assignment를 한 곳에서 확인할 수 있다.
* revoke before assign 순서를 coordinator가 강제한다.
* stuck rebalance와 member fencing을 명확히 모델링할 수 있다.
* member client logic이 단순해진다.

Costs:

* coordinator lease와 failover를 구현해야 한다.
* coordinator state store가 운영상 중요해진다.
* coordinator bug는 group 전체 assignment에 영향을 줄 수 있다.

### Embedded Coordinator

Benefits:

* 별도 service 배포가 필요 없다.
* 기존 application runtime 안에서 시작할 수 있다.
* Redis lease로 active coordinator를 하나만 유지할 수 있다.

Costs:

* application runtime resource와 coordinator resource가 섞인다.
* coordinator failover latency는 lease TTL에 묶인다.
* 운영자가 어떤 runtime instance가 coordinator인지 관측해야 한다.

## Risks

* coordinator lease renew bug로 split-brain assignment가 생길 수 있다.
* target assignment write와 member current assignment 처리 순서가 꼬이면 duplicate read 위험이 있다.
* stuck revoke 처리 정책이 과격하면 불필요한 replay가 늘어난다.
* Redis outage 시 coordinator와 data plane이 함께 영향받는다.
* assignment state가 손상되면 수동 복구 runbook이 필요하다.
* shard count migration 중 old/new key ordering이 깨질 수 있다.

## Open Questions

* coordinator worker를 embedded로 둘지, 별도 service로 분리할지?
* member UUID를 member runtime이 직접 생성하는 방식을 유지할지, coordinator가 registration ack와 함께 부여할지?
* target assignment assignor는 `STICKY_PARTITION`으로 충분한지?
* member capacity는 max-concurrency만 볼지, lag/CPU 같은 dynamic signal도 반영할지?
* Redis key update는 Lua CAS만 쓸지, Redis Streams event log를 둘지?
* Admin API 인증/인가를 service auth로 둘지 별도 operator token으로 둘지?
* stuck revoke timeout 이후 forced revoke는 자동으로 할지 operator approval을 요구할지?
* DRAINING old version의 FAILED message를 어떻게 PEL에서 제거할지?

## Decision Needed

* MVP coordinator mode: `EMBEDDED_LEASED`
* MVP assignor: `STICKY_PARTITION`
* MVP member identity: member-runtime-generated UUID
* MVP state store: Redis HASH/JSON + Lua CAS
* MVP rebalance style: coordinator-driven incremental reconciliation
* MVP shard count source of truth: Coordinator Admin API로 생성/변경된 coordinator metadata
