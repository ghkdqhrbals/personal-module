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
class NaverOauthService(
    private val client: RestClient,
    private val registry: OauthProviderRegistry,
) : OAuthService {
    private val config = registry.require(providerKind())
    final override fun providerKind() = OauthProviderKind.NAVER
    override fun disconnect(accessToken: String) {
        val uri = UriComponentsBuilder
            .fromHttpUrl(config.tokenPath ?: error("tokenPath not configured for ${providerKind()}"))
            .queryParam("client_secret", config.clientSecret)
            .queryParam("client_id", config.clientId)
            .queryParam("grant_type", "delete")
            .queryParam("access_token", accessToken)
            .build(true)
            .toUriString()

        val body = client.get()
            .uri(uri)
            .retrieve()
            .body(Map::class.java)
    }

    override fun requestAuthCode(response: HttpServletResponse) {
        log().info(" aas dfasdf saf ${config.codePath}")
        val scopesEncoded = URLEncoder.encode(config.scopes.joinToString(" "), StandardCharsets.UTF_8)
        val uri = UriComponentsBuilder
            .fromHttpUrl("${config.codePath}")
            .queryParam("client_id", config.clientId)
            .queryParam("redirect_uri", config.redirectUri)
            .queryParam("response_type", "code")
            .queryParam("state", RandomUtils.generate(8))
            .build(true)
            .toUriString()
        response.sendRedirect(uri)
    }

    override fun getUserInfo(accessToken: String): OAuthUserInfo {
        val url = config.userInfoPath ?: error("userInfoPath not configured for NAVER")
        val response = client.get()
            .uri(url)
            .header("Authorization", "Bearer $accessToken")
            .retrieve()
            .body(Map::class.java) as Map<*, *>
        val resp = response["response"] as? Map<*, *>
        val id = resp?.get("id").toString()
        return OAuthUserInfo(
            providerId = id,
            provider = providerKind(),
            rawAttributes = response.filterKeys { it is String } as Map<String, Any>
        )
    }

    override fun getAccessToken(code: String): String? {
        val tokenPath = config.tokenPath ?: error("tokenPath not configured for NAVER")
        val clientId = config.clientId ?: error("clientId not configured for NAVER")
        val clientSecret = config.clientSecret ?: error("clientSecret not configured for NAVER")
        val form: MultiValueMap<String, String> = LinkedMultiValueMap<String, String>().apply {
            add("grant_type", "authorization_code")
            add("client_id", clientId)
            add("code", code)
            add("state", RandomUtils.generate(8))
            add("client_secret", clientSecret)
        }
        val tokenResponse = client.post()
            .uri(tokenPath)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(form)
            .retrieve()
            .body(Map::class.java)
        return tokenResponse?.get("access_token")?.toString() ?: throw BadRequestException("Failed to get access token")
    }
}