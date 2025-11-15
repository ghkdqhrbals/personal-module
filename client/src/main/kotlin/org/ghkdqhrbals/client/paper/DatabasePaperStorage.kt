package org.ghkdqhrbals.client.paper

import org.ghkdqhrbals.client.config.logger
import org.ghkdqhrbals.client.paper.entity.PaperEntity
import org.ghkdqhrbals.client.paper.repository.PaperRepository
import org.springframework.stereotype.Component
import java.time.LocalDate

@Component
class DatabasePaperStorage(
    private val paperRepository: PaperRepository
) : PaperStorage {

    override fun save(paper: Paper) {
        try {
            // arXiv ID 추출 (URL에서)
            val arxivId = extractArxivId(paper.url)

            // 이미 존재하는 논문인지 확인
            if (arxivId != null && paperRepository.existsByArxivId(arxivId)) {
                logger().info("Paper already exists in DB: arxivId=$arxivId, title='${paper.title}'")
                return
            }

            val entity = PaperEntity(
                arxivId = arxivId,
                title = paper.title.take(255),
                author = paper.authors.joinToString(", ").take(255),
                publishedAt = paper.publicationDate?.let { parseDate(it) },
                searchDate = LocalDate.now(),
                summaryDate = if (paper.summary != null) LocalDate.now() else null,
                url = paper.url?.take(255),
                summary = paper.summary
            )

            paperRepository.save(entity)
            logger().info("Paper saved to DB: id=${entity.id}, arxivId=$arxivId, title='${paper.title}'")
        } catch (e: Exception) {
            logger().error("Failed to save paper to DB: title='${paper.title}'", e)
        }
    }

    private fun extractArxivId(url: String?): String? {
        if (url == null) return null
        // arXiv URL 패턴: http://arxiv.org/abs/2101.12345 또는 https://arxiv.org/abs/2101.12345v1
        val regex = Regex("arxiv\\.org/abs/([0-9.]+(?:v[0-9]+)?)")
        return regex.find(url)?.groupValues?.getOrNull(1)
    }

    private fun parseDate(dateStr: String): LocalDate? {
        return try {
            LocalDate.parse(dateStr.substring(0, 10))
        } catch (e: Exception) {
            null
        }
    }
}

