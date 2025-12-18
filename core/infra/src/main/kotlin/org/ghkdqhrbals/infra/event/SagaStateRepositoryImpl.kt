package org.ghkdqhrbals.infra.event

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import jakarta.persistence.EntityManager
import org.ghkdqhrbals.model.event.SagaStateModel
import org.ghkdqhrbals.model.event.SagaStatus

class SagaStateRepositoryImpl(
    private val sagaStateJdbcRepository: SagaStateJdbcRepository,
    private val objectMapper: ObjectMapper,
    private val em: EntityManager
) : SagaStateRepository {

    override fun findById(sagaId: String): SagaStateModel? {
        return sagaStateJdbcRepository.findById(sagaId).orElse(null)?.toModel()
    }

    override fun findByStatus(status: SagaStatus): List<SagaStateModel> {
        return sagaStateJdbcRepository.findByStatus(status).map { it.toModel() }
    }

    override fun findBySagaTypeAndStatus(sagaType: String, status: SagaStatus): List<SagaStateModel> {
        return sagaStateJdbcRepository.findBySagaTypeAndStatus(sagaType, status).map { it.toModel() }
    }

    override fun findActiveSagas(): List<SagaStateModel> {
        return sagaStateJdbcRepository.findActiveSagas().map { it.toModel() }
    }

    override fun save(state: SagaStateModel): SagaStateModel {
        val entity = state.toEntity()
        return sagaStateJdbcRepository.save(entity).toModel()
    }

    override fun deleteById(sagaId: String) {
        sagaStateJdbcRepository.deleteById(sagaId)
    }

    override fun insert(state: SagaStateModel): SagaStateModel {
        em.persist(state.toEntity())
        return state
    }

    private fun SagaStateEntity.toModel(): SagaStateModel {
        return SagaStateModel(
            sagaId = this.sagaId,
            sagaType = this.sagaType,
            status = this.status,
            currentStepIndex = this.currentStepIndex,
            totalSteps = this.totalSteps,
            sagaData = parseSagaData(this.sagaData),
            createdAt = this.createdAt,
            updatedAt = this.updatedAt
        )
    }

    private fun SagaStateModel.toEntity(): SagaStateEntity {
        return SagaStateEntity(
            sagaId = this.sagaId,
            sagaType = this.sagaType,
            status = this.status,
            currentStepIndex = this.currentStepIndex,
            totalSteps = this.totalSteps,
            sagaData = this.sagaData?.let { objectMapper.writeValueAsString(it) },
            createdAt = this.createdAt,
            updatedAt = this.updatedAt
        )
    }

    private fun parseSagaData(sagaData: String?): Map<String, Any>? {
        return sagaData?.let {
            try {
                objectMapper.readValue(it)
            } catch (e: Exception) {
                null
            }
        }
    }
}

