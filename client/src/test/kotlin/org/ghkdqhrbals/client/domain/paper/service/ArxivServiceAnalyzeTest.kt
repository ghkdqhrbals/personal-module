package org.ghkdqhrbals.client.domain.paper.service

import org.ghkdqhrbals.client.domain.event.EventPublisher
import org.ghkdqhrbals.client.domain.event.PaperSearchAndStoreEvent
import org.ghkdqhrbals.client.domain.paper.entity.PaperEntity
import org.ghkdqhrbals.client.domain.paper.entity.repository.PaperRepository
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.mockito.Mockito.*
import org.springframework.data.redis.core.StringRedisTemplate

/**
 * ArxivService.analyze() 유닛 테스트
 * - Mock을 사용한 빠른 단위 테스트
 * - HTTP 호출 모킹 (실제 API 호출 없음)
 * - 비즈니스 로직만 검증
 */
class ArxivServiceAnalyzeTest {

    private val eventPublisher = mock(EventPublisher::class.java)
    private val paperRepository = mock(PaperRepository::class.java)
    private val redisTemplate = mock(StringRedisTemplate::class.java)
    private val arxivHttpClient = mock(ArxivHttpClient::class.java)

    private val service = ArxivService(
        eventPublisher,
        paperRepository,
        redisTemplate,
        arxivHttpClient
    )

    @Test
    fun `신규 논문이 존재하면 저장 후 매핑된 결과를 반환한다`() {
        // Given
        val event = PaperSearchAndStoreEvent(
            searchEventId = "event-1",
            query = "ai",
            categories = null,
            maxResults = 3,
            page = 0,
            fromDate = null,
            shouldSummarize = true,
            ver = 1,
            meta = emptyMap()
        )

        val paper1 = ArxivPaper(rawArxivId = "A1", title = "t1", url = "u1")
        val paper2 = ArxivPaper(rawArxivId = "A2", title = "t2", url = "u2")
        val paper3 = ArxivPaper(rawArxivId = "A3", title = "t3", url = "u3")

        `when`(arxivHttpClient.search(event))
            .thenReturn(listOf(paper1, paper2, paper3))

        // 기존 DB에는 A1 만 존재한다고 가정
        val existing = PaperEntity(
            id = 10,
            arxivId = "A1",
            title = "t1",
            url = "u1"
        )
        `when`(paperRepository.findAllByArxivIdIn(listOf("A1", "A2", "A3")))
            .thenReturn(listOf(existing))

        // 신규 A2, A3 저장 시 반환되는 엔티티 Mock
        val savedEntities = listOf(
            PaperEntity(id = 20, arxivId = "A2", title = "t2", url = "u2"),
            PaperEntity(id = 30, arxivId = "A3", title = "t3", url = "u3"),
        )
        `when`(paperRepository.saveAll(anyList()))
            .thenReturn(savedEntities)

        // When
        val result = service.analyze(event)

        // Then
        assertNotNull(result)
        assertEquals(3, result!!.size)

        // 기존 논문
        assertEquals(existing, result[paper1])

        // 신규 논문이 saveAll 결과로 채워지는지 확인
        assertEquals(savedEntities[0], result[paper2])
        assertEquals(savedEntities[1], result[paper3])

        // Repository save 호출 검증
        verify(paperRepository, times(1)).saveAll(anyList())
    }
}