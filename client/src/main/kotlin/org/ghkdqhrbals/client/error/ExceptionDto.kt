package org.ghkdqhrbals.client.error

import com.fasterxml.jackson.annotation.JsonIncludeProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIncludeProperties("detail", "code")
class ExceptionDto(
    @JsonProperty
    val detail: String = "",
    @JsonProperty
    val code: String,
) {
}