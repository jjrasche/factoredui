import copy
import json
import shutil
import subprocess
import sys
from decimal import Decimal
from fractions import Fraction
from pathlib import Path

import pytest

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

import engine_ref as engine  # noqa: E402
import run_conformance  # noqa: E402
import validate  # noqa: E402

PLOT_WORLD = HERE / "conformance" / "worlds" / "instances-plot.frozen.world.json"


def write_plot_world(tmp_path: Path, replacements: dict[str, str]) -> Path:
    """The frozen instances plot with literal text swapped, so a number reaches the engine exactly as written."""
    text = PLOT_WORLD.read_text(encoding="utf-8")
    for old, new in replacements.items():
        assert text.count(old) == 1, old
        text = text.replace(old, new)
    path = tmp_path / "instances-plot.world.json"
    path.write_text(text, encoding="utf-8")
    return path


# ---------------------------------------------------------------- 1. exact decimals


def test_a_21_digit_footprint_mm_spans_the_tiles_its_decimal_spans(tmp_path):
    path = write_plot_world(tmp_path, {
        '"tile_ft": 10': '"tile_ft": 25',
        '"footprint": [2, 2], "footprint_mm": [6000, 4500]': '"footprint": [2, 1], "footprint_mm": [7620.00000000000000001, 7620]',
    })
    world = engine.load_world(path)
    assert engine.derived_footprint(world, "house") == [2, 1]


def test_a_21_digit_tile_ft_is_the_decimal_as_written(tmp_path):
    path = write_plot_world(tmp_path, {'"tile_ft": 10': '"tile_ft": 9.99999999999999999999'})
    world = engine.World(path)
    assert world.tile_mm() == Fraction("9.99999999999999999999") * Fraction("304.8")


def test_a_world_number_keeps_its_written_decimal_through_a_state_copy(tmp_path):
    path = write_plot_world(tmp_path, {'"x_mm": 1500': '"x_mm": 1500.00000000000000000001'})
    state = engine.World(path).seed_state().copy()
    copied = copy.deepcopy(state).instance_layer["m-oak-1"]["x_mm"]
    assert engine.exact(copied) == Fraction("1500.00000000000000000001")
    assert copied == 1500.0


def test_read_json_keeps_integers_as_integers():
    document = engine.read_json('{"cols": 8, "tile_ft": 10.5}')
    assert type(document["cols"]) is int and document["tile_ft"] == 10.5


# ---------------------------------------------------------------- 2. the exponent bound


@pytest.mark.parametrize("text", ["1e400", "1e-400", "1" + "0" * 400, "0." + "0" * 399 + "1", "-1e400"])
def test_a_number_whose_exponent_is_at_most_400_reads(text):
    assert engine.read_json(f"[{text}]")[0] == json.loads(text)


@pytest.mark.parametrize("text", ["1e401", "1e-401", "1" + "0" * 401, "1e999999999", "-1e999999999", "1e99999999999999999999"])
def test_a_number_whose_exponent_passes_400_is_refused_by_name(text):
    with pytest.raises(engine.WorldError, match="number-exponent-too-large"):
        engine.read_json(f'{{"value": {text}}}')


def test_the_exponent_refusal_does_not_print_a_402_digit_number():
    with pytest.raises(engine.WorldError) as refusal:
        engine.read_json("[1" + "0" * 401 + "]")
    assert "0" * 40 not in str(refusal.value)


def test_load_world_refuses_a_world_holding_1e999999999(tmp_path):
    path = write_plot_world(tmp_path, {'"height_mm": 16500': '"height_mm": 1e999999999'})
    with pytest.raises(engine.WorldError, match="number-exponent-too-large"):
        engine.load_world(path)


@pytest.mark.parametrize("text", ["1" + "0" * 401, "0." + "0" * 400 + "1"])
def test_an_expression_literal_past_the_exponent_bound_is_refused(text):
    with pytest.raises(engine.ExprError) as refusal:
        engine.parse_expression(text)
    assert refusal.value.kind == "number-exponent-too-large"


# ---------------------------------------------------------------- 3. the footprint cap


def test_load_world_refuses_a_footprint_over_two_million_tiles_without_printing_its_size(tmp_path):
    path = write_plot_world(tmp_path, {'"footprint_mm": [2400, 3100]': '"footprint_mm": [1e400, 3100]'})
    with pytest.raises(engine.WorldError) as refusal:
        engine.load_world(path)
    message = str(refusal.value)
    assert "footprint-too-large" in message
    assert "0" * 20 not in message and len(message) < 400


def test_the_validator_reports_a_huge_footprint_once_and_without_its_size(tmp_path):
    copy_dir = tmp_path / "worlds"
    shutil.copytree(HERE / "worlds", copy_dir)
    validate.apply_mutation(copy_dir, {"world": "parcel-lidar-sample.world.json", "edits": [
        {"op": "set", "path": ["object_types", {"id": "shed"}, "footprint_mm"], "value": [Decimal("1e400"), 3000]}]})
    fired = validate.run_validation(copy_dir)["fired"]
    assert [f["rule"] for f in fired] == ["footprint-too-large"]
    assert "0" * 20 not in fired[0]["message"]


def test_a_footprint_of_exactly_two_million_tiles_is_allowed_and_one_row_more_is_not(tmp_path):
    path = write_plot_world(tmp_path, {'"footprint_mm": [2400, 3100]': '"footprint_mm": [6096000, 3048000]'})
    assert not engine.is_footprint_too_large(engine.load_world(path), "shed")
    path = write_plot_world(tmp_path, {'"footprint_mm": [2400, 3100]': '"footprint_mm": [6096000, 3048000.001]'})
    assert engine.is_footprint_too_large(engine.World(path), "shed")


# ---------------------------------------------------------------- 4. duplicate event ids


def placed_then_branched_log() -> dict:
    log = engine.Log(engine.load_world(PLOT_WORLD))
    log.attempt("main", "jim", "place", {"type": "garden", "col": 0, "row": 0}, "2026-10-05T00:00:00Z")
    log.branch("side", "main", "jim", "2026-10-05T00:00:01Z")
    return log.dump()


def test_the_loader_refuses_a_branch_event_reusing_an_id():
    document = placed_then_branched_log()
    document["events"][1]["id"] = "e1"
    with pytest.raises(engine.WorldError, match="event-id-duplicate"):
        engine.Log.load(engine.load_world(PLOT_WORLD), document)


def test_replay_itself_refuses_a_branch_event_reusing_an_id():
    document = placed_then_branched_log()
    log = engine.Log(engine.load_world(PLOT_WORLD))
    log.replay(document["events"][0])
    duplicate = dict(document["events"][1], id="e1")
    with pytest.raises(engine.WorldError, match="event-id-duplicate"):
        log.replay(duplicate)
    assert log.by_id["e1"]["action"] == "place"


def test_an_action_after_loading_a_log_whose_ids_skip_takes_a_fresh_id():
    live = engine.Log(engine.load_world(PLOT_WORLD))
    live.attempt("main", "jim", "place", {"type": "garden", "col": 0, "row": 0}, "2026-10-05T00:00:00Z")
    live.attempt("main", "jim", "place", {"type": "garden", "col": 1, "row": 0}, "2026-10-05T00:00:01Z")
    document = live.dump()
    document["events"][1]["id"] = "e3"
    loaded = engine.Log.load(engine.load_world(PLOT_WORLD), document)
    placed = loaded.attempt("main", "jim", "place", {"type": "garden", "col": 2, "row": 0}, "2026-10-05T00:00:02Z")
    branched = loaded.branch("side", "main", "jim", "2026-10-05T00:00:03Z")
    ids = [event["id"] for event in loaded.events]
    assert (placed["id"], branched["id"]) == ("e4", "e5") and len(set(ids)) == len(ids)


# ---------------------------------------------------------------- 5. the validator self-test


def test_the_self_test_with_no_mutations_is_blind(capsys):
    exit_code = validate.print_self_test(HERE / "worlds", [])
    printed = capsys.readouterr().out
    assert exit_code == validate.EXIT_BLIND
    assert printed.splitlines()[-1] == "0 of 0 broken copies caught"


def test_the_self_test_refuses_to_run_over_starting_worlds_that_are_not_valid(tmp_path, capsys):
    copy_dir = tmp_path / "worlds"
    shutil.copytree(HERE / "worlds", copy_dir)
    validate.apply_mutation(copy_dir, {"world": "dungeon-tiny.world.json",
                                       "edits": [{"op": "set", "path": ["grid", "cols"], "value": "twelve"}]})
    exit_code = validate.print_self_test(copy_dir, validate.read_mutations()[:2])
    printed = capsys.readouterr().out
    assert exit_code == 1
    assert "world-schema" in printed and printed.splitlines()[-1] == "0 of 2 broken copies caught"


def test_a_mutation_that_breaks_nothing_is_missed(capsys):
    no_op = {"rule": "world-schema", "world": "dungeon-tiny.world.json",
             "edits": [{"op": "set", "path": ["grid", "cols"], "value": 12}]}
    exit_code = validate.print_self_test(HERE / "worlds", [no_op])
    assert exit_code == 1
    assert capsys.readouterr().out.splitlines()[-2:] == ["MISSED world-schema", "0 of 1 broken copies caught"]


def test_a_rule_already_firing_on_the_starting_worlds_is_not_caught_by_a_mutation():
    mutation = {"rule": "world-schema", "world": "a.world.json"}
    fired = {"blind": [], "fired": [{"rule": "world-schema", "world": "a.world.json", "message": ""}],
             "invalid": ["a.world.json"]}
    clean = {"blind": [], "fired": [], "invalid": []}
    assert validate.is_mutation_caught(mutation, fired, clean)
    assert not validate.is_mutation_caught(mutation, fired, fired)


def test_a_rule_firing_only_on_another_world_does_not_catch_the_mutation():
    mutation = {"rule": "link-resolves", "world": "locality-stub.world.json"}
    mutated = {"blind": [], "fired": [{"rule": "link-resolves", "world": "parcel-five-acre.world.json", "message": ""}],
               "invalid": ["parcel-five-acre.world.json"]}
    assert not validate.is_mutation_caught(mutation, mutated, {"blind": [], "fired": [], "invalid": []})


def test_a_mutation_keeps_a_21_digit_decimal_exactly(tmp_path):
    copy_dir = tmp_path / "worlds"
    shutil.copytree(HERE / "worlds", copy_dir)
    validate.apply_mutation(copy_dir, {"world": "dungeon-tiny.world.json",
                                       "edits": [{"op": "set", "path": ["grid", "tile_ft"], "value": Decimal("9.99999999999999999999")}]})
    assert '"tile_ft": 9.99999999999999999999' in (copy_dir / "dungeon-tiny.world.json").read_text(encoding="utf-8")


def test_mutations_json_is_read_with_its_decimals_as_written():
    values = json.dumps([m["edits"] for m in validate.read_mutations()], default=str)
    assert "1E+999999999" in values


# ---------------------------------------------------------------- 6. mutant timeouts


def test_a_mutant_whose_run_times_out_is_reported_as_timeout_not_killed(monkeypatch):
    def time_out(command, **options):
        raise subprocess.TimeoutExpired(command, options.get("timeout"))
    monkeypatch.setattr(run_conformance.subprocess, "run", time_out)
    mutant = run_conformance.read_mutants()[0]
    verdict, detail = run_conformance.run_mutant(mutant)
    assert verdict == "timeout" and "600" in detail


def test_a_timed_out_mutant_is_counted_apart_and_fails_the_run(monkeypatch, capsys):
    mutants = [{"id": "a", "breaks": "a"}, {"id": "b", "breaks": "b"}]
    monkeypatch.setattr(run_conformance, "read_mutants", lambda: mutants)
    monkeypatch.setattr(run_conformance, "run_cases", lambda engine_path: 0)
    verdicts = {"a": ("killed", "by x"), "b": ("timeout", "no answer within 600 s")}
    monkeypatch.setattr(run_conformance, "run_mutant", lambda mutant: verdicts[mutant["id"]])
    exit_code = run_conformance.run_mutants()
    lines = capsys.readouterr().out.splitlines()
    assert exit_code == 1
    assert "1 timed out" in lines and lines[-1] == "1 of 2 mutants killed"


def test_cases_keep_world_edit_decimals_and_read_expectations_as_floats():
    cases = {case["id"]: case for case in run_conformance.read_cases()}
    edits = cases["load-footprint-mm-21-digits-agrees"]["world"]["edits"]
    assert edits[2]["value"][0] == Decimal("7620.00000000000000001")
    assert isinstance(cases["expr-min-distance-of-two-instances-1-mm-apart"]["expect"]["value"], (int, float))


# ---------------------------------------------------------------- 7. min_distance_mm in float64


def test_min_distance_converts_to_float64_then_takes_the_root_of_the_summed_squares():
    first = [{"id": "a", "x_mm": 9500, "y_mm": 8000}]
    second = [{"id": "b", "x_mm": 9501, "y_mm": 8001}]
    assert engine.nearest_instance_gap_ft(first, second) == (1.0 * 1.0 + 1.0 * 1.0) ** 0.5 / 304.8


def test_coincident_instances_are_a_pair_at_distance_zero():
    pair = [{"id": "a", "x_mm": 9500, "y_mm": 8000}, {"id": "b", "x_mm": 9500, "y_mm": 8000}]
    assert engine.nearest_instance_gap_ft(pair, pair) == 0.0
