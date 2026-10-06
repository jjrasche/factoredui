import json
import shutil
import sys
from pathlib import Path

import pytest

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

import engine_ref as engine  # noqa: E402

WORLDS = HERE / "worlds"
STAMP = "2026-10-05T12:00:00Z"


def parcel_world() -> engine.World:
    return engine.load_world(WORLDS / "parcel-five-acre.world.json")


def dungeon_world() -> engine.World:
    return engine.load_world(WORLDS / "dungeon-tiny.world.json")


def run_demo(world: engine.World) -> engine.Log:
    steps = json.loads(engine.demo_path(world).read_text(encoding="utf-8"))["steps"]
    return engine.run_script(world, steps)[0]


def place(log: engine.Log, type_id: str, col: int, row: int, branch: str = "main"):
    return log.attempt(branch, "jim", "place", {"type": type_id, "col": col, "row": row}, STAMP)


def lay_path(log: engine.Log, tiles) -> None:
    for col, row in tiles:
        assert not isinstance(place(log, "path", col, row), engine.Refusal)


def counts_on(log: engine.Log, branch: str = "main") -> dict:
    return engine.report_outputs(log.world, log.state_of(branch))["counts"]


# ---------------------------------------------------------------- scripted worlds


def test_parcel_demo_reaches_the_expected_counts_and_scores():
    log = run_demo(parcel_world())
    outputs = engine.report_outputs(log.world, log.state_of("main"))
    assert outputs["counts"] == {"paddock": 12, "hoop_house": 2, "commons_building": 4, "van_pad": 1,
                                 "path": 6, "pond": 2, "woodland_tree": 4}
    assert outputs["scoring"]["hoop_house_labor"] == pytest.approx(2 * 75.5 / (96 * 20) * 625)
    assert outputs["equations"]["pasture_yield"] == pytest.approx(12 * 625 / 43560 * 4.0)
    assert outputs["stocks"]["standing_forage"] == pytest.approx(12 * 625 / 43560 * 4.0)
    assert 0.0 < outputs["scoring"]["neighbor_support"] < 1.0


def test_dungeon_demo_reaches_the_expected_counts_and_scores():
    log = run_demo(dungeon_world())
    outputs = engine.report_outputs(log.world, log.state_of("main"))
    assert outputs["counts"] == {"wall": 10, "floor": 7, "door": 1, "monster": 1, "treasure": 2}
    assert outputs["scoring"]["treasure_per_monster"] == pytest.approx(2.0)
    assert outputs["scoring"]["guarded_treasure"] == pytest.approx(1.0)
    assert outputs["ticks"] == 10


def test_the_cli_demo_prints_the_van_pad_count(capsys):
    engine.main(["--world", str(WORLDS / "parcel-five-acre.world.json"), "--demo"])
    assert "count van_pad 1 tiles 625 sq_ft" in capsys.readouterr().out


# ---------------------------------------------------------------- event log


def test_replaying_a_saved_log_rebuilds_the_identical_state():
    world = parcel_world()
    original = run_demo(world)
    reloaded = engine.Log.load(parcel_world(), json.loads(json.dumps(original.dump())))
    for branch in original.heads:
        assert reloaded.state_of(branch).snapshot() == original.state_of(branch).snapshot()
    assert reloaded.dump() == original.dump()


def test_state_is_the_fold_of_the_chain_not_a_separate_store():
    log = run_demo(parcel_world())
    assert log.fold(log.heads["main"]).snapshot() == log.state_of("main").snapshot()


def test_a_refused_action_leaves_the_log_unchanged():
    log = engine.Log(parcel_world())
    lay_path(log, [(6, 0)])
    before = json.dumps(log.dump(), sort_keys=True)
    refusal = place(log, "van_pad", 10, 10)
    assert isinstance(refusal, engine.Refusal) and refusal.rule == "van-pad-needs-path"
    assert json.dumps(log.dump(), sort_keys=True) == before


def test_a_tampered_log_does_not_replay():
    original = run_demo(parcel_world())
    document = original.dump()
    refused_spot = next(e for e in document["events"] if e["action"] == "place" and e["parameters"]["type"] == "van_pad")
    refused_spot["parameters"]["col"], refused_spot["parameters"]["row"] = 10, 10
    with pytest.raises(engine.WorldError):
        engine.Log.load(parcel_world(), document)


def test_a_branch_isolates_its_changes_from_main():
    log = engine.Log(parcel_world())
    lay_path(log, [(6, 0)])
    log.branch("idea", "main", "jim", STAMP)
    place(log, "pond", 3, 20, branch="idea")
    assert counts_on(log, "idea")["pond"] == 1
    assert counts_on(log, "main")["pond"] == 0


def test_merge_applies_the_branch_events_to_the_target():
    log = engine.Log(parcel_world())
    log.branch("idea", "main", "jim", STAMP)
    place(log, "pond", 3, 20, branch="idea")
    place(log, "path", 6, 0)
    merged = log.merge("idea", "main", "jim", STAMP)
    assert not isinstance(merged, engine.Refusal)
    assert counts_on(log)["pond"] == 1 and counts_on(log)["path"] == 1


def test_merge_is_refused_when_both_branches_changed_the_same_tile():
    log = engine.Log(parcel_world())
    log.branch("idea", "main", "jim", STAMP)
    place(log, "pond", 3, 20, branch="idea")
    place(log, "paddock", 3, 20)
    refusal = log.merge("idea", "main", "jim", STAMP)
    assert isinstance(refusal, engine.Refusal) and refusal.rule == "merge-conflict"
    assert "3,20" in refusal.message


def test_merge_rechecks_rules_against_the_target_state():
    log = engine.Log(parcel_world())
    lay_path(log, [(6, 3)])
    log.branch("idea", "main", "jim", STAMP)
    assert not isinstance(place(log, "van_pad", 7, 3, branch="idea"), engine.Refusal)
    log.attempt("main", "jim", "remove", {"col": 6, "row": 3}, STAMP)
    refusal = log.merge("idea", "main", "jim", STAMP)
    assert isinstance(refusal, engine.Refusal) and refusal.rule == "van-pad-needs-path"
    assert counts_on(log)["van_pad"] == 0


def test_revert_of_a_place_removes_what_it_placed():
    log = engine.Log(parcel_world())
    placed = place(log, "pond", 3, 20)
    reverted = log.revert(placed["id"], "main", "jim", STAMP)
    assert not isinstance(reverted, engine.Refusal)
    assert counts_on(log)["pond"] == 0


def test_revert_of_a_remove_puts_the_object_back():
    log = engine.Log(parcel_world())
    place(log, "pond", 3, 20)
    removed = log.attempt("main", "jim", "remove", {"col": 3, "row": 20}, STAMP)
    log.revert(removed["id"], "main", "jim", STAMP)
    assert counts_on(log)["pond"] == 1


def test_revert_is_refused_when_a_later_event_touched_the_same_tile():
    log = engine.Log(parcel_world())
    placed = place(log, "pond", 3, 20)
    log.attempt("main", "jim", "remove", {"col": 3, "row": 20}, STAMP)
    place(log, "paddock", 3, 20)
    refusal = log.revert(placed["id"], "main", "jim", STAMP)
    assert isinstance(refusal, engine.Refusal) and refusal.rule == "revert-conflict"


def test_revert_is_checked_against_the_rules_like_any_action():
    log = engine.Log(parcel_world())
    lay_path(log, [(6, 3)])
    path_event = log.events[-1]["id"]
    place(log, "van_pad", 7, 3)
    refusal = log.revert(path_event, "main", "jim", STAMP)
    assert isinstance(refusal, engine.Refusal) and refusal.rule == "van-pad-needs-path"


def test_an_always_rule_refuses_a_removal_that_would_strand_a_van_pad():
    log = engine.Log(parcel_world())
    lay_path(log, [(6, 3)])
    place(log, "van_pad", 7, 3)
    refusal = log.attempt("main", "jim", "remove", {"col": 6, "row": 3}, STAMP)
    assert isinstance(refusal, engine.Refusal) and refusal.rule == "van-pad-needs-path"


# ---------------------------------------------------------------- proposals and agents


def proposal_log() -> engine.Log:
    log = engine.Log(parcel_world())
    log.branch("proposal", "main", "jim", STAMP, proposal=True)
    place(log, "pond", 3, 20, branch="proposal")
    return log


def test_a_proposal_does_not_merge_before_an_endorsement():
    log = proposal_log()
    refusal = log.merge("proposal", "main", "jim", STAMP)
    assert isinstance(refusal, engine.Refusal) and refusal.rule == "proposal-unendorsed"


def test_a_synthetic_agent_cannot_endorse_because_its_vote_is_a_projection():
    log = proposal_log()
    refusal = log.attempt("proposal", "neighbor-2", "endorse", {"weight_class": "nearby"}, STAMP)
    assert isinstance(refusal, engine.Refusal) and refusal.rule == "projection-not-binding"


def test_an_opted_in_agent_endorses_and_the_proposal_merges():
    log = proposal_log()
    log.attempt("main", "neighbor-2", "opt_in", {"agent_id": "neighbor-2", "attributes": {"van_aversion": 0}}, STAMP)
    log.branch("proposal-2", "main", "jim", STAMP, proposal=True)
    place(log, "pond", 4, 20, branch="proposal-2")
    endorsed = log.attempt("proposal-2", "neighbor-2", "endorse", {"weight_class": "nearby"}, STAMP)
    assert not isinstance(endorsed, engine.Refusal)
    assert not isinstance(log.merge("proposal-2", "main", "jim", STAMP), engine.Refusal)
    assert counts_on(log)["pond"] == 1


def test_an_endorsement_needs_a_vote_class_from_the_matchmaking_vocabulary():
    log = proposal_log()
    refusal = log.attempt("proposal", "jim", "endorse", {"weight_class": "landlord"}, STAMP)
    assert isinstance(refusal, engine.Refusal) and refusal.rule == "unknown-weight-class"


def test_projected_support_falls_when_van_pads_appear_for_a_van_averse_neighbour():
    log = engine.Log(parcel_world())
    for col, row in ((9, 12), (10, 12)):
        place(log, "paddock", col, row)
    before = engine.report_outputs(log.world, log.state_of())["scoring"]["neighbor_support"]
    log.attempt("main", "jim", "opt_in", {"agent_id": "neighbor-1", "attributes": {"wants_food": 0, "van_aversion": 1}}, STAMP)
    lay_path(log, [(6, 2)])
    place(log, "van_pad", 7, 2)
    after = engine.report_outputs(log.world, log.state_of())["scoring"]["neighbor_support"]
    assert after < before


# ---------------------------------------------------------------- rules, links and the clock


def test_a_hoop_house_needs_an_open_south_side():
    log = engine.Log(parcel_world())
    place(log, "woodland_tree", 3, 9)
    refusal = place(log, "hoop_house", 2, 8)
    assert isinstance(refusal, engine.Refusal) and refusal.rule == "hoop-house-open-south"


def test_the_inherited_setback_refuses_a_structure_at_the_lot_line():
    log = engine.Log(parcel_world())
    refusal = place(log, "hoop_house", 1, 8)
    assert isinstance(refusal, engine.Refusal) and refusal.rule == "structure-setback"


def test_the_parcel_level_override_relaxes_the_inherited_setback(tmp_path):
    shutil.copytree(WORLDS, tmp_path / "worlds")
    child = tmp_path / "worlds" / "parcel-five-acre.world.json"
    document = json.loads(child.read_text(encoding="utf-8"))
    document["links"][0]["parcel"] = "parcel-b"
    child.write_text(json.dumps(document), encoding="utf-8")
    log = engine.Log(engine.load_world(child))
    assert not isinstance(place(log, "hoop_house", 1, 8), engine.Refusal)
    assert isinstance(place(log, "commons_building", 0, 14), engine.Refusal)


def test_a_tick_advances_stocks_by_their_equations():
    log = engine.Log(parcel_world())
    for col in range(9, 12):
        place(log, "paddock", col, 12)
    log.attempt("main", "clock", "tick", {"n": 365}, STAMP)
    outputs = engine.report_outputs(log.world, log.state_of())
    assert outputs["ticks"] == 365
    assert outputs["stocks"]["standing_forage"] == pytest.approx(3 * 625 / 43560 * 4.0)


def test_a_tap_on_an_empty_tile_places_and_a_tap_on_the_same_use_removes():
    log = engine.Log(parcel_world())
    assert engine.action_for_tap(log.state_of(), 3, 20, "pond") == ("place", {"type": "pond", "col": 3, "row": 20})
    place(log, "pond", 3, 20)
    assert engine.action_for_tap(log.state_of(), 3, 20, "pond") == ("remove", {"col": 3, "row": 20})
    assert isinstance(engine.action_for_tap(log.state_of(), 3, 20, "paddock"), engine.Refusal)


def test_render_props_carry_the_tilemap_contract():
    log = run_demo(parcel_world())
    props = engine.render_props(log.world, log.state_of())
    assert {"cols", "rows", "shape", "view", "uses", "cells", "counts", "areas"} <= set(props)
    assert len(props["cells"]) == sum(props["counts"].values())


# ---------------------------------------------------------------- the expression language


def check_in_parcel(text: str, site: str = "equation"):
    world = parcel_world()
    return engine.check_expression(engine.parse_expression(text), engine.Scope(world, site))


@pytest.mark.parametrize("text, kind", [
    ("while(true)", "unknown_word"),
    ("count('path'); count('path')", "syntax"),
    ("{ count('path') }", "syntax"),
    ("import('os')", "unknown_word"),
    ("count(pasture_yield)", "syntax"),
    ("lambda", "unknown_word"),
])
def test_out_of_vocabulary_input_is_refused(text, kind):
    with pytest.raises(engine.ExprError) as refused:
        check_in_parcel(text)
    assert refused.value.kind == kind


def test_an_expression_over_the_node_bound_is_refused():
    with pytest.raises(engine.ExprError) as refused:
        engine.parse_expression(" + ".join(["1"] * (engine.MAX_NODES + 1)))
    assert refused.value.kind == "bound"


def test_an_expression_nested_past_the_depth_bound_is_refused():
    with pytest.raises(engine.ExprError) as refused:
        engine.parse_expression("(" * (engine.MAX_DEPTH + 1) + "1" + ")" * (engine.MAX_DEPTH + 1))
    assert refused.value.kind == "bound"


def test_a_self_referring_equation_is_a_cycle_the_world_refuses(tmp_path):
    shutil.copytree(WORLDS, tmp_path / "worlds")
    child = tmp_path / "worlds" / "parcel-five-acre.world.json"
    document = json.loads(child.read_text(encoding="utf-8"))
    document["equations"][0]["expr"] = "pasture_yield + 0 [ton/year]"
    child.write_text(json.dumps(document), encoding="utf-8")
    with pytest.raises(engine.WorldError, match="cycle"):
        engine.load_world(child)


def test_adding_unlike_units_is_refused():
    with pytest.raises(engine.ExprError) as refused:
        check_in_parcel("count('path') + tile_area")
    assert refused.value.kind == "unit_mismatch"


def test_units_convert_through_the_unit_table():
    world = parcel_world()
    evaluation = engine.Evaluation(world, world.seed_state())
    assert evaluation.value_of(engine.parse_expression("1 [acre] == 43560 [sq_ft]")) is True
    assert evaluation.value_of(engine.parse_expression("1 [year] == 365 [day]")) is True


def test_if_evaluates_only_the_branch_it_chooses():
    world = dungeon_world()
    evaluation = engine.Evaluation(world, world.seed_state())
    tree = engine.parse_expression("if(count('monster') > 0 [tile], count('treasure') / count('monster'), 0)")
    assert evaluation.value_of(tree) == 0
