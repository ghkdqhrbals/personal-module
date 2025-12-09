package org.ghkdqhrbals.infra.paper

import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.data.jdbc.repository.config.EnableJdbcRepositories

@AutoConfiguration
@EnableJdbcRepositories
class PaperRepositoryAutoConfiguration {
    @Bean
    fun paperRepository(paperJdbcRepository: PaperJdbcRepository): PaperRepository {
        return PaperRepositoryImpl(
            paperJdbcRepository,
        )
    }
}