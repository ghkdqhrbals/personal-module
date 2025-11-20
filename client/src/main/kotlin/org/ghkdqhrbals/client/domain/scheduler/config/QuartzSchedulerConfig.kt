package org.ghkdqhrbals.client.domain.scheduler.config


import org.ghkdqhrbals.client.domain.scheduler.job.SubscribeBatchQuartzJob

import org.quartz.*
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

/**
 * Quartz 스케줄러 설정
 * SubscribeBatchJob을 매 10분마다 실행하도록 스케줄링
 */
@Configuration
class QuartzSchedulerConfig {

    /**
     * JobDetail 등록
     */
    @Bean
    fun subscribeBatchJobDetail(): JobDetail =
        JobBuilder.newJob(SubscribeBatchQuartzJob::class.java)
            .withIdentity("subscribeBatchJob", "batch-jobs")
            .storeDurably()
            .build()

    /**
     * Trigger: 1초 간격 반복
     */
    @Bean
    fun subscribeBatchJobTrigger(jobDetail: JobDetail): Trigger =
        TriggerBuilder.newTrigger()
            .forJob(jobDetail)
            .withIdentity("subscribeBatchJobTrigger", "batch-triggers")
            .startNow()
            .withSchedule(
                SimpleScheduleBuilder.simpleSchedule()
                    .withIntervalInMinutes(10)
                    .repeatForever()
            )
            .build()
}