# factoredui

The rendering half of agent-platform. Kotlin Multiplatform + Compose Multiplatform renderer for declarative UI specs. Targets Android, iOS (3 arches), JVM desktop, and browser (wasmJs). Published to Maven at `https://jjrasche.github.io/factoredui/`.

## Stack

- Language: Kotlin Multiplatform (KMP) + Compose Multiplatform
- Build: Gradle (`packages/kotlin-compose/`)
- Schema: declarative SDUI specs — a closed enum of primitives + typed props + `{binding.refs}` against a reactive data flow
- Publish: gradle `publish` → local maven repo → deployed to gh-pages (`https://jjrasche.github.io/factoredui/`)

## Packages (post-2026-04-24 cleanup)

- `packages/kotlin-compose/` — **the live renderer.** All rendering work happens here.
- `packages/kotlin-compose-schema/` — pure-Kotlin spec/wire types (the schema source of truth).
- `packages/kotlin-engine/` — the factor + experiment engine (clustering/governance/targeting), bit-exact parity tested.
- `packages/kotlin-server/`, `packages/kotlin-compose-playground/` — server + dev playground.

## What was deleted

- 2026-04-24: `packages/react/`, `packages/react-native/`, `packages/adapter-supabase/` (replaced by kotlin-compose).
- 2026-06-25: `packages/core/` (the last TypeScript) + all root npm tooling (package.json, tsconfig, vitest). It was the pre-Kotlin reference — sdui/capture/experiment/factor — and was fully ported (capture→kotlin-compose/capture, experiment+factors→kotlin-engine, sdui→kotlin-compose-schema). The repo is now 100% Kotlin/gradle, no npm island.

## Commands

```bash
# 100% Kotlin/gradle — run from repo root
./gradlew build             # compile + test all modules, all targets
./gradlew :kotlin-compose:wasmJsBrowserDevelopmentExecutableDistribution   # browser bundle
./gradlew :kotlin-compose:publish   # publish to local maven repo (CDN release = push a kotlin-compose-v* tag)
```

## Architecture

### Renderer dispatch (kotlin-compose)

`SpecNodeType` is a closed enum in `kotlin-compose-schema/.../schema/SpecNode.kt` — the sole source of truth. `RenderNode.kt` has a single `when (node.type)` dispatch to a private composable per primitive. To add a primitive: update the enum + add a renderer branch.

### Primitive classes

- **Container primitives** — column, row, stack, scrollview, grid, list, card, tabs, modal.
- **Widget leaves** — text, button, image, icon, divider, spacer, textinput, toggle, select, slider, chip.
- **Dense/semantic primitives** — new category. `forcegraph` is the first. Takes typed data (topology) + physics config; runs its own simulation loop; no child components. Future members: timeline, heatmap, flow-field, scatterplot3d.

### Spec schema

`Spec { spec_version, renderer_min, root: SpecNode }`. `SpecNode { id, type, props, children, visible, action }`. Props are polymorphic `SpecValue` (string / number / bool / null / nested-node / array / object). Binding refs are `"{path.to.value}"` strings, resolved at render time from a `StateFlow<Map<String, Any?>>`.

### Actions

`ActionRef { action, params }` dispatched through the host app's `ActionRegistry`. Client-side only; the host resolves the action to either a local effect or an API call to the back-end.

## Project-Specific Notes

- CONCEPT.md — the thesis (5-piece autonomy loop, three-tier factor model, novelty)
- DECISIONS.md — architectural decisions and the porting roadmap (current state of the loop, what comes next)
- packages/kotlin-compose/README.md — detailed setup / publish / consumer integration
