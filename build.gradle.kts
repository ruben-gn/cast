plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktor)
    alias(libs.plugins.kotlin.serialization)
}

allprojects {
    afterEvaluate {
        if (tasks.findByName("prepareKotlinBuildScriptModel") == null) {
            tasks.register("prepareKotlinBuildScriptModel") {
                group = "help"
                description = "Compatibility placeholder for IntelliJ Kotlin Gradle model import."
            }
        }
    }
}

group = "grootnibbel.ink"
version = "0.0.1"

application {
    mainClass = "io.ktor.server.netty.EngineMain"
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":core"))

    implementation(libs.ktor.server.netty)

    implementation(libs.logback.classic)
    implementation(libs.kotlin.logging)

    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.config.yaml)

    implementation(libs.ktor.serialization.kotlinx.json)
}
