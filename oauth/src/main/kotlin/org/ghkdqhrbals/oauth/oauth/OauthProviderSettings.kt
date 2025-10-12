package org.ghkdqhrbals.oauth.oauth

import org.ghkdqhrbals.oauth.service.OauthProviderKind

// OAuth 설정 스냅샷 (불변)
data class OAuthConfig(
    val kind: OauthProviderKind,
    val clientSecret: String?,
    val redirectUri: String?,
    val tokenPath: String?,
    val codePath: String?,
    val userInfoPath: String?,
    val scopes: List<String>,
    val clientId: String?,
    val extra: Map<String, String>,
)

// 내부 빌더 (AutoConfiguration 에서만 사용)
class MutableOAuthConfig internal constructor(private val kind: OauthProviderKind) {
    var clientSecret: String? = null
    var redirectUri: String? = null
    var codePath: String? = null
    var tokenPath: String? = null
    var userInfoPath: String? = null
    var clientId: String? = null
    var scopes: MutableList<String> = mutableListOf()
    private val extraMap: MutableMap<String, String> = mutableMapOf()

    fun scope(vararg s: String) { scopes.addAll(s) }
    fun putExtra(key: String, value: String) { extraMap[key] = value }

    fun snapshot(): OAuthConfig = OAuthConfig(
        kind = kind,
        clientSecret = clientSecret,
        redirectUri = redirectUri,
        tokenPath = tokenPath,
        codePath = codePath,
        userInfoPath = userInfoPath,
        scopes = scopes.toList(),
        extra = extraMap.toMap(),
        clientId = clientId,
    )
}
