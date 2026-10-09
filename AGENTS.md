# Authoring a factored-ui spec (for agents)

Every screen is a spec the Kotlin renderer draws. You write JSON; you never write HTML, JS or canvas code. If a screen needs something the primitives cannot do, ask for a primitive or prop; do not build a page around it.

Source of truth, in this order: `packages/kotlin-compose-schema/.../schema/SpecNode.kt` (envelope, node, primitive names), `SpecProps.kt` (what each primitive reads from `props`), `packages/kotlin-compose/.../renderer/RenderNode.kt` (what is drawn). There is no JSON Schema for specs; the Kotlin types are the contract.

## Envelope and node

```json
{ "spec_version": 1, "renderer_min": 1,
  "root": { "id": "root", "type": "column", "props": {}, "children": [] } }
```

A node is `{ id, type, props, children, visible, action }`. `id` is unique in the spec. `props` values are literals, binding refs, or nested nodes. `visible` is a binding ref; the node is hidden when it resolves falsy. `action` is `{ "action": "name", "params": {} }`, dispatched to the host's ActionRegistry on primary interaction.

## Primitives (closed set; the test `AgentsGuideTest` keeps this list equal to the enum)

column, row, stack, scrollview, grid, text, image, video, icon, divider, spacer, textinput, button, toggle, select, slider, card, list, tabs, modal, chip, scene3d, canvas, geomap, graph, tilemap, scene.

There is no table, no ratio-against-a-limit widget and no money or unit formatting. A table is a `list` with an `itemTemplate`; text prints the bound value as given, so supply display strings (for example "$92.37", "8′ 7″") in the data.

## Bindings

A string `"{path.to.value}"` is resolved at render time from one state map. Inside a list row template the row is `{row.field}`.

- `list`: either `children` (one node per row) or `data` plus `itemTemplate` (one template over rows). Never both; the renderer prints a red notice if you give both. `data` is a state key or a ref such as `"{row.branches}"`.
- Controls (`toggle`, `select`, `slider`, `textinput`) write their new value back to the bound path.
- Dense primitives take typed data, not children. `geomap` shows a selection from a `selected` binding (list of ids), writes it on tap, and dispatches `on_feature_tap`. `tilemap` dispatches `on_tile_tap` and `on_instance_tap`. `scene` and `tilemap` pick a renderer from `view_state` (`level_feet`, `quarter_turns`, `centre_mm`, `renderer`).

## Actions

`button`, `card` and tappable nodes dispatch their `action` to the host. No built-in "set this binding" action exists today, so a spec cannot by itself make a row tap change state: only controls and the dense primitives above write bindings.

## Checking a spec

Decode with `Json { ignoreUnknownKeys = true }` into `Spec.serializer()`, then draw it with `RenderSpec(spec, RenderContext(initialData = ...))` in a `runComposeUiTest`. `RenderListChildrenTest` and `EveryPrimitiveDrawsTest` are the working examples. A binding that names no key renders blank; assert the text you expect is displayed.

## Scene data

The `scene` family reads the engine's scene payload (`SceneView`, read by `readSceneView`): millimetres, layers (ground, surface, footprints, instances, water, boundaries, annotations), each with a version and provenance. Footprints carry `stack_level` and `z_mm`; types carry `unit_cost_usd` and `unit_weight_kg` (null means unknown, never zero); annotations carry a numeric `value`.
