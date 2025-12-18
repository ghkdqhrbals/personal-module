package org.ghkdqhrbals.orchestrator.saga

import org.ghkdqhrbals.message.saga.step.sagaDefinition
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test

class SagaDefinitionTest {
    @Test
    fun `should create saga definition with steps`() {
        val saga = sagaDefinition("ORDER_CREATE") {
            step(
                name = "CREATE_ORDER",
                commandTopic = "order-command",
                compensationTopic = "order-compensation"
            )
            step(
                name = "RESERVE_INVENTORY",
                commandTopic = "inventory-command",
                compensationTopic = "inventory-compensation"
            )
            responseTopic("saga-response")
        }

        assertEquals("ORDER_CREATE", saga.sagaType)
        assertEquals(2, saga.getTotalSteps())
        assertEquals("saga-response", saga.responseTopic)

        val step0 = saga.getStep(0)
        assertNotNull(step0)
        assertEquals("CREATE_ORDER", step0?.name)
        assertEquals(0, step0?.index)

        val step1 = saga.getStep(1)
        assertNotNull(step1)
        assertEquals("RESERVE_INVENTORY", step1?.name)
        assertEquals(1, step1?.index)
    }

    @Test
    fun `should return compensation steps in reverse order`() {
        val saga = sagaDefinition("TEST_SAGA") {
            step(
                name = "STEP_1",
                commandTopic = "topic-1",
                compensationTopic = "comp-1"
            )
            step(
                name = "STEP_2",
                commandTopic = "topic-2",
                compensationTopic = "comp-2"
            )
            step(
                name = "STEP_3",
                commandTopic = "topic-3",
                hasCompensation = false  // 보상 없음
            )
            responseTopic("saga-response")
        }

        // STEP_3에서 실패한 경우 (index 2)
        val compensationSteps = saga.getCompensationSteps(2)

        // STEP_3은 hasCompensation=false이므로 제외
        assertEquals(2, compensationSteps.size)
        assertEquals("STEP_2", compensationSteps[0].name)  // 역순
        assertEquals("STEP_1", compensationSteps[1].name)
    }

    @Test
    fun `should throw exception for empty steps`() {
        assertThrows(IllegalArgumentException::class.java) {
            sagaDefinition("EMPTY_SAGA") {
                responseTopic("saga-response")
            }
        }
    }

    @Test
    fun `should throw exception when response topic is not set`() {
        assertThrows(IllegalArgumentException::class.java) {
            sagaDefinition("NO_RESPONSE_TOPIC") {
                step(
                    name = "STEP_1",
                    commandTopic = "topic-1"
                )
            }
        }
    }
}

