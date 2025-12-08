package org.ghkdqhrbals.client.error

import com.fasterxml.jackson.annotation.JsonIncludeProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.databind.JsonSerializer
import com.fasterxml.jackson.databind.SerializerProvider
import com.fasterxml.jackson.databind.annotation.JsonSerialize
import org.springframework.http.HttpStatus


abstract class MyRuntimeException(
    val title: String,
    open val detail: String? = ErrorCode.UNEXPECTED.description,
    val status: HttpStatus = HttpStatus.INTERNAL_SERVER_ERROR,
    ex: Throwable? = null,
) :
    RuntimeException(title, ex), MyException {
    override fun title(): String = title
    override fun detail(): String =
        detail?.ifBlank { ErrorCode.UNEXPECTED.description } ?: ErrorCode.UNEXPECTED.description

    override fun status(): HttpStatus = status
}

@JsonIncludeProperties("type", "title", "detail", "code", "status")
interface MyException {
    @JsonProperty
    fun type(): String = this::class.simpleName.toString()

    @JsonProperty
    fun title(): String

    @JsonProperty
    fun detail(): String = ""

    @JsonProperty
    fun code(): String = ErrorCode.UNEXPECTED.code

    @JsonProperty
    @JsonSerialize(using = HttpStatusSerializer::class)
    fun status(): HttpStatus
}

class HttpStatusSerializer : JsonSerializer<HttpStatus>() {
    override fun serialize(value: HttpStatus, gen: JsonGenerator, serializers: SerializerProvider) {
        gen.writeNumber(value.value())
    }
}
