package org.ghkdqhrbals.infra.event

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.ghkdqhrbals.model.event.EventStoreModel
import org.ghkdqhrbals.model.event.SagaEventType

class EventStoreRepositoryImpl(
    private val eventStoreJdbcRepository: EventStoreJdbcRepository,
    private val objectMapper: ObjectMapper
) : EventStoreRepository {
    override fun findMaxSequenceNumberBySagaId(sagaId: String): Long {
        return eventStoreJdbcRepository.findMaxSequenceNumberBySagaId(sagaId)
    }

    override fun findBySagaId(sagaId: String): List<EventStoreModel> {
        return eventStoreJdbcRepository.findBySagaIdOrderBySequenceNumberAsc(sagaId)
            .map { it.toModel() }
    }

    override fun findMaxSequenceNumber(sagaId: String): Long {
        return eventStoreJdbcRepository.findMaxSequenceNumberBySagaId(sagaId)
    }

    override fun findByEventType(sagaId: String, eventType: SagaEventType): List<EventStoreModel> {
        return eventStoreJdbcRepository.findBySagaIdAndEventType(sagaId, eventType)
            .map { it.toModel() }
    }

    override fun findByStepIndex(sagaId: String, stepIndex: Int): List<EventStoreModel> {
        return eventStoreJdbcRepository.findBySagaIdAndStepIndex(sagaId, stepIndex)
            .map { it.toModel() }
    }

    override fun save(event: EventStoreModel): EventStoreModel {
        val entity = event.toEntity()
        return eventStoreJdbcRepository.save(entity).toModel()
    }

    override fun saveAll(events: List<EventStoreModel>): List<EventStoreModel> {
        val entities = events.map { it.toEntity() }
        return eventStoreJdbcRepository.saveAll(entities).map { it.toModel() }
    }

    override fun findBySagaIdOrderBySequenceNumberAsc(sagaId: String): List<EventStoreModel> {
        return eventStoreJdbcRepository.findBySagaIdOrderBySequenceNumberAsc(sagaId).map { it.toModel() }
    }

    private fun EventStoreEntity.toModel(): EventStoreModel {
        return EventStoreModel(
            id = this.id,
            eventId = this.eventId,
            sagaId = this.sagaId,
            sequenceNumber = this.sequenceNumber,
            eventType = this.eventType,
            timestamp = this.timestamp,
            payload = parsePayload(this.payload),
            stepName = this.stepName,
            stepIndex = this.stepIndex,
            success = this.success,
            errorMessage = this.errorMessage
        )
    }

    private fun EventStoreModel.toEntity(): EventStoreEntity {
        return EventStoreEntity(
            id = this.id,
            eventId = this.eventId,
            sagaId = this.sagaId,
            sequenceNumber = this.sequenceNumber,
            eventType = this.eventType,
            timestamp = this.timestamp,
            payload = objectMapper.writeValueAsString(this.payload),
            stepName = this.stepName,
            stepIndex = this.stepIndex,
            success = this.success,
            errorMessage = this.errorMessage
        )
    }

    private fun parsePayload(payload: String): Map<String, Any> {
        return try {
            objectMapper.readValue(payload)
        } catch (e: Exception) {
            emptyMap()
        }
    }
}
