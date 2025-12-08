package org.ghkdqhrbals.repository.user

import org.ghkdqhrbals.oauth.service.OauthProviderKind
import org.springframework.data.jpa.repository.JpaRepository

interface OauthProviderRepository : JpaRepository<OauthProvider, Long> {
    fun findByKindAndProviderId(kind: OauthProviderKind, providerId: String): OauthProvider?
}
