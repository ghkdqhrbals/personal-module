package org.ghkdqhrbals.client.eventsourcing.api

import org.ghkdqhrbals.client.eventsourcing.store.EventStore
import org.ghkdqhrbals.client.eventsourcing.store.EventStoreEntity
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.Instant

/**
 * 이벤트 소싱 API 컨트롤러
 */
@RestController
@RequestMapping("/api/events")
class EventSourcingController(
    private val eventStore: EventStore
) {

    /**
     * 이벤트 히스토리 조회
     */
    @GetMapping("/{aggregateId}/history")
    fun getEventHistory(@PathVariable aggregateId: String): ResponseEntity<List<EventResponse>> {
        val events = eventStore.getEvents(aggregateId)
        val response = events.map { EventResponse.from(it) }
        return ResponseEntity.ok(response)
    }

    /**
     * 특정 이벤트 타입으로 조회
     */
    @GetMapping("/types/{eventType}")
    fun getEventsByType(@PathVariable eventType: String): ResponseEntity<List<EventResponse>> {
        val events = eventStore.getEventsByType(eventType)
        val response = events.map { EventResponse.from(it) }
        return ResponseEntity.ok(response)
    }

    /**
     * 특정 시간 이후 이벤트 조회
     */
    @GetMapping("/since")
    fun getEventsSince(@RequestParam timestamp: String): ResponseEntity<List<EventResponse>> {
        val instant = Instant.parse(timestamp)
        val events = eventStore.getEventsAfter(instant)
        val response = events.map { EventResponse.from(it) }
        return ResponseEntity.ok(response)
    }

    /**
     * Aggregate의 최신 버전 조회
     */
    @GetMapping("/aggregates/{aggregateId}/version")
    fun getLatestVersion(@PathVariable aggregateId: String): ResponseEntity<VersionResponse> {
        val version = eventStore.getLatestVersion(aggregateId)
        return ResponseEntity.ok(VersionResponse(aggregateId, version))
    }
}

/**
 * 이벤트 응답 DTO
 */
data class EventResponse(
    val eventId: String,
    val aggregateId: String,
    val eventType: String,
    val timestamp: Instant,
    val version: Long,
    val payload: String,
    val metadata: String?
) {
    companion object {
        fun from(entity: EventStoreEntity): EventResponse {
            return EventResponse(
                eventId = entity.eventId,
                aggregateId = entity.aggregateId,
                eventType = entity.eventType,
                timestamp = entity.timestamp,
                version = entity.version,
                payload = entity.payload,
                metadata = entity.metadata
            )
        }
    }
}

/**
 * 버전 응답 DTO
 */
data class VersionResponse(
    val aggregateId: String,
    val latestVersion: Long
)

