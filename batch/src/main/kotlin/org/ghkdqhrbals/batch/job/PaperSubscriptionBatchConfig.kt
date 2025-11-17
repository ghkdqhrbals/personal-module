package org.ghkdqhrbals.batch.job

import org.slf4j.LoggerFactory
import org.springframework.batch.core.Job
import org.springframework.batch.core.Step
import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.core.step.builder.StepBuilder
import org.springframework.batch.repeat.RepeatStatus
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.transaction.PlatformTransactionManager

/**
 * 논문 구독 배치 작업 설정
 * - Citation 점수 업데이트
 * - 새로운 논문 스캔
 * - 구독자 알림 발송
 */
@Configuration
class PaperSubscriptionBatchConfig(
    private val jobRepository: JobRepository,
    private val transactionManager: PlatformTransactionManager
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    @Bean
    fun paperSubscriptionJob(): Job {
        return JobBuilder("paperSubscriptionJob", jobRepository)
            .start(scanNewPapersStep())
            .next(updateCitationScoresStep())
            .next(sendNotificationsStep())
            .build()
    }

    @Bean
    fun scanNewPapersStep(): Step {
        return StepBuilder("scanNewPapersStep", jobRepository)
            .tasklet({ contribution, chunkContext ->
                log.info("🔍 새로운 논문 스캔 시작...")
                // TODO: arXiv API 호출하여 새 논문 수집
                // TODO: 구독 주제와 매칭
                log.info("✅ 새로운 논문 스캔 완료")
                RepeatStatus.FINISHED
            }, transactionManager)
            .build()
    }

    @Bean
    fun updateCitationScoresStep(): Step {
        return StepBuilder("updateCitationScoresStep", jobRepository)
            .tasklet({ contribution, chunkContext ->
                log.info("📊 Citation 점수 업데이트 시작...")
                // TODO: OpenAlex API로 citation 수 업데이트
                // TODO: 임계값 이상인 논문 필터링
                log.info("✅ Citation 점수 업데이트 완료")
                RepeatStatus.FINISHED
            }, transactionManager)
            .build()
    }

    @Bean
    fun sendNotificationsStep(): Step {
        return StepBuilder("sendNotificationsStep", jobRepository)
            .tasklet({ contribution, chunkContext ->
                log.info("📧 구독자 알림 발송 시작...")
                // TODO: 구독자 설정 조회
                // TODO: Citation 임계값 체크
                // TODO: 이메일/푸시 알림 발송
                log.info("✅ 구독자 알림 발송 완료")
                RepeatStatus.FINISHED
            }, transactionManager)
            .build()
    }
}

