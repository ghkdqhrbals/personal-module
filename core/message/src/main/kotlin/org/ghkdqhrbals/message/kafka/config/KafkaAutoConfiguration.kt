package org.ghkdqhrbals.message.kafka.config

import org.apache.kafka.clients.consumer.ConsumerConfig
import org.apache.kafka.clients.producer.ProducerConfig
import org.apache.kafka.common.serialization.StringDeserializer
import org.apache.kafka.common.serialization.StringSerializer
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.annotation.EnableKafka
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory
import org.springframework.kafka.core.*

/**
 * Kafka 자동 설정
 * spring.kafka.enabled=true 일 때만 활성화
 */
@Configuration
@EnableKafka
@ConditionalOnKafkaEnabled
class KafkaAutoConfiguration {

    @Value("\${spring.kafka.bootstrap-servers:localhost:9092}")
    private lateinit var bootstrapServers: String

    @Value("\${spring.kafka.consumer.group-id:default-group}")
    private lateinit var consumerGroupId: String

    /**
     * Kafka Producer Factory
     */
    @Bean
    @ConditionalOnMissingBean(ProducerFactory::class)
    fun kafkaProducerFactory(): ProducerFactory<String, String> {
        val configProps = mapOf(
            ProducerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
            ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
            ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG to StringSerializer::class.java,
            ProducerConfig.ACKS_CONFIG to "all",
            ProducerConfig.RETRIES_CONFIG to 3,
            ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG to true,
            ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION to 5
        )
        return DefaultKafkaProducerFactory(configProps)
    }

    /**
     * Kafka Template
     */
    @Bean
    @ConditionalOnMissingBean(KafkaTemplate::class)
    fun kafkaTemplate(producerFactory: ProducerFactory<String, String>): KafkaTemplate<String, String> {
        return KafkaTemplate(producerFactory)
    }

    /**
     * Kafka Consumer Factory
     */
    @Bean
    @ConditionalOnMissingBean(ConsumerFactory::class)
    fun kafkaConsumerFactory(): ConsumerFactory<String, String> {
        val configProps = mapOf(
            ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG to bootstrapServers,
            ConsumerConfig.GROUP_ID_CONFIG to consumerGroupId,
            ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
            ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG to StringDeserializer::class.java,
            ConsumerConfig.AUTO_OFFSET_RESET_CONFIG to "earliest",
            ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG to false
        )
        return DefaultKafkaConsumerFactory(configProps)
    }

    /**
     * Kafka Listener Container Factory
     */
    @Bean
    @ConditionalOnMissingBean(ConcurrentKafkaListenerContainerFactory::class)
    fun kafkaListenerContainerFactory(
        consumerFactory: ConsumerFactory<String, String>
    ): ConcurrentKafkaListenerContainerFactory<String, String> {
        val factory = ConcurrentKafkaListenerContainerFactory<String, String>()
        factory.consumerFactory = consumerFactory
        factory.setConcurrency(3)
        return factory
    }
}

