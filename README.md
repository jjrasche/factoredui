# FactoredUI

Capture user interactions, compute standardized behavioral factors, run LLM-driven experiments, and render server-driven UI — all from Supabase.

Three npm packages, one pipeline — plus a Kotlin Multiplatform rendering engine:

| Package | Purpose | Install |
|---|---|---|
| `@factoredui/core` | Capture pipeline, factors, experiments, SDUI engine, CLI | `npm i @factoredui/core` |
| `@factoredui/react` | Provider, hooks, path context, SDUI renderer | `npm i @factoredui/react` |
| `@factoredui/react-native` | 20 themed RN component primitives | `npm i @factoredui/react-native` |
| `kotlin-compose` | KMP + Compose Multiplatform renderer (Android, iOS, Desktop, Wasm) | Gradle — see below |

## Kotlin Multiplatform / Compose Multiplatform

`packages/kotlin-compose/` is a Gradle subproject consumed directly by KMP apps.
It is **not** an npm package — it is not installed via npm.

```kotlin
// settings.gradle.kts in your KMP app
includeBuild("path/to/factored-ui/packages/kotlin-compose")

// build.gradle.kts
dependencies {
    commonMainImplementation("ai.factoredui:kotlin-compose")
}
```

**What it provides:**
- `schema/` — `Spec`, `SpecNode`, `SpecValue` data classes matching the TypeScript JSON format exactly
- `renderer/` — `RenderSpec()` / `RenderNode()` Composables handling 15 of 20 primitives
- `observability/` — `Observability` interface (`onRender` / `onInteraction`) + `LoggingObservability` default
- `experiments/` — `Experiments` interface (`assignVariant` / `logExposure`) + `InMemoryExperiments` for tests
- `adapter/` — `SpecAdapter` interface for the remote/cache/baseline spec loading contract

**Targets:** Android, iOS (arm64/x64/simulatorArm64), JVM Desktop, Wasm (browser)

**Build:**
```bash
cd packages/kotlin-compose
./gradlew assemble     # build all targets
./gradlew allTests     # run schema + binding + observability tests
```

See [`packages/kotlin-compose/README.md`](packages/kotlin-compose/README.md) for full usage docs.

## Quick Start

### 1. Set up your database

```bash
npx factoredui init
```

This copies 27 migrations and the clustering edge function into your Supabase project directory, then prints the remaining setup steps (extensions, PostgREST config, edge function deploy).

### 2. Initialize in your app

```tsx
import { createClient } from '@supabase/supabase-js'
import { Provider } from '@factoredui/react'

const supabase = createClient(url, anonKey, {
  db: { schema: 'factoredui' }
})

function App() {
  return (
    <Provider supabase={supabase}>
      <YourApp />
    </Provider>
  )
}
```

The `<Provider>` automatically starts capture — clicks, scrolls, navigation, rage clicks — and batches them to `factoredui.events`.

### 3. Structure your component tree

```tsx
import { Flow, Page, Component, Element } from '@factoredui/react'

function Onboarding() {
  return (
    <Flow name="onboarding">
      <Page name="review">
        <Component name="photo-grid">
          <Element name="upload-button">
            <button>Upload</button>
          </Element>
        </Component>
      </Page>
    </Flow>
  )
}
```

Events are tagged with paths like `onboarding/review/photo-grid/upload-button`. SQL materialized views compute factors (completion rate, error rate, rage clicks, hesitation, etc.) per component path.

### 4. Run experiments

```tsx
import { useFlag } from '@factoredui/react'

function CheckoutPage() {
  const { variant, loading } = useFlag('checkout-redesign')

  if (loading) return <Spinner />
  return variant === 'treatment' ? <NewCheckout /> : <OldCheckout />
}
```

Experiments use factor-based targeting, automatic governance (statistical significance checks on a cron), and a full audit log.

### 5. Render server-driven UI

```tsx
import { renderSpec } from '@factoredui/react'
import { loadSpec, createSpecStorage, devSignatureVerifier } from '@factoredui/core'

const storage = createSpecStorage()
const spec = await loadSpec(supabase, 'home-hero', storage, devSignatureVerifier)

// In your component:
<>{renderSpec(spec.tree, componentRegistry, actionRegistry, dataSources)}</>
```

Specs are signed JSON documents stored in Supabase. The SDUI engine resolves data bindings, dispatches actions, and renders through a platform-specific component registry.

## Architecture

```
User interactions
  → CaptureAdapter (web or RN)
  → batched writer → factoredui.events
  → SQL materialized views → factoredui.factors
  → experiment system reads factors for targeting + governance
  → SDUI specs define what to render per variant
```

**Data flow**: Capture → Events → Factors → Experiments → SDUI

**Cross-platform**: Same `CaptureAdapter` interface for web and React Native. Same `Flow/Page/Component/Element` path hierarchy. Platform is a bucketing dimension for experiments.

## Per-App Deployment

FactoredUI is a package, not a service. Each consuming app:

1. Runs `npx factoredui init` to copy migrations and edge functions
2. Applies migrations to its own Supabase database
3. Adds `factoredui` to `PGRST_DB_SCHEMAS`
4. Deploys the clustering edge function
5. Configures pg_cron GUC settings for the cron→edge function auth chain

No shared infrastructure. Each app owns its own schema.

## Packages

### @factoredui/core

Pure TypeScript, no framework dependencies.

- `initCapture(config)` — start the capture pipeline
- `createWebAdapter()` — DOM-based capture adapter
- `evaluateFlag(supabase, experimentName, ...)` — get a user's experiment assignment
- `createExperiment()` / `startExperiment()` — experiment lifecycle
- `queryFactors()` / `queryComponentFactors()` — read computed factors
- `loadSpec()` / `validateSpec()` — SDUI spec loading and validation
- `createEd25519Verifier()` / `createEd25519Signer()` — spec signing
- CLI: `npx factoredui init` — scaffold migrations and edge functions

Peer dependency: `@supabase/supabase-js ^2.0.0`

### @factoredui/react

React bindings. All hooks are context-aware via `<Provider>`.

- `<Provider>` — initializes capture, provides Supabase context
- `<Flow>` / `<Page>` / `<Component>` / `<Element>` — path hierarchy
- `useFlag(experimentName)` — experiment assignment with loading state
- `useFactors()` / `useComponentFactors(path)` — factor data
- `useGovernanceLog(experimentId)` — realtime governance verdicts
- `useExperimentDashboard()` — experiment summary list
- `renderSpec(tree, components, actions, data)` — SDUI renderer
- `useSourceData(sources, supabase)` — async data source resolution

Peer dependencies: `@factoredui/core`, `@supabase/supabase-js`, `react ^18 || ^19`

### @factoredui/react-native

React Native component registry for SDUI rendering.

- `createComponentRegistry(theme)` — 20 themed primitives (Text, Button, Card, Tabs, Grid, Modal, etc.)
- `createRnAdapter()` — React Native capture adapter

Peer dependencies: `@factoredui/core`, `@factoredui/react`, `react`, `react-native >=0.73`

## React Native / Expo

```tsx
import { Provider } from '@factoredui/react'
import { createRnAdapter, createComponentRegistry } from '@factoredui/react-native'

const adapter = createRnAdapter()
const components = createComponentRegistry({ primary: '#007AFF' })

function App() {
  return (
    <Provider supabase={supabase} adapter={adapter} platform="ios">
      <YourApp />
    </Provider>
  )
}
```

## License

MIT
