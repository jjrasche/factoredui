// Root project — exists so each Gradle plugin gets loaded exactly once
// across the multi-module build. Without `apply false` here, subprojects
// that apply the same plugin via `plugins { alias(...) }` trigger
// Gradle's "loaded multiple times" guard (kotlin-compose + kotlin-compose-schema
// both apply kotlinMultiplatform, etc.).

plugins {
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinJvm) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinSerialization) apply false
    alias(libs.plugins.androidLibrary) apply false
}
