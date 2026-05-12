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

## Decision: two gradle subprojects in one repo

**Status:** decided 2026-05-08, implemented 2026-05-08.

Original intent (one KMP module with `jvm("desktop")` + `jvm("server")`)
hit a hard constraint: Kotlin Multiplatform doesn't allow two `jvm()`
targets in the same module. The replacement: two gradle subprojects
under `packages/`, sharing one version catalog and one wrapper.

```
factored-ui/                             ← multi-project gradle root
├── settings.gradle.kts                  ← includes all subprojects
├── gradle/libs.versions.toml            ← shared version catalog
├── gradlew, gradle/wrapper/             ← shared wrapper
└── packages/
    ├── kotlin-compose-schema/           ← pure-Kotlin wire types (added 0.9.0)
    │   ├── jvm                          ← engine consumers
    │   ├── androidTarget                ← device-side, no Compose
    │   ├── iosX64/Arm64/Sim
    │   ├── linuxX64                     ← Kotlin/Native server
    │   └── wasmJs
    ├── kotlin-compose/                  ← KMP renderer + frontend capture
    │   ├── androidTarget                ← Compose UI + Android capture
    │   ├── iosX64/Arm64/Sim             ← Compose UI + iOS capture
    │   ├── jvm("desktop")               ← Compose desktop + capture
    │   └── wasmJs                       ← Compose web + DOM capture
    │   (api-depends on kotlin-compose-schema)
    └── kotlin-server/                   ← JVM-only Kotlin library
        └── (Postgres JDBC, ingest, factor engine, experiments,
             LLM hypothesis runner). Depends on kotlin-compose-schema
             for shared wire types (Spec, CaptureEvent, Session).
```

Three published artifacts (as of 0.9.0): `ai.factoredui:kotlin-compose-schema`,
`ai.factoredui:kotlin-compose`, and `ai.factoredui:kotlin-server`. Conceptually one package — the user-
facing experience is "depend on factoredui in your renderer, depend
on factoredui in your backend, both ship from the same release" —
but the gradle topology has to be two-project to satisfy KMP.

Why this is fine: nothing about the user-facing model changes. A
consumer who only renders specs depends on kotlin-compose. A consumer
who only ingests events depends on kotlin-server (which since 0.9.0
transitively pulls in kotlin-compose-schema only — no Compose-MP). A
consumer that just needs the wire types (e.g. an engine that emits or
validates specs) depends on kotlin-compose-schema directly.

**Future rename**: `packages/kotlin-compose/` → `packages/kotlin/`
once the rename can land cleanly. Now that the server lives in its
own subproject, the `kotlin-compose` name is at least self-consistent
again (it really is just the Compose renderer).

## Decision: split kotlin-compose into schema + renderer

**Status:** decided 2026-05-12, implemented 2026-05-12 in 0.9.0.

The 0.8.0 single artifact `ai.factoredui:kotlin-compose` bundled the
SDUI schema (`Spec`, `SpecNode`, `SpecValue`, …), the capture wire-
format types (`CaptureEvent`, `Session`), and the Compose Multiplatform
renderer in one publication. 0.9.0 splits this into:

- `ai.factoredui:kotlin-compose-schema` — pure Kotlin, no Compose-MP,
  no Ktor, no androidx, no skiko. KMP targets `jvm`, `android`, iOS
  (3 arches), `wasmJs`, `linuxX64`. Contains all `@Serializable` wire-
  format types: SDUI schema, capture event types, the `Session` data
  class. Pure data + serializers + binding resolver + typed prop
  accessors.
- `ai.factoredui:kotlin-compose` — Compose-MP renderer. Same coordinates
  as before; depends on `kotlin-compose-schema` via Gradle `api(...)`
  so existing consumers keep getting the schema types transitively.
  Holds the runtime: `RenderNode`, `RenderContext`, `SessionManager`,
  `CaptureClient`, `HttpEventTransport`, `CaptureObservability`,
  observability, experiments, adapter, forcegraph, testing.

**Why:** server-side engine consumers (agent-platform's factor / LLM
hypothesis modules) need `SpecNode` to emit and validate specs without
dragging Compose-MP onto their classpath. Compose-MP ABI bumps roughly
every three months — coupling unrelated engine work to that cadence
was forcing churn we don't want.

The split rule is "wire format depends on nothing UI-related." Anything
that exists to serialize over HTTP / JDBC moves to schema. Anything
that holds runtime state, schedules work, opens sockets, or composes
UI stays in the renderer. `Session` (data class) → schema. The
`SessionManager` that owns the live session and rotates on inactivity
→ renderer.

**SemVer handling:** 0.8.0 stays published as the frozen bundled
snapshot. 0.9.0 is the split. Package names are unchanged, so no
import changes for existing consumers; only artifact-coordinate
changes if a consumer wants to depend on schema directly. See
`CHANGELOG.md` for migration steps.

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

### Port wave 1: observability MVP — DONE 2026-05-08

Goal: every UI interaction documented end to end.

- [x] **Capture event types in commonMain** —
      `packages/kotlin-compose/.../capture/CaptureEvent.kt`,
      `Session.kt`, `EventTransport.kt`, `EventWriter.kt`,
      `CaptureClient.kt`, `CaptureObservability.kt`. Pure-Kotlin, all
      KMP targets.
- [x] **HTTP transport** — `HttpEventTransport` POSTs JSON batches
      via Ktor. Idempotent on event id (UUID generated client-side).
- [x] **Renderer-driven autocapture** — `CaptureObservability`
      bridges the existing `Observability` hook into capture. Every
      button/chip/toggle/slider/select tap becomes a CLICK event
      automatically. Works on every platform without per-platform code.
- [x] **wasmJs page-level autocapture** — `WebAutoCapture` hooks
      `window error` and `document visibilitychange`. Visibility=hidden
      triggers an immediate flush.
- [x] **Server subproject** — `packages/kotlin-server/`. Postgres-backed
      ingest (`ingestEvents(connection, session, events)`) +
      migrations. Idempotent inserts on session id and event id.

What still needs attention before this is considered production-ready
(deferred from MVP):

- Per-platform autocapture for android / iOS / desktop. Renderer-
  driven capture covers the action-bearing primitives, which is most
  of what matters; raw page-level signals (scroll, focus on non-
  renderer DOM, etc.) are wasmJs-only today.
- Rage-click + dead-click + scroll-reversal detection (algorithms in
  TS reference; needs wasmJs port).
- Real-Postgres integration test for ingest. H2 PG-mode is too
  divergent on TIMESTAMPTZ + JSONB; needs testcontainers or a real-DB
  test in agent-platform.
- Beacon-based final flush on `beforeunload` (currently relies on
  visibility=hidden flush).

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
