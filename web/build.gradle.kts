plugins {
    alias(libs.plugins.kotlin.jvm)
}

dependencies {
    implementation(project(":core"))
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.html.builder.jvm)
    implementation(libs.kotlinx.html.jvm)
    implementation(libs.ktor.server.di)
}