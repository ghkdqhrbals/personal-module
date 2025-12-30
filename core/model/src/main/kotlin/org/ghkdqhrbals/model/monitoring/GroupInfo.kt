package org.ghkdqhrbals.model.monitoring

data class GroupInfo(
    val name: String,
    val consumers: Long,
    val pending: Long,
    var consumerInfo: List<ConsumerInfo> = emptyList(),
    val lastDeliveredId: String
) {
    companion object {
        fun from(xInfoGroup: org.springframework.data.redis.connection.stream.StreamInfo.XInfoGroup?): GroupInfo? {
            return xInfoGroup?.let {
                GroupInfo(
                    name = it.groupName(),
                    consumers = it.consumerCount(),
                    pending = it.pendingCount(),
                    lastDeliveredId = it.lastDeliveredId()
                )
            }
        }
    }
    data class ConsumerInfo(
        val name: String,
        val pending: Long,
        val idleTime: IdleTime
    ) {
        companion object {
            fun from(xInfoConsumer: org.springframework.data.redis.connection.stream.StreamInfo.XInfoConsumer): ConsumerInfo {
                return ConsumerInfo(
                    name = xInfoConsumer.consumerName(),
                    pending = xInfoConsumer.pendingCount(),
                    idleTime = IdleTime(xInfoConsumer.idleTime().toMillis())
                )
            }
        }
    }
}
