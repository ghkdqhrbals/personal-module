package org.ghkdqhrbals.client.domain.scheduler.config

import org.ghkdqhrbals.client.config.log.logger
import org.ghkdqhrbals.client.domain.event.PaperSearchAndStoreEvent
import org.ghkdqhrbals.client.domain.paper.service.ArxivHttpClient
import org.ghkdqhrbals.client.domain.paper.service.ArxivPaper
import org.springframework.batch.item.ItemReader

class SearchReader(
    private val client: ArxivHttpClient,
    private val query: String,
    private val categories: List<String>? = null,
    private val chunkSize: Int
) : ItemReader<ArxivPaper> {

    private var currentIndex = 0
    private var currentPage = 0
    private var buffer: List<ArxivPaper> = emptyList()

    override fun read(): ArxivPaper? {
        logger().info("🔍 SearchReader가 논문을 읽는 중... (현재 페이지: $currentPage, 현재 인덱스: $currentIndex)")

        // 버퍼가 비었으면 다음 페이지 로드
        if (currentIndex >= buffer.size) {
            buffer = loadNextPage() ?: return null
            currentIndex = 0
        }

        // 버퍼가 여전히 비어있으면 종료
        if (buffer.isEmpty()) {
            return null
        }

        // 버퍼에서 하나씩 반환
        val item = buffer[currentIndex]
        currentIndex++
        logger().info("▶️ 읽은 논문: ${item.title} (${item.arxivId})")
        return item
    }

    private fun loadNextPage(): List<ArxivPaper>? {
        logger().info("🔄 SearchReader가 페이지 $currentPage 로드 중...")

        val papers = client.search(
            PaperSearchAndStoreEvent(
                searchEventId = "search-$query-page$currentPage-${System.currentTimeMillis()}",
                query = query,
                categories = categories,
                maxResults = chunkSize,
                page = currentPage,
                shouldSummarize = false
            )
        )

        logger().info("✅ SearchReader 페이지 $currentPage 로드 완료: ${papers.size}개 논문")

        // 논문이 없으면 종료
        if (papers.isEmpty()) {
            logger().info("ℹ️ SearchReader 더 이상 논문이 없음 (페이지: $currentPage)")
            return null
        }

        currentPage++
        return papers
    }
}