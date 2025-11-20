# Subscribe 기능 구현 완료

## 📋 구현 내용

### 1. 엔티티 (Entity)

#### Subscribe 엔티티
- **위치**: `client/src/main/kotlin/org/ghkdqhrbals/client/domain/subscribe/entity/Subscribe.kt`
- **기능**: 구독 주제 정의 (arXiv 카테고리, 키워드, 저자 등)
- **필드**:
  - `name`: 구독 주제 이름 (유니크)
  - `description`: 주제 설명
  - `subscribeType`: 구독 타입 (CATEGORY, KEYWORD, AUTHOR, CUSTOM)
  - `isActive`: 활성화 여부

#### UserSubscribe 엔티티
- **위치**: `client/src/main/kotlin/org/ghkdqhrbals/client/domain/subscribe/entity/UserSubscribe.kt`
- **기능**: 사용자-구독 관계 관리
- **필드**:
  - `user`: 사용자 정보 (ManyToOne)
  - `subscribe`: 구독 주제 (ManyToOne)
  - `notificationEnabled`: 알림 활성화 여부
  - `priority`: 우선순위 (1-5)
  - `subscribedAt`: 구독 시작 시간
  - `unsubscribedAt`: 구독 취소 시간

#### PaperSubscribe 엔티티 ⭐ NEW
- **위치**: `client/src/main/kotlin/org/ghkdqhrbals/client/domain/paper/entity/PaperSubscribe.kt`
- **기능**: 논문-구독주제 관계 및 AI 매칭 관리
- **필드**:
  - `paper`: 논문 정보 (ManyToOne)
  - `subscribe`: 구독 주제 (ManyToOne)
  - `matchScore`: AI 매칭 점수 (0.0 ~ 1.0)
  - `matchReason`: 매칭 이유
  - `matchedAt`: 매칭 시간

### 2. Repository

#### SubscribeRepository
- **위치**: `client/src/main/kotlin/org/ghkdqhrbals/client/domain/subscribe/repository/SubscribeRepository.kt`
- **주요 메서드**:
  - `findByName()`: 이름으로 구독 주제 조회
  - `existsByName()`: 이름 중복 체크
  - `findBySubscribeType()`: 타입별 구독 주제 조회
  - `findByIsActiveTrue()`: 활성 구독 주제 목록

#### UserSubscribeRepository
- **위치**: `client/src/main/kotlin/org/ghkdqhrbals/client/domain/subscribe/repository/UserSubscribeRepository.kt`
- **주요 메서드**:
  - `findActiveSubscriptionsByUserId()`: 사용자의 활성 구독 목록
  - `findByUserAndSubscribeId()`: 사용자-구독 관계 조회
  - `existsByUserIdAndSubscribeId()`: 구독 여부 확인

### 3. Service

#### SubscribeService
- **위치**: `client/src/main/kotlin/org/ghkdqhrbals/client/domain/subscribe/service/SubscribeService.kt`
- **주요 메서드**:
  - `addSubscription()`: 새로운 구독 주제 추가
  - `subscribeToTopic()`: 사용자 구독 추가
  - `unsubscribeFromTopic()`: 구독 취소
  - `getUserActiveSubscriptions()`: 사용자의 활성 구독 조회
  - `getAllActiveSubscribes()`: 모든 활성 구독 주제 조회
  - `getSubscribesByType()`: 타입별 구독 주제 조회

#### UserService
- **위치**: `client/src/main/kotlin/org/ghkdqhrbals/client/domain/user/service/UserService.kt`
- **주요 메서드**:
  - `getUserById()`: ID로 사용자 조회
  - `getUserByEmail()`: 이메일로 사용자 조회
  - `existsByEmail()`: 이메일 존재 여부 확인

#### PaperRecommendationService ⭐ NEW
- **위치**: `client/src/main/kotlin/org/ghkdqhrbals/client/domain/paper/service/PaperRecommendationService.kt`
- **주요 메서드**:
  - `matchPaperWithSubscribes()`: 논문과 구독 주제 간 매칭 생성
  - `getRecommendedPapersForUser()`: 사용자 맞춤 추천 논문 조회
  - `getPapersBySubscribe()`: 특정 구독 주제의 관련 논문 조회
  - `getSubscribesForPaper()`: 논문의 관련 구독 주제 조회
  - `autoMatchPaperWithAllSubscribes()`: 신규 논문 자동 매칭
  - `calculateMatchScore()`: AI 기반 매칭 점수 계산 (현재는 키워드 기반)

### 4. API Controllers

#### SubscribeApiController
- **위치**: `client/src/main/kotlin/org/ghkdqhrbals/client/domain/subscribe/api/SubscribeApiController.kt`
- **엔드포인트**:
  - `GET /api/subscriptions`: 전체 활성 구독 주제 목록
  - `GET /api/subscriptions/type/{type}`: 타입별 구독 주제 목록
  - `GET /api/subscriptions/user/{userId}`: 사용자의 구독 목록
  - `POST /api/subscriptions/user/{userId}/subscribe/{subscribeId}`: 구독 추가
  - `DELETE /api/subscriptions/user/{userId}/subscribe/{subscribeId}`: 구독 취소
  - `POST /api/subscriptions`: 새 구독 주제 생성

#### UserApiController
- **위치**: `client/src/main/kotlin/org/ghkdqhrbals/client/domain/user/api/UserApiController.kt`
- **엔드포인트**:
  - `GET /api/users/{userId}`: 사용자 정보 조회
  - `GET /api/users/me`: 현재 로그인 사용자 정보

#### PaperRecommendationApiController ⭐ NEW
- **위치**: `client/src/main/kotlin/org/ghkdqhrbals/client/domain/paper/api/PaperRecommendationApiController.kt`
- **엔드포인트**:
  - `GET /api/papers/recommendations/user/{userId}`: 사용자 맞춤 추천 논문
  - `GET /api/papers/subscribe/{subscribeId}`: 구독 주제별 관련 논문
  - `GET /api/papers/{paperId}/subscribes`: 논문의 관련 구독 주제
  - `POST /api/papers/{paperId}/auto-match`: 논문 자동 매칭

### 5. 웹 UI

#### 레이아웃 업데이트
- **파일**: `templates/layout/base.html`
- **추가 기능**:
  - 우측 상단에 사용자 프로필 드롭다운 메뉴
  - 프로필, 구독 관리, 설정, 로그아웃 링크
  - 네비게이션에 '구독 관리' 메뉴 추가

#### 프로필 페이지
- **파일**: `templates/profile.html`
- **경로**: `/profile`
- **기능**:
  - 사용자 기본 정보 표시 (이름, 이메일, 전화번호, 나이, 성별, 상태)
  - 현재 구독중인 주제 목록
  - 구독 관리 페이지로 이동 링크

#### 구독 관리 페이지
- **파일**: `templates/subscriptions.html`
- **경로**: `/subscriptions`
- **기능**:
  - **내 구독 탭**:
    - 현재 구독중인 주제 목록
    - 각 구독의 알림 상태, 우선순위, 구독일 표시
    - 알림 켜기/끄기 버튼
    - 구독 취소 버튼
  - **전체 주제 탭**:
    - 모든 사용 가능한 구독 주제 목록
    - 타입별 필터링 (CATEGORY, KEYWORD, AUTHOR, CUSTOM)
    - 구독하기 버튼

#### AI 추천 논문 페이지 ⭐ NEW
- **파일**: `templates/recommendations.html`
- **경로**: `/recommendations`
- **기능**:
  - 사용자 구독 기반 맞춤 논문 추천
  - 매칭 점수 및 관련도 표시 (높음/중간/낮음)
  - 관련도별 필터링
  - 추천 이유 표시
  - 논문 요약 모달
  - arXiv 원문 링크
  - 논문 저장 기능

### 6. WebController 업데이트
- **위치**: `client/src/main/kotlin/org/ghkdqhrbals/client/controller/WebController.kt`
- **추가 라우팅**:
  - `GET /profile`: 사용자 프로필 페이지
  - `GET /subscriptions`: 구독 관리 페이지
  - `GET /recommendations`: AI 추천 논문 페이지 ⭐ NEW

### 7. 초기 데이터 설정
- **DataInitializer**: `client/src/main/kotlin/org/ghkdqhrbals/client/config/DataInitializer.kt`
  - 애플리케이션 시작 시 기본 구독 주제 자동 생성
  - arXiv 카테고리 10개
  - 인기 키워드 10개
  - 유명 저자 5개

## 🎯 주요 기능

### 1. 사용자 정보 표시
- ✅ 우측 상단 네비게이션에 사용자 프로필 아이콘 및 드롭다운 메뉴
- ✅ 프로필 페이지에서 상세 정보 확인
- ✅ 구독 목록 및 현황 확인

### 2. 구독 관리
- ✅ 새로운 주제 구독하기
- ✅ 구독 취소하기
- ✅ 알림 설정 (준비중)
- ✅ 타입별 필터링
- ✅ 우선순위 설정

### 3. AI 기반 논문 추천 ⭐ NEW
- ✅ 사용자 구독 주제 기반 맞춤 논문 추천
- ✅ AI 매칭 점수 계산 (0.0 ~ 1.0)
- ✅ 관련도별 분류 (높음/중간/낮음)
- ✅ 추천 이유 자동 생성
- ✅ 관련도별 필터링
- ✅ 논문-구독주제 관계 추적

### 4. REST API
- ✅ 구독 CRUD 작업을 위한 RESTful API
- ✅ 사용자 정보 조회 API
- ✅ 논문 추천 API ⭐ NEW
- ✅ JSON 응답 형식

## 📝 TODO

### 인증/인가
- [ ] Spring Security 통합
- [ ] 현재 로그인 사용자 정보 가져오기 (SecurityContext)
- [ ] 세션 기반 사용자 식별

### 알림 기능
- [ ] 알림 켜기/끄기 API 구현
- [ ] 우선순위 변경 API
- [ ] 실시간 알림 시스템 연동

### UI 개선
- [ ] 구독 주제 검색 기능
- [ ] 페이지네이션
- [ ] 정렬 기능 (최신순, 이름순 등)
- [ ] 로딩 상태 표시

### 데이터베이스
- [ ] 초기 구독 주제 데이터 생성 (arXiv 카테고리 등)
- [ ] 마이그레이션 스크립트

## 🚀 사용 방법

### 1. 프로필 확인
```
http://localhost:8080/profile
```
- 사용자 기본 정보 및 구독 목록 확인

### 2. 구독 관리
```
http://localhost:8080/subscriptions
```
- 새로운 주제 구독하기
- 기존 구독 관리하기

### 3. API 사용 예시

#### 사용자 정보 조회
```bash
curl http://localhost:8080/api/users/me
```

#### 전체 구독 주제 목록
```bash
curl http://localhost:8080/api/subscriptions
```

#### 사용자의 구독 목록
```bash
curl http://localhost:8080/api/subscriptions/user/1
```

#### 새 구독 추가
```bash
curl -X POST http://localhost:8080/api/subscriptions/user/1/subscribe/1?priority=5
```

#### 구독 취소
```bash
curl -X DELETE http://localhost:8080/api/subscriptions/user/1/subscribe/1
```

## 🗄️ 데이터베이스 스키마

### subscribes 테이블
```sql
CREATE TABLE subscribes (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(255) NOT NULL UNIQUE,
    description TEXT,
    subscribe_type VARCHAR(50) NOT NULL,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);
```

### user_subscribes 테이블
```sql
CREATE TABLE user_subscribes (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_id BIGINT NOT NULL,
    subscribe_id BIGINT NOT NULL,
    notification_enabled BOOLEAN DEFAULT TRUE,
    priority INT DEFAULT 3,
    subscribed_at TIMESTAMP,
    unsubscribed_at TIMESTAMP,
    UNIQUE KEY unique_user_subscribe (user_id, subscribe_id),
    FOREIGN KEY (user_id) REFERENCES users(id),
    FOREIGN KEY (subscribe_id) REFERENCES subscribes(id)
);
```

## 🎨 UI 스크린샷 설명

### 네비게이션 바
- 좌측: NotiPaper 로고
- 중앙: Dashboard, My Papers, Search, 구독 관리, Settings 메뉴
- 우측: 사용자 프로필 아이콘 및 드롭다운

### 프로필 페이지
- 상단: 사용자 아바타, 이름, 이메일
- 중단: 전화번호, 가입일 등 상세 정보
- 하단: 현재 구독중인 주제 카드 목록

### 구독 관리 페이지
- 탭 1: 내 구독 - 구독중인 주제 관리
- 탭 2: 전체 주제 - 새로운 주제 탐색 및 구독

