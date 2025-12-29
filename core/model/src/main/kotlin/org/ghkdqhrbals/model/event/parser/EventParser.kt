package org.ghkdqhrbals.model.event.parser

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.ghkdqhrbals.model.event.SagaEventType
import org.ghkdqhrbals.model.event.SagaResponseEvent
import org.springframework.stereotype.Component
import java.util.*

@Component
class EventParser(
    private val objectMapper: ObjectMapper
) {
    @Suppress("UNCHECKED_CAST")
    fun parseEvent(message: String): SagaResponseEvent {
        val map: Map<String, Any> = objectMapper.readValue(message)

        return SagaResponseEvent(
            eventId = map["eventId"] as? String ?: UUID.randomUUID().toString(),
            sagaId = map["sagaId"] as String,
            eventType = map["eventType"] as? SagaEventType ?: SagaEventType.SAGA_RESPONSE,
            sourceService = map["sourceService"] as? String ?: "unknown",
            success = map["success"] as? Boolean ?: true,
            errorMessage = map["errorMessage"] as? String,
            payload = map["payload"] // Any? 타입으로 직접 할당
        )
    }
}