import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

plugins {
    kotlin("jvm") version "1.9.25" apply false
    kotlin("plugin.spring") version "1.9.25" apply false
    kotlin("plugin.jpa") version "1.9.25" apply false
    id("org.springframework.boot") version "3.5.6" apply false
    id("io.spring.dependency-management") version "1.1.7" apply false
    id("com.google.protobuf") version "0.9.2" apply false
}

allprojects {
    repositories { mavenCentral() }

    // 모든 프로젝트의 빌드 결과를 build 폴더에 저장
    layout.buildDirectory = layout.projectDirectory.dir("build")
}

subprojects {
    plugins.withId("org.jetbrains.kotlin.jvm") {
        extensions.configure<KotlinJvmProjectExtension>("kotlin") { jvmToolchain(21) }
    }

    // org.ghkdqhrbals:oauth 좌표를 로컬 :oauth 프로젝트로 치환
    configurations.configureEach {
        resolutionStrategy.dependencySubstitution {
            substitute(module("org.ghkdqhrbals:oauth")).using(project(":oauth"))
        }
    }
}

// Root aggregator tasks
// Gradle multi-project에서 루트에 'classes'가 없기 때문에 편의 태스크를 추가합니다.
// 하위 프로젝트들의 ':<subproject>:classes' 에 의존합니다.
tasks.register("classes") {
    group = "build"
    description = "Assembles main classes for all subprojects"
    dependsOn(subprojects.map { "${it.path}:classes" })
}

// 필요 시 루트에서 ':testClasses'도 실행할 수 있게 하시려면 아래 주석을 해제하세요.
// tasks.register("testClasses") {
//     group = "build"
//     description = "Assembles test classes for all subprojects"
//     dependsOn(subprojects.map { "${it.path}:testClasses" })
// }
