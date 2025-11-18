package org.ghkdqhrbals.client.config

import org.ghkdqhrbals.client.config.Ansi.CYAN
import org.ghkdqhrbals.client.config.Ansi.PURPLE
import org.ghkdqhrbals.client.config.Ansi.RESET
import org.slf4j.Logger
import org.slf4j.LoggerFactory

inline fun <reified T> T.logger(): Logger {
    return LoggerFactory.getLogger(T::class.java)
}


fun Logger.title(title: String, msg: String) = this.info("[$CYAN$title$RESET] $msg")

// lazy message 버전 (로그 레벨 체크 후 메시지 평가)
inline fun Logger.title(title: String, noinline msg: () -> String) {
    if (this.isInfoEnabled) this.info("[$CYAN$title$RESET] ${'$'}{msg()}")
}

fun Logger.setting(msg: String) = this.info("${PURPLE}${msg}${RESET}")

object Ansi {
    const val RESET = "\u001B[0m"

    const val RED = "\u001B[31m"
    const val GREEN = "\u001B[32m"
    const val YELLOW = "\u001B[33m"
    const val BLUE = "\u001B[34m"
    const val PURPLE = "\u001B[35m"
    const val CYAN = "\u001B[36m"
    const val WHITE = "\u001B[37m"
}