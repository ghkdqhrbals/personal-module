package org.ghkdqhrbals.oauth.config

import org.springframework.boot.context.properties.ConfigurationProperties

data class OauthProp(
    var clientId: String = "",
    var clientSecret: String = "",
    var redirectUri: String = "",
    var tokenPath: String? = "",
    var codePath: String? = "",
    var userInfoPath: String? = "",
    var scopes: List<String> = emptyList(),
)

@ConfigurationProperties(prefix = "oauth")
data class OauthProvidersProperties(
    var providers: Map<String, OauthProp> = emptyMap()
)