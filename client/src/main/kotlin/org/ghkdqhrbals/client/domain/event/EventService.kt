package org.ghkdqhrbals.client.domain.event

import com.fasterxml.jackson.databind.ObjectMapper
import org.ghkdqhrbals.client.config.log.logger
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime
import org.ghkdqhrbals.client.config.Jackson

/**
 * 이벤트 저장소 서비스
 */
@Service
class EventService(
    private val eventStoreRepository: EventStoreRepository
) {
    // Jackson 전역 설정을 사용 (JavaTimeModule 등록됨)
    val objectMapper: ObjectMapper = Jackson.getMapper()

    /**
     * 이벤트 저장
     */
    @Transactional
    fun save(event: DomainEvent) {
        val payload = objectMapper.writeValueAsString(event)
        val metadata = objectMapper.writeValueAsString(event.metadata)

        val entity = EventStoreEntity(
            eventId = event.eventId,
            aggregateId = event.aggregateId,
            eventType = event.eventType,
            payload = payload,
            timestamp = event.timestamp,
            version = event.version,
            metadata = metadata
        )

        eventStoreRepository.save(entity)
        logger().info(
            "[EventStore] Saved event: eventId=${event.eventId}, " +
            "type=${event.eventType}, aggregateId=${event.aggregateId}, version=${event.version}"
        )
    }

    /**
     * 여러 이벤트 일괄 저장
     */
    @Transactional
    fun saveAll(events: List<DomainEvent>) {
        val entities = events.map { event ->
            val payload = objectMapper.writeValueAsString(event)
            val metadata = objectMapper.writeValueAsString(event.metadata)

            EventStoreEntity(
                eventId = event.eventId,
                aggregateId = event.aggregateId,
                eventType = event.eventType,
                payload = payload,
                timestamp = event.timestamp,
                version = event.version,
                metadata = metadata
            )
        }

        eventStoreRepository.saveAll(entities)
        logger().info("[EventStore] Saved ${events.size} events in batch")
    }

    /**
     * Aggregate의 모든 이벤트 조회
     */
    fun getEvents(aggregateId: String): List<EventStoreEntity> {
        val events = eventStoreRepository.findByAggregateIdOrderByVersionAsc(aggregateId)
        logger().info(
            "[EventStore] Retrieved ${events.size} events for aggregateId=$aggregateId: " +
            events.joinToString(", ") { "${it.eventType}(v${it.version})" }
        )
        return events
    }

    /**
     * Aggregate의 특정 버전 이후 이벤트 조회
     */
    fun getEventsAfterVersion(aggregateId: String, version: Long): List<EventStoreEntity> {
        return eventStoreRepository.findByAggregateIdAndVersionGreaterThanEqualOrderByVersionAsc(
            aggregateId, version
        )
    }

    /**
     * 이벤트 타입으로 조회
     */
    fun getEventsByType(eventType: String): List<EventStoreEntity> {
        return eventStoreRepository.findByEventTypeOrderByTimestampDesc(eventType)
    }

    /**
     * 특정 시간 이후 이벤트 조회
     */
    fun getEventsAfter(timestamp: OffsetDateTime): List<EventStoreEntity> {
        return eventStoreRepository.findByTimestampAfterOrderByTimestampAsc(timestamp)
    }

    /**
     * Aggregate의 최신 버전 조회
     */
    fun getLatestVersion(aggregateId: String): Long {
        return eventStoreRepository.findLatestVersion(aggregateId) ?: 0L
    }

    /**
     * 이벤트를 도메인 객체로 역직렬화
     */
    final inline fun <reified T : DomainEvent> deserialize(entity: EventStoreEntity): T {
        return objectMapper.readValue(entity.payload, T::class.java)
    }
}

