package org.ghkdqhrbals.message.event

interface EventPublisher {
    fun <T : Any> send(topic: String, event: T): String
}