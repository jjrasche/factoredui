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

## Schema nodes ported (Milestone 1)

All 20 SpecNodeType values are declared in the schema. The renderer handles:

| Node | Renderer |
|------|----------|
| `column` | `Column` with padding/gap |
| `row` | `Row` with padding/gap |
| `stack` | `Box` |
| `scrollview` | `Column` + `verticalScroll` |
| `grid` | Chunked rows (LazyVerticalGrid in Milestone 2) |
| `text` | `Text` with variant → MaterialTheme.typography mapping |
| `button` | `Button` / `OutlinedButton` / `TextButton` per variant |
| `image` | Placeholder Box (Coil wiring in Milestone 2) |
| `icon` | Text placeholder (icon font wiring in Milestone 2) |
| `divider` | `Divider` |
| `spacer` | `Spacer` |
| `list` | `LazyColumn` with per-item context injection |
| `card` | `Card` |
| `textinput` | `OutlinedTextField` stub (state hoisting in Milestone 2) |
| `chip` | `FilterChip` |
| `tabs`, `modal`, `toggle`, `select`, `slider` | Stub Box — Milestone 2 |

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

## Milestone 2 scope

- Real image loading via expect/actual (Coil on Android, SDWebImage wrapper on iOS)
- `LazyVerticalGrid` for grid nodes
- Full `TABS`, `MODAL`, `TOGGLE`, `SELECT`, `SLIDER` implementations
- Supabase `SpecAdapter` implementation for remote spec loading
- OpenTelemetry `Observability` implementation
- `TextInput` state hoisting pattern
- Compose UI semantics tests with `createComposeRule()`
