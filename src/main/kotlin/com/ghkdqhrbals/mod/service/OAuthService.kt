package com.ghkdqhrbals.mod.service

import jakarta.servlet.http.HttpServletResponse

abstract class OAuthService(
    val kind: OauthProviderKind
) {
    abstract fun buildAuthorizationUrl(state: String? = null): String
    abstract fun providerKind(): OauthProviderKind
    abstract fun fetchUserInfo(accessToken: String): OAuthUserInfo
    abstract fun exchangeToken(code: String): String?
    abstract fun revoke(accessToken: String)
}