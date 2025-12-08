package org.ghkdqhrbals.client.config

import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.context.annotation.Configuration

/**
 * arXiv API 재시도 설정
 */
@Configuration
@ConfigurationProperties(prefix = "arxiv.retry")
data class ArxivRetryConfig(
    /**
     * 최대 재시도 횟수
     */
    var maxRetries: Int = 3,

    /**
     * 초기 대기 시간 (밀리초)
     */
    var initialDelayMs: Long = 2000,

    /**
     * Rate Limit 시 대기 시간 배수
     */
    var rateLimitMultiplier: Int = 2,

    /**
     * 지수 백오프 활성화 여부
     */
    var exponentialBackoff: Boolean = true
)

