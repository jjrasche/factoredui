# factoredui

Autonomous app optimization via standardized components. Three npm packages (`@factoredui/core`, `@factoredui/react`, `@factoredui/react-native`) + Supabase migrations.

## The core insight

The standardized component is both the unit of observation AND the unit of change. Every existing system has a translation layer between "what we measured" and "what we can change." This eliminates that layer. That's what closes the loop.

## The loop

```
Raw interaction data + errors
  -> Standardized factors (alarm, diagnostic, structural)
  -> LLM inference (hypothesis about why a factor is off)
  -> Component-level experiment (A/B test via standardized vocabulary)
  -> Verified change (factors improved or didn't)
  -> Governor decides to ship or not
```

## Three-tier factor model

- **Alarm** -- completion rate, drop-off, error rate. Something is wrong.
- **Diagnostic** -- hesitation time, rage clicks, scroll reversals, dead clicks, retry patterns. Here's what the user experienced.
- **Structural** -- Lighthouse scores, CLS, LCP, contrast, tap targets, accessibility. Here's what the page physically looks like.

These three tiers together give an LLM everything a human watching a session replay would have, as structured data at scale. No replay needed.

## Component hierarchy

| Tier | What can change | Example |
|------|----------------|---------|
| Element | exists/hidden, text, variant, size | "Start talking" vs "Record narration" |
| Component | which elements, arrangement, spacing | Controls above photo vs below |
| Page | which components, layout, copy | Review with photo strip vs without |
| Flow | which pages, order, step count | 3-photo min vs 1-photo min |

Experiment at element/component level (isolates causation). Measure factors at every level.

## Three audiences

1. **AI** -- reads factor views to develop, debug, experiment. Queries via Supabase MCP.
2. **Individual users** -- see their own interaction patterns (RLS-secured).
3. **Community/governors** -- aggregated anonymized factors enable democratic governance. AI surfaces facts, community decides, AI implements.

## Architecture

```
packages/
├── core/             -- capture pipeline, factors, experiments, SDUI engine, types, CLI, migrations
├── react/            -- AuxiProvider, hooks, path context, SDUI renderer, useSourceData
└── react-native/     -- 20 themed RN component primitives (createComponentRegistry)
```

Cross-platform: TypeScript for web. Dart for Flutter. Swift/Kotlin for native. All write same event shape to same Supabase tables. Factor engine is SQL.

## Design decisions

- No session replay -- three-tier factors replace 95%+ of replay value
- Libraries not services -- npm package, not containers
- Separate schema, same Supabase -- `auxi.*` apart from app tables
- AI queries materialized views, not raw event tables
- No engagement metrics -- measure completion, patterns, flow. Not attention.
- Platform as pipes -- capture and present, do not interpret
- No personalization -- factors determine best defaults, users configure explicitly
- Governance decides thresholds -- what to measure is what to value
- Factor engine survives UI death -- same factors apply to conversation interfaces

## Novelty

The full loop does not exist anywhere (as of April 2026). No product, paper, or open-source project combines: standardized component vocabulary as observation+change unit, multi-tier factor engine, LLM hypothesis generation, autonomous experimentation, democratic governance.

Closest prior art: Booking.com combinatorial bandits, Karpathy autoresearch, Datadog BitsEvolve, Kameleoon PBX, Contentsquare Frustration Score, IBM MAPE-K.

See RESEARCH.md for full landscape analysis.
