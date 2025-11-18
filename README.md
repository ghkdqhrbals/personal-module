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

## Local Development

### Prerequisites
- Java 17+
- Redis (running on localhost:6379)
- Ollama (https://ollama.ai)

### Quick Start (All Services)

```bash
# Start Redis (if not running)
brew services start redis
# or: redis-server

# Start everything (Ollama + Spring Boot Client)
./start-all.sh
```

This script will:
1. ✅ Check prerequisites (Java, Ollama, Redis)
2. 🚀 Start Ollama server and load gemma3 model
3. 🏗️ Build Spring Boot client module
4. 🚀 Start Spring Boot application

### Individual Scripts

```bash
# Start only Ollama
./start-ollama.sh

# Start only Spring Boot Client
./start-client.sh
```

### Environment Variables

```bash
# Ollama configuration (optional)
export OLLAMA_MODEL=gemma3        # default: gemma3
export OLLAMA_PORT=11434          # default: 11434

# Spring profiles
export SPRING_PROFILES_ACTIVE=local
```

## Demo
Run and open http://localhost:8080/oauth2

> Store secrets safely (e.g., AWS Secrets Manager, Vault, Doppler).
