package org.ghkdqhrbals.message

import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

/**
 * Message 모듈 자동 설정
 * META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports에 등록되어 자동으로 로드됨
 */
@Configuration
@EnableJpaRepositories
class MessageModuleConfiguration

