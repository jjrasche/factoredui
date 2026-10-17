rootProject.name = "factoredui"

pluginManagement {
    repositories {
        google()
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
    }
}

include(":measure-minimal", ":kotlin-compose-schema", ":kotlin-compose-capture", ":kotlin-engine", ":kotlin-compose", ":kotlin-server", ":kotlin-application", ":kotlin-compose-playground")
project(":kotlin-compose-schema").projectDir = file("packages/kotlin-compose-schema")
project(":kotlin-compose-capture").projectDir = file("packages/kotlin-compose-capture")
project(":kotlin-engine").projectDir = file("packages/kotlin-engine")
project(":kotlin-compose").projectDir = file("packages/kotlin-compose")
project(":kotlin-server").projectDir = file("packages/kotlin-server")
project(":kotlin-application").projectDir = file("packages/kotlin-application")
project(":kotlin-compose-playground").projectDir = file("packages/kotlin-compose-playground")
project(":measure-minimal").projectDir = file("packages/measure-minimal")
