package com.ghkdqhrbals.mod.service

import jakarta.servlet.http.HttpServletResponse

interface OAuthService {
    fun providerKind(): OauthProviderKind
    fun getUserInfo(accessToken: String): OAuthUserInfo
    fun getAccessToken(code: String): String?
    fun disconnect(accessToken: String)
    fun requestAuthCode(response: HttpServletResponse)
}