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

include(":kotlin-compose-schema", ":kotlin-engine", ":kotlin-compose", ":kotlin-server", ":kotlin-compose-playground")
project(":kotlin-compose-schema").projectDir = file("packages/kotlin-compose-schema")
project(":kotlin-engine").projectDir = file("packages/kotlin-engine")
project(":kotlin-compose").projectDir = file("packages/kotlin-compose")
project(":kotlin-server").projectDir = file("packages/kotlin-server")
project(":kotlin-compose-playground").projectDir = file("packages/kotlin-compose-playground")
