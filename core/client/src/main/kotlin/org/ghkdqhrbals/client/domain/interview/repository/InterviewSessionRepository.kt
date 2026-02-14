package org.ghkdqhrbals.client.domain.interview.repository

import org.ghkdqhrbals.client.domain.interview.entity.InterviewSession
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface InterviewSessionRepository : JpaRepository<InterviewSession, Long>
