package org.ghkdqhrbals.client.domain.event

import com.fasterxml.jackson.databind.ObjectMapper
import org.ghkdqhrbals.client.config.Jackson
import org.ghkdqhrbals.client.config.log.logger
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.redis.connection.stream.StreamRecords
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

/**
 * 도메인 이벤트 발행자 (Redis Stream 기반)
 * 이벤트를 EventStore에 저장하고 이벤트 타입별 Redis Stream으로 발행
 */
@Service
class EventPublisher(
    private val eventService: EventService,
    private val redisTemplate: StringRedisTemplate,
    @Value("\${redis.stream.events.paper-search-and-store:domain:events:paper-search-and-store}") private val paperSearchAndStoreStream: String,
    @Value("\${redis.stream.events.summary:domain:events:summary}") private val summaryStream: String
) {
    private val mapper: ObjectMapper = Jackson.getMapper()

    /**
     * 단일 이벤트 발행
     * 1. EventStore에 저장
     * 2. Redis Stream으로 발행
     */
    @Transactional
    fun publish(event: DomainEvent) {
        // 1. 이벤트 저장 (Event Sourcing)
        eventService.save(event)

        // 2. Redis Stream으로 발행
        publishToStream(event)

        logger().info(
            "[EventPublisher] Published to stream: type=${event.eventType}, " +
            "aggregateId=${event.aggregateId}, eventId=${event.eventId}"
        )
    }

    /**
     * 여러 이벤트 일괄 발행
     */
    @Transactional
    fun publishAll(events: List<DomainEvent>) {
        if (events.isEmpty()) return

        // 1. 모든 이벤트 저장
        eventService.saveAll(events)

        // 2. 모든 이벤트를 Stream으로 발행
        events.forEach { event ->
            publishToStream(event)
        }

        logger().info("[EventPublisher] Published ${events.size} events to stream")
    }

    /**
     * Redis Stream으로 이벤트 발행
     */
    private fun publishToStream(event: DomainEvent) {
        try {
            // 이벤트 타입별로 적절한 스트림 선택
            val streamKey = getStreamKeyForEvent(event.eventType)

            val payload = mapper.writeValueAsString(event)

            val record = StreamRecords.newRecord()
                .`in`(streamKey)
                .ofMap(mapOf(
                    "eventId" to event.eventId,
                    "eventType" to event.eventType,
                    "aggregateId" to event.aggregateId,
                    "payload" to payload,
                    "timestamp" to event.timestamp.toString(),
                    "version" to event.version.toString()
                ))

            val recordId = redisTemplate.opsForStream<String, String>().add(record)

            logger().debug(
                "[EventPublisher] Stream record added: stream=$streamKey, recordId=$recordId, " +
                "eventType=${event.eventType}, eventId=${event.eventId}"
            )
        } catch (e: Exception) {
            logger().error(
                "[EventPublisher] Failed to publish to stream: " +
                "eventType=${event.eventType}, eventId=${event.eventId}", e
            )
            throw e
        }
    }

    /**
     * 이벤트 타입에 따라 적절한 스트림 키 반환
     */
    private fun getStreamKeyForEvent(eventType: String): String {
        return when (eventType) {
            "PaperSearchAndStore" -> paperSearchAndStoreStream
            "Summary" -> summaryStream
            else -> {
                logger().warn("[EventPublisher] Unknown event type: $eventType, using paper-search-and-store stream")
                paperSearchAndStoreStream
            }
        }
    }
}

