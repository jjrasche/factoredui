"""Run the conformance cases against engine_ref.py, or a broken copy of it: prints '<n> cases pass' and '<m> fail'.

Exit 0 when every case passes and every coverage set is covered; 1 on any failure, on a coverage gap, and on
BLIND (no cases found). --list prints the case ids by kind. --mutants proves every broken copy in
conformance/mutants fails at least one case.
"""
from __future__ import annotations

import argparse
import hashlib
import importlib.util
import json
import math
import re
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

HERE = Path(__file__).resolve().parent
ENGINE_PATH = HERE / "engine_ref.py"
WORLDS = HERE / "worlds"
CONFORMANCE = HERE / "conformance"
CASES = CONFORMANCE / "cases"
MUTANTS = CONFORMANCE / "mutants"
UNITS = CONFORMANCE / "units.json"
FROZEN_WORLDS = CONFORMANCE / "worlds"
FROZEN_SUFFIX = ".frozen.world.json"
FROZEN_SUMS = FROZEN_WORLDS / "SHA256SUMS"
RULES = HERE / "rules.json"

KINDS = ("expression", "world_load", "action_sequence", "replay", "branching", "clock", "agents", "report")
SCRIPT_KINDS = ("action_sequence", "branching", "clock", "agents")
SOURCES = ("hand", "generated")
CASE_FIELDS = ("id", "kind", "source", "world", "input", "expect", "notes")
OUTPUT_SECTIONS = ("counts", "areas", "equations", "stocks", "scoring")
RELATIVE_TOLERANCE = 1e-9
ABSOLUTE_TOLERANCE = 1e-12
CANONICAL_DECIMALS = 9
MUTANT_TIMEOUT_SECONDS = 600
UNIT_PART = re.compile(r"([*/]?)\s*([a-z_]+)(?:\^(\d+))?")
ENGINE_REFUSAL = re.compile(r"Refusal\(\"([a-z-]+)\"")
FAIL_COUNT = re.compile(r"^(\d+) fail$", re.M)
FAILED_CASE = re.compile(r"^FAIL (\S+)", re.M)


# ---------------------------------------------------------------- engine under test


def load_engine(engine_path: Path | None):
    if engine_path is None:
        sys.path.insert(0, str(HERE))
        import engine_ref
        return engine_ref
    spec = importlib.util.spec_from_file_location("engine_ref", engine_path)
    module = importlib.util.module_from_spec(spec)
    sys.modules["engine_ref"] = module
    spec.loader.exec_module(module)
    module.HERE = HERE
    return module


def load_validator(engine):
    sys.path.insert(0, str(HERE))
    import validate
    validate.engine = engine
    return validate


# ---------------------------------------------------------------- canonical form


def format_canonical_number(value) -> str:
    if isinstance(value, int):
        return str(value)
    if not math.isfinite(value):
        raise ValueError(f"{value} has no canonical form")
    text = f"{value:.{CANONICAL_DECIMALS}f}".rstrip("0").rstrip(".")
    return "0" if text in ("", "-0") else text


def canonical_json(value) -> str:
    if value is None:
        return "null"
    if isinstance(value, bool):
        return "true" if value else "false"
    if isinstance(value, (int, float)):
        return format_canonical_number(value)
    if isinstance(value, str):
        return json.dumps(value, ensure_ascii=False)
    if isinstance(value, (list, tuple)):
        return "[" + ",".join(canonical_json(item) for item in value) + "]"
    members = (json.dumps(str(key), ensure_ascii=False) + ":" + canonical_json(value[key]) for key in sorted(value))
    return "{" + ",".join(members) + "}"


# ---------------------------------------------------------------- units the expectations are stated in


def read_unit_table() -> dict:
    return json.loads(UNITS.read_text(encoding="utf-8"))


def parse_expected_unit(text: str | None, table: dict) -> tuple[float, dict]:
    cleaned = (text or "1").strip()
    if cleaned == "1":
        return 1.0, {}
    factor, dims = 1.0, {}
    for operator, name, power in UNIT_PART.findall(cleaned):
        unit = table["units"][name]
        exponent = (-1 if operator == "/" else 1) * (int(power) if power else 1)
        factor *= (unit["ratio"][0] / unit["ratio"][1]) ** exponent
        for base, base_exponent in unit["dims"].items():
            dims[base] = dims.get(base, 0) + exponent * base_exponent
    return factor, {base: power for base, power in dims.items() if power}


def engine_dims(engine, dims: tuple[int, ...]) -> dict:
    return {base: power for base, power in zip(engine.BASE_DIMS, dims) if power}


def is_close(observed, expected) -> bool:
    if expected == "inf":
        return observed == math.inf
    if not isinstance(observed, (int, float)) or isinstance(observed, bool):
        return False
    return abs(observed - expected) <= max(RELATIVE_TOLERANCE * abs(expected), ABSOLUTE_TOLERANCE)


# ---------------------------------------------------------------- worlds a case runs in


def hash_frozen_world(path: Path) -> str:
    return hashlib.sha256(path.read_bytes().replace(b"\r\n", b"\n")).hexdigest()


def read_frozen_sums() -> dict[str, str]:
    sums = {}
    for sums_file in sorted(FROZEN_WORLDS.glob(f"{FROZEN_SUMS.name}*")):
        for line in sums_file.read_text(encoding="utf-8").splitlines():
            if line.strip():
                digest, name = line.split(maxsplit=1)
                sums[name.strip()] = digest
    return sums


def find_frozen_world_changes() -> list[str]:
    if not FROZEN_SUMS.exists():
        return [f"{FROZEN_SUMS} is missing, so nothing proves the frozen worlds are the ones the cases were written against"]
    sums = read_frozen_sums()
    present = {path.name for path in FROZEN_WORLDS.glob(f"*{FROZEN_SUFFIX}")}
    problems = [f"{name} is not listed in SHA256SUMS" for name in sorted(present - set(sums))]
    problems += [f"{name} is listed in SHA256SUMS but missing" for name in sorted(set(sums) - present)]
    problems += [f"{name} changed: sha256 {hash_frozen_world(FROZEN_WORLDS / name)}, frozen as {digest}"
                 for name, digest in sorted(sums.items()) if name in present and hash_frozen_world(FROZEN_WORLDS / name) != digest]
    return problems


def is_live_world_case(case: dict) -> bool:
    file = case.get("world", {}).get("file")
    return file is not None and not file.endswith(FROZEN_SUFFIX)


def materialize_worlds(scratch: Path, world_spec: dict, validator) -> Path:
    copy_dir = scratch / "worlds"
    shutil.copytree(WORLDS, copy_dir)
    for frozen in FROZEN_WORLDS.glob(f"*{FROZEN_SUFFIX}"):
        shutil.copy(frozen, copy_dir / frozen.name)
    edits = world_spec.get("edits", [])
    if edits:
        validator.apply_mutation(copy_dir, {"world": world_spec.get("edits_in", world_spec.get("file")), "edits": edits})
    return copy_dir


# ---------------------------------------------------------------- scripts: one step format for every kind that acts


def default_timestamp(index: int) -> str:
    return f"2026-10-05T00:{index // 60:02d}:{index % 60:02d}Z"


def take_tap(engine, log, step: dict, actor: str, timestamp: str):
    branch = step.get("on", "main")
    chosen = engine.action_for_tap(log.state_of(branch), step["col"], step["row"], step.get("brush"))
    if isinstance(chosen, engine.Refusal):
        return chosen
    verb, parameters = chosen
    return log.attempt(branch, actor, verb, parameters, timestamp)


def take_step(engine, log, step: dict, index: int):
    timestamp = step.get("timestamp", default_timestamp(index))
    actor, verb, branch = step.get("actor", "jim"), step["do"], step.get("on", "main")
    if step.get("raw"):
        return log.attempt(branch, actor, verb, step.get("parameters", {}), timestamp)
    if verb == "tap":
        return take_tap(engine, log, step, actor, timestamp)
    if verb == "branch":
        return log.branch(step["name"], step.get("from", "main"), actor, timestamp, step.get("proposal", False))
    if verb == "merge":
        return log.merge(step["branch"], step.get("into", "main"), actor, timestamp)
    if verb == "revert":
        return log.revert(step["event"], branch, actor, timestamp)
    return log.attempt(branch, actor, verb, step.get("parameters", {}), timestamp)


def describe_step_result(engine, result) -> dict:
    if isinstance(result, engine.Refusal):
        return {"refused": result.rule, "message": result.message}
    return {"applied": result["id"]}


def run_steps(engine, log, steps: list[dict]) -> list[dict]:
    return [describe_step_result(engine, take_step(engine, log, step, index)) for index, step in enumerate(steps)]


def report_branches(engine, log) -> dict:
    return {branch: engine.report_outputs(log.world, log.state_of(branch)) for branch in sorted(log.heads)}


# ---------------------------------------------------------------- observing a case


def expand_text(text) -> str:
    if isinstance(text, str):
        return text
    return "".join(segment * times for segment, times in text)


def observe_expression(engine, worlds_dir: Path, case: dict) -> dict:
    case_input = case["input"]
    world = engine.World(worlds_dir / case["world"]["file"])
    cycle = world.find_cycle()
    if cycle:
        return {"error": "cycle", "detail": " -> ".join(cycle)}
    log = engine.Log(world)
    setup = run_steps(engine, log, case_input.get("setup", []))
    refused_setup = [step for step in setup if "refused" in step]
    if refused_setup:
        return {"setup_refused": refused_setup}
    state = log.state_of("main")
    bindings = case_input.get("bindings", {})
    tile_instance = state.instance_at(*bindings["tile"]) if "tile" in bindings else None
    agent = state.agents.get(bindings["self"]) if "self" in bindings else None
    agent_type = agent["type"] if agent else None
    try:
        tree = engine.parse_expression(expand_text(case_input["text"]))
        produced = engine.check_expression(tree, engine.Scope(world, case_input.get("site", "equation"), agent_type))
        value = engine.Evaluation(world, state, tile_instance=tile_instance, agent=agent).value_of(tree)
    except engine.ExprError as problem:
        return {"error": problem.kind, "detail": problem.message}
    if produced[0] == "num":
        return {"type": "num", "dims": engine_dims(engine, produced[1]), "value": value}
    return {"type": produced[0], "value": value}


def observe_world_load(validator, worlds_dir: Path, case: dict) -> dict:
    report = validator.run_validation(worlds_dir)
    target = case["world"].get("file")
    fired = []
    for finding in report["fired"]:
        if finding["world"] == target and finding["rule"] not in fired:
            fired.append(finding["rule"])
    return {"valid": target in report["valid"], "fired": fired, "blind": [b["rule"] for b in report["blind"]]}


def observe_script(engine, worlds_dir: Path, case: dict) -> dict:
    world = engine.load_world(worlds_dir / case["world"]["file"])
    log = engine.Log(world)
    steps = run_steps(engine, log, case["input"]["steps"])
    return {"steps": steps, "final": report_branches(engine, log)}


def tamper_log(validator, document: dict, edits: list[dict]) -> dict:
    tampered = json.loads(json.dumps(document))
    for edit in edits:
        parent, last = validator.resolve_parent(tampered, edit["path"])
        if isinstance(last, dict):
            last = parent.index(validator.select_step(parent, last))
        if edit["op"] == "delete":
            del parent[last]
        else:
            parent[last] = edit["value"]
    return tampered


def snapshot_branches(log) -> dict:
    return {branch: canonical_json(log.state_of(branch).snapshot()) for branch in sorted(log.heads)}


def observe_replay(engine, validator, worlds_dir: Path, case: dict) -> dict:
    world_path = worlds_dir / case["world"]["file"]
    live = engine.Log(engine.load_world(world_path))
    steps = run_steps(engine, live, case["input"]["steps"])
    dumped = live.dump()
    tampered = tamper_log(validator, dumped, case["input"].get("tamper", []))
    observed = {"steps": steps, "log": canonical_json(dumped), "live": snapshot_branches(live)}
    try:
        replayed = engine.Log.load(engine.load_world(world_path), tampered)
    except (engine.WorldError, engine.Refusal, engine.ExprError, KeyError) as problem:
        return {**observed, "loads": False, "reason": str(problem)}
    return {**observed, "loads": True, "replayed": snapshot_branches(replayed),
            "replayed_log": canonical_json(replayed.dump())}


def evaluate_stated(engine, world, state, entry: dict, table: dict):
    tree = engine.parse_expression(entry["text"])
    engine.check_expression(tree, engine.Scope(world, entry.get("site", "equation")))
    value = engine.Evaluation(world, state).value_of(tree)
    if value is None or isinstance(value, bool):
        return value
    return value / parse_expected_unit(entry.get("unit"), table)[0]


def observe_report(engine, worlds_dir: Path, case: dict) -> dict:
    world = engine.load_world(worlds_dir / case["world"]["file"])
    log = engine.Log(world)
    steps = run_steps(engine, log, case["input"].get("steps", []))
    state = log.state_of(case["input"].get("branch", "main"))
    table = read_unit_table()
    try:
        values = [evaluate_stated(engine, world, state, entry, table) for entry in case["input"].get("expressions", [])]
    except engine.ExprError as problem:
        return {"steps": steps, "crash": f"expression refused: {problem.kind}: {problem.message}"}
    return {"steps": steps, "render": engine.render_props(world, state), "outputs": engine.report_outputs(world, state),
            "expressions": values}


def observe_case(engine, validator, case: dict) -> dict:
    with tempfile.TemporaryDirectory() as scratch:
        try:
            worlds_dir = materialize_worlds(Path(scratch), case["world"], validator)
            if case["kind"] == "expression":
                return observe_expression(engine, worlds_dir, case)
            if case["kind"] == "world_load":
                return observe_world_load(validator, worlds_dir, case)
            if case["kind"] == "replay":
                return observe_replay(engine, validator, worlds_dir, case)
            if case["kind"] == "report":
                return observe_report(engine, worlds_dir, case)
            return observe_script(engine, worlds_dir, case)
        except Exception as problem:
            return {"crash": f"{type(problem).__name__}: {problem}"}


# ---------------------------------------------------------------- comparing an observation with its expectation


def compare_expression(expect: dict, observed: dict, table: dict) -> list[str]:
    if "error" in expect:
        if observed.get("error") != expect["error"]:
            return [f"expected error {expect['error']}, observed {describe_observed(observed)}"]
        return []
    if "error" in observed or "value" not in observed:
        return [f"expected a value, observed {describe_observed(observed)}"]
    if isinstance(expect["value"], (bool, str)) and expect["value"] != "inf":
        if observed["value"] != expect["value"] or isinstance(observed["value"], bool) != isinstance(expect["value"], bool):
            return [f"expected {expect['value']!r}, observed {observed['value']!r}"]
        return []
    factor, dims = parse_expected_unit(expect.get("unit"), table)
    problems = []
    if observed.get("type") != "num" or observed.get("dims") != dims:
        problems.append(f"expected dimensions {dims or 'dimensionless'}, observed {observed.get('type')} {observed.get('dims')}")
    stated = observed["value"] / factor if isinstance(observed["value"], (int, float)) else observed["value"]
    if not is_close(stated, expect["value"]):
        problems.append(f"expected {expect['value']} {expect.get('unit', '1')}, observed {stated}")
    return problems


def describe_observed(observed: dict) -> str:
    if "error" in observed:
        return f"error {observed['error']} ({observed.get('detail', '')})"
    if "value" in observed:
        return f"value {observed['value']!r}"
    return json.dumps(observed)[:300]


def compare_world_load(expect: dict, observed: dict) -> list[str]:
    problems = []
    if "blind" in expect and expect["blind"] not in observed["blind"]:
        problems.append(f"expected BLIND {expect['blind']}, observed blind {observed['blind']}")
    if "valid" in expect and observed["valid"] != expect["valid"]:
        problems.append(f"expected valid={expect['valid']}, observed valid={observed['valid']} firing {observed['fired']}")
    if "first" in expect and observed["fired"][:1] != [expect["first"]]:
        problems.append(f"expected first refusal {expect['first']}, observed {observed['fired']}")
    if "fired" in expect and observed["fired"] != expect["fired"]:
        problems.append(f"expected rules {expect['fired']}, observed {observed['fired']}")
    return problems


def compare_steps(expected_steps: list[dict], observed_steps: list[dict]) -> list[str]:
    if len(expected_steps) != len(observed_steps):
        return [f"expected {len(expected_steps)} step results, observed {len(observed_steps)}"]
    problems = []
    for index, (expected, observed) in enumerate(zip(expected_steps, observed_steps)):
        for field in ("applied", "refused", "message"):
            if field in expected and observed.get(field) != expected[field]:
                problems.append(f"step {index}: expected {field} {expected[field]!r}, observed {observed}")
                break
    return problems


def compare_outputs(branch: str, expected: dict, observed: dict) -> list[str]:
    problems = []
    if "ticks" in expected and observed["ticks"] != expected["ticks"]:
        problems.append(f"{branch} ticks: expected {expected['ticks']}, observed {observed['ticks']}")
    for section in OUTPUT_SECTIONS:
        for key, value in expected.get(section, {}).items():
            if key not in observed[section]:
                problems.append(f"{branch} {section}.{key}: missing")
            elif not is_close(observed[section][key], value):
                problems.append(f"{branch} {section}.{key}: expected {value}, observed {observed[section][key]}")
    return problems


def compare_script(expect: dict, observed: dict) -> list[str]:
    problems = compare_steps(expect.get("steps", []), observed["steps"]) if "steps" in expect else []
    for branch, expected in expect.get("final", {}).items():
        if branch not in observed["final"]:
            problems.append(f"branch {branch} does not exist")
            continue
        problems += compare_outputs(branch, expected, observed["final"][branch])
    return problems


def compare_replay(expect: dict, observed: dict) -> list[str]:
    problems = compare_steps(expect["steps"], observed["steps"]) if "steps" in expect else []
    if observed["loads"] != expect["loads"]:
        return problems + [f"expected loads={expect['loads']}, observed loads={observed['loads']} {observed.get('reason', '')}"]
    if not expect["loads"]:
        if expect.get("reason_contains", "") not in observed["reason"]:
            problems.append(f"expected the refusal to say {expect['reason_contains']!r}, observed {observed['reason']!r}")
        return problems
    for branch, live_state in observed["live"].items():
        if observed["replayed"].get(branch) != live_state:
            problems.append(f"branch {branch}: replayed state differs from the live run")
    if observed["replayed_log"] != observed["log"]:
        problems.append("the replayed log differs from the live log")
    if "log" in expect and canonical_json(expect["log"]) != observed["log"]:
        problems.append(f"log differs: expected {canonical_json(expect['log'])[:400]} observed {observed['log'][:400]}")
    for branch, state in expect.get("state", {}).items():
        if canonical_json(state) != observed["replayed"].get(branch):
            problems.append(f"branch {branch} canonical state: expected {canonical_json(state)} observed {observed['replayed'].get(branch)}")
    return problems


def compare_stated(where: str, expected, observed) -> list[str]:
    """Every entry the expectation lists, recursively: dict keys it names, lists in full, numbers within tolerance."""
    if isinstance(expected, dict):
        if not isinstance(observed, dict):
            return [f"{where}: expected an object, observed {observed!r}"]
        problems = []
        for key, value in expected.items():
            if key not in observed:
                problems.append(f"{where}.{key}: missing")
            else:
                problems += compare_stated(f"{where}.{key}", value, observed[key])
        return problems
    if isinstance(expected, list):
        if not isinstance(observed, list) or len(observed) != len(expected):
            return [f"{where}: expected {len(expected)} items, observed {observed!r}"[:400]]
        return [p for index, item in enumerate(expected) for p in compare_stated(f"{where}[{index}]", item, observed[index])]
    if expected is None or isinstance(expected, (bool, str)):
        if observed != expected or isinstance(observed, bool) != isinstance(expected, bool):
            return [f"{where}: expected {expected!r}, observed {observed!r}"]
        return []
    if not is_close(observed, expected):
        return [f"{where}: expected {expected}, observed {observed!r}"]
    return []


def compare_report(expect: dict, observed: dict) -> list[str]:
    problems = compare_steps(expect["steps"], observed["steps"]) if "steps" in expect else []
    for section in ("render", "outputs", "expressions"):
        if section in expect:
            problems += compare_stated(section, expect[section], observed[section])
    problems += [f"render.{key}: expected absent, observed {observed['render'][key]!r}"
                 for key in expect.get("render_absent", []) if key in observed["render"]]
    return problems


def compare_case(case: dict, observed: dict, table: dict) -> list[str]:
    if "crash" in observed:
        return [f"crashed: {observed['crash']}"]
    if "setup_refused" in observed:
        return [f"setup was refused: {observed['setup_refused']}"]
    if case["kind"] == "expression":
        return compare_expression(case["expect"], observed, table)
    if case["kind"] == "world_load":
        return compare_world_load(case["expect"], observed)
    if case["kind"] == "replay":
        return compare_replay(case["expect"], observed)
    if case["kind"] == "report":
        return compare_report(case["expect"], observed)
    return compare_script(case["expect"], observed)


# ---------------------------------------------------------------- the case set


def read_cases() -> list[dict]:
    return [json.loads(path.read_text(encoding="utf-8")) | {"_file": path.name} for path in sorted(CASES.glob("*.json"))]


def find_case_shape_problems(case: dict) -> list[str]:
    problems = [f"missing field {field}" for field in CASE_FIELDS if field not in case]
    if case.get("kind") not in KINDS:
        problems.append(f"kind {case.get('kind')!r} is not one of {KINDS}")
    if case.get("source") not in SOURCES:
        problems.append(f"source {case.get('source')!r} is not one of {SOURCES}")
    if case.get("id") != case["_file"].removesuffix(".json"):
        problems.append(f"id {case.get('id')!r} does not name its file {case['_file']}")
    return problems


def judge_case(engine, validator, case: dict, table: dict) -> list[str]:
    problems = find_case_shape_problems(case)
    if problems:
        return problems
    return compare_case(case, observe_case(engine, validator, case), table)


# ---------------------------------------------------------------- coverage: what the cases must reach


def world_rules() -> dict[str, dict]:
    rules = {}
    for path in sorted(FROZEN_WORLDS.glob(f"*{FROZEN_SUFFIX}")):
        document = json.loads(path.read_text(encoding="utf-8"))
        for rule in document.get("rules", []):
            rules[rule["id"]] = dict(rule, world=path.name)
    return rules


def expected_refusals(cases: list[dict]) -> set[str]:
    refused = set()
    for case in cases:
        for step in case.get("expect", {}).get("steps", []):
            if "refused" in step:
                refused.add(step["refused"])
    return refused


def expected_world_load_rules(cases: list[dict]) -> set[str]:
    named = set()
    for case in cases:
        if case.get("kind") != "world_load":
            continue
        expect = case["expect"]
        named |= set(expect.get("fired", [])) | {expect[key] for key in ("first", "blind") if key in expect}
    return named


def is_effect_rule_exercised(rule: dict, cases: list[dict]) -> bool:
    for case in cases:
        if case.get("kind") not in SCRIPT_KINDS or case["world"].get("file") != rule["world"] or "final" not in case["expect"]:
            continue
        placed = {step.get("parameters", {}).get("type") for step in case["input"]["steps"] if step["do"] == "place"}
        if placed & set(rule.get("applies_to", [])):
            return True
    return False


def measure_coverage(engine, cases: list[dict]) -> list[tuple[str, set[str], set[str]]]:
    texts = " ".join(expand_text(case["input"].get("text", "")) for case in cases if case.get("kind") == "expression")
    functions = set(engine.FUNCTIONS)
    rules = world_rules()
    refused = expected_refusals(cases)
    covered_rules = {rule_id for rule_id, rule in rules.items()
                     if rule_id in refused or ("effect" in rule and is_effect_rule_exercised(rule, cases))}
    rules_json = {rule["id"] for rule in json.loads(RULES.read_text(encoding="utf-8"))["rules"]}
    engine_refusals = set(ENGINE_REFUSAL.findall(ENGINE_PATH.read_text(encoding="utf-8")))
    return [
        ("functions", functions, {name for name in functions if re.search(rf"\b{name}\(", texts)}),
        ("world rules", set(rules), covered_rules),
        ("rules.json", rules_json, rules_json & expected_world_load_rules(cases)),
        ("engine refusals", engine_refusals, engine_refusals & refused),
    ]


def print_coverage(coverage) -> bool:
    print("coverage: " + ", ".join(f"{len(covered)}/{len(wanted)} {label}" for label, wanted, covered in coverage))
    gaps = [(label, sorted(wanted - covered)) for label, wanted, covered in coverage if wanted - covered]
    for label, missing in gaps:
        print(f"COVERAGE GAP {label}: {missing}")
    return not gaps


# ---------------------------------------------------------------- modes


def run_cases(engine_path: Path | None) -> int:
    changed = find_frozen_world_changes()
    if changed:
        for problem in changed:
            print(f"FROZEN WORLD CHANGED {problem}")
        print("refusing to run: the cases were written against the frozen worlds, and these are not them")
        return 1
    engine = load_engine(engine_path)
    validator = load_validator(engine)
    cases = read_cases()
    if not cases:
        print(f"BLIND: no cases under {CASES}, so nothing was checked")
        print("0 cases pass")
        print("0 fail")
        return 1
    table = read_unit_table()
    failed = 0
    for case in cases:
        problems = judge_case(engine, validator, case, table)
        if problems:
            failed += 1
            print(f"FAIL {case.get('id', case['_file'])} ({case.get('kind')})")
            for problem in problems:
                print(f"    {problem}")
    is_covered = print_coverage(measure_coverage(engine, cases))
    print(f"{sum(1 for case in cases if is_live_world_case(case))} of them run against a live world")
    print(f"{len(cases) - failed} cases pass")
    print(f"{failed} fail")
    return 0 if failed == 0 and is_covered else 1


def list_cases() -> int:
    cases = read_cases()
    if not cases:
        print(f"BLIND: no cases under {CASES}")
        return 1
    for kind in KINDS:
        of_kind = [case for case in cases if case.get("kind") == kind]
        hand = sum(1 for case in of_kind if case.get("source") == "hand")
        print(f"{kind} ({len(of_kind)}, {hand} hand)")
        for case in of_kind:
            print(f"  {case['id']}  [{case.get('source')}]")
    print(f"{len(cases)} cases, {sum(1 for case in cases if case.get('source') == 'hand')} hand")
    return 0


def print_observation(case_id: str) -> int:
    engine = load_engine(None)
    validator = load_validator(engine)
    matching = [case for case in read_cases() if case.get("id") == case_id]
    if not matching:
        print(f"no case {case_id}")
        return 1
    print(json.dumps(observe_case(engine, validator, matching[0]), indent=1, default=str))
    return 0


def read_mutants() -> list[dict]:
    return [json.loads(path.read_text(encoding="utf-8")) for path in sorted(MUTANTS.glob("*.json"))]


def write_mutant(mutant: dict, scratch: Path) -> str | None:
    source = ENGINE_PATH.read_text(encoding="utf-8")
    for edit in mutant["edits"]:
        occurrences = source.count(edit["find"])
        if occurrences != 1:
            return f"its find text occurs {occurrences} times in engine_ref.py, not once"
        source = source.replace(edit["find"], edit["replace"])
    (scratch / "engine_ref.py").write_text(source, encoding="utf-8")
    return None


def run_mutant(mutant: dict) -> tuple[str, str]:
    with tempfile.TemporaryDirectory() as scratch:
        stale = write_mutant(mutant, Path(scratch))
        if stale:
            return "STALE", stale
        command = [sys.executable, str(Path(__file__).resolve()), "--engine", str(Path(scratch) / "engine_ref.py")]
        try:
            finished = subprocess.run(command, capture_output=True, text=True, timeout=MUTANT_TIMEOUT_SECONDS)
        except subprocess.TimeoutExpired:
            return "killed", f"no answer within {MUTANT_TIMEOUT_SECONDS} s"
    counted = FAIL_COUNT.search(finished.stdout)
    if not counted:
        return "ERROR", f"the runner printed no fail count: {(finished.stdout + finished.stderr)[-400:]}"
    if int(counted.group(1)) == 0:
        return "SURVIVED", "every case passes against it"
    return "killed", "by " + ", ".join(FAILED_CASE.findall(finished.stdout))


def run_mutants() -> int:
    mutants = read_mutants()
    if not mutants:
        print(f"BLIND: no mutants under {MUTANTS}, so the suite's power to fail is unmeasured")
        return 1
    if run_cases(None) != 0:
        print("the reference itself fails the suite, so no mutant result means anything")
        return 1
    killed = 0
    for mutant in mutants:
        verdict, detail = run_mutant(mutant)
        killed += verdict == "killed"
        print(f"{verdict} {mutant['id']} ({mutant['breaks']}): {detail}")
    print(f"{killed} of {len(mutants)} mutants killed")
    return 0 if killed == len(mutants) else 1


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--list", action="store_true", help="print the case ids by kind")
    parser.add_argument("--mutants", action="store_true", help="run every broken copy and report which cases kill it")
    parser.add_argument("--engine", type=Path, help="run the cases against this copy of engine_ref.py")
    parser.add_argument("--observe", metavar="CASE_ID", help="print what the reference produces for one case")
    options = parser.parse_args(argv)
    if options.list:
        return list_cases()
    if options.observe:
        return print_observation(options.observe)
    if options.mutants:
        return run_mutants()
    return run_cases(options.engine)


if __name__ == "__main__":
    sys.exit(main())
