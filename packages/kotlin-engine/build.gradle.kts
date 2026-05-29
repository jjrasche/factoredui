import com.android.build.gradle.LibraryExtension
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.androidLibrary)
    `maven-publish`
}

group = "ai.factoredui"
version = "0.10.0"

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
            baseName = "FactoredUIEngine"
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
                // The pure factor + experiment logic depends only on the
                // wire-format schema (the factor dashboard builds a Spec).
                // `api` so consumers get the schema types transitively. No
                // Postgres, no Compose, no JVM-only APIs — this is the half of
                // the engine that runs on every target the host runs on
                // (agent-platform's Android phone target included).
                api(project(":kotlin-compose-schema"))
                // The schema's SpecNode/SpecValue are @Serializable, so their
                // generated companions reference kotlinx-serialization internals.
                // The schema declares it `implementation` (not transitive), so
                // the engine needs serialization on its own compile classpath to
                // construct those types in the factor dashboard builder.
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

extensions.configure<LibraryExtension>("android") {
    namespace = "ai.factoredui.engine"
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
            name.set("FactoredUI Engine")
            description.set(
                "Pure-Kotlin, multiplatform factor + experiment logic for FactoredUI: factor " +
                    "types + k-means clustering, deterministic traffic bucketing, the targeting " +
                    "predicate engine, governance verdicts, and experiment validation. No Postgres, " +
                    "no Compose. Depend on this to run the engine's decision logic on any KMP target."
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
