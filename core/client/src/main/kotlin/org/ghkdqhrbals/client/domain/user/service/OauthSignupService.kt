package org.ghkdqhrbals.client.domain.user.service

import org.ghkdqhrbals.infra.user.OauthProvider
import org.ghkdqhrbals.infra.user.UserEntity
import org.ghkdqhrbals.infra.user.OauthProviderRepository
import org.ghkdqhrbals.infra.user.UserRepository
import org.ghkdqhrbals.oauth.service.OAuthClientFactory
import org.ghkdqhrbals.oauth.service.OAuthService
import org.ghkdqhrbals.oauth.service.OauthProviderKind
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class OauthSignupService(
    private val userRepository: UserRepository,
    private val oauthProviderRepository: OauthProviderRepository,
    private val oAuthClientFactory: OAuthClientFactory,
) {

    data class SignupResult(
        val userId: Long,
        val isNew: Boolean,
        val provider: OauthProviderKind,
        val providerId: String,
        val accessToken: String,
    )

    fun getRedirectUrl(provider: OauthProviderKind, state: String? = null): String {
        val client = oAuthClientFactory.get(provider)
        val buildAuthorizationUrl = client.buildAuthorizationUrl(state)
        return buildAuthorizationUrl
    }

    @Transactional
    fun signupWithAuthorizationCode(provider: OauthProviderKind, code: String): SignupResult {
        val client = oAuthClientFactory.get(provider)
        val accessToken = client.exchangeToken(code) ?: throw IllegalStateException("Failed to exchange token")
        return internalSignup(provider, accessToken, client)
    }

    @Transactional
    fun signupWithAccessToken(provider: OauthProviderKind, accessToken: String): SignupResult {
        val client = oAuthClientFactory.get(provider)
        return internalSignup(provider, accessToken, client)
    }

    private fun internalSignup(provider: OauthProviderKind, accessToken: String, client: OAuthService): SignupResult {
        val userInfo = client.fetchUserInfo(accessToken)
        val providerId = userInfo.providerId

        // 기존 연동 여부 확인
        val existed = oauthProviderRepository.findByKindAndProviderId(provider, providerId)
        if (existed != null) {
            return SignupResult(
                userId = existed.userId,
                isNew = false,
                provider = provider,
                providerId = providerId,
                accessToken = accessToken,
            )
        }

        // 신규 유저 생성
        val user = UserEntity.defUser().apply {
            this.email = (userInfo.rawAttributes["email"] as? String)
            this.name = (userInfo.rawAttributes["name"] as? String) ?: this.name
        }
        val saved = userRepository.save(user)

        // OAuth provider 연결 저장
        val link = OauthProvider(
            userId = saved.id,
            providerId = providerId,
            kind = provider,
        )
        oauthProviderRepository.save(link)

        return SignupResult(
            userId = saved.id,
            isNew = true,
            provider = provider,
            providerId = providerId,
            accessToken = accessToken,
        )
    }
}

