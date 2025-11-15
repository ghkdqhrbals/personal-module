package org.ghkdqhrbals.client.paper

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponentsBuilder
import org.ghkdqhrbals.client.ai.LlmClient
import org.ghkdqhrbals.client.config.logger

@Service
class ScholarService(
    private val restTemplate: RestTemplate,
    private val metricProvider: JournalMetricProvider,
    private val llm: LlmClient,
    private val dedup: DedupService,
    @Value("\${serpapi.key:}") private val serpApiKey: String
) {
    private val mapper = jacksonObjectMapper()

    fun search(
        query: String,
        maxResults: Int = 10,
        summarize: Boolean = true
    ): PaperSearchResponse {
        if (serpApiKey.isBlank()) {
            return PaperSearchResponse(emptyList(), 0, "Google Scholar")
        }

        val url = UriComponentsBuilder.fromUriString("https://serpapi.com/search.json")
            .queryParam("engine", "google_scholar")
            .queryParam("q", query)
            .queryParam("num", maxResults)
            .queryParam("api_key", serpApiKey)
            .build().toUriString()

        val json = restTemplate.getForObject(url, String::class.java) ?: return PaperSearchResponse(emptyList(), 0, "Google Scholar")
        logger().info("SerpAPI Scholar response: $json")
        val root = mapper.readTree(json)
        val results = root.path("organic_results")

        val papers = results.mapNotNull { node -> toPaper(node) }

        val processed = papers.map { p ->
            if (dedup.alreadyExists(p)) {
                logger().info("Skip LLM (exists): ${p.url ?: p.title}")
                p
            } else if (summarize && !p.abstract.isNullOrBlank()) {
                val s = runCatching { llm.summarizePaper(p.abstract, 120) }.getOrNull()
                p.copy(summary = s?.coreContribution, novelty = s?.noveltyAgainstPreviousWorks)
            } else p
        }

        return PaperSearchResponse(processed, processed.size, "Google Scholar")
    }

    private fun toPaper(node: JsonNode): Paper? {
        val title = node.path("title").asText(null) ?: return null
        val link = node.path("link").asText(null)
        val snippet = node.path("snippet").asText(null)
        val publicationInfo = node.path("publication_info").path("summary").asText("")
        val journal = extractVenue(publicationInfo)

        // 저널이 없으면 Impact Factor 없이 기본 Paper 반환
        if (journal == null) {
            return Paper(
                title = title,
                authors = emptyList(),
                journal = "",
                publicationDate = null,
                doi = null,
                abstract = snippet,
                url = link,
                citations = null,
                impactFactor = null
            )
        }

        val (ifValue, _) = metricProvider.getLatestImpactMetric(journal) ?: (null to null)

        return Paper(
            title = title,
            authors = emptyList(),
            journal = journal,
            publicationDate = null,
            doi = null,
            abstract = snippet,
            url = link,
            citations = null,
            impactFactor = ifValue
        )
    }

    private fun extractVenue(summary: String?): String? {
        if (summary.isNullOrBlank()) return null
        // 단순 추출: "Journal Name - 2025" 또는 "Journal Name, 2025" 등에서 저널명 후보 추출
        return summary.split(" - ", ",", limit = 2).firstOrNull()?.trim()
    }
}
