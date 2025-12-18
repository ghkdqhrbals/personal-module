package org.ghkdqhrbals.infra.user

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.boot.autoconfigure.AutoConfiguration
import org.springframework.context.annotation.Bean
import org.springframework.data.jdbc.repository.config.EnableJdbcRepositories

@AutoConfiguration
@EnableJdbcRepositories
class UserRepositoryAutoConfiguration {
    @Bean
    fun userRepositoryImpl(
        userRepository: UserJdbcRepository,
        objectMapper: ObjectMapper
    ): UserRepository {
        return UserRepositoryImpl(userRepository,)
    }
}