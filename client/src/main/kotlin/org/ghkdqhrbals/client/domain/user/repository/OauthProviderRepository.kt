package org.ghkdqhrbals.client.domain.user.repository

import org.ghkdqhrbals.client.domain.user.entity.OauthProvider
import org.ghkdqhrbals.oauth.service.OauthProviderKind
import org.springframework.data.jpa.repository.JpaRepository

interface OauthProviderRepository : JpaRepository<OauthProvider, Long> {
    fun findByKindAndProviderId(kind: OauthProviderKind, providerId: String): OauthProvider?
}
