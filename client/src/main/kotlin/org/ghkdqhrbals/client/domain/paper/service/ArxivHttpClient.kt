package org.ghkdqhrbals.client.domain.paper.service

import org.ghkdqhrbals.client.common.RedisSlidingWindowRateLimiter
import org.ghkdqhrbals.client.config.log.logger
import org.ghkdqhrbals.client.domain.event.PaperSearchAndStoreEvent
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.RestClient
import org.springframework.web.util.UriComponentsBuilder
import java.time.LocalDate

@Component
class ArxivHttpClient(
    @Qualifier("plainClient") private val restClient: RestClient,
    private val rateLimiter: RedisSlidingWindowRateLimiter,
    private val xmlParser: ArxivXmlParser
) {
    companion object {
        private const val RATE_LIMIT_KEY = "rate:arxiv:global"
        private const val MAX_RETRIES = 3
        private const val INITIAL_RETRY_DELAY_MS = 2000L
        private const val ARXIV_API_BASE_URL = "https://export.arxiv.org/api/query"
        private const val DEFAULT_QUERY = "all:machine+all:learning"

    }

    fun search(event: PaperSearchAndStoreEvent): ArxivParseResult {
        val url = buildRequestUrl(event.query, event.categories, event.maxResults, event.page)

        return try {
            val parseResult = rateLimiter.acquire(
                key = RATE_LIMIT_KEY,
                windowSec = 3,
                limit = 1,
                maxWaitMillis = 15_000
            ) {
                val xmlBytes = fetchXml(url)

                xmlParser.parse(
                    xmlBytes,
                    event.fromDate?.let { LocalDate.parse(it) }
                )
            }

            if (parseResult.papers.isEmpty()) {
                logger().info("[PaperSearchAndStoreListener] No papers found")
            }
            parseResult.papers.sortedByDescending { it.publishedDate }
            parseResult
        } catch (e: Exception) {
            logger().error("Failed to search papers: ${e.message}", e)
            ArxivParseResult(emptyList(), 0)
        }
    }

    fun buildRequestUrl(
        query: String?,
        categories: List<String>?,
        maxResults: Int,
        page: Int
    ): String {
        val searchQuery = buildSearchQuery(query, categories)
        val start = maxResults * page

        return UriComponentsBuilder.fromUriString(ARXIV_API_BASE_URL)
            .queryParam("search_query", searchQuery)
            .queryParam("start", start)
            .queryParam("sortBy", "submittedDate")
            .queryParam("sortOrder", "descending")
            .queryParam("max_results", maxResults)
            .build(false)
            .toUriString()
    }

    fun fetchXml(url: String): ByteArray {
        repeat(MAX_RETRIES) { attempt ->
            try {
                // Rate limiter를 통해 API 호출
                return rateLimiter.acquire(
                    key = RATE_LIMIT_KEY,
                    windowSec = 3,
                    limit = 1,
                    maxWaitMillis = 15_000
                ) {
                    logger().info("arXiv API request attempt=${attempt + 1}/$MAX_RETRIES")

                    val response = restClient.get()
                        .uri(url)
                        .accept(MediaType.APPLICATION_ATOM_XML)
                        .retrieve()
                        .body(ByteArray::class.java)
                        ?: throw IllegalStateException("Empty response from arXiv")

                    // HTML 에러 페이지 체크
                    validateResponse(response)

                    logger().info("arXiv API request successful on attempt=${attempt + 1}")
                    response
                }

            } catch (e: HttpClientErrorException) {
                logger().warn("arXiv HTTP 4xx error on attempt=${attempt + 1}: ${e.statusCode}")
                if (e.statusCode.value() == 429) {
                    handleRateLimitError(attempt)
                } else {
                    throw ArxivApiException("HTTP 4xx error: ${e.statusCode}", e)
                }
            } catch (e: HttpServerErrorException) {
                logger().warn("arXiv HTTP 5xx error on attempt=${attempt + 1}: ${e.statusCode}")
                handleServerError(attempt)
            } catch (e: ArxivHtmlErrorException) {
                logger().warn("arXiv returned HTML error page, attempt=${attempt + 1}")
                handleHtmlError(attempt)
            } catch (e: Exception) {
                logger().error("arXiv request failed on attempt=${attempt + 1}: ${e.message}", e)
                if (attempt >= MAX_RETRIES - 1) {
                    throw ArxivApiException("Failed after $MAX_RETRIES attempts", e)
                }
                waitBeforeRetry(attempt)
            }
        }

        throw ArxivApiException("arXiv API request failed after $MAX_RETRIES attempts")
    }

    private fun validateResponse(response: ByteArray) {
        val responseStr = String(response, Charsets.UTF_8)
        if (responseStr.trimStart().startsWith("<!DOCTYPE html>", ignoreCase = true) ||
            responseStr.trimStart().startsWith("<html>", ignoreCase = true)) {
            throw ArxivHtmlErrorException("HTML error page received")
        }
    }

    private fun handleRateLimitError(attempt: Int) {
        if (attempt < MAX_RETRIES - 1) {
            val waitTime = INITIAL_RETRY_DELAY_MS * (attempt + 1) * 2
            logger().info("Rate limited (429). Waiting ${waitTime}ms before retry...")
            Thread.sleep(waitTime)
        } else {
            throw ArxivApiException("Rate limited after $MAX_RETRIES attempts")
        }
    }

    private fun handleServerError(attempt: Int) {
        if (attempt < MAX_RETRIES - 1) {
            val waitTime = INITIAL_RETRY_DELAY_MS * (attempt + 1)
            logger().info("Server error. Waiting ${waitTime}ms before retry...")
            Thread.sleep(waitTime)
        } else {
            throw ArxivApiException("Server error after $MAX_RETRIES attempts")
        }
    }

    private fun handleHtmlError(attempt: Int) {
        if (attempt < MAX_RETRIES - 1) {
            val waitTime = INITIAL_RETRY_DELAY_MS * (attempt + 1)
            logger().info("Waiting ${waitTime}ms before retry...")
            Thread.sleep(waitTime)
        } else {
            throw ArxivApiException("HTML error page after $MAX_RETRIES attempts")
        }
    }

    private fun waitBeforeRetry(attempt: Int) {
        if (attempt < MAX_RETRIES - 1) {
            val waitTime = INITIAL_RETRY_DELAY_MS * (attempt + 1)
            logger().info("Waiting ${waitTime}ms before retry...")
            Thread.sleep(waitTime)
        }
    }

    private fun buildSearchQuery(query: String?, categories: List<String>?): String {
        val tokens = mutableListOf<String>()

        query?.trim()?.takeIf { it.isNotBlank() }?.let { q ->
            val words = q.split(Regex("\\s+"))
            tokens += if (words.size == 1) {
                "all:${words[0]}"
            } else {
                "all:${words.joinToString("+")}"
            }
        }

        categories?.filter { it.isNotBlank() }?.forEach { cat ->
            tokens += "cat:$cat"
        }

        return tokens.takeIf { it.isNotEmpty() }?.joinToString("+") ?: DEFAULT_QUERY
    }
}

