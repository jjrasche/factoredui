# Auxi Integration Guide

All auxi apps are cross-platform. One codebase, one import, renders on iOS, Android, and web via React Native + Expo.

```bash
npm install auxi @supabase/supabase-js
```

Everything comes from one import:

```tsx
import { AuxiProvider, createComponentRegistry, renderSpec, useSourceData, ... } from 'auxi'
```

## What Your App Provides

Auxi handles capture, factors, experiments, governance, rendering, spec loading, and storage. Your app provides three things:

**1. Theme tokens** (how it looks)
```tsx
import { createComponentRegistry, type ThemeTokens } from 'auxi'

const theme: ThemeTokens = {
  colors: {
    background: '#ffffff', foreground: '#0a0a0a', card: '#ffffff',
    primary: '#171717', primaryForeground: '#fafafa',
    secondary: '#f5f5f5', secondaryForeground: '#171717',
    mutedForeground: '#737373',
    destructive: '#ef4444', destructiveForeground: '#fafafa',
    border: '#e5e5e5', input: '#e5e5e5',
  },
  spacing: { xs: 4, sm: 8, md: 16, lg: 24 },
  fontSize: { xs: 12, sm: 14, base: 16, lg: 18 },
  radius: { sm: 6, md: 8, lg: 12, full: 9999 },
}

export const componentRegistry = createComponentRegistry(theme)
```

**2. Action handlers** (what the buttons do)
```tsx
import type { ActionRegistry } from 'auxi'

export const actions: ActionRegistry = {
  submit: async (params) => { /* your domain logic */ },
  confirm: async () => { /* execute pending work */ },
  navigate: async (params) => { router.push(params.route as string) },
}
```

**3. Data source queries** (where the data comes from)
```tsx
import type { DataSourceRegistry } from 'auxi'

export function buildSourceRegistry(): DataSourceRegistry {
  return {
    items: {
      fetch: async () => {
        const { data } = await supabase.from('items').select('*')
        return data ?? []
      },
      cache: 'local',
    },
  }
}
```

---

## Setup

### Provider

Wrap your root in `AuxiProvider` with a `CaptureAdapter`:

```tsx
import { AuxiProvider } from 'auxi'

export default function Layout() {
  return (
    <AuxiProvider supabase={supabase} adapter={adapter} platform="android">
      <Slot />
    </AuxiProvider>
  )
}
```

`platform` accepts `"ios"`, `"android"`, or `"web"`. Expo handles web via `react-native-web`.

### CaptureAdapter

Your adapter implements platform-specific capture:

```ts
import type { CaptureAdapter } from 'auxi'
```

| Method                   | Purpose                              |
|--------------------------|--------------------------------------|
| `startListening(onEvent)`| Capture JS errors, app state changes |
| `stopListening()`        | Tear down listeners                  |
| `collectSessionMetadata()` | Device info, screen size, app version |
| `storeSessionId(id)`     | Persist session ID (AsyncStorage)    |
| `loadSessionId()`        | Restore session ID                   |
| `clearSessionId()`       | Clear on logout                      |
| `registerUnloadHandler(fn)` | Flush on background/inactive      |

### Path Context

Wrap screens and components for structured event paths:

```tsx
import { AuxiFlow, AuxiPage, AuxiComponent } from 'auxi'

<AuxiFlow name="my-app">
  <AuxiPage name="dashboard">
    <AuxiComponent name="chart">
      <MyChart />
    </AuxiComponent>
  </AuxiPage>
</AuxiFlow>
```

### Hooks

```tsx
import { useFlag, useFactors, useGovernanceLog, useExperimentDashboard } from 'auxi'

const { variantKey, config, isLoading } = useFlag('onboarding-v2')
const { factors } = useFactors('my-app/dashboard/chart')
const { log } = useGovernanceLog(experimentId)
const { summaries } = useExperimentDashboard({ status: 'running' })
```

---

## SDUI

The app is a shell. All UI comes from JSON specs.

### Storage

```tsx
import { createSpecStorage, createDataSourceCache, type KVStorage } from 'auxi'
import AsyncStorage from '@react-native-async-storage/async-storage'

const kv: KVStorage = {
  getItem: (key) => AsyncStorage.getItem(key),
  setItem: (key, value) => AsyncStorage.setItem(key, value),
  removeItem: (key) => AsyncStorage.removeItem(key),
}

export const specStorage = createSpecStorage(kv)
export const dataSourceCache = createDataSourceCache(kv)
```

### Signature Verification

```tsx
import { devSignatureVerifier } from 'auxi'  // dev only — always passes
```

For production, implement Ed25519 via the `SignatureVerifier` interface.

### Spec Loading

Fallback chain: remote (Supabase) > active (local storage) > baseline (bundled):

```tsx
import { loadSpec } from 'auxi'

const { spec, source } = await loadSpec(supabase, 'android', baselineSpec, specStorage, verifier)
```

### Rendering

```tsx
import { renderSpec, useSourceData, type RenderContext } from 'auxi'

const { sourceData, sourcesLoaded } = useSourceData(buildSourceRegistry, dataSourceCache)

const context: RenderContext = {
  components: componentRegistry,
  actions,
  data: { ...sourceData, shell: { isProcessing, inputText } },
}

return <View style={{ flex: 1 }}>{renderSpec(spec.root, context)}</View>
```

### Data Binding

Specs reference data via `{path.to.value}`:

```json
{
  "id": "status",
  "type": "text",
  "props": { "value": "{shell.inputText}", "variant": "body" },
  "visible": "{shell.hasResult}"
}
```

No expressions, no arithmetic, no conditionals beyond boolean visibility.

---

## Spec Format

```json
{
  "spec_version": 1,
  "renderer_min": 1,
  "root": { "id": "root", "type": "column", "props": { "padding": 16 }, "children": [] }
}
```

| Field      | Required | Description                                |
|------------|----------|--------------------------------------------|
| `id`       | yes      | Unique identifier (React key + auxi path)  |
| `type`     | yes      | One of 20 primitives                       |
| `props`    | no       | Component props, may contain binding refs  |
| `children` | no       | Nested spec nodes                          |
| `visible`  | no       | Binding ref, renders only when truthy      |
| `action`   | no       | `{ "action": "name", "params": { ... } }` |

The 20 primitives: `column`, `row`, `stack`, `scrollview`, `grid`, `text`, `image`, `icon`, `divider`, `spacer`, `textinput`, `button`, `toggle`, `select`, `slider`, `card`, `list`, `tabs`, `modal`, `chip`.

---

## Minimal Shell

```tsx
import { View } from 'react-native'
import { createComponentRegistry, renderSpec, useSourceData, type RenderContext } from 'auxi'
import { theme } from './theme'
import { actions } from './actions'
import { buildSourceRegistry } from './sources'
import { dataSourceCache } from './storage'
import baselineSpec from '../assets/baseline-spec.json'

const componentRegistry = createComponentRegistry(theme)

export default function ShellScreen() {
  const { sourceData, sourcesLoaded } = useSourceData(buildSourceRegistry, dataSourceCache)
  if (!sourcesLoaded) return null

  const context: RenderContext = {
    components: componentRegistry,
    actions,
    data: sourceData,
  }

  return <View style={{ flex: 1 }}>{renderSpec(baselineSpec.root, context)}</View>
}
```
