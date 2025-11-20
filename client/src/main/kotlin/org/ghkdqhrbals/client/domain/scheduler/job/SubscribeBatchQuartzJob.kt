package org.ghkdqhrbals.client.domain.scheduler.job

import org.quartz.Job
import org.quartz.JobExecutionContext
import org.springframework.batch.core.JobParametersBuilder
import org.springframework.batch.core.launch.JobLauncher
import org.springframework.stereotype.Component

@Component
class SubscribeBatchQuartzJob(
    private val jobLauncher: JobLauncher,
    private val subscribeJob: org.springframework.batch.core.Job
) : Job {

    override fun execute(context: JobExecutionContext) {
        val params = JobParametersBuilder()
            .addLong("timestamp", System.currentTimeMillis())
            .toJobParameters()

        jobLauncher.run(subscribeJob, params)
    }
}