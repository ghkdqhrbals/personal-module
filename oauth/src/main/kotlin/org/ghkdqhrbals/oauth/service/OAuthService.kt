package org.ghkdqhrbals.oauth.service

import org.ghkdqhrbals.oauth.oauth.OAuthConfig
import org.ghkdqhrbals.oauth.oauth.OauthProviderRegistry

abstract class OAuthService(
    val provider: OauthProviderKind,
    val registry: OauthProviderRegistry,
) {
    fun revoke(accessToken: String) {
        config.tokenRevocationUri ?: throw RuntimeException("token revoke uri property is missing")
        tokenRevoke(accessToken)
    }
    fun exchange(code: String) {
        config.redirectUri ?: throw RuntimeException()
        config.tokenPath ?: throw RuntimeException()
        exchangeToken(code)
    }
    fun auth() {
        config.authorizationUri ?: throw RuntimeException()
    }

    private fun loadConfig(): OAuthConfig {
        val config = registry.get(provider)
        config?: throw Exception("$provider configuration is not set.")
        return config
    }

    protected val config = this.loadConfig().apply {
        clientId ?: throw IllegalStateException("$provider clientId is missing")
    }
    abstract fun buildAuthorizationUrl(state: String? = null): String
    abstract fun fetchUserInfo(accessToken: String): OAuthUserInfo
    abstract fun exchangeToken(code: String): String?
    abstract fun tokenRevoke(accessToken: String)
}