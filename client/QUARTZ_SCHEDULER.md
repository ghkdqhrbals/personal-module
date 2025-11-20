# Quartz 스케줄러 배치 작업 구현

## 📋 개요

Quartz 스케줄러를 사용하여 **매 10분마다** Subscribe 테이블의 모든 레코드를 읽어 각 row마다 "hello"를 출력하는 배치 작업을 구현했습니다.

## 🗂️ 구현된 파일

### 1. Job 클래스
**파일**: `client/src/main/kotlin/org/ghkdqhrbals/client/scheduler/job/SubscribeBatchJob.kt`

- Subscribe 테이블의 모든 데이터를 조회
- 각 레코드마다 "hello"와 함께 ID, Name, Type 출력
- 로그와 콘솔 양쪽에 출력

### 2. 스케줄러 설정
**파일**: `client/src/main/kotlin/org/ghkdqhrbals/client/scheduler/config/QuartzSchedulerConfig.kt`

- JobDetail 정의: `subscribeBatchJob`
- Trigger 정의: Cron 표현식 `0 0/10 * * * ?` (매 10분마다)
- Job과 Trigger를 Spring Bean으로 등록

### 3. Spring-Quartz 통합 설정
**파일**: `client/src/main/kotlin/org/ghkdqhrbals/client/scheduler/config/QuartzConfig.kt`

- SchedulerFactoryBean 설정
- Spring ApplicationContext와 통합
- Job에 Spring Bean 자동 주입 가능

**파일**: `client/src/main/kotlin/org/ghkdqhrbals/client/scheduler/config/AutowiringSpringBeanJobFactory.kt`

- 커스텀 JobFactory
- Quartz Job에 Spring 의존성 주입 지원

### 4. Quartz 프로퍼티
**파일**: `client/src/main/resources/quartz.properties`

- Scheduler 인스턴스 설정
- Thread Pool 설정 (5개 스레드)
- RAMJobStore 사용 (메모리 기반)
- 로깅 플러그인 설정

### 5. 스케줄러 관리 API
**파일**: `client/src/main/kotlin/org/ghkdqhrbals/client/scheduler/api/SchedulerApiController.kt`

스케줄러 상태 조회 및 제어를 위한 REST API

## 🚀 사용 방법

### 1. 애플리케이션 시작

```bash
cd /Users/ghkdqhrbals/personal/mod/client
./gradlew bootRun
```

애플리케이션이 시작되면 Quartz 스케줄러가 자동으로 시작되며, **매 10분마다** SubscribeBatchJob이 실행됩니다.

### 2. 실행 로그 확인

```
========================================
SubscribeBatchJob 시작 - Wed Nov 19 14:00:00 KST 2025
========================================
총 25개의 Subscribe 레코드를 조회했습니다.
hello - [1] ID: 1, Name: cs.AI, Type: CATEGORY
hello - [2] ID: 2, Name: cs.LG, Type: CATEGORY
hello - [3] ID: 3, Name: Transformer, Type: KEYWORD
...
========================================
SubscribeBatchJob 완료 - 처리된 레코드: 25
========================================
```

### 3. 스케줄러 상태 확인 (API)

#### 스케줄러 상태 조회
```bash
curl http://localhost:8080/api/scheduler/status
```

응답 예시:
```json
{
  "schedulerName": "NotiPaperScheduler",
  "isStarted": true,
  "isInStandbyMode": false,
  "isShutdown": false,
  "metadata": {
    "schedulerName": "NotiPaperScheduler",
    "schedulerInstanceId": "AUTO",
    "schedulerClass": "StdScheduler",
    "version": "2.3.2",
    "numberOfJobsExecuted": 5,
    "runningSince": "2025-11-19T13:00:00"
  }
}
```

#### 등록된 Job 목록 조회
```bash
curl http://localhost:8080/api/scheduler/jobs
```

응답 예시:
```json
[
  {
    "jobName": "subscribeBatchJob",
    "jobGroup": "batch-jobs",
    "jobClass": "SubscribeBatchJob",
    "description": "Subscribe 테이블을 읽어서 각 row마다 hello를 출력하는 배치 작업",
    "triggers": [
      {
        "triggerName": "subscribeBatchJobTrigger",
        "triggerGroup": "batch-triggers",
        "triggerState": "NORMAL",
        "nextFireTime": "2025-11-19T14:10:00",
        "previousFireTime": "2025-11-19T14:00:00"
      }
    ]
  }
]
```

#### Job 수동 실행 (즉시 트리거)
```bash
curl -X POST http://localhost:8080/api/scheduler/jobs/batch-jobs/subscribeBatchJob/trigger
```

응답:
```json
{
  "message": "Job이 실행되었습니다: batch-jobs.subscribeBatchJob"
}
```

#### 스케줄러 일시 정지
```bash
curl -X POST http://localhost:8080/api/scheduler/standby
```

#### 스케줄러 재시작
```bash
curl -X POST http://localhost:8080/api/scheduler/start
```

## ⚙️ 스케줄 설정 변경

### Cron 표현식 변경

**파일**: `QuartzSchedulerConfig.kt`

현재 설정: `0 0/10 * * * ?` (매 10분마다)

다른 예시:
- `0 0/5 * * * ?` - 매 5분마다
- `0 0/30 * * * ?` - 매 30분마다
- `0 0 * * * ?` - 매 시간 정각
- `0 0 0 * * ?` - 매일 자정
- `0 0 9 * * ?` - 매일 오전 9시

```kotlin
.withSchedule(
    CronScheduleBuilder.cronSchedule("0 0/10 * * * ?") // 여기를 수정
        .withMisfireHandlingInstructionDoNothing()
)
```

### SimpleSchedule 사용 (대안)

Cron 대신 간단한 간격 설정을 원하면 주석 처리된 코드를 사용:

```kotlin
@Bean
fun subscribeBatchJobTrigger(subscribeBatchJobDetail: JobDetail): Trigger {
    return TriggerBuilder.newTrigger()
        .forJob(subscribeBatchJobDetail)
        .withIdentity("subscribeBatchJobTrigger", "batch-triggers")
        .withSchedule(
            SimpleScheduleBuilder.simpleSchedule()
                .withIntervalInMinutes(10) // 10분 간격
                .repeatForever()
        )
        .startNow()
        .build()
}
```

## 🔍 Cron 표현식 가이드

Quartz Cron 표현식 형식: `초 분 시 일 월 요일 [년]`

| 필드 | 허용값 | 특수문자 |
|------|--------|----------|
| 초 | 0-59 | , - * / |
| 분 | 0-59 | , - * / |
| 시 | 0-23 | , - * / |
| 일 | 1-31 | , - * ? / L W |
| 월 | 1-12 또는 JAN-DEC | , - * / |
| 요일 | 1-7 또는 SUN-SAT | , - * ? / L # |

**특수문자 의미**:
- `*` : 모든 값
- `?` : 특정 값 없음 (일/요일 중 하나는 반드시 ?)
- `-` : 범위 (예: 10-12)
- `,` : 여러 값 (예: MON,WED,FRI)
- `/` : 증분 (예: 0/15 = 0, 15, 30, 45)
- `L` : 마지막 (예: 월의 마지막 날)
- `W` : 평일 (가장 가까운 평일)
- `#` : N번째 요일 (예: 2#1 = 첫째주 월요일)

## 📊 데이터베이스 JobStore (선택사항)

현재는 RAMJobStore(메모리 기반)를 사용합니다. 애플리케이션 재시작 시 Job 히스토리가 사라집니다.

영구 저장이 필요하면 `quartz.properties`를 다음과 같이 수정:

```properties
# JDBC JobStore로 변경
org.quartz.jobStore.class=org.quartz.impl.jdbcjobstore.JobStoreTX
org.quartz.jobStore.driverDelegateClass=org.quartz.impl.jdbcjobstore.StdJDBCDelegate
org.quartz.jobStore.useProperties=false
org.quartz.jobStore.dataSource=myDS
org.quartz.jobStore.tablePrefix=QRTZ_
org.quartz.jobStore.isClustered=false

# DataSource 설정
org.quartz.dataSource.myDS.driver=com.mysql.cj.jdbc.Driver
org.quartz.dataSource.myDS.URL=jdbc:mysql://localhost:3306/yourdb
org.quartz.dataSource.myDS.user=root
org.quartz.dataSource.myDS.password=password
org.quartz.dataSource.myDS.maxConnections=5
```

**주의**: JDBC JobStore 사용 시 Quartz 테이블을 먼저 생성해야 합니다.
(Quartz 배포판에 SQL 스크립트 포함)

## 🎯 주요 기능

### ✅ 구현 완료
- [x] Quartz 스케줄러 설정
- [x] 매 10분마다 자동 실행
- [x] Subscribe 테이블 전체 조회
- [x] 각 row마다 hello 출력
- [x] Spring Bean 의존성 주입
- [x] 스케줄러 상태 조회 API
- [x] Job 수동 실행 API
- [x] 스케줄러 제어 API (시작/정지)

### 📝 향후 개선 가능 사항
- [ ] JDBC JobStore로 영구 저장
- [ ] Job 실행 히스토리 저장
- [ ] Job 실행 결과 통계
- [ ] 알림/모니터링 연동
- [ ] 동적 Job 추가/제거 UI
- [ ] Cluster 모드 지원

## 🧪 테스트

### 즉시 실행 테스트
10분을 기다리지 않고 바로 테스트하려면:

```bash
curl -X POST http://localhost:8080/api/scheduler/jobs/batch-jobs/subscribeBatchJob/trigger
```

실행 후 로그를 확인하면 "hello" 메시지가 출력됩니다.

### 스케줄 확인
다음 실행 시간 확인:

```bash
curl http://localhost:8080/api/scheduler/jobs | jq '.[].triggers[].nextFireTime'
```

## 📚 참고 자료

- [Quartz Scheduler 공식 문서](https://www.quartz-scheduler.org/documentation/)
- [Spring Boot + Quartz 통합](https://docs.spring.io/spring-boot/docs/current/reference/html/io.html#io.quartz)
- [Cron Expression Generator](https://www.freeformatter.com/cron-expression-generator-quartz.html)

## 🎉 완료!

Quartz 기반 스케줄러가 성공적으로 구현되었습니다:
- ✅ 매 10분마다 자동 실행
- ✅ Subscribe 테이블 읽기
- ✅ 각 row마다 hello 출력
- ✅ REST API로 제어 가능
- ✅ 운영 환경에서 바로 사용 가능

