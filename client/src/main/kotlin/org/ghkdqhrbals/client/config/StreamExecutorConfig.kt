package org.ghkdqhrbals.client.config

import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import java.util.concurrent.ExecutorService
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.LinkedBlockingQueue

@Configuration
class StreamExecutorConfig {

    @Bean(name = ["summaryStreamExecutor"])
    fun summaryStreamExecutor(
        @Value("\${redis.stream.consumer.threads:3}") consumerThreads: Int
    ): ExecutorService {
        // 고정 크기 ThreadPool 사용 - 병렬 처리 보장
        return ThreadPoolExecutor(
            consumerThreads,              // corePoolSize
            consumerThreads,              // maximumPoolSize
            60L,                          // keepAliveTime
            TimeUnit.SECONDS,
            LinkedBlockingQueue(),
            Thread.ofVirtual()
                .name("summary-worker-", 0)
                .factory()
        ).apply {
            allowCoreThreadTimeOut(false)  // 코어 스레드 유지
        }
    }
}

