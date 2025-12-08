package org.ghkdqhrbals.client.domain.scheduler.config

import org.ghkdqhrbals.client.config.log.logger
import org.springframework.batch.item.ItemReader
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.ghkdqhrbals.repository.subscribe.Subscribe
import org.ghkdqhrbals.repository.subscribe.SubscribeRepository

class SubscribeReader(
    private val repo: SubscribeRepository,
    private val chunkSize: Int
) : ItemReader<Subscribe> {

    private var currentIndex = 0
    private var currentPage = 0
    private var buffer: List<Subscribe> = emptyList()

    override fun read(): Subscribe? {
        logger().info("🔍 구독 청크 리더가 구독을 읽는 중... (현재 페이지: $currentPage, 현재 인덱스: $currentIndex)")
        if (currentIndex >= buffer.size) {
            buffer = loadNextPage() ?: return null
            currentIndex = 0
        }

        if (buffer.isEmpty()) {
            return null
        }

        val item = buffer[currentIndex]
        currentIndex++
        logger().info("▶️ 읽은 구독: [Subscribe#${item.id}] '${item.name}' (${item.subscribeType})")
        return item
    }

    private fun loadNextPage(): List<Subscribe>? {
        val page = PageRequest.of(currentPage, chunkSize, Sort.by(Sort.Direction.ASC, "id"))
        val result = repo.findAllByActivatedIsTrue(page)
        logger().info("구독 청크 리더가 페이지 $currentPage 에서 ${result.content.size}개의 구독을 로드했습니다.")

        if (result.isEmpty) {
            return null
        }

        currentPage++
        return result.content
    }
}