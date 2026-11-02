import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
    application
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

application {
    mainClass.set("ai.factoredui.application.MainKt")
}

dependencies {
    implementation(project(":kotlin-compose-schema"))
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.kotlin.test)
}
