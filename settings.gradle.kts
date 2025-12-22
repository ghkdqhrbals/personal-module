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
    ":api",
    ":model",
    ":repository",
)

project(":client").projectDir = file("core/client")
project(":api").projectDir = file("core/api")
project(":message").projectDir = file("core/message")
project(":model").projectDir = file("core/model")
project(":repository").projectDir = file("core/repository")
