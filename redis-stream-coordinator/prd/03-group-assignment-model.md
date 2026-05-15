# Group Metadata and Assignment Model

## Group Metadata

```json
{
  "streamPrefix": "summary",
  "consumerGroup": "summary-group",
  "groupEpoch": 12,
  "metadataVersion": 8,
  "assignmentEpoch": 11,
  "state": "STABLE",
  "activeWriteVersion": "v2",
  "readableVersions": ["v1", "v2"],
  "assignmentStrategy": "STICKY_PARTITION",
  "updatedAt": "2026-05-15T10:00:00Z"
}
```

`groupEpoch`은 group metadata가 바뀔 때 증가한다.

Trigger:

* member join
* member heartbeat TTL 만료
* member metadata 변경
* readable stream version 변경
* Coordinator Admin API로 요청된 shard count migration 시작/완료
* assignment strategy 변경
* coordinator가 member를 fenced 처리

## Member Metadata

```json
{
  "memberId": "018f8d27-8d71-7a7c-a7e7-b3f77c613d7f",
  "memberName": "summary-consumer-a",
  "memberRunId": "018f8d27-8bdb-7dc8-8db4-141575e8749e",
  "state": "ACTIVE",
  "memberEpoch": 11,
  "supportedAssignmentStrategies": ["STICKY_PARTITION"],
  "metadataVersion": 8,
  "maxConcurrency": 12,
  "currentAssignment": [
    {"streamVersion": "v2", "shardIndex": 1},
    {"streamVersion": "v1", "shardIndex": 3}
  ],
  "revoking": [],
  "lastHeartbeatAt": "2026-05-15T10:00:00Z",
  "expiresAt": "2026-05-15T10:00:15Z",
  "idleExpiresAt": "2026-05-15T10:00:30Z"
}
```

Member metadata는 member lifecycle의 source of truth이다.

* `lastHeartbeatAt`: coordinator가 마지막 heartbeat를 정상 반영한 시각이다.
* `expiresAt`: heartbeat TTL 기준 만료 시각이다. 이 시각 이후에는 신규 assign 대상에서 제외한다.
* `idleExpiresAt`: idle timeout 기준 제거 시각이다. 이 시각 이후에는 `EXPIRED`로 fencing하고 재할당을 시작한다.

## Target Assignment

Target assignment은 coordinator가 계산한 desired state이다.

```json
{
  "streamPrefix": "summary",
  "consumerGroup": "summary-group",
  "assignmentEpoch": 12,
  "groupEpoch": 12,
  "members": {
    "member-a": [
      {"streamVersion": "v2", "shardIndex": 0}
    ],
    "member-b": [
      {"streamVersion": "v2", "shardIndex": 1}
    ]
  }
}
```

Invariant:

* readable shard는 정확히 하나의 target owner를 가진다.
* unknown member에게 shard를 assign하지 않는다.
* member capacity를 초과하지 않는다.
* `DRAINING` shard도 readable이면 assignment 대상이다.
* revoke 완료 전 shard는 새 member의 effective assignment에 포함하지 않는다.

## Current Assignment

Current assignment는 member가 실제로 적용 중이라고 보고한 state이다.

```json
{
  "memberId": "member-a",
  "memberEpoch": 11,
  "owned": [
    {"streamVersion": "v2", "shardIndex": 0}
  ],
  "revoking": [
    {"streamVersion": "v1", "shardIndex": 4}
  ],
  "ackedRevocations": [
    {"streamVersion": "v1", "shardIndex": 4}
  ]
}
```

Coordinator는 current assignment를 보고 revoke ack가 들어온 shard만 새 owner에게 assign한다.

## Member Removal State

Member 제거는 두 경로로 진행된다.

* graceful scale-in: member가 `LEAVING`을 보고하면 coordinator가 revoke를 명령하고 ack를 기다린다.
* idle removal: heartbeat가 `idle-timeout`을 넘기면 coordinator가 member를 `EXPIRED`로 fencing하고 lease TTL 이후 재할당한다.

`EXPIRED` member metadata는 즉시 삭제하지 않는다. coordinator failover와 운영 디버깅을 위해 `metadata-retention` 동안 tombstone으로 보관하고, current assignment가 비어 있거나 lease TTL이 지난 뒤 삭제한다.

## Assignment Strategy

MVP 기본은 `STICKY_PARTITION`이다. member의 `supportedAssignmentStrategies`는 협상용 복수 후보가 아니라 capability 확인 필드로 사용한다. MVP member는 시작부터 `["STICKY_PARTITION"]`을 보고해야 하며, coordinator는 이 값을 지원하지 않는 member에게 shard를 assign하지 않는다.

목표:

* shard를 member capacity 안에서 균등 분배한다.
* 기존 owner를 최대한 유지한다.
* revoke가 필요한 shard만 이동한다.
* `ACTIVE`와 `DRAINING` readable version을 모두 assignment 대상으로 포함한다.

`STICKY_PARTITION`은 현재 target assignment를 입력으로 사용해 movement cost를 최소화한다. scale-out에서는 신규 member capacity만큼 일부 shard만 이동하고, scale-in에서는 사라지는 member의 shard만 살아 있는 member에게 재분배한다.
