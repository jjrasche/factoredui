# FactoredUI Integration Guide

> **Status**: 2026-04-24 — the old TS/React/RN integration story was removed. This guide is a placeholder until the Kotlin-Compose consumer story is written down fully. For now, integration is straightforward KMP Gradle + published maven artifact.

## Add the dependency

```kotlin
// settings.gradle.kts (consuming app)
dependencyResolutionManagement {
    repositories {
        maven("https://jjrasche.github.io/factoredui/")
    }
}

// build.gradle.kts (consuming app)
kotlin {
    sourceSets {
        val commonMain by getting {
            dependencies {
                implementation("ai.factoredui:kotlin-compose:<latest>")
            }
        }
    }
}
```

Or, during local development, `includeBuild("path/to/factored-ui/packages/kotlin-compose")` in the consumer's `settings.gradle.kts` and depend on `ai.factoredui:kotlin-compose` — Gradle substitutes the local project transparently.

## Render a spec

```kotlin
import ai.factoredui.compose.renderer.RenderSpec
import ai.factoredui.compose.renderer.RenderContext
import ai.factoredui.compose.schema.Spec

@Composable
fun MyApp(spec: Spec, context: RenderContext) {
    RenderSpec(root = spec.root, context = context)
}
```

`RenderContext` bundles the live data flow (for `{binding.ref}` resolution), the action dispatcher, and an observability hook. Consumers construct it once at boot.

## Targets supported

Android · iOS (x64, arm64, simulator-arm64) · JVM desktop · WASM browser.

The WASM browser target is what agent-platform serves from `adapter:sdui:web` — a static bundle scp'd to the VPS. Native apps link the renderer at build time.

## What's not here yet

- Action dispatch recipe (ActionRegistry patterns)
- Observability hook wiring (for agent-platform signal emissions)
- Capture/factor/experiment pipeline (see CLAUDE.md — deferred to a Kotlin port)

Those land as agent-platform exercises them and we learn the shape. This document grows with that work.
