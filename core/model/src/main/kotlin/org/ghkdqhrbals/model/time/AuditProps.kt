package org.ghkdqhrbals.model.time

import java.time.OffsetDateTime

interface AuditProps {
    val createdAt: OffsetDateTime
    val updatedAt: OffsetDateTime
}