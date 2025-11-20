package org.ghkdqhrbals.client.domain.user.service

import io.mockk.*
import org.ghkdqhrbals.client.domain.user.entity.OauthProvider
import org.ghkdqhrbals.client.domain.user.entity.UserEntity
import org.ghkdqhrbals.client.domain.user.repository.OauthProviderRepository
import org.ghkdqhrbals.client.domain.user.repository.UserRepository
import org.ghkdqhrbals.oauth.service.OAuthClientFactory
import org.ghkdqhrbals.oauth.service.OAuthService
import org.ghkdqhrbals.oauth.service.OAuthUserInfo
import org.ghkdqhrbals.oauth.service.OauthProviderKind
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class OauthSignupServiceTest {

    private lateinit var userRepository: UserRepository
    private lateinit var oauthProviderRepository: OauthProviderRepository
    private lateinit var clientFactory: OAuthClientFactory
    private lateinit var oauthService: OAuthService

    private lateinit var service: OauthSignupService

    @BeforeEach
    fun setup() {
        userRepository = mockk()
        oauthProviderRepository = mockk()
        clientFactory = mockk()
        oauthService = mockk()
        service = OauthSignupService(userRepository, oauthProviderRepository, clientFactory)
    }

    @AfterEach
    fun tearDown() { clearAllMocks() }

    @Test
    fun `인가코드로 신규 회원가입을 수행한다`() {
        val code = "auth-code"
        val accessToken = "access-token"
        val provider = OauthProviderKind.GOOGLE
        val userInfo = OAuthUserInfo(provider, providerId = "google-123", rawAttributes = mapOf("email" to "a@b.c", "name" to "Alice"))

        every { clientFactory.get(provider) } returns oauthService
        every { oauthService.exchangeToken(code) } returns accessToken
        every { oauthService.fetchUserInfo(accessToken) } returns userInfo
        every { oauthProviderRepository.findByKindAndProviderId(provider, "google-123") } returns null

        val savedUser = UserEntity.defUser().apply { id = 42; email = "a@b.c"; name = "Alice" }
        every { userRepository.save(any<UserEntity>()) } returns savedUser
        every { oauthProviderRepository.save(any()) } returns OauthProvider(userId = 42, providerId = "google-123", kind = provider)

        val res = service.signupWithAuthorizationCode(provider, code)

        assertTrue(res.isNew)
        assertEquals(42, res.userId)
        assertEquals(provider, res.provider)
        assertEquals("google-123", res.providerId)
        assertEquals(accessToken, res.accessToken)

        verify(exactly = 1) { userRepository.save(match { it.email == "a@b.c" && it.name == "Alice" }) }
        verify(exactly = 1) { oauthProviderRepository.save(match { it.userId == 42L && it.providerId == "google-123" }) }
    }

    @Test
    fun `이미 연동된 계정이면 기존 유저 정보를 반환한다`() {
        val provider = OauthProviderKind.KAKAO
        val accessToken = "token"
        val userInfo = OAuthUserInfo(provider, providerId = "kakao-999", rawAttributes = emptyMap())

        every { clientFactory.get(provider) } returns oauthService
        every { oauthService.fetchUserInfo(accessToken) } returns userInfo
        every { oauthProviderRepository.findByKindAndProviderId(provider, "kakao-999") } returns OauthProvider(userId = 7, providerId = "kakao-999", kind = provider)

        val res = service.signupWithAccessToken(provider, accessToken)

        assertFalse(res.isNew)
        assertEquals(7, res.userId)
        assertEquals("kakao-999", res.providerId)

        verify(exactly = 0) { userRepository.save(any<UserEntity>()) }
        verify(exactly = 0) { oauthProviderRepository.save(any()) }
    }

    @Test
    fun `접근토큰으로 신규 회원가입을 수행하고 이름과 이메일을 매핑한다`() {
        val provider = OauthProviderKind.NAVER
        val accessToken = "naver-token"
        val userInfo = OAuthUserInfo(provider, providerId = "naver-1", rawAttributes = mapOf("email" to "x@y.z", "name" to "Bob"))

        every { clientFactory.get(provider) } returns oauthService
        every { oauthService.fetchUserInfo(accessToken) } returns userInfo
        every { oauthProviderRepository.findByKindAndProviderId(provider, "naver-1") } returns null

        val savedUser = UserEntity.defUser().apply { id = 100; email = "x@y.z"; name = "Bob" }
        every { userRepository.save(any<UserEntity>()) } returns savedUser
        every { oauthProviderRepository.save(any()) } returns OauthProvider(userId = 100, providerId = "naver-1", kind = provider)

        val res = service.signupWithAccessToken(provider, accessToken)

        assertTrue(res.isNew)
        assertEquals(100, res.userId)
        assertEquals("naver-1", res.providerId)
        verify { userRepository.save(match { it.email == "x@y.z" && it.name == "Bob" }) }
    }
}

