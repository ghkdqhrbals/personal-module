package org.ghkdqhrbals.orchestrator.saga.service

import com.fasterxml.jackson.databind.ObjectMapper
import org.ghkdqhrbals.message.service.EventStoreService
import org.slf4j.LoggerFactory
import org.springframework.data.redis.connection.Message
import org.springframework.data.redis.connection.MessageListener
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.listener.ChannelTopic
import org.springframework.data.redis.listener.RedisMessageListenerContainer
import org.springframework.stereotype.Service
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter
import java.util.concurrent.ConcurrentHashMap

/**
 * SSE를 통한 Saga 이벤트 실시간 스트리밍 서비스
 * Redis pub/sub을 활용하여 이벤트 전파
 */
@Service
class SagaEventStreamService(
    private val redisTemplate: RedisTemplate<String, String>,
    private val redisMessageListenerContainer: RedisMessageListenerContainer,
    private val eventStoreService: EventStoreService,
    private val objectMapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(this::class.java)

    // sagaId별 SSE emitter 목록 관리
    private val emitters = ConcurrentHashMap<String, MutableSet<SseEmitter>>()

    // sagaId별 Redis 리스너 관리
    private val listeners = ConcurrentHashMap<String, MessageListener>()

    fun revokeEmitters(sagaId: String) {
        emitters[sagaId]?.forEach { emitter ->
            try {
                emitter.complete()
            } catch (e: Exception) {
                log.warn("Failed to complete emitter for sagaId: {}", sagaId, e)
            }
        }
        emitters.remove(sagaId)
        unsubscribeFromSagaEvents(sagaId)
        log.info("Revoked all SSE emitters for sagaId: {}", sagaId)
    }
    /**
     * 특정 sagaId에 대한 SSE 연결 생성
     */
    fun createEmitter(sagaId: String): SseEmitter {
        val emitter = SseEmitter(300_000L) // 5분 타임아웃

        // Emitter 등록
        emitters.computeIfAbsent(sagaId) { mutableSetOf() }.add(emitter)

        // Redis 리스너가 없으면 생성
        if (!listeners.containsKey(sagaId)) {
            subscribeToSagaEvents(sagaId)
        }

        // Emitter 종료 시 정리
        emitter.onCompletion {
            complete(sagaId, emitter)
        }
        emitter.onTimeout {
            complete(sagaId, emitter)
        }
        emitter.onError {
            complete(sagaId, emitter)
        }

        log.info("SSE emitter created for sagaId: {}", sagaId)

        try {
            // 1. 초기 연결 확인 메시지
            emitter.send(
                SseEmitter.event()
                    .name("connected")
                    .data(mapOf("sagaId" to sagaId, "message" to "Connected to Saga event stream"))
            )

            // 2. 기존 이벤트 히스토리 전송
            sendEventHistory(sagaId, emitter)

        } catch (e: Exception) {
            log.error("Failed to send initial messages for sagaId: {}", sagaId, e)
            complete(sagaId, emitter)
        }

        return emitter
    }

    /**
     * 기존 이벤트 히스토리를 SSE로 전송
     */
    private fun sendEventHistory(sagaId: String, emitter: SseEmitter) {
        try {
            val eventSourcing = eventStoreService.getSagaEventSourcing(sagaId)

            if (eventSourcing == null) {
                log.warn("No event sourcing data found for sagaId: {}", sagaId)
                return
            }

            log.info("Sending {} events for sagaId: {}", eventSourcing.events.size, sagaId)

            // 모든 이벤트를 순서대로 전송
            eventSourcing.events.forEach { event ->
                try {
                    emitter.send(
                        SseEmitter.event()
                            .name("saga-event-history")
                            .data(mapOf(
                                "eventId" to event.eventId,
                                "sagaId" to sagaId,
                                "sequenceNumber" to event.sequenceNumber,
                                "eventType" to event.eventType.name,
                                "stepName" to event.stepName,
                                "stepIndex" to event.stepIndex,
                                "success" to event.success,
                                "errorMessage" to event.errorMessage,
                                "timestamp" to event.timestamp.toInstant().toEpochMilli(),
                                "payload" to event.payload
                            ))
                    )
                } catch (e: Exception) {
                    log.warn("Failed to send event history: eventId={}", event.eventId, e)
                }
            }

            // 현재 Saga 상태 전송
            emitter.send(
                SseEmitter.event()
                    .name("saga-state")
                    .data(mapOf(
                        "sagaId" to eventSourcing.sagaId,
                        "sagaType" to eventSourcing.sagaType,
                        "status" to eventSourcing.currentStatus.name,
                        "currentStepIndex" to eventSourcing.currentStepIndex,
                        "totalSteps" to eventSourcing.totalSteps,
                        "createdAt" to eventSourcing.createdAt.toString(),
                        "updatedAt" to eventSourcing.updatedAt.toString()
                    ))
            )

            log.info("Event history sent successfully for sagaId: {}", sagaId)

            // Saga가 이미 완료 상태면 SSE 연결 종료
            if (eventSourcing.currentStatus.name in listOf("COMPLETED", "FAILED", "COMPENSATION_COMPLETED", "COMPENSATION_FAILED")) {
                log.info("Saga already finished with status: {}, closing SSE connection for sagaId: {}", eventSourcing.currentStatus.name, sagaId)
                emitter.complete()
                complete(sagaId, emitter)
            }

        } catch (e: Exception) {
            log.error("Failed to send event history for sagaId: {}", sagaId, e)
        }
    }

    /**
     * Redis pub/sub 구독 시작
     */
    private fun subscribeToSagaEvents(sagaId: String) {
        val channel = "saga:events:$sagaId"
        val listener = MessageListener { message, _ ->
            handleRedisMessage(sagaId, message)
        }

        listeners[sagaId] = listener
        redisMessageListenerContainer.addMessageListener(listener, ChannelTopic(channel))

        log.info("Subscribed to Redis channel: {}", channel)
    }

    /**
     * Redis 메시지 처리 및 SSE 전송
     */
    private fun handleRedisMessage(sagaId: String, message: Message) {
        try {
            val eventData = String(message.body)
            log.debug("Received Redis message for sagaId {}: {}", sagaId, eventData)

            val emitterSet = emitters[sagaId] ?: return

            // 모든 클라이언트에게 이벤트 전송
            val deadEmitters = mutableSetOf<SseEmitter>()

            emitterSet.forEach { emitter ->
                try {
                    emitter.send(
                        SseEmitter.event()
                            .name("saga-event")
                            .data(eventData)
                    )
                } catch (e: Exception) {
                    log.warn("Failed to send SSE message to client for sagaId: {}", sagaId, e)
                    deadEmitters.add(emitter)
                }
            }

            // 실패한 emitter 제거
            deadEmitters.forEach { complete(sagaId, it) }

            // Saga 완료 상태 확인 모든 emitter 종료
            try {
                val eventMap = objectMapper.readValue(eventData, Map::class.java) as Map<*, *>
                val eventType = eventMap["eventType"] as? String

                if (eventType in listOf("SAGA_COMPLETED", "SAGA_FAILED", "SAGA_COMPENSATION_COMPLETED", "SAGA_COMPENSATION_FAILED")) {
                    log.info("Saga finished with eventType: {}, closing all SSE connections for sagaId: {}", eventType, sagaId)
                    emitterSet.toList().forEach { emitter ->
                        try {
                            emitter.complete()
                        } catch (e: Exception) {
                            log.warn("Failed to complete emitter for sagaId: {}", sagaId, e)
                        }
                    }
                    emitters.remove(sagaId)
                    unsubscribeFromSagaEvents(sagaId)
                }
            } catch (e: Exception) {
                log.debug("Could not parse event data for completion check: {}", e.message)
            }

        } catch (e: Exception) {
            log.error("Error handling Redis message for sagaId: {}", sagaId, e)
        }
    }

    /**
     * Emitter 제거
     */
    private fun complete(sagaId: String, emitter: SseEmitter) {
        emitter.complete()
        emitters[sagaId]?.remove(emitter)

        // 더 이상 emitter가 없으면 Redis 구독 해제
        if (emitters[sagaId]?.isEmpty() == true) {
            emitters.remove(sagaId)
            unsubscribeFromSagaEvents(sagaId)
        }

        log.debug("SSE emitter removed for sagaId: {}", sagaId)
    }

    /**
     * Redis pub/sub 구독 해제
     */
    private fun unsubscribeFromSagaEvents(sagaId: String) {
        listeners.remove(sagaId)?.let { listener ->
            redisMessageListenerContainer.removeMessageListener(listener)
            log.info("Unsubscribed from Redis channel: saga:events:{}", sagaId)
        }
    }

    /**
     * 이벤트 발행 (Saga 상태 변경 시 호출)
     */
    fun publishEvent(sagaId: String, eventData: Any) {
        try {
            val channel = "saga:events:$sagaId"
            val jsonData = objectMapper.writeValueAsString(eventData)
            redisTemplate.convertAndSend(channel, jsonData)
            log.debug("Published event to Redis channel {}: {}", channel, jsonData)
        } catch (e: Exception) {
            log.error("Failed to publish event for sagaId: {}", sagaId, e)
        }
    }

    /**
     * 새 이벤트를 EventStore에서 조회하여 SSE로 전송
     */
    fun publishNewEvent(sagaId: String, eventId: String) {
        try {
            val events = eventStoreService.getEventsBySagaId(sagaId)
            val latestEvent = events.find { it.eventId == eventId }

            if (latestEvent != null) {
                val eventData = mapOf(
                    "eventId" to latestEvent.eventId,
                    "sagaId" to latestEvent.sagaId,
                    "sequenceNumber" to latestEvent.sequenceNumber,
                    "eventType" to latestEvent.eventType.name,
                    "stepName" to latestEvent.stepName,
                    "stepIndex" to latestEvent.stepIndex,
                    "success" to latestEvent.success,
                    "errorMessage" to latestEvent.errorMessage,
                    "timestamp" to latestEvent.timestamp.toInstant().toEpochMilli(),
                    "payload" to objectMapper.readValue(latestEvent.payload, Map::class.java)
                )

                publishEvent(sagaId, eventData)
                log.debug("Published new event for sagaId: {}, eventId: {}", sagaId, eventId)
            } else {
                log.warn("Event not found: sagaId={}, eventId={}", sagaId, eventId)
            }
        } catch (e: Exception) {
            log.error("Failed to publish new event for sagaId: {}, eventId: {}", sagaId, eventId, e)
        }
    }
}

