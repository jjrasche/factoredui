import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.kotlinSerialization)
    `maven-publish`
}

group = "ai.factoredui"
version = "0.9.0"

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

dependencies {
    // Shared wire types (SpecNode schema + capture event/session types).
    // kotlin-compose-schema is the pure-Kotlin half of the renderer split —
    // no Compose Multiplatform on the server's classpath.
    implementation(project(":kotlin-compose-schema"))

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.datetime)

    // Postgres-specific. The server target is *the* place where we commit
    // to a backend — see DECISIONS.md for the rationale.
    implementation(libs.postgresql)
    implementation(libs.hikari.cp)

    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
}

publishing {
    publications {
        create<MavenPublication>("server") {
            from(components["java"])
            pom {
                name.set("FactoredUI Server")
                description.set("Backend ingest, factor engine, and experiment lifecycle for FactoredUI. Postgres-backed; embed in your existing JVM backend.")
                url.set("https://github.com/jjrasche/factoredui")
                licenses {
                    license { name.set("MIT") }
                }
            }
        }
    }
    repositories {
        maven {
            name = "LocalBuildRepo"
            url = uri(rootProject.layout.buildDirectory.dir("maven-repo"))
        }
    }
}
