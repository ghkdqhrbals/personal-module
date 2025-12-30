package org.ghkdqhrbals.client.config.aspect

import io.micrometer.core.instrument.MeterRegistry
import org.aspectj.lang.ProceedingJoinPoint
import org.aspectj.lang.annotation.Around
import org.aspectj.lang.annotation.Aspect
import org.springframework.stereotype.Component

@Target(AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.RUNTIME)
annotation class TimedMetric(val name: String)

@Aspect
@Component
class TimedMetricAspect(private val registry: MeterRegistry) {
    @Around("@annotation(t)")
    fun around(pjp: ProceedingJoinPoint, t: TimedMetric): Any? =
        registry.timer(t.name).recordCallable { pjp.proceed() }
}