package org.ghkdqhrbals.client.config

import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonInclude.Include
import com.fasterxml.jackson.core.JsonGenerator
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.core.json.JsonReadFeature
import com.fasterxml.jackson.databind.*
import com.fasterxml.jackson.databind.cfg.MapperConfig
import com.fasterxml.jackson.databind.introspect.AnnotatedField
import com.fasterxml.jackson.databind.introspect.AnnotatedMethod
import com.fasterxml.jackson.databind.introspect.AnnotatedParameter
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.datatype.jsr310.deser.LocalTimeDeserializer
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer
import com.fasterxml.jackson.datatype.jsr310.ser.LocalTimeSerializer
import com.fasterxml.jackson.module.kotlin.*
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

object Jackson {
    private val mapper: ObjectMapper = JsonMapper.builder()
        .enable(MapperFeature.DEFAULT_VIEW_INCLUSION)
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .addModule(JavaTimeModule())
        .addModule(KotlinModule.Builder().build())
        .addModule(
            SimpleModule()
                .addSerializer(CustomDateSerializer())
                .addSerializer(LocalDateTime::class.java, LocalDateTimeSerializer(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"))),
        )
        .build()
        .setSerializationInclusion(Include.NON_NULL)
        .registerKotlinModule()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false).apply {
            enable(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS.mappedFeature())
        }

    fun getMapper(): ObjectMapper = mapper

    private val snakeCaseMapper: ObjectMapper = JsonMapper.builder()
        .enable(MapperFeature.DEFAULT_VIEW_INCLUSION)
        .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .addModule(JavaTimeModule())
        .addModule(KotlinModule.Builder().build())
        .addModule(
            SimpleModule()
                .addSerializer(CustomDateSerializer())
                .addSerializer(LocalDateTime::class.java, LocalDateTimeSerializer(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"))),
        )
        .build()
        .setSerializationInclusion(Include.ALWAYS)
        .registerKotlinModule()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    fun getMapperUsingUnderScore(): ObjectMapper = snakeCaseMapper

    private val upperCaseMapper: ObjectMapper = JsonMapper.builder()
        .enable(MapperFeature.DEFAULT_VIEW_INCLUSION)
        .propertyNamingStrategy(SnakeCaseUpperCaseNamingStrategy()) // 대문자 전략 설정
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .addModule(JavaTimeModule())
        .addModule(KotlinModule.Builder().build())
        .addModule(
            SimpleModule()
                .addSerializer(CustomDateSerializer())
                .addSerializer(
                    LocalDateTime::class.java,
                    LocalDateTimeSerializer(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss")),
                ),
        )
        .build()
        .setSerializationInclusion(Include.ALWAYS)
        .registerKotlinModule()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    fun getMapperUsingUpperCase(): ObjectMapper = upperCaseMapper
}

class SnakeCaseUpperCaseNamingStrategy : PropertyNamingStrategy() {
    private val snakeCaseStrategy = PropertyNamingStrategies.SNAKE_CASE // 스네이크 케이스 전략 인스턴스화

    override fun nameForField(
        config: MapperConfig<*>,
        field: AnnotatedField,
        defaultName: String,
    ): String {
        return snakeCaseStrategy.nameForField(config, field, defaultName).uppercase()
    }

    override fun nameForGetterMethod(
        config: MapperConfig<*>,
        method: AnnotatedMethod,
        defaultName: String,
    ): String {
        return snakeCaseStrategy.nameForGetterMethod(config, method, defaultName).uppercase()
    }

    override fun nameForSetterMethod(
        config: MapperConfig<*>,
        method: AnnotatedMethod,
        defaultName: String,
    ): String {
        return snakeCaseStrategy.nameForSetterMethod(config, method, defaultName).uppercase()
    }

    override fun nameForConstructorParameter(
        config: MapperConfig<*>,
        ctorParam: AnnotatedParameter,
        defaultName: String,
    ): String {
        return snakeCaseStrategy.nameForConstructorParameter(config, ctorParam, defaultName).uppercase()
    }
}

private class CustomDateSerializer : StdSerializer<Date>(Date::class.java) {
    override fun serialize(value: Date?, gen: JsonGenerator?, provider: SerializerProvider?) {
        if (value != null && gen != null) {
            gen.writeString(
                LocalDateTime.ofInstant(
                    value.toInstant(),
                    ZoneId.systemDefault(),
                ).format(DateTimeFormatter.ISO_DATE_TIME),
            )
        }
    }
}

object LocalTimeMapper {
    private val customTimeModule = JavaTimeModule()
        .addSerializer(
            LocalTime::class.java,
            object : JsonSerializer<LocalTime>() {
                private val defaultSerializer = LocalTimeSerializer(DateTimeFormatter.ofPattern("HH:mm"))
                override fun serialize(value: LocalTime?, g: JsonGenerator, provider: SerializerProvider) {
                    if (value != null && LocalTime.of(23, 59, 59) < value) {
                        g.writeString("24:00")
                    } else {
                        defaultSerializer.serialize(value, g, provider)
                    }
                }
            },
        )
        .addDeserializer(
            LocalTime::class,
            object : JsonDeserializer<LocalTime>() {
                private val defaultDeserializer = LocalTimeDeserializer(DateTimeFormatter.ofPattern("HH:mm"))
                private val secondDeserializer = LocalTimeDeserializer(DateTimeFormatter.ofPattern("H:mm"))

                override fun deserialize(parser: JsonParser, context: DeserializationContext?): LocalTime? {
                    return if (parser.text == null || parser.text == "null") {
                        null
                    } else if (parser.text == "24:00") {
                        LocalTime.MAX
                    } else if (parser.text.length == 4) {
                        /** 9:00 꼴일때 **/
                        secondDeserializer.deserialize(parser, context)
                    } else {
                        /** 19:00 꼴일때 **/
                        defaultDeserializer.deserialize(parser, context)
                    }
                }
            },
        )

    val mapper: ObjectMapper = jacksonObjectMapper()
        .registerModule(
            customTimeModule,
        )

    fun toString(time: LocalTime): String {
        return mapper.writeValueAsString(time).replace("\"", "")
    }
}

fun <T> T.toJson(): String = Jackson.getMapper().writeValueAsString(this)
fun <T> T.toSnakeJson(): String = Jackson.getMapperUsingUnderScore().writeValueAsString(this)
inline fun <reified T> String.fromJson(): T = Jackson.getMapper().readValue(this)
inline fun <reified T> ByteArray.fromJson(): T = Jackson.getMapper().readValue(this)
