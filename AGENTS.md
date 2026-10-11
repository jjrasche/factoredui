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

`text` props: `value`, `variant` (`heading`, `subheading`, `body` (default), `caption`, `label`), `bold` (bool), `color`, `numberOfLines`, `align` (`left` (default), `center`, `right`), `format` and `missing`. A `center` or `right` text fills the width of its slot (its flex cell in a row, or the width its parent gives), so a column of figures lines up. In a row slot with no `flex` it keeps the width of its words, leaving the rest of the row to its flex siblings. Give a table's figure cells a `flex`.

`text` prints its value as given unless it names a `format`. With a format, bind the raw number. The renderer draws the display string:

- `usd`: dollars, cents only when there are cents ("$92.37", "$248", "$5,630.49").
- `feet_inches`: the value is millimetres ("9′ 0 1/16″").
- `percent`: the value is a fraction (0.76 draws "76%").
- `pounds`: the value is kilograms ("22.0 lb"; whole pounds from 100 lb).
- An object carrying its own conversion: `"format": { "scale": 0.5, "decimals": 2, "suffix": " pts" }` draws the value times `scale`, rounded to `decimals` places, grouped in thousands as `usd` is, then `suffix` as written. 2469 draws "1,234.50 pts". `scale` is required. `decimals` is a whole number from 0 to 12 (default 0) and `suffix` defaults to nothing. Halves round away from zero, as the named formats round.

A number bound with no format prints as a plain figure: 12.0 draws "12", 2440.5 draws "2440.5".

What a bad or missing value draws:

- A null or absent value draws the dash "–", or the node's `missing` text (`"missing": "not measured"`, or a binding to a reason). With no format it draws the `missing` text or nothing.
- A value that is not a number draws a notice: "usd needs a number, not '92.37'". Text that looks like a number is still text.
- An unknown format name draws "unknown format 'usdd'".
- An object format that cannot be read draws a notice and no figure, even when the value is missing: "format scale needs a number, not '0.0002'" (a scale written as text, or null, NaN, infinity or a bool), "format decimals needs a whole number from 0 to 12, not '1.5'", "format suffix needs text, not '3'", "format has no key 'decimal'; it reads scale, decimals and suffix". Under an object format a non-number value draws "format needs a number, not 'abc'". A figure too large to hold its decimals exactly draws "format cannot draw 10000000000 to 12 decimals exactly".
- Never pre-format a number in the data when a format exists for it.

## Bindings

A string `"{path.to.value}"` is resolved at render time from one state map. Inside a list row template the row is `{row.field}`.

- `list`: either `children` (one node per row) or `data` plus `itemTemplate` (one template over rows). Never both; the renderer prints a red notice if you give both. `data` is a state key or a ref such as `"{row.branches}"`.
- `itemTemplate` is one node drawn once per row, with the row bound as both `{row.x}` and `{item.x}`.
- `emptyText` (text) is drawn in place of the rows when there are none. With no `emptyText` an empty list draws nothing.
- `maxItems` (whole number) draws only that many rows.
- `stacked` (bool, default false) lays the rows out in a plain column instead of a lazy list that scrolls itself. A list whose height is unbounded, as inside a `scrollview`, stacks its rows (or children) whether or not it says so, and takes the height of its content. A list with a bounded height, such as one with a `flex` in a `column` outside any scroll, is lazy and scrolls itself.
- `flex` (number) on a child of a `column` or `row` gives it that share of the space left over, as a weight. A `column` with a flex child fills its height. Other layout props: `gap` and `padding` (whole numbers), `align` (`start`, `center`, `end`, `stretch`), `justify` (`start`, `center`, `end`, `between`, `around`) and `background` (a theme token `ground`, `surface`, `ink` or `muted`, or a colour).
- A `scene` inside a `scrollview` takes the shape of its window at the available width (at least 200 tall, at most 1.5 times its width) instead of filling the height.
- Controls (`toggle`, `select`, `slider`, `textinput`) write their new value back to the bound path.
- Dense primitives take typed data, not children.
- `geomap` shows a selection from a `selected` binding (list of ids), writes it on tap, and dispatches `on_feature_tap`.
- `tilemap` dispatches `on_tile_tap` and `on_instance_tap`.
- `scene` and `tilemap` pick a renderer from `view_state` (`level_feet`, `quarter_turns`, `centre_mm`, `renderer`).

## Actions

- `button`, `card` and tappable nodes dispatch their `action` to the host's ActionRegistry.
- Every param is resolved against the data first. Inside a list row, `"{row.ids}"` is that row's ids.
- One action is built in: `set_binding` with params `{ "path": "a.b", "value": ... }`. It writes `value` to `path` and never reaches the host.
- A row tap changes state with it, for example a report row that picks its parts: `{ "action": "set_binding", "params": { "path": "board.picked", "value": "{row.object_ids}" } }`.
- Dotted paths reach into row maps: `{row.values.worth}` is the `worth` entry of the row's `values`.
- `set_binding` refuses an empty `path` or a missing `value`. It writes nothing to the path and writes the reason to `renderer.error`. Bind a text node to `"{renderer.error}"` to show it.
- Any other state change the screen needs belongs to the host.

## Stacked scene

A `scene` node with `"view_state": { "renderer": "stacked_scene" }` draws parts stacked in levels from a scene payload. Props:

- `scene_view`: the payload, bound to an object or its JSON text. A bad payload prints the reason on screen.
- `view`: `plan` (default), `iso`, `south` or `west`. The plan draws a part's x toward the right. Its y runs as `y_axis` says. The south elevation looks along +y with x to the right. The west elevation looks along +x, so the y that is drawn at the top of the plan is on its left.
- `y_axis`: `up` (default) draws the largest y at the top of the plan, so the smallest y is the bottom edge. `down` draws the smallest y at the top, for a world whose rows count downward from its top edge. Under `down` the plan, the iso (which side is seen and which parts are in front) and the west elevation all turn together: the scene is drawn as if each part's y were mirrored across the window. The south elevation's left-to-right order is unchanged. Any other value draws a notice naming `up` and `down` and draws `up`. For example, a tile world with row 0 at its top edge: `"y_axis": "down"`.
- `focus`: `all` (default) or a whole stack level number, as a number or as text ("1"), so a `select` can write it.
- `show_lower` (bool): also shows the levels below the focus, faded. A `toggle` can write it.
- `hide_top` (whole number, as a number or as text): hides that many top levels. A `select` can write it.
- `highlight`: a binding to a list of part ids. Those parts get the heavy outline and every other part fades.
- `selected`: a binding path to a list of part ids. A tap selects the part and opens a popup naming its type, its id and, under `color_by`, its value in the active scale's `format` followed by the label of the stop the value falls in: "p2 · 32% · busy". A stop with no label adds nothing. A part with no value shows the scale's `unknown_label`, if it has one. The popup follows a switched scheme. Tapping it again or tapping empty space clears it. Unbound, the selection is kept locally.
- `on_part_tap`: an action name, dispatched with params `{ "id": "<part id>" }`.
- `color_by`: `{ "channel", "stops": [ { "up_to", "color", "label" } ], "unknown_color", "unknown_label", "format" }`. A part's value is the annotation on its footprint whose `kind` is the channel. It takes the first stop whose `up_to` it does not pass; a stop with no `up_to` takes everything above. No value, or a value no stop covers, takes `unknown_color`. Labelled stops draw a legend. Every legend swatch, the unknown one included, has a thin neutral outline that reads on light and dark pages, so even a white stop can be found. Without `color_by` every part is one neutral grey: the renderer holds no bands of its own.
- `color_by` as schemes: `{ "scheme": "<name>", "schemes": { "<name>": <scale>, ... } }`, where each scale is the object above with its own channel, stops, legend and format. The scene is coloured by `schemes[scheme]`, and `scheme` is usually a binding, so a `select` can switch the colouring live; the parts and the legend redraw. An absent or unbound `scheme` takes the first scheme as written. A name the schemes do not hold, or a scheme that is not text, draws every part grey and names the schemes on screen. For example:

```json
"color_by": {
  "scheme": "{board.scheme}",
  "schemes": {
    "threat": { "channel": "threat", "stops": [ { "up_to": 0.5, "color": "#2e7d32", "label": "calm" }, { "color": "#c62828", "label": "danger" } ] },
    "hp": { "channel": "hp", "stops": [ { "up_to": 0.25, "color": "#c62828", "label": "low" }, { "color": "#1565c0", "label": "healthy" } ], "format": "percent" }
  }
}
```

Notices drawn over the scene:

- A prop the renderer cannot read is named, with what it fell back to: "view 'isometric' is not plan, iso, south or west; the plan is drawn." The same holds for `focus`, `highlight`, `show_lower`, `hide_top`, `on_part_tap` and `color_by`.
- Each part is drawn from its `z_mm` up by its own `height_mm`, or its type's when the footprint states none.
- The iso, south and west drawings say "The scene states no part heights, so every part is drawn flat at its base." when no shown part has a height.
- The south and west elevations stretch heights so a low stack can be read, and say so: "Heights are drawn 2 times their true scale." The plan and iso are true to scale.
- A focus that shows no part says so.

`packages/kotlin-compose/src/desktopTest/resources/specs/stacked-board.spec.json` is a working example: the scene, level selects, a colour scale, and report rows that each highlight their parts.

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
- Types carry an id, label, tags, `footprint_mm` and `height_mm`. Any other type key is ignored.
- Annotations carry a numeric `value`. Null means unknown, never zero.
- An absent or null key takes its default.
- A present key of the wrong type is refused with a `SceneViewFormatException` naming the key. A fraction where a whole number belongs is refused, never rounded. So are a number given as text, a non-array list, a non-object list entry and a malformed `points_mm` pair.

## Reports data

The engine writes a reports file beside the scene: `{ "reports": [ { "id", "label", "columns": [ { "id", "label", "unit" } ], "rows": [ { "id", "label", "object_ids": [...], "values": { ... } } ] } ] }`.

- `readReports(text)` reads it into `Report` and `ReportRow` (`ai.factoredui.compose.scene`).
- A value is a number or null. Null means the engine does not know it, never zero, and stays null.
- A value of the wrong type, a row or column without an id, a non-object row and two reports sharing an id are refused with a `ReportsFormatException` naming where (`reports[0].rows[0].values.quantity must be a number or null, not "12"`).
- `reportsState(reports)` keys the reports by id for the state map; each is `{ id, label, columns, rows }` and each row `{ id, label, object_ids, values }`.
- A spec binds a report's rows directly: put `reportsState(...)` under `reports` and list `"data": "{reports.<id>.rows}"`, showing `{row.label}` and `{row.values.<column>}` with any text `format`. A null or absent value shows the text node's `missing`, which can itself be a binding (`"missing": "{board.unknown_reason}"`) so the reason comes from data.
- The library sums, prices and weighs nothing: totals, costs and weights are rows the host sends.
