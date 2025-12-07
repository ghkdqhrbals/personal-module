package org.ghkdqhrbals.repository.common

import java.nio.ByteBuffer
import java.util.*

fun generateKey(): String {
    val uuid = UUID.randomUUID()
    val longValue = ByteBuffer.wrap(uuid.toString().toByteArray()).long
    return "${System.currentTimeMillis()}_${longValue.toString(Character.MAX_RADIX)}"
}