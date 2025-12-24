package org.ghkdqhrbals.message.kafka.config

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty

/**
 * Kafka가 활성화된 경우에만 빈을 등록하는 조건부 어노테이션입니다.
 * spring.kafka.enabled=true 일 때만 해당 빈이 활성화됩니다.
 */
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
@ConditionalOnProperty(name = ["spring.kafka.enabled"], havingValue = "true", matchIfMissing = false)
annotation class ConditionalOnKafkaEnabled

