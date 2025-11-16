package org.ghkdqhrbals.client.paper

interface PaperSearchService {
    /**
     * 카테고리와 Impact Factor 기준으로 최신 논문 검색
     */
    fun searchPapers(request: PaperSearchRequest): PaperSearchResponse

    /**
     * 특정 저널의 최신 논문 검색
     */
    fun searchPapersByJournal(journalName: String, maxResults: Int = 10): List<Paper>

    /**
     * 카테고리별 주요 저널 목록 조회
     */
    fun getTopJournals(category: String, minImpactFactor: Double? = null): List<Journal>
}

