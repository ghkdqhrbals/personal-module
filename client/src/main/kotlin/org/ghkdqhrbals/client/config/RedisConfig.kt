package org.ghkdqhrbals.client.config

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.serializer.StringRedisSerializer

/**
 * Redis 설정
 * - 테스트 환경에서는 TestContainer의 @ServiceConnection이 자동으로 RedisConnectionFactory를 제공
 * - Production 환경에서는 spring.redis.host/port 설정 사용
 */
@Configuration
@ConditionalOnProperty(
    name = ["spring.redis.host"],
    matchIfMissing = false
)
class RedisConfig {

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
