package com.ghkdqhrbals.mod.service

import com.ghkdqhrbals.mod.config.log
import com.ghkdqhrbals.mod.oauth.OauthProviderRegistry
import com.ghkdqhrbals.mod.utils.RandomUtils
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
class GoogleOauthService(
    private val client: RestClient,
    private val registry: OauthProviderRegistry,
): OAuthService {
    final override fun providerKind() = OauthProviderKind.GOOGLE
    private val config = registry.require(providerKind())
    override fun disconnect(accessToken: String) {
        val p = "https://oauth2.googleapis.com/revoke"
        val uri = UriComponentsBuilder
            .fromHttpUrl(p)
            .queryParam("token", accessToken)
            .build(true)
            .toUriString()

        client.post()
            .uri(uri)
            .header("Content-Type", MediaType.APPLICATION_FORM_URLENCODED_VALUE)
            .retrieve()
            .body(Map::class.java)
    }

    override fun requestAuthCode(response: HttpServletResponse) {
        val cfg = registry.require(providerKind())
        val scopesEncoded = URLEncoder.encode(cfg.scopes.joinToString(" "), StandardCharsets.UTF_8)
        val uri = UriComponentsBuilder
            .fromHttpUrl("${cfg.codePath}")
            .queryParam("client_id", cfg.clientId)
            .queryParam("redirect_uri", cfg.redirectUri)
            .queryParam("response_type", "code")
            .queryParam("state", RandomUtils.generate(8))
            .queryParam("scope", scopesEncoded)
            .build(true)
            .toUriString()

        response.sendRedirect(uri)
    }

    override fun getUserInfo(accessToken: String): OAuthUserInfo {
        val url = config.userInfoPath ?: error("userInfoPath not configured for Google")
        val response = client.get()
            .uri(url)
            .header("Authorization", "Bearer $accessToken")
            .retrieve()
            .body(Map::class.java) as Map<*, *>
        return OAuthUserInfo(
            providerId = response["sub"].toString(),
            provider = providerKind(),
            rawAttributes = response.filterKeys { it is String } as Map<String, Any>
        )
    }

    override fun getAccessToken(code: String): String? {
        val tokenPath = config.tokenPath ?: error("tokenPath not configured for Google")
        val clientId = config.clientId ?: error("clientId not configured for Google")
        val clientSecret = config.clientSecret ?: error("clientSecret not configured for Google")
        val form: MultiValueMap<String, String> = LinkedMultiValueMap<String, String>().apply {
            add("grant_type", "authorization_code")
            add("client_id", clientId)
            add("code", code)
            add("state", RandomUtils.generate(8))
            add("client_secret", clientSecret)
            add("redirect_uri", config.redirectUri ?: error("redirectUri not configured for Google"))
        }
        val tokenResponse = client.post()
            .uri(tokenPath)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(form)
            .retrieve()
            .body(Map::class.java)

        return tokenResponse?.get("access_token")?.toString()
            ?: throw BadRequestException("Failed to get access token")
    }
}