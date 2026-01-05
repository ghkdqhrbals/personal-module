package org.ghkdqhrbals.client.config.stream

import com.fasterxml.jackson.databind.ObjectMapper

inline fun <reified T : Any> parseAutoClaimResult(
    raw: Any?,
    objectMapper: ObjectMapper,
    crossinline handler: (id: String, value: T) -> Unit
) {
    try {
        // XAUTOCLAIM 결과 구조:
        // [0] = next-id (cursor for next iteration) - String or ByteArray
        // [1] = List of claimed entries
        //   Each entry: [id, fields]
        //   id: String or ByteArray
        //   fields: List of [key1, val1, key2, val2, ...]

        val result = raw as? List<*> ?: return

        if (result.isEmpty()) return

        // result[0] = next-id
        @Suppress("UNUSED_VARIABLE")
        val nextId = when (val cursor = result[0]) {
            is ByteArray -> String(cursor)
            is String -> cursor
            else -> cursor?.toString() ?: ""
        }

        // result[1] = claimed entries list
        val claimed = result.getOrNull(1) as? List<*> ?: return

        claimed.forEach { entry ->
            try {
                // 각 entry는 [id, fields] 구조
                if (entry !is List<*> || entry.size < 2) return@forEach

                // entry[0] = message ID (String or ByteArray)
                val idRaw = entry[0]
                val id = when (idRaw) {
                    is ByteArray -> String(idRaw)
                    is String -> idRaw
                    else -> idRaw?.toString() ?: return@forEach
                }

                // entry[1] = fields (List<Any?>)
                val fields = entry[1] as? List<*> ?: return@forEach

                // fields를 Map으로 변환 (key1, val1, key2, val2, ...)
                val map = mutableMapOf<String, String>()
                var i = 0
                while (i < fields.size - 1) {
                    val keyRaw = fields[i]
                    val valRaw = fields[i + 1]

                    val key = when (keyRaw) {
                        is ByteArray -> String(keyRaw)
                        is String -> keyRaw
                        else -> null
                    }

                    val value = when (valRaw) {
                        is ByteArray -> String(valRaw)
                        is String -> valRaw
                        else -> null
                    }

                    if (key != null && value != null) {
                        map[key] = value
                    }

                    i += 2
                }

                if (map.isEmpty()) return@forEach

                // Map을 T로 변환
                try {
                    val value = objectMapper.convertValue(map, T::class.java)
                    handler(id, value)
                } catch (e: Exception) {
                    // 파싱 실패 무시
                }
            } catch (e: Exception) {
                // 엔트리 처리 실패 무시
            }
        }
    } catch (e: Exception) {
        // 전체 파싱 실패 무시
    }
}

data class ClaimedMessage<T>(
    val id: String,
    val value: T
)