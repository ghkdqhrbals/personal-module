package org.ghkdqhrbals.client.domain.scheduler.config

import jakarta.persistence.EntityManager
import jakarta.persistence.EntityManagerFactory
import org.ghkdqhrbals.client.config.log.logger
import org.ghkdqhrbals.client.domain.subscribe.entity.Subscribe
import org.ghkdqhrbals.client.domain.subscribe.repository.SubscribeRepository
import org.springframework.batch.item.Chunk
import org.springframework.batch.item.ItemWriter
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Transactional

@Component
class SubscribeWriter(
    private val repo: SubscribeRepository
) : ItemWriter<Subscribe> {

    @Transactional
    override fun write(items: Chunk<out Subscribe>) {
        repo.saveAll(items.toList())
    }

}