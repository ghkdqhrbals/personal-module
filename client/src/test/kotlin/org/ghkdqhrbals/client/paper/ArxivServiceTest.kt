package org.ghkdqhrbals.client.paper

import io.mockk.*
import org.ghkdqhrbals.client.eventsourcing.domain.PaperSearchAndStoreEvent
import org.ghkdqhrbals.client.eventsourcing.publisher.EventPublisher
import org.ghkdqhrbals.client.paper.entity.PaperEntity
import org.ghkdqhrbals.client.paper.repository.PaperRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.redis.core.HashOperations
import org.springframework.data.redis.core.StringRedisTemplate
import java.time.LocalDate
import java.util.concurrent.TimeUnit

class ArxivServiceTest {

    private lateinit var eventPublisher: EventPublisher
    private lateinit var paperRepository: PaperRepository
    private lateinit var redisTemplate: StringRedisTemplate
    private lateinit var hashOperations: HashOperations<String, String, String>
    private lateinit var arxivService: ArxivService

    @BeforeEach
    fun setUp() {
        eventPublisher = mockk()
        paperRepository = mockk()
        redisTemplate = mockk()
        hashOperations = mockk()

        every { redisTemplate.opsForHash<String, String>() } returns hashOperations

        arxivService = ArxivService(eventPublisher, paperRepository, redisTemplate)
    }

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    fun `이벤트가 성공적으로 발행되면 eventId를 반환한다`() {
        every { eventPublisher.publish(any<PaperSearchAndStoreEvent>()) } just Runs

        val eventId = arxivService.searchAsync(
            query = "machine learning",
            categories = listOf("cs.AI"),
            maxResults = 10,
            page = 0,
            fromDate = "2024-01-01",
            summarize = true
        )

        assertNotNull(eventId)
        verify(exactly = 1) { eventPublisher.publish(any<PaperSearchAndStoreEvent>()) }
    }

    @Test
    fun `올바른 파라미터로 이벤트를 발행한다`() {
        val capturedEvent = slot<PaperSearchAndStoreEvent>()
        every { eventPublisher.publish(capture(capturedEvent)) } just Runs

        arxivService.searchAsync(
            query = "deep learning",
            categories = listOf("cs.LG", "cs.AI"),
            maxResults = 20,
            page = 1,
            fromDate = "2024-06-01",
            summarize = false
        )

        val event = capturedEvent.captured
        assertEquals("deep learning", event.query)
        assertEquals(listOf("cs.LG", "cs.AI"), event.categories)
        assertEquals(20, event.maxResults)
        assertEquals(1, event.page)
        assertEquals("2024-06-01", event.fromDate)
        assertEquals(false, event.shouldSummarize)
        assertEquals(1, event.ver)
        assertNotNull(event.meta["requestedAt"])
    }

    @Test
    fun `null 파라미터로 호출시 기본값으로 이벤트를 발행한다`() {
        val capturedEvent = slot<PaperSearchAndStoreEvent>()
        every { eventPublisher.publish(capture(capturedEvent)) } just Runs

        arxivService.searchAsync()

        val event = capturedEvent.captured
        assertNull(event.query)
        assertNull(event.categories)
        assertEquals(10, event.maxResults)
        assertEquals(0, event.page)
        assertNull(event.fromDate)
        assertEquals(true, event.shouldSummarize)
    }

    @Test
    fun `이벤트 발행 실패시 Redis에 실패 상태를 저장한다`() {
        val exception = RuntimeException("Publishing failed")
        val capturedKey = slot<String>()
        val capturedMap = slot<Map<String, String>>()

        every { eventPublisher.publish(any<PaperSearchAndStoreEvent>()) } throws exception
        every { hashOperations.putAll(capture(capturedKey), capture(capturedMap)) } just Runs
        every { redisTemplate.expire(any<String>(), any<Long>(), any<TimeUnit>()) } returns true

        val eventId = arxivService.searchAsync(query = "test")

        assertNotNull(eventId)
        assertEquals("search:$eventId:progress", capturedKey.captured)
        assertEquals("FAILED", capturedMap.captured["status"])
        assertEquals("Publishing failed", capturedMap.captured["error"])
        verify { redisTemplate.expire("search:$eventId:progress", 3600, TimeUnit.SECONDS) }
    }

    @Test
    fun `진행 데이터가 없으면 NOT_FOUND 상태를 반환한다`() {
        every { hashOperations.entries(any()) } returns emptyMap()

        val status = arxivService.getSearchStatus("non-existent-id")

        assertEquals(SearchStatus.NOT_FOUND, status.status)
        assertEquals("non-existent-id", status.eventId)
        assertNull(status.batch)
        assertNull(status.summary)
        assertNull(status.papers)
    }

    @Test
    fun `검색이 시작되지 않았으면 PENDING 상태를 반환한다`() {
        val progressData = mapOf(
            "status" to "PENDING",
            "total" to "0",
            "completed" to "0",
            "failed" to "0"
        )
        every { hashOperations.entries("search:test-id:progress") } returns progressData

        val status = arxivService.getSearchStatus("test-id")

        assertEquals(SearchStatus.PENDING, status.status)
        assertEquals("test-id", status.eventId)
        assertEquals(0, status.batch?.totalPapers)
        assertEquals(0, status.summary?.total)
        assertEquals(0, status.summary?.completed)
        assertEquals(0, status.summary?.failed)
        assertEquals(0, status.summary?.processing)
        assertEquals(0.0, status.summary?.progressPercent)
        assertFalse(status.summary?.isDone ?: true)
        assertNull(status.papers)
    }

    @Test
    fun `진행 중일 때 올바른 진행률을 계산하여 IN_PROGRESS 상태를 반환한다`() {
        val progressData = mapOf(
            "status" to "IN_PROGRESS",
            "total" to "100",
            "completed" to "60",
            "failed" to "10"
        )
        every { hashOperations.entries("search:test-id:progress") } returns progressData
        every { paperRepository.findTop100ByOrderBySearchDateDesc() } returns listOf(createMockPaperEntity())

        val status = arxivService.getSearchStatus("test-id")

        assertEquals(SearchStatus.IN_PROGRESS, status.status)
        assertEquals(100, status.batch?.totalPapers)
        assertEquals(100, status.summary?.total)
        assertEquals(60, status.summary?.completed)
        assertEquals(10, status.summary?.failed)
        assertEquals(30, status.summary?.processing)
        assertEquals(70.0, status.summary?.progressPercent)
        assertFalse(status.summary?.isDone ?: true)
        assertNotNull(status.papers)
        assertEquals(1, status.papers?.size)
    }

    @Test
    fun `모든 논문이 처리되면 COMPLETED 상태를 반환한다`() {
        val progressData = mapOf(
            "status" to "COMPLETED",
            "total" to "50",
            "completed" to "50",
            "failed" to "0"
        )
        every { hashOperations.entries("search:test-id:progress") } returns progressData
        every { paperRepository.findTop100ByOrderBySearchDateDesc() } returns listOf(createMockPaperEntity())

        val status = arxivService.getSearchStatus("test-id")

        assertEquals(SearchStatus.COMPLETED, status.status)
        assertEquals(50, status.summary?.total)
        assertEquals(50, status.summary?.completed)
        assertEquals(0, status.summary?.failed)
        assertEquals(0, status.summary?.processing)
        assertEquals(100.0, status.summary?.progressPercent)
        assertTrue(status.summary?.isDone ?: false)
    }

    @Test
    fun `검색이 실패하면 FAILED 상태를 반환한다`() {
        val progressData = mapOf(
            "status" to "FAILED",
            "total" to "10",
            "completed" to "5",
            "failed" to "5"
        )
        every { hashOperations.entries("search:test-id:progress") } returns progressData
        every { paperRepository.findTop100ByOrderBySearchDateDesc() } returns emptyList()

        val status = arxivService.getSearchStatus("test-id")

        assertEquals(SearchStatus.FAILED, status.status)
        assertEquals(10, status.summary?.total)
        assertEquals(5, status.summary?.completed)
        assertEquals(5, status.summary?.failed)
        assertEquals(0, status.summary?.processing)
        assertTrue(status.summary?.isDone ?: false)
    }

    @Test
    fun `진행 필드가 누락되어도 정상적으로 처리한다`() {
        val progressData = mapOf("status" to "IN_PROGRESS")
        every { hashOperations.entries("search:test-id:progress") } returns progressData

        val status = arxivService.getSearchStatus("test-id")

        assertEquals(SearchStatus.IN_PROGRESS, status.status)
        assertEquals(0, status.batch?.totalPapers)
        assertEquals(0, status.summary?.total)
        assertEquals(0, status.summary?.completed)
        assertEquals(0, status.summary?.failed)
        assertEquals(0.0, status.summary?.progressPercent)
    }

    @Test
    fun `일부 논문이 실패해도 진행률을 올바르게 계산한다`() {
        val progressData = mapOf(
            "status" to "IN_PROGRESS",
            "total" to "200",
            "completed" to "150",
            "failed" to "25"
        )
        every { hashOperations.entries("search:test-id:progress") } returns progressData
        every { paperRepository.findTop100ByOrderBySearchDateDesc() } returns emptyList()

        val status = arxivService.getSearchStatus("test-id")

        assertEquals(87.5, status.summary?.progressPercent)
        assertEquals(25, status.summary?.processing)
    }

    @Test
    fun `처리 중인 논문 수가 음수가 되지 않도록 보장한다`() {
        val progressData = mapOf(
            "status" to "IN_PROGRESS",
            "total" to "10",
            "completed" to "8",
            "failed" to "5"
        )
        every { hashOperations.entries("search:test-id:progress") } returns progressData
        every { paperRepository.findTop100ByOrderBySearchDateDesc() } returns emptyList()

        val status = arxivService.getSearchStatus("test-id")

        assertTrue(status.summary?.processing!! >= 0)
        assertEquals(0, status.summary?.processing)
    }

    @Test
    fun `논문 엔티티를 논문 모델로 올바르게 매핑한다`() {
        val progressData = mapOf(
            "status" to "COMPLETED",
            "total" to "1",
            "completed" to "1",
            "failed" to "0"
        )
        val paperEntity = createMockPaperEntity(
            title = "Test Paper",
            author = "Author One, Author Two",
            journal = "Test Journal",
            url = "https://arxiv.org/test",
            summary = "Test summary",
            novelty = "High novelty",
            impactFactor = 5.5
        )
        every { hashOperations.entries("search:test-id:progress") } returns progressData
        every { paperRepository.findTop100ByOrderBySearchDateDesc() } returns listOf(paperEntity)

        val status = arxivService.getSearchStatus("test-id")

        assertEquals(1, status.papers?.size)
        val paper = status.papers?.first()
        assertEquals("Test Paper", paper?.title)
        assertEquals(listOf("Author One", "Author Two"), paper?.authors)
        assertEquals("Test Journal", paper?.journal)
        assertEquals("https://arxiv.org/test", paper?.url)
        assertEquals("Test summary", paper?.summary)
        assertEquals("High novelty", paper?.novelty)
        assertEquals(5.5, paper?.impactFactor)
    }

    @Test
    fun `저자 필드가 null인 논문 엔티티를 정상적으로 처리한다`() {
        val progressData = mapOf(
            "status" to "COMPLETED",
            "total" to "1",
            "completed" to "1",
            "failed" to "0"
        )
        val paperEntity = createMockPaperEntity(author = null)
        every { hashOperations.entries("search:test-id:progress") } returns progressData
        every { paperRepository.findTop100ByOrderBySearchDateDesc() } returns listOf(paperEntity)

        val status = arxivService.getSearchStatus("test-id")

        assertEquals(emptyList<String>(), status.papers?.first()?.authors)
    }

    @Test
    fun `총 논문 수가 0이면 null papers를 반환한다`() {
        val progressData = mapOf(
            "status" to "PENDING",
            "total" to "0",
            "completed" to "0",
            "failed" to "0"
        )
        every { hashOperations.entries("search:test-id:progress") } returns progressData

        val status = arxivService.getSearchStatus("test-id")

        assertNull(status.papers)
    }

    @Test
    fun `여러 번 호출시 고유한 이벤트 ID를 생성한다`() {
        every { eventPublisher.publish(any<PaperSearchAndStoreEvent>()) } just Runs

        val eventId1 = arxivService.searchAsync(query = "test1")
        val eventId2 = arxivService.searchAsync(query = "test2")
        val eventId3 = arxivService.searchAsync(query = "test3")

        assertNotEquals(eventId1, eventId2)
        assertNotEquals(eventId2, eventId3)
        assertNotEquals(eventId1, eventId3)
    }

    private fun createMockPaperEntity(
        title: String = "Mock Paper",
        author: String? = "Mock Author",
        journal: String? = "Mock Journal",
        url: String? = "https://example.com",
        summary: String? = "Mock summary",
        novelty: String? = "Mock novelty",
        impactFactor: Double? = 1.0
    ): PaperEntity {
        return PaperEntity(
            id = 1L,
            arxivId = "test-id",
            title = title,
            author = author,
            journal = journal,
            publishedAt = LocalDate.now(),
            url = url,
            impactFactor = impactFactor,
            summary = summary,
            novelty = novelty,
            searchDate = LocalDate.now()
        )
    }
}

