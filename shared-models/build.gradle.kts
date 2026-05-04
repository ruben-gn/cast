plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(libs.kotlinx.serialization.json)
}

tasks.register<JavaExec>("generateTypeScript") {
    dependsOn("classes")
    classpath = sourceSets["main"].output + configurations["runtimeClasspath"]
    mainClass.set("cast.api.TypeScriptGeneratorKt")
    val outputFile = rootProject.file("webapp/generated/api.ts")
    outputs.file(outputFile)
    doFirst {
        outputFile.parentFile.mkdirs()
        standardOutput = outputFile.outputStream()
    }
}