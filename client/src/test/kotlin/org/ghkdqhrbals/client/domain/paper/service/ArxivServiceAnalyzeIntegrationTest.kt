package org.ghkdqhrbals.client.domain.paper.service

import com.redis.testcontainers.RedisContainer
import org.ghkdqhrbals.client.domain.event.PaperSearchAndStoreEvent
import org.ghkdqhrbals.client.domain.paper.entity.repository.PaperRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.testcontainers.service.connection.ServiceConnection
import org.springframework.test.context.ActiveProfiles
import org.testcontainers.containers.MySQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName

/**
 * ArxivService.analyze() 통합 테스트
 * - 실제 MySQL TestContainer 사용
 * - 실제 Redis TestContainer 사용
 * - 실제 ArxivHttpClient HTTP 호출 (mocking 없음)
 * - 실제 데이터베이스에 저장 및 조회 검증
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class ArxivServiceAnalyzeIntegrationTest {

    companion object {
        @Container
        @ServiceConnection
        val mysqlContainer = MySQLContainer(DockerImageName.parse("mysql:8.0"))
            .withDatabaseName("test_db")
            .withUsername("test")
            .withPassword("test")

        @Container
        @ServiceConnection
        val redisContainer = RedisContainer(DockerImageName.parse("redis:7.2-alpine"))
            .withExposedPorts(6379)
    }

    @Autowired
    private lateinit var arxivService: ArxivService

    @Autowired
    private lateinit var paperRepository: PaperRepository

    @Autowired
    private lateinit var arxivHttpClient: ArxivHttpClient

    @BeforeEach
    fun setUp() {
        // 테스트 전 DB 초기화
        paperRepository.deleteAll()
    }

    @AfterEach
    fun tearDown() {
        // 테스트 후 DB 정리
        paperRepository.deleteAll()
    }

    @Test
    fun `실제 Arxiv API 호출로 논문을 검색하고 DB에 저장한다`() {
        // Given: 실제 ArxivHttpClient가 API 호출
        val event = PaperSearchAndStoreEvent(
            searchEventId = "integration-test-1",
            query = "machine learning",
            categories = null,
            maxResults = 5,
            page = 0,
            fromDate = null,
            shouldSummarize = false,
            ver = 1,
            meta = emptyMap()
        )

        // When: 실제 API 호출로 논문 검색 및 저장
        val result = arxivService.analyze(event)

        // Then
        assertNotNull(result)
        assertTrue(result!!.size > 0, "API에서 논문이 반환되어야 함")

        // 모든 결과가 arxivId를 가지고 있는지 확인
        result.forEach { (paper, entity) ->
            assertNotNull(paper.arxivId, "ArxivPaper는 arxivId를 가져야 함")
            assertTrue(paper.arxivId!!.isNotEmpty(), "arxivId는 비어있지 않아야 함")

            // Entity가 null이 아닌지 확인 (신규 논문)
            assertNotNull(entity, "새로 저장된 논문의 entity는 null이 아니어야 함")
            assertNotNull(entity?.id, "저장된 entity는 ID를 가져야 함")
        }

        // DB에 실제로 저장되었는지 확인
        val dbPapers = paperRepository.findAll().toList()
        assertTrue(dbPapers.size > 0, "DB에 논문이 저장되어야 함")

        // arxivId 검증
        dbPapers.forEach { paper ->
            assertNotNull(paper.arxivId, "DB의 논문은 arxivId를 가져야 함")
            assertTrue(paper.arxivId!!.isNotEmpty(), "DB의 arxivId는 비어있지 않아야 함")
        }
    }

    @Test
    fun `동일한 arxiv 쿼리로 두 번째 호출 시 기존 논문은 스킵하고 새 논문만 저장한다`() {
        // Given: 첫 번째 호출
        val event = PaperSearchAndStoreEvent(
            searchEventId = "integration-test-2a",
            query = "deep learning",
            categories = null,
            maxResults = 3,
            page = 0,
            fromDate = null,
            shouldSummarize = false,
            ver = 1,
            meta = emptyMap()
        )

        val firstResult = arxivService.analyze(event)
        assertNotNull(firstResult)
        val firstCount = firstResult!!.size
        assertTrue(firstCount > 0, "첫 번째 호출에서 논문이 검색되어야 함")

        // DB에서 첫 번째 결과 확인
        var dbCount = paperRepository.findAll().toList().size
        assertEquals(firstCount, dbCount, "첫 번째 호출 후 DB 논문 수 일치")

        // When: 두 번째 호출 (동일한 쿼리, 다른 page)
        val event2 = PaperSearchAndStoreEvent(
            searchEventId = "integration-test-2b",
            query = "deep learning",
            categories = null,
            maxResults = 3,
            page = 1,  // 다음 페이지
            fromDate = null,
            shouldSummarize = false,
            ver = 1,
            meta = emptyMap()
        )

        val secondResult = arxivService.analyze(event2)
        assertNotNull(secondResult)

        // Then: DB에서 중복 제거 확인
        dbCount = paperRepository.findAll().toList().size
        assertTrue(
            dbCount >= firstCount,
            "두 번째 호출 후에도 기존 논문은 중복 저장되지 않고, 새 논문만 추가됨"
        )

        // 모든 논문이 고유한 arxivId를 가지는지 확인
        val allPapers = paperRepository.findAll().toList()
        val arxivIds = allPapers.map { it.arxivId }.toSet()
        assertEquals(allPapers.size, arxivIds.size, "모든 논문은 고유한 arxivId를 가져야 함")
    }

    @Test
    fun `검색 결과의 arxivId와 DB 저장된 arxivId가 일치한다`() {
        // Given
        val event = PaperSearchAndStoreEvent(
            searchEventId = "integration-test-3",
            query = "artificial intelligence",
            categories = null,
            maxResults = 2,
            page = 0,
            fromDate = null,
            shouldSummarize = false,
            ver = 1,
            meta = emptyMap()
        )

        // When
        val result = arxivService.analyze(event)

        // Then: 모든 결과의 arxivId가 DB에 저장되어 있는지 확인
        assertNotNull(result)
        result!!.forEach { (paper, entity) ->
            assertNotNull(entity, "Entity는 null이 아니어야 함")

            // DB에서 동일한 arxivId로 조회
            val dbEntity = paperRepository.findAll().toList()
                .find { it.arxivId == paper.arxivId }

            assertNotNull(
                dbEntity,
                "API에서 반환한 arxivId '${paper.arxivId}'가 DB에 존재해야 함"
            )
            assertEquals(entity?.arxivId, dbEntity?.arxivId, "arxivId 일치 확인")
        }
    }

    @Test
    fun `ArxivService가 실제 빈으로 주입되어 실행된다`() {
        // Sanity check: ArxivService가 제대로 주입되었는지 확인
        assertNotNull(arxivService, "ArxivService가 주입되어야 함")
        assertNotNull(paperRepository, "PaperRepository가 주입되어야 함")
        assertNotNull(arxivHttpClient, "ArxivHttpClient가 주입되어야 함")

        // 실제 빈 인스턴스 검증
        assertTrue(
            arxivService is ArxivService,
            "arxivService는 ArxivService의 인스턴스여야 함"
        )
    }
}

