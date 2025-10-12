# mod (OAuth + DateTime Utilities)

> 개인적으로 여기저기 꺼내쓰기 위한(+ 사내 적용 전 실험목적) 패키지입니다. 

Reusable OAuth integration (Kakao/Naver/Google) and DateTime helpers for Spring Boot.

## Features
- OAuth: auth URL, code→token, user info, revoke, pluggable providers
- Configurable per-provider (redirectUri, scopes, extra params)
- DTO mapping and extensible handlers (success/failure/logout)
- Works with/without Spring Security
- Time: convert epoch(ms/µs/ns) and parse common date-time strings

## Getting started (Gradle - Kotlin DSL)
```kotlin
repositories {
    mavenCentral()
    maven { url = uri("https://jitpack.io") }
}

dependencies {
    implementation("com.github.ghkdqhrbals:personal-module:{TAG}:oauth")
    implementation("com.github.ghkdqhrbals:personal-module:{TAG}:time")
}

```

## Minimal usage example

```kotlin
@RestController
class LoginController(private val kakao: KakaoOauthService) {
    @GetMapping("/login/kakao")
    fun kakaoLogin(resp: HttpServletResponse) {
        resp.sendRedirect(kakao.authorizationUrl())
    }
    @GetMapping("/login/oauth2/code/kakao")
    fun kakaoCallback(@RequestParam code: String): Map<String, Any?> {
        val token = kakao.exchange(code)
        return kakao.userInfo(token.accessToken)
    }
}
```

## Demo
Run and open http://localhost:8080/oauth2

> Store secrets safely (e.g., AWS Secrets Manager, Vault, Doppler).
