package org.ghkdqhrbals.client.domain.user.entity

import jakarta.persistence.*
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.OffsetDateTime
import java.time.temporal.ChronoUnit
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

    @Column(columnDefinition = "DATETIME(6)")
    var certifiedAt: OffsetDateTime? = null

    var note: String? = null

    @OneToMany(mappedBy = "user", cascade = [CascadeType.ALL])
    var oauthProviders: MutableList<OauthProvider> = mutableListOf()
    @Column(columnDefinition = "DATETIME(6)")
    var deletedAt: OffsetDateTime? = null

    @CreationTimestamp
    @Column(columnDefinition = "DATETIME(6)")
    var createdAt: OffsetDateTime = OffsetDateTime.now()

    @UpdateTimestamp
    @Column(columnDefinition = "DATETIME(6)")
    var updatedAt: OffsetDateTime = OffsetDateTime.now()
    @Column(columnDefinition = "DATETIME(6)")
    var activatedAt: OffsetDateTime? = null

    var migrationId: String? = null

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
