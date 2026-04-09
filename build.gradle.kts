plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktor)
    alias(libs.plugins.kotlin.serialization)
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
    implementation(libs.ktor.server.netty)
    implementation(libs.logback.classic)

    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.config.yaml)
    implementation(libs.ktor.server.di)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.html.builder.jvm)

    implementation(libs.ktor.client.content.negotiation)

    implementation(libs.ktor.serialization.kotlinx.json)

    implementation(libs.kotlinx.html.jvm)
    implementation("io.ktor:ktor-serialization-jackson:3.4.2")

    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.kotlin.test.junit)
    testImplementation(libs.kotlin.test.junit)

    // Add Kotest to the main test suite if you want it for unit tests too
    testImplementation(libs.kotest.runner)
    testImplementation(libs.kotest.assertions)

}

testing {
    suites {
        val integrationTest by registering(JvmTestSuite::class) {

            dependencies {
                implementation(project())
                implementation(libs.ktor.client.content.negotiation) // TODO see if we can inherit these
                implementation(libs.ktor.serialization.kotlinx.json) // TODO see if we can inherit these
                implementation(libs.ktor.server.test.host)
                implementation(libs.kotest.assertions)
                implementation(libs.kotest.runner)
            }
        }
    }
}