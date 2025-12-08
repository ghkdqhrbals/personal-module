package org.ghkdqhrbals.client.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

@Configuration
@ConfigurationProperties(prefix = "openai")
class OpenAiProperties {
    var enabled: Boolean = false
    var api: ApiProperties = ApiProperties()

    class ApiProperties {
        var key: String = ""
    }
}

