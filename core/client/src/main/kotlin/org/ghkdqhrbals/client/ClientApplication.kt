package org.ghkdqhrbals.client

import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.domain.EntityScan
import org.springframework.boot.runApplication
import org.springframework.data.jpa.repository.config.EnableJpaRepositories
import kotlin.system.exitProcess

@SpringBootApplication
@EntityScan("org.ghkdqhrbals.client.domain")
@EnableJpaRepositories("org.ghkdqhrbals.client.domain")
class ClientApplication

fun main(args: Array<String>) {
    val exitRequested = args.none { it == "--always-run=true" }

    val context = runApplication<ClientApplication>(*args)

//    if (exitRequested) {
//        exitProcess(SpringApplication.exit(context))
//    }
}
