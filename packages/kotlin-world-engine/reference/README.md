# World engine

One engine, many worlds. A world file declares what can exist on a grid, the rules every action must pass, and the equations and scores computed from its state. Every expression is data, so a world can be shared like a Dungeons and Dragons module. `engine_ref.py` is the reference interpreter. It is the spec the Kotlin engine must match: same demo scripts, same counts, same refusals.

`python engine_ref.py --world worlds/parcel-five-acre.world.json --demo` · `python validate.py` (`--self-test` runs every broken copy) · `python -m pytest -q`

## Layers

renderer (factored-ui: draws state, reports taps) → engine (world file + event log: rules, equations, tick) → twin (the task model's equations and bindings, cited by id) → governance (branches as proposals, endorsements by vote class).

## The world file (`world.schema.json`)

`sprites` (closed generic set: flat, block, tree, arch, water, fence, plus extensions that name a generic base) · `object_types` (sprite, colour, footprint in tiles and/or `footprint_mm`, `height_mm`, tags, properties with unit, kind and source) · `grid` (cols, rows, tile_ft, shape) · `frame` (optional: EPSG code and the grid's south-west corner in metres, x east, y north) · `clock` · `rules` (on `place`, `remove` or `always`; a `require` with its refusal message, or a one-shot `effect`) · `equations` · `stocks` (state the tick advances) · `actions` (verb, parameters, the events it emits) · `agents` (attributes, a distance `weight`, a `utility`; `vote` is always `projection`) · `scoring` (`binding` marks scores that decide things) · `links` (parent locality, parcel id, the rules inherited) · `seed`.

A price, labour, yield, regulation or demographic figure must carry a twin id, a row id, or `placeholder` with a reason. The parcel's yield is binding `mi_grass_clover_tons_per_acre` (row `yield-mi-grass-clover-4.0t`, equation `eq-aux-stocking-static`). Hoop-house hours are 24.576823 a year per 625 sq ft tile: the four `uky-tunnel-labor-hours` tasks in `task_hours.jsonl` sum to 75.5 h for the study's 96 x 20 ft house, scaled by area (`twin-constants.json`). Scores are `labor_hours_total`, `capex_floor` (sourced prices only: fence energizer plus stock tank, $219.98) and `paddock_dm_yield`; 31 of 35 per-use constants are null with a stated reason, because the twin has no hoop-house, building, fence-per-foot or tree-stock price. `run_conformance.py` runs 164 language-neutral cases (frozen worlds guarded by sha256, plus live-parcel cases) and 24 mutants; a port must pass the same cases.

## Millimetres and instances

All new lengths are millimetres (1 ft = 304.8 mm); the grid keeps `tile_ft`. A type with only `footprint_mm` covers ceil(footprint_mm / (tile_ft × 304.8)) tiles per axis, computed exactly on the decimals as written; with both, they must agree (`footprint-mm-agrees`). **Instances** are objects at free millimetre positions that no tile snaps: x east and y north from the grid's south-west corner, the frame's origin when there is one. Measured instances are seed `place_instance` entries carrying `source` and a per-field `error` (`position_mm`, `height_mm`, `crown_radius_mm`, each a number with a source or null with `null_reason`); no rule is checked against them, because a rule refuses actions and a standing tree is not one. Proposed instances come only from `place_instance {type, x_mm, y_mm, rotation_deg}` and `remove_instance {id}`, which pass the rules like any action. Rules on those verbs bind `instance`, the acted-on record, so `min_distance_mm(instance, '#structure') >= 3000 [mm]` checks the new tree alone. Instances never change tile counts or areas. A merge is refused when both branches touched the same `instance:<id>`. `worlds/parcel-lidar-sample.world.json` holds ten lidar trees from plot-twin, built by `python import_plot_twin_trees.py <sample.json>`.

`count_instances(use)` is dimensionless; `min_distance_mm(a, b)` is the least centre-to-centre distance in the plane between two different instances, a length, and null when no pair exists. Null is not-measured: it propagates through arithmetic, comparison, min, max and an `if` condition, `and`/`or`/`not` are three-valued, a rule refuses only on false, and an equation that reads it reports null.

## Expression language

```
expr := or ; or := and ("or" and)* ; and := not ("and" not)* ; not := "not" not | compare
compare := sum (("<"|"<="|">"|">="|"=="|"!=") sum)? ; sum := term (("+"|"-") term)*
term := unary (("*"|"/") unary)* ; unary := "-" unary | atom
atom := NUMBER ("[" unit "]")? | 'string' | true | false | NAME | FN "(" expr ("," expr)* ")" | "(" expr ")"
FN := count | sum | neighbors | side | edge | distance | if | min | max | projected_support | count_instances | min_distance_mm
```

Names are built-ins (`tile`, `tile_area`, `now`, `tick_length`, and `instance` inside a place_instance or remove_instance rule), equation, stock and score ids, `type.property`, `self.attribute` (inside agents) and `<link alias>.property`. A use argument is a type id, `#tag` (after Minecraft's `#namespace:tag`) or `any`. Units are checked statically against a fixed unit table: `+`, `-` and comparisons need equal dimensions, while `*` and `/` combine them.

**Why it terminates.** (1) The grammar has no loop, no binding form and no function definition, so a parse is a finite tree. The parser refuses more than 256 nodes or a nesting depth over 24. (2) Evaluation is structural recursion that visits each node at most once, and `if` evaluates only its chosen branch. (3) A name expands to another expression only through equations, scores and agents. Load refuses any cycle in that graph, found by depth-first search, and memoises every name once it is computed. (4) Every built-in iterates over at most cols × rows tiles or the placed instances. The instance layer holds at most 5000 instances, enforced at load and on every `place_instance`, so `count_instances` visits at most 5000 and `min_distance_mm` at most 5000 × 5000 = 25,000,000 pairs. (5) A tick event carries a finite `n` ≤ 100,000 and evaluates each stock once per tick. So each evaluation costs at most nodes × names × max(cols × rows, 25,000,000) steps, and that bound comes from the world alone.

## Event log and versions (`events.schema.json`)

An event is `{id, parent, world, branch, actor, action, parameters, timestamp}`. The engine adds `touches`, plus `removed` on a remove. **State is the fold of the chain from the world's seed to a branch head.** Replay re-checks every rule and every governance decision, so a tampered log does not load.

- `branch(name, from)` opens a head at an event. **A proposal is a branch with `proposal: true`.**
- `merge(branch)` applies the source events the target does not yet hold. **Conflict rule:** the merge is refused if any of them touches a tile (or the clock, or an agent) that the target changed since the two diverged. Otherwise they are replayed against the target's state and refused if any rule fails there. A proposal merges only after at least one endorsement.
- `revert(event)` applies the inverse of a place or remove. It is refused if a later event touched the same tiles, and it is checked against the rules like any action.
- `endorse` carries a weight class from the matchmaking vocabulary: `on_site`, `nearby` or `supporting`. Those class weights are null there, so the engine tallies endorsements by class and never sums them. A synthetic agent's endorsement is refused. A refused action of any kind leaves the log byte-identical.

## The two worlds, and the locality

`parcel-five-acre` covers 13 × 26 tiles of 625 sq ft, with seven uses. A van pad needs a path on one of its sides, and this rule is checked `always`, so removing the path is refused too. A hoop house needs nothing tall directly south of it. Structures obey the setback inherited from `locality-stub` through the link to `parcel-a` (50 ft). In the locality, `parcel-b` overrides that setback to 25 ft. The parcel world has three synthetic neighbours, and their `projected_support` is a non-binding score. `dungeon-tiny` runs on the same engine with 5 ft squares: a door must join floor to floor, and its scores are treasure per monster and guarded treasure. Every number in all three worlds is cited or declared a placeholder.

## Contract with factored-ui

**In:** `on_tile_tap {col, row, use}`, which `action_for_tap` turns into place, remove or a refusal. **Out:** `render_props` returns `{cols, rows, shape, view, tile_area, uses[{id,label,color,sprite}], cells[{col,row,use}], counts, areas, units: "mm", instances[{id,type,x_mm,y_mm,z_mm,rotation_deg,height_mm,crown_radius_mm,provenance}]}`, plus `frame` when the world has one: the tilemap's props, and what a 3D reader or a print export places at real positions. The prototype on `tilemap-prototype` still applies the brush itself (`TileBrush.kt applyBrush`) and computes counts locally. Both have to move behind the engine: the renderer dispatches the tap, and the engine's `cells` and `counts` overwrite its own, so a refused tap never shows.

## Open questions, each with a recommendation

1. **Where the engine runs.** Recommendation: a Kotlin Multiplatform module beside the renderer that the renderer never imports. The Stage wires tap to engine to props, so it runs in the browser through Compose-wasm and can still live with the twin. The Kotlin build passes when it reproduces this interpreter's demo outputs and every mutation in `mutations.json`.
2. **The twin's JavaScript solvers.** Recommendation: port the closed forms into world expressions, as `pasture_yield` already is. Call the iterative solvers (ModVege growth, the greenhouse heat balance) through a declared solver signature: inputs, outputs and units, with a step bound the host enforces. Termination then rests on that bound, and the bound must be stated per solver. `standing_forage` is a placeholder until `eq-aux-growth` is wired this way.
3. **Hex grids.** The schema allows only `square` for now, because neighbours, sides and edges on hex tiles are not yet defined. Recommendation: offset-hex coordinates with six named directions, added as a schema version bump.
4. **What merges a proposal.** One real endorsement is the engine's floor. Recommendation: leave quorum and class weights to the bylaws as a world-level governance block once 3Cs publishes a vote shape, never as engine code.

Prior art read: [Minecraft predicates](https://minecraft.wiki/w/Predicate) (typed JSON conditions composed by `all_of`, `any_of` and `inverted`), [Dwarf Fortress raw files](https://dwarffortresswiki.org/index.php/Raw_file) (objects declared by type, templates applied by reference, the model for `links.inherit`), and [Tiled JSON maps](https://doc.mapeditor.org/en/stable/reference/json-map-format/) (orientation enum, flat tile layers, typed custom properties).
