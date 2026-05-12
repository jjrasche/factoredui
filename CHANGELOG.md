# Changelog

All notable changes to factoredui Kotlin artifacts. Format inspired by Keep a Changelog; versioning follows SemVer.

## [0.9.0] — schema / renderer split

Single bundled `ai.factoredui:kotlin-compose:0.8.0` is now two artifacts.

### Added

- **`ai.factoredui:kotlin-compose-schema:0.9.0`** — pure-Kotlin wire-format types. No Compose Multiplatform, no Ktor, no androidx, no skiko. KMP targets: `jvm`, `android`, `iosX64`, `iosArm64`, `iosSimulatorArm64`, `wasmJs`, `linuxX64`.
  - SDUI schema: `Spec`, `SpecNode`, `SpecValue`, `SpecNodeType`, `ActionRef`, `SpecValueSerializer`, `RENDERER_VERSION`
  - Typed prop accessors: `SpecProps.kt` (`asButtonProps`, `asTextProps`, `asForceGraphProps`, …)
  - Binding resolver: `BindingResolver.kt`
  - Capture wire types: `CaptureEvent`, `EventType`, `CapturePlatform`, `Session` (data class)

### Changed

- **`ai.factoredui:kotlin-compose:0.9.0`** — renderer artifact. Now depends on `kotlin-compose-schema` via Gradle `api(...)` so existing consumers transitively get the same schema types they had before; coordinates and package names are unchanged. Pulled out of this artifact and into `kotlin-compose-schema`:
  - `ai.factoredui.compose.schema.*` (Spec types + binding resolver + prop accessors)
  - `ai.factoredui.compose.capture.CaptureEvent` / `EventType` / `CapturePlatform`
  - `ai.factoredui.compose.capture.Session` (the `@Serializable` data class)

  Renderer-side runtime stays in `kotlin-compose`: `SessionManager`, `CaptureClient`, `EventWriter`, `HttpEventTransport`, `CaptureObservability`, all renderer / forcegraph / observability / experiments / adapter / testing code.

- **`ai.factoredui:kotlin-server:0.9.0`** — switched its shared-types dependency from `kotlin-compose` to `kotlin-compose-schema`. Server consumers no longer pull Compose Multiplatform transitively.

### Migration

**App / device consumers** rendering specs: no source change required. `kotlin-compose` re-exports the schema types via `api(...)`. Bump your dependency version from `0.8.0` to `0.9.0`. If you want to depend on the schema artifact directly (e.g. for codegen, a multiplatform UI-tests module), add:

```kotlin
implementation("ai.factoredui:kotlin-compose-schema:0.9.0")
```

**Server / engine consumers** that need to emit or validate specs without Compose Multiplatform: depend on the new artifact:

```kotlin
implementation("ai.factoredui:kotlin-compose-schema:0.9.0")
// drop any previous `kotlin-compose` dep if it was only for SpecNode / CaptureEvent
```

**Existing consumers on 0.8.0** continue to work without changes — the single bundled `kotlin-compose:0.8.0` artifact stays published as a frozen snapshot. No SemVer break.

### Pinned dependency versions (unchanged from 0.8.0)

- Kotlin `2.3.21`
- Compose Multiplatform `1.10.3`
- kotlinx.serialization `1.11.0`
- kotlinx.coroutines `1.10.2`
- kotlinx.datetime `0.7.1`
- Android Gradle Plugin `8.9.3`
- Ktor `3.2.0`
- Coil `3.2.0`

## [0.8.0]

Pre-split bundled artifact. `ai.factoredui:kotlin-compose:0.8.0` shipped the full schema + renderer + capture stack as one publication. Remains published as a frozen snapshot for consumers that haven't migrated to the split layout.
