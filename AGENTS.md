# factored-ui for agents

## Driving a window: the control port

Build or verify a screen (a world, a table, anything) by driving the window a person uses through its control port, and read it as text. The control port has two halves: eyes (`/tree`, `/get`) to read what is drawn and the live state, and hands (`/click`, `/set`, `/wheel`) to act. Pictures (`/shot`) are a receipt, never the check.

Launch:

- Build once: `./gradlew :kotlin-compose:installSpecWindow`.
- Then run `java -cp "packages/kotlin-compose/build/spec-window/lib/*" ai.factoredui.compose.render.SpecWindowKt <spec.json> [width] [height] [--data seeds.json] [--state a.b=text|json|reports:file ...] [--theme light|dark] [--title "name"] [--control <port>]`.
- `--state` repeats. `text` binds the file's text, `json` its decoded object, and `reports` its reports keyed by id.
- `--control <port>` serves the control port on 127.0.0.1 only. Each route is a GET with these query parameters (the test `ControlPortDocTest` keeps this block equal to the routes `WindowControl` serves):

<!-- control-port-routes -->
```
/tree   id text visible
/get    path
/set    path value
/click  id x y
/wheel  x y notches
/shot   file
```
<!-- /control-port-routes -->

Eyes:

- `/tree` returns what is drawn now, as JSON. Each node has `{id, type, text, props, visible, enabled, action, bounds {x,y,w,h}, children}`.
  - A list's rows appear once each, with ids such as `rows/0/row-label`.
  - `props` holds the resolved values of the props that were bindings. A bulky value appears as its shape, e.g. `"<list: 3 items>"`.
  - The same screen gives the same bytes.
- `/tree?id=<id>` returns that subtree, and `/tree?visible=1` leaves out hidden nodes. `/tree?text=<words>` returns the ids whose drawn text contains the words.
- A node that paints colour says which, in the values the drawing used. A `text` gives its `ink`, and its `fill` when it has a `background_ramp`. A `card`, `column` or `row` with a background gives its `fill`. Colours are `#rrggbb`, or `#rrggbbaa` when translucent.
- A `select` gives `options` (`value` and `label`) and its current `value` in `props`. While its menu is open, the items are its children, with ids `<select id>/option/<value>`, `text`, `selected` and `bounds`. A `modal`'s children are ordinary nodes.
- A `stacked_scene` adds `scene: {view, focus, hide_top, show_lower, y_axis, active_scheme, legend, notices, parts, popup}`.
  - Each part has `{id, type_id, level, fill, outline, value, bounds, centre, covered_by}`. `outline` is `normal`, `highlight`, `selected` or `faded`.
  - `fill` is the colour painted at the part's centre (the top face in the iso).
  - `covered_by` lists the parts drawn over that centre. When it is not empty, a pixel click there reaches the top part, not this one.
- `/get?path=a.b.c` takes a dotted path and answers its live value as JSON. A path that names no key is refused.
- Scrolled rows:
  - A row below the fold of a `scrollview` (or of a list inside one) is in `/tree` with its text, but its bounds have no size (`w` and `h` 0) until it is scrolled into view.
  - A lazy list (one with a bounded height, such as a `flex` list outside a scroll) draws only the rows in view. A row beyond them is not in `/tree` at all.
  - In both cases `/wheel` is the hand that brings the row into view.

Hands:

- `/click?id=<id>` clicks the centre of a node. A node with no size answers HTTP 400 "refused: '<id>' is drawn with no size".
- `/click?id=<scene id>/<part id>` (e.g. `board/e21`) taps that part by id, even when another part covers it. The selection (and the scene's `selected` binding when it has one), highlight, popup and `on_part_tap` are the same as a real tap. `/tree` then shows the part's `outline` as `selected`, and the scene's `popup` names it.
- `/click?id=<select id>/option/<value>` picks that item, writing the binding and closing the menu. It works only while the menu is open, so click the select by id first. On a closed select it answers 400 "refused: select '<id>' is not open".
- `/click?x=&y=` is a plain pixel click. Bounds and centres are in these same units.
- An unknown id or part, or a missing parameter, is refused with HTTP 400 "refused: ...".
- `/set?path=a.b&value=<json>` writes a JSON value into the live state at a dotted path.
- `/wheel?x=&y=&notches=` scrolls whatever is under the point.
- `/shot?file=<png>` saves what the window shows.
- `/set` and `/click` change the running screen's state, as a person's input would.

The loop that works:

1. Read `/tree` (or `/tree?id=`) and assert each node's `text` and `fill` against the engine's own numbers.
2. Act by id with `/click?id=` (or `/set`).
3. Read the binding back with `/get` and the screen back with `/tree`.
4. Restore what you changed with `/set`, so the next check starts from the state the window was launched with.

Example excerpt of `/tree?id=board` on the board fixture:

```json
{"id":"board","type":"scene","text":null,"props":{"hide_top":0,"scene_view":"<text: 3437 chars>","view":"plan"},"visible":true,"enabled":true,"action":null,
 "bounds":{"x":0,"y":128,"w":800,"h":772},
 "scene":{"view":"plan","focus":"all","hide_top":0,"show_lower":false,"y_axis":"up","active_scheme":null,
  "legend":[{"text":"quiet","color":"#3a7ca5"},{"text":"busy","color":"#e0a030"}, ...],"notices":[],
  "parts":[{"id":"r1","type_id":"bar","level":1,"fill":"#3a7ca5","outline":"normal","value":0.2,"bounds":{"x":24,"y":646,"w":752,"h":75},"centre":{"x":400,"y":684}}, ...],
  "popup":"Plate / p2 · 50% · busy"}}
```

## Authoring a spec

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

`text` props: `value`, `variant` (`heading`, `subheading`, `body` (default), `caption`, `label`), `bold` (bool), `color`, `numberOfLines`, `align` (`left` (default), `center`, `right`), `format`, `missing`, and `color_ramp` and `background_ramp` (see Colour ramps). A `center` or `right` text fills the width of its slot (its flex cell in a row, or the width its parent gives), so a column of figures lines up. In a row slot with no `flex` it keeps the width of its words, leaving the rest of the row to its flex siblings. Give a table's figure cells a `flex`.

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

## Colour ramps

A ramp turns a number into a colour on a gradient:

```json
"ramp": { "stops": [[0, "#3b7dd8"], [0.5, "#e6a93a"], [1, "#b5382a"]], "over": "#5a1e8c", "no_figure": "#9aa3ab",
          "legend": [{ "at": 0, "text": "0" }, { "at": 1, "text": "full" }], "over_text": "over", "no_figure_text": "no figure" }
```

- At or below the first stop's value a number takes the first colour. Between two stops each channel (alpha too) is interpolated in sRGB. Exactly the last stop's value takes the last colour.
- Above the last stop's value a number takes `over`, which defaults to the last colour. The ramp never clamps: 1.3 on a 0 to 1 ramp is `over`.
- A null or absent value takes `no_figure` (default a neutral grey).
- `stops` needs at least two `[number, colour]` pairs with values rising. Colours are `#rrggbb` or `#rrggbbaa`.
- A ramp that breaks a rule draws a notice naming it: "ramp needs at least 2 stops, not 1", "stop values must rise: 0.5 is followed by 0.25", "stop 1 must be [value, colour] with a number and #rrggbb or #rrggbbaa". The colouring falls back to grey.
- Uses:
  - A `color_by` scale or scheme may carry `ramp` instead of `stops`. Parts take the interpolated colour of their channel value. The legend strip shows a gradient bar from the first to the last stop value, each `legend` text under its value, then a swatch for `over_text` and one for `no_figure_text`. The popup adds a legend text only when the value equals its `at`, and the over or no-figure text.
  - A `text` takes `"color_ramp": { "value": "{row.values.x}", "ramp": {...} }` for its ink, and `"background_ramp"` (same shape) to fill its cell. `value` is a binding or a number; a value that is not a number draws "color_ramp needs a number, not 'abc'" in place of the words. Over a `background_ramp` the ink stays the `color_ramp` or `color` only if it reaches 4.5:1 against the fill; otherwise it is black or white, whichever contrasts more. A filled cell fills its slot.

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
- `color_by`: `{ "channel", "stops": [ { "up_to", "color", "label" } ], "unknown_color", "unknown_label", "format" }`. A part's value is the annotation on its footprint whose `kind` is the channel. It takes the first stop whose `up_to` it does not pass; a stop with no `up_to` takes everything above. No value, or a value no stop covers, takes `unknown_color`. Labelled stops draw a legend as a strip below the drawing. The drawing is fitted to the space left above it, so the legend never covers a part. Every legend swatch, the unknown one included, has a thin neutral outline that reads on light and dark pages, so even a white stop can be found. Without `color_by` every part is one neutral grey: the renderer holds no bands of its own.
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
- `reportsState(reports)` keys the reports by id for the state map; each is `{ id, label, columns, rows }` and each row `{ id, label, object_ids, values }`. A row also carries `fractions` (column to number or null, never clamped: 1.3 stays 1.3) and `flags` (column to text or null, such as `over` or `within`) when the report gives them. A row without them has neither key. Columns carry `id`, `label` and `unit` only; a column kind or a row's limits are not read.
- Colour a cell by its fraction: `"background_ramp": { "value": "{row.fractions.use}", "ramp": {...} }`. Show a flag as text: `{row.flags.use}`. A fraction field that is not an object, or an entry that is not a number, is refused with the reader's error naming where it sits.
- A spec binds a report's rows directly: put `reportsState(...)` under `reports` and list `"data": "{reports.<id>.rows}"`, showing `{row.label}` and `{row.values.<column>}` with any text `format`. A null or absent value shows the text node's `missing`, which can itself be a binding (`"missing": "{board.unknown_reason}"`) so the reason comes from data.
- The library sums, prices and weighs nothing: totals, costs and weights are rows the host sends.
