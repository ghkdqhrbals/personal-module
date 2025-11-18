package org.ghkdqhrbals.client.config

/**
 * 로그 타이틀 상수 모음.
 * 사용 예: logger().title(LogTitle.SUMMARY, "처리 완료")
 */
class LogTitle private constructor() {
    companion object {
        // 공통 도메인 카테고리
        const val STARTUP = "Startup"
        const val STREAM = "Stream"
        const val SUMMARY = "Summary"
        const val PAPER = "Paper"
        const val REDIS = "Redis"
        const val OLLAMA = "Ollama"
        const val SEARCH = "Search"
        const val EVENT = "Event"
        const val HTTP = "HTTP"
    }
}

