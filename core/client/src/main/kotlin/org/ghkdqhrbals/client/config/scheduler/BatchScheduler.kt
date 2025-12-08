package org.ghkdqhrbals.client.config.scheduler

import org.ghkdqhrbals.client.config.log.logger
import org.springframework.scheduling.annotation.EnableScheduling
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@EnableScheduling
class BatchScheduler(

) {
    // per 10 minute.
    @Scheduled(cron = "*/1 * * * * *")
    fun hello() {
        // call local api
    }
}