package org.ghkdqhrbals.oauth.oauth

import org.ghkdqhrbals.oauth.service.OauthProviderKind

// OAuth 설정 스냅샷 (불변)
data class OAuthConfig(
    val provider: OauthProviderKind,
    var clientSecret: String? = null,
    var redirectUri: String? = null,
    var tokenPath: String? = null,
    var tokenRevocationUri: String? = null,
    var authorizationUri: String? = null,
    var userInfoPath: String? = null,
    var scopes: MutableSet<String> = mutableSetOf(),
    var clientId: String? = null,
) {
    fun scope(vararg s: String) { scopes.addAll(s) }
    fun addScope(vararg s: String) { scopes.addAll(s) }
    fun snapshot() = this.copy()
}
