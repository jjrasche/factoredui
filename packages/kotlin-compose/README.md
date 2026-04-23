# @factoredui/kotlin-compose

Kotlin Multiplatform + Compose Multiplatform rendering engine for FactoredUI SDUI specs.

This package is the Kotlin counterpart of `@factoredui/core` + `@factoredui/react`. It lets
KMP apps (Android, iOS, JVM Desktop, Wasm) consume server-driven UI specs produced by the
FactoredUI TypeScript pipeline without any JavaScript bridge.

## How it maps to the JS packages

| JS | Kotlin |
|----|--------|
| `@factoredui/core` `Spec` / `SpecNode` types | `schema/SpecNode.kt` — identical JSON shape |
| `@factoredui/core` `binding.ts` | `schema/BindingResolver.kt` |
| `@factoredui/core` `action-dispatch.ts` | `renderer/RenderContext.kt` `dispatch()` |
| `@factoredui/react` `renderSpec()` | `renderer/RenderNode.kt` `RenderSpec()` |
| `@factoredui/core` `CaptureAdapter` | `observability/Observability.kt` |
| `@factoredui/core` `evaluateFlag()` | `experiments/Experiments.kt` |
| `@factoredui/core` `loadSpec()` | `adapter/SpecAdapter.kt` |

## Supported targets

| Target | Status |
|--------|--------|
| Android | Supported |
| iOS (arm64, x64, simulatorArm64) | Supported |
| JVM Desktop | Supported |
| Wasm (browser) | Supported (experimental) |

## Build

Requires JDK 11+. Kotlin toolchain and Android SDK are bootstrapped by Gradle.

```bash
# Bootstrap Gradle wrapper (one-time, requires an internet connection)
gradle wrapper --gradle-version 8.6

# Build all targets
./gradlew assemble

# Run all tests
./gradlew allTests
```

## Usage

### 1. Deserialize a spec

```kotlin
import ai.factoredui.compose.schema.Spec
import kotlinx.serialization.json.Json

val json = Json { ignoreUnknownKeys = true }
val spec = json.decodeFromString<Spec>(specJsonString)
```

### 2. Build a render context

```kotlin
import ai.factoredui.compose.renderer.RenderContext
import ai.factoredui.compose.observability.LoggingObservability
import ai.factoredui.compose.experiments.InMemoryExperiments

val context = RenderContext(
    actions = mapOf(
        "navigate" to { params -> router.push(params["route"] as String) },
        "submit" to { params -> viewModel.submit(params) },
    ),
    data = mapOf("shell" to mapOf("inputText" to state.inputText)),
    observability = LoggingObservability(),
    experiments = InMemoryExperiments(mapOf("checkout-redesign" to "treatment")),
)
```

### 3. Render the spec

```kotlin
import ai.factoredui.compose.renderer.RenderSpec

@Composable
fun AppShell(spec: Spec, context: RenderContext) {
    RenderSpec(root = spec.root, context = context)
}
```

## Schema nodes ported

All 20 SpecNodeType values are declared in the schema. The renderer handles:

| Node | Renderer |
|------|----------|
| `column` | `Column` with padding/gap |
| `row` | `Row` with padding/gap |
| `stack` | `Box` |
| `scrollview` | `Column` + `verticalScroll` |
| `grid` | Chunked rows (LazyVerticalGrid in a later milestone) |
| `text` | `Text` with variant → MaterialTheme.typography mapping |
| `button` | `Button` / `OutlinedButton` / `TextButton` per variant |
| `image` | `AsyncImage` via Coil 3 KMP — URL loading, content scale, rounded corners |
| `icon` | Text placeholder (icon font wiring in a later milestone) |
| `divider` | `Divider` |
| `spacer` | `Spacer` |
| `list` | `LazyColumn` with per-item context injection |
| `card` | `Card` |
| `textinput` | `OutlinedTextField` with two-way binding — typed text flows to `RenderContext` |
| `chip` | `FilterChip` |
| `tabs`, `modal`, `toggle`, `select`, `slider` | Stub Box — not yet implemented |

## Observability

```kotlin
// Development: prints to console
val obs = LoggingObservability()

// Tests: inspect what fired
val obs = object : Observability {
    val renders = mutableListOf<String>()
    override fun onRender(nodeId: String) { renders.add(nodeId) }
    override fun onInteraction(nodeId: String, action: ActionRef) = Unit
}

// Production: wire to OpenTelemetry span in Milestone 2
```

## A/B Experiments

```kotlin
// Always control (default)
ControlExperiments

// Test-friendly
InMemoryExperiments(mapOf("slot-id" to "treatment"))

// Production: implement Experiments interface backed by PostHog / Supabase flags
```

## Two-way bindings

The `textinput` primitive uses the binding path on its `value` prop to write typed
text back into `RenderContext`:

```json
{ "type": "textinput", "props": { "value": "{shell.composeText}", "placeholder": "Type..." } }
```

As the user types, `context.setBinding("shell.composeText", typedText)` is called and
the `dataFlow` emits; any node reading `{shell.composeText}` recomposes. Action
handlers (`sendSms`, etc.) read the current value from `context.data["shell"]["composeText"]`.

## Version 0.2.1 — what's new

- **Action params now resolve bindings before dispatch.** A spec like
  `{"action": "reply", "params": {"phone": "{item.phone}"}}` arrives at the
  handler as `{"phone": "+15551234567"}` — a plain `String`, not a raw
  `SpecValue.StringValue("{item.phone}")`. Host code can cast directly.
- **`list` `data` prop accepts binding refs.** Use `"data": "threads"` for a
  top-level key, or `"data": "{item.branches}"` inside a nested list
  template to iterate a field of the enclosing item.

## Version 0.2.0

- **textinput** writes back to bindings (state hoisting done in commonMain).
- **image** renders real URLs via Coil 3 KMP. Platform-agnostic — no expect/actual,
  no per-target forks. Works on Android, iOS, JVM Desktop, and Wasm from `commonMain`.
- `RenderContext.data` became reactive (`StateFlow`); `setBinding(path, value)` mutates
  the reactive store and triggers recomposition.
- Gradle `group`/`version` set; `publishToMavenLocal` now works out-of-the-box
  (see "Consume from an Android app" below).

## Remaining milestone scope

- `LazyVerticalGrid` for grid nodes
- Full `TABS`, `MODAL`, `TOGGLE`, `SELECT`, `SLIDER` implementations
- Supabase `SpecAdapter` implementation for remote spec loading
- OpenTelemetry `Observability` implementation
- Compose UI semantics tests with `createComposeRule()`

## Consume from an Android app

Artifacts are published to a **public static Maven repo on GitHub Pages** at
`https://jjrasche.github.io/factoredui/`. No auth required.

Push a tag named `kotlin-compose-v<version>` (e.g. `kotlin-compose-v0.2.0`)
and CI ([.github/workflows/kotlin-compose-publish.yml](../../.github/workflows/kotlin-compose-publish.yml))
builds artifacts, then commits them to the `gh-pages` branch which GitHub
serves over HTTPS.

In your consumer `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven("https://jjrasche.github.io/factoredui/")
    }
}
```

In your consumer `app/build.gradle.kts`:

```kotlin
dependencies {
    implementation("ai.factoredui:kotlin-compose-android:0.2.1")
}
```

That's the whole integration — no PAT, no credentials, no `.m2` tweaks.

**One-time repo setup**: after the first CI run creates the `gh-pages` branch,
enable GitHub Pages in the repo settings (Settings → Pages → Source: "Deploy
from a branch", Branch: `gh-pages`, Folder: `/`).
