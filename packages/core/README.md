# @factoredui/core

The TypeScript half of [FactoredUI](https://github.com/jjrasche/factoredui).

## What this package is

Two things, with very different liveness:

1. **`spec-types.ts` — the canonical SDUI schema mirror.** Single source of
   truth for the spec format that the Kotlin renderer also implements (see
   `packages/kotlin-compose/src/commonMain/.../schema/SpecNode.kt`). If the
   Kotlin enum and this TS union ever diverge, this file is the reference.

2. **Capture / factors / experiments / SDUI pipeline — dormant.** Pure-TS
   implementations of the agent-platform autonomy loop (interaction capture,
   factor computation, experiment lifecycle, signed-spec loading). These
   built originally against a Supabase-backed store; today they target a
   storage-agnostic `FactoredStore` interface and are pending a Kotlin port.
   Not actively consumed by any current FactoredUI deployment.

If you only need the schema, you only need the types — none of the runtime
pipeline executes unless you call into it.

## Install

```bash
npm install @factoredui/core
```

No runtime dependencies; pure TypeScript.

## Schema-only usage

```ts
import type { Spec, SpecNode, SpecNodeType, SignedSpec } from "@factoredui/core";

const spec: Spec = {
  spec_version: 1,
  renderer_min: 1,
  root: { id: "hello", type: "text", props: { value: { type: "string", value: "hi" } } },
};
```

The Kotlin renderer will accept this JSON over the wire and produce identical
UI on Android, iOS, desktop, and the browser.

## Pipeline usage (storage-agnostic)

Every pipeline function takes a `FactoredStore` — an interface the caller
implements over whatever persistence they use (Supabase, SQLite, Postgres,
in-memory). The `FactoredStore` contract lives in `src/store.ts`.

```ts
import { initCapture, evaluateFlag, queryFactors, loadSpec } from "@factoredui/core";
import type { FactoredStore } from "@factoredui/core";

const store: FactoredStore = makeMyStore();

// Capture
const capture = initCapture({ store, platform: "web" });
capture.trackNavigation("checkout/payment", "mount");
await capture.flushEvents();

// Experiments
const assignment = await evaluateFlag(store, "checkout-redesign", "web");
// → { experiment_id, variant_key, config } | null

// Factors
const factors = await queryFactors(store, userId, "checkout/payment");

// Signed specs
const loaded = await loadSpec(store, "web", baselineSpec, storage, verifier);
```

## API surface

### Schema (the live, canonical part)
- Types: `Spec`, `SpecNode`, `SpecNodeType`, `SpecValue`, `ActionRef`, `SignedSpec`
- Per-primitive prop types: `LayoutProps`, `TextProps`, `ButtonProps`, `TextInputProps`, `ImageProps`, `IconProps`, `CardProps`, `TabsProps`, `GridProps`, `SelectProps`, `ChipProps`, `ModalProps`, `ScrollViewProps`, `ToggleProps`, `SliderProps`, `DividerProps`, `SpacerProps`, `ListProps`
- `RENDERER_VERSION` constant
- `validateSpec(spec)` — structural validation
- `resolveBinding(ref, data)`, `resolveTextWithBindings(...)`, `resolveProps(...)`, `isBindingRef(ref)`
- Spec signing: `createEd25519Verifier`, `createEd25519Signer`, `generateEd25519Keypair`, `devSignatureVerifier`
- `loadSpec(store, platform, baseline, storage, verifier)`
- `dispatchAction(action, handlers)`

### Pipeline (dormant, pending Kotlin port)
- Capture: `initCapture(config)`, `createEventWriter`, `createSessionManager`, `createWebAdapter`, `resolveComponentPath`
- Factors: `queryFactors(store, userId, componentPath)`, `queryComponentFactors(store, componentPath)`, `queryFactorHistory(...)`, `kMeans(...)`, `queryUserCluster(...)`
- Experiments: `evaluateFlag(store, name, platform?, deviceMetadata?)`, `createExperiment(store, definition)`, `startExperiment(store, id)`, `evaluateTargeting(...)`, `evaluateExperimentThresholds(...)`, `concludeExperiment(...)`, `runGovernanceCheck(...)`
- Storage helpers: `createSpecStorage()`, `createDataSourceCache()`

See `src/index.ts` for the full export list.

## Stability

- **Schema types: stable.** They mirror the Kotlin renderer; treat changes as
  breaking on both sides simultaneously.
- **Pipeline functions: dormant.** Internal API; no SemVer guarantee while
  the Kotlin port is pending.

## License

MIT
