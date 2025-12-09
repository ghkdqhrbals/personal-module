package org.ghkdqhrbals.client.domain.scheduler.config

import org.ghkdqhrbals.infra.subscribe.Subscribe
import org.ghkdqhrbals.infra.subscribe.SubscribeRepository
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