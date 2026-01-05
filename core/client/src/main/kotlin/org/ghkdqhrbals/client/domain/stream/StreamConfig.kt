package org.ghkdqhrbals.client.domain.stream

class SummaryStreamConfig {
    companion object {
        const val CDL_POSTFIX = "CDL"
        const val CDL_RETRY_COUNT = 10
        const val CDL_RETRY_INTERVAL_MS = 1000L * 10 // 10초
        const val STREAM_KEY_PREFIX = "summary"
        const val MAX_PARTITIONS = 100L
        const val POLL_BATCH_SIZE = 1
        // 3회
        const val MAX_RETRY_COUNT = 3
        // 5초
        const val RETRY_INTERVAL_MS = 5000L
        const val PARTITION_NUMBERS = 6
        const val DELIMITER = ":"
        const val CONSUMER_GROUP_NAME = "summary-consumer-group"

        private fun partitions(): IntRange =
            0 until PARTITION_NUMBERS

        fun getAllStreamKeys(): List<String> =
            partitions().flatMap { p ->
                listOf(getDataStreamKey(p), getCdlStreamKey(p))
            }

        fun getDataStreamKey(partition: Int): String =
            "$STREAM_KEY_PREFIX$DELIMITER$partition"

        fun getCdlStreamKey(partition: Int): String =
            "$STREAM_KEY_PREFIX$DELIMITER$partition$DELIMITER$CDL_POSTFIX"

        fun getDataStreamKey(): List<String> =
            partitions().map(::getDataStreamKey)

        fun getCdlStreamKey(): List<String> =
            partitions().map(::getCdlStreamKey)

        fun getCdlStreamKey(key: String) = "$key$DELIMITER$CDL_POSTFIX"
    }
}