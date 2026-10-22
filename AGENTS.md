# factored-ui for agents

## Driving a window: the control port

Build or verify a screen (a world, a table, anything) by driving the window a person uses through its control port, and read it as text. The control port has two halves: eyes (`/tree`, `/get`) to read what is drawn and the live state, and hands (`/click`, `/set`, `/wheel`) to act. Pictures (`/shot`) are a receipt, never the check.

Launch:

- Build once: `./gradlew :kotlin-compose:installSpecWindow`.
- The window needs Java 21: the schema library on its class path (`kotlin-compose-schema`) is built as class file version 65. The first `java` on a PATH may be older and fail with `UnsupportedClassVersionError`, so launch with a Java 21 JDK's `java`.
- Then run `java -cp "packages/kotlin-compose/build/spec-window/lib/*" ai.factoredui.compose.render.SpecWindowKt <spec.json> [width] [height] [--data seeds.json] [--state a.b=text|json|reports:file ...] [--theme light|dark] [--title "name"] [--section <key>] [--control <port>]`.
- `--section <key>` draws the spec (`{spec_version, renderer_min, root}`) found at that top-level key of the file, so one file can hold a `screen` beside other sections, which are ignored. A key the file lacks is refused with the keys it holds: "the file has no section 'scene'; its top-level keys are projection, screen, sprites, types". Without `--section` the whole file is the spec.
- `--state` repeats. `text` binds the file's text, `json` its decoded object, and `reports` its reports keyed by id.
- `--control <port>` serves the control port on 127.0.0.1 only. Each route is a GET with these query parameters (the test `ControlPortDocTest` keeps this block equal to the routes `WindowControl` serves):

<!-- control-port-routes -->
```
/tree   id text visible depth
/get    path
/set    path value settle_ms
/click  id x y settle_ms
/wheel  x y notches settle_ms
/shot   file
```
<!-- /control-port-routes -->

Eyes:

- `/tree` returns what is drawn now, as JSON. Each node has these fields (the test `ControlPortDocTest` keeps this list equal to the fields a node carries): <!-- tree-node-fields -->`id, type, text, props, visible, enabled, action, composed, bounds, children`<!-- /tree-node-fields -->. The object returned also carries a top-level `frame`: the count of frames the screen has drawn while the control port waited for it to settle.
  - `bounds` is `{x, y, w, h}` where the node shows in the window. `composed` is `true` when the node has that place on screen. A node with no place on screen, because it is clipped wholly out of view (below the fold of a scroll, or off the window) or is not placed yet, has `"composed": false` and `"bounds": null`, while `visible` still says whether its spec shows it.
  - A node in view with no size, such as an empty `column`, is composed: its `bounds` give its place with `w` or `h` 0.
  - A list's rows appear once each, with ids such as `rows/0/row-label`.
  - `props` holds the resolved values of the props that were bindings. A bulky value appears as its shape, e.g. `"<list: 3 items>"`.
  - The same screen gives the same bytes.
- `/tree?id=<id>` returns that subtree. Any id the tree emits works, including `<list id>/empty`, an open select's `<select id>/option/<value>` and a scene part's `<scene id>/<part id>`. A part comes back as its scene lists it, with `type` `part` and the full id.
- `/tree?visible=1` leaves out hidden nodes.
- `/tree?text=<words>` answers `{ids, hits}`: `ids` lists the ids whose drawn text contains the words, and `hits` gives each as `{id, text, bounds}`.
- `/tree?depth=<n>` stops `n` levels below the node returned, so a large page's top-level structure is cheap to read. A node at the limit keeps its own fields, gives `children: []`, and says how many children it left out in `children_omitted`. With `?id=` the depth counts from that node.
- A list with no rows that draws its `emptyText` has a child node `<list id>/empty` of type `empty`, whose `text` is the drawn message. The node is there only while the list is empty.
- A `text` whose `value` binding resolved to null carries `"null": true`, so a verifier can list the bound cells that drew nothing, a dash or their `missing` text.
- A node that paints colour says which, in the values the drawing used. A `text` gives its `ink`, and its `fill` when it has a `background_ramp`. A `card`, `column` or `row` with a background gives its `fill`. Colours are `#rrggbb`, or `#rrggbbaa` when translucent.
- A `select` gives `options` (`value` and `label`) and its current `value` in `props`. While its menu is open, the items are its children, with ids `<select id>/option/<value>`, `text`, `selected` and `bounds`. A `modal`'s children are ordinary nodes.
- A `stacked_scene` adds `scene` with these fields (the test `ControlPortDocTest` keeps this list equal to the fields a scene record carries): <!-- scene-record-fields -->`view, focus, hide_top, show_lower, y_axis, north, active_scheme, quarter_turns, zoom, pan_x, pan_y, legend, notices, parts, popup`<!-- /scene-record-fields -->.
- A `chart` adds `chart` with these fields (kept equal to the record by `ControlPortDocTest`): <!-- chart-record-fields -->`x, axes, series, hover, legend`<!-- /chart-record-fields -->. `x` is `{low, high, ticks}`, the span of the plotted rows and its labelled ticks `[{x, label}]`; `axes` lists each drawn axis as `{side, unit, label, low, high, ticks}`; `series` lists each as `{id, label, kind, axis, color, runs}` where `runs` is its unbroken stretches, each a list of `[x, value, from, to]`; `hover` is null or `{x, values: [{id, text}]}`, what the pointer is reading.
  - `quarter_turns`, `zoom`, `pan_x` and `pan_y` are the values the drawing was made with: `quarter_turns` 0 to 3, `zoom` held between 0.25 and 16.
  - Each part has `{id, type_id, level, fill, outline, value, bounds, centre, covered_by}`. `outline` is `normal`, `highlight`, `selected` or `faded`.
  - `fill` is the colour painted at the part's centre (the top face in the iso).
  - `covered_by` lists the parts drawn over that centre. When it is not empty, a pixel click there reaches the top part, not this one.
- `/get?path=a.b.c` takes a dotted path and answers its live value as JSON. A path that names no key is refused.
- Scrolled rows:
  - A row below the fold of a `scrollview` (or of a list or table inside one) is in `/tree` with its text, but has `"composed": false` and `"bounds": null` until it is scrolled into view. A table's head and rows below the fold are the same.
  - A lazy list (one with a bounded height, such as a `flex` list outside a scroll) draws only the rows in view. A row beyond them is not in `/tree` at all.
  - In both cases `/wheel` is the hand that brings the row into view.

Hands:

- `/click?id=<id>` clicks the centre of a node. A node that is not composed answers HTTP 400 "refused: '<id>' is drawn with no bounds", and a composed node with no size answers "refused: '<id>' is drawn with no size".
- `/click?id=<scene id>/<part id>` (e.g. `board/e21`) taps that part by id, even when another part covers it. The selection (and the scene's `selected` binding when it has one), highlight, popup and `on_part_tap` are the same as a real tap. `/tree` then shows the part's `outline` as `selected`, and the scene's `popup` names it.
- `/click?id=<select id>/option/<value>` picks that item, writing the binding and closing the menu. It works only while the menu is open, so click the select by id first. On a closed select it answers 400 "refused: select '<id>' is not open".
- `/click?x=&y=` is a plain pixel click. Bounds and centres are in these same units.
- An unknown id or part, or a missing parameter, is refused with HTTP 400 "refused: ...".
- `/set?path=a.b&value=<json>` writes a JSON value into the live state at a dotted path.
- `/wheel?x=&y=&notches=` scrolls whatever is under the point. Over a stacked scene whose `quarter_turns` or `zoom` is bound it zooms the scene about the point instead; a negative `notches` zooms in.
- `/shot?file=<png>` saves what the window shows.
- `/set` and `/click` change the running screen's state, as a person's input would.
- `/click`, `/set` and `/wheel` return only once the screen has settled: it has drawn a frame after the write, then a frame that changed nothing in the tree. A `/tree` read straight after shows the result, with no polling; a chosen select option's menu is already gone, and a second tap acts on the first tap's result. The body ends `settled frame=<n>`. The wait is bounded, 2000 ms by default; `settle_ms=<n>` changes it and `settle_ms=0` returns at once. When the bound is hit (for example a minimised window drawing no frames), the answer is still HTTP 200, since the write happened, and ends `unsettled after <n> ms frame=<n>`.

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

- `theme` (optional): `light` or `dark`. It sets the ink and surfaces of every node that takes them from the theme (text, buttons, sliders, the `audio` and `voice` controls) and the window's ground when the root names no `background`. It overrides the host's choice; a name it does not know is written to `renderer.error` and the host's theme is used. The stock browser host takes `?theme=dark` or `?theme=light`, else the browser's `prefers-color-scheme`, else light. A node that names its own colour keeps it. A root `background` alone does not change the theme: a dark ground with the light theme draws dark ink on it.
- `state` (optional): dotted binding paths to the values they start at, `{ "day.from": 2757 }`. The first frame already shows them; a path that already has a value (set by the host or by a person) keeps it.
- A node is `{ id, type, props, children, visible, action }`.
- `id` is unique in the spec.
- `props` values are literals, binding refs, or nested nodes.
- `visible` is a binding ref. The node is hidden when it resolves falsy.
- `action` is `{ "action": "name", "params": {} }`. It is dispatched on primary interaction.

## Primitives (closed set; the test `AgentsGuideTest` keeps this list equal to the enum)

column, row, stack, scrollview, grid, text, image, video, audio, voice, icon, divider, spacer, textinput, button, toggle, select, slider, card, list, tabs, modal, chip, scene3d, canvas, geomap, graph, scene, table, plan, volume, chart, stat.

- There is no table and no ratio-against-a-limit widget.
- A table of the engine's views is a `table` (see Tables). Any other table is a `list` with an `itemTemplate`.

## Formatted figures

`text` props: `value`, `variant` (`heading`, `subheading`, `body` (default), `caption`, `label`), `bold` (bool), `color`, `numberOfLines`, `align` (`left` (default), `center`, `right`), `format`, `missing`, and `color_ramp` and `background_ramp` (see Colour ramps). A `center` or `right` text fills the width of its slot (its flex cell in a row, or the width its parent gives), so a column of figures lines up. In a row slot with no `flex` it keeps the width of its words, leaving the rest of the row to its flex siblings. A text with a `width` fills that width whatever its `align`. Give a table's figure cells a `flex`.

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
- `stacked` (bool, default false) lays the rows out in a plain column instead of a lazy list that scrolls itself. A list whose height is unbounded, as inside a `scrollview`, stacks its rows (or children) whether or not it says so, and takes the height of its content. A list with a bounded height, such as one with a `flex` in a `column` outside any scroll, or one with a `height` even inside a scroll, is a fixed pane: it is lazy and scrolls its own rows.
- `flex` (number) on a child of a `column` or `row` gives it that share of the space left over, as a weight. A `column` with a flex child fills its height.
- `width` and `height` (numbers, dp above 0) on a child of a `column`, `row`, `stack` or `scrollview` draw it exactly that wide or that tall. In a row a `width` takes the place of `flex`, and in a column a `height` does; the flex siblings share what is left. In a scroll, whose height is unbounded, a `height` is what gives a child with no flex a height. A size of 0 or less, or one that is not a number, is ignored, and the parent shows a notice naming the child: "'chart' height must be a number of dp above 0, not '0'; it is ignored." A size is a literal number, not a binding. Other layout props: `gap` and `padding` (whole numbers), `align` (`start`, `center`, `end`, `stretch`), `justify` (`start`, `center`, `end`, `between`, `around`) and `background` (a theme token `ground`, `surface`, `ink` or `muted`, or a colour).
- A `scene` inside a `scrollview` takes the shape of its window at the available width (at least 200 tall, at most 1.5 times its width) instead of filling the height. A `height` gives it that height instead.
- Controls (`toggle`, `select`, `slider`, `textinput`) write their new value back to the bound path.
- A `toggle` takes `label` and `color` (the label's ink, `#rrggbb`), for a toggle on a coloured ground.
- A `stack` with `fill: true` fills the space its parent gives it, and takes a `background`. Its children sit at the top-left unless one carries `anchor`: `fill` fills the stack, `top` or `bottom` spans its width at that edge. A child with `sheet: { closed_dp, open_dp }` (open not less than closed) is a bottom panel over the stack: a handle (`<id>/handle`) is dragged to set its height between the two, snapping to the nearer on release, and a tap on it flips it. The sheet's first child stays above; the rest scroll under it.
- A `select` option is `{ "label": "Zoom 2 times", "value": 2 }`; its `value` is text, a number or true/false, and is written in that type. An option is chosen when the bound value reads as the same text (2, 2.0 and "2" all choose option 2), and `/click?id=<select id>/option/2` picks it. An option with no text `label` or with any other `value` is dropped, and the select shows one notice naming each dropped option's index: "select 'zoom' dropped options 1, 3: each needs a text 'label' and a 'value' that is text, a number or true/false".
- Dense primitives take typed data, not children.
- `geomap` shows a selection from a `selected` binding (list of ids), writes it on tap, and dispatches `on_feature_tap`.
- `scene` picks a renderer from `view_state` (`level_feet`, `quarter_turns`, `centre_mm`, `renderer`).

## Actions

- `button`, `card` and tappable nodes dispatch their `action` to the host's ActionRegistry.
- Every param is resolved against the data first. Inside a list row, `"{row.ids}"` is that row's ids.
- One action is built in: `set_binding` with params `{ "path": "a.b", "value": ... }`. It writes `value` to `path` and never reaches the host.
- `set_binding` with params `{ "values": { "a.turns": 0, "a.zoom": 1 } }` writes every path in one tap, in the object's key order, each value resolved as `value` is. It refuses `values` beside `path` or `value`, an empty `values`, a `values` that is not an object, or an empty path, and then writes none of them.
- `set_binding` with params `{ "path": "ui.step", "add": 1, "min": 0, "max": 9 }` adds `add` to the number at `path` (`min`, else 0, when there is none) and keeps it between `min` and `max` when given: a next or previous button. It refuses `add` beside `value` or one that is not a number.
- `visible` takes `"{ui.step}=3"` too: the node shows when the value at the path equals what follows the `=`, numbers compared as numbers. One card per step then shows only for its own step.
- A row tap changes state with it, for example a report row that picks its parts: `{ "action": "set_binding", "params": { "path": "board.picked", "value": "{row.object_ids}" } }`.
- Dotted paths reach into row maps: `{row.values.worth}` is the `worth` entry of the row's `values`.
- `set_binding` refuses an empty `path` or a missing `value`. It writes nothing to the path and writes the reason to `renderer.error`. Bind a text node to `"{renderer.error}"` to show it.
- Any other state change the screen needs belongs to the host.

## Stacked scene

A `scene` node with `"view_state": { "renderer": "stacked_scene" }` draws parts stacked in levels from a scene payload. Props:

- `scene_view`: the payload, bound to an object or its JSON text. A bad payload prints the reason on screen.
- `view`: `plan` (default), `iso`, `south` or `west`. The iso is a fixed isometric projection: it looks down at 35.264 degrees from an azimuth of 45 degrees, and `quarter_turns` picks which of its four aspects is shown. The plan draws a part's x toward the right. Its y runs as `y_axis` says. The south elevation looks along +y with x to the right. The west elevation looks along +x, so the y that is drawn at the top of the plan is on its left.
- `y_axis`: `up` (default) draws the largest y at the top of the plan, so the smallest y is the bottom edge. `down` draws the smallest y at the top, for a world whose rows count downward from its top edge. Under `down` the plan, the iso (which side is seen and which parts are in front) and the west elevation all turn together: the scene is drawn as if each part's y were mirrored across the window. The south elevation's left-to-right order is unchanged. Any other value draws a notice naming `up` and `down` and draws `up`. For example, a tile world with row 0 at its top edge: `"y_axis": "down"`.
- With no `y_axis` prop, the scene payload's `grid.north` decides. `"north": "row_0"` (row 0 is the plan's north edge) draws as `down`, so row 0 is at the top. With no `north` the scene draws `up`. Any other `north` draws `up` with a notice naming `row_0`. A stated `y_axis` always wins. `/tree` gives the scene's `north` and the `y_axis` it drew with.
- `focus`: `all` (default) or a whole stack level number, as a number or as text ("1"), so a `select` can write it.
- `show_lower` (bool): also shows the levels below the focus, faded. A `toggle` can write it.
- `hide_top` (whole number, as a number or as text): hides that many top levels. A `select` can write it.
- `highlight`: a binding to a list of part ids. Those parts get the heavy outline and every other part fades.
- `selected`: a binding path to a list of part ids. A tap selects the part and opens a popup naming its type, its id and, under `color_by`, its value in the active scale's `format` followed by the label of the stop the value falls in: "p2 · 32% · busy". A stop with no label adds nothing. A part with no value shows the scale's `unknown_label`, if it has one. The popup follows a switched scheme. Tapping it again or tapping empty space clears it. Unbound, the selection is kept locally.
- `on_part_tap`: an action name, dispatched with params `{ "id": "<part id>" }`.
- `quarter_turns` (whole number, as a number or as text): which of the iso's four aspects is drawn, each a quarter turn round from the last. 0 (default) looks from the smallest x and y; 1 from the smallest x and the largest y; 2 from the largest x and y; 3 from the largest x and the smallest y. It is read round the four, so 4 draws as 0 and -1 as 3. A value that is not a whole number draws the first aspect and a notice naming the scene: "scene 'board': quarter_turns must be a whole number, not '1.5'; the first aspect is drawn." The plan and the elevations ignore it.
- `zoom` (number, default 1, held between 0.25 and 16), `pan_x` and `pan_y` (numbers, default 0) frame the fitted drawing in every view. `zoom` scales it about the centre of the drawing area. `pan_x` moves it right by that share of the drawing area's width and `pan_y` down by that share of its height, so a value means the same at any window size: `"pan_x": 0.25` moves it a quarter of the width. Taps, a part's `bounds`, `centre` and `covered_by` follow the framed drawing. A zoom or pan that is not a finite number (or a zoom of 0 or less) draws the default and a notice naming the prop.
- Gestures: when `quarter_turns` or `zoom` is a binding, the drawing takes the pointer. The wheel zooms about the pointer, 1.25 times per notch, and the drawn point under the pointer stays under it. A drag with the control key held pans. In the iso a horizontal drag of at least 48 units steps `quarter_turns` one aspect in its direction, once per drag. A tap with no drag still taps a part; a drag never does. Each gesture writes a bound prop as the value it then reads (`quarter_turns` 0 to 3, `zoom` held between its bounds, a pan as a share); an unbound one is held by the scene. With neither `quarter_turns` nor `zoom` bound, the scene leaves the drag and the wheel to the page, so a scene inside a scrolling page scrolls with it. For example: `"quarter_turns": "{board.turns}", "zoom": "{board.zoom}", "pan_x": "{board.pan_x}", "pan_y": "{board.pan_y}"`.
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

## Tables

A `table` draws one view from the engine's views response: `{ id, label, columns: [ { id, label, unit, kind, type, null_display } ], rows: [ { id, label, object_ids, values } ] }`. It draws a title, a header row and one row per view row, laid out like list rows (lazy in a bounded pane, stacked inside a scroll). A `table` with a `height` is a fixed pane that scrolls its own rows, inside a scroll too.

- `view`: a binding to the view object, for example `"view": "{reports.<view id>}"` with the views loaded by `--state reports=reports:<views.json>`. A bare id string is not a view: it is refused with the notice "table '<id>' has no view: bind view to the engine's view object".
- `display`: the display profile, inline or a binding so one profile serves every table: `{ "units": { "<unit>": <entry> }, "kinds": { "<kind>": <entry> } }`. An entry is a named format (`"usd"`), an object format (`{ "scale", "suffix", "decimals" }`), or `{ "format": <either>, "label": "<text>" }`.
- `display` is inherited. Any container (`column`, `row`, `stack`, `scrollview`, `grid`, `card`, `list`, `tabs`, `modal`) may carry `display`, and every `table` below it inherits it, through list rows, cards and scrolls; the screen root's `display` is the screen-wide default. The nearest container with a `display` wins for its subtree. A table's own `display` is merged over what it inherits, key by key: each of its `units` and `kinds` entries replaces the inherited entry for the same unit or kind, and every other entry is inherited. `display` on any other node is ignored. The resolution order and the notice below use the merged profile.
- A column's format is decided in this order: its `columns` override's `format`; else the profile's `units` entry for its `unit`; else the profile's `kinds` entry for its `kind`; else none, the plain figure. A unit with no entry falls through to the kind, so `unit: "1", kind: "ratio"` takes `kinds.ratio`. A text column (`type` or `kind` `text`) is drawn as text with no format.
- The table gives one notice, on screen and in its `/tree` `notices`, naming only what nothing covered: the units of columns drawn as plain figures because neither an override, a unit entry nor a kind entry covered them, and the kinds of such columns that have no unit. "table 'bom': no display entry for unit(s) 'kg*mm/second^2', 'joule'; drawn as plain figures". A table that covers every column gives none.
- `show_unit` (default true): the header shows the entry's unit label after the column label: the entry's `label`, else an object format's `suffix` trimmed (" lbf" shows "Force (lbf)"), else nothing (a named format such as `usd` adds nothing unless it has a `label`).
- `columns`: overrides by column id, `{ "format", "label", "hidden", "align", "width" }`. An override `label` is the whole header, as written. Numeric columns align right and others left unless `align` says otherwise. `width` (dp number) draws that column, its head and every row's cell, at that fixed width; the other columns keep sharing the rest of the row (the label column two shares, each value column one). A cell's text wraps within that width when it is longer. A `width` of 0 or less, or one that is not a number, is ignored and named in the table's one notice: "table 'bom': column 'span' width must be a number of dp above 0, not '-3'; it is ignored.".
- `title`: defaults to the view's `label`; a string replaces it and `false` hides it.
- `show_label` (default true) shows each row's label as the first column, headed `label_header` (default empty).
- `missing` (default "–") is drawn for a null value; a column's `null_display` wins over it. A null-bound cell carries `"null": true` in `/tree`.
- `empty_text` is drawn when the view has no rows, as the node `<table id>/empty`.
- `on_row_tap`: an action dispatched when a row is tapped, with params in the row's scope as list rows have: `{row.id}`, `{row.label}`, `{row.object_ids}`, `{row.values.<column>}`.
- `flex` as any child of a column or row.
- `/tree` ids: the table `<id>`, its header row `<id>/head` and header cells `<id>/head/<column>` (the row label's header is `<id>/head/label`), each row `<id>/row/<i>` (zero-based) and each cell `<id>/row/<i>/label` and `<id>/row/<i>/<column>`. Cells are text nodes: they carry `text`, `null`, `ink` and `bounds`. A row with `on_row_tap` clicks by id.

```json
{ "id": "bom", "type": "table", "props": {
    "view": "{views.bom}", "display": "{display}", "label_header": "Item",
    "on_row_tap": { "action": "set_binding", "params": { "path": "board.picked", "value": "{row.object_ids}" } } } }
```

with a profile such as `{ "units": { "count": { "scale": 1, "decimals": 0 }, "usd": "usd", "usd/count": "usd", "mm": "feet_inches", "kg*mm/second^2": { "scale": 0.000224809, "suffix": " lbf", "decimals": 0 } }, "kinds": { "ratio": "percent" } }`.

A `row` with an `action` is tappable too, dispatching it like a card does.

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

## Plan

A `plan` draws shapes placed in millimetres, seen from above, with the largest y at the top. It holds no vocabulary of its own: the host names every style.

- `shapes`: a list of `{ id, kind, x_mm, y_mm, ... }`. `kind` is `rect` (`width_mm` east and `depth_mm` north from its corner), `line` (to `x2_mm`, `y2_mm`), `marker` (a small square at the point) or `label` (`text`, `align` of `start`, `center` (default) or `end` about the point, and `dx_dp`, `dy_dp` to nudge it right and down).
- `styles`: style name to `{ fill, stroke, weight, dash }`. Colours are `#rrggbb` or `#rrggbbaa`, `weight` is dp, `dash` is a bool. A shape's `style` names one; a shape with no style takes the theme's ink.
- `legend`: a list of `{ label, style }`, drawn as swatches under the plan.
- `empty_text`: drawn when no shape can be drawn.
- `selected`: a binding to a list of ids (`"{selection.ids}"`). A tap picks the line or point within 14 dp of it, else the smallest rect holding it, a second tap on it or a tap on nothing clears, and the list is written to the bound path, so a `plan` and a `volume` bound to one path agree. With no binding the drawing keeps its own selection. The picked thing is outlined in the theme's ink.
- `details`: id to `{ title, sections: [{ heading, rows: [{ label, value, note }] }] }`. When given, only ids it holds can be picked, and the selected one's card is drawn under the drawing (`/tree` node `<id>/detail`); `value` null reads as a dash and `note` is a quiet line under its row.
- The drawing is fitted to the available width with a margin, and takes the height of its window (at most 1.5 times its width).
- Shapes are painted in the order given. `/tree` has a node `<plan id>/<shape id>` for each shape, with its bounds, and `<plan id>/legend/<i>` for each legend text.
- A shape that cannot be drawn (a rect with no `width_mm`, an unknown `kind`, a point that is not a number) is not drawn and the plan prints a notice naming it. A shape whose `style` is not in `styles` is drawn plain and the plan names the style.

## Volume

A `volume` draws boxes in millimetres as one solid scene the person turns, slides and zooms. Like `plan`, it holds no vocabulary: the host names every style. Millimetres are x east, y north, z up.

- `boxes`: a list of `{ id, x_mm, y_mm, z_mm, width_mm, depth_mm, height_mm, rotation_deg, style, estimate }`. `x_mm`, `y_mm` is the centre of the footprint on the ground, `z_mm` the underside, and `rotation_deg` turns it anticlockwise seen from above. A `width_mm` or `depth_mm` of 0 is a vertical plane and both 0 is a vertical edge. An `estimate` of true hatches the box and dashes its outline: the host uses it for a size it was not given.
- `styles`: style name to `{ stroke, weight }`. The body is the stroke lightened and shaded by face. `legend`: a list of `{ label, style, hatched }`; `hatched` draws the swatch hatched. `empty_text` is drawn when nothing can be drawn.
- `fill`: true makes the drawing fill the height its parent gives (a `stack` child anchored `fill`), not 1.15 times its width, and puts "Reset view" over its top-right corner; the selected card is then left to the host. `bottom_inset_dp` leaves that many dp at the bottom for a panel drawn over it.
- `lighting: "sun"` lights the faces from a fixed sun in the south-west, casts soft ground shadows from every box whose size is known. Edges are a deeper tone of the style's `stroke`, which is the body colour.
- `step`: a number (usually a binding); each box's `stage` is the step it is put in place at. A box is a faint ghost until its stage, drawn with an amber outline at it, and drawn as normal after; a box with no `stage` stays a ghost. With no `step` every box is drawn as normal.
- `grade_mm`: the height in mm of the ground. A translucent soil plane is drawn there and a box that ends below it is drawn under the plane, seen through it; shadows fall on it. Without it the ground is at the lowest box.
- `detail_text`: a binding path the drawing writes the selected thing's card to, as lines (title, headings, `label: value` rows, notes), empty when nothing or several are selected. A `text` bound to it, with `visible` the same path, shows the tapped piece in a dock.
- `mode: "blueprint"` draws edges only (no faces or shadows) on `ground`, a colour; `lines` are `{id, from, to, style}` with `from` and `to` as `[x, y, z]` in mm, and `labels` are `{id, at, text, style}`, both styled from `styles`. A `plan` takes the same `ground`.
- `selected`: a binding to a list of ids (`"{selection.ids}"`). A tap (a press that does not move) picks the nearest box whose faces hold it, else one with an edge within 12 dp, a second tap on it or a tap on nothing clears, and the list is written to the bound path, so a `plan` and a `volume` bound to one path agree. With no binding the drawing keeps its own selection. The picked thing is outlined in the theme's ink.
- `details`: id to `{ title, sections: [{ heading, rows: [{ label, value, note }] }] }`. When given, only ids it holds can be picked, and the selected one's card is drawn under the drawing (`/tree` node `<id>/detail`); `value` null reads as a dash and `note` is a quiet line under its row.
- One finger or the left button turns the view; two fingers, a right-drag or Ctrl-drag slide it; a pinch or the wheel zooms. A "Reset view" line under the drawing returns to the first framing, from the south-west looking north-east.
- The drawing is at most 560 dp wide and 1.15 times its width tall. Boxes are painted far to near by their centres, so two boxes that interpenetrate may overlap wrongly.
- `/tree` has a node `<volume id>/<box id>` for each box with its projected bounds (so a drag shows as bounds that moved), `<volume id>/north`, `<volume id>/reset` and `<volume id>/legend/<i>`. A box that cannot be drawn is named in a notice and not drawn; a `style` not in `styles` is drawn plain and named.

## Chart

A `chart` draws series of numbers against a numeric x: lines, or areas stacked about zero, on a left and a right axis. It computes nothing about the data: it reads each series' value from each row by a path and draws it. The host names every series, axis and unit.

- `rows`: a list of objects, usually a binding (`"{season.steps}"`). `x`: the dotted path in each row to its x (time or any number). A row with no number at `x` is not drawn and one notice counts them. Rows are drawn in the order given.
- `window`: `{ from, to }` or `{ from, length }` (`to` is `from + length`); either bound may be missing; only rows whose x is inside it, ends included, are drawn. A `from` bound to a path with no value yet leaves every row in.
- `x_axis`: `{ every, as }`. A tick and a faint vertical line at each multiple of `every` inside the rows drawn, labelled by `as`: `number` (default) or `hour_of_day` (the x modulo 24 as `03:00`). With ticks the x end labels are not drawn; `every` that is not above zero, or an `as` it does not know, is named in a notice.
- `ink` and `muted`: `#rrggbb` for the axis numbers, legend and readout text and the grid (muted for the numbers), for a page whose ground the theme's ink does not suit; each defaults to the theme's.
- `series`: a list of `{ id, path, label, kind, axis, color, stack }`. `path` is the dotted path to the value in each row (`stocks.water_temperature_f`). `kind` is `line` (default) or `area`; `axis` is `left` (default) or `right`; `color` is `#rrggbb` (a series without one takes the next of a fixed palette); `label` defaults to `id`. A row with no number at a series' path breaks that series into two runs. A zero stacks on the side its neighbours are on (the nearest nonzero value before it, else after it, else above zero), so it keeps its place on the stack edge; an area that goes from above zero to below is drawn as two pieces that meet where the straight line between the two values crosses zero. A series that cannot be drawn (no id or path, a `kind` or `axis` it does not know, no number at its path in any row) is not drawn and a notice names it.
- `areas`: positive values stack upward from zero and negative values downward, in series order, among the areas on one axis with the same `stack` name (default `stack`). Each area fills the band between its stack's edges; a zero value is a band of no height. Lines are never stacked.
- `axes`: `{ left: { unit, label }, right: { unit, label } }`. An axis is drawn only when a series is on its side; it spans round tick values that enclose its series (and zero when it holds an area), with `unit` and `label` printed at its top and `unit` after each value read from a series on it. `x_label` is printed before the x end values and the hover's x.
- Hover: a pointer over the drawing (or a touch pressed on it) reads the row nearest it: a crosshair, a dot on each series, and under the drawing each series' own value, not the edge it is stacked to. The legend, axes, x ticks, plotted values and the hover are in the node's `chart` field of `/tree`; the legend, axis and readout texts also carry accessibility labels `<id>/legend/<i>`, `<id>/axis/...` and `<id>/readout/...`, which are not `/tree` nodes.
- `empty_text` is drawn when no series can be drawn.

## Audio and voice

Both run only where the host gives the renderer a media engine: the browser (wasmJs) does; desktop, Android and iOS draw a notice and read back `unsupported`. The mic and speech recognition need a secure page (HTTPS or localhost). Neither works with the screen locked except playback and its lock-screen controls.

`audio` plays one stored file (an `<audio>` element, so it keeps playing with the screen locked) and hands the lock screen's controls to the page through the Media Session.

- `source`: the URL. `title`, `artist`, `album`, `artwork` (a URL): what the lock screen shows. A source that changes is opened again.
- Two-way bindings: `position` (seconds), `playing` (bool), `rate` (number), and `duration` (seconds, written when known). The node writes `position` at most twice a second while it plays and writes `playing` when the player starts, pauses, ends or is refused; a write by anything else seeks, plays, pauses or changes the rate. A `position` already held when the length is known is where it resumes. Seek to a passage with a button's `set_binding` on `position`.
- `markers`: `[{ at, label }]` in seconds, kept in time order and read back; with markers the lock screen's previous and next go to the start of the passage being heard (or the one before it, within 3 seconds of its start) and the next one; without them they seek by `seek_step` (default 15 seconds, as seekbackward and seekforward do).
- `on_position`: an action told, with `position`, `rate`, `playing`, `duration` and `reason` (`play`, `pause`, `seek`, `rate`, `ended` or `tick`) added to its params, on each of those and every `report_every` seconds of narration while it plays (default 5).
- `media_actions`: `{ <lock-screen action>: { action, params } }` for `play`, `pause`, `previoustrack`, `nexttrack`, `seekbackward`, `seekforward`, `seekto`, `stop`. A named one is dispatched instead of its default, with its params resolved then (`"position": "{book.position}"`), so a spec can make a lock-screen button a reaction such as "lost me". A name it does not know is named in a notice.
- `show_title`: false hides the on-page title; `title` is still what the lock screen shows.
- `controls`: true (default) draws a play and pause button, minus and plus `seek_step` buttons, a seek bar, the time and a rate button (0.75 to 2). Browsers start sound only after a tap, and a page that sets `playing` before one is refused: the node reads back `blocked`, writes `playing` false and says to tap play.
- `/tree` node `audio` field: `source, status, position, duration, playing, rate, markers, media_actions`; `status` is `idle`, `loading`, `ready`, `blocked`, `failed` or `unsupported`.

`voice` is one large control that captures speech.

- `mode`: `hold` (default: listens while pressed) or `toggle`. `label` (default "Hold to talk" or "Tap to talk"). `lang`.
- `capture`: `text` (default; the browser's speech recognition, which on Chrome sends the audio to the browser vendor's service) or `audio` (records and POSTs the recording to `upload_url`). Only one is used, because a phone's mic can serve one capture at a time.
- `on_capture`: an action told when listening ends with something to tell, with `text`, `confidence`, `ms`, `uploaded` and `bytes` added to its params. Listening that ends with nothing heard tells nothing.
- Bindings written: `listening` (bool) and `transcript` (the last words heard).
- `/tree` node `voice` field: `label, mode, capture, status, listening, transcript`; `status` is `ready`, `listening`, `failed` or `unsupported`, and a failure (a refused mic, no HTTPS, no recognition) is in the node's notices.

A `button` with `size: "xl"` is at least 72 dp tall with a large label, for a thumb. In the stock browser host, an action the host does not know is POSTed as `{ action, params }` (numbers and booleans kept) to `/action` beside the spec URL (`/spec.json` becomes `/action`), or to the `action` query parameter. The server's JSON answer can write bindings: `{ "set": { "pushback.guide": "…", "pushback.audio": "/x.mp3" } }` writes each path in one step, as `set_binding` with `values` does, except that a text is stored as it is and never read as a binding. An empty answer writes nothing. A status outside 200 to 299, an answer that is not a JSON object, a `set` that is not an object or has an empty path, or a server that cannot be reached writes the reason to `renderer.error` and changes nothing else. There is one route per spec: a server branches on the action name.

## Stat

A `stat` is a card for one figure: a label, a big monospaced number, a unit and an optional status.

- `label`, `value`, `unit`: text. `value` is formatted as a `text` node formats its value (`format`, `missing`), so a figure the engine gave as null reads as the missing mark and the card is never empty.
- `status`: `pass`, `warn` or `fail` draws a bar in the status colour down the card's left edge and says the word under the figure ("Pass", "Warn", "Fail"), so colour is never the only signal. `status_text` words it differently ("Fits"). Any other status is named in a notice and the card is drawn without one.
- It reads back in `/tree` as one line of `text` (`label value unit`), `props.status_shown` and its `notices`.

## Theme and tokens

Every colour, size and step a node draws with comes from a theme, and a theme is a set of design tokens in the W3C Design Tokens format (DTCG): `design/dark.tokens.json` and `design/light.tokens.json` are the defaults, written by the code, and a test fails if they drift.

- Tokens: `color.ground`, `ink`, `muted`, `accent`, `on-accent`, `status.pass`, `status.warn`, `status.fail`, `series.1` to `series.8`; `elevation.surface`, `raised`, `overlay` (how far each surface steps from the ground toward the ink); `dimension.space.xs` to `xxl` and `gutter`; `dimension.radius.sm` to `xl`; `dimension.control.sm` (36), `md` (48), `lg` (56), `xl` (72); `type.display`, `heading`, `subheading`, `body`, `caption`, `label`, `mono` (DTCG typography: fontSize, fontWeight, lineHeight as a multiplier).
- Names for a colour in a spec (`background`, a text's `color`): `ground`, `surface`, `raised`, `overlay`, `ink`, `muted`, `accent`, `on_accent`, `pass`, `warn`, `fail`, or `#rrggbb`. A text `variant` is `display`, `heading`, `subheading`, `body`, `caption`, `label` or `mono`.
- A `button` is `size` `sm`, `md` (default), `lg` or `xl`: 36, 48, 56 and 72 dp, the first drawn that tall inside the platform's 48 dp touch target.
- A chart takes the series colours from `color.series` (eight, colour-blind safe, 3:1 on every ground in both themes) unless a series names its own.
- A user brings a theme as a DTCG file: `?tokens=<url>` or `localStorage["factoredui.theme"]` in the browser host. It declares `$extensions["ai.factoredui"].mode` (`dark` or `light`) and overrides only the tokens it names. A token with no type, the wrong type, a translucent colour, a value the renderer does not read, a text colour under 4.5:1 or a series colour under 3:1 on the ground, the surface or the raised surface, or one that would make a default colour unreadable, keeps its default; each reason is in `theme.notices`. A colour is a DTCG colour object (`colorSpace`, `components`, `hex`) or a bare hex string.

## Window size

The renderer writes the window's size class as bindings, so one spec lays out for a phone, a tablet and a desktop with `visible`: `window.class` (`compact` under 600 dp wide, `medium` from 600, `expanded` from 840), `window.is_compact`, `window.is_medium`, `window.is_expanded`, `window.is_at_least_medium`, `window.height_class` (`compact` under 480 dp, `medium` from 480, `expanded` from 900), `window.width` and `window.height` in dp.
## Reports data

The engine answers with its views response: `{ "views": [ { "id", "label", "columns": [ { "id", "label", "unit" } ], "rows": [ { "id", "label", "object_ids": [...], "values": { ... } } ] } ] }`. These named tables are not the stacked_scene's `view` prop. An older engine answered `{ "reports": [...] }`, which is still read; when both keys are present, `views` is used. This guide calls each table a report.

- `readReports(text)` reads it into `Report` and `ReportRow` (`ai.factoredui.compose.scene`).
- A value is a number, text or null. A text column's value is a string and binds as text; a number `format` on it draws "percent needs a number, not 'pine'". Null means the engine does not know it, never zero, and stays null.
- A value of another type, a row or column without an id, a non-object row and two reports sharing an id are refused with a `ReportsFormatException` naming where, under the key that was read (`views[0].rows[0].values.ok must be a number, text or null, not true`).
- `reportsState(reports)` keys the reports by id for the state map; each is `{ id, label, columns, rows }` and each row `{ id, label, object_ids, values }`. A row also carries `fractions` (column to number or null, never clamped: 1.3 stays 1.3), `flags` (column to text or null, such as `over` or `within`) and `limits` (column to number or null) when the engine gives them. A row without them has none of those keys. Columns carry `id`, `label` and `unit`, and `kind`, `type`, `null_display` and `bad` when the engine gives them, so a `table` bound to the state gets every column field it reads.
- Colour a cell by its fraction: `"background_ramp": { "value": "{row.fractions.use}", "ramp": {...} }`. Show a flag as text: `{row.flags.use}`. A fraction field that is not an object, or an entry that is not a number, is refused with the reader's error naming where it sits.
- A spec binds a report's rows directly: put `reportsState(...)` under `reports` and list `"data": "{reports.<id>.rows}"`, showing `{row.label}` and `{row.values.<column>}` with any text `format`. A null or absent value shows the text node's `missing`, which can itself be a binding (`"missing": "{board.unknown_reason}"`) so the reason comes from data.
- The library sums, prices and weighs nothing: totals, costs and weights are rows the host sends.
