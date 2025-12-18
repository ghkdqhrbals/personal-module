package org.ghkdqhrbals.model.user

import org.ghkdqhrbals.model.time.AuditProps
import java.time.OffsetDateTime

data class UserModel(
    override val createdAt: OffsetDateTime,
    override val updatedAt: OffsetDateTime,
    val id: Long? = null,
    val identity: String? = null,
    val password: String ?= null,
    val name: String,
    val phoneNumber: String? = null,
    val gender: Gender? = Gender.UNKNOWN,
    val birth: OffsetDateTime? = null,
    val email: String? = null,
    val ci: String? = null,
    val status: Status,
    val certifiedAt: OffsetDateTime? = null,
    val note: String? = null,
    val deletedAt: OffsetDateTime?,
    val activatedAt: OffsetDateTime?,
    val migrationId: String? = null,
    val oauthProviders: List<OauthProviderModel>? = emptyList()
) : AuditProps

enum class Gender {
    MALE, FEMALE, UNKNOWN
}

enum class Status {
    INIT, ACTIVE, INACTIVE, DELETED
}

data class OauthProviderModel(
    val id: Long?,
    val userId: Long?,
    val provider: String,
    val providerId: String,
    val accessToken: String?,
    val refreshToken: String?,
    val createdAt: OffsetDateTime,
    val updatedAt: OffsetDateTime
)

