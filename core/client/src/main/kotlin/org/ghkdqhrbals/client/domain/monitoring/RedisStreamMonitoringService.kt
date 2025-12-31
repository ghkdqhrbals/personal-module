package org.ghkdqhrbals.client.domain.monitoring

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.ghkdqhrbals.client.config.log.logger
import org.ghkdqhrbals.model.config.Jackson
import org.ghkdqhrbals.model.monitoring.GroupInfo
import org.ghkdqhrbals.model.monitoring.StreamMessage
import org.ghkdqhrbals.model.monitoring.StreamMessageResponse
import org.springframework.data.redis.connection.stream.PendingMessagesSummary
import org. springframework. data. domain. Range
import org.springframework.data.redis.connection.Limit
import org.springframework.data.redis.connection.ReturnType
import org.springframework.data.redis.core.ScanOptions
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
    fun getStreamPartitionInfo(key: String, maxPartition: Long = 100): List<String>? {
        val options = ScanOptions.scanOptions().match("$key:*").count(maxPartition).build()
        return redisTemplate.execute { conn ->
            conn.scan(options).asSequence().map { String(it) }.toList()
        } ?: emptyList()

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
    data class GroupLag(val name: String, val lag: Long?, val pending: Long?, val consumers: Long?)

    private val script = """
        local stream = KEYS[1]
        local groups = redis.call('XINFO','GROUPS',stream)
        local result = {}
        for _, group in ipairs(groups) do
          local groupInfo = {}
          for i = 1, #group, 2 do
            local key = group[i]
            local value = group[i + 1]
            groupInfo[key] = value
          end
          table.insert(result, {
            name = groupInfo['name'],
            lag = groupInfo['lag'],
            pending = groupInfo['pending'],
            consumers = groupInfo['consumers']
          })
        end
        return cjson.encode(result)
    """.trimIndent()

    fun fetchLag(stream: String, om: ObjectMapper): List<GroupLag> {
        return try {
            @Suppress("DEPRECATION")
            val result: Any? = redisTemplate.execute { conn ->
                conn.eval(script.toByteArray(), ReturnType.VALUE, 1, stream.toByteArray())
            }

            // ByteArray를 String으로 변환
            val jsonString = when (result) {
                is ByteArray -> String(result, Charsets.UTF_8)
                is String -> result
                else -> {
                    logger().warn("Unexpected result type: {}", result?.javaClass?.name)
                    "[]"
                }
            }

            logger().info("Raw Lua script result: {}", jsonString)


            val parsed = om.readValue(jsonString, object : TypeReference<List<GroupLag>>() {})
            logger().info("Parsed GroupLag list: {}", parsed)

            parsed
        } catch (e: Exception) {
            logger().error("Error fetching lag from Redis: {}", e.message, e)
            emptyList()
        }
    }


    fun getStreamGroups(streamKey: String): List<GroupInfo>? {
        val groups = redisTemplate.execute { it.streamCommands().xInfoGroups(streamKey.toByteArray()) }

        // Lua 스크립트로 lag 정보 조회
        val om = Jackson.getMapper()
        val lagList = fetchLag(streamKey, om)
        val lagMap = lagList.associateBy { it.name }

        logger().info("Stream: {}, Fetched lag info: {}", streamKey, lagMap)

        val execute = groups?.mapNotNull { xInfoGroup ->
            val groupName = xInfoGroup.groupName()
            val lagInfo = lagMap[groupName]
            val lag = lagInfo?.lag ?: 0L

            logger().info("Group: {}, lagInfo: {}, final lag: {}", groupName, lagInfo, lag)

            val from = GroupInfo.from(xInfoGroup, lag)
            from?.apply {
                val consumers = getConsumers(streamKey, this.name)
                this.consumerInfo = consumers ?: emptyList()
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
        val from = org.ghkdqhrbals.model.monitoring.StreamInfo.from(streamKey, execute)
        return from
    }
}