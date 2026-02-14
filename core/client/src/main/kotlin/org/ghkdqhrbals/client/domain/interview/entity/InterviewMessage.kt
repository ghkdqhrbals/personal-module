package org.ghkdqhrbals.client.domain.interview.entity

import jakarta.persistence.*
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.LocalDateTime

@Entity
@Table(name = "interview_messages")
@EntityListeners(AuditingEntityListener::class)
class InterviewMessage(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    var session: InterviewSession? = null,

    @Column(name = "role", nullable = false)
    val role: String, // "user" or "assistant"

    @Column(name = "content", columnDefinition = "TEXT", nullable = false)
    val content: String,

    @Column(name = "turn_number", nullable = false)
    val turnNumber: Int,

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: LocalDateTime = LocalDateTime.now()
)
