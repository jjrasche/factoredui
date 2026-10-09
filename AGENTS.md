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

There is no table and no ratio-against-a-limit widget. A table is a `list` with an `itemTemplate`.

## Formatted figures

`text` prints its value as given unless it names a `format`. With a format, bind the raw number and the renderer draws the display string:

- `usd`: dollars, cents only when there are cents ("$92.37", "$248", "$5,630.49").
- `feet_inches`: the value is millimetres ("9′ 0 1/16″").
- `percent`: the value is a fraction (0.76 draws "76%").
- `pounds`: the value is kilograms ("22.0 lb"; whole pounds from 100 lb).

A null, missing or non-numeric value draws the dash "–", or the node's `missing` text when it has one (`"missing": "weight not known"`). Never pre-format a number in the data when a format exists for it.

## Bindings

A string `"{path.to.value}"` is resolved at render time from one state map. Inside a list row template the row is `{row.field}`.

- `list`: either `children` (one node per row) or `data` plus `itemTemplate` (one template over rows). Never both; the renderer prints a red notice if you give both. `data` is a state key or a ref such as `"{row.branches}"`.
- Controls (`toggle`, `select`, `slider`, `textinput`) write their new value back to the bound path.
- Dense primitives take typed data, not children. `geomap` shows a selection from a `selected` binding (list of ids), writes it on tap, and dispatches `on_feature_tap`. `tilemap` dispatches `on_tile_tap` and `on_instance_tap`. `scene` and `tilemap` pick a renderer from `view_state` (`level_feet`, `quarter_turns`, `centre_mm`, `renderer`).

## Actions

`button`, `card` and tappable nodes dispatch their `action` to the host, with every param resolved against the data first (inside a list row, `"{row.ids}"` is that row's ids).

One action is built in: `set_binding` with params `{ "path": "a.b", "value": ... }` writes `value` to `path` and never reaches the host's ActionRegistry. It is how a row tap changes state, for example a BOM row that picks its parts: `{ "action": "set_binding", "params": { "path": "bom.picked", "value": "{row.ids}" } }`. Any other state change the screen needs belongs to the host.

## Structure scene

A `scene` node with `"view_state": { "renderer": "structure" }` draws a built structure from the engine's scene payload instead of a map. Props:

- `scene_view`: the payload, bound to an object or its JSON text. A bad payload prints the reason on screen.
- `view`: `plan` (default), `iso`, `south` or `west`.
- `focus`: `all` (default) or a stack level number; `show_lower` (bool) also shows the levels below it; `hide_top` (int) hides that many top levels.
- `highlight`: a binding to a list of part ids. Those parts get the heavy outline and every other part fades.
- `selected`: a binding path to a list of part ids. A tap selects the part and opens a popup naming its type, id and share of capacity; tapping it again or tapping empty space clears it. Unbound, the selection is kept locally.
- `on_part_tap`: an action name dispatched with params `{ "id": "<part id>" }`.
- `color_by`: `utilisation` (the only colouring): green to red by share of capacity, purple over 100%, grey where the scene carries no figure.

A part the scene gives no height is drawn flat at its base, and the elevations say so. `packages/kotlin-compose/src/desktopTest/resources/specs/engine-house.spec.json` is a working example: the structure beside a BOM list whose rows highlight their parts.

## Checking a spec

Decode with `Json { ignoreUnknownKeys = true }` into `Spec.serializer()`, then draw it with `RenderSpec(spec, RenderContext(initialData = ...))` in a `runComposeUiTest`. `RenderListChildrenTest` and `EveryPrimitiveDrawsTest` are the working examples. A binding that names no key renders blank; assert the text you expect is displayed.

## Scene data

The `scene` family reads the engine's scene payload (`SceneView`, read by `readSceneView`): millimetres, layers (ground, surface, footprints, instances, water, boundaries, annotations), each with a version and provenance. Footprints carry `stack_level` and `z_mm`; types carry `unit_cost_usd` and `unit_weight_kg` (null means unknown, never zero); annotations carry a numeric `value`.
