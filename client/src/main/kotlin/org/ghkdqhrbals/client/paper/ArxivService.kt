package org.ghkdqhrbals.client.paper

import com.rometools.rome.io.SyndFeedInput
import com.rometools.rome.io.XmlReader
import org.jsoup.Jsoup
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponentsBuilder
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.time.LocalDate
import org.ghkdqhrbals.client.ai.LlmClient
import org.ghkdqhrbals.client.config.logger
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import com.rometools.rome.feed.synd.SyndEntryImpl
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.ghkdqhrbals.client.paper.repository.PaperRepository
import org.jdom2.Element
import org.springframework.transaction.annotation.Transactional

@Service
class ArxivService(
    private val restTemplate: RestTemplate,
    private val llm: LlmClient,
    private val dedup: DedupService,
    private val metricProvider: JournalMetricProvider,
    private val repository: PaperRepository,
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

        val xml = try {
            val headers = HttpHeaders()
            headers["User-Agent"] = "ghkdqhrbals-arxiv-client/1.0"

            val entity = HttpEntity<String>(headers)

            val xmls = restTemplate.exchange(url, HttpMethod.GET, entity, String::class.java)
            val xml = xmls.body

            xml
        } catch (e: Exception) {
            logger().error("arXiv HTTP error: ${e.message}")
            return PaperSearchResponse(emptyList(), 0, "arXiv")
        } ?: return PaperSearchResponse(emptyList(), 0, "arXiv")

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
            val journalName = normalizeJournalRef(journalRefRaw)

            val year = published?.substring(0, 4)?.toIntOrNull()
                ?: extractYearFromJournalRef(journalRefRaw)
            val impact = journalName?.let {
                metricProvider.getLatestImpactMetric(it, year)?.first.also { if_ ->
                    if (if_ != null) {
                        logger().debug("Journal found: '$it' with IF=$if_")
                    }
                }
            }

            Paper(
                title = title,
                authors = authors,
                journal = journalName ?: "arXiv",
                publicationDate = published,
                doi = doi,
                abstract = abs,
                url = link,
                citations = null,
                impactFactor = impact
            )
        }.filter { p ->
            if (from == null) true else p.publicationDate?.let { LocalDate.parse(it) >= from } ?: false
        }

        logger().info("PAPERS : ${papers.map { it.title }}")
        papers.take(5).forEachIndexed { idx, p ->
            logger().info("arXiv result[$idx] title='${p.title}', date='${p.publicationDate}', url='${p.url}'")
        }

        // 병렬로 요약 생성
        val processed = runBlocking {
            papers.map { p ->
                async {
                    // 존재하면 요약 스킵
                    if (dedup.alreadyExists(p)) {
                        logger().info("Skip LLM (exists): ${p.url ?: p.title}")
                        p
                    } else if (summarize && !p.abstract.isNullOrBlank()) {
                        logger().info("Start LLM summary for: ${p.title}")
                        val s = runCatching { llm.summarizePaper(p.abstract, 120) }
                            .onSuccess { logger().info("Completed LLM summary for: ${p.title}") }
                            .onFailure { e -> logger().warn("summary failed for '${p.title}': ${e.message}") }
                            .getOrNull()
                        p.copy(summary = s)
                    } else p
                }
            }.awaitAll()
        }

        val entitiesToSave = processed.map { p ->
            val arxivId = p.url?.let { url ->
                val regex = Regex("arxiv\\.org/abs/([0-9.]+(?:v[0-9]+)?)")
                regex.find(url)?.groupValues?.getOrNull(1)
            }
            org.ghkdqhrbals.client.paper.entity.PaperEntity(
                arxivId = arxivId,
                title = p.title.take(255),
                author = p.authors.joinToString(", ").take(255),
                publishedAt = p.publicationDate?.let { LocalDate.parse(it) },
                searchDate = LocalDate.now(),
                summaryDate = if (p.summary != null) LocalDate.now() else null,
                url = p.url?.take(255),
                journal = p.journal?.take(255),
                impactFactor = p.impactFactor,
                summary = p.summary
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
            totalResults = totalResults
        )
    }

    private fun buildSearchQuery(
        query: String?,
        categories: List<String>?
    ): String {

        val tokens = mutableListOf<String>()

        // 1) free-text query → all:<word> 형태로 쪼개기
//        query
//            ?.trim()
//            ?.takeIf { it.isNotBlank() }
//            ?.split(Regex("\\s+"))
//            ?.forEach { word ->
//                tokens += "all:$word"
//            }
        // query 에 "machine learning" 이라면 all:machine+learning 으로 변환
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

    private fun extractYearFromJournalRef(ref: String?): Int? {
        if (ref.isNullOrBlank()) return null

        // "Nature 646, 818-824 (2025)" -> 2025
        // "Cell 187 (2024)" -> 2024
        val yearInParens = Regex("\\((\\d{4})\\)").find(ref)?.groupValues?.getOrNull(1)?.toIntOrNull()
        if (yearInParens != null) return yearInParens

        // "JAMA, 2024" -> 2024
        val yearAfterComma = Regex(",\\s*(\\d{4})").find(ref)?.groupValues?.getOrNull(1)?.toIntOrNull()
        if (yearAfterComma != null) return yearAfterComma

        return null
    }

    private fun normalizeJournalRef(ref: String?): String? {
        if (ref.isNullOrBlank()) return null

        logger().debug("Parsing journal_ref: '$ref'")

        val cleaned = ref.trim()

        // 1. 쉼표 이전 부분 추출 (예: "Nature 646, 818-824 (2025)" -> "Nature 646")
        val beforeComma = cleaned.split(",").firstOrNull()?.trim() ?: return null

        // 2. 숫자로 시작하는 부분 제거 (볼륨/페이지 번호)
        //    예: "Nature 646" -> "Nature"
        //    예: "IEEE TPAMI vol.45" -> "IEEE TPAMI vol"
        val journalName = beforeComma.split(Regex("\\s+"))
            .takeWhile { !it.matches(Regex("^\\d+.*")) && !it.startsWith("vol", ignoreCase = true) }
            .joinToString(" ")
            .trim()

        return journalName.takeIf { it.isNotBlank() }.also { result ->
            logger().debug("Extracted journal name: '$result' from '$ref'")
        }
    }
}
