package org.ghkdqhrbals.client.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.serializer.StringRedisSerializer
import java.time.Duration

@Configuration
class RedisConfig(
    @Value("\${redis.host}") private val host: String,
    @Value("\${redis.port}") private val port: Int
) {

//    @Bean
//    fun redisConnectionFactory(): RedisConnectionFactory {
//        val clientConfig = LettuceClientConfiguration.builder()
//            .shutdownTimeout(Duration.ofSeconds(3))
//            .build()
//        val standalone = RedisStandaloneConfiguration(host, port)
//        return LettuceConnectionFactory(standalone, clientConfig).apply {
//            // 초기화
//            afterPropertiesSet()
//        }
//    }

    @Bean
    fun stringRedisTemplate(connectionFactory: RedisConnectionFactory): StringRedisTemplate = StringRedisTemplate().apply {
        setConnectionFactory(connectionFactory)
        keySerializer = StringRedisSerializer()
        valueSerializer = StringRedisSerializer()
        hashKeySerializer = StringRedisSerializer()
        hashValueSerializer = StringRedisSerializer()
        afterPropertiesSet()
    }
}
