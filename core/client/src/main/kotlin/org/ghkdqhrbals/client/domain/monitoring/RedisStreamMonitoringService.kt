package org.ghkdqhrbals.client.domain.monitoring

import org.ghkdqhrbals.model.monitoring.GroupInfo
import org.ghkdqhrbals.model.monitoring.StreamMessage
import org.ghkdqhrbals.model.monitoring.StreamMessageResponse
import org.springframework.data.redis.connection.stream.PendingMessagesSummary
import org. springframework. data. domain. Range
import org.springframework.data.redis.connection.Limit
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service

@Service
class RedisStreamMonitoringService(
    private val redisTemplate: StringRedisTemplate
) {
    fun getStreamLength(streamKey: String): Long =
        redisTemplate.execute { it.streamCommands().xLen(streamKey.toByteArray()) } ?: 0L

    fun getPendingMessageSummary(streamKey: String, groupName: String): PendingMessagesSummary? {
        val execute = redisTemplate.execute {
            it.streamCommands().xPending(streamKey.toByteArray(), groupName)
        }
        return execute
    }

    fun getMessage(streamKey: String, startId: String, count: Long): List<Map<String, String>> {
        val messages = redisTemplate.execute { conn ->
            conn.streamCommands().xRange(
                streamKey.toByteArray(),
                Range.closed(startId, "+"),
                Limit.limit().count(count.toInt())
            )
        }

        return messages?.map { record ->
            val map = mutableMapOf<String, String>()
            map["id"] = record.id.value
            record.value.forEach { (key, value) ->
                map[key.decodeToString()] = value.decodeToString()
            }
            map
        } ?: emptyList()
    }

    /**
     * 페이지네이션을 지원하는 메시지 조회
     * @param streamKey Redis Stream 키
     * @param cursor 현재 커서 (null이면 처음부터, "-"는 가장 오래된 메시지부터)
     * @param pageSize 페이지당 메시지 수 (기본 10, 최대 100)
     * @return StreamMessageResponse (메시지 리스트, 다음 커서, 더보기 여부)
     */
    fun getMessagesWithPagination(
        streamKey: String,
        cursor: String?,
        pageSize: Int
    ): StreamMessageResponse {
        val actualPageSize = pageSize.coerceIn(1, 100)
        val startCursor = cursor ?: "-" // null이면 처음부터

        // 총 메시지 개수 조회
        val totalCount = getStreamLength(streamKey).toInt()

        // 실제로는 pageSize + 1 개를 조회하여 다음 페이지가 있는지 확인
        val messages = redisTemplate.execute { conn ->
            conn.streamCommands().xRange(
                streamKey.toByteArray(),
                Range.closed(startCursor, "+"),
                Limit.limit().count(actualPageSize + 1)
            )
        }

        if (messages.isNullOrEmpty()) {
            return StreamMessageResponse(
                messages = emptyList(),
                nextCursor = null,
                hasMore = false,
                totalCount = totalCount
            )
        }

        // pageSize보다 많이 조회되면 다음 페이지가 있다는 의미
        val hasMore = messages.size > actualPageSize
        val messageList = if (hasMore) {
            messages.take(actualPageSize)
        } else {
            messages
        }

        // 다음 커서는 마지막 메시지의 ID보다 큰 값
        val nextCursor = if (hasMore) {
            // Redis Stream ID는 "timestamp-sequence" 형식
            // 마지막 메시지의 ID를 다음 커서로 사용하되, exclusive하게 처리하기 위해
            // 다음 메시지 ID를 사용
            messages[actualPageSize].id.value
        } else {
            null
        }

        val streamMessages = messageList.map { record ->
            val fields = mutableMapOf<String, String>()
            record.value.forEach { (key, value) ->
                fields[key.decodeToString()] = value.decodeToString()
            }

            // Redis Stream ID에서 timestamp 추출 (ID 형식: "timestamp-sequence")
            val timestamp = record.id.value.split("-")[0].toLongOrNull() ?: 0L

            StreamMessage(
                id = record.id.value,
                timestamp = timestamp,
                fields = fields
            )
        }

        return StreamMessageResponse(
            messages = streamMessages,
            nextCursor = nextCursor,
            hasMore = hasMore,
            totalCount = totalCount
        )
    }


    fun getMessage(streamKey: String, messageId: String): Map<String, String>? {
        val messages = redisTemplate.execute { conn ->
            conn.streamCommands().xRange(
                streamKey.toByteArray(),
                Range.closed(messageId, messageId),
                Limit.limit().count(1)
            )
        }

        val record = messages?.firstOrNull() ?: return null

        val map = mutableMapOf<String, String>()
        map["id"] = record.id.value
        record.value.forEach { (key, value) ->
            map[key.decodeToString()] = value.decodeToString()
        }
        return map
    }

    fun getStreamGroups(streamKey: String): List<GroupInfo>? {
        val groups = redisTemplate.execute { it.streamCommands().xInfoGroups(streamKey.toByteArray()) }
        val execute = groups?.mapNotNull {
            val from = GroupInfo.from(it)
            from.apply {
                if (this != null) {
                    val consumers = getConsumers(streamKey, this.name)
                    this.consumerInfo = consumers ?: emptyList()
                }
            }

            from

        }
        return execute
    }

    fun getConsumers(streamKey: String, groupName: String): List<GroupInfo.ConsumerInfo>? {
        val consumers = redisTemplate.execute {
            it.streamCommands().xInfoConsumers(
                streamKey.toByteArray(),
                groupName
            )
        }

        val execute = consumers?.mapNotNull { GroupInfo.ConsumerInfo.from(it) }
        return execute
    }


    fun getStreamInfo(streamKey: String):  org.ghkdqhrbals.model.monitoring.StreamInfo? {
        val execute = redisTemplate.execute { it.streamCommands().xInfo(streamKey.toByteArray()) }
        val from = org.ghkdqhrbals.model.monitoring.StreamInfo.from(execute)
        return from
    }
}