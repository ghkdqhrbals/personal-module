package org.ghkdqhrbals.client.domain.user.entity

import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
import jakarta.persistence.CascadeType
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import jakarta.persistence.Table
import org.ghkdqhrbals.client.common.generateKey
import org.ghkdqhrbals.client.domain.user.constant.Gender
import org.ghkdqhrbals.client.domain.user.constant.Status
import kotlin.math.absoluteValue

@Entity
@Table(name = "users")
class UserEntity private constructor(
    var identity: String,
    var password: String,
    var name: String,
    var phoneNumber: String,
    @Enumerated(EnumType.STRING)
    var gender: Gender = Gender.UNKNOWN,
    var birth: OffsetDateTime? = null,
    var email: String? = null,
) {
    companion object {
        fun defUser() = UserEntity(
            name = "no name",
            identity = generateKey(),
            password = generateKey(),
            phoneNumber = "",
        )
    }

    val age: Long
        get() = birth?.let {
            val now = OffsetDateTime.now()
            val age = it.until(now, ChronoUnit.YEARS)
            age.absoluteValue
        } ?: 0

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0

    var ci: String? = null

    @Enumerated(EnumType.STRING)
    var status = Status.INIT // 기본적으로 본인인증 이후, 회원가입을 함

    var certifiedAt: OffsetDateTime? = null

    var note: String? = null

    @OneToMany(mappedBy = "user", cascade = [CascadeType.ALL])
    var oauthProviders: MutableList<OauthProvider> = mutableListOf()
    var deletedAt: OffsetDateTime? = null
    var releaseAt: OffsetDateTime? = null

    var deleteReason: String? = null

    @CreationTimestamp
    var createdAt: OffsetDateTime = OffsetDateTime.now()

    @UpdateTimestamp
    var updatedAt: OffsetDateTime = OffsetDateTime.now()
    var activatedAt: OffsetDateTime? = null

    var migrationId: String? = null

    fun release() {
        this.releaseAt = OffsetDateTime.now().minusSeconds(1)
    }

    fun maskName(): String {
        return if (name.isEmpty()) {
            ""
        } else {
            val firstLetter = name[0]
            val maskedPart = "*".repeat(name.length - 1)
            "$firstLetter$maskedPart"
        }
    }
}
