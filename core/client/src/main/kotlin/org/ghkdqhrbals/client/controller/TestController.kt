package org.ghkdqhrbals.client.controller

import io.micrometer.core.instrument.Gauge
import io.micrometer.core.instrument.MeterRegistry
import org.ghkdqhrbals.client.config.aspect.TimedMetric
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController

@RestController
class TestController(
    private val meterRegistry: MeterRegistry
) {
    @TimedMetric(name = "test_endpoint_timer")
    @GetMapping("/test/metrics")
    fun getMetrics(): String {
        return "Metrics counter incremented. Current count"
    }
}