# @factoredui/core

Capture user interactions, compute behavioral factors, run experiments, and render server-driven UI. Pure TypeScript — no framework dependencies.

Part of the [FactoredUI](https://github.com/jjrasche/factoredui) monorepo.

## Install

```bash
npm install @factoredui/core @supabase/supabase-js
```

## Setup

```bash
npx factoredui init
```

Copies 27 SQL migrations + the clustering edge function into your Supabase project, then prints the remaining steps (extensions, PostgREST config, deploy).

## Usage

### Capture

```ts
import { initCapture } from '@factoredui/core'
import { createClient } from '@supabase/supabase-js'

const supabase = createClient(url, anonKey, {
  db: { schema: 'factoredui' }
})

const capture = initCapture({ supabase })

// Track events manually (auto-capture handles clicks, scrolls, etc.)
capture.trackNavigation('checkout/payment', 'mount')
capture.trackImpression('checkout/payment/promo-banner')

// Flush and stop
await capture.flushEvents()
capture.stopCapture()
```

### Experiments

```ts
import { evaluateFlag, createExperiment, startExperiment } from '@factoredui/core'

// Assign user to a variant
const assignment = await evaluateFlag(supabase, 'checkout-redesign', 'web', deviceMetadata)
// → { experiment_id, variant_key, config }

// Create and start an experiment
const exp = await createExperiment(supabase, {
  name: 'checkout-redesign',
  component_path: 'checkout/payment',
  variants: [
    { key: 'control', weight: 50 },
    { key: 'treatment', weight: 50 },
  ],
})
await startExperiment(supabase, exp.id)
```

### Factors

```ts
import { queryFactors, queryComponentFactors } from '@factoredui/core'

// User's factors
const factors = await queryFactors(supabase, userId)

// Aggregated factors for a component
const agg = await queryComponentFactors(supabase, 'checkout/payment')
```

### SDUI

```ts
import {
  loadSpec,
  validateSpec,
  createSpecStorage,
  devSignatureVerifier,
  createEd25519Verifier,
  generateEd25519Keypair,
  createEd25519Signer,
} from '@factoredui/core'

// Load a signed spec from Supabase
const storage = createSpecStorage()
const spec = await loadSpec(supabase, 'home-hero', storage, devSignatureVerifier)

// In production, verify signatures
const { publicKey, privateKey } = await generateEd25519Keypair()
const signer = createEd25519Signer(privateKey)
const verifier = createEd25519Verifier(publicKey)
```

## API

### Capture
- `initCapture(config)` — start the capture pipeline, returns `CaptureHandle`
- `createWebAdapter()` — DOM-based capture adapter
- `resolveComponentPath(parts)` — build a `/`-separated path

### Factors
- `queryFactors(supabase, userId)` — user's computed factors
- `queryComponentFactors(supabase, componentPath)` — aggregated component factors
- `queryFactorHistory(supabase, userId, factorName, days)` — historical snapshots
- `queryUserCluster(supabase, userId)` — user's cluster assignment

### Experiments
- `evaluateFlag(supabase, name, platform, deviceMetadata)` — get variant assignment
- `createExperiment(supabase, definition)` — create an experiment
- `startExperiment(supabase, id)` — start an experiment
- `evaluateTargeting(rules, factors, metadata)` — check targeting rules
- `evaluateExperimentThresholds(supabase, id)` — governance check
- `concludeExperiment(supabase, id, winner)` — end an experiment

### SDUI
- `loadSpec(supabase, key, storage, verifier)` — load and verify a spec
- `validateSpec(spec)` — validate spec structure
- `resolveBinding(ref, data)` — resolve a data binding reference
- `dispatchAction(action, handlers)` — dispatch a spec action

### Types
- `Config`, `CaptureHandle`, `CaptureEvent`, `CaptureAdapter`
- `Factor`, `FactorTier`, `FactorSnapshot`, `FactorDelta`
- `ExperimentAssignment`, `ExperimentDefinition`, `VariantDefinition`
- `Spec`, `SpecNode`, `SignedSpec`, `ComponentRegistry`, `ActionRegistry`
- `Platform` (`"web" | "ios" | "android"`)

## Peer Dependencies

- `@supabase/supabase-js ^2.0.0`

## License

MIT
