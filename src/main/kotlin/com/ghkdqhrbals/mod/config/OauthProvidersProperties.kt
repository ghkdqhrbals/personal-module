package com.ghkdqhrbals.mod.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "oauth")
data class OauthProvidersProperties(
    var providers: Map<String, OauthProviderProperties> = emptyMap()
)