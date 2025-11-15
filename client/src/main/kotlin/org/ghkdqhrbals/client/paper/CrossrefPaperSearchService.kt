package org.ghkdqhrbals.client.paper

import org.ghkdqhrbals.client.config.logger
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponentsBuilder
import java.time.LocalDate
import org.ghkdqhrbals.client.ai.LlmClient

@Service
class CrossrefPaperSearchService(
    private val restTemplate: RestTemplate,
    private val metricProvider: JournalMetricProvider,
    private val llmClient: LlmClient,
    private val dedup: DedupService
) : PaperSearchService {

    companion object {
        private const val CROSSREF_API_BASE = "https://api.crossref.org/works"

        private val CATEGORY_JOURNALS = mapOf(
            "Computer Science" to listOf(
                "IEEE Transactions on Pattern Analysis and Machine Intelligence",
                "Nature Machine Intelligence",
                "Neural Information Processing Systems",
                "International Conference on Learning Representations",
                "ACM Computing Surveys",
                "Journal of Machine Learning Research"
            ),
            "Medicine" to listOf(
                "The Lancet",
                "New England Journal of Medicine",
                "JAMA",
                "The BMJ",
                "Nature Medicine"
            ),
            "Physics" to listOf(
                "Physical Review Letters",
                "Nature Physics",
                "Reviews of Modern Physics"
            ),
            "Biology" to listOf(
                "Nature",
                "Science",
                "Cell",
                "Nature Biotechnology"
            )
        )
    }

    override fun searchPapers(request: PaperSearchRequest): PaperSearchResponse {
        logger().info("Searching papers for category: ${request.category}, minImpactFactor: ${request.minImpactFactor}")

        val targetJournals = getFilteredJournals(request.category, request.minImpactFactor)
        if (targetJournals.isEmpty()) {
            return PaperSearchResponse(emptyList(), 0, request.category)
        }

        val allPapers = mutableListOf<Paper>()
        val perJournal = (request.maxResults / targetJournals.size).coerceAtLeast(1)

        targetJournals.take(8).forEach { journal ->
            runCatching {
                val papers = searchPapersByJournal(journal.name, perJournal)
                papers.forEach { p ->
                    val withIf = p.copy(impactFactor = journal.impactFactor)
                    val summarized = if (!dedup.alreadyExists(withIf) && request.summarize == true && !p.abstract.isNullOrBlank()) {
                        logger().info("Generate LLM summary for: ${p.title}")
                        val summary = runCatching { llmClient.summarizePaper(p.abstract, 120) }.getOrNull()
                        withIf.copy(summary = summary)
                    } else {
                        if (dedup.alreadyExists(withIf)) logger().info("Skip LLM (exists): ${p.title}")
                        withIf
                    }
                    allPapers += summarized
                }
            }.onFailure { e -> logger().warn("Failed journal fetch: ${journal.name}", e) }
        }

        val fromDate = request.fromDate?.let { LocalDate.parse(it) }
        val filtered = allPapers.filter { paper ->
            if (fromDate == null) true else paper.publicationDate?.take(10)?.let { LocalDate.parse(it) >= fromDate } ?: false
        }

        val sorted = filtered.sortedByDescending { it.citations ?: 0 }.take(request.maxResults)
        return PaperSearchResponse(sorted, sorted.size, request.category)
    }

    override fun searchPapersByJournal(journalName: String, maxResults: Int): List<Paper> {
        logger().info("Crossref search for journal: $journalName")

        val (ifValue, year) = metricProvider.getLatestImpactMetric(journalName) ?: (null to null)

        val url = UriComponentsBuilder.fromUriString(CROSSREF_API_BASE)
            .queryParam("query.container-title", journalName)
            .queryParam("rows", maxResults)
            .queryParam("sort", "published")
            .queryParam("order", "desc")
            .queryParam("select", "title,author,container-title,DOI,URL,published-online,published-print,is-referenced-by-count,abstract")
            .build().toUriString()

        val response = runCatching { restTemplate.getForObject(url, CrossrefResponse::class.java) }.getOrNull()
        val items = response?.message?.items ?: return emptyList()

        return items.map { item ->
            val abs = item.abstract?.let { dehtml(it) }
            Paper(
                title = item.title?.firstOrNull() ?: "Unknown Title",
                authors = item.author?.mapNotNull { listOfNotNull(it.given, it.family).joinToString(" ") } ?: emptyList(),
                journal = item.containerTitle?.firstOrNull() ?: journalName,
                publicationDate = extractDate(item),
                doi = item.DOI,
                abstract = abs,
                url = item.URL,
                citations = item.citationCount,
                impactFactor = ifValue
            )
        }
    }

    override fun getTopJournals(category: String, minImpactFactor: Double?): List<Journal> {
        return getFilteredJournals(category, minImpactFactor)
            .sortedByDescending { it.impactFactor ?: 0.0 }
    }

    private fun getFilteredJournals(category: String, minImpactFactor: Double?): List<Journal> {
        val names = CATEGORY_JOURNALS[category] ?: emptyList()
        return names.mapNotNull { name ->
            val metric = metricProvider.getLatestImpactMetric(name)
            val (value, year) = metric ?: (null to null)
            val pass = minImpactFactor == null || (value != null && value >= minImpactFactor)
            if (pass) Journal(name, value, null, null, category, year, metricSource = "OpenAlex") else null
        }
    }

    private fun extractDate(item: CrossrefItem): String? {
        val date = item.publishedOnline ?: item.publishedPrint
        return date?.dateParts?.firstOrNull()?.let { parts ->
            when (parts.size) {
                1 -> "${parts[0]}-01-01"
                2 -> "${parts[0]}-${parts[1].toString().padStart(2, '0')}-01"
                3 -> "${parts[0]}-${parts[1].toString().padStart(2, '0')}-${parts[2].toString().padStart(2, '0')}"
                else -> null
            }
        }
    }

    private fun dehtml(s: String): String {
        // Crossref abstracts may come as JATS XML or HTML-like; strip basic tags
        return s
            .replace(Regex("<[^>]+>"), "")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
            .trim()
    }
}
