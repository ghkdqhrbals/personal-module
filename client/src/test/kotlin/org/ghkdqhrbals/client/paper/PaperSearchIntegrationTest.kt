package org.ghkdqhrbals.client.paper

import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.boot.test.web.client.TestRestTemplate
import org.springframework.http.HttpStatus
import org.junit.jupiter.api.Assertions.*

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class PaperSearchIntegrationTest {

    @Autowired
    private lateinit var restTemplate: TestRestTemplate

    @Test
    fun `should get supported categories`() {
        val response = restTemplate.getForEntity(
            "/api/papers/categories",
            List::class.java
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
        assertTrue(response.body!!.contains("Computer Science"))
    }

    @Test
    fun `should search papers by category`() {
        val request = PaperSearchRequest(
            category = "Computer Science",
            minImpactFactor = 10.0,
            maxResults = 5
        )

        val response = restTemplate.postForEntity(
            "/api/papers/search",
            request,
            PaperSearchResponse::class.java
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
    }

    @Test
    fun `should get top journals by category`() {
        val response = restTemplate.getForEntity(
            "/api/papers/journals/Computer Science?minImpactFactor=15.0",
            List::class.java
        )

        assertEquals(HttpStatus.OK, response.statusCode)
        assertNotNull(response.body)
    }
}

