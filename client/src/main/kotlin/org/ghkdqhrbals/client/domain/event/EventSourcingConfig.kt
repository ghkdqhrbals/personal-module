package org.ghkdqhrbals.client.domain.event

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.scheduling.annotation.EnableAsync
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor
import java.util.concurrent.Executor

/**
 * 이벤트 소싱 관련 설정
 */
@Configuration
@EnableAsync
class EventSourcingConfig {

    /**
     * 비동기 이벤트 처리를 위한 Executor
     */
    @Bean(name = ["eventHandlerExecutor"])
    fun eventHandlerExecutor(): Executor {
        val executor = ThreadPoolTaskExecutor()
        executor.corePoolSize = 5
        executor.maxPoolSize = 10
        executor.queueCapacity = 100
        executor.setThreadNamePrefix("event-handler-")
        executor.setWaitForTasksToCompleteOnShutdown(true)
        executor.setAwaitTerminationSeconds(60)
        executor.initialize()
        return executor
    }
}

