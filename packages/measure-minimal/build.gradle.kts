// Smallest possible factored-ui browser page: one spec, one text, one button.
// Exists to measure the renderer's floor payload, not to ship.

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
}

// repo.maven.apache.org does not resolve from this workstation (DNS), so every
// dependency must already be in the Gradle cache; atomicfu is pinned to a cached build.
configurations.all {
    resolutionStrategy.force("org.jetbrains.kotlinx:atomicfu:0.27.0")
    resolutionStrategy.eachDependency {
        if (requested.group == "org.jetbrains.kotlinx" && requested.name.startsWith("atomicfu")) {
            useVersion("0.27.0")
        }
    }
}

kotlin {
    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        browser {
            commonWebpackConfig {
                outputFileName = "minimal.js"
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
                implementation(libs.kotlinx.coroutines.core)
            }
        }
    }
}
