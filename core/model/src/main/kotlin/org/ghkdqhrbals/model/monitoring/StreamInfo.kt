package org.ghkdqhrbals.model.monitoring

data class StreamInfo(
    val length: Long?,
    val radixTreeKeys: Long?,
    val radixTreeNodes: Long?,
    val groupCount: Long?,
    val lastGeneratedId: String,
    val first: String?,
    val last: String?
) {
    companion object {
        fun from(xInfoStream: org.springframework.data.redis.connection.stream.StreamInfo.XInfoStream?): StreamInfo {
            val firstEntry = xInfoStream?.firstEntry?.keys?.first().toString()
            val lastEntry  = xInfoStream?.lastEntry?.keys?.first().toString()
            return StreamInfo(
                length = xInfoStream?.streamLength(),
                radixTreeKeys = xInfoStream?.radixTreeKeySize(),
                radixTreeNodes = xInfoStream?.radixTreeNodesSize(),
                groupCount = xInfoStream?.groupCount(),
                lastGeneratedId = xInfoStream?.lastGeneratedId() ?: "0-0",
                first = firstEntry,
                last = lastEntry
            )
        }
    }
}
