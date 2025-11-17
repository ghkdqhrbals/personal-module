plugins {
    kotlin("jvm") version "1.9.25"
    kotlin("plugin.spring") version "1.9.25"
    id("org.springframework.boot") version "3.5.6"
    id("io.spring.dependency-management") version "1.1.7"
    id("maven-publish")
    id("jacoco")
    kotlin("kapt") version "1.9.25"
}

group = "org.ghkdqhrbals"
version = "0.0.1"
description = "client"

// 외부에서 -PartifactVersion 로 전달 시 우선 적용
if (project.hasProperty("artifactVersion")) {
    version = project.property("artifactVersion").toString()
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

tasks.named<Jar>("jar") {
    from("${layout.buildDirectory}/tmp/kapt3/classes/main/META-INF") {
        include("spring-configuration-metadata.json")
        include("additional-spring-configuration-metadata.json")
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            groupId = "org.ghkdqhrbals"
            artifactId = description
            version = project.version.toString()
        }
    }

    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/ghkdqhrbals/personal-module")
            credentials {
                username = System.getenv("PACKAGES_ACTOR") ?: "ghkdqhrbals"
                password = System.getenv("PACKAGES_TOKEN") ?: System.getenv("GITHUB_PACKAGES_TOKEN")
                        ?: runCatching { "git config --get github.package-token".runCommand() }.getOrNull()
            }
        }
    }
}

fun String.runCommand(): String {
    return try {
        val process = ProcessBuilder("bash", "-c", this)
            .redirectErrorStream(true)
            .start()
        process.inputStream.bufferedReader().readText().trim()
    } catch (e: Exception) {
        ""
    }
}
java { toolchain { languageVersion = JavaLanguageVersion.of(21) } }

repositories {
    mavenCentral()
}

dependencies {
    // thymeleaf
    runtimeOnly("org.springframework.boot:spring-boot-starter-thymeleaf")
    kapt("org.springframework.boot:spring-boot-configuration-processor")
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-web")
    runtimeOnly("io.awspring.cloud:spring-cloud-aws-starter-secrets-manager:3.1.1")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    // jpa
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("com.mysql:mysql-connector-j")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation ("com.aallam.openai:openai-client:4.0.1")
    // Ktor HTTP client engine (OkHttp) for JVM
    implementation("io.ktor:ktor-client-okhttp:3.0.0")
    implementation("io.ktor:ktor-client-content-negotiation:3.0.0")
    implementation("io.ktor:ktor-client-logging:3.0.0")
    implementation("io.ktor:ktor-serialization-kotlinx-json:3.0.0")
    // Swagger UI (springdoc-openapi)
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.6.0")
    // Atom/Feed parser for arXiv
    implementation("com.rometools:rome:2.1.0")
    // HTML sanitize
    implementation("org.jsoup:jsoup:1.17.2")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    // Apache HttpClient for RestClient connection pooling (version managed by Spring Boot BOM)
    implementation("org.apache.httpcomponents.client5:httpclient5")
    implementation("org.apache.httpcomponents.core5:httpcore5")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

jacoco { toolVersion = "0.8.12" }

tasks.test { finalizedBy("jacocoTestReport") }

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
        csv.required.set(false)
    }
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.jacocoTestReport)
    violationRules {
        rule {
            limit {
                minimum = "0.20".toBigDecimal() // 80% line coverage 최소
            }
        }
    }
}
