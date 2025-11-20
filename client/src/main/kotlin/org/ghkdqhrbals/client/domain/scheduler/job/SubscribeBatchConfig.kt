package org.ghkdqhrbals.client.domain.scheduler.job

import jakarta.persistence.EntityManagerFactory
import org.ghkdqhrbals.client.domain.event.EventPublisher
import org.ghkdqhrbals.client.domain.paper.service.ArxivHttpClient
import org.ghkdqhrbals.client.domain.paper.service.ArxivService
import org.ghkdqhrbals.client.domain.subscribe.entity.Subscribe
import org.ghkdqhrbals.client.domain.subscribe.repository.SubscribeRepository
import org.springframework.batch.core.Job
import org.springframework.batch.core.Step
import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.core.step.builder.StepBuilder
import org.springframework.batch.item.ItemProcessor
import org.springframework.batch.item.data.RepositoryItemReader
import org.springframework.batch.item.data.builder.RepositoryItemReaderBuilder
import org.springframework.batch.item.database.JpaItemWriter
import org.springframework.batch.item.database.builder.JpaItemWriterBuilder
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.domain.Sort
import org.springframework.transaction.PlatformTransactionManager

@Configuration
class SubscribeBatchConfig(
    private val jobRepository: JobRepository,
    private val transactionManager: PlatformTransactionManager,
    private val entityManagerFactory: EntityManagerFactory,
    private val arxivService: ArxivService,
    private val subscribeRepository: SubscribeRepository,
    private val eventPublisher: EventPublisher,
    private val arxivHttpClient: ArxivHttpClient,
    private val subscribePaperChunkProcessor: SubscribePaperChunkProcessor
) {

    @Bean("subscribeJob")
    fun subscribeJob(subscribeStep: Step): Job {
        return JobBuilder("subscribeJob", jobRepository)
            .start(subscribeStep)
            .build()
    }

    @Bean
    fun subscribeStep(): Step {
        return StepBuilder("subscribeStep", jobRepository)
            .chunk<Subscribe, Subscribe>(50, transactionManager)
            .reader(subscribeItemReader())
            .processor(subscribeItemProcessor())
            .writer(subscribeItemWriter())
            .build()
    }

    @Bean
    fun subscribeItemReader(): RepositoryItemReader<Subscribe> {
        return RepositoryItemReaderBuilder<Subscribe>()
            .name("subscribeRepositoryReader")
            .repository(subscribeRepository)
            .methodName("findAllByActivatedIsTrue")
            .pageSize(10)
            .sorts(mapOf("id" to Sort.Direction.ASC))
            .build()
    }

    @Bean
    fun subscribeItemProcessor(): ItemProcessor<Subscribe, Subscribe> {
        return ItemProcessor { subscribe ->
            // 각 Subscribe에 대해 모든 페이지의 논문을 청크 단위로 처리
            val totalProcessed = subscribePaperChunkProcessor.processAllPages(subscribe, pageSize = 10)

            val result = if (totalProcessed > 0) "✅" else "⚠️"
            println("$result [Subscribe#${subscribe.id}] '${subscribe.name}' (${subscribe.subscribeType}) - 완료: ${totalProcessed}개 논문 처리")

            subscribe
        }
    }

    @Bean
    fun subscribeItemWriter(): JpaItemWriter<Subscribe> {
        return JpaItemWriterBuilder<Subscribe>()
            .entityManagerFactory(entityManagerFactory)
            .usePersist(false)
            .build()
    }
}