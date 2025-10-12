package org.ghkdqhrbals.oauth.autoconfigure


import org.ghkdqhrbals.oauth.config.OauthProvidersProperties
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(OauthProvidersProperties::class)
@ComponentScan("org.ghkdqhrbals.oauth")
class PersonalModuleAutoConfig