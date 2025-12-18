package org.ghkdqhrbals.infra.event

import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.persistence.EntityManager
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.data.jdbc.repository.config.EnableJdbcRepositories

@AutoConfiguration
@EnableJdbcRepositories
class EventRepositoryAutoConfiguration {
    @Bean
    fun sagaStateRepositoryImpl(
        sagaStateJdbcRepository: SagaStateJdbcRepository,
        objectMapper: ObjectMapper,
        em: EntityManager
    ): SagaStateRepository {
        return SagaStateRepositoryImpl(sagaStateJdbcRepository, objectMapper, em)
    }

    @Bean
    fun eventStoreRepositoryImpl(
        eventStoreJdbcRepository: EventStoreJdbcRepository,
        objectMapper: ObjectMapper
    ): EventStoreRepository {
        return EventStoreRepositoryImpl(eventStoreJdbcRepository, objectMapper)
    }
}