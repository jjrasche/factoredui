import com.android.build.gradle.LibraryExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.kotlinSerialization)
    alias(libs.plugins.androidLibrary)
    `maven-publish`
}

group = "ai.factoredui"
version = (findProperty("factoredUiVersion") as String?) ?: "0.18.0"

kotlin {
    jvm()

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
            baseName = "FactoredUICapture"
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
                implementation(libs.kotlinx.datetime)
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

extensions.configure<LibraryExtension>("android") {
    namespace = "ai.factoredui.compose.capture"
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
            name.set("FactoredUI Capture")
            description.set(
                "Capture/telemetry wire types for FactoredUI. Split out of kotlin-compose-schema " +
                    "so a consumer that wants only the SDUI spec grammar does not inherit a " +
                    "capture concept by transitive dependency."
            )
            url.set("https://github.com/jjrasche/factoredui")
            licenses {
                license {
                    name.set("MIT")
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
