import com.android.build.gradle.LibraryExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
}

group = "ai.factoredui"
version = "0.1.0"

kotlin {
    jvm()

    androidTarget {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions {
                    jvmTarget.set(JvmTarget.JVM_11)
                }
            }
        }
    }

    listOf(
        iosX64(),
        iosArm64(),
        iosSimulatorArm64(),
    ).forEach { iosTarget ->
        iosTarget.binaries.framework {
            baseName = "FactoredUIWorldEngine"
            isStatic = true
        }
    }

    linuxX64()

    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation(libs.kotlinx.serialization.json)
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.serialization.json)
            }
        }
    }
}

val defaultWorldEngineDesignDir = "C:/Users/rasche_j/Documents/workspace/van-life/.git-worktrees/design-world-engine/design/world-engine"

tasks.withType<Test>().configureEach {
    val conformanceDir = System.getenv("WORLD_ENGINE_CONFORMANCE_DIR") ?: System.getProperty("WORLD_ENGINE_CONFORMANCE_DIR")
    val designDir = System.getenv("WORLD_ENGINE_DESIGN_DIR") ?: defaultWorldEngineDesignDir
    conformanceDir?.let { systemProperty("WORLD_ENGINE_CONFORMANCE_DIR", it) }
    systemProperty("WORLD_ENGINE_DESIGN_DIR", designDir)
    // The cases live outside this repo, so without these inputs Gradle serves a cached verdict on a reference that has since moved.
    inputs.files(fileTree(designDir)).withPathSensitivity(PathSensitivity.RELATIVE).withPropertyName("worldEngineReference")
    conformanceDir?.let { inputs.files(fileTree(it)).withPathSensitivity(PathSensitivity.RELATIVE).withPropertyName("worldEngineConformanceCases") }
}

extensions.configure<LibraryExtension>("android") {
    namespace = "ai.factoredui.worldengine"
    compileSdk = libs.versions.androidCompileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.androidMinSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}
