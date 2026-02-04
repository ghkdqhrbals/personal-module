package org.ghkdqhrbals.client.config

import io.lettuce.core.RedisURI
import io.lettuce.core.cluster.RedisClusterClient
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection
import io.lettuce.core.codec.ByteArrayCodec
import org.ghkdqhrbals.message.redis.ConditionalOnRedisStreamEnabled
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.data.redis.listener.RedisMessageListenerContainer
import org.springframework.data.redis.serializer.StringRedisSerializer

/**
 * Redis 설정
 * - 테스트 환경에서는 TestContainer의 @ServiceConnection이 자동으로 RedisConnectionFactory를 제공
 * - Production 환경에서는 spring.redis.host/port 설정 사용
 */
@Configuration
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

    @Bean
    fun redisMessageListenerContainer(connectionFactory: RedisConnectionFactory): RedisMessageListenerContainer {
        val container = RedisMessageListenerContainer()
        container.setConnectionFactory(connectionFactory)
        return container
    }
//
//    @Bean(destroyMethod = "shutdown")
//    fun redisClusterClient(): RedisClusterClient =
//        RedisClusterClient.create(nodes.map { RedisURI.create(it) })
//
//    /** ★ XAUTOCLAIM 전용 단일 커넥션 */
//    @Bean(destroyMethod = "close")
//    fun redisClusterConnection(
//        redisClusterClient: RedisClusterClient
//    ): StatefulRedisClusterConnection<ByteArray, ByteArray> =
//        redisClusterClient.connect(ByteArrayCodec.INSTANCE)
}
