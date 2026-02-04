package org.ghkdqhrbals.client.domain.monitoring

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.ghkdqhrbals.client.config.log.logger
import org.ghkdqhrbals.model.config.Jackson
import org.ghkdqhrbals.model.monitoring.GroupInfo
import org.ghkdqhrbals.model.monitoring.StreamMessage
import org.ghkdqhrbals.model.monitoring.StreamMessageResponse
import org.springframework.data.redis.connection.stream.PendingMessagesSummary
import org.springframework.data.domain.Range
import org.springframework.data.redis.connection.Limit
import org.springframework.data.redis.connection.RedisClusterConnection
import org.springframework.data.redis.connection.RedisStandaloneConfiguration
import org.springframework.data.redis.connection.ReturnType
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory
import org.springframework.data.redis.core.ScanOptions
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

inline fun Long.toHumanBytes(): String {
    if (this <= 0) return "0B"
    val units = arrayOf("B", "K", "M", "G", "T")
    var value = this.toDouble()
    var idx = 0

    while (value >= 1024 && idx < units.lastIndex) {
        value /= 1024
        idx++
    }
    return String.format("%.2f%s", value, units[idx])
}

@Service
class RedisStreamMonitoringService(
    private val redisTemplate: StringRedisTemplate
) {
    private val nodeFactoryCache =
        ConcurrentHashMap<String, LettuceConnectionFactory>()

    private fun getOrCreateFactory(host: String, port: Int): LettuceConnectionFactory =
        nodeFactoryCache.computeIfAbsent("$host:$port") {
            LettuceConnectionFactory(
                RedisStandaloneConfiguration(host, port)
            ).apply { afterPropertiesSet() }
        }

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

    data class MemoryUsageInfo(
        val streamKeyMemory: Long,
        val maxMemory: Long,
        val usagePercentage: Double,
        val nodeId: String? = null,
        val nodeAddress: String? = null
    )

    data class NodeMemoryInfo(
        val nodeId: String,
        val host: String,
        val port: Int,
        val maxMemory: Long,
        val usedMemory: Long,
        val usedMemoryHuman: String,
        val maxMemoryHuman: String,
        val memoryUsagePercentage: Double,
        val streamMemorySizeHuman: String,
        val streamBytes: Long
    )

    fun infoByKeyNode(streamKey: String): NodeMemoryInfo? {
        return try {
            val slot = io.lettuce.core.cluster.SlotHash.getSlot(streamKey)
            val clusterConnection = redisTemplate.connectionFactory?.let {
                if (it is LettuceConnectionFactory) {
                    it.getClusterConnection()
                } else {
                    null
                }
            } ?: run {
                // 클러스터가 아닌 경우 일반 연결에서 정보 조회
                logger().debug("Not a cluster connection, trying standalone info")
                return getStandaloneNodeMemoryInfo(streamKey)
            }

            // 슬롯을 담당하는 노드 찾기
            var targetNodeInfo: NodeMemoryInfo? = null

            clusterConnection.clusterGetNodes().forEach { node ->
                if (node.servesSlot(slot)) {
                    logger().debug("Found node serving slot {} for key {}: {}:{}", slot, streamKey, node.host, node.port)
                    val factory = getOrCreateFactory(node.host!!, node.port!!)

                    factory.connection.use { conn ->
                        val infoMemory = conn.execute("INFO", "memory".toByteArray())
                        val infoServer = conn.execute("INFO", "server".toByteArray())

                        val memoryInfo = when (infoMemory) {
                            is ByteArray -> String(infoMemory, Charsets.UTF_8)
                            is String -> infoMemory
                            else -> ""
                        }

                        val serverInfo = when (infoServer) {
                            is ByteArray -> String(infoServer, Charsets.UTF_8)
                            is String -> infoServer
                            else -> ""
                        }

                        val script = "return redis.call('MEMORY','USAGE', KEYS[1])"
                        val streamBytes = (conn.eval(
                            script.toByteArray(),
                            ReturnType.INTEGER,
                            1,
                            streamKey.toByteArray()
                        ) as? Long) ?: 0L


                        targetNodeInfo = parseMemoryInfo(memoryInfo, serverInfo, node.host!!, node.port!!, streamBytes)
                    }
                }
            }

            targetNodeInfo ?: run {
                logger().warn("No node serves the slot for key: {}", streamKey)
                null
            }
        } catch (e: Exception) {
            logger().error("Error fetching node memory info for key {}: {}", streamKey, e.message, e)
            null
        }
    }

    private fun getStandaloneNodeMemoryInfo(streamKey: String): NodeMemoryInfo? {
        return try {
            val infoMemory = redisTemplate.execute { conn ->
                val result = conn.execute("INFO", "memory".toByteArray()) as? ByteArray
                result?.let { String(it, Charsets.UTF_8) }
            }

            val infoServer = redisTemplate.execute { conn ->
                val result = conn.execute("INFO", "server".toByteArray()) as? ByteArray
                result?.let { String(it, Charsets.UTF_8) }
            }

            // streamBytes 조회
            val streamBytes = redisTemplate.execute { conn ->
                try {
                    val debugObj = conn.execute("DEBUG", "OBJECT".toByteArray(), streamKey.toByteArray())
                    val debugStr = when (debugObj) {
                        is ByteArray -> String(debugObj, Charsets.UTF_8)
                        is String -> debugObj
                        else -> ""
                    }
                    debugStr.substringAfter("serializedlength:", "")
                        .substringBefore(" ")
                        .toLongOrNull() ?: 0L
                } catch (e: Exception) {
                    logger().debug("Failed to get stream bytes: {}", e.message)
                    0L
                }
            } ?: 0L

            val factory = redisTemplate.connectionFactory as LettuceConnectionFactory
            val host = factory.standaloneConfiguration.hostName
            val port = factory.standaloneConfiguration.port

            if (infoMemory != null && infoServer != null) {
                parseMemoryInfo(infoMemory, infoServer, host, port, streamBytes)
            } else {
                null
            }
        } catch (e: Exception) {
            logger().error("Error fetching standalone node memory info: {}", e.message, e)
            null
        }
    }

    private fun parseMemoryInfo(memoryInfo: String, serverInfo: String, host: String, port: Int, streamBytes: Long = 0L): NodeMemoryInfo? {
        return try {
            var maxMemory = 0L
            var usedMemory = 0L
            var usedMemoryHuman = ""
            var maxMemoryHuman = ""
            var nodeId = "unknown"

            // Memory info 파싱
            memoryInfo.split("\r\n").forEach { line ->
                when {
                    line.startsWith("maxmemory:") -> maxMemory = line.substringAfter(":").trim().toLongOrNull() ?: 0L
                    line.startsWith("used_memory:") -> usedMemory = line.substringAfter(":").trim().toLongOrNull() ?: 0L
                    line.startsWith("used_memory_human:") -> usedMemoryHuman = line.substringAfter(":").trim()
                    line.startsWith("maxmemory_human:") -> maxMemoryHuman = line.substringAfter(":").trim()
                }
            }

            // Server info 파싱 (nodeId)
            serverInfo.split("\r\n").forEach { line ->
                if (line.startsWith("run_id:")) {
                    nodeId = line.substringAfter(":").trim()
                }
            }

            val memoryUsagePercentage = if (maxMemory > 0) {
                (usedMemory.toDouble() / maxMemory.toDouble()) * 100.0
            } else {
                0.0
            }

            logger().debug("Parsed node info: host={}, port={}, maxMemory={}, usedMemory={}, percentage={}, streamBytes={}",
                host, port, maxMemory, usedMemory, memoryUsagePercentage, streamBytes)

            NodeMemoryInfo(
                nodeId = nodeId,
                host = host,
                port = port,
                maxMemory = maxMemory,
                usedMemory = usedMemory,
                usedMemoryHuman = usedMemoryHuman,
                maxMemoryHuman = maxMemoryHuman,
                memoryUsagePercentage = memoryUsagePercentage,
                streamBytes = streamBytes,
                streamMemorySizeHuman = streamBytes.toHumanBytes()
            )
        } catch (e: Exception) {
            logger().error("Error parsing memory info: {}", e.message, e)
            null
        }
    }

    // ...existing code...

    /**
     * 페이지네이션을 지원하는 메시지 조회 (최신 메시지부터 역순)
     * @param streamKey Redis Stream 키
     * @param cursor 현재 커서 (null이면 처음부터, "+"는 가장 최신 메시지부터)
     * @param pageSize 페이지당 메시지 수 (기본 10, 최대 100)
     * @return StreamMessageResponse (메시지 리스트, 다음 커서, 더보기 여부)
     */
    fun getMessagesWithPagination(
        streamKey: String,
        cursor: String?,
        pageSize: Int
    ): StreamMessageResponse {
        val actualPageSize = pageSize

        val totalCount = getStreamLength(streamKey).toInt()

        // xRevRange: descending order (최신 → 오래된)
        // Range.of(Bound, Bound)를 사용하여 명시적으로 순서 지정
        val range = if (cursor == null) {
            // 최신(+)부터 가장 오래된(-)까지
            Range.closed("+", "-")
        } else {
            // cursor보다 오래된 메시지들
            // cursor(exclusive upper bound) ~ -(inclusive lower bound)
            Range.rightOpen("-", cursor)
        }

        val messages = redisTemplate.execute { conn ->
            conn.streamCommands().xRevRange(
                streamKey.toByteArray(),
                range,
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

        val hasMore = messages.size > actualPageSize
        val messageList = if (hasMore) {
            messages.take(actualPageSize)
        } else {
            messages
        }

        // nextCursor는 실제로 표시된 마지막 메시지의 ID
        val nextCursor = if (hasMore) {
            messageList.last().id.value
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


    fun getMessage(streamKey: String, messageId: String): StreamMessage? {
        val messages = redisTemplate.execute { conn ->
            conn.streamCommands().xRange(
                streamKey.toByteArray(),
                Range.closed(messageId, messageId),
                Limit.limit().count(1)
            )
        }

        val record = messages?.firstOrNull() ?: return null

        val fields = mutableMapOf<String, String>()
        record.value.forEach { (key, value) ->
            fields[key.decodeToString()] = value.decodeToString()
        }

        val timestamp = record.id.value.split("-")[0].toLongOrNull() ?: 0L

        return StreamMessage(
            id = record.id.value,
            timestamp = timestamp,
            fields = fields
        )
    }
    data class GroupLag(
        val name: String,
        val lag: Long? = null,
        val pending: Long? = null,
        val consumers: Long? = null
    )

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
          local lagValue = groupInfo['lag']
          local pendingValue = groupInfo['pending']
          local consumersValue = groupInfo['consumers']
          
          -- null이거나 false인 값을 0으로 변환
          if not lagValue or lagValue == false then
            lagValue = 0
          end
          if not pendingValue or pendingValue == false then
            pendingValue = 0
          end
          if not consumersValue or consumersValue == false then
            consumersValue = 0
          end
          
          table.insert(result, {
            name = groupInfo['name'],
            lag = lagValue,
            pending = pendingValue,
            consumers = consumersValue
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

            logger().debug("Lua script returned for stream {}: {}", stream, jsonString)

            try {
                val parsed = om.readValue(jsonString, object : TypeReference<List<GroupLag>>() {})
                parsed.forEach { lag ->
                    logger().debug("Stream {} group {} - lag: {}, pending: {}, consumers: {}",
                        stream, lag.name, lag.lag, lag.pending, lag.consumers)
                }
                parsed
            } catch (e: Exception) {
                logger().error("Error deserializing lag data from JSON: {} | JSON: {}", e.message, jsonString, e)
                emptyList()
            }
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

        val execute = groups?.mapNotNull { xInfoGroup ->
            val groupName = xInfoGroup.groupName()
            val lagInfo = lagMap[groupName]
            val lag = lagInfo?.lag ?: 0L

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