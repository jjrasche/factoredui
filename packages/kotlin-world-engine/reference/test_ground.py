import json
import math
import sys
from pathlib import Path

import pytest

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

import engine_ref as engine  # noqa: E402
import import_ground_from_dem as converter  # noqa: E402
import validate  # noqa: E402

GROUND_WORLD = HERE / "conformance" / "worlds" / "ground-plot.frozen.world.json"
PARCEL_WORLD = HERE / "conformance" / "worlds" / "parcel-five-acre.frozen.world.json"
DEMO_WORLD = HERE / "worlds" / "parcel-ground-demo.world.json"
BASE = [1300] * 5 + [1200] * 5 + [1100, 1100, 900, 1100, 1100] + [1000] * 5
STAMP = "2026-10-05T00:00:00Z"
needs_dem = pytest.mark.skipif(not converter.DEFAULT_DEM.exists(), reason=f"{converter.DEFAULT_DEM} is not on this machine")


def ground_log() -> engine.Log:
    return engine.Log(engine.load_world(GROUND_WORLD))


def dig(log: engine.Log, col: int, row: int, depth, branch: str = "main"):
    return log.attempt(branch, "jim", "dig", {"col": col, "row": row, "depth_mm": depth}, STAMP)


def vertex(heights: list, vc: int, vr: int, vertex_cols: int = 5):
    return heights[vr * vertex_cols + vc]


# ---------------------------------------------------------------- the engine


def test_a_world_without_ground_keeps_its_snapshot_unchanged():
    state = engine.Log(engine.load_world(PARCEL_WORLD)).state_of()
    assert "ground" not in state.snapshot()


def test_the_seed_surface_is_version_zero_with_row_zero_north():
    state = ground_log().state_of()
    assert state.ground == BASE and state.ground_version == 0
    assert vertex(state.ground, 2, 2) == 900


def test_a_dig_lowers_each_of_the_four_corners_exactly_once():
    log = ground_log()
    dig(log, 1, 0, 200)
    heights = log.state_of().ground
    changed = {(vc, vr) for vr in range(4) for vc in range(5) if vertex(heights, vc, vr) != vertex(BASE, vc, vr)}
    assert changed == {(1, 0), (2, 0), (1, 1), (2, 1)}
    assert all(vertex(BASE, vc, vr) - vertex(heights, vc, vr) == 200 for vc, vr in changed)


def test_a_refused_dig_leaves_the_log_byte_identical():
    log = ground_log()
    before = json.dumps(log.dump())
    refusal = dig(log, 1, 1, 700)
    assert isinstance(refusal, engine.Refusal) and refusal.rule == "dig-floor"
    assert json.dumps(log.dump()) == before


def test_version_counts_every_height_change_including_a_revert():
    log = ground_log()
    first = dig(log, 2, 0, 100)
    log.revert(first["id"], "main", "jim", STAMP)
    state = log.state_of()
    assert state.ground == BASE and state.ground_version == 2


def test_replay_reproduces_heights_and_version():
    log = ground_log()
    dig(log, 1, 0, 50.5)
    log.attempt("main", "jim", "raise", {"col": 3, "row": 2, "height_mm": 12}, STAMP)
    replayed = engine.Log.load(engine.load_world(GROUND_WORLD), log.dump())
    assert replayed.state_of().snapshot() == log.state_of().snapshot()
    assert replayed.state_of().snapshot()["ground"]["version"] == 2


def test_ground_never_changes_tile_counts_or_areas():
    log = ground_log()
    before = engine.report_outputs(log.world, log.state_of())
    dig(log, 3, 2, 100)
    after = engine.report_outputs(log.world, log.state_of())
    assert after["counts"] == before["counts"] and after["areas"] == before["areas"]


def test_cut_fill_is_current_minus_base():
    log = ground_log()
    dig(log, 0, 0, 40)
    props = engine.render_props(log.world, log.state_of())
    assert props["ground_base"]["heights_mm"] == BASE
    assert props["ground_base"]["cut_fill_mm"] == [c - b for c, b in zip(props["ground"]["heights_mm"], BASE)]
    assert min(props["ground_base"]["cut_fill_mm"]) == -40


def test_a_tilted_plane_reads_its_own_gradient_on_either_triangle():
    side = 3048.0
    corners = {"nw": 0.0, "ne": 30.0, "sw": -40.0, "se": -10.0}
    assert math.isclose(engine.tile_slope_pct(corners["nw"], corners["ne"], corners["sw"], corners["se"], side),
                        100 * math.hypot(30, 40) / side)


def test_ground_problems_name_the_size_and_the_range():
    world = engine.World(GROUND_WORLD)
    world.ground = dict(world.ground, heights_mm=BASE[:19])
    assert any("19 heights" in problem for problem in engine.ground_problems(world))
    world.ground = dict(world.ground, heights_mm=[6000000] + BASE[1:])
    assert any("outside -500000 to 5000000" in problem for problem in engine.ground_problems(world))


def test_load_world_refuses_a_ground_of_the_wrong_length(tmp_path):
    document = json.loads(GROUND_WORLD.read_text(encoding="utf-8"))
    document["ground"]["heights_mm"] = [1250] * 12
    path = tmp_path / "per-tile.world.json"
    path.write_text(json.dumps(document), encoding="utf-8")
    with pytest.raises(engine.WorldError, match="ground"):
        engine.load_world(path)


# ---------------------------------------------------------------- the converter


def tilted_plane(rows: int, cols: int, cell_m: float, row0: str) -> list[list[float]]:
    """Cell centre at (c + 0.5, r + 0.5) cells from the south-west corner (south-up row r): h = 200 + 0.01 x + 0.02 y metres."""
    south_up = [[200 + 0.01 * (c + 0.5) * cell_m + 0.02 * (r + 0.5) * cell_m for c in range(cols)] for r in range(rows)]
    return south_up if row0 == "south" else list(reversed(south_up))


def plane_world(cols: int = 3, rows: int = 2, tile_ft: float = 10) -> dict:
    return {"id": "plane", "grid": {"cols": cols, "rows": rows, "tile_ft": tile_ft, "shape": "square"},
            "frame": {"epsg": 26916, "origin_east_m": 1000.0, "origin_north_m": 2000.0, "x_axis": "east", "y_axis": "north"}}


@pytest.mark.parametrize("row0", ["south", "north"])
def test_bilinear_sampling_reproduces_a_tilted_plane_at_every_vertex(row0):
    dem = tilted_plane(rows=20, cols=20, cell_m=1.0, row0=row0)
    world = plane_world()
    ground = converter.build_ground(world, dem, dem_origin=(995.0, 1995.0), cell_m=1.0, dem_row0=row0,
                                    datum="NAVD88", source="synthetic plane", null_reason="synthetic")
    tile_m = 10 * 0.3048
    expected = [round((200 + 0.01 * (1000.0 + vc * tile_m - 995.0) + 0.02 * (2000.0 + (2 - vr) * tile_m - 1995.0)) * 1000)
                for vr in range(3) for vc in range(4)]
    assert ground["heights_mm"] == expected


def test_the_converter_writes_whole_millimetres_and_the_ground_block():
    ground = converter.build_ground(plane_world(), tilted_plane(20, 20, 1.0, "south"), dem_origin=(995.0, 1995.0), cell_m=1.0,
                                    dem_row0="south", datum="NAVD88", source="synthetic plane", null_reason="synthetic")
    assert all(isinstance(height, int) for height in ground["heights_mm"])
    assert ground["unit"] == "mm" and ground["datum"] == "NAVD88"
    assert ground["error"] == {"vertical_mm": {"value": None, "null_reason": "synthetic"}}


def test_a_world_reaching_past_the_dem_is_refused():
    with pytest.raises(ValueError, match="outside the DEM"):
        converter.build_ground(plane_world(cols=10), tilted_plane(5, 5, 1.0, "south"), dem_origin=(995.0, 1995.0), cell_m=1.0,
                               dem_row0="south", datum="NAVD88", source="s", null_reason="n")


# ---------------------------------------------------------------- the demo world


def test_the_demo_world_validates_and_is_one_of_five():
    report = validate.run_validation(HERE / "worlds")
    assert "parcel-ground-demo.world.json" in report["valid"] and report["invalid"] == []


def test_the_demo_world_holds_a_low_basin_and_a_tilt():
    world = engine.load_world(DEMO_WORLD)
    heights, vertex_cols = world.ground["heights_mm"], world.cols + 1
    low = min(range(len(heights)), key=heights.__getitem__)
    vc, vr = low % vertex_cols, low // vertex_cols
    assert 0 < vc < world.cols and 0 < vr < world.rows
    north_mean = sum(heights[:vertex_cols]) / vertex_cols
    south_mean = sum(heights[-vertex_cols:]) / vertex_cols
    assert north_mean != south_mean


@needs_dem
def test_the_committed_demo_world_is_what_the_converter_builds_today():
    committed = DEMO_WORLD.read_text(encoding="utf-8").replace("\r\n", "\n")
    assert converter.render_world(converter.build_demo_world()) == committed
