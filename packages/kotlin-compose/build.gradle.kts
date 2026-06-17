import com.android.build.gradle.LibraryExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.androidLibrary)
    `maven-publish`
}

group = "ai.factoredui"
version = "0.13.2"

kotlin {
    androidTarget {
        publishLibraryVariants("release")
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
            baseName = "FactoredUICompose"
            isStatic = true
        }
    }

    jvm("desktop") {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions {
                    jvmTarget.set(JvmTarget.JVM_11)
                }
            }
        }
    }

    @OptIn(org.jetbrains.kotlin.gradle.ExperimentalWasmDsl::class)
    wasmJs {
        browser()
    }

    sourceSets {
        val commonMain by getting {
            dependencies {
                // `api` so consumers of kotlin-compose transitively get the
                // pure-Kotlin spec + capture wire types without having to
                // declare kotlin-compose-schema separately. Server-side
                // consumers depend on kotlin-compose-schema directly to
                // avoid pulling Compose Multiplatform.
                api(project(":kotlin-compose-schema"))
                implementation(compose.runtime)
                implementation(compose.foundation)
                implementation(compose.material3)
                implementation(compose.ui)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.coroutines.core)
                // kotlinx-datetime: used by the forcegraph primitive for
                // monotonic-ish wall clocks driving firing pulses + particle
                // animations. Common across all targets.
                implementation(libs.kotlinx.datetime)
                // Coil 3 is KMP-native — no expect/actual needed. Supports
                // Android, iOS, JVM Desktop, Wasm from commonMain.
                implementation(libs.coil.compose)
                implementation(libs.coil.network.ktor3)
                implementation(libs.coil.svg)
                implementation(libs.ktor.client.core)
                implementation(libs.ktor.client.content.negotiation)
                implementation(libs.ktor.serialization.kotlinx.json)
            }
        }

        val commonTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.coroutines.test)
                implementation(libs.ktor.client.mock)
                @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
                implementation(compose.uiTest)
            }
        }

        // Intermediate source set for everything that ISN'T wasmJs.
        // Holds the ktor-based SSE subscription actual — works with
        // okhttp / darwin engines but NOT with ktor-client-js's
        // fetch-based engine on wasmJs (browser fetch buffers SSE
        // responses until completion). The wasmJs target gets its
        // own actual using the browser's native EventSource.
        val nonWasmJsMain by creating {
            dependsOn(commonMain)
        }

        val androidMain by getting {
            dependsOn(nonWasmJsMain)
            dependencies {
                implementation(libs.androidx.activity.compose)
                // Ktor engine for Coil network fetches on Android
                implementation(libs.ktor.client.okhttp)
            }
        }

        val desktopMain by getting {
            dependsOn(nonWasmJsMain)
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(libs.ktor.client.okhttp)
            }
        }

        // Create iosMain explicitly (parent of all three iOS targets) so we can
        // add a single Ktor engine for the iOS platform.
        val iosMain by creating {
            dependsOn(nonWasmJsMain)
            dependencies {
                implementation(libs.ktor.client.darwin)
            }
        }
        val iosX64Main by getting { dependsOn(iosMain) }
        val iosArm64Main by getting { dependsOn(iosMain) }
        val iosSimulatorArm64Main by getting { dependsOn(iosMain) }

        val wasmJsMain by getting {
            dependencies {
                implementation(libs.ktor.client.js)
            }
        }
    }
}

// Use the explicit extension-configure form to sidestep Kotlin DSL accessor
// generation issues that arise when android-library is applied alongside KMP.
extensions.configure<LibraryExtension>("android") {
    namespace = "ai.factoredui.compose"
    compileSdk = libs.versions.androidCompileSdk.get().toInt()

    defaultConfig {
        minSdk = libs.versions.androidMinSdk.get().toInt()
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

publishing {
    publications.withType<MavenPublication>().configureEach {
        pom {
            name.set("FactoredUI Compose")
            description.set("Kotlin Multiplatform + Compose Multiplatform rendering engine for FactoredUI SDUI specs.")
            url.set("https://github.com/jjrasche/factoredui")
            licenses {
                license {
                    name.set("MIT")
                }
            }
        }
    }

    repositories {
        // Local file repo — CI writes here, then deploys the whole directory
        // to the gh-pages branch so consumers can fetch over public HTTPS
        // at https://jjrasche.github.io/factoredui/ with no credentials.
        maven {
            name = "LocalBuildRepo"
            url = uri(rootProject.layout.buildDirectory.dir("maven-repo"))
        }
    }
}
