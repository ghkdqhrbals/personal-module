package org.ghkdqhrbals.client.domain.scheduler.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Batch Job 관련 설정
 */
@Configuration
class BatchJobConfig {

    /**
     * Reader chunk size
     * application.yml에서 설정 가능
     */
    @Bean
    fun readerChunkSize(@Value("\${batch.reader.chunk-size:100}") chunkSize: Int): Int {
        return chunkSize
    }

    /**
     * Job chunk size
     * application.yml에서 설정 가능
     */
    @Bean
    fun jobChunkSize(@Value("\${batch.job.chunk-size:10}") chunkSize: Int): Int {
        return chunkSize
    }
}

