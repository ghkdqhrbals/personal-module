package org.ghkdqhrbals.client.paper

import org.ghkdqhrbals.client.config.logger
import org.springframework.stereotype.Component
import org.springframework.web.client.RestTemplate
import org.springframework.web.util.UriComponentsBuilder

@Component
class OpenAlexMetricProvider(
    private val restTemplate: RestTemplate
) : JournalMetricProvider {

    override fun getLatestImpactMetric(journalName: String, year: Int?): Pair<Double, Int>? {
        return runCatching {
            val url = UriComponentsBuilder.fromUriString("https://api.openalex.org/sources")
                .queryParam("search", journalName)
                .queryParam("per-page", 1)
                .build()
                .toUriString()

            logger().debug("OpenAlex search for journal: '$journalName' (year: $year) -> $url")

            val response = restTemplate.getForObject(url, Map::class.java) as Map<*, *>?
            val results = response?.get("results") as? List<*> ?: run {
                logger().debug("No results from OpenAlex for: '$journalName'")
                return null
            }

            val first = results.firstOrNull() as? Map<*, *> ?: return null
            val displayName = first["display_name"] as? String

            logger().debug("OpenAlex found journal: '$displayName' for query: '$journalName'")

            // year가 주어진 경우 해당 연도의 citation 데이터 조회
            if (year != null) {
                val citedByCount = first["cited_by_count"] as? Map<*, *>
                val yearlyStats = citedByCount?.get(year.toString()) as? Number

                if (yearlyStats != null) {
                    val impactFactor = yearlyStats.toDouble()
                    logger().info("Found Impact Factor for '$displayName' ($year): $impactFactor")
                    return impactFactor to year
                } else {
                    logger().debug("No citation data for '$displayName' in year $year, trying summary_stats")
                }
            }

            // year가 없거나 해당 연도 데이터가 없으면 summary_stats 사용 (최신 2년 평균)
            val summaryStats = first["summary_stats"] as? Map<*, *> ?: run {
                logger().debug("No summary_stats for journal: '$displayName'")
                return null
            }

            val twoYrMean = (summaryStats["2yr_mean_citedness"] as? Number)?.toDouble()
            val currentYear = year ?: java.time.Year.now().value

            if (twoYrMean != null) {
                logger().info("Found Impact Factor for '$displayName': $twoYrMean (year: $currentYear, 2yr average)")
                twoYrMean to currentYear
            } else {
                logger().debug("No 2yr_mean_citedness for: '$displayName'")
                null
            }
        }.onFailure { e ->
            logger().warn("OpenAlex API error for '$journalName': ${e.message}")
        }.getOrNull()
    }
}
