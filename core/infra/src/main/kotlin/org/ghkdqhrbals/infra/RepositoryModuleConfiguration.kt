package org.ghkdqhrbals.infra

import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.context.annotation.Configuration
import org.springframework.data.jpa.repository.config.EnableJpaRepositories

/**
 * Repository 모듈 자동 설정
 * 다른 모듈에서 이 설정을 자동으로 로드하여 리포지토리와 엔티티를 사용할 수 있게 함
 */
@Configuration
@EntityScan("org.ghkdqhrbals.infra")
@EnableJpaRepositories("org.ghkdqhrbals.infra")
class RepositoryModuleConfiguration
