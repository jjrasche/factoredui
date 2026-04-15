# @factoredui/react

React bindings for FactoredUI — provider, hooks, path context, and SDUI renderer.

Part of the [FactoredUI](https://github.com/jimjrasche/factored-ui) monorepo.

## Install

```bash
npm install @factoredui/core @factoredui/react @supabase/supabase-js
```

## Setup

Wrap your app in `<Provider>`. This initializes capture automatically.

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

Provider props:
- `supabase` — Supabase client (required)
- `adapter` — custom `CaptureAdapter` (defaults to web adapter)
- `platform` — `"web" | "ios" | "android"` (defaults to `"web"`)
- `flushIntervalMs` — event batch interval (default 5000)
- `flushBatchSize` — max events per batch (default 50)
- `sessionTimeoutMs` — session inactivity timeout (default 30 min)

## Path Context

Structure your component tree with path providers. Events are automatically tagged with hierarchical paths.

```tsx
import { Flow, Page, Component, Element } from '@factoredui/react'

<Flow name="onboarding">
  <Page name="review">
    <Component name="photo-grid">
      <Element name="upload-button">
        <button>Upload</button>
      </Element>
    </Component>
  </Page>
</Flow>
// Path: onboarding/review/photo-grid/upload-button
```

Read the current path anywhere:

```tsx
import { useComponentPath } from '@factoredui/react'

const path = useComponentPath()
// → "onboarding/review/photo-grid"
```

## Hooks

All hooks use the Supabase client from `<Provider>` context.

### useFlag

```tsx
import { useFlag } from '@factoredui/react'

function CheckoutPage() {
  const { variant, loading, error } = useFlag('checkout-redesign')

  if (loading) return <Spinner />
  if (error) return <Fallback />

  return variant === 'treatment' ? <NewCheckout /> : <OldCheckout />
}
```

### useFactors / useComponentFactors

```tsx
const { factors, loading } = useFactors()                       // current user's factors
const { factors, loading } = useComponentFactors('checkout/cta') // aggregated for a component
```

### useGovernanceLog

```tsx
const { entries, loading } = useGovernanceLog(experimentId)
// Realtime — updates as new governance verdicts arrive
```

### useExperimentDashboard

```tsx
const { experiments, loading } = useExperimentDashboard({ status: 'running' })
```

### useExperimentResults

```tsx
const { results, loading } = useExperimentResults(experimentId, ['completion_rate', 'error_rate'])
```

### usePlatform

```tsx
const platform = usePlatform() // "web" | "ios" | "android"
```

## SDUI Renderer

Render server-driven UI specs:

```tsx
import { renderSpec } from '@factoredui/react'
import { loadSpec, createSpecStorage, devSignatureVerifier } from '@factoredui/core'

const spec = await loadSpec(supabase, 'home-hero', storage, devSignatureVerifier)

function HeroSection() {
  return <>{renderSpec(spec.tree, componentRegistry, actionRegistry, dataSources)}</>
}
```

### useSourceData

Resolve async data sources for spec bindings:

```tsx
import { useSourceData } from '@factoredui/react'

const { data, loading, error } = useSourceData(spec.dataSources, supabase)
```

## Peer Dependencies

- `@factoredui/core ^0.2.0`
- `@supabase/supabase-js ^2.0.0`
- `react ^18.0.0 || ^19.0.0`

## License

MIT
