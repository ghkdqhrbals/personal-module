package org.ghkdqhrbals.client.domain.scheduler.config

import org.springframework.batch.core.ExitStatus
import org.springframework.batch.core.StepExecution
import org.springframework.batch.core.StepExecutionListener

class SubscribeStepSummaryListener : StepExecutionListener {

    override fun afterStep(stepExecution: StepExecution): ExitStatus {
        val read = stepExecution.readCount               // 읽은 Subscribe 수
        val write = stepExecution.writeCount             // 처리 후 write 된 수
        val skip = stepExecution.processSkipCount        // 스킵된 것
        val commit = stepExecution.commitCount           // 커밋 횟수

        println("===== SubscribeBatch Summary =====")
        println("총 구독 처리 수: $read")
        println("DB 반영된 수: $write")
        println("스킵된 수: $skip")
        println("트랜잭션 커밋 횟수: $commit")
        println("=================================")

        return stepExecution.exitStatus
    }
}