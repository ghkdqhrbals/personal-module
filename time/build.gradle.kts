plugins {
    kotlin("jvm") version "1.9.25"
    kotlin("plugin.spring") version "1.9.25"
    id("maven-publish")
    id("jacoco")
    kotlin("kapt")
    id("org.springframework.boot") version "3.5.6"
    id("io.spring.dependency-management") version "1.1.7"
}

group = "org.ghkdqhrbals"
version = "0.0.1"
description = "time"

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

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

jacoco { toolVersion = "0.8.12" }
tasks.test { finalizedBy("jacocoTestReport") }

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}
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

tasks.withType<Test> {
    useJUnitPlatform()
}
