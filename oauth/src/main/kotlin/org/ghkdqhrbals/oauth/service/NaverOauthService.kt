package org.ghkdqhrbals.oauth.service

import org.ghkdqhrbals.oauth.config.log
import org.ghkdqhrbals.oauth.oauth.OauthProviderRegistry
import org.ghkdqhrbals.oauth.utils.RandomUtils
import org.apache.coyote.BadRequestException
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import org.springframework.util.LinkedMultiValueMap
import org.springframework.util.MultiValueMap
import org.springframework.web.client.RestClient
import org.springframework.web.util.UriComponentsBuilder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

@Service
@ConditionalOnProperty(prefix = "oauth", name = ["enable-providers"], havingValue = "true", matchIfMissing = false)
internal class NaverOauthService(
    @Qualifier("restClient") private val client: RestClient,
    registry: OauthProviderRegistry,
) : OAuthService(
    provider = OauthProviderKind.NAVER,
    registry = registry
) {
    override fun buildAuthorizationUrl(state: String?): String {
        log().info("Initiating Naver OAuth authorization code request. codePath=${config.authorizationUri}")
        val scopesEncoded = URLEncoder.encode(config.scopes.joinToString(" "), StandardCharsets.UTF_8)
        val uri = UriComponentsBuilder
            .fromHttpUrl("${config.authorizationUri}")
            .queryParam("client_id", config.clientId)
            .queryParam("redirect_uri", config.redirectUri)
            .queryParam("response_type", "code")
            .queryParam("state", state ?: RandomUtils.generate(8))
            .queryParam("scope", scopesEncoded)
            .build(true)
            .toUriString()
        return uri
    }

    override fun tokenRevoke(accessToken: String) {
        val uri = UriComponentsBuilder
            .fromHttpUrl(config.tokenPath ?: error("tokenPath not configured for ${provider}"))
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
    override fun fetchUserInfo(accessToken: String): OAuthUserInfo {
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
            provider = provider,
            rawAttributes = response.filterKeys { it is String } as Map<String, Any>
        )
    }

    override fun exchangeToken(code: String): String? {
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