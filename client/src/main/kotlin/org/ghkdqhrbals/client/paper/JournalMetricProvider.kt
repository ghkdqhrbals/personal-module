package org.ghkdqhrbals.client.paper

interface JournalMetricProvider {
    /**
     * 저널의 Impact Factor 조회
     * @param journalName 저널명
     * @param year 발표 연도 (null이면 최신 연도 사용)
     * @return (Impact Factor, 해당 연도) 또는 null
     */
    fun getLatestImpactMetric(journalName: String, year: Int? = null): Pair<Double, Int>?
}
