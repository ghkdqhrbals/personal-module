package org.ghkdqhrbals.client.domain.scheduler.config

import org.quartz.spi.TriggerFiredBundle
import org.springframework.beans.factory.config.AutowireCapableBeanFactory
import org.springframework.context.ApplicationContext
import org.springframework.context.ApplicationContextAware
import org.springframework.scheduling.quartz.SpringBeanJobFactory
import org.springframework.stereotype.Component

/**
 * Spring Bean을 Quartz Job에 주입할 수 있도록 하는 커스텀 JobFactory
 *
 * Quartz는 기본적으로 Job 인스턴스를 자체적으로 생성하므로,
 * Spring의 의존성 주입이 작동하지 않습니다.
 * 이 클래스는 Spring ApplicationContext를 사용하여 Job 인스턴스를 생성하고
 * 의존성을 주입할 수 있도록 합니다.
 */
@Component
class AutowiringSpringBeanJobFactory : SpringBeanJobFactory(), ApplicationContextAware {

    private lateinit var beanFactory: AutowireCapableBeanFactory

    override fun setApplicationContext(context: ApplicationContext) {
        beanFactory = context.autowireCapableBeanFactory
    }

    override fun createJobInstance(bundle: TriggerFiredBundle): Any {
        val job = super.createJobInstance(bundle)
        beanFactory.autowireBean(job)
        return job
    }
}

