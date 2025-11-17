package org.ghkdqhrbals.client.paper

import com.rometools.rome.io.SyndFeedInput
import com.rometools.rome.io.XmlReader
import org.jsoup.Jsoup
import org.springframework.stereotype.Service
import org.springframework.web.client.RestClient
import org.springframework.web.util.UriComponentsBuilder
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import org.ghkdqhrbals.client.ai.LlmClient
import org.ghkdqhrbals.client.config.logger
import com.rometools.rome.feed.synd.SyndEntryImpl
import org.ghkdqhrbals.client.paper.entity.PaperEntity
import org.ghkdqhrbals.client.paper.repository.PaperRepository
import org.jdom2.Element
import org.springframework.transaction.annotation.Transactional
import org.ghkdqhrbals.client.paper.queue.SummaryQueueProducer
import org.ghkdqhrbals.client.paper.queue.SummaryJobRequest
import org.springframework.http.MediaType
import java.util.UUID

import org.springframework.beans.factory.annotation.Qualifier

@Service
class ArxivService(
    @Qualifier("plainClient") private val plainClient: RestClient,
    // llm은 향후 동기 처리 경로나 즉시 응답 모드 전환에 대비해 유지
    private val llm: LlmClient,
    private val dedup: DedupService,
    private val repository: PaperRepository,
    private val summaryQueueProducer: SummaryQueueProducer,
) {

    @Transactional
    fun search(
        query: String?,
        categories: List<String>?,
        maxResults: Int = 10,
        page: Int = 0,
        fromDate: String? = null,
        summarize: Boolean = true,
        save: Boolean = true
    ): PaperSearchResponse {
        val searchQuery = buildSearchQuery(query, categories)
        val start = maxResults * page  // start = size * page

        val url = UriComponentsBuilder.fromUriString("https://export.arxiv.org/api/query")
            .queryParam("search_query", searchQuery)  // URI builder가 알아서 인코딩함
            .queryParam("start", start)
            .queryParam("sortBy", "submittedDate")
            .queryParam("sortOrder", "descending")
            .queryParam("max_results", maxResults)
            .build(false)
            .toUriString()

        logger().info("arXiv request url=$url (page=$page, start=$start, size=$maxResults)")

        val xmlBytes = try {
            plainClient.get()
                .uri(url)
                .accept(MediaType.APPLICATION_ATOM_XML)
                .retrieve()
                .body(ByteArray::class.java)
        } catch (e: Exception) {
            logger().error("arXiv HTTP request failed: ${e.message}", e)
            return PaperSearchResponse(emptyList(), 0, "arXiv")
        }

        if (xmlBytes == null || xmlBytes.isEmpty()) {
            logger().error("arXiv returned null or empty response for url=$url")
            return PaperSearchResponse(emptyList(), 0, "arXiv")
        }

        val xml = String(xmlBytes, Charsets.UTF_8)

        logOpenSearchMeta(xml)

        val input = SyndFeedInput()
        val feed = input.build(XmlReader(ByteArrayInputStream(xml.toByteArray(StandardCharsets.UTF_8))))
        logger().info("arXiv feed entries=${feed.entries.size}")

        // 에러 피드 감지 (title=Error 또는 링크가 /api/errors 포함)
        val errorEntry = feed.entries.firstOrNull { entry ->
            (entry.title?.equals("Error", ignoreCase = true) == true) ||
                    (entry.links?.any { it.href?.contains("/api/errors") == true } == true)
        }
        if (errorEntry != null) {
            val errSummary = Jsoup.parse(errorEntry.description?.value ?: "").text()
            logger().error("arXiv API error feed detected: title='${errorEntry.title}', summary='${errSummary}'")
            // 필요시 요약 일부만 출력
            logger().debug("arXiv error raw preview='${xml.take(500)}'")
            return PaperSearchResponse(emptyList(), 0, "arXiv")
        }

        val from = fromDate?.let { LocalDate.parse(it) }

        val papers = feed.entries.map { entry ->
            val title = entry.title ?: "Untitled"
            val authors = entry.authors?.mapNotNull { it.name } ?: emptyList()
            val link = entry.link ?: entry.links?.firstOrNull()?.href
            val published = entry.publishedDate?.toInstant()?.toString()?.substring(0, 10)
            val summaryHtml = entry.description?.value ?: ""
            val abs = Jsoup.parse(summaryHtml).text().trim()

            // arXiv 확장 메타 추출
            val entryImpl = entry as? SyndEntryImpl
            val foreign: List<Element> = entryImpl?.foreignMarkup ?: emptyList()
            val doi = foreign.firstOrNull { it.namespacePrefix == "arxiv" && it.name == "doi" }?.text
            val journalRefRaw = foreign.firstOrNull { it.namespacePrefix == "arxiv" && it.name == "journal_ref" }?.text

            Paper(
                title = title,
                authors = authors,
                journal = null,  // LLM에서 추출할 예정
                publicationDate = published,
                doi = doi,
                abstract = abs,
                url = link,
                citations = null,
                impactFactor = null,  // LLM 추출 후 조회
                journalRefRaw = journalRefRaw  // 임시 저장
            )
        }.filter { p ->
            if (from == null) true else p.publicationDate?.let { LocalDate.parse(it) >= from } ?: false
        }

        logger().info("PAPERS : ${papers.map { it.title }}")
        papers.take(5).forEachIndexed { idx, p ->
            logger().info("arXiv result[$idx] title='${p.title}', date='${p.publicationDate}', url='${p.url}'")
        }

        // 병렬로 요약 생성 및 저널 정보 추출 -> 이제 비동기 큐에 적재
        val eventId = if (summarize) UUID.randomUUID().toString() else null
        if (eventId != null) {
            runCatching { summaryQueueProducer.enqueueProgressInit(eventId) }
                .onFailure { logger().warn("Progress init failed eventId=$eventId err=${it.message}") }
        }

        val processed = papers.map { p ->
            if (dedup.alreadyExists(p)) {
                logger().info("Skip enqueue (exists): ${p.url ?: p.title}")
                p
            } else if (summarize && !p.abstract.isNullOrBlank()) {
                summaryQueueProducer.enqueue(
                    SummaryJobRequest(
                        arxivId = p.url?.let { url -> Regex("arxiv\\.org/abs/([0-9.]+(?:v[0-9]+)?)").find(url)?.groupValues?.getOrNull(1) },
                        title = p.title,
                        abstract = p.abstract,
                        journalRefRaw = p.journalRefRaw,
                        maxLength = 120,
                        eventId = eventId
                    )
                )
                p.copy(journal = p.journal ?: "arXiv")
            } else p
        }
        if (eventId != null) {
            val totalQueued = processed.count { it.summary == null && summarize && !it.abstract.isNullOrBlank() }
            summaryQueueProducer.updateTotal(eventId, totalQueued)
        }

        val entitiesToSave = processed.map { p ->
            val arxivId = p.url?.let { url ->
                val regex = Regex("arxiv\\.org/abs/([0-9.]+(?:v[0-9]+)?)")
                regex.find(url)?.groupValues?.getOrNull(1)
            }
            PaperEntity(
                arxivId = arxivId,
                title = p.title.take(255),
                author = p.authors.joinToString(", ").take(255),
                publishedAt = p.publicationDate?.let { LocalDate.parse(it) },
                searchDate = LocalDate.now(),
                summaryDate = if (p.summary != null) LocalDate.now() else null,
                url = p.url?.take(255),
                journal = p.journal?.take(255),
                impactFactor = p.impactFactor,
                summary = p.summary,
                novelty = p.novelty
            )
        }.filter { entity ->
            // 중복 제거: arxiv_id 또는 url이 이미 존재하면 스킵
            val isDuplicate = (entity.arxivId != null && repository.existsByArxivId(entity.arxivId)) ||
                    (entity.url != null && repository.existsByUrl(entity.url))
            if (isDuplicate) {
                logger().info("Skip save (duplicate): arxivId=${entity.arxivId}, url=${entity.url}")
            }
            !isDuplicate
        }

        repository.saveAll(entitiesToSave)
        val totalResults = extractTotalResults(xml)

        logger().info("arXiv processed count=${processed.size}, page=$page, totalResults=${totalResults}")
        return PaperSearchResponse(
            papers = processed,
            count = processed.size,
            source = "arXiv",
            page = page,
            size = maxResults,
            totalResults = totalResults,
            eventId = eventId
        )
    }

    private fun buildSearchQuery(
        query: String?,
        categories: List<String>?
    ): String {

        val tokens = mutableListOf<String>()

        query
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { q ->
                val words = q.split(Regex("\\s+"))
                if (words.size == 1) {
                    tokens += "all:${words[0]}"
                } else {
                    val combined = words.joinToString("+")
                    tokens += "all:$combined"
                }
            }

        // 2) categories → cat:<category>
        categories
            ?.filter { it.isNotBlank() }
            ?.forEach { cat ->
                tokens += "cat:$cat"
            }

        // 아무것도 없으면 디폴트 쿼리
        if (tokens.isEmpty()) {
            // "machine learning" 이라면 all:machine+all:learning 으로 가는게 더 정석
            return "all:machine+all:learning"
        }

        // 핵심: 공백/괄호/AND 없이, 토큰을 + 로만 이어붙인다.
        return tokens.joinToString("+")
    }

    private fun extractTotalResults(xml: String): Int? {
        val regex = Regex("<opensearch:totalResults>\\s*([0-9]+)\\s*</opensearch:totalResults>", RegexOption.IGNORE_CASE)
        return regex.find(xml)?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    private fun logOpenSearchMeta(xml: String) {
        fun extract(tag: String): String? {
            val regex = Regex("<opensearch:$tag>\\s*([0-9]+)\\s*</opensearch:$tag>", RegexOption.IGNORE_CASE)
            return regex.find(xml)?.groupValues?.getOrNull(1)
        }

        val total = extract("totalResults")
        val perPage = extract("itemsPerPage")
        val startIdx = extract("startIndex")
        if (total != null || perPage != null || startIdx != null) {
            logger().info("arXiv opensearch: totalResults=$total, itemsPerPage=$perPage, startIndex=$startIdx")
        }
    }
}
