package org.ghkdqhrbals.client.domain.event.listener

import org.springframework.context.SmartLifecycle
import org.springframework.data.redis.connection.stream.MapRecord
import org.springframework.data.redis.stream.StreamListener

/**
 * RedisStream Gracefully 하게 종료하는 Consumer
 */
abstract class GracefulConsumer() : StreamListener<String, MapRecord<String, String, String>>, SmartLifecycle {
    override fun getPhase() = 0
    @Volatile
    var running = false
    @Volatile
    var shuttingDown: Boolean = false
    override fun isRunning(): Boolean = running
}