package org.ghkdqhrbals.oauth.service

import org.ghkdqhrbals.oauth.oauth.OauthProviderRegistry
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.DisplayName
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test

class OAuthClientFactoryTest {

    private fun registryWithAll(kind: OauthProviderKind) =
        OauthProviderRegistry.build {
            add(kind) {
                clientId = "client-id"
                clientSecret = "client-secret"
                redirectUri = "https://example.com/callback"
                authorizationUri = "https://auth.example.com/authorize"
                tokenPath = "https://auth.example.com/token"
                userInfoPath = "https://api.example.com/userinfo"
                scope("openid", "email", "profile")
            }
        }

    private fun registryMissing(kind: OauthProviderKind, missing: String) =
        OauthProviderRegistry.build {
            add(kind) {
                when (missing) {
                    "clientId" -> clientId = null
                    else -> clientId = "client-id"
                }
                clientSecret = "client-secret"
                when (missing) {
                    "redirectUri" -> redirectUri = null
                    else -> redirectUri = "https://example.com/callback"
                }
                when (missing) {
                    "codePath" -> authorizationUri = null
                    else -> authorizationUri = "https://auth.example.com/authorize"
                }
                when (missing) {
                    "tokenPath" -> tokenPath = null
                    else -> tokenPath = "https://auth.example.com/token"
                }
                when (missing) {
                    "userInfoPath" -> userInfoPath = null
                    else -> userInfoPath = "https://api.example.com/userinfo"
                }
                scope("openid")
            }
        }

    private class StubService(kind: OauthProviderKind, registry: OauthProviderRegistry) : OAuthService(kind, registry) {
        override fun buildAuthorizationUrl(state: String?): String = "stub"
        override fun fetchUserInfo(accessToken: String): OAuthUserInfo =
            OAuthUserInfo(provider = provider, providerId = "id", rawAttributes = emptyMap())

        override fun exchangeToken(code: String): String? = "token"
        override fun tokenRevoke(accessToken: String) {}
    }

    @Nested
    @DisplayName("get(provider) 성공")
    inner class Success {
        @Test
        @DisplayName("정상 설정이면 서비스 반환")
        fun returns_service_when_config_complete() {
            val kind = OauthProviderKind.KAKAO
            val registry = registryWithAll(kind)
            val factory = OAuthClientFactory(listOf(StubService(kind, registry)), registry)
            val svc = assertDoesNotThrow<OAuthService> { factory.get(kind.name) }
            assertSame(kind, svc.provider)
        }
    }

    @Nested
    @DisplayName("get(provider) 실패 - 설정 누락")
    inner class MissingConfig {
        @Test
        @DisplayName("clientId 누락 시 예외")
        fun missing_client_id() {
            val kind = OauthProviderKind.GOOGLE
            val registry = registryMissing(kind, "clientId")
            assertThrows(IllegalStateException::class.java) {
                StubService(kind, registry)
            }
        }

        @Nested
        @DisplayName("get(provider) 실패 - 기타")
        inner class OtherFailures {
            @Test
            @DisplayName("정의되지 않은 provider 문자열이면 예외")
            fun unknown_provider_string() {
                val kind = OauthProviderKind.KAKAO
                val registry = registryWithAll(kind)
                val factory = OAuthClientFactory(listOf(StubService(kind, registry)), registry)
                assertThrows(IllegalArgumentException::class.java) { factory.get("UNKNOWN") }
            }

            @Test
            @DisplayName("설정은 있으나 해당 서비스 Bean 없음 → 예외")
            fun missing_service_instance() {
                val kind = OauthProviderKind.GOOGLE
                val factory = OAuthClientFactory(emptyList(), registryWithAll(kind))
                assertThrows(IllegalArgumentException::class.java) { factory.get(kind.name) }
            }
        }
    }
}
