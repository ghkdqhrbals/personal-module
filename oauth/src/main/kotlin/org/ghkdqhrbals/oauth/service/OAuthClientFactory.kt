package org.ghkdqhrbals.oauth.service

import org.ghkdqhrbals.oauth.oauth.OauthProviderRegistry
import org.springframework.stereotype.Component

@Component
class OAuthClientFactory(
    private val services: List<OAuthService>,
    private val registry: OauthProviderRegistry,
) {
    private val map = services.associateBy { it.kind }

    fun get(provider: String): OAuthService {
        val kind = OauthProviderKind.valueOf(provider)
        val svc = map[kind] ?: throw IllegalArgumentException("Unknown provider: $provider")
        validateConfig(kind)
        return svc
    }

    fun get(kind: OauthProviderKind): OAuthService {
        val svc = map[kind] ?: throw IllegalArgumentException("Unknown provider: $kind")
        validateConfig(kind)
        return svc
    }

    private fun validateConfig(kind: OauthProviderKind) {
        val cfg = registry.require(kind)
        val missing = mutableListOf<String>()
        if (cfg.clientId.isNullOrBlank()) missing += "clientId"
        if (cfg.redirectUri.isNullOrBlank()) missing += "redirectUri"
        if (cfg.codePath.isNullOrBlank()) missing += "codePath"
        if (cfg.tokenPath.isNullOrBlank()) missing += "tokenPath"
        if (cfg.userInfoPath.isNullOrBlank()) missing += "userInfoPath"
        if (missing.isNotEmpty()) {
            throw IllegalStateException("Missing OAuth config for $kind: ${missing.joinToString(", ")}")
        }
    }
}