package org.ghkdqhrbals.model.paper

class PaperSearchAndStoreEvent(
    val searchEventId: String,
    val query: String,
    val categories: List<String>? = null,
    val maxResults: Int,
    val page: Int,
    val shouldSummarize: Boolean = false,
    val fromDate: String? = null,
) {
}