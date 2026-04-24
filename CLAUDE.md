# factoredui

The rendering half of agent-platform. Kotlin Multiplatform + Compose Multiplatform renderer for declarative UI specs. Targets Android, iOS (3 arches), JVM desktop, and browser (wasmJs). Published to Maven at `https://jjrasche.github.io/factoredui/`.

## Stack

- Language: Kotlin Multiplatform (KMP) + Compose Multiplatform
- Build: Gradle (`packages/kotlin-compose/`)
- Schema: declarative SDUI specs — a closed enum of primitives + typed props + `{binding.refs}` against a reactive data flow
- Publish: gradle `publish` → local maven repo → deployed to gh-pages (`https://jjrasche.github.io/factoredui/`)

## Packages (post-2026-04-24 cleanup)

- `packages/kotlin-compose/` — **the live renderer.** All rendering work happens here.
- `packages/core/` — TypeScript. Contains `src/sdui/spec-types.ts` (canonical spec schema source of truth, mirrored into Kotlin `SpecNode.kt`) plus capture/factor/experiment pipelines. Pipeline code is **deferred for a Kotlin port**, not actively used by any current consumer. See agent-platform's memory for the "AI-proactive UI improvement" thesis that motivates it.

## What was deleted on 2026-04-24

- `packages/react/` — React renderer (replaced by kotlin-compose WASM).
- `packages/react-native/` — RN primitives (replaced by kotlin-compose Android/iOS).
- `packages/adapter-supabase/` — Supabase-specific storage adapter (agent-platform uses SQLite; not relevant to the rendering role).

## Commands

```bash
# Kotlin (the primary work)
cd packages/kotlin-compose
./gradlew build             # compile all targets
./gradlew wasmJsBrowserDevelopmentExecutableDistribution   # browser bundle
./gradlew publish           # publish to local maven repo

# TS (spec-types + dormant pipeline)
npm run build               # tsup: core
npm test                    # vitest run
npm run typecheck
```

## Architecture

### Renderer dispatch (kotlin-compose)

`SpecNodeType` is a closed enum in `src/commonMain/.../schema/SpecNode.kt`, mirrored exactly with the TS union in `packages/core/src/sdui/spec-types.ts`. `RenderNode.kt` has a single `when (node.type)` dispatch to a private composable per primitive. To add a primitive: update both enum sites + add a renderer branch.

### Primitive classes

- **Container primitives** — column, row, stack, scrollview, grid, list, card, tabs, modal.
- **Widget leaves** — text, button, image, icon, divider, spacer, textinput, toggle, select, slider, chip.
- **Dense/semantic primitives** — new category. `forcegraph` is the first. Takes typed data (topology) + physics config; runs its own simulation loop; no child components. Future members: timeline, heatmap, flow-field, scatterplot3d.

### Spec schema

`Spec { spec_version, renderer_min, root: SpecNode }`. `SpecNode { id, type, props, children, visible, action }`. Props are polymorphic `SpecValue` (string / number / bool / null / nested-node / array / object). Binding refs are `"{path.to.value}"` strings, resolved at render time from a `StateFlow<Map<String, Any?>>`.

### Actions

`ActionRef { action, params }` dispatched through the host app's `ActionRegistry`. Client-side only; the host resolves the action to either a local effect or an API call to the back-end.

## Project-Specific Notes

- CONCEPT.md — original design brief (TS-era, partially stale; will be updated post-refactor)
- RESEARCH.md — landscape analysis, prior art
- packages/kotlin-compose/README.md — detailed setup / publish / consumer integration
