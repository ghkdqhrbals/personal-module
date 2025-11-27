package org.ghkdqhrbals.client.domain.paper.entity.repository

import org.ghkdqhrbals.client.domain.paper.entity.PaperSubscribe
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface PaperSubscribeRepository : JpaRepository<PaperSubscribe, Long> {

    fun findByPaperId(paperId: Long): List<PaperSubscribe>

    fun findBySubscribeId(subscribeId: Long): List<PaperSubscribe>

    @Query("SELECT ps FROM PaperSubscribe ps WHERE ps.paper.id = :paperId AND ps.matchScore >= :minScore")
    fun findHighRelevancePapersByPaperId(paperId: Long, minScore: Double = 0.7): List<PaperSubscribe>

    @Query("SELECT ps FROM PaperSubscribe ps WHERE ps.subscribe.id = :subscribeId AND ps.matchScore >= :minScore ORDER BY ps.matchScore DESC")
    fun findHighRelevancePapersBySubscribeId(subscribeId: Long, minScore: Double = 0.7): List<PaperSubscribe>

    @Query("""
        SELECT ps FROM PaperSubscribe ps 
        WHERE ps.subscribe.id IN (
            SELECT us.subscribe.id FROM UserSubscribe us 
            WHERE us.user.id = :userId AND us.unsubscribedAt IS NULL
        )
        AND ps.matchScore >= :minScore
        ORDER BY ps.matchedAt DESC
    """)
    fun findRecommendedPapersForUser(userId: Long, minScore: Double = 0.5): List<PaperSubscribe>
}

