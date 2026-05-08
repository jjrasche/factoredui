# factoredui

The rendering half of an autonomy loop: standardized declarative UI primitives
that an AI can observe, hypothesize about, and experiment with — because the
unit of observation is the same as the unit of change.

## The core insight

The standardized component is both the unit of observation AND the unit of
change. Every existing system has a translation layer between "what we
measured" and "what we can change." This eliminates that layer. That's what
closes the loop.

## The loop

```
Raw interaction data + errors
  -> Standardized factors (alarm, diagnostic, structural)
  -> LLM inference (hypothesis about why a factor is off)
  -> Component-level experiment (A/B test via standardized vocabulary)
  -> Verified change (factors improved or didn't)
  -> Governor decides to ship or not
```

factoredui is **the rendering half** of this loop. Specs are the change
vocabulary; the renderer makes them perceptible on whichever substrate is
running. The capture / factor / experiment / governance halves live in
[agent-platform](https://github.com/jjrasche/agent-platform), or — for the
TypeScript prototypes — as dormant code in `packages/core/` pending a Kotlin
port.

## Three-tier factor model

- **Alarm** — completion rate, drop-off, error rate. Something is wrong.
- **Diagnostic** — hesitation time, rage clicks, scroll reversals, dead clicks, retry patterns. Here's what the user experienced.
- **Structural** — Lighthouse scores, CLS, LCP, contrast, tap targets, accessibility. Here's what the page physically looks like.

These three tiers together give an LLM everything a human watching a session
replay would have, as structured data at scale. No replay needed.

## Component hierarchy

| Tier | What can change | Example |
|------|----------------|---------|
| Element | exists/hidden, text, variant, size | "Start talking" vs "Record narration" |
| Component | which elements, arrangement, spacing | Controls above photo vs below |
| Page | which components, layout, copy | Review with photo strip vs without |
| Flow | which pages, order, step count | 3-photo min vs 1-photo min |

Experiment at element/component level (isolates causation). Measure factors at
every level.

## Three audiences

1. **AI** — reads factor views to develop, debug, experiment.
2. **Individual users** — see their own interaction patterns.
3. **Community/governors** — aggregated anonymized factors enable democratic governance. AI surfaces facts, community decides, AI implements.

## Current scope (as of 2026-05)

```
packages/
├── kotlin-compose/   — the live renderer. Compose Multiplatform; targets
│                       Android, iOS (3 arches), JVM desktop, browser (wasmJs).
└── core/             — TypeScript. Holds spec-types.ts as the canonical schema
                        mirror. Capture / factor / experiment pipeline code is
                        present but dormant pending a Kotlin port.
```

Previously also shipped: `@factoredui/react`, `@factoredui/react-native`,
`@factoredui/adapter-supabase`. All deleted on 2026-04-24 in favour of the
single Compose Multiplatform renderer.

## Design decisions

- **One renderer surface across substrates** — Compose Multiplatform compiles
  the same primitives to Android, iOS, desktop JVM, and the browser. No
  per-platform translation layer.
- **Closed primitive palette** — 22 enum cases (containers, leaves,
  dense/semantic). Adding a primitive requires changing both the schema enum
  and the dispatch in `RenderNode.kt`. Low ceremony, high consistency.
- **Storage-agnostic** — the renderer never knows about a backend. Specs come
  in pre-fetched; bindings resolve against a `StateFlow<Map<String, Any?>>`
  the host wires.
- **Actions are host-resolved** — `ActionRef { action, params }` is dispatched
  through a host-supplied `ActionRegistry`. The renderer doesn't hit the
  network.
- **No session replay** — three-tier factors replace 95%+ of replay value.
- **No engagement metrics** — measure completion, patterns, flow. Not attention.
- **Platform as pipes** — capture and present, do not interpret.
- **No personalization** — factors determine best defaults, users configure explicitly.
- **Governance decides thresholds** — what to measure is what to value.
- **Factor engine survives UI death** — same factors apply to conversation interfaces.

## Novelty

The full loop does not exist anywhere (as of April 2026). No product, paper,
or open-source project combines: standardized component vocabulary as
observation+change unit, multi-tier factor engine, LLM hypothesis generation,
autonomous experimentation, democratic governance.

Closest prior art: Booking.com combinatorial bandits (component-level
bandits, no LLM), Karpathy autoresearch (LLM hill-climbing, no factor
engine), Datadog BitsEvolve (full autonomous loop, no UI vocabulary),
Kameleoon PBX (LLM hypotheses, DOM-based not factor-based),
Contentsquare Frustration Score (single composite, not multi-tier),
IBM MAPE-K (the framework, never applied to UI). No system today
combines a standardized component vocabulary, multi-tier factor engine,
LLM hypothesis generation, autonomous experimentation, and democratic
governance.
