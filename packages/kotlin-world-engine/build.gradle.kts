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

tasks.withType<Test>().configureEach {
    System.getenv("WORLD_ENGINE_CONFORMANCE_DIR")?.let { systemProperty("WORLD_ENGINE_CONFORMANCE_DIR", it) }
    System.getProperty("WORLD_ENGINE_CONFORMANCE_DIR")?.let { systemProperty("WORLD_ENGINE_CONFORMANCE_DIR", it) }
    System.getenv("WORLD_ENGINE_DESIGN_DIR")?.let { systemProperty("WORLD_ENGINE_DESIGN_DIR", it) }
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
