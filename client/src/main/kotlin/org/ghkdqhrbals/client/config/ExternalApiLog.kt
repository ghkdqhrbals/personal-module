package org.ghkdqhrbals.client.config

import com.fasterxml.jackson.annotation.JsonInclude

@JsonInclude(JsonInclude.Include.NON_NULL)
data class ExternalApiLog(
    val uri: String,
    val method: String,
    val requestBody: Any?,
    val responseBody: Any?,
    val statusCode: Int?,
    val headers: Any?,
    val mdc: Any?,
)
