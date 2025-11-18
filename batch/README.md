# Batch Module

논문 구독 서비스를 위한 Spring Batch 모듈입니다.

## 기능

### 1. 논문 스캔 배치 (scanNewPapersStep)
- arXiv API를 통해 새로운 논문 수집
- 구독 주제와 매칭하여 관련 논문 필터링
- DB에 신규 논문 저장

### 2. Citation 점수 업데이트 (updateCitationScoresStep)
- OpenAlex API를 통해 논문의 인용 횟수 업데이트
- 사용자가 설정한 citation 임계값과 비교
- 임계값 이상인 논문 플래그 처리

### 3. 알림 발송 (sendNotificationsStep)
- 구독자 설정 조회
- Citation 임계값 초과 논문 확인
- 이메일/푸시 알림 발송

## 실행 방법

```bash
# 배치 애플리케이션 실행
./gradlew :batch:bootRun

# 특정 Job 실행
./gradlew :batch:bootRun --args='--job.name=paperSubscriptionJob'
```

## 설정

`application.yaml`에서 다음 설정 가능:
- DB 연결 정보
- Redis 연결 정보
- Batch 메타데이터 초기화 옵션
- 로깅 레벨

## TODO

- [ ] arXiv API 연동
- [ ] OpenAlex API 연동
- [ ] 이메일 발송 서비스
- [ ] 푸시 알림 서비스
- [ ] Quartz Scheduler 연동 (정기 실행)
- [ ] 배치 실행 모니터링

