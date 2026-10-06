import json
import sys
from pathlib import Path

import pytest

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

import derive_twin_constants as derive  # noqa: E402
import engine_ref as engine  # noqa: E402
import validate  # noqa: E402

CONSTANTS_PATH = HERE / "twin-constants.json"
PARCEL = HERE / "worlds" / "parcel-five-acre.world.json"
CONSTANT_PREFIX = "twin-constants.json#"
TILE_SQ_FT = 25 * 25
SQ_FT_PER_ACRE = 43560
HOOP_HOUSE_ROW_SQ_FT = 96 * 20
HOOP_HOUSE_ROW_HOURS = 10 + 3 + 40 + 22.5
HERD_MOVE_HOURS = 15 * 153 / 60
ENERGIZER_PLUS_TANK_USD = 129.99 + 89.99
FORAGE_LB_PER_TILE = 4.0 * 2000 * TILE_SQ_FT / SQ_FT_PER_ACRE

SOURCES_PRESENT = all((derive.DEFAULT_WORKTREES / relative).exists() for relative in derive.ROW_STORES.values())
needs_sources = pytest.mark.skipif(not SOURCES_PRESENT, reason="the task-model and research worktrees are not beside this one")


def committed_constants() -> dict:
    return json.loads(CONSTANTS_PATH.read_text(encoding="utf-8"))


def constant_fields(constants: dict):
    for use, record in constants["uses"].items():
        for field, basis_field, unit in derive.FIELDS:
            yield use, field, record[field], record[basis_field], unit


def cited_properties():
    world = json.loads(PARCEL.read_text(encoding="utf-8"))
    for object_type in world["object_types"]:
        for prop in object_type.get("properties", []):
            cited = prop.get("source", {}).get("twin", "")
            if cited.startswith(CONSTANT_PREFIX):
                use, field = cited.removeprefix(CONSTANT_PREFIX).split(".")
                yield object_type["id"], prop, use, field


def run_parcel(steps: list[dict]) -> dict:
    world = engine.load_world(PARCEL)
    log = engine.run_script(world, steps)[0]
    return engine.report_outputs(world, log.state_of("main"))["scoring"]


def place(type_id: str, col: int, row: int) -> dict:
    return {"do": "place", "parameters": {"type": type_id, "col": col, "row": row}}


# ---------------------------------------------------------------- every constant is traced


def test_every_use_of_the_parcel_world_has_a_constants_record():
    world = json.loads(PARCEL.read_text(encoding="utf-8"))
    assert sorted(committed_constants()["uses"]) == sorted(t["id"] for t in world["object_types"])


@pytest.mark.parametrize("use, field, value, basis, unit", list(constant_fields(committed_constants())))
def test_no_constant_is_a_bare_number(use, field, value, basis, unit):
    if value is None:
        assert basis.get("declared"), f"{use}.{field} is null without a declared reason"
    else:
        assert "declared" not in basis
        assert basis.get("arithmetic"), f"{use}.{field} shows no arithmetic"
        assert basis.get("rows") or basis.get("tasks"), f"{use}.{field} names no row and no task"


def test_figures_exist_only_where_a_row_supports_them():
    figures = {(use, field) for use, field, value, _basis, _unit in constant_fields(committed_constants()) if value is not None}
    assert figures == {("paddock", "hours_per_year_per_use"), ("paddock", "capex_usd_per_use"),
                       ("paddock", "yield_kg_dm_per_tile_per_year"), ("hoop_house", "hours_per_year_per_tile")}


def test_the_constants_show_the_hand_arithmetic():
    uses = committed_constants()["uses"]
    assert uses["hoop_house"]["hours_per_year_per_tile"] == pytest.approx(HOOP_HOUSE_ROW_HOURS / HOOP_HOUSE_ROW_SQ_FT * TILE_SQ_FT)
    assert uses["paddock"]["hours_per_year_per_use"] == pytest.approx(HERD_MOVE_HOURS)
    assert uses["paddock"]["capex_usd_per_use"] == pytest.approx(ENERGIZER_PLUS_TANK_USD)
    assert uses["paddock"]["yield_kg_dm_per_tile_per_year"] == pytest.approx(FORAGE_LB_PER_TILE * 0.45359237)


# ---------------------------------------------------------------- the derivation


@needs_sources
def test_the_committed_file_is_what_the_sources_derive_today():
    assert derive.render_json(derive.derive_constants(derive.DEFAULT_WORKTREES)) == CONSTANTS_PATH.read_text(encoding="utf-8")


@needs_sources
def test_the_derivation_is_deterministic():
    first = derive.render_json(derive.derive_constants(derive.DEFAULT_WORKTREES))
    second = derive.render_json(derive.derive_constants(derive.DEFAULT_WORKTREES))
    assert first == second


@needs_sources
def test_every_missing_price_was_searched_and_its_matches_reviewed():
    sources = derive.Sources(derive.DEFAULT_WORKTREES)
    assert len(sources.priced) > 0
    for use, spec in derive.NULL_CAPEX.items():
        assert sources.find_priced(spec["search"]) == sorted(spec["reviewed"]), (
            f"{use}: the priced rows matching /{spec['search']}/ changed; review them and update NULL_CAPEX")


@needs_sources
def test_the_price_search_sees_a_price_it_should():
    assert "cap-fence-energizer" in derive.Sources(derive.DEFAULT_WORKTREES).find_priced(r"fence charger")


# ---------------------------------------------------------------- the world reads the constants by id


def test_every_figure_the_world_cites_from_the_constants_equals_it():
    constants = committed_constants()
    kg_per_lb = constants["conversions"]["kg_per_lb"]
    cited = list(cited_properties())
    assert len(cited) == 4
    for type_id, prop, use, field in cited:
        value = constants["uses"][use][field]
        assert value is not None, f"{type_id}.{prop['name']} cites the null constant {use}.{field}"
        world_value = prop["default"] * kg_per_lb if prop["unit"].startswith("lb") else prop["default"]
        assert world_value == pytest.approx(value, rel=1e-12), f"{type_id}.{prop['name']} drifted from {use}.{field}"


def test_every_figure_the_constants_derive_is_read_by_the_world():
    cited = {(use, field) for _type_id, _prop, use, field in cited_properties()}
    derived = {(use, field) for use, field, value, _basis, _unit in constant_fields(committed_constants()) if value is not None}
    assert derived == cited


def test_the_world_still_validates():
    report = validate.run_validation(HERE / "worlds")
    assert report["invalid"] == [] and report["blind"] == []


# ---------------------------------------------------------------- scores a person can check by hand


def test_the_scripted_demo_scores_check_by_hand():
    demo = json.loads((HERE / "demos" / "parcel-five-acre.demo.json").read_text(encoding="utf-8"))["steps"]
    scoring = run_parcel(demo)
    hoop_house_tiles, paddock_tiles = 2, 12
    hoop_house_hours = hoop_house_tiles * HOOP_HOUSE_ROW_HOURS / HOOP_HOUSE_ROW_SQ_FT * TILE_SQ_FT
    assert hoop_house_hours == pytest.approx(49.153645833)
    assert scoring["hoop_house_labor"] == pytest.approx(hoop_house_hours)
    assert scoring["labor_hours_total"] == pytest.approx(hoop_house_hours + HERD_MOVE_HOURS)
    assert scoring["labor_hours_total"] == pytest.approx(87.403645833)
    assert scoring["capex_floor"] == pytest.approx(219.98)
    assert scoring["paddock_dm_yield"] == pytest.approx(paddock_tiles * FORAGE_LB_PER_TILE)
    assert scoring["paddock_dm_yield"] == pytest.approx(1377.410468)


def test_herd_move_hours_and_the_capex_floor_are_charged_once_not_per_tile():
    one = run_parcel([place("paddock", 9, 12)])
    three = run_parcel([place("paddock", 9, 12), place("paddock", 10, 12), place("paddock", 11, 12)])
    assert one["labor_hours_total"] == three["labor_hours_total"] == pytest.approx(HERD_MOVE_HOURS)
    assert one["capex_floor"] == three["capex_floor"] == pytest.approx(ENERGIZER_PLUS_TANK_USD)
    assert three["paddock_dm_yield"] == pytest.approx(3 * one["paddock_dm_yield"])


def test_removing_the_last_paddock_clears_its_hours_cost_and_yield():
    scoring = run_parcel([place("paddock", 9, 12), {"do": "remove", "parameters": {"col": 9, "row": 12}}])
    assert scoring["labor_hours_total"] == 0
    assert scoring["capex_floor"] == 0
    assert scoring["paddock_dm_yield"] == 0
