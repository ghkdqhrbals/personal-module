package org.ghkdqhrbals.client

import org.ghkdqhrbals.client.config.TestLlmConfig
import org.junit.jupiter.api.Test
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.context.annotation.Import

@SpringBootTest
@Import(TestLlmConfig::class)
class ClientApplicationTests {


    @Test
    fun contextLoads() {
    }

}
