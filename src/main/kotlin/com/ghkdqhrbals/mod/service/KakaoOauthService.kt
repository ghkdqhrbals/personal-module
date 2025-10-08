package com.ghkdqhrbals.mod.service

import com.ghkdqhrbals.mod.config.log
import com.ghkdqhrbals.mod.oauth.OauthProviderRegistry
import jakarta.servlet.http.HttpServletResponse
import org.apache.coyote.BadRequestException
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.util.LinkedMultiValueMap
import org.springframework.util.MultiValueMap
import org.springframework.web.client.RestClient
import org.springframework.web.util.UriComponentsBuilder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@Service
class KakaoOauthService(
    private val client: RestClient,
    private val registry: OauthProviderRegistry,
): OAuthService {
    final override fun providerKind() = OauthProviderKind.KAKAO
    private val config = registry.require(providerKind())

    override fun requestAuthCode(response: HttpServletResponse) {
        log().info(config.codePath)
        val scopesEncoded = URLEncoder.encode(config.scopes.joinToString(" "), StandardCharsets.UTF_8)
        val uri = UriComponentsBuilder
            .fromHttpUrl("${config.codePath}")
            .queryParam("client_id", config.clientId)
            .queryParam("redirect_uri", config.redirectUri)
            .queryParam("response_type", "code")
            .apply { if (config.scopes.isNotEmpty()) queryParam("scope", scopesEncoded) }
            .build(true)
            .toUriString()
        response.sendRedirect(uri)
    }

    override fun getUserInfo(accessToken: String): OAuthUserInfo {
        val url = config.userInfoPath ?: error("userInfoPath not configured for KAKAO")
        val response = client.get()
            .uri(url)
            .header("Authorization", "Bearer $accessToken")
            .retrieve()
            .body(Map::class.java) as Map<*, *>
        return OAuthUserInfo(
            providerId = response["id"].toString(),
            provider = providerKind(),
            rawAttributes = response.filterKeys { it is String } as Map<String, Any>
        )
    }

    override fun getAccessToken(code: String): String? {
        val tokenPath = config.tokenPath ?: error("tokenPath not configured for KAKAO")
        val clientId = config.clientId ?: error("clientId not configured for KAKAO")
        val form: MultiValueMap<String, String> = LinkedMultiValueMap<String, String>().apply {
            add("grant_type", "authorization_code")
            add("client_id", clientId)
            config.clientSecret?.takeIf { it.isNotBlank() }?.let { add("client_secret", it) }
            config.redirectUri?.takeIf { it.isNotBlank() }?.let { add("redirect_uri", it) }
            add("code", code)
        }
        val tokenResponse = client.post()
            .uri(tokenPath)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(form)
            .retrieve()
            .body(Map::class.java)
        return tokenResponse?.get("access_token")?.toString()?: throw BadRequestException("Failed to get access token")
    }

    override fun disconnect(accessToken: String) {
        val url = "https://kapi.kakao.com/v1/user/unlink"
        client.post()
            .uri(url)
            .headers {
                it.add("Authorization", "Bearer $accessToken")
            }
            .retrieve()
            .body(String::class.java)
    }
}