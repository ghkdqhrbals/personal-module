package org.ghkdqhrbals.model.paper

data class SummaryEvent(
    val searchEventId: String,
    val paperId: String,
    val arxivId: String,
    val title: String,
    val abstract: String,
    val journalRefRaw: String?
)