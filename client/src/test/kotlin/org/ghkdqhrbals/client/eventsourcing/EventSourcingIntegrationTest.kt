package org.ghkdqhrbals.client.eventsourcing

import org.ghkdqhrbals.client.eventsourcing.domain.*
import org.ghkdqhrbals.client.eventsourcing.publisher.EventPublisher
import org.ghkdqhrbals.client.eventsourcing.store.EventStore
import org.ghkdqhrbals.client.eventsourcing.projection.PaperProjectionService
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.test.context.ActiveProfiles
import java.util.UUID
import org.assertj.core.api.Assertions.assertThat

/**
 * 이벤트 소싱 통합 테스트
 */
@SpringBootTest
@ActiveProfiles("test")
class EventSourcingIntegrationTest {

    @Autowired
    private lateinit var eventPublisher: EventPublisher

    @Autowired
    private lateinit var eventStore: EventStore

    @Autowired
    private lateinit var projectionService: PaperProjectionService

    @Test
    fun `논문 발견 이벤트가 정상적으로 저장되고 프로젝션된다`() {
        // Given
        val paperId = UUID.randomUUID().toString()
        val event = PaperDiscoveredEvent(
            paperId = paperId,
            arxivId = paperId,
            title = "Test Paper",
            authors = listOf("Test Author"),
            abstract = "Test abstract",
            publishedDate = "2025-11-17",
            url = "https://example.com",
            ver = 1
        )

        // When
        eventPublisher.publish(event)

        // Then
        val events = eventStore.getEvents(paperId)
        assertThat(events).hasSize(1)
        assertThat(events[0].eventType).isEqualTo("PaperDiscovered")
        assertThat(events[0].aggregateId).isEqualTo(paperId)
        assertThat(events[0].version).isEqualTo(1)

        // Projection 확인 (이벤트 핸들러가 비동기이므로 잠시 대기)
        Thread.sleep(1000)
        val projection = projectionService.projectPaperState(paperId)
        assertThat(projection).isNotNull
        assertThat(projection?.title).isEqualTo("Test Paper")
        assertThat(projection?.authors).containsExactly("Test Author")
    }

    @Test
    fun `요약 완료 이벤트가 프로젝션에 반영된다`() {
        // Given
        val paperId = UUID.randomUUID().toString()

        // 논문 발견 이벤트
        val discoveredEvent = PaperDiscoveredEvent(
            paperId = paperId,
            arxivId = paperId,
            title = "Test Paper",
            authors = listOf("Author 1"),
            abstract = "Test abstract",
            publishedDate = "2025-11-17",
            url = "https://example.com",
            ver = 1
        )
        eventPublisher.publish(discoveredEvent)

        // 요약 완료 이벤트
        val summaryEvent = SummaryCompletedEvent(
            paperId = paperId,
            arxivId = paperId,
            summary = "Test summary",
            novelty = "Test novelty",
            journalName = "Test Journal",
            impactFactor = 10.0,
            ver = 2
        )

        // When
        eventPublisher.publish(summaryEvent)

        // Then
        val events = eventStore.getEvents(paperId)
        assertThat(events).hasSize(2)

        Thread.sleep(1000)
        val projection = projectionService.projectPaperState(paperId)
        assertThat(projection).isNotNull
        assertThat(projection?.summary).isEqualTo("Test summary")
        assertThat(projection?.novelty).isEqualTo("Test novelty")
        assertThat(projection?.journalName).isEqualTo("Test Journal")
        assertThat(projection?.impactFactor).isEqualTo(10.0)
    }

    @Test
    fun `배치 작업 상태가 올바르게 프로젝션된다`() {
        // Given
        val batchId = UUID.randomUUID().toString()

        val batchStarted = BatchJobStartedEvent(
            batchId = batchId,
            totalPapers = 5,
            category = "cs.AI",
            ver = 1
        )

        // When
        eventPublisher.publish(batchStarted)

        // Then
        Thread.sleep(500)
        val projection = projectionService.projectBatchState(batchId)
        assertThat(projection).isNotNull
        assertThat(projection?.totalPapers).isEqualTo(5)
        assertThat(projection?.category).isEqualTo("cs.AI")
    }

    @Test
    fun `여러 이벤트를 일괄 발행할 수 있다`() {
        // Given
        val paperId1 = UUID.randomUUID().toString()
        val paperId2 = UUID.randomUUID().toString()

        val events = listOf(
            PaperDiscoveredEvent(
                paperId = paperId1,
                arxivId = paperId1,
                title = "Paper 1",
                authors = listOf("Author 1"),
                abstract = "Abstract 1",
                publishedDate = "2025-11-17",
                url = "https://example.com/1",
                ver = 1
            ),
            PaperDiscoveredEvent(
                paperId = paperId2,
                arxivId = paperId2,
                title = "Paper 2",
                authors = listOf("Author 2"),
                abstract = "Abstract 2",
                publishedDate = "2025-11-17",
                url = "https://example.com/2",
                ver = 1
            )
        )

        // When
        eventPublisher.publishAll(events)

        // Then
        val events1 = eventStore.getEvents(paperId1)
        val events2 = eventStore.getEvents(paperId2)
        assertThat(events1).hasSize(1)
        assertThat(events2).hasSize(1)
    }

    @Test
    fun `이벤트 타입별로 조회할 수 있다`() {
        // Given
        val paperId = UUID.randomUUID().toString()
        val event = PaperDiscoveredEvent(
            paperId = paperId,
            arxivId = paperId,
            title = "Test",
            authors = listOf("Author"),
            abstract = "Abstract",
            publishedDate = "2025-11-17",
            url = "https://example.com",
            ver = 1
        )

        // When
        eventPublisher.publish(event)

        // Then
        val discoveredEvents = eventStore.getEventsByType("PaperDiscovered")
        assertThat(discoveredEvents).isNotEmpty
        assertThat(discoveredEvents.any { it.aggregateId == paperId }).isTrue
    }

    @Test
    fun `최신 버전을 조회할 수 있다`() {
        // Given
        val paperId = UUID.randomUUID().toString()

        eventPublisher.publish(PaperDiscoveredEvent(
            paperId = paperId,
            arxivId = paperId,
            title = "Test",
            authors = listOf("Author"),
            abstract = "Abstract",
            publishedDate = "2025-11-17",
            url = "https://example.com",
            ver = 1
        ))

        eventPublisher.publish(SummaryCompletedEvent(
            paperId = paperId,
            arxivId = paperId,
            summary = "Summary",
            novelty = "Novelty",
            journalName = null,
            impactFactor = null,
            ver = 2
        ))

        // When
        val latestVersion = eventStore.getLatestVersion(paperId)

        // Then
        assertThat(latestVersion).isEqualTo(2)
    }
}

