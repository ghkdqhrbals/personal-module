# mod (OAuth + DateTime Utility Module)

이 프로젝트는 Spring Boot 애플리케이션에서 재사용할 수 있는 **OAuth 공급자 통합 모듈** 및 **OffsetDateTime / 날짜 유틸리티**를 제공합니다. 
별도 서비스에서 Maven/Gradle 의존성으로 추가하여 사용할 수 있도록 설계되었습니다.

---
## 주요 기능
- 표준화된 `OAuthService` 인터페이스 (Kakao / Naver / Google 지원 진행 중)
- 모듈 초기화 시 OAuth Provider 설정 자동 로딩 (`OauthProviderConfiguration`)
- 사용자 정의 Registry Builder (`OauthProviderRegistry`) 로 프로그래매틱 재정의 가능
- 표준 토큰 모델 `OAuthToken`, 표준 유저 정보 모델 `OAuthUserInfo`
- 원시 UserInfo Map → 매핑 전용 Mapper 구조 (예: `NaverUserInfoMapper`)
- 고해상도 타임스탬프 처리 (`toOffsetDateTimeWithNano` – ns/µs/ms/s 자동 인식)
- 다국적 포맷 파싱 및 Offset 변환 유틸 (`OffsetDateTimeUtils.kt`)
- Thymeleaf 기반 간단한 OAuth 데모 페이지 (`/oauth` → `templates/oauth.html`)
- GitHub Packages 퍼블리시 (MavenCentral 외 내부 배포)

---
## 설치 (Import)
GitHub Packages 를 사용하므로 personal access token(읽기 권한) 이 필요합니다.

### Gradle (Kotlin DSL)
```kotlin
repositories {
    mavenCentral()
    maven {
        url = uri("https://maven.pkg.github.com/ghkdqhrbals/personal-module")
        credentials {
            username = System.getenv("GITHUB_ACTOR") ?: "<github_username>"
            password = System.getenv("GITHUB_TOKEN") ?: "<personal_access_token>"
        }
    }
}

dependencies {
    implementation("com.ghkdqhrbals:tester:<version>")
}
```

### Maven
```xml
<repositories>
  <repository>
    <id>ghkdqhrbals-gpr</id>
    <url>https://maven.pkg.github.com/ghkdqhrbals/personal-module</url>
  </repository>
</repositories>

<dependencies>
  <dependency>
    <groupId>com.ghkdqhrbals</groupId>
    <artifactId>tester</artifactId>
    <version>1.0.1</version>
  </dependency>
</dependencies>
```

---
## 구성 개요
### 1. 자동 구성 (application.yml)
```yaml
oauth:
  providers:
    kakao:
      client-id: ${KAKAO_CLIENT_ID}
      client-secret: ${KAKAO_CLIENT_SECRET}
      redirect-uri: https://your.app/login/oauth2/code/kakao
      scopes: [account_email, profile_nickname]
    naver:
      client-id: ${NAVER_CLIENT_ID}
      client-secret: ${NAVER_CLIENT_SECRET}
      redirect-uri: https://your.app/login/oauth2/code/naver
    google:
      client-id: ${GOOGLE_CLIENT_ID}
      client-secret: ${GOOGLE_CLIENT_SECRET}
      redirect-uri: https://your.app/login/oauth2/code/google
      scopes: [openid, email, profile]
```
`OauthProviderConfiguration` 이 위 값을 읽어 registry 에 저장합니다.

### 2. 프로그래매틱 재정의 (선택)
```kotlin
val registry = OauthProviderRegistry.build {
    add(OauthProviderKind.KAKAO) {
        clientId = "..."
        clientSecret = "..."
        redirectUri = "https://your.app/login/oauth2/code/kakao"
        tokenPath = "https://kauth.kakao.com/oauth/token"
        codePath = "https://kauth.kakao.com/oauth/authorize"
        userInfoPath = "https://kapi.kakao.com/v2/user/me"
        scope("account_email")
    }
}
```
> Spring 자동 구성과 병행 시, 커스텀 Registry 주입 구조를 직접 확장해야 합니다.

---
## OAuthService 사용 예
```kotlin
@RestController
class AuthController(
    private val kakao: KakaoOauthService,
) {
    @GetMapping("/login/kakao")
    fun kakaoLogin(response: HttpServletResponse) {
        response.sendRedirect(kakao.authorizationUrl())
    }

    @GetMapping("/login/oauth2/code/kakao")
    fun kakaoCallback(@RequestParam code: String): OAuthUserInfo {
        val token = kakao.exchange(code)
        return kakao.userInfo(token.accessToken)
    }
}
```

### 표준 메서드 목록
| 메서드 | 설명 |
|--------|------|
| `authorizationUrl(state?, scopesOverride?)` | 인가 코드 요청 URL 생성 |
| `exchange(code)` | 인가 코드 → `OAuthToken` 교환 |
| `fetchRawUserInfo(accessToken)` | Provider 원시 Map |
| `mapUserInfo(raw)` | 표준 `OAuthUserInfo` 변환 |
| `userInfo(accessToken)` | 위 두 단계를 합친 편의 메서드 |
| `revoke(accessToken)` | 토큰 폐기/연결해제 (지원되는 경우) |

---
## UserInfo 매퍼 확장
Naver 예제: `NaverUserInfoMapper`
```kotlin
@Component
class CustomKakaoMapper { /* Kakao raw -> 표준 DTO 변환 직접 구현 */ }
```
서비스 구현에서 `mapUserInfo` 오버라이드 또는 별도 Mapper 주입 구조로 확장 가능합니다.

---
## DateTime 유틸 주요 포인트
```kotlin
val millis = 1_700_000_000_123L.toOffsetDateTimeWithNano()
val micros = 1_700_000_000_123_456L.toOffsetDateTimeWithNano()
val nanos  = 1_700_000_000_123_456_789L.toOffsetDateTimeWithNano()
```
규칙:
- 1e18 이상: 나노초
- 1e12 이상: 마이크로초
- 1e9 이상: 밀리초
- 그 외: 초 단위

패턴 유연 파싱 (ISO / 다양한 fraction / yyyyMMdd 등) → `String.toOffsetDateTime()` 활용.

---
## 데모 페이지
Thymeleaf 템플릿: `templates/oauth.html`
- 경로: `/oauth`
- 로그인 / 토큰 revoke / UserInfo 확인 버튼 제공

---
## 테스트 & 커버리지
```bash
./gradlew test
./gradlew jacocoTestReport
```
리포트: `build/reports/jacoco/test/html/index.html`

---
## 퍼블리시 (수동 워크플로우)
GitHub Actions Manual Publish 사용 (major / minor / patch 인자 선택) → 태그 `vX.Y.Z` 생성 및 GitHub Packages 업로드.
로컬 수동 배포:
```bash
./gradlew publish -PartifactVersion=1.0.2
```

---
## 보안 주의
- client-secret, private key 는 절대 평문 커밋 금지
- 환경 변수 또는 Secret Manager (예: AWS Secrets Manager) 사용 권장
- state 파라미터(anti-CSRF) 실제 서비스 적용 필수

---
## 향후 로드맵
- Apple 로그인 지원 (JWT 생성 로직 포함)
- 동적 Provider 갱신 API (Hot Reload)
- Refresh Token Rotation 헬퍼
- 더 많은 시간대/캘린더 변환 유틸

---
## 기여
PR / Issue 환영합니다.
1. Fork
2. 기능 브랜치 생성
3. 테스트 추가 및 통과 확인
4. PR 생성 (변경 요약 포함)

---
## 라이선스
(라이선스 명시 예정)

---
## 문의
- GitHub: [ghkdqhrbals](https://github.com/ghkdqhrbals)

> by ghkdqhrbals

