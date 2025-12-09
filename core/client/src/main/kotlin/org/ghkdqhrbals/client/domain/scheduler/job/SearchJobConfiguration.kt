package org.ghkdqhrbals.client.domain.scheduler.job

import jakarta.persistence.EntityManagerFactory
import org.ghkdqhrbals.client.config.log.logger
import org.ghkdqhrbals.client.domain.paper.service.ArxivHttpClient
import org.ghkdqhrbals.client.domain.paper.service.ArxivService
import org.ghkdqhrbals.client.domain.scheduler.config.NowJobTimeStamper
import org.ghkdqhrbals.client.domain.scheduler.config.SubscribeReader
import org.ghkdqhrbals.client.domain.scheduler.config.SubscribeStepSummaryListener
import org.ghkdqhrbals.infra.subscribe.Subscribe
import org.ghkdqhrbals.infra.subscribe.SubscribeRepository
import org.springframework.batch.core.Job
import org.springframework.batch.core.Step
import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.core.step.builder.StepBuilder
import org.springframework.batch.item.ItemProcessor
import org.springframework.batch.item.database.JpaItemWriter
import org.springframework.batch.item.database.builder.JpaItemWriterBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.transaction.PlatformTransactionManager

@Configuration
class SearchJobConfiguration(
    private val jobRepository: JobRepository,
    private val transactionManager: PlatformTransactionManager,
    private val entityManagerFactory: EntityManagerFactory,
    private val arxivService: ArxivService,
    private val subscribeRepository: SubscribeRepository,
    private val arxivHttpClient: ArxivHttpClient,
    private val nowJobTimeStamper: NowJobTimeStamper,
    private val subscribePaperChunkProcessor: SubscribePaperChunkProcessor
) {
    companion object {
        val PAGE_SIZE = 10
        const val JOB_NAME = "searchJob"
    }

    @Bean(JOB_NAME)
    fun searchJob(searchStep: Step): Job {
        return JobBuilder(JOB_NAME, jobRepository)
            .start(searchStep)
            .incrementer(nowJobTimeStamper)
            .build()
    }

    @Bean
    fun searchStep(): Step {
        return StepBuilder("${JOB_NAME}Step", jobRepository)
            .chunk<Subscribe, Subscribe>(PAGE_SIZE, transactionManager)
            .reader(SubscribeReader(subscribeRepository, chunkSize = PAGE_SIZE))
            .processor(searchItemProcessor())
            .listener(SubscribeStepSummaryListener())
            .writer(searchItemWriter())
            .allowStartIfComplete(true)  // 이미 완료된 Step도 재실행 가능하게 설정
            .build()
    }

//    @Bean
//    fun subscribeItemReader(): RepositoryItemReader<Subscribe> {
//        return RepositoryItemReaderBuilder<Subscribe>()
//            .name("subscribeRepositoryReader")
//            .repository(subscribeRepository)
//            .methodName("findAllByActivatedIsTrue")
//            .pageSize(10)
//            .sorts(mapOf("id" to Sort.Direction.ASC))
//            .build()
//    }

    @Bean
    fun searchItemProcessor(): ItemProcessor<Subscribe, Subscribe> {
        return ItemProcessor { subscribe ->
            logger().info("▶️ [Subscribe#${subscribe.id}] '${subscribe.name}' (${subscribe.subscribeType}) - 처리 시작")
            // 각 Subscribe에 대해 모든 페이지의 논문을 청크 단위로 처리
            val totalProcessed = subscribePaperChunkProcessor.processAllPages(subscribe, pageSize = PAGE_SIZE)

            val result = if (totalProcessed > 0) "✅" else "⚠️"
            println("$result [Subscribe#${subscribe.id}] '${subscribe.name}' (${subscribe.subscribeType}) - 완료: ${totalProcessed}개 논문 처리")

            subscribe
        }
    }

    @Bean
    fun searchItemWriter(): JpaItemWriter<Subscribe> {
        return JpaItemWriterBuilder<Subscribe>()
            .entityManagerFactory(entityManagerFactory)
            .usePersist(false)
            .build()
    }
}