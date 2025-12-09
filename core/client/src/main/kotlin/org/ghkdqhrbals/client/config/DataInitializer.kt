package org.ghkdqhrbals.client.config

import org.ghkdqhrbals.client.config.log.setting
import org.ghkdqhrbals.infra.subscribe.Subscribe
import org.ghkdqhrbals.infra.subscribe.SubscribeType
import org.ghkdqhrbals.infra.subscribe.SubscribeRepository
import org.slf4j.LoggerFactory
import org.springframework.boot.CommandLineRunner
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.transaction.annotation.Transactional

@Configuration
class DataInitializer {

    private val logger = LoggerFactory.getLogger(DataInitializer::class.java)

    @Bean
    @Transactional
    fun initSubscribeData(subscribeRepository: SubscribeRepository): CommandLineRunner {
        return CommandLineRunner {
            // 이미 데이터가 있으면 초기화하지 않음
            if (subscribeRepository.count() > 0) {
                logger.setting("구독 데이터가 이미 존재합니다. 초기화를 건너뜁니다.")
                return@CommandLineRunner
            }

            logger.setting("구독 주제 초기 데이터를 생성합니다...")

            // arXiv 카테고리 구독 주제
//            val categories = listOf(
//                Subscribe("cs.AI", "Artificial Intelligence - Covers all areas of AI except Vision, Robotics, Machine Learning", SubscribeType.CATEGORY),
//                Subscribe("cs.LG", "Machine Learning - Papers on machine learning", SubscribeType.CATEGORY),
//                Subscribe("cs.CL", "Computation and Language - Natural language processing", SubscribeType.CATEGORY),
//                Subscribe("cs.CV", "Computer Vision and Pattern Recognition", SubscribeType.CATEGORY),
//                Subscribe("cs.NE", "Neural and Evolutionary Computing", SubscribeType.CATEGORY),
//                Subscribe("cs.RO", "Robotics", SubscribeType.CATEGORY),
//                Subscribe("cs.CR", "Cryptography and Security", SubscribeType.CATEGORY),
//                Subscribe("cs.DB", "Databases", SubscribeType.CATEGORY),
//                Subscribe("cs.DC", "Distributed, Parallel, and Cluster Computing", SubscribeType.CATEGORY),
//                Subscribe("cs.SE", "Software Engineering", SubscribeType.CATEGORY)
//            )

            // 인기 키워드 구독 주제
            val keywords = listOf(
                Subscribe("Transformer", "Papers related to Transformer architecture", SubscribeType.KEYWORD),
//                Subscribe("GPT", "Generative Pre-trained Transformer related papers", SubscribeType.KEYWORD),
//                Subscribe("BERT", "BERT and bidirectional transformer papers", SubscribeType.KEYWORD),
//                Subscribe("Diffusion Models", "Papers on diffusion models and generative AI", SubscribeType.KEYWORD),
//                Subscribe("Reinforcement Learning", "Papers on RL algorithms and applications", SubscribeType.KEYWORD),
//                Subscribe("Zero-Shot Learning", "Zero-shot and few-shot learning papers", SubscribeType.KEYWORD),
//                Subscribe("Large Language Model", "LLM related research papers", SubscribeType.KEYWORD),
//                Subscribe("Neural Architecture Search", "NAS and AutoML papers", SubscribeType.KEYWORD),
//                Subscribe("Federated Learning", "Distributed and federated learning", SubscribeType.KEYWORD),
//                Subscribe("Explainable AI", "XAI and interpretability research", SubscribeType.KEYWORD)
            )

            // 유명 저자 구독 주제
//            val authors = listOf(
//                Subscribe("Yoshua Bengio", "Papers by Yoshua Bengio", SubscribeType.AUTHOR),
//                Subscribe("Geoffrey Hinton", "Papers by Geoffrey Hinton", SubscribeType.AUTHOR),
//                Subscribe("Yann LeCun", "Papers by Yann LeCun", SubscribeType.AUTHOR),
//                Subscribe("Andrew Ng", "Papers by Andrew Ng", SubscribeType.AUTHOR),
//                Subscribe("Ilya Sutskever", "Papers by Ilya Sutskever", SubscribeType.AUTHOR)
//            )

            // 모든 구독 주제 저장
            val allSubscribes = keywords
            subscribeRepository.saveAll(allSubscribes)

            logger.setting("총 ${allSubscribes.size}개의 구독 주제가 생성되었습니다.")
//            logger.setting("- 카테고리: ${categories.size}개")
            logger.setting("- 키워드: ${keywords.size}개")
//            logger.setting("- 저자: ${authors.size}개")
        }
    }
}