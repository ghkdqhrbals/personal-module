package org.ghkdqhrbals.oauth.service

data class OAuthUserInfo(
    val provider: OauthProviderKind,
    val providerId: String,
    val rawAttributes: Map<String, Any>
)