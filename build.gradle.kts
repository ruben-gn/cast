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
    implementation(libs.kotlin.logging)

    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.config.yaml)
    implementation(libs.ktor.server.di)
    implementation(libs.ktor.server.content.negotiation)
    implementation(libs.ktor.server.html.builder.jvm)

    implementation(libs.ktor.client.cio)
    implementation(libs.ktor.client.content.negotiation)

    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.serialization.kotlinx.xml)

    implementation(libs.kotlinx.html.jvm)
    implementation("io.ktor:ktor-serialization-jackson:3.4.2")
}

testing {
    suites {
        val test by getting(JvmTestSuite::class) {
            useJUnitJupiter()

            dependencies {
                implementation(project())
                implementation(libs.ktor.client.content.negotiation) // TODO see if we can inherit these
                implementation(libs.ktor.serialization.kotlinx.json) // TODO see if we can inherit these
                implementation(libs.ktor.server.test.host)
                implementation(libs.ktor.client.mock)
                implementation(libs.kotest.assertions)
                implementation(libs.kotest.runner)
            }
        }
    }
}