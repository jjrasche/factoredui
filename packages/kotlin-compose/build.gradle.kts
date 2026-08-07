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
version = (findProperty("factoredUiVersion") as String?) ?: "0.19.0"

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
                // declare them separately. Server-side consumers depend on
                // kotlin-compose-schema directly to avoid pulling Compose.
                api(project(":kotlin-compose-schema"))
                api(project(":kotlin-compose-capture"))
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
                // `common`, NOT `currentOs`: currentOs resolves at OUR configuration time and
                // hard-pins the publisher's OS variant into the published pom, so a Linux
                // publish makes every Windows/macOS consumer hunt a skiko .dll that will never
                // be there. The consumer contributes its own platform artifact.
                implementation(compose.desktop.common)
                implementation(libs.ktor.client.okhttp)
            }
        }

        val desktopTest by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
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

// The Compose UI tests live in commonTest and need a real Compose runtime. Android's
// UNIT-test variant is a bare JVM with no Android runtime, so runComposeUiTest NPEs
// there — 21 failures that say nothing about the code and made `./gradlew build` red
// on every branch. The same tests run for real on desktopTest (118 green). There are
// no android-specific test sources, so disabling costs zero coverage; re-enable with a
// filter if androidUnitTest ever gains its own tests.
tasks.matching { it.name == "testDebugUnitTest" || it.name == "testReleaseUnitTest" }
    .configureEach { enabled = false }

// "All tests passed" is a number, not a proof, unless you also check how many ran. A stale
// --tests filter once gated CI to 7 of 15 classes and stayed green for months. This floor
// is what would have caught it: it trips only when the suite SHRINKS.
val minimumDesktopTests = 100

tasks.named<Test>("desktopTest") {
    doLast {
        // Counts tests that actually RAN. `tests` includes skipped ones, so counting it
        // alone would let @Ignore quietly erode the suite past this floor.
        val executed = reports.junitXml.outputLocation.get().asFile
            .listFiles { file -> file.name.endsWith(".xml") }
            .orEmpty()
            .sumOf { xml ->
                val text = xml.readText()
                fun attr(name: String) =
                    Regex("""$name="(\d+)"""").find(text)?.groupValues?.get(1)?.toInt() ?: 0
                attr("tests") - attr("skipped")
            }
        check(executed >= minimumDesktopTests) {
            "desktopTest ran only $executed tests, below the floor of $minimumDesktopTests. " +
                "Either the suite shrank or a --tests filter is narrowing it. Investigate " +
                "before lowering this number."
        }
    }
}

// Headless render entry for non-JVM callers (il-render's Python correctness gate):
// ./gradlew :kotlin-compose:renderSpecCli --args="spec.json out.png [w] [h] [density]"
// Local-only skiko natives. Kept out of every published configuration so the pom stays
// OS-neutral (see the desktopMain comment), while local runs still get a real library.
val desktopNativeRuntime: Configuration by configurations.creating

dependencies {
    desktopNativeRuntime(compose.desktop.currentOs)
}

tasks.register<JavaExec>("renderSpecCli") {
    group = "render"
    description = "Render an SDUI spec JSON file to a PNG headlessly (spec-in -> PNG-out)."
    val desktopCompilation = kotlin.jvm("desktop").compilations.getByName("main")
    classpath(
        desktopCompilation.output.allOutputs,
        desktopCompilation.runtimeDependencyFiles,
        desktopNativeRuntime,
    )
    mainClass.set("ai.factoredui.compose.render.RenderSpecCliKt")
}

// Headed GPU fps spike (window opens on the workstation): measurement source lives in
// desktopTest so it can reuse TerrainFixtures, but it is a plain main — never a @Test —
// so the suite and its floor are untouched.
tasks.register<JavaExec>("headedFpsSpike") {
    group = "render"
    description = "Render the rolling-hills terrain in a real GPU-backed window and print fps."
    val desktopTestCompilation = kotlin.jvm("desktop").compilations.getByName("test")
    val desktopMainCompilation = kotlin.jvm("desktop").compilations.getByName("main")
    classpath(
        desktopTestCompilation.output.allOutputs,
        desktopMainCompilation.output.allOutputs,
        desktopTestCompilation.runtimeDependencyFiles,
    )
    mainClass.set("ai.factoredui.compose.scene3d.HeadedFpsSpikeKt")
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
