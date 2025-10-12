package org.ghkdqhrbals.oauth.service

import org.ghkdqhrbals.oauth.oauth.OauthProviderRegistry
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
): OAuthService(OauthProviderKind.KAKAO) {
    override fun buildAuthorizationUrl(state: String?): String {
        val scopesEncoded = URLEncoder.encode(config.scopes.joinToString(" "), StandardCharsets.UTF_8)
        val uri = UriComponentsBuilder
            .fromHttpUrl("${config.codePath}")
            .queryParam("client_id", config.clientId)
            .queryParam("redirect_uri", config.redirectUri)
            .queryParam("response_type", "code")
            .apply { if (config.scopes.isNotEmpty()) queryParam("scope", scopesEncoded) }
            .build(true)
            .toUriString()
        return uri
    }

    final override fun providerKind() = OauthProviderKind.KAKAO
    private val config = registry.require(providerKind())

    override fun fetchUserInfo(accessToken: String): OAuthUserInfo {
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

    override fun exchangeToken(code: String): String? {
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

    override fun revoke(accessToken: String) {
        val url = "https://kapi.kakao.com/v1/user/unlink"
        client.post()
            .uri(url)
            .headers {
                it.add("Authorization", "Bearer $accessToken")
            }
            .retrieve()
//            .onStatus({ !it.is2xxSuccessful }) { _, clientResponse->
//                // clientResponse.statusCode 에 맞는 에러 throw
//            }
            .body(String::class.java)
    }
}