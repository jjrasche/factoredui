# The Stage — illuminant showrunner UX, built on factored-ui SDUI

The Stage is the unified showrunner surface for illuminant, built entirely as factored-ui
spec + bindings (no parallel React app — the Stage is the on-ramp to agent-platform via the
shared CaptureEvent identity). This doc is the load-bearing architecture; it supersedes the
`showrunner_ux_and_authoring_loop` memory (code-as-memory, 2026-06-06).

## The UX framework (read first before any UI work)

Three keepers:
1. **Character creation is an AUDITION** — an appearance ladder + an audition ladder; a shared
   Laban latent drives both how they look and how they move.
2. **Arc is a LISTENER, not a generator** — a thought partner; per-level LLM summaries are the
   audit surface; cards are a read-only spatial view (structural change is *spoken*, not dragged).
   (il-arc's lane; realized in `illuminant/pipeline/arc/`.)
3. **Renderer = review-and-triage** — diagnose → route the fault upstream; blind co-verification.

**Decision spine:** floor/ceiling per stage; three anti-correlated gradients (competence /
caring / cost) → automate the middle, gate render at the wallet; gates are self-pruning locks
plus a user lock/iffy affordance; the UI sheds inputs as a character matures; omnibox everywhere;
the verifier is the nervous system.

## The interactive authoring loop (the unifying frame)

`type/speak → see → record → render → iterate`, across four stages: **Creator → Arc → Composer
→ Renderer.** Five generative edges, five deterministic middles (generation at the edges,
closed-form in the middle). Recording is the SEAM that turns interactive *authoring* into render
*output* — without it the interactive layer is a sketchpad and the render layer eats YAML; with
it, the showrunner's session is the source of truth. The omnibox loop: one spoken sentence →
a small LLM (Groq llama-3.1-8b) translates to one ActionRegistry call / a state diff →
deterministic dispatch → live re-skin.

## The converged surface

ONE surface, story-spined, with **object-typed focus contexts** and four cross-cutting layers
(omnibox / lock-iffy / verifier-flags / CaptureEvent). illuminant's Stage and agent-platform's
review feed are ONE factored-ui pattern. The 21 spec primitives live in `packages/kotlin-compose`
(the shared renderer); the Stage composes them — building a screen is composing specs, not adding
node types.

### Contexts (nav rail), as built

`packages/kotlin-compose-playground/.../playground/StageApp.kt` + `resources/specs/*.json`:

- **Story** — the story spine home (`specs/story-spine.json`).
- **Character** — the audition surface. A **dense single-view dashboard** (no scroll, no
  collapsing): a production-ladder spine on top (`✓ Speech / ✓ Headshot / ○ Full body / ○ 3D /
  ○ Splat`) with one primary CTA for the next rung; below it a 3-column at-glance dashboard —
  Identity (the rendered headshot hero) | Look (appearance_description + the full DerivedAppearance
  grid) | Signature (nearest Laban effort + the 8 Laban/dynamics dials). Speak-to-shape: the
  omnibox is a continuous perception stream (flush on a ~10s cadence), each flush updates the
  signature + the appearance attributes + a live render_prompt + an optional ask-back question.
- **Composer** — the scene composer; embeds the `scene3d` viewport that self-subscribes to
  `/world/stream` and LBS-re-skins per pose frame (the "plop" / play-reaction animates the body live).
- **Agents** — the build-loop monitor (`specs/agents.json`, scaffold): cross-bro roster +
  per-bro output stream + grade affordance + the build-loop domain-table catalog. Binds to
  agent-platform's served `/brothers/*/spec` when its output-capture substrate ships.
- **Review** — render-triage (coming; render hosts its own `/spec` on :8775).

## Binding model + server contracts

Each backend lane is an SDUI/data host on its own port; a Stage context loads a spec + binds:

- **Composer** → `fu-scene3d` / `simulator` on **:8765** (`/director/prompt`, `/world/stream`,
  `/world/state`, `/action`).
- **Character** → `il-character` on **:8770** (`GET /character`, `GET /character/<id>`,
  `POST /character` create, `POST /character/<id>/prompt` speak→state, `POST /character/<id>/
  reference-images` multipart upload). The read model carries `personality`, `appearance`
  (DerivedAppearance), `visual.reference_images`, `rendered.headshot_url`, `render_prompt`.
- **Render** → `render` on **:8775** (`POST /render {character_id|render_prompt, rung}` →
  poll `GET /render/status/<id>` → fetchable URL; `rung ∈ headshot|full_body|smpl|splat`; the
  `character_id` path does identity Qwen-edit off uploaded refs. render also now hosts `/spec` +
  `/world/stream` for its own triage surface).
- **Agent-platform / plan substrate** → `ap`. The SDUI↔plan contract (locked): a sibling payload
  `{spec, target, bindings, plan_seed}`; spec stays pure; CaptureEvent is the wire (instant UI /
  training row / authoritative PlanAdjusted); the binding holds at the *intent* layer (direct
  manipulation is a separate record-loop); `plan_path` is a JSON Pointer; the store is a flat
  warehouse with scoped writes. ap *serves* specs (`/brothers/spec`, `/review/spec`) — the
  renderer renders the server-served spec directly; `target.kind` discriminates
  (`character` / `render_task` / `brother` / `brother_outputs` / `llm_output` / …).

**Server-driven convergence:** a host returns `{spec, target, bindings, plan_seed}` and the Stage
renders it; a generic context that loads a spec URL + subscribes to its `/world/stream` + applies
binding deltas would make composer, character, render, and the brothers monitor uniform
server-driven contexts. (scene3d already self-subscribes; the generic non-scene3d binding path is
the next convergence step.)

## Self-verification

The Stage runs in Compose-wasm (canvas, no DOM). Drive + verify via the SDUI introspection bridge
(`StageDebug.kt`): `window.__stageBindings` / `__stageLog` / `__stageLastAction` /
`__stageTrainingRows`. Prefer this over pixels; verify UI state after known async completes.
