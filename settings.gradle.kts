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

include(":kotlin-compose", ":kotlin-server")
project(":kotlin-compose").projectDir = file("packages/kotlin-compose")
project(":kotlin-server").projectDir = file("packages/kotlin-server")
