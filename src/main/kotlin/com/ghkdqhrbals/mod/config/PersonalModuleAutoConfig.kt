package com.ghkdqhrbals.mod.config


import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.context.annotation.ComponentScan
import org.springframework.context.annotation.Configuration

@Configuration
@EnableConfigurationProperties(OauthProvidersProperties::class)
@ComponentScan("com.ghkdqhrbals.mod")
class PersonalModuleAutoConfig