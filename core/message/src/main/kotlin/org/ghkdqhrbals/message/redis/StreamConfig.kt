package org.ghkdqhrbals.message.redis

import kotlin.math.absoluteValue

interface StreamConfig {
    var streamKey: String
    var maxLen: Long
    var maxTryCount: Int
    var retryIntervalMs: Long
    val pollBatchSize: Int
}

interface Partitioned {
    var partitionNumbers: Int
    var delimiter: String
    fun partitions(): IntRange = 0 until partitionNumbers
    /**
     * 본 함수로 분배는 동일하게, consuming 은 모든 곳에서 처리하면 이후 stream partition 이 늘어나는 경우 대처 가능.
     *
     * 어느 파티션으로 보낼지 결정한다.
     */
    fun resolvePartition(key: String): Int =
        (key.hashCode().absoluteValue) % partitionNumbers
}

interface PartitionedStream : StreamConfig, Partitioned {
    fun getKey(partition: Int): String =
        "$streamKey$delimiter$partition"

    fun getAllStreamKeys(): List<String> =
        partitions().map { p -> getKey(p) }
}

interface CdlConfig {
    var cdlPostfix: String
    var cdlRetryCount: Int
    var cdlRetryIntervalMs: Long
    fun getCdlStreamKey(key: String): String =
        "$key$delimiter$cdlPostfix"
    companion object {
        const val delimiter = ":"
    }
}

interface PartitionedCdl : StreamConfig, Partitioned, CdlConfig {
    fun getCdlKey(partition: Int): String =
        "$streamKey$delimiter$partition$delimiter$cdlPostfix"
    fun getAllCdlKeys(): List<String> =
        partitions().map { p -> getCdlKey(p) }
}