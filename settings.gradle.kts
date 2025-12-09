plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.8.0"
}
rootProject.name = "mod"

include(
    ":oauth",
    ":time",
    ":client",
    ":message",
    ":batch",
    ":orchestrator",
    ":api",
    ":model",
    ":infra",
    ":schema"
)

project(":client").projectDir = file("core/client")
project(":api").projectDir = file("core/api")
project(":orchestrator").projectDir = file("core/orchestrator")
project(":message").projectDir = file("core/message")
project(":model").projectDir = file("core/model")
project(":infra").projectDir = file("core/infra")
project(":schema").projectDir = file("core/schema")