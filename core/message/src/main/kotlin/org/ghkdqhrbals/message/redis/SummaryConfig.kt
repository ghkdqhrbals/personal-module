package org.ghkdqhrbals.message.redis

data class SummaryConfig(
    override var streamKey: String = Defaults.STREAM_KEY,
    override var maxLen: Long = Defaults.MAX_LEN,
    override var maxTryCount: Int = Defaults.MAX_TRY_COUNT,
    override var retryIntervalMs: Long = Defaults.RETRY_INTERVAL_MS,
    override var partitionNumbers: Int = Defaults.PARTITION_NUMBERS,
    override var delimiter: String = Defaults.DELIMITER,
    override var cdlPostfix: String = Defaults.CDL_POSTFIX,
    override var cdlRetryCount: Int = Defaults.CDL_RETRY_COUNT,
    override var cdlRetryIntervalMs: Long = Defaults.CDL_RETRY_INTERVAL_MS,
    override val pollBatchSize: Int = Defaults.POLL_BATCH_SIZE
) : PartitionedStream, PartitionedCdl {

    private object Defaults {
        const val PARTITION_NUMBERS = 6
        const val STREAM_KEY = "summary"
        const val MAX_LEN = 10_000L
        const val MAX_TRY_COUNT = 2
        const val RETRY_INTERVAL_MS = 1_00L
        const val DELIMITER = ":"
        const val CDL_POSTFIX = "CDL"
        const val CDL_RETRY_COUNT = 2
        const val CDL_RETRY_INTERVAL_MS = 3_00L // 50초
        const val POLL_BATCH_SIZE =  1 // 한번에 몇 개씩 가져올 건지
    }

    companion object {
        fun default() = SummaryConfig()
    }
}

