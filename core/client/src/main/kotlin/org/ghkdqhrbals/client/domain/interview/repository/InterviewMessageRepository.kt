package org.ghkdqhrbals.client.domain.interview.repository

import org.ghkdqhrbals.client.domain.interview.entity.InterviewMessage
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface InterviewMessageRepository : JpaRepository<InterviewMessage, Long> {
    fun findBySessionIdOrderByTurnNumberAsc(sessionId: Long): List<InterviewMessage>
}
