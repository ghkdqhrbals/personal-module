package org.ghkdqhrbals.client.config.log

import ch.qos.logback.classic.pattern.ClassicConverter
import ch.qos.logback.classic.spi.ILoggingEvent

class LogConverter : ClassicConverter() {
    private val width = 10 // 원하는 고정 폭

    override fun convert(event: ILoggingEvent): String {
        val name = event.threadName
        val fixed = if (name.length > width) {
            name.takeLast(width)
        } else {
            name.padStart(width, ' ')
        }
        return fixed
    }
}