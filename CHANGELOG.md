# Changelog

All notable changes to factoredui Kotlin artifacts. Format inspired by Keep a Changelog; versioning follows SemVer.

## [0.10.0] — factor + experiment engine, reactive renderer features

### Added

- **`ai.factoredui:kotlin-engine:0.10.0`** — new pure-Kotlin, multiplatform module (`jvm`, `android`, `iosX64`, `iosArm64`, `iosSimulatorArm64`, `wasmJs`, `linuxX64`) holding the storage-free decision logic of the autonomy loop: factor types + k-means, DJB2 traffic bucketing, the targeting predicate engine, governance verdicts, `ExperimentValidationError` (sealed) + `findValidationError`, and the factor dashboard spec builder. Lets server/native/Android consumers run the engine's logic without Postgres or Compose.
- **`ai.factoredui:kotlin-server:0.10.0`** — factor engine: five v1 factors (`error_rate`; `rage_click_rate`/`dead_click_rate`/`scroll_reversal_rate`; `hesitation_time_p50_ms`) as Postgres views over `factoredui_events`, plus `queryFactors` / `queryComponentFactors` taking a JDBC `Connection`. Depends on `kotlin-engine`.
- **Renderer (`ai.factoredui:kotlin-compose:0.10.0`)**: reactive `LIST` `data_source` (live `HostDataSource` subscription + per-row `{row.*}` scoping); `RenderSpec(Flow<Spec>)` hot-swap; `Spec.keybindings: Map<ShortcutKey, ActionRef>` (closed key enum) via `KeybindingHost`.
- **Schema (`ai.factoredui:kotlin-compose-schema:0.10.0`)**: `ListProps.data_source`, `Spec.keybindings` + `ShortcutKey`.

### Changed

- `Observability.onInteraction` now carries the resolved action params, which `CaptureObservability` nests under `payload.params` — captured interactions are anchored to the data they were about.
- `Experiments.assignVariant` / `logExposure` gain an optional `subjectId`, so one instance buckets many workers (deterministic per `(slotId, subjectId)`).

Backwards-compatible: existing specs and consumers are unaffected (new fields default to empty / null). Bump your dependency from `0.9.0` to `0.10.0`.

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

### Known not-blocking issues

- [#1](https://github.com/jjrasche/factoredui/issues/1) — `RenderControlsTest` 6 tests NPE under `runComposeUiTest`. Pre-existing on the 0.8.0 baseline, reproduced on a clean stash; not introduced by the split. Tracked separately.

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
