package org.ghkdqhrbals.client.domain.paper.service

import com.rometools.rome.feed.synd.SyndEntry
import com.rometools.rome.feed.synd.SyndEntryImpl
import com.rometools.rome.io.SyndFeedInput
import com.rometools.rome.io.XmlReader
import org.ghkdqhrbals.client.config.log.logger
import org.ghkdqhrbals.client.controller.paper.dto.Paper
import org.ghkdqhrbals.repository.paper.PaperEntity
import org.ghkdqhrbals.client.error.CommonException
import org.ghkdqhrbals.model.paper.SummaryEvent
import org.jdom2.Element
import org.jsoup.Jsoup
import org.springframework.stereotype.Component
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets
import java.time.LocalDate

@Component
class ArxivXmlParser {

    fun parse(xmlBytes: ByteArray, fromDate: LocalDate? = null): ArxivParseResult {
        val xml = String(xmlBytes, Charsets.UTF_8)

        val feed = SyndFeedInput().build(
            XmlReader(ByteArrayInputStream(xml.toByteArray(StandardCharsets.UTF_8)))
        )

        if (hasError(feed.entries)) {
            logger().error("arXiv API error feed detected")
            return ArxivParseResult.empty()
        }

        val papers = feed.entries
            .map { entry -> entryToPaper(entry) }
            .filter { paper -> matchesDateFilter(paper, fromDate) }

        val totalResults = extractTotalResults(xml)

        logger().info("Parsed ${papers.size} papers from arXiv feed")

        return ArxivParseResult(papers, totalResults)
    }

    private fun hasError(entries: List<SyndEntry>): Boolean {
        return entries.firstOrNull { entry ->
            entry.title?.equals("Error", ignoreCase = true) == true ||
            entry.links?.any { it.href?.contains("/api/errors") == true } == true
        } != null
    }

    private fun entryToPaper(entry: SyndEntry): ArxivPaper {
        val title = entry.title ?: "Untitled"
        val authors = entry.authors?.mapNotNull { it.name } ?: emptyList()
        val url = entry.link ?: entry.links?.firstOrNull()?.href
        val publishedDate = entry.publishedDate?.toInstant()?.toString()?.substring(0, 10)
        val abstract = Jsoup.parse(entry.description?.value ?: "").text().trim()

        val (doi, journalRef) = extractArxivMetadata(entry)

        return ArxivPaper(
            title = title,
            authors = authors,
            url = url,
            publishedDate = publishedDate,
            abstract = abstract,
            doi = doi,
            journalRefRaw = journalRef
        )
    }

    private fun extractArxivMetadata(entry: SyndEntry): Pair<String?, String?> {
        val foreign = (entry as? SyndEntryImpl)?.foreignMarkup ?: emptyList<Element>()

        val doi = foreign.firstOrNull {
            it.namespacePrefix == "arxiv" && it.name == "doi"
        }?.text

        val journalRef = foreign.firstOrNull {
            it.namespacePrefix == "arxiv" && it.name == "journal_ref"
        }?.text

        return Pair(doi, journalRef)
    }

    private fun matchesDateFilter(paper: ArxivPaper, fromDate: LocalDate?): Boolean {
        if (fromDate == null) return true

        return paper.publishedDate?.let {
            LocalDate.parse(it) >= fromDate
        } ?: false
    }

    private fun extractTotalResults(xml: String): Int? {
        val regex = Regex("<opensearch:totalResults>\\s*([0-9]+)\\s*</opensearch:totalResults>", RegexOption.IGNORE_CASE)
        return regex.find(xml)?.groupValues?.getOrNull(1)?.toIntOrNull()
    }
}

data class ArxivParseResult(
    val papers: List<ArxivPaper>,
    val totalResults: Int?
) {
    companion object {
        fun empty() = ArxivParseResult(emptyList(), null)
    }
}

data class ArxivPaper(
    val title: String,
    val authors: List<String> = emptyList(),
    val url: String? = null,
    val publishedDate: String? = null,
    val abstract: String? = "",
    val doi: String? = null,
    val journalRefRaw: String? = null,
    private val rawArxivId: String? = null
) {
    companion object {
        private val ARXIV_ID_REGEX = Regex("arxiv\\.org/abs/([0-9.]+(?:v[0-9]+)?)")
    }

    val arxivId: String get() =
        rawArxivId
            ?: url?.let { ARXIV_ID_REGEX.find(it)?.groupValues?.getOrNull(1) }
            ?: throw CommonException("Invalid arXiv URL: $url")

    val paperId = arxivId

    fun toPaper(): Paper = Paper(
        title = title,
        authors = authors,
        journal = null,
        publicationDate = publishedDate,
        doi = doi,
        abstract = abstract,
        url = url,
        citations = null,
        impactFactor = null,
        journalRefRaw = journalRefRaw
    )

    fun toPaperEntity(): PaperEntity {
        return PaperEntity(
            arxivId = arxivId,
            title = title,
            author = authors.joinToString(", "),
            publishedAt = publishedDate?.let { LocalDate.parse(it) },
            searchDate = LocalDate.now(),
            url = url
        )
    }

    fun toSummaryEvent(eventId: String = ""): SummaryEvent {
        return SummaryEvent(
            searchEventId = eventId,
            paperId = paperId,
            arxivId = arxivId,
            title = title,
            abstract = abstract ?: "",
            journalRefRaw = journalRefRaw
        )
    }
}
