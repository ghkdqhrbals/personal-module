package org.ghkdqhrbals.message.event

interface EventSender {
    fun <T : Any> send(topic: String, event: T): String
}