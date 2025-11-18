package org.ghkdqhrbals.client.paper

import org.ghkdqhrbals.client.common.RedisSlidingWindowRateLimiter
import org.ghkdqhrbals.client.config.logger
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.http.MediaType
import org.springframework.stereotype.Component
import org.springframework.web.client.HttpClientErrorException
import org.springframework.web.client.HttpServerErrorException
import org.springframework.web.client.RestClient

@Component
class ArxivHttpClient(
    @Qualifier("plainClient") private val restClient: RestClient,
    private val rateLimiter: RedisSlidingWindowRateLimiter
) {
    companion object {
        private const val RATE_LIMIT_KEY = "rate:arxiv:global"
        private const val MAX_RETRIES = 3
        private const val INITIAL_RETRY_DELAY_MS = 2000L
    }

    fun fetchXml(url: String): ByteArray? {
        repeat(MAX_RETRIES) { attempt ->
            try {
                val acquired = rateLimiter.acquire(
                    key = RATE_LIMIT_KEY,
                    windowSec = 3,
                    limit = 1,
                    maxWaitMillis = 15_000
                )

                if (!acquired) {
                    logger().warn("Rate limit timeout; skipping attempt=${attempt + 1}")
                    return@repeat
                }

                logger().info("arXiv API request attempt=${attempt + 1}/$MAX_RETRIES")

                val response = restClient.get()
                    .uri(url)
                    .accept(MediaType.APPLICATION_ATOM_XML)
                    .retrieve()
                    .body(ByteArray::class.java)

                if (response != null && response.isNotEmpty()) {
                    val responseStr = String(response, Charsets.UTF_8)
                    if (responseStr.trimStart().startsWith("<!DOCTYPE html>", ignoreCase = true) ||
                        responseStr.trimStart().startsWith("<html>", ignoreCase = true)) {
                        logger().warn("arXiv returned HTML error page, attempt=${attempt + 1}")

                        if (attempt < MAX_RETRIES - 1) {
                            val waitTime = INITIAL_RETRY_DELAY_MS * (attempt + 1)
                            logger().info("Waiting ${waitTime}ms before retry...")
                            Thread.sleep(waitTime)
                            return@repeat
                        }
                    } else {
                        logger().info("arXiv API request successful on attempt=${attempt + 1}")
                        return response
                    }
                }

            } catch (e: HttpClientErrorException) {
                logger().warn("arXiv HTTP 4xx error on attempt=${attempt + 1}: ${e.statusCode}")
                if (e.statusCode.value() == 429 && attempt < MAX_RETRIES - 1) {
                    val waitTime = INITIAL_RETRY_DELAY_MS * (attempt + 1) * 2
                    logger().info("Rate limited. Waiting ${waitTime}ms before retry...")
                    Thread.sleep(waitTime)
                } else {
                    throw e
                }
            } catch (e: HttpServerErrorException) {
                logger().warn("arXiv HTTP 5xx error on attempt=${attempt + 1}: ${e.statusCode}")
                if (attempt < MAX_RETRIES - 1) {
                    val waitTime = INITIAL_RETRY_DELAY_MS * (attempt + 1)
                    logger().info("Server error. Waiting ${waitTime}ms before retry...")
                    Thread.sleep(waitTime)
                }
            } catch (e: Exception) {
                logger().error("arXiv request failed on attempt=${attempt + 1}: ${e.message}", e)
                if (attempt < MAX_RETRIES - 1) {
                    val waitTime = INITIAL_RETRY_DELAY_MS * (attempt + 1)
                    logger().info("Waiting ${waitTime}ms before retry...")
                    Thread.sleep(waitTime)
                }
            }
        }

        logger().error("arXiv API request failed after $MAX_RETRIES attempts")
        return null
    }
}

