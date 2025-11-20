package org.ghkdqhrbals.client.common

/**
 * Rate limiter에서 토큰 획득 타임아웃 시 발생하는 예외
 */
class LockTimeoutException(
    message: String,
    val key: String,
    val attemptCount: Int,
    val elapsedMillis: Long
) : RuntimeException(message)

