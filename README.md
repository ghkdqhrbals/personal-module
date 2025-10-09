# mod (OAuth + DateTime Utility Module)

This module provides an **OAuth provider integration module** that can be reused in Spring Boot applications (supports Kakao / Naver / Google).

# Why I built this?

Yes. Spring security already supports OAuth2.0 Client. And it's convenient to use. 

**But it's hard to customize and has many unnecessary dependencies!** Also it does not support token revocation.

Besides, not only OAuth2.0 Client, but also DateTime utility is included. That means I built this module to use it in various projects, and I'm releasing it as an open-source module. 

# Key features

* OAuth2.0(Google, Naver, Kakao)
  * revoke token
  * get user info
  * exchange code to token
  * generate authorization url
* OffsetDateTime utilities
  * convert epoch millis/micros/nanos to OffsetDateTime
  * parse various datetime strings to OffsetDateTime

# OAuth2.0 Quick Start

Since this module uses github packages, you need to provide a read token :)

### 1. Gradle(Kotlin DSL)

```kotlin
repositories {
    mavenCentral()
    maven {
        url = uri("https://maven.pkg.github.com/ghkdqhrbals/personal-module")
        credentials {
            username = "<github_username>"
            password = "<personal_access_token>"
        }
    }
}

dependencies {
    implementation("com.github.ghkdqhrbals:personal-module:0.2.0")
}
```

### 2. configure application.yml

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


## Usage

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
    // etc.
}
```

## Demo

run this module directly and acess [http://localhost:8080/oauth2](http://localhost:8080/oauth2)

> For your safety, please use secure vault service (ex. AWS Secrets Manager, HashiCorp Vault, Doppler, etc.)

## After v0.2.0 we will ...
- support Apple OAuth
- Refresh Token Rotation


