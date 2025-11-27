package org.ghkdqhrbals.client.domain.scheduler.config

import org.springframework.batch.core.JobParameter
import org.springframework.batch.core.JobParameters
import org.springframework.batch.core.JobParametersBuilder
import org.springframework.batch.core.JobParametersIncrementer
import org.springframework.boot.ApplicationArguments
import org.springframework.context.annotation.Configuration

@Configuration
class NowJobTimeStamper(private val applicationArguments: ApplicationArguments) : JobParametersIncrementer {

    override fun getNext(parameters: JobParameters?): JobParameters {
        val builder = JobParametersBuilder(parameters ?: JobParameters())
            .addLong("currentTimeMillis", System.currentTimeMillis(), true)

        val deleteKeyTargets = mutableListOf<String>()
        applicationArguments.sourceArgs.forEach { arg ->
            val parts = arg.split("=")
            if (parts.size == 2 && parts[0].startsWith("--")) {
                val key = parts[0].substring(2)
                val value = parts[1]
                if (value == "null") {
                    deleteKeyTargets.add(key)
                } else {
                    builder.addString(key, value)
                }
            }
        }
        return createNewJobParameters(builder.toJobParameters(), deleteKeyTargets).also {
        }
    }

    fun createNewJobParameters(jobParameters: JobParameters, deleteKeyTargets: List<String>): JobParameters {
        val builder = JobParametersBuilder()
        jobParameters.parameters.forEach { (key, value) ->
            if (!deleteKeyTargets.contains(key)) {
                when (value) {
                    is JobParameter -> {
                        when (value.type) {
                            String::class.java -> {
                                builder.addString(key, value.value as String, value.isIdentifying)
                            }

                            java.lang.Long::class.java -> {
                                builder.addLong(key, value.value as Long, value.isIdentifying)
                            }

                            java.lang.Double::class.java -> {
                                builder.addDouble(key, value.value as Double, value.isIdentifying)
                            }

                            java.util.Date::class.java -> {
                                builder.addDate(key, value.value as java.util.Date, value.isIdentifying)
                            }
                        }
                    }
                }
            }
        }
        return builder.toJobParameters()
    }
}
