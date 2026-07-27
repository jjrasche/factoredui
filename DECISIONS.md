# Architecture Decisions and Roadmap

This document tracks load-bearing architectural choices and the
state of the autonomy loop the project is heading toward. Update it
when a decision is made or its assumptions change.

## The 5-piece autonomy loop and where we are

The thesis (see `CONCEPT.md`) is a 5-stage loop:

| # | Stage | Built? |
|---|---|---|
| 1 | Standardized component vocabulary (observation = change unit) | **Done** — kotlin-compose, 22 primitives, all targets |
| 2 | Multi-tier factor engine (alarm / diagnostic / structural) | **Engine ported to Kotlin** (`kotlin-engine/factors/`: KMeans, FactorModel, FactorDashboardSpec, bit-exact parity tested). Not yet wired into a live proactive loop. |
| 3 | LLM hypothesis generation (read factors → propose component-level changes) | **Not built anywhere.** New work, slated for this repo (not agent-platform). |
| 4 | Component-level experimentation (variant assignment, exposure, governance) | **Ported to Kotlin** (`kotlin-engine/experiments/`: Bucketing, Governance, Lifecycle, Targeting + `kotlin-compose/experiments/Experiments`). Not yet wired into a live loop. |
| 5 | Democratic governance (community-decided thresholds and shipping gates) | **Not built.** |

The engine pieces (1, 2, 4) are built in Kotlin; what remains is the
LIVE LOOP — wiring 2+4 into proactive operation plus 3 (LLM hypothesis)
and 5 (governance). The TS reference (`packages/core`) that prototyped
2/4 was deleted 2026-06-25 after the port; Kotlin is the only source now.

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

### Port wave 2: factor engine — v1 SHIPPED 2026-05-29

Correction to the original plan: there was **no SQL to reuse**. The TS
`packages/core/factors/` modules were pure delegators over a
`FactoredStore` interface; the concrete Postgres SQL lived in
`adapter-supabase`, deleted 2026-04-24. So wave 2 = port the surviving
pure logic + write the factor SQL fresh.

Shipped (`packages/kotlin-server/.../factors/`, `0002_factors.sql`):

- **v1 factor taxonomy** — five factors, materialized as Postgres views
  over `factoredui_events ⨝ factoredui_sessions`:
  - `error_rate` (alarm) = errors / total events per component.
  - `rage_click_rate`, `dead_click_rate`, `scroll_reversal_rate`
    (diagnostic) = those event counts over clicks / scrolls.
  - `hesitation_time_p50_ms` (diagnostic) = p50 of impression →
    first-interaction latency per session, aggregated per user.
- **Query API** — `queryFactors(conn, user, component)` +
  `queryComponentFactors(conn, component)`, `Connection`-based like ingest.
- **`kMeans`** (Mulberry32, bit-exact to the TS) for behavioural clustering.
- Verified against real Postgres via testcontainers.

Deferred (no consumer / no data source yet):

- **Completion / drop-off** — needs a per-component funnel (which events
  mark start vs success). The completion that matters for the labeling
  case is session-level, which lives in agent-platform's `:session`
  domain, not a per-component factor. Add when a funnel-using consumer asks.
- **Structural tier** (Lighthouse/CLS/LCP) — separate offline ingest project.

### Port wave 3: experiments + LLM hypothesis

Pure decision logic SHIPPED 2026-05-29 (`packages/kotlin-server/.../experiments/`):
DJB2 traffic bucketing (bit-exact), targeting predicate engine, governance
verdict logic, `validateDefinition` — all pure, unit-tested. Remaining:

- **Experiment SQL + state** — tables for experiments/variants/assignments/
  exposures/thresholds/governance-log, and `Connection`-based store ops
  wiring the pure logic to Postgres. (But see the ownership boundary below —
  experiment STATE is agent-platform's, not factored-ui's.)
- **LLM hypothesis runner** — reads factor deltas, calls Claude with the
  factor data + current spec, gets back a proposed spec mutation, queues it
  as an experiment variant.

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

## Decision: factored-ui / agent-platform ownership boundary

**Status:** decided 2026-05-29, with agent-platform.

The Kotlin port sharpened a split that earlier framings had blurred. Three
layers, three owners:

1. **Pure experiment + factor logic** — factored-ui owns the code, in the
   **`kotlin-engine` KMP module** (`ai.factoredui.engine.{factors,experiments}`:
   Bucketing, Targeting, Governance, Lifecycle, KMeans, FactorDashboardSpec,
   the domain models). No Postgres, no Compose, no JVM-only APIs — it compiles
   to JVM / Android / iOS / wasmJs / native, so agent-platform's Android target
   imports the *same module*. **Single canonical implementation**, no
   reimplementation. (This is why it is a separate module and not part of
   kotlin-server, which is JVM-only: the boundary is only buildable if the
   pure logic is multiplatform.)
2. **Postgres factor engine + ingest** — factored-ui owns
   (`0001_init.sql`, `0002_factors.sql`, `Ingest.kt`, `Factors.kt`).
   Multi-tenant consumers (e.g. illuminant's server) use it directly. Personal
   agent-platform is SQLite, so it **reimplements the factor queries over
   SQLite using the v1 factor taxonomy above as the spec** — not the SQL.
3. **Experiment STATE** — who's exposed, current verdicts, the ratification
   trail — **agent-platform owns**, as rows on its own SQLite. factored-ui
   provides the decision functions; it does not store experiment state.

**Consequence for the SQL stage (wave 3):** the experiment tables/store in
factored-ui's server target are for multi-tenant Postgres consumers. Personal
agent-platform does not use them. So "Stage C" is lower priority than wave-2
factors were, and is gated on an actual multi-tenant consumer needing it.

## Open questions

- **Spec mutation safety.** When the LLM proposes a spec mutation, what
  prevents catastrophic outputs (broken layouts, removed CTAs)?
  BitsEvolve's answer was formal verification. Ours might be: every
  mutation runs as a shadow variant against a small traffic % first,
  with auto-rollback on factor regression. Defer until port wave 3.
- **Capture overhead budget.** Autocapture has to be cheap on every
  interaction. Set a perf budget (e.g. <0.5ms per event recorded,
  zero-alloc on the hot path) and enforce in benchmarks.

## Decision: the renderer is HTTP-zero (host owns all I/O)

**Status:** decided 2026-06-25 (Jim). Implementation = the scene3d
decomposition arc, in progress.

The renderer's contract is `state → pixels` + `interaction → action
dispatch`. It must not open a socket. Data arrives via `dataFlow`
(host fetched it); intents leave via `ActionRegistry` (host does the
POST). This is already enforced for the slider, the field `canvas`,
and the inline-`body_frame` / bare-body paths (that HTTP-freeness is
exactly why `renderSpecToPng` is a pure function).

**The violation:** `scene3d` predates the rule. It has **18 HTTP
call-sites** (`world_state_url` fetch, action POSTs, SSE stream, preview/
settle/drop/sit/prompt handlers) and forces `ktor.client.*` to be a
renderer dependency. `forcegraph` adds 7 more (slated for deletion
anyway). `capture/` posts telemetry (observability, movable host-side).

**Acceptance test (binary, unfakeable):** the `kotlin-compose` module
drops its `ktor.client.*` dependency. You cannot half-violate "no HTTP"
if the HTTP client isn't on the classpath. Sub-goals on the way:
- scene3d decomposes to a pure rasterizer leaf (`BodyFrame`/world-state
  → pixels), the seam every lane meets at (render consumes, runtime
  produces, il-verify's believability heads tap the same `BodyFrame`).
- drive (clip playback) / source (URL/SSE) / interaction (action POSTs)
  move host-side — clip-playback-now and the live policy+physics runtime
  become interchangeable **step drivers** feeding the same render (the
  render ⊥ drive cut). Live `{body}` binding (0.15.4) is that seam in
  miniature.
- chrome (Move/Pose/Settle/… buttons + prompt) composes in SDUI host-side
  (the play/pause thinning at 0.13.8 is the template).
- image loading (`coil.network.ktor3`) → host-provided image loader.

**Cut by concern, not by class** (il-embodiment): render / drive /
interact / source — not "Scene3dView split into 8 files." **Keep the
shadow-node emit inside the rasterizer** when it splits, or the headless
gate + BDD-visual loop die in the split. **Don't bake `SMPL24_PARENTS`
into the leaf** (il-verify) — take skeleton connectivity from the
`BodyFrame`; the leaf is topology-neutral.

**The gate is the forcing function** (il-verify): a correctness gate that
renders the pure leaf IS the executable spec of what scene3d decomposes
to. Decompose against a green gate — re-entangle I/O into the render path
and a golden reds. That's how this doesn't grow back a third time (the
`fieldgraph` anti-pattern, deleted, regrowing inside scene3d).

## Decision: a stored spec decodes by DECLARED keys, never by inference

**Status:** decided + shipped 2026-07-26 (0.17.0, `af7cdb8`). Raised by
ap-genesis, which adopts `kotlin-compose-schema` as a row payload in an
append-only log.

`SpecValueSerializer` decided a JSON object was a nested `SpecNode` by
testing for `id` + `type` keys. Tolerable for a wire format — you control
both ends and a misparse shows up immediately. Unacceptable for a payload
that is **stored**: the ambiguity becomes permanent the first time a row
lands, and changing the rule later changes what old rows mean.

The failure was sharper than a misparse. That decode ran on a *default*
strict `Json`, so an ordinary props object carrying `id` + `type` plus any
third key **threw**, as did any `type` that wasn't a `SpecNodeType` name.
`{"id":"u1","type":"admin"}` — ordinary data — was a crash. The silent case
was narrower: keys exactly `id` + `type` with `type` colliding with a
primitive name.

**Decision:** nodehood is declared by prop key via `NODE_BEARING_PROP_KEYS`,
consulted by `SpecPropsSerializer`. `SpecValueSerializer` can no longer
produce a `NodeValue` at all — a value serializer cannot see its own key,
so it can only guess. Nested nodes decode with the *caller's* `Json`, so a
nested node never parses under stricter rules than the spec containing it.

**Why by-position and not a marker key:** `itemTemplate` is the only
node-bearing prop in the schema, so the declared rule requires zero
migration and no producer changes. A marker key would have invalidated
every existing spec. ap-genesis ratified this: genesis never decodes a spec
in a build that doesn't link the library — an unknown row shape is deferred
wholesale, never parsed — so the case a visible marker would protect
against cannot arise.

`RENDERER_VERSION` stays in the schema module, against a request to move it
to the renderer: `kotlin-engine` is a Compose-free spec *producer* that
stamps `renderer_min`, and moving the constant would force Compose onto a
module whose purpose is not having it. It describes the spec envelope, not
the renderer's build identity.

## Decision: published artifacts are OS-neutral; the consumer contributes the platform

**Status:** decided + shipped 2026-07-26 (0.17.0, `af7cdb8`).

`desktopMain` used `compose.desktop.currentOs`, which resolves at *our*
configuration time and hard-pins the publisher's OS into the published pom.
A Linux CI publish emitted `desktop-jvm-linux-x64`, so every Windows and
macOS consumer died at skiko class-init hunting a native that would never
be there. Reproduced in the mirror: generating the pom on Windows stamped
`desktop-jvm-windows-x64`.

**Decision:** `compose.desktop.common` in `desktopMain`; the pom carries
plain `desktop-jvm`. `currentOs` is confined to `desktopTest` and to a
non-published configuration feeding `renderSpecCli`, so local runs and the
render gate still get real natives.

**Consequence the consumer owns:** desktop consumers must declare
`compose.desktop.currentOs` themselves, and cross-deploy builds name the
variant explicitly. Documented in the renderer README's consumer contract.
Missing skiko after upgrading is this contract, not a regression.

Capture telemetry (`CaptureEvent`, `Session`) moved to `:kotlin-compose-capture`
in the same release so a consumer wanting only the spec grammar stops
inheriting a capture concept transitively. Package names unchanged.

**Release mechanics learned the hard way:** the publish workflow names each
module explicitly, so a NEW published module is invisible to it. Adding
`:kotlin-compose-capture` without editing the workflow would have shipped a
`kotlin-compose` pom depending on a 404 coordinate, on an immutable version.
Any new published module = edit the workflow AND rehearse
`publishAllPublicationsToLocalBuildRepoRepository` into `build/maven-repo`
before tagging.

## Evidence for HTTP-zero: an Android consumer could not build at all

**Status:** evidence logged 2026-07-27, reinforcing the HTTP-zero decision
above. Fix shipped 0.17.1 (`111e5b9`) is a *stopgap*, not the resolution.

ap-genesis built the first real Android consumer and found `kotlin-compose`
**undexable on minSdk < 34**. ktor 3.2.0 ships
`io/ktor/client/plugins/Messages.class` carrying a nested class whose
`SimpleName` contains literal spaces; D8 permits that only at DEX version
040. Not a crash — the APK could not be assembled.

Verified rather than assumed: pulled `ktor-client-core-jvm` 3.2.0 through
3.5.1 and grepped the class bytes. 3.2.0 is the only affected release;
JetBrains fixed it in the next patch (KTOR-8583 / KTOR-8617). We were
pinned exactly on the bad one. 0.17.1 moves to 3.2.4.

**Why this belongs here:** a consumer rendering `column` / `text` /
`textinput` / `button` — no images, no live lists, no capture — still
inherited an HTTP client stack and, through it, an unbuildable APK. That is
precisely the cost the HTTP-zero decision predicts, arriving from a
direction nobody anticipated: not a runtime violation, a *build* one.

The consumer's instinct was `exclude(group = "io.ktor")`. That is a trap
worth recording: ktor is imported **directly** in `capture/HttpEventTransport`,
`net/SseSubscription` and `scene3d/RenderScene3d`, not only transitively via
coil, so the exclude silently disarms live lists, capture and scene3d. The
loud build failure becomes a silent `NoClassDefFoundError` months later.

It also means the frequently-proposed fix — "split images into their own
module" — **cannot work**. Images are not the only door ktor comes through.
The acceptance test in the HTTP-zero decision is still the right one and is
still binary: the module drops `ktor.client.*` entirely. Dependency
inversion (core declares the seams; ktor/coil implementations become opt-in)
is the shape that reaches it. `compileOnly` is rejected: class-presence
probing does not port to wasm/native, which we also ship.

**Sequencing:** after agent-platform's Compose 1.7.3 → 1.10.3 + Kotlin bump
lands. Stacking a second breaking change on an unfinished one stalls both,
and no consumer is blocked in the meantime — 0.17.1 unbreaks the build
without any consumer-side workaround.

## Decision: the Android unit-test variant does not host Compose UI tests

**Status:** decided 2026-07-27 (`build.gradle.kts`, `.github/workflows/`).

`./gradlew build` was red on every branch, and had been long enough that the
redness was treated as ambient. Cause: the Compose UI tests live in
`commonTest`, and Android's *unit*-test variant is a bare JVM with no
Android runtime, so `runComposeUiTest` NPEs — 21 failures that say nothing
about the code. Confirmed pre-existing by running a clean worktree at an
older commit.

**Decision:** `testDebugUnitTest` / `testReleaseUnitTest` are disabled for
`:kotlin-compose`. There are no android-specific test sources, so this costs
zero coverage; the same tests run for real on `desktopTest`. Re-enable with
a filter if `androidUnitTest` ever gains its own tests.

**The worse half, found while fixing it:** CI's renderer step carried a
`--tests` filter added when `RenderControlsTest` had NPEs under
`runComposeUiTest`. Those were fixed; the filter outlived them and was
silently gating CI to **7 of 15 test classes** — every Compose UI render
test went unrun on every release. Widened to the whole suite after verifying
118 tests / 35 classes / 0 failures under `--rerun-tasks`.

Both holes share a shape: a workaround for a real, temporary problem that
was never removed when the problem went away, and which nothing failed loudly
enough to surface. A narrowing filter needs a linked issue or it becomes
permanent by default.
