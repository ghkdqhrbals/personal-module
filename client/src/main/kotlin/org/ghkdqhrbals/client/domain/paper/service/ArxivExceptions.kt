package org.ghkdqhrbals.client.domain.paper.service

/**
 * ArXiv API 호출 실패 시 발생하는 예외
 */
class ArxivApiException(
    message: String,
    cause: Throwable? = null
) : RuntimeException(message, cause)

/**
 * ArXiv API가 HTML 에러 페이지를 반환했을 때 발생하는 예외
 */
class ArxivHtmlErrorException(
    message: String
) : RuntimeException(message)

