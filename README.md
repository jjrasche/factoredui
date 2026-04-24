# FactoredUI

Kotlin Multiplatform + Compose Multiplatform renderer for declarative SDUI specs. Targets Android, iOS, JVM desktop, and browser (wasmJs).

The rendering half of [agent-platform](https://github.com/jjrasche/agent-platform). Specs describe *what* to render; FactoredUI's primitive palette makes them perceptible on whichever substrate is running.

## Primitive palette

- **Container** — column, row, stack, scrollview, grid, list, card, tabs, modal
- **Widget leaves** — text, button, image, icon, divider, spacer, textinput, toggle, select, slider, chip
- **Dense/semantic** — `forcegraph` (first member). Future: timeline, heatmap, flow-field, scatterplot3d. These take typed data + physics/layout config instead of child components, and render dense perceptual channels rather than discrete widgets.

## Usage (Gradle)

```kotlin
// settings.gradle.kts
dependencyResolutionManagement {
    repositories {
        maven("https://jjrasche.github.io/factoredui/")
    }
}

// build.gradle.kts
dependencies {
    commonMainImplementation("ai.factoredui:kotlin-compose:<version>")
}
```

Or `includeBuild(...)` the local checkout during development.

## Spec schema

```kotlin
@Serializable data class Spec(
    val specVersion: Int,
    val rendererMin: Int,
    val root: SpecNode,
)

@Serializable data class SpecNode(
    val id: String,
    val type: SpecNodeType,           // closed enum
    val props: Map<String, SpecValue> = emptyMap(),
    val children: List<SpecNode> = emptyList(),
    val visible: String? = null,      // "{path.to.bool}" binding ref
    val action: ActionRef? = null,    // "button was clicked" → host-registered action
)
```

Props support literals, binding refs (`"{path.to.value}"`), nested nodes, arrays, and objects. Bindings resolve against a `StateFlow<Map<String, Any?>>` the host provides.

## Build

```bash
cd packages/kotlin-compose
./gradlew assemble                                         # all targets
./gradlew wasmJsBrowserDevelopmentExecutableDistribution   # browser bundle
./gradlew allTests                                         # all tests
./gradlew publish                                          # publish to local maven repo
```

Published to `https://jjrasche.github.io/factoredui/` via a gh-pages workflow.

## Packages

- `packages/kotlin-compose/` — the live renderer (Kotlin + Compose Multiplatform).
- `packages/core/` — TypeScript. Holds `src/sdui/spec-types.ts` as the canonical TS mirror of the Kotlin `SpecNode` schema. Also holds dormant capture/factor/experiment pipeline code pending a Kotlin port (see agent-platform memory for the motivating thesis).

## History

Previously FactoredUI shipped React and React-Native renderers plus a Supabase-backed capture/factor/experiment pipeline. On 2026-04-24 the React, React-Native, and adapter-supabase packages were deleted. Kotlin-compose is now the only renderer. The capture/factor/experiment concept survives as deferred TS pipeline code that will eventually be ported to Kotlin to support "AI-proactive UI improvement" loops in agent-platform.

## License

MIT
