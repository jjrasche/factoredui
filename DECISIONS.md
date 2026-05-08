# Architecture Decisions and Roadmap

This document tracks load-bearing architectural choices and the
state of the autonomy loop the project is heading toward. Update it
when a decision is made or its assumptions change.

## The 5-piece autonomy loop and where we are

The thesis (see `CONCEPT.md`) is a 5-stage loop:

| # | Stage | Built? |
|---|---|---|
| 1 | Standardized component vocabulary (observation = change unit) | **Done** — kotlin-compose, 22 primitives, all targets |
| 2 | Multi-tier factor engine (alarm / diagnostic / structural) | **Not built in Kotlin.** Was prototyped in TS in `packages/core/factors/`; that code is the reference for the port. |
| 3 | LLM hypothesis generation (read factors → propose component-level changes) | **Not built anywhere.** New work, slated for this repo (not agent-platform). |
| 4 | Component-level experimentation (variant assignment, exposure, governance) | **Not built in Kotlin.** TS prototype in `packages/core/experiment/`. Kotlin has an `Experiments` interface stub only. |
| 5 | Democratic governance (community-decided thresholds and shipping gates) | **Not built.** |

Today FactoredUI implements 1 of 5. Next port wave is **2 + 4 + the
observability slice of 3** (every interaction documented). Then 3 in
full, then 5.

## Decision: one Kotlin package, two JVM targets

**Status:** decided 2026-05-08.

The autonomy loop's frontend half (renderer + capture client) and
backend half (factor engine + experiments + LLM hypothesis runner) ship
as one published package. The frontend is only useful because it
enables collection; splitting them into two packages would suggest
they're independently usable, which they aren't.

**KMP target structure (target end state):**

```
jvm("desktop")        — Compose desktop renderer + frontend capture
jvm("server")         — backend pipeline: Postgres JDBC, factor engine,
                        experiment lifecycle, LLM hypothesis runner.
                        New target alongside `desktop`.
androidTarget         — frontend renderer + autocapture (Activity hooks)
iosX64/Arm64/Sim      — frontend renderer + autocapture (UIKit hooks)
wasmJs                — frontend renderer + DOM autocapture
commonMain            — schema, RenderContext, BindingResolver, capture
                        event types, network protocol for event flush
```

Each KMP target publishes its own artifact with a classifier; consumers
pick which classifier they pull. A backend service depends on
`factoredui-server-X.Y.Z.jar` (with Postgres deps); an Android app
depends on `factoredui-android-X.Y.Z.aar` (no Postgres deps).

The package directory `packages/kotlin-compose/` becomes a misnomer
once the server target lands. **Pending rename:**
`packages/kotlin-compose/` → `packages/kotlin/`. Defer until the server
target work starts so commits don't mix mechanical rename with code
changes.

## Decision: storage is Postgres-specific on the server target

**Status:** decided 2026-05-08.

The server target bundles a Postgres-specific implementation: schema
DDL, migrations, JDBC client, factor-engine SQL using JSONB / window
functions / materialized views. Consumers get a turnkey factor engine
they configure with a connection string; no `FactoredStore`
abstraction in the runtime path.

**Why:** the factor engine's value is in the SQL — alarm/diagnostic/
structural aggregations are written as views and queries that depend
on JSONB and window functions. Forcing the SQL through a least-common-
denominator storage abstraction would either gut the queries or fork
them per backend. Postgres is the one production database where every
needed feature is first-class.

The TS `FactoredStore` interface in `packages/core/store.ts` is **not**
the target shape for the Kotlin port. Treat it as a structural
reference for what operations the pipeline performs, not as an
abstraction to preserve.

## Decision: icons via Iconify CDN

**Status:** decided 2026-05-08, implemented 2026-05-08.

`RenderIcon` resolves `name: "lucide:settings"` →
`https://api.iconify.design/lucide/settings.svg` and renders the
fetched SVG via Coil. Default prefix is `lucide` if the spec author
doesn't supply one (`name: "settings"` → lucide:settings).

**Why:** zero bundle weight, ~150 icon sets behind one CDN, host
doesn't have to wire an icon library. Cost is one network round-trip
per unique icon on first paint (cacheable thereafter).

## Decision: lazy grid is opt-in, not default

**Status:** decided 2026-05-08, implemented 2026-05-08.

`grid` defaults to chunked-row rendering (composes every child up
front, works in any container). `props.lazy: true` switches to
`LazyVerticalGrid` (windowed rendering, but requires bounded height
context — runtime crash if dropped into an unbounded scrollview).

**Why:** lazy is the better choice for large item counts but breaks
specs that worked with the chunked version. Opt-in keeps existing
specs working and forces the spec author to acknowledge the height
constraint when they want windowing.

## Roadmap

### Next milestone: observability MVP (port wave 1)

Goal: every UI interaction documented end to end.

1. **Capture event types in commonMain** — port `packages/core/types.ts`
   `CaptureEvent`, session/timing fields, JSON serialization.
2. **Per-platform autocapture**
   - wasmJs: port `web-adapter.ts` (DOM click/scroll/error/navigation
     listeners) using browser EventListener APIs.
   - android: tap recording via Activity LifecycleCallbacks +
     PointerInputModifier on RenderNode.
   - ios: UIKit gesture recognizer hooks.
   - desktop: Compose `awaitPointerEventScope` on RenderNode root.
3. **Network flush** — batch + POST to a configured endpoint. Add the
   endpoint as a `RenderContext` field. Reuse the existing Ktor client.
4. **Server target ingest** — add `jvm("server")` target. Endpoint
   handler validates events, writes to a `factoredui_events` Postgres
   table (single JSONB payload column + indexed user_id, session_id,
   component_path, created_at).
5. **Smoke test** — host app fires manual events, server writes them,
   query confirms.

Success criterion: a user clicks a button in agent-platform's Compose
frontend and an event row lands in Postgres within 5 seconds.

### Port wave 2: factor engine

Port `packages/core/factors/` to the server target as Kotlin. Reuse
the SQL from the TS prototype (it stays Postgres-specific). Three-tier
factor framework:

- Alarm: completion rate, drop-off, error rate per component_path.
- Diagnostic: hesitation time, rage clicks, dead clicks, retry patterns.
- Structural: from periodic Lighthouse runs against rendered specs.

Materialize via Postgres views. Expose a query API.

### Port wave 3: experiments + LLM hypothesis

- Port experiment lifecycle: targeting rules, traffic allocation,
  exposure logging, governance verdicts.
- New: LLM hypothesis runner. Reads factor deltas, calls Claude (or
  similar) with the factor data + current spec, gets back a proposed
  spec mutation, queues it as an experiment variant.

### Then: 5 (governance)

Open question. Not solved by anyone in industry. Defer until 1–4 are
real.

## Decision: server target is embedded, not standalone

**Status:** decided 2026-05-08.

The `jvm("server")` artifact is a library, not a microservice. The
host's existing backend framework (Ktor / Spring / Vert.x / whatever)
imports it and mounts our handler functions on the host's own routes.
The host configures the Postgres connection pool; we expose
operations that take a `Connection` (or a connection-providing
function). We ship SQL migrations as a resource file; the host runs
them through whatever migration tool they already use (Flyway,
Liquibase, hand-rolled).

**Why:** consumers of factoredui are already running a backend. Adding
a separate microservice doubles ops surface (deployment, monitoring,
secrets, network policy) for no gain. As a library we plug in beside
their existing code, share their connection pool, and inherit their
auth/observability/error handling.

**What this means for the API shape:** the server target exposes
suspend functions (`ingestEvent`, `recomputeFactors`,
`assignVariant`) that take whatever transactional context the host
provides. We do not expose a Ktor `Application` or HTTP server.

## Open questions

- **Spec mutation safety.** When the LLM proposes a spec mutation, what
  prevents catastrophic outputs (broken layouts, removed CTAs)?
  BitsEvolve's answer was formal verification. Ours might be: every
  mutation runs as a shadow variant against a small traffic % first,
  with auto-rollback on factor regression. Defer until port wave 3.
- **Capture overhead budget.** Autocapture has to be cheap on every
  interaction. Set a perf budget (e.g. <0.5ms per event recorded,
  zero-alloc on the hot path) and enforce in benchmarks.
