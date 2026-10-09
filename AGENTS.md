# Authoring a factored-ui spec (for agents)

Write JSON; never HTML or canvas code.

- Every screen is a spec the Kotlin renderer draws.
- If a screen needs something the primitives cannot do, ask for a primitive or prop. Do not build a page around it.
- Source of truth, in this order: `packages/kotlin-compose-schema/.../schema/SpecNode.kt` (envelope, node, primitive names), `SpecProps.kt` (what each primitive reads from `props`), `packages/kotlin-compose/.../renderer/RenderNode.kt` (what is drawn).
- There is no JSON Schema for specs. The Kotlin types are the contract.

## Envelope and node

```json
{ "spec_version": 1, "renderer_min": 1,
  "root": { "id": "root", "type": "column", "props": {}, "children": [] } }
```

- A node is `{ id, type, props, children, visible, action }`.
- `id` is unique in the spec.
- `props` values are literals, binding refs, or nested nodes.
- `visible` is a binding ref. The node is hidden when it resolves falsy.
- `action` is `{ "action": "name", "params": {} }`. It is dispatched on primary interaction.

## Primitives (closed set; the test `AgentsGuideTest` keeps this list equal to the enum)

column, row, stack, scrollview, grid, text, image, video, icon, divider, spacer, textinput, button, toggle, select, slider, card, list, tabs, modal, chip, scene3d, canvas, geomap, graph, tilemap, scene.

- There is no table and no ratio-against-a-limit widget.
- A table is a `list` with an `itemTemplate`.

## Formatted figures

`text` prints its value as given unless it names a `format`. With a format, bind the raw number. The renderer draws the display string:

- `usd`: dollars, cents only when there are cents ("$92.37", "$248", "$5,630.49").
- `feet_inches`: the value is millimetres ("9′ 0 1/16″").
- `percent`: the value is a fraction (0.76 draws "76%").
- `pounds`: the value is kilograms ("22.0 lb"; whole pounds from 100 lb).

What a bad or missing value draws:

- A null or absent value draws the dash "–", or the node's `missing` text (`"missing": "weight not known"`).
- A value that is not a number draws a notice: "usd needs a number, not '92.37'". Text that looks like a number is still text.
- An unknown format name draws "unknown format 'usdd'".
- Never pre-format a number in the data when a format exists for it.

## Bindings

A string `"{path.to.value}"` is resolved at render time from one state map. Inside a list row template the row is `{row.field}`.

- `list`: either `children` (one node per row) or `data` plus `itemTemplate` (one template over rows). Never both; the renderer prints a red notice if you give both. `data` is a state key or a ref such as `"{row.branches}"`.
- Controls (`toggle`, `select`, `slider`, `textinput`) write their new value back to the bound path.
- Dense primitives take typed data, not children.
- `geomap` shows a selection from a `selected` binding (list of ids), writes it on tap, and dispatches `on_feature_tap`.
- `tilemap` dispatches `on_tile_tap` and `on_instance_tap`.
- `scene` and `tilemap` pick a renderer from `view_state` (`level_feet`, `quarter_turns`, `centre_mm`, `renderer`).

## Actions

- `button`, `card` and tappable nodes dispatch their `action` to the host's ActionRegistry.
- Every param is resolved against the data first. Inside a list row, `"{row.ids}"` is that row's ids.
- One action is built in: `set_binding` with params `{ "path": "a.b", "value": ... }`. It writes `value` to `path` and never reaches the host.
- A row tap changes state with it, for example a BOM row that picks its parts: `{ "action": "set_binding", "params": { "path": "bom.picked", "value": "{row.ids}" } }`.
- `set_binding` refuses an empty `path` or a missing `value`. It writes nothing to the path and writes the reason to `renderer.error`. Bind a text node to `"{renderer.error}"` to show it.
- Any other state change the screen needs belongs to the host.

## Structure scene

A `scene` node with `"view_state": { "renderer": "structure" }` draws a built structure from the engine's scene payload. Props:

- `scene_view`: the payload, bound to an object or its JSON text. A bad payload prints the reason on screen.
- `view`: `plan` (default), `iso`, `south` or `west`.
- `focus`: `all` (default) or a whole stack level number.
- `show_lower` (bool): also shows the levels below the focus.
- `hide_top` (whole number): hides that many top levels.
- `highlight`: a binding to a list of part ids. Those parts get the heavy outline and every other part fades.
- `selected`: a binding path to a list of part ids. A tap selects the part and opens a popup naming its type, id and share of capacity. Tapping it again or tapping empty space clears it. Unbound, the selection is kept locally.
- `on_part_tap`: an action name, dispatched with params `{ "id": "<part id>" }`.
- `color_by`: `utilisation` (the only colouring). Green to red by share of capacity, purple over 100%, grey where the scene carries no figure.

Notices drawn over the structure:

- A prop the renderer cannot read is named, with what it fell back to: "view 'isometric' is not plan, iso, south or west; the plan is drawn." The same holds for `focus`, `highlight`, `show_lower`, `hide_top`, `on_part_tap` and `color_by`.
- The iso, south and west drawings say "The scene states no part heights, so every part is drawn flat at its base." when no shown part has a height.
- A focus that shows no part says so.

The BOM:

- Hosts build the BOM rows with `bomLines` until a named report supplies them.
- `packages/kotlin-compose/src/desktopTest/resources/specs/engine-house.spec.json` is a working example: the structure beside a BOM list whose rows highlight their parts.

## Checking a spec

- Decode with `Json { ignoreUnknownKeys = true }` into `Spec.serializer()`.
- Draw it with `RenderSpec(spec, RenderContext(initialData = ...))` in a `runComposeUiTest`.
- `RenderListChildrenTest` and `EveryPrimitiveDrawsTest` are the working examples.
- A binding that names no key renders blank. Assert the text you expect is displayed.

## Scene data

The `scene` family reads the engine's scene payload (`SceneView`, read by `readSceneView`).

- Units are millimetres.
- Layers: ground, surface, footprints, instances, water, boundaries and annotations. Each carries a version.
- Every layer but annotations also carries provenance. Annotations carry a version only.
- Footprints carry `stack_level` and `z_mm`.
- Types carry `unit_cost_usd` and `unit_weight_kg`. Null means unknown, never zero.
- Annotations carry a numeric `value`.
- An absent or null key takes its default.
- A present key of the wrong type is refused with a `SceneViewFormatException` naming the key. A fraction where a whole number belongs is refused, never rounded. So are a number given as text, a non-array list, a non-object list entry and a malformed `points_mm` pair.
