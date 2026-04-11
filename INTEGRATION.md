# Auxi Integration Guide

How to integrate auxi into a consuming application. Auxi has two independent capabilities — **capture** (behavioral observation) and **SDUI** (server-driven UI) — that can be adopted separately or together.

## Entry Points

| Import path       | Purpose                                     | Platform      |
|--------------------|---------------------------------------------|---------------|
| `auxi`            | Core: capture init, flags, factors, governance | Any           |
| `auxi/react`      | React bindings: provider, hooks, path context  | Web (React)   |
| `auxi/react-native` | React Native bindings: provider, adapter contract | iOS/Android/Web (Expo) |
| `auxi/sdui`       | SDUI engine: renderer, spec loading, components  | Any (renderer is RN) |

Install auxi as a dependency and `@supabase/supabase-js` as a peer:

```bash
npm install auxi @supabase/supabase-js
```

---

## 1. Capture Only (Web)

Wraps your existing React app to capture user interactions. No UI changes required.

```tsx
// main.tsx
import { AuxiProvider } from 'auxi/react'
import { createClient } from '@supabase/supabase-js'

const supabase = createClient(SUPABASE_URL, SUPABASE_ANON_KEY)

createRoot(document.getElementById('root')!).render(
  <AuxiProvider supabase={supabase}>
    <App />
  </AuxiProvider>
)
```

Wrap pages and components in path context providers to get structured event paths:

```tsx
import { AuxiFlow, AuxiPage, AuxiComponent } from 'auxi/react'

function App() {
  return (
    <AuxiFlow name="my-app">
      <AuxiPage name="dashboard">
        <AuxiComponent name="chart">
          <MyChart />
        </AuxiComponent>
      </AuxiPage>
    </AuxiFlow>
  )
}
```

Events automatically capture the component path (`my-app/dashboard/chart`), enabling per-component factor computation in Supabase views.

### Hooks available inside `<AuxiProvider>`:

```tsx
import { useFlag, useFactors, useGovernanceLog, useExperimentDashboard } from 'auxi/react'

// Feature flags with experiment bucketing
const { variantKey, config, isLoading } = useFlag('onboarding-v2')

// Behavioral factors for a component path
const { factors, isLoading } = useFactors('my-app/dashboard/chart')

// Governance log with realtime updates
const { log, isLoading } = useGovernanceLog(experimentId)

// Experiment dashboard summaries
const { summaries, isLoading } = useExperimentDashboard({ status: 'running' })
```

---

## 2. Capture Only (React Native / Expo)

Same pattern, but you provide a `CaptureAdapter` that implements platform-specific concerns.

```tsx
// _layout.tsx
import { AuxiNativeProvider } from 'auxi/react-native'
import { createExpoCaptureAdapter } from './auxi/capture-adapter'

const adapter = createExpoCaptureAdapter()

export default function Layout() {
  return (
    <AuxiNativeProvider supabase={supabase} adapter={adapter} platform="android">
      <Slot />
    </AuxiNativeProvider>
  )
}
```

### CaptureAdapter contract

Your adapter must implement this interface (imported from `auxi/react-native`):

```ts
import type { CaptureAdapter, AuxiEvent } from 'auxi/react-native'

interface CaptureAdapter {
  startListening(onEvent: (event: AuxiEvent) => void): void
  stopListening(): void
  collectSessionMetadata(): Record<string, unknown>
  storeSessionId(id: string): void
  loadSessionId(): string | null
  clearSessionId(): void
  registerUnloadHandler(onUnload: () => void): void
}
```

Typical implementations use `ErrorUtils` for JS error capture, `AppState` for visibility events, `AsyncStorage` for session persistence, and `expo-application` for device metadata.

---

## 3. SDUI (Server-Driven UI)

The SDUI engine renders your entire UI from declarative JSON specs. The app becomes a shell — all screens are spec-driven, and auxi can swap UI configurations via experiments.

### 3a. Theme and Component Registry

Provide your app's design tokens. Auxi creates all 20 primitives styled with your theme:

```tsx
import { createComponentRegistry, type ThemeTokens } from 'auxi/sdui'

const theme: ThemeTokens = {
  colors: {
    background: '#ffffff',
    foreground: '#0a0a0a',
    card: '#ffffff',
    primary: '#171717',
    primaryForeground: '#fafafa',
    secondary: '#f5f5f5',
    secondaryForeground: '#171717',
    mutedForeground: '#737373',
    destructive: '#ef4444',
    destructiveForeground: '#fafafa',
    border: '#e5e5e5',
    input: '#e5e5e5',
  },
  spacing: { xs: 4, sm: 8, md: 16, lg: 24 },
  fontSize: { xs: 12, sm: 14, base: 16, lg: 18 },
  radius: { sm: 6, md: 8, lg: 12, full: 9999 },
}

export const componentRegistry = createComponentRegistry(theme)
```

The 20 primitives: `column`, `row`, `stack`, `scrollview`, `grid`, `text`, `image`, `icon`, `divider`, `spacer`, `textinput`, `button`, `toggle`, `select`, `slider`, `card`, `list`, `tabs`, `modal`, `chip`.

To override a single component, spread and replace:

```tsx
const registry = createComponentRegistry(theme)
registry.button = myCustomButtonRenderer
```

### 3b. Storage

Specs are stored locally so the app works offline. Provide a KV storage backend:

```tsx
import { createSpecStorage, createDataSourceCache, type KVStorage } from 'auxi/sdui'
import AsyncStorage from '@react-native-async-storage/async-storage'

// React Native
const kv: KVStorage = {
  getItem: (key) => AsyncStorage.getItem(key),
  setItem: (key, value) => AsyncStorage.setItem(key, value),
  removeItem: (key) => AsyncStorage.removeItem(key),
}

// Web (localStorage works too — sync return types are accepted)
const kv: KVStorage = localStorage

export const specStorage = createSpecStorage(kv)
export const dataSourceCache = createDataSourceCache(kv)
```

### 3c. Signature Verification

Specs are signed to prevent unauthorized UI changes. In development, use the stub:

```tsx
import { devSignatureVerifier } from 'auxi/sdui'
```

For production, implement `SignatureVerifier` with Ed25519:

```ts
import type { SignatureVerifier } from 'auxi/sdui'

const productionVerifier: SignatureVerifier = {
  async verify(specHash: string, signature: string): Promise<boolean> {
    // Ed25519 verification against your embedded public key
  },
  async computeHash(spec: AuxiSpec): Promise<string> {
    // SHA-256 of the canonical JSON
  },
}
```

### 3d. Actions

The spec references actions by name. Your app defines what they do:

```tsx
import type { ActionRegistry } from 'auxi/sdui'

const actions: ActionRegistry = {
  submit: async (params) => {
    const text = params.text as string
    await processPipeline(text)
  },
  confirm: async () => { /* execute pending tool calls */ },
  reject: async () => { /* discard pending result */ },
  navigate: async (params) => {
    router.push(params.route as string)
  },
}
```

Specs reference these by name: `{ "action": "submit", "params": { "text": "{shell.inputText}" } }`.

### 3e. Data Sources

Register named data sources that specs can bind to. Each source is a fetch function with optional caching:

```tsx
import type { DataSourceRegistry } from 'auxi/sdui'

function buildSourceRegistry(): DataSourceRegistry {
  return {
    items_needed: {
      fetch: async () => {
        const { data } = await supabase.from('items').select('*').eq('status', 'needed')
        return data ?? []
      },
      cache: 'local',
      maxItems: 50,
    },
    actions_pending: {
      fetch: async () => {
        const { data } = await supabase.from('actions').select('*').eq('done', false)
        return data ?? []
      },
      cache: 'local',
    },
  }
}
```

Use the `useSourceData` hook to resolve sources with React state management:

```tsx
import { useSourceData } from 'auxi/sdui'

const { sourceData, sourcesLoaded, errors, refreshSources } = useSourceData(
  buildSourceRegistry,
  dataSourceCache,
)
```

### 3f. Spec Loading

Specs follow a fallback chain: remote (Supabase) > active (local storage) > baseline (bundled):

```tsx
import { loadSpec, type AuxiSpec } from 'auxi/sdui'

const baselineSpec: AuxiSpec = require('./assets/baseline-spec.json')

const { spec, source } = await loadSpec(
  supabase,
  'android',        // platform
  baselineSpec,     // bundled fallback
  specStorage,      // from 3b
  devSignatureVerifier, // from 3c (or production verifier)
)
```

### 3g. Rendering

Assemble a `RenderContext` and call `renderSpec`:

```tsx
import { renderSpec, type RenderContext } from 'auxi/sdui'

const renderContext: RenderContext = {
  components: componentRegistry,  // from 3a
  actions: actionRegistry,        // from 3d
  data: {                         // merged data for binding resolution
    ...sourceData,                // from 3e
    shell: { isProcessing, inputText, hasResult },  // app state
  },
}

function ShellScreen() {
  const rendered = renderSpec(spec.root, renderContext)
  return <View style={{ flex: 1 }}>{rendered}</View>
}
```

### 3h. Data Binding in Specs

Specs reference data via `{path.to.value}` strings. The renderer resolves them against the `data` object in `RenderContext`:

```json
{
  "id": "status-text",
  "type": "text",
  "props": {
    "value": "Processing: {shell.isProcessing}",
    "variant": "caption"
  },
  "visible": "{shell.hasResult}"
}
```

- `{shell.isProcessing}` resolves to `renderContext.data.shell.isProcessing`
- `{sources.items_needed}` resolves to the array returned by your data source
- `visible` field accepts a binding ref — node renders only when the value is truthy
- No expressions, no arithmetic, no conditionals beyond boolean visibility

---

## Spec Format

A spec is a JSON document with this envelope:

```json
{
  "spec_version": 1,
  "renderer_min": 1,
  "root": {
    "id": "root",
    "type": "column",
    "props": { "padding": 16, "gap": 12 },
    "children": [...]
  }
}
```

Each node has:

| Field      | Required | Description                                    |
|------------|----------|------------------------------------------------|
| `id`       | yes      | Unique identifier (used as React key + auxi path) |
| `type`     | yes      | One of the 20 primitive types                  |
| `props`    | no       | Component-specific props, may contain binding refs |
| `children` | no       | Nested spec nodes                              |
| `visible`  | no       | Binding ref — node renders only when truthy    |
| `action`   | no       | `{ "action": "name", "params": { ... } }`     |

---

## Putting It All Together

A minimal SDUI shell (Expo):

```tsx
// app/index.tsx
import { View } from 'react-native'
import { renderSpec, useSourceData, type RenderContext } from 'auxi/sdui'
import { componentRegistry } from '../src/auxi/components'
import { specStorage, dataSourceCache, devSignatureVerifier } from '../src/auxi/storage'
import { actionRegistry } from '../src/auxi/actions'
import { buildSourceRegistry } from '../src/auxi/sources'
import baselineSpec from '../assets/baseline-spec.json'

export default function ShellScreen() {
  const { sourceData, sourcesLoaded, refreshSources } = useSourceData(
    buildSourceRegistry,
    dataSourceCache,
  )

  if (!sourcesLoaded) return null

  const context: RenderContext = {
    components: componentRegistry,
    actions: actionRegistry,
    data: sourceData,
  }

  return <View style={{ flex: 1 }}>{renderSpec(baselineSpec.root, context)}</View>
}
```

The app ships a baseline spec in the APK. Auxi can push new specs via Supabase, gated by experiments and validated by signature verification. The app's UI changes without app store updates.
