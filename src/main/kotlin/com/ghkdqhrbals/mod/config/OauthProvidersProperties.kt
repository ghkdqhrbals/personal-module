package com.ghkdqhrbals.mod.config

import org.springframework.boot.context.properties.ConfigurationProperties


data class OauthProviderProperties(
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
    var providers: Map<String, OauthProviderProperties> = emptyMap()
)