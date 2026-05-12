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

include(":kotlin-compose-schema", ":kotlin-compose", ":kotlin-server")
project(":kotlin-compose-schema").projectDir = file("packages/kotlin-compose-schema")
project(":kotlin-compose").projectDir = file("packages/kotlin-compose")
project(":kotlin-server").projectDir = file("packages/kotlin-server")
