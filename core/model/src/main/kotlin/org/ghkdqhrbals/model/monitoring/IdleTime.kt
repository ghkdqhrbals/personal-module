package org.ghkdqhrbals.model.monitoring

data class IdleTime(
    val millis: Long,
) {
    val short: String by lazy { format(millis).first }
    val human: String by lazy { format(millis).second }

    private fun format(ms: Long): Pair<String, String> {
        var r = ms
        val h = r / 3_600_000; r %= 3_600_000
        val m = r / 60_000;    r %= 60_000
        val s = r / 1_000

        val short = buildString {
            if (h > 0) append("${h}h")
            if (m > 0) append("${m}m")
            if (s > 0 || isEmpty()) append("${s}s")
        }
        val human = buildString {
            if (h > 0) append("${h}시간 ")
            if (m > 0) append("${m}분 ")
            if (s > 0 || isEmpty()) append("${s}초")
        }.trim()

        return short to human
    }
}