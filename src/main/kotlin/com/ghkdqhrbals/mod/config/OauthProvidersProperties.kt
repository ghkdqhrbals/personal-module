package com.ghkdqhrbals.mod.config

import com.ghkdqhrbals.mod.annotation.ConfigDescription
import org.springframework.boot.context.properties.ConfigurationProperties


data class OauthProviderProperties(
    @ConfigDescription("OAuth Client ID")
    var clientId: String = "",
    @ConfigDescription("OAuth Client Secret")
    var clientSecret: String = "",
    @ConfigDescription("OAuth Redirect URI")
    var redirectUri: String = "",
    @ConfigDescription("OAuth Token Path")
    var tokenPath: String? = "",
    @ConfigDescription("OAuth Code Path")
    var codePath: String? = "",
    @ConfigDescription("OAuth User Info Path")
    var userInfoPath: String? = "",
    @ConfigDescription("OAuth Scopes")
    var scopes: List<String> = emptyList(),
)

@ConfigurationProperties(prefix = "oauth")
data class OauthProvidersProperties(
    var providers: Map<String, OauthProviderProperties> = emptyMap()
)