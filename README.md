# FactoredUI

Kotlin Multiplatform + Compose Multiplatform renderer for declarative SDUI specs. Targets Android, iOS, JVM desktop, and browser (wasmJs).

The rendering half of [agent-platform](https://github.com/jjrasche/agent-platform). Specs describe *what* to render; FactoredUI's primitive palette makes them perceptible on whichever substrate is running.

## Primitive palette

- **Container** — column, row, stack, scrollview, grid, list, card, tabs, modal
- **Widget leaves** — text, button, image, video, icon, divider, spacer, textinput, toggle, select, slider, chip
- **Dense/semantic** — `scene3d`, `canvas`, `geomap`. Future: timeline, heatmap, flow-field, scatterplot3d. These take typed data plus layout or physics config instead of child components, and render dense perceptual channels rather than discrete widgets.

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
# the Gradle wrapper lives at the repo root
./gradlew build                                            # compile + test every module, all targets
./gradlew :kotlin-compose:assemble                         # renderer, all targets
./gradlew :kotlin-compose-playground:wasmJsBrowserDevelopmentRun           # playground in a browser
./gradlew :kotlin-compose:publish                          # publish to the local maven repo
```

Published to `https://jjrasche.github.io/factoredui/` via a gh-pages workflow.

## Packages

- `packages/kotlin-compose/` — the live renderer (Kotlin + Compose Multiplatform).
- `packages/kotlin-compose-schema/` — pure-Kotlin spec and wire types; the schema source of truth.
- `packages/kotlin-compose-capture/` — capture events and sessions.
- `packages/kotlin-engine/` — the factor and experiment engine: bucketing, governance, targeting.
- `packages/kotlin-server/` — server-side factor SQL and endpoints.
- `packages/kotlin-compose-playground/` — wasmJs dev playground for authoring specs.
- `packages/measure-minimal/` — the smallest wasmJs page that renders one spec, for payload measurement.

## History

FactoredUI once shipped React and React-Native renderers plus a Supabase-backed capture/factor/experiment pipeline. The React, React-Native and adapter-supabase packages were deleted on 2026-04-24, leaving kotlin-compose as the only renderer. `packages/core/`, the last TypeScript, was deleted on 2026-06-25 once its port landed: capture in `kotlin-compose-capture`, experiments and factors in `kotlin-engine`, the spec types in `kotlin-compose-schema`. The repo is 100% Kotlin and Gradle, with no npm tooling.

## License

MIT
