# Redis Stream Prometheus 메트릭 모니터링

## 개요

Spring Boot 애플리케이션이 **30초마다** Redis Stream 정보를 자동으로 수집하여 Prometheus 메트릭으로 노출합니다.

## 아키텍처

```
RedisStreamMetricsCollector (Spring @Scheduled)
    ↓ (30초마다)
Redis Stream 조회
    ↓
Micrometer Gauges 업데이트
    ↓
/actuator/prometheus 엔드포인트
    ↓ (Prometheus가 scrape)
Prometheus (15초마다 scrape)
    ↓
Grafana 대시보드
```

## 수집되는 메트릭

### 1. Stream 메트릭

#### `redis_stream_length`
- **설명**: Stream의 총 메시지 수
- **태그**: `stream` (스트림 이름)
- **타입**: Gauge

#### `redis_stream_first_entry`
- **설명**: Stream의 첫 번째 엔트리 존재 여부
- **태그**: `stream`, `entry_id`
- **타입**: Gauge (0 또는 1)

#### `redis_stream_last_entry`
- **설명**: Stream의 마지막 엔트리 존재 여부
- **태그**: `stream`, `entry_id`
- **타입**: Gauge (0 또는 1)

### 2. Consumer Group 메트릭

#### `redis_stream_group_consumers`
- **설명**: 그룹의 활성 Consumer 수
- **태그**: `stream`, `group`
- **타입**: Gauge

#### `redis_stream_group_pending`
- **설명**: 그룹의 Pending 메시지 수
- **태그**: `stream`, `group`
- **타입**: Gauge

#### `redis_stream_group_lag` ⭐ **중요**
- **설명**: 그룹의 Lag (처리되지 않은 메시지 수)
- **태그**: `stream`, `group`
- **타입**: Gauge
- **알람 추천**: lag > 1000

### 3. Consumer 메트릭

#### `redis_stream_consumer_pending`
- **설명**: 각 Consumer의 Pending 메시지 수
- **태그**: `stream`, `group`, `consumer`
- **타입**: Gauge

#### `redis_stream_consumer_idle_millis`
- **설명**: Consumer의 유휴 시간 (밀리초)
- **태그**: `stream`, `group`, `consumer`
- **타입**: Gauge
- **알람 추천**: idle > 300000 (5분 이상 유휴)

## 설정

### 1. 모니터링할 Stream 설정

`RedisStreamMetricsCollector.kt`에서 수정:

```kotlin
private val monitoringStreams = listOf(
    "summary:1",
    "summary:2",
    "summary:3"
)
```

파티션이 있는 경우 (예: `summary:1`, `summary:2`, `summary:3`) 자동으로 모든 파티션을 조회합니다.

### 2. 수집 주기 변경

기본값: 30초

```kotlin
@Scheduled(fixedDelay = 30000, initialDelay = 5000)
```

- `fixedDelay`: 이전 작업 완료 후 대기 시간 (밀리초)
- `initialDelay`: 애플리케이션 시작 후 첫 실행까지 대기 시간

### 3. Prometheus 스크랩 주기

`prometheus.yml`:

```yaml
scrape_configs:
  - job_name: 'rate-limiter-service'
    metrics_path: '/actuator/prometheus'
    static_configs:
      - targets: ['host.docker.internal:8080']
    scrape_interval: 15s  # 15초마다 수집
```

## 사용 방법

### 1. 메트릭 확인

#### Spring Boot Actuator
```bash
curl http://localhost:8080/actuator/prometheus | grep redis_stream
```

출력 예시:
```
# HELP redis_stream_length
# TYPE redis_stream_length gauge
redis_stream_length{stream="summary:1",} 6065.0
redis_stream_length{stream="summary:2",} 3421.0

# HELP redis_stream_group_lag
# TYPE redis_stream_group_lag gauge
redis_stream_group_lag{stream="summary:1",group="my-group",} 1.0
redis_stream_group_lag{stream="summary:1",group="summary-consumer-group",} 0.0
```

#### Prometheus 쿼리
```
http://localhost:9090
```

쿼리 예시:
```promql
# Lag가 0보다 큰 그룹
redis_stream_group_lag > 0

# 특정 Stream의 길이
redis_stream_length{stream="summary:1"}

# 5분 이상 유휴 상태인 Consumer
redis_stream_consumer_idle_millis > 300000
```

### 2. Grafana 대시보드

#### 자동 프로비저닝 (권장)
대시보드가 자동으로 로드됩니다:
- 파일: `init/redis-stream-dashboard.json`
- URL: `http://localhost:3000/d/redis-stream-monitoring`

#### 수동 Import
1. Grafana 접속: `http://localhost:3000`
2. 계정: `admin` / `admin`
3. Dashboards → Import
4. `init/redis-stream-dashboard.json` 파일 업로드

### 3. 대시보드 패널

#### Panel 1: Stream Length
- Stream별 총 메시지 수
- 시계열 차트

#### Panel 2: Consumer Group Lag ⭐
- 그룹별 Lag
- 임계값: 노란색(100), 빨간색(1000)

#### Panel 3: Group Pending Messages
- 그룹별 Pending 메시지 수

#### Panel 4: Active Consumers
- 그룹별 활성 Consumer 수

#### Panel 5: Consumer Idle Time
- Consumer별 유휴 시간
- 임계값: 노란색(1분), 빨간색(5분)

## 알람 설정

### Prometheus Alerting Rules

`prometheus-alerts.yml` 생성:

```yaml
groups:
  - name: redis_stream_alerts
    interval: 30s
    rules:
      # Lag가 1000을 초과하는 경우
      - alert: HighStreamLag
        expr: redis_stream_group_lag > 1000
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "High lag detected in {{$labels.stream}}/{{$labels.group}}"
          description: "Lag is {{ $value }} messages"

      # Consumer가 5분 이상 유휴 상태
      - alert: ConsumerIdle
        expr: redis_stream_consumer_idle_millis > 300000
        for: 2m
        labels:
          severity: warning
        annotations:
          summary: "Consumer {{$labels.consumer}} is idle"
          description: "Idle for {{ $value }}ms"

      # Pending 메시지가 5000을 초과
      - alert: HighPendingMessages
        expr: redis_stream_group_pending > 5000
        for: 10m
        labels:
          severity: critical
        annotations:
          summary: "High pending messages in {{$labels.stream}}/{{$labels.group}}"
          description: "{{ $value }} pending messages"
```

`prometheus.yml`에 추가:

```yaml
rule_files:
  - 'prometheus-alerts.yml'
```

## 유용한 PromQL 쿼리

### 1. 전체 Stream의 총 메시지 수
```promql
sum(redis_stream_length)
```

### 2. Lag가 있는 그룹 목록
```promql
redis_stream_group_lag{lag!="0"}
```

### 3. 그룹별 처리율 (메시지/초)
```promql
rate(redis_stream_length[1m]) - rate(redis_stream_group_lag[1m])
```

### 4. Consumer당 평균 Pending 메시지
```promql
redis_stream_consumer_pending / on(stream, group) group_left redis_stream_group_consumers
```

### 5. 가장 느린 Consumer
```promql
topk(5, redis_stream_consumer_idle_millis)
```

### 6. Stream별 Lag 합계
```promql
sum by (stream) (redis_stream_group_lag)
```

## 트러블슈팅

### 1. 메트릭이 나타나지 않음

**확인사항:**
```bash
# 애플리케이션 로그 확인
tail -f logs/application.log | grep "Error collecting"

# Actuator 엔드포인트 확인
curl http://localhost:8080/actuator/prometheus | grep redis_stream

# Prometheus targets 확인
# http://localhost:9090/targets
```

### 2. Lag가 항상 0으로 표시됨

**원인:** Lua 스크립트가 ByteArray를 제대로 변환하지 못함

**해결:** 로그에서 다음 확인:
```
Raw Lua script result: [{"name":"my-group","lag":1,...}]
```

### 3. 스케줄러가 실행되지 않음

**확인사항:**
- `@EnableScheduling` 어노테이션이 `ClientApplication`에 있는지 확인
- 애플리케이션 로그에서 "collecting stream metrics" 검색

## 성능 고려사항

### 1. 스크랩 주기
- Prometheus: 15초 (기본값)
- Collector: 30초 (기본값)
- 권장: Collector ≥ Prometheus 스크랩 주기

### 2. Redis 부하
- Stream 3개, Group 2개, Consumer 10개 = 약 30개 Redis 명령/30초
- 부하가 적음 (초당 1 명령 미만)

### 3. 메모리 사용
- Gauge 메트릭: 약 1KB per metric
- 100개 메트릭 = 약 100KB

## 모범 사례

1. **중요한 메트릭에 집중**
   - Lag (가장 중요)
   - Pending
   - Consumer idle time

2. **알람 임계값 설정**
   - Lag > 1000: Warning
   - Lag > 5000: Critical
   - Idle > 5분: Warning

3. **정기적인 대시보드 확인**
   - 일일: Lag 추세
   - 주간: Consumer 성능
   - 월간: Stream 증가 패턴

4. **로그와 함께 확인**
   - 메트릭만으로는 부족할 수 있음
   - 에러 로그와 함께 분석

## 참고 링크

- [Micrometer Documentation](https://micrometer.io/)
- [Prometheus Query Language](https://prometheus.io/docs/prometheus/latest/querying/)
- [Grafana Dashboards](https://grafana.com/docs/grafana/latest/dashboards/)

