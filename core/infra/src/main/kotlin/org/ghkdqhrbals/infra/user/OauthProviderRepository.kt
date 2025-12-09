package org.ghkdqhrbals.infra.user

import org.ghkdqhrbals.oauth.service.OauthProviderKind
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.repository.CrudRepository

interface OauthProviderRepository : JpaRepository<OauthProviderEntity, Long> {
    fun findByKindAndProviderId(kind: OauthProviderKind, providerId: String): OauthProviderEntity?
}