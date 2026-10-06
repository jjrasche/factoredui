# World engine conformance

A port of `engine_ref.py` conforms when it passes every case in `cases/`. `python run_conformance.py` runs them against the reference (`--list` by kind, `--observe <id>` prints what the reference produces, `--mutants` proves each broken copy in `mutants/` fails at least one case).

## Loading a case

Each `cases/<id>.json` is `{id, kind, source, world, input, expect, notes}`. `source` is `hand` (the expectation was computed by hand, with the arithmetic in `notes`) or `generated` (recorded from the reference).

- **World.** Copy `../worlds/*.world.json` and `worlds/*.frozen.world.json` into one fresh directory, so a link's `parent` resolves beside its child. Then apply `world.edits` to the file named `world.edits_in`, or `world.file` when absent. Edits use `../mutations.json`'s path language: a string is a key, an integer an index, an object picks the list member whose fields equal it; ops are `set`, `delete`, `set_repeated` (`unit` x `times` + `tail`), `set_items` (a list of `times` copies of `template`, with `{i}` in every string replaced by 1..times), `write_text`, `delete_all_worlds`.
- **Frozen worlds.** Cases run against `worlds/*.frozen.world.json`: copies of the shipped worlds at commit 0d45a7f (the parcel's link points at the frozen locality), and `instances-plot`, written for the millimetre layer. Every `worlds/SHA256SUMS*` file holds sha256 lines, with CRLF read as LF; the runner refuses to run if any frozen world is unlisted or differs. Cases named `live-*` run against the live worlds and are counted separately.
- **Steps** (every kind that acts): `{do, on="main", actor="jim", parameters={}, timestamp}`. `do` is a world verb, `endorse`, `branch` (`name`, `from`, `proposal`), `merge` (`branch`, `into="main"`), `revert` (`event`, `on`) or `tap` (`col`, `row`, `brush`: the renderer's on_tile_tap). `raw: true` appends the verb as a plain action. The default timestamp of step i is `2026-10-05T00:MM:SSZ` with MM = i div 60 and SS = i mod 60. Event ids are `e<n>`, numbered across all branches; a refusal takes no id.

## Kinds

| kind | input | expect |
|---|---|---|
| `expression` | `text` (a string, or `[[segment, times], ...]` joined), `site` (`equation` default, `rule`, `instance_rule` (binds `instance`, never `tile`), `scoring`, `stock`, `agent`), `bindings` (`tile: [col, row]` binds the instance there; `self: agent_id`), `setup` steps | `{value, unit}`, `{value: true}`, or `{error}`: `syntax`, `unknown_word`, `unit_mismatch`, `bound` (over 256 nodes, or depth over 24 with the top level counted as 1), `cycle` (the world's name graph has one), `divide_by_zero` |
| `world_load` | none | `valid`, `first` (first rule of `../rules.json`, in file order, that fires on `world.file`), `fired` (every distinct rule that fires, in that order), or `blind` |
| `action_sequence`, `branching`, `clock`, `agents` | `steps` | `steps`: `{applied: id}` or `{refused: rule, message}`; `final`: per branch, `counts`, `areas`, `equations`, `stocks`, `scoring` (declared units), `ticks` |
| `report` | `steps`, `branch` (`main`), `expressions` (`[{text, site, unit}]`, evaluated with no tile bound) | `steps`; `render` (entries of `render_props`), `render_absent` (keys it must not carry), `outputs` (entries of the report, declared units), `expressions` (values stated in each entry's `unit`; `null` is not-measured). Objects compare the keys listed, lists in full |
| `replay` | `steps`, `tamper` (edits to the saved log) | `loads`; when true, the reloaded state of every branch equals the live run's byte for byte in canonical form, and equals `state` and `log` when given; when false, the refusal contains `reason_contains` |

## Comparison

- Every entry an expectation lists is compared; entries it leaves out are not. A step `message` is compared exactly when it is stated. Messages that render a list in a language-specific form are left out.
- Numbers match when |observed - expected| <= max(1e-9 x |expected|, 1e-12). `"inf"` means +infinity.
- An expression's value is compared in the expected `unit`, and its dimensions must equal that unit's.
- **Units.** `units.json` is the table. A quantity in unit u is stored as value x ratio in base dimensions (ft, lb, usd, hour, tile, head). A unit is `name(^n)` joined left to right by `*` and `/`; `1` is dimensionless. `+`, `-`, comparisons, `min`, `max` and the two branches of `if` need equal dimensions; `*` and `/` combine them. Outputs in `final` are stated in each item's declared unit.
- **Canonical form** (replay): JSON with keys sorted, no whitespace, strings as JSON escapes them with non-ASCII left as is, lists in order. A number is rounded half-even to 9 decimal places on its exact binary value, written in fixed point with trailing zeros and a trailing point removed, and `-0` written `0`; an integer is written as is. State is `{cells: [[col, row, type]] sorted, stocks (base units, 9 decimals), ticks, agents by id, endorsements}`, plus `instances` by id when the instance layer is non-empty: `{id, type, x_mm, y_mm, z_mm, rotation_deg, height_mm, crown_radius_mm, provenance, source, error}`, millimetres as given, null where absent.
- **Millimetres.** A footprint_mm-only type covers ceil(footprint_mm / (tile_ft x 304.8)) tiles, and an instance is on the grid when 0 <= x_mm < cols x tile_ft x 304.8 (likewise y with rows); both are exact on the decimals as written. Instance positions are x east, y north from the grid's south-west corner. `min_distance_mm` returns a length (base unit ft), is null with no pair of two different instances, and null propagates; `and`/`or`/`not` are Kleene; a rule refuses only on false. Measured (seed) instances pass no rule; the cap is 5000.

A run that finds no cases is BLIND and exits 1, as does any failure or a case set that leaves a function, a world rule, a `rules.json` rule or an engine refusal unexercised.
