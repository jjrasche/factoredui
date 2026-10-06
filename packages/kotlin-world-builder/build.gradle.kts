import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlinMultiplatform)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

group = "ai.factoredui"
version = "0.1.0"

kotlin {
    jvm("desktop") {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions {
                    jvmTarget.set(JvmTarget.JVM_11)
                }
            }
        }
    }

    sourceSets {
        val desktopMain by getting {
            dependencies {
                implementation(project(":kotlin-compose"))
                implementation(project(":kotlin-world-engine"))
                implementation(compose.desktop.currentOs)
                implementation(compose.runtime)
                implementation(libs.kotlinx.serialization.json)
                implementation(libs.kotlinx.coroutines.core)
            }
        }

        val desktopTest by getting {
            dependencies {
                implementation(libs.kotlin.test)
                implementation(libs.kotlinx.serialization.json)
                implementation(compose.desktop.currentOs)
                @OptIn(org.jetbrains.compose.ExperimentalComposeLibrary::class)
                implementation(compose.uiTest)
            }
        }
    }
}

val worldReferenceDir = project(":kotlin-world-engine").layout.projectDirectory.dir("reference")

tasks.withType<Test>().configureEach {
    systemProperty("WORLD_ENGINE_DESIGN_DIR", worldReferenceDir.asFile.path)
    inputs.files(fileTree(worldReferenceDir)).withPathSensitivity(PathSensitivity.RELATIVE).withPropertyName("worldEngineReference")
    if (!project.hasProperty("benchmarks")) exclude("**/FiveFootPerformanceTest*")
}

tasks.register<JavaExec>("worldBuilder") {
    group = "render"
    description = "Open the world builder window on a world file, driven by the world engine."
    val desktopCompilation = kotlin.jvm("desktop").compilations.getByName("main")
    classpath(desktopCompilation.output.allOutputs, desktopCompilation.runtimeDependencyFiles)
    mainClass.set("ai.factoredui.worldbuilder.WorldBuilderMainKt")
}
