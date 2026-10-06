import json
import shutil
import sys
from pathlib import Path

import pytest

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

import validate  # noqa: E402

MUTATIONS = validate.read_mutations()


def test_the_shipped_worlds_are_all_valid_and_nothing_fires():
    report = validate.run_validation(HERE / "worlds")
    assert report["fired"] == [] and report["blind"] == []
    assert sorted(report["valid"]) == ["dungeon-tiny.world.json", "locality-stub.world.json", "parcel-five-acre.world.json",
                                       "parcel-lidar-sample.world.json"]


def test_every_rule_in_rules_json_has_a_broken_copy():
    assert {m["rule"] for m in MUTATIONS} == {r["id"] for r in validate.read_rules()}


@pytest.mark.parametrize("mutation", MUTATIONS, ids=[m["rule"] for m in MUTATIONS])
def test_each_broken_copy_trips_its_rule_and_invalidates_its_world(mutation):
    report = validate.run_mutation(mutation)
    tripped = {f["rule"] for f in report["fired"]} | {b["rule"] for b in report["blind"]}
    assert mutation["rule"] in tripped
    if mutation["world"]:
        assert mutation["world"] in report["invalid"]


def test_an_empty_worlds_directory_exits_blind_not_green(tmp_path, capsys):
    exit_code = validate.main(["--worlds", str(tmp_path)])
    printed = capsys.readouterr().out
    assert exit_code == validate.EXIT_BLIND
    assert "BLIND blind-worlds" in printed and "0 worlds valid" in printed


def write_parcel_with_support_equation(tmp_path, require: str, score_binding: bool) -> Path:
    copy_dir = tmp_path / "worlds"
    shutil.copytree(HERE / "worlds", copy_dir)
    path = copy_dir / "parcel-five-acre.world.json"
    document = json.loads(path.read_text(encoding="utf-8"))
    document["equations"].append({"id": "support_now", "expr": "projected_support('neighbor')", "unit": "1"})
    document["rules"][0]["require"] = require
    document["scoring"].append({"id": "support_copy", "label": "Support", "expr": "support_now", "unit": "1",
                                "binding": score_binding})
    path.write_text(json.dumps(document), encoding="utf-8")
    return copy_dir


def projection_findings(copy_dir: Path) -> list[str]:
    return [f["message"] for f in validate.run_validation(copy_dir)["fired"] if f["rule"] == "projection-not-binding"]


def test_a_projection_read_through_an_equation_by_a_rule_is_refused(tmp_path):
    copy_dir = write_parcel_with_support_equation(tmp_path, "support_now >= 0.5", score_binding=False)
    assert [m for m in projection_findings(copy_dir) if "rules.van-pad-needs-path" in m]


def test_a_projection_read_through_an_equation_by_a_binding_score_is_refused(tmp_path):
    copy_dir = write_parcel_with_support_equation(tmp_path, "neighbors(tile, 1 [tile], 'path') >= 1 [tile]", score_binding=True)
    assert [m for m in projection_findings(copy_dir) if "scoring.support_copy" in m]


def test_a_projection_read_through_an_equation_by_nothing_binding_passes(tmp_path):
    copy_dir = write_parcel_with_support_equation(tmp_path, "neighbors(tile, 1 [tile], 'path') >= 1 [tile]", score_binding=False)
    assert projection_findings(copy_dir) == []


def test_a_child_whose_parent_is_broken_reports_a_dangling_link(tmp_path):
    copy_dir = tmp_path / "worlds"
    shutil.copytree(HERE / "worlds", copy_dir)
    (copy_dir / "locality-stub.world.json").unlink()
    report = validate.run_validation(copy_dir)
    assert {"rule": "link-resolves", "world": "parcel-five-acre.world.json"} in [
        {"rule": f["rule"], "world": f["world"]} for f in report["fired"]
    ]
