package com.ghkdqhrbals.mod.service

data class OAuthUserInfo(
    val provider: OauthProviderKind,
    val providerId: String,
    val rawAttributes: Map<String, Any>
)