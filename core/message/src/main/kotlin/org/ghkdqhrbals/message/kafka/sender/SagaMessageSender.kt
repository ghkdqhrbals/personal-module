package org.ghkdqhrbals.message.kafka.sender

import com.fasterxml.jackson.databind.ObjectMapper
import org.ghkdqhrbals.message.saga.definition.SagaTopics
import org.ghkdqhrbals.model.event.SagaCommandEvent
import org.ghkdqhrbals.model.event.SagaResponse
import org.slf4j.LoggerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.stereotype.Component
import java.util.concurrent.CompletableFuture

/**
 * Saga 메시지 전송 컴포넌트
 */
@Component
class SagaMessageSender(
    private val kafkaTemplate: KafkaTemplate<String, String>,
    private val objectMapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(SagaMessageSender::class.java)

    /**
     * Saga 명령 전송
     */
    fun sendCommand(
        topic: String,
        sagaId: String,
        command: SagaCommandEvent
    ): CompletableFuture<SendResult> {
        val message = objectMapper.writeValueAsString(command)
        return send(topic, sagaId, message)
            .thenApply { result ->
                log.info("Command sent: topic={}, sagaId={}, step={}",
                    topic, sagaId, command.stepName)
                result
            }
    }

    /**
     * Saga 응답 전송
     */
    fun sendResponse(
        sagaId: String,
        response: SagaResponse,
        responseTopic: String = SagaTopics.SAGA_RESPONSE
    ): CompletableFuture<SendResult> {
        val message = objectMapper.writeValueAsString(response)
        return send(responseTopic, sagaId, message)
            .thenApply { result ->
                log.info("Response sent: topic={}, sagaId={}, success={}",
                    responseTopic, sagaId, response.success)
                result
            }
    }

    /**
     * 성공 응답 전송
     */
    fun sendSuccessResponse(
        sagaId: String,
        stepName: String,
        stepIndex: Int,
        sourceService: String,
        payload: Map<String, Any> = emptyMap(),
        responseTopic: String = SagaTopics.SAGA_RESPONSE
    ): CompletableFuture<SendResult> {
        val response = org.ghkdqhrbals.message.saga.builder.SagaResponseBuilder.success(
            sagaId = sagaId,
            stepName = stepName,
            stepIndex = stepIndex,
            sourceService = sourceService,
            payload = payload
        )
        val message = objectMapper.writeValueAsString(response)
        return send(responseTopic, sagaId, message)
            .thenApply { result ->
                log.info("Success response sent: sagaId={}, step={}", sagaId, stepName)
                result
            }
    }

    /**
     * 실패 응답 전송
     */
    fun sendFailureResponse(
        sagaId: String,
        stepName: String,
        stepIndex: Int,
        sourceService: String,
        errorMessage: String,
        payload: Map<String, Any> = emptyMap(),
        responseTopic: String = SagaTopics.SAGA_RESPONSE
    ): CompletableFuture<SendResult> {
        val response = org.ghkdqhrbals.message.saga.builder.SagaResponseBuilder.failure(
            sagaId = sagaId,
            stepName = stepName,
            stepIndex = stepIndex,
            sourceService = sourceService,
            errorMessage = errorMessage,
            payload = payload
        )
        val message = objectMapper.writeValueAsString(response)
        return send(responseTopic, sagaId, message)
            .thenApply { result ->
                log.error("Failure response sent: sagaId={}, step={}, error={}",
                    sagaId, stepName, errorMessage)
                result
            }
    }

    /**
     * 보상 성공 응답 전송
     */
    fun sendCompensationSuccessResponse(
        sagaId: String,
        stepName: String,
        stepIndex: Int,
        sourceService: String,
        payload: Map<String, Any> = emptyMap(),
        responseTopic: String = SagaTopics.SAGA_RESPONSE
    ): CompletableFuture<SendResult> {
        val response = org.ghkdqhrbals.message.saga.builder.SagaResponseBuilder.compensationSuccess(
            sagaId = sagaId,
            stepName = stepName,
            stepIndex = stepIndex,
            sourceService = sourceService,
            payload = payload
        )
        val message = objectMapper.writeValueAsString(response)
        return send(responseTopic, sagaId, message)
            .thenApply { result ->
                log.info("Compensation success response sent: sagaId={}, step={}", sagaId, stepName)
                result
            }
    }

    /**
     * 일반 메시지 전송
     */
    fun send(topic: String, key: String, message: String): CompletableFuture<SendResult> {
        val future = CompletableFuture<SendResult>()

        kafkaTemplate.send(topic, key, message)
            .thenAccept { result ->
                val sendResult = SendResult(
                    success = true,
                    topic = topic,
                    partition = result.recordMetadata.partition(),
                    offset = result.recordMetadata.offset(),
                    timestamp = result.recordMetadata.timestamp()
                )
                log.debug("Message sent: topic={}, partition={}, offset={}",
                    topic, sendResult.partition, sendResult.offset)
                future.complete(sendResult)
            }
            .exceptionally { ex ->
                val sendResult = SendResult(
                    success = false,
                    topic = topic,
                    errorMessage = ex.message
                )
                log.error("Failed to send message: topic={}, key={}, error={}",
                    topic, key, ex.message)
                future.complete(sendResult)
                null
            }

        return future
    }

    /**
     * 동기 전송 (블로킹)
     */
    fun sendSync(topic: String, key: String, message: String, timeoutMs: Long = 10000): SendResult {
        return send(topic, key, message).get(timeoutMs, java.util.concurrent.TimeUnit.MILLISECONDS)
    }
}

/**
 * 전송 결과
 */
data class SendResult(
    val success: Boolean,
    val topic: String,
    val partition: Int? = null,
    val offset: Long? = null,
    val timestamp: Long? = null,
    val errorMessage: String? = null
)

