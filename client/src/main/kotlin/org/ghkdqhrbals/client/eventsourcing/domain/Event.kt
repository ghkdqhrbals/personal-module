package org.ghkdqhrbals.client.eventsourcing.domain

import java.time.Instant
import java.util.*

/**
 * 이벤트 소싱의 기본 이벤트 인터페이스
 */
interface DomainEvent {
    val eventId: String
    val aggregateId: String
    val eventType: String
    val timestamp: Instant
    val version: Long
    val metadata: Map<String, Any>
}

/**
 * 기본 도메인 이벤트 구현체
 */
abstract class BaseDomainEvent(
    override val eventId: String = UUID.randomUUID().toString(),
    override val aggregateId: String,
    override val eventType: String,
    override val timestamp: Instant = Instant.now(),
    override val version: Long,
    override val metadata: Map<String, Any> = emptyMap()
) : DomainEvent

/**
 * Paper 도메인 이벤트들
 */
sealed class PaperEvent(
    eventId: String = UUID.randomUUID().toString(),
    aggregateId: String,
    eventType: String,
    timestamp: Instant = Instant.now(),
    version: Long,
    metadata: Map<String, Any> = emptyMap()
) : BaseDomainEvent(eventId, aggregateId, eventType, timestamp, version, metadata)

/**
 * 논문 검색 및 저장 이벤트
 * - arXiv 검색 요청
 * - 검색 결과 저장
 * - 진행 상태 추적
 */
data class PaperSearchAndStoreEvent(
    val searchEventId: String,  // 전체 검색 작업의 ID (유저에게 반환되는 ID)
    val query: String?,
    val categories: List<String>?,
    val maxResults: Int = 10,
    val page: Int = 0,
    val fromDate: String? = null,
    val shouldSummarize: Boolean = true,
    val ver: Long = 1,
    val meta: Map<String, Any> = emptyMap()
) : PaperEvent(
    aggregateId = searchEventId,
    eventType = "PaperSearchAndStore",
    version = ver,
    metadata = meta
)

/**
 * 요약 이벤트 (optional)
 * - 논문 요약 생성 요청
 */
data class SummaryEvent(
    val searchEventId: String,  // 원본 검색 이벤트 ID
    val paperId: String,
    val arxivId: String?,
    val title: String,
    val abstract: String?,
    val journalRefRaw: String? = null,
    val maxLength: Int = 120,
    val ver: Long = 1,
    val meta: Map<String, Any> = emptyMap()
) : PaperEvent(
    aggregateId = searchEventId,
    eventType = "Summary",
    version = ver,
    metadata = meta
)

