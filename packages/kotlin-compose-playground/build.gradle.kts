// Standalone wasmJs Compose app — the SDUI spec authoring/iteration surface.
// Depends on :kotlin-compose but is NOT published: no maven-publish plugin,
// no executable binary in the library artifact. `binaries.executable()` here
// lives only in this app module, so `wasmJsBrowserDevelopmentRun` has an
// entry point while the renderer stays a pure library.

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

kotlin {
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        browser {
            commonWebpackConfig {
                outputFileName = "playground.js"
            }
        }
        binaries.executable()
    }

    sourceSets {
        val wasmJsMain by getting {
            dependencies {
                implementation(project(":kotlin-compose"))
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.coroutines.core)
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.js)
            }
        }
    }
}
