package org.ghkdqhrbals.message.redis

import org.springframework.data.redis.connection.stream.RecordId
import org.springframework.data.redis.connection.stream.StreamInfo
import org.springframework.data.redis.connection.stream.StreamRecords
import org.springframework.data.redis.core.RedisCallback
import org.springframework.data.redis.core.StringRedisTemplate

fun StringRedisTemplate.executeCommand(
    command: String,
    vararg args: String
): Any? {
    return this.execute { connection ->
        connection.execute(command, *args.map { it.toByteArray() }.toTypedArray())
    }
}

/**
 * Redis 명령어를 직접 실행 (Byte 배열로)
 * @receiver StringRedisTemplate
 * @param command Redis 명령어
 * @param args 명령어 인자들 (Byte 배열)
 * @return 명령어 실행 결과
 */
fun StringRedisTemplate.executeCommandBytes(
    command: String,
    vararg args: ByteArray
): Any? {
    return this.execute { connection ->
        connection.execute(command, *args)
    }
}

/**
 * return msg record id
 */
fun StringRedisTemplate.add(topic: String, data: Any): RecordId? {
    val id = this.opsForStream<String, String>().add(
        StreamRecords.newRecord().`in`(topic).ofObject(data)
    )
    return id
}

/**
 * RedisCallback을 사용하여 저수준 Redis 명령어 실행
 * @receiver StringRedisTemplate
 * @param callback 실행할 콜백
 * @return 콜백 실행 결과
 */
fun <T> StringRedisTemplate.executeCallback(
    callback: RedisCallback<T>
): T? {
    return this.execute(callback)
}

/**
 * XINFO STREAM 명령어 실행
 * Stream 정보를 조회합니다.
 * @receiver StringRedisTemplate
 * @param key Stream 키
 * @return Stream 정보
 */
fun StringRedisTemplate.infoStream(key: String): StreamInfo. XInfoStream {
    val info = this.opsForStream<String, String>().info(key)
    return info
}

/**
 * XACKDEL key group [KEEPREF | DELREF | ACKED] IDS numids id [id ...]
 *
 * redis 8.2 new feature. only work with redis server 8.2 or above
 */
enum class XAckDelResult(val code: Int) {
    ACK_AND_DEL(1),
    ACK_ONLY(2),
    NOT_EXISTS(-1);

    companion object {
        fun from(code: Int): XAckDelResult =
            values().firstOrNull { it.code == code } ?: NOT_EXISTS
    }
}

fun StringRedisTemplate.xAckDel(
    topic: String,
    groupName: String,
    messageIds: List<String>
): Map<String, XAckDelResult> {

    val args = mutableListOf<ByteArray>()
    args += topic.toByteArray()
    args += groupName.toByteArray()
    args += "DELREF".toByteArray()
    args += "IDS".toByteArray()
    args += messageIds.size.toString().toByteArray()
    messageIds.forEach { args += it.toByteArray() }

    val raw = this.execute { connection ->
        connection.execute("XACKDEL", *args.toTypedArray())
    }

    val results = when (raw) {
        is List<*> -> raw.map {
            when (it) {
                is Long -> XAckDelResult.from(it.toInt())
                is ByteArray -> XAckDelResult.from(String(it).toInt())
                else -> XAckDelResult.NOT_EXISTS
            }
        }
        else -> List(messageIds.size) { XAckDelResult.NOT_EXISTS }
    }

    return messageIds
        .zip(results)
        .associate { (id, result) ->
            id to result
        }
}