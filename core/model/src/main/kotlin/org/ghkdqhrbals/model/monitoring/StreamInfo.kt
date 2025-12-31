package org.ghkdqhrbals.model.monitoring

data class StreamInfo(
    val key: String,
    val length: Long?,
    val radixTreeKeys: Long?,
    val radixTreeNodes: Long?,
    val groupCount: Long?,
    val lastGeneratedId: String,
    val first: String?,
    val last: String?,
    val consumerInfo: List<GroupInfo.ConsumerInfo>? = null
) {
    companion object {
        fun from(streamKey: String, xInfoStream: org.springframework.data.redis.connection.stream.StreamInfo.XInfoStream?): StreamInfo {
            val firstEntry = xInfoStream?.firstEntry?.keys?.first().toString()
            val lastEntry  = xInfoStream?.lastEntry?.keys?.first().toString()
            return StreamInfo(
                key = streamKey,
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
