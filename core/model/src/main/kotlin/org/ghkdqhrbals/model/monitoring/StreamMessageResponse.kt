package org.ghkdqhrbals.model.monitoring

data class StreamMessageResponse(
    val messages: List<StreamMessage>,
    val nextCursor: String?,
    val hasMore: Boolean,
    val totalCount: Int
)

data class StreamMessage(
    val id: String,
    val timestamp: Long,
    val fields: Map<String, String>
)

data class StreamMessagePageRequest(
    val cursor: String? = null,  // null이면 처음부터, "-"로 시작 가능
    val pageSize: Int = 10
) {
    init {
        require(pageSize in 1..100) { "pageSize must be between 1 and 100" }
    }
}

