package org.ghkdqhrbals.oauth.config

import org.ghkdqhrbals.oauth.service.OauthProviderKind
import org.ghkdqhrbals.oauth.oauth.OauthProviderRegistry
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(OauthProvidersProperties::class)
class OauthProviderAutoConfiguration {

    @Bean
    fun oauthProviderRegistry(props: OauthProvidersProperties): OauthProviderRegistry {
        log().debug("Configuring OAuth clients")
        val build = OauthProviderRegistry.build {
            // default
            add(OauthProviderKind.KAKAO) {
                tokenPath = "https://kauth.kakao.com/oauth/token"
                tokenRevocationUri = "https://kauth.kakao.com/oauth/token"
                authorizationUri = "https://kauth.kakao.com/oauth/authorize"
                userInfoPath = "https://kapi.kakao.com/v2/user/me"
            }
            add(OauthProviderKind.NAVER) {
                tokenPath = "https://nid.naver.com/oauth2.0/token"
                tokenRevocationUri = "https://nid.naver.com/oauth2.0/token"
                authorizationUri = "https://nid.naver.com/oauth2.0/authorize"
                userInfoPath = "https://openapi.naver.com/v1/nid/me"
            }
            add(OauthProviderKind.GOOGLE) {
                tokenRevocationUri = "https://oauth2.googleapis.com/revoke"
                tokenPath = "https://oauth2.googleapis.com/token"
                authorizationUri = "https://accounts.google.com/o/oauth2/v2/auth"
                userInfoPath = "https://www.googleapis.com/oauth2/v3/userinfo"
                scope("openid", "email")
            }

            // properties override
            props.providers.forEach { (name, cfg) ->
                val kind = OauthProviderKind.valueOf(name.uppercase())
                add(kind) {
                    if (cfg.clientId.isNotEmpty()) clientId = cfg.clientId
                    if (cfg.clientSecret.isNotEmpty()) clientSecret = cfg.clientSecret
                    if (cfg.redirectUri.isNotEmpty()) redirectUri = cfg.redirectUri
                    if (!cfg.tokenPath.isNullOrEmpty()) tokenPath = cfg.tokenPath
                    if (!cfg.codePath.isNullOrEmpty()) authorizationUri = cfg.codePath
                    if (!cfg.userInfoPath.isNullOrEmpty()) userInfoPath = cfg.userInfoPath
                    if (cfg.scopes.isNotEmpty()) scope(*cfg.scopes.toTypedArray())
                }
            }
        }
        return build
    }
}