package org.ghkdqhrbals.infra.subscribe

import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface SubscribeRepository : JpaRepository<Subscribe, Long> {
    fun findByName(name: String): Subscribe?
    fun existsByName(name: String): Boolean
    fun findBySubscribeType(subscribeType: SubscribeType): List<Subscribe>
    fun findAllByActivatedIsTrue(): List<Subscribe>
    fun findAllByActivatedIsTrue(pageable: Pageable): Page<Subscribe>
}

