plugins {
    kotlin("jvm") version "1.9.25"
    kotlin("plugin.spring") version "1.9.25"
    id("org.springframework.boot") version "3.5.6"
    id("io.spring.dependency-management") version "1.1.7"
    id("maven-publish")
    id("jacoco")
    kotlin("kapt") version "1.9.25"
}

group = "com.ghkdqhrbals"
version = "0.0.2"
description = "mod"

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
    from("$buildDir/classes/java/main/META-INF") {
        include("spring-configuration-metadata.json")
//        include("additional-spring-configuration-metadata.json")
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            groupId = "com.ghkdqhrbals"
            artifactId = "mod"
            version = project.version.toString()
        }
    }

    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/ghkdqhrbals/personal-module")
            credentials {
                username = System.getenv("PACKAGES_ACTOR") ?: "ghkdqhrbals"
                password = System.getenv("PACKAGES_TOKEN") ?: System.getenv("GITHUB_PACKAGES_TOKEN") ?: runCatching { "git config --get github.package-token".runCommand() }.getOrNull()
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
    kapt("org.springframework.boot:spring-boot-configuration-processor")
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.springframework.boot:spring-boot-starter-web")
    runtimeOnly("io.awspring.cloud:spring-cloud-aws-starter-secrets-manager:3.1.1")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

jacoco {
    toolVersion = "0.8.12"
}

tasks.test {
    finalizedBy("jacocoTestReport")
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
