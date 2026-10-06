import json
import sys
from decimal import Decimal
from pathlib import Path

import pytest

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

import engine_ref as engine  # noqa: E402
import import_plot_twin_trees as importer  # noqa: E402
import validate  # noqa: E402

PLOT_WORLD = HERE / "conformance" / "worlds" / "instances-plot.frozen.world.json"
LIDAR_WORLD = HERE / "worlds" / "parcel-lidar-sample.world.json"
needs_sample = pytest.mark.skipif(not importer.DEFAULT_SAMPLE.exists(), reason=f"{importer.DEFAULT_SAMPLE} is not on this machine")


def plot_log() -> engine.Log:
    return engine.Log(engine.load_world(PLOT_WORLD))


def place_oak(log: engine.Log, x_mm, y_mm, branch: str = "main"):
    return log.attempt(branch, "jim", "place_instance", {"type": "oak", "x_mm": x_mm, "y_mm": y_mm, "rotation_deg": 0},
                       "2026-10-05T00:00:00Z")


def value_in_plot(text: str, log: engine.Log | None = None):
    log = log or plot_log()
    return engine.Evaluation(log.world, log.state_of()).value_of(engine.parse_expression(text))


def sample() -> dict:
    return importer.read_sample(importer.DEFAULT_SAMPLE)


# ---------------------------------------------------------------- the importer and the lidar world


@needs_sample
def test_every_sample_tree_becomes_a_measured_instance_at_its_apex_return_millimetres():
    trees = sample()["trees"]
    seed = json.loads(LIDAR_WORLD.read_text(encoding="utf-8"))["seed"]
    assert [(e["parameters"]["x_mm"], e["parameters"]["y_mm"]) for e in seed] == \
           [(t["apex_return"]["east_mm"], t["apex_return"]["north_mm"]) for t in trees]
    assert {e["parameters"]["provenance"] for e in seed} == {"measured"}


@needs_sample
def test_the_frame_puts_each_tree_on_its_utm_coordinates():
    world = engine.load_world(LIDAR_WORLD)
    state = world.seed_state()
    for index, tree in enumerate(sample()["trees"]):
        record = state.instance_layer[f"tree-{index + 1:02d}"]
        assert abs(world.frame["origin_east_m"] + record["x_mm"] / 1000 - float(tree["apex_return"]["easting_utm16n_m"])) < 0.001
        assert abs(world.frame["origin_north_m"] + record["y_mm"] / 1000 - float(tree["apex_return"]["northing_utm16n_m"])) < 0.001


@needs_sample
def test_the_committed_lidar_world_is_what_the_importer_builds_today():
    built = importer.render_world(importer.build_world(sample(), importer.DEFAULT_SAMPLE.name))
    assert LIDAR_WORLD.read_text(encoding="utf-8").replace("\r\n", "\n") == built


def test_the_lidar_world_validates_and_holds_ten_measured_trees():
    report = validate.run_validation(HERE / "worlds")
    assert "parcel-lidar-sample.world.json" in report["valid"]
    state = engine.load_world(LIDAR_WORLD).seed_state()
    assert len(state.instance_layer) == 10
    assert {record["provenance"] for record in state.instance_layer.values()} == {"measured"}


def test_no_measured_tree_carries_an_error_number_without_a_source():
    seed = json.loads(LIDAR_WORLD.read_text(encoding="utf-8"))["seed"]
    figures = [figure for entry in seed for figure in entry["parameters"]["error"].values()]
    assert all(figure["value"] is None and figure["null_reason"] for figure in figures)


def test_a_crown_at_the_clip_is_stated_as_a_lower_bound():
    assert "at least 6000 mm" in importer.crown_reason(Decimal("6.0"))
    assert "at least" not in importer.crown_reason(Decimal("4.02"))


def test_metres_become_millimetres_in_exact_decimals():
    assert importer.metres_to_mm(Decimal("29.65")) == 29650
    assert importer.metres_to_mm(Decimal("0.0005")) == 0.5


# ---------------------------------------------------------------- the engine's instance layer


def test_a_footprint_mm_only_type_covers_the_ceiling_of_its_tiles():
    world = engine.load_world(PLOT_WORLD)
    assert engine.footprint_of(world, "shed") == [1, 2]


def test_a_refused_placement_leaves_the_log_byte_identical():
    log = plot_log()
    before = json.dumps(log.dump())
    refusal = place_oak(log, 5000, 4000)
    assert isinstance(refusal, engine.Refusal) and refusal.rule == "tree-clear-of-structures"
    assert json.dumps(log.dump()) == before


def test_the_clearance_rule_reads_the_new_tree_only_not_the_measured_ones():
    log = plot_log()
    assert not isinstance(place_oak(log, 20000, 15000), engine.Refusal)


def test_instances_never_change_tile_counts_or_areas():
    log = plot_log()
    place_oak(log, 20000, 15000)
    outputs = engine.report_outputs(log.world, log.state_of())
    assert set(outputs["counts"].values()) == {0} and set(outputs["areas"].values()) == {0}


@pytest.mark.parametrize("text, expected", [
    ("min_distance_mm('oak', 'house') >= 1 [mm]", None),
    ("min_distance_mm('oak', 'house') >= 1 [mm] and false", False),
    ("min_distance_mm('oak', 'house') >= 1 [mm] and true", None),
    ("min_distance_mm('oak', 'house') >= 1 [mm] or true", True),
    ("min_distance_mm('oak', 'house') >= 1 [mm] or false", None),
    ("not (min_distance_mm('oak', 'house') >= 1 [mm])", None),
])
def test_a_missing_pair_is_not_measured_and_logic_over_it_is_three_valued(text, expected):
    assert value_in_plot(text) is expected


def test_render_props_carry_units_instances_and_the_frame():
    log = plot_log()
    props = engine.render_props(log.world, log.state_of())
    assert props["units"] == "mm" and props["frame"]["epsg"] == 26916
    assert [i["id"] for i in props["instances"]] == ["m-oak-1", "m-shed-1"]
    assert set(props["instances"][0]) == set(engine.RENDERED_INSTANCE_FIELDS)


def test_a_removed_measured_instance_keeps_its_provenance_in_the_log_and_replays():
    log = plot_log()
    log.branch("idea", "main", "jim", "2026-10-05T00:00:00Z")
    removal = log.attempt("idea", "jim", "remove_instance", {"id": "m-oak-1"}, "2026-10-05T00:00:01Z")
    assert removal["removed_instance"]["provenance"] == "measured"
    assert removal["removed_instance"]["source"]["file"].endswith("instances-plot.frozen.world.json")
    replayed = engine.Log.load(engine.load_world(PLOT_WORLD), log.dump())
    assert replayed.state_of("idea").snapshot() == log.state_of("idea").snapshot()


def test_the_cli_reports_a_missing_pair_as_not_measured():
    world = engine.load_world(PLOT_WORLD)
    state = world.seed_state()
    state.instance_layer.pop("m-shed-1")
    lines = engine.format_outputs(world, engine.report_outputs(world, state))
    assert "equation nearest_tree_to_shed not-measured mm" in lines
