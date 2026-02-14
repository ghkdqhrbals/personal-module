package org.ghkdqhrbals.client.domain.interview.entity

import jakarta.persistence.*
import org.ghkdqhrbals.client.domain.interview.dto.InterviewStatus
import org.springframework.data.annotation.CreatedDate
import org.springframework.data.annotation.LastModifiedDate
import org.springframework.data.jpa.domain.support.AuditingEntityListener
import java.time.LocalDateTime

@Entity
@Table(name = "interview_sessions")
@EntityListeners(AuditingEntityListener::class)
class InterviewSession(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long? = null,

    @Column(name = "candidate_name")
    var candidateName: String? = null,

    @Column(name = "cv_text", columnDefinition = "TEXT")
    var cvText: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    var status: InterviewStatus = InterviewStatus.CREATED,

    @Column(name = "opening_question", columnDefinition = "TEXT")
    var openingQuestion: String? = null,

    @OneToMany(mappedBy = "session", cascade = [CascadeType.ALL], orphanRemoval = true)
    val messages: MutableList<InterviewMessage> = mutableListOf(),

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    var createdAt: LocalDateTime = LocalDateTime.now(),

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    var updatedAt: LocalDateTime = LocalDateTime.now()
) {
    fun addMessage(message: InterviewMessage) {
        messages.add(message)
        message.session = this
    }
}
