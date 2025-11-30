package org.ghkdqhrbals.client.domain.user.entity

import com.fasterxml.jackson.annotation.JsonIgnore
import org.hibernate.annotations.CreationTimestamp
import org.hibernate.annotations.UpdateTimestamp
import java.time.OffsetDateTime
import jakarta.persistence.*
import org.ghkdqhrbals.oauth.service.OauthProviderKind

@Entity
@Table(name = "oauth_providers")
class OauthProvider(
    @Column(name = "user_id")
    var userId: Long,
    /** oauth 업체에서 제공해준 유저의 고유 ID **/
    val providerId: String,
    @Enumerated(EnumType.STRING)
    var kind: OauthProviderKind,
) {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", updatable = false, insertable = false)
    lateinit var user: UserEntity

    @CreationTimestamp
    @Column(columnDefinition = "TIMESTAMP(6)")
    var createdAt: OffsetDateTime? = OffsetDateTime.now()

    @UpdateTimestamp
    @Column(columnDefinition = "TIMESTAMP(6)")
    var updatedAt: OffsetDateTime = OffsetDateTime.now()
}
