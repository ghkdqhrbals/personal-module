package org.ghkdqhrbals.client.domain.user.service

import org.ghkdqhrbals.oauth.service.OAuthClientFactory
import org.springframework.stereotype.Service

@Service
class OAuthService(
    private val oAuthClientFactory: OAuthClientFactory,
) {
    fun getAuthorizationUrl(provider: String, state: String = "state"): String {
        val kind = provider.trim().uppercase()
        return oAuthClientFactory.get(kind).buildAuthorizationUrl(state)
    }
}