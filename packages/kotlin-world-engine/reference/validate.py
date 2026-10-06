"""Check every world file against rules.json: prints '<n> worlds valid' and '<m> invalid'.

Exit 0 when every world is valid, 1 when any is invalid, 3 (BLIND) when a set the rules need is empty.
"""
from __future__ import annotations

import argparse
import json
import shutil
import sys
import tempfile
from decimal import Decimal
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))

import engine_ref as engine  # noqa: E402

SCANS = ("worlds", "expressions", "names", "seeds","rules", "actions", "links", "projections", "figures", "object_types",
         "footprints", "instances", "grounds")
EXIT_BLIND = 3


def read_rules() -> list[dict]:
    return json.loads((HERE / "rules.json").read_text(encoding="utf-8"))["rules"]


def read_mutations() -> list[dict]:
    return read_decimal_json((HERE / "mutations.json").read_text(encoding="utf-8"))["mutations"]


def read_decimal_json(text: str):
    """JSON with every fractional or exponent number kept as the Decimal written, unchecked, so a broken copy can hold any number."""
    return json.loads(text, parse_float=Decimal)


def write_decimal_json(node) -> str:
    """JSON text in which every Decimal is written exactly as it was read."""
    if isinstance(node, Decimal):
        return str(node)
    if isinstance(node, dict):
        return "{" + ", ".join(f"{json.dumps(key)}: {write_decimal_json(value)}" for key, value in node.items()) + "}"
    if isinstance(node, list):
        return "[" + ", ".join(write_decimal_json(item) for item in node) + "]"
    return json.dumps(node, ensure_ascii=False)


def classify_site_problem(problem: dict | None) -> dict:
    record = {"syntax_errors": [], "unknown_words": [], "unit_errors": [], "bound_errors": [], "over_bound": False}
    if problem is None:
        return record
    bucket = {"syntax": "syntax_errors", "unknown_word": "unknown_words", "unit_mismatch": "unit_errors",
              "bound": "bound_errors", engine.NUMBER_EXPONENT_RULE: "bound_errors"}.get(problem["kind"], "syntax_errors")
    record[bucket].append(problem["message"])
    record["over_bound"] = bool(record["bound_errors"])
    return record


def declaration_sites(world: engine.World) -> list[tuple[str, str]]:
    units = [(f"object_types.{t['id']}.properties.{p['name']}.unit", p.get("unit", "1"))
             for t in world.types.values() for p in t.get("properties", []) if p.get("type") != "text"]
    units += [(f"agents.{a['type']}.attributes.{x['name']}.unit", x["unit"])
              for a in world.agents.values() for x in a.get("attributes", [])]
    units.append(("clock.tick_unit", world.doc["clock"]["tick_unit"]))
    return units


def derive_expression_facts(name: str, world: engine.World) -> list[dict]:
    records = []
    for site in world.expression_sites():
        records.append({"world": name, "where": site["where"], **classify_site_problem(engine.check_site(site))})
    for where, unit in declaration_sites(world):
        try:
            engine.parse_unit(unit)
            problem = None
        except engine.ExprError as error:
            problem = {"kind": error.kind, "message": error.message}
        records.append({"world": name, "where": where, **classify_site_problem(problem)})
    return records


def derive_rule_facts(name: str, world: engine.World) -> list[dict]:
    records = []
    own = [r for r in world.doc.get("rules", [])]
    for rule in own + world.inherited_rules:
        unknown = []
        checks_targets = rule.get("scope", "self") == "self" or rule in world.inherited_rules
        if checks_targets:
            unknown += [t for t in rule.get("applies_to", []) if t not in world.types]
            if "applies_to_tag" in rule and rule["applies_to_tag"] not in world.tags:
                unknown.append(f"#{rule['applies_to_tag']}")
        records.append({"world": name, "id": rule["id"], "kind": "require" if "require" in rule else "effect",
                        "message": rule.get("message", ""), "unknown_targets": unknown})
    return records


def named_nodes(world: engine.World, tree) -> list[str]:
    nodes = []
    for referenced in engine.referenced_names(tree):
        if referenced in world.equations:
            nodes.append(f"equation:{referenced}")
        elif referenced in world.scoring:
            nodes.append(f"scoring:{referenced}")
    return nodes


def find_projected_agents(world: engine.World, graph: dict[str, set[str]], tree) -> set[str]:
    """Agent types whose projected vote the expression reads, directly or through any chain of equation and score names."""
    projected = set(engine.projected_agent_types(tree))
    pending, seen = named_nodes(world, tree), set()
    while pending:
        node = pending.pop()
        if node in seen:
            continue
        seen.add(node)
        if node.startswith("agent:"):
            projected.add(node.removeprefix("agent:"))
        pending.extend(graph.get(node, ()))
    return projected


def derive_projection_facts(name: str, world: engine.World) -> list[dict]:
    records = []
    graph = world.name_graph()
    for site in world.expression_sites():
        try:
            tree = world.ast(site["text"])
        except engine.ExprError:
            continue
        if not find_projected_agents(world, graph, tree):
            continue
        is_binding = site["where"].startswith(("rules.", "inherited.")) or site.get("binding", False)
        records.append({"world": name, "where": site["where"], "binding": is_binding})
    return records


def derive_link_facts(name: str, world: engine.World) -> list[dict]:
    if not world.link:
        return []
    parent_extent = world.linked_extent_sq_ft()
    return [{
        "world": name,
        "parent": world.link["parent"],
        "problems": list(world.link_problems),
        "extent_sq_ft": world.extent_sq_ft(),
        "parent_extent_sq_ft": parent_extent,
        "fits": parent_extent is None or world.extent_sq_ft() <= parent_extent,
    }]


def derive_figure_facts(name: str, world: engine.World) -> list[dict]:
    return [
        {"world": name, "type": t["id"], "property": p["name"], "kind": p["kind"], "has_source": "source" in p}
        for t in world.types.values() for p in t.get("properties", []) if p.get("kind") in engine.SOURCED_KINDS
    ]


def derive_type_facts(name: str, world: engine.World) -> list[dict]:
    known = set(engine.GENERIC_SPRITES) | {e["id"] for e in world.doc["sprites"].get("extensions", [])}
    return [{"world": name, "type": t["id"], "sprite": t["sprite"], "sprite_known": t["sprite"] in known,
             "footprint_too_large": engine.is_footprint_too_large(world, t["id"])}
            for t in world.types.values()]


def derive_footprint_facts(name: str, world: engine.World) -> list[dict]:
    """footprint-too-large reports a type over the tile cap, so its derived count is never compared here or printed."""
    return [{"world": name, "type": t["id"], "footprint": t["footprint"], "derived": engine.derived_footprint(world, t["id"]),
             "agrees": t["footprint"] == engine.derived_footprint(world, t["id"])}
            for t in world.types.values()
            if "footprint" in t and "footprint_mm" in t and not engine.is_footprint_too_large(world, t["id"])]


def is_figure_reasoned(figure: dict) -> bool:
    if figure.get("value") is None:
        return bool(figure.get("null_reason"))
    return "source" in figure


def derive_instance_facts(name: str, world: engine.World) -> list[dict]:
    records = []
    entries = [entry for entry in world.doc.get("seed", []) if entry["action"] == "place_instance"]
    seen_ids = [entry["id"] for entry in entries]
    for entry in entries:
        parameters = entry["parameters"]
        error = parameters.get("error") or {}
        records.append({
            "world": name,
            "id": entry["id"],
            "is_unique": seen_ids.count(entry["id"]) == 1,
            "has_source": "source" in parameters,
            "missing_error_fields": [field for field in engine.ERROR_FIELDS if field not in error],
            "unreasoned": [field for field in engine.ERROR_FIELDS if field in error and not is_figure_reasoned(error[field])],
        })
    return records


def derive_ground_facts(name: str, world: engine.World) -> list[dict]:
    if world.ground is None:
        return []
    heights = world.ground["heights_mm"]
    return [{
        "world": name,
        "cols": world.cols,
        "rows": world.rows,
        "length": len(heights),
        "expected_length": engine.expected_ground_length(world),
        "size_agrees": len(heights) == engine.expected_ground_length(world),
        "out_of_range": engine.ground_out_of_range(heights),
        "error_reasoned": is_figure_reasoned(world.ground["error"]["vertical_mm"]),
    }]


def find_seed_error(world: engine.World) -> str:
    try:
        world.seed_state()
    except (engine.Refusal, engine.ExprError, KeyError, ValueError, TypeError) as problem:
        return str(problem)
    return ""


def derive_facts(worlds_dir: Path) -> dict[str, list[dict]]:
    facts: dict[str, list[dict]] = {scan: [] for scan in SCANS}
    schema = engine.load_schema("world.schema.json")
    for path in sorted(worlds_dir.glob("*.world.json")):
        record = {"world": path.name, "parse_ok": True, "parse_error": "", "oversized_number": "", "schema_errors": []}
        facts["worlds"].append(record)
        try:
            document = engine.read_json(path.read_text(encoding="utf-8"))
        except engine.NumberExponentTooLarge as problem:
            record["oversized_number"] = str(problem)
            continue
        except (json.JSONDecodeError, UnicodeDecodeError) as problem:
            record["parse_ok"] = False
            record["parse_error"] = str(problem)
            continue
        record["schema_errors"] = engine.schema_errors(document, schema)
        if record["schema_errors"]:
            continue
        world = engine.World(path)
        facts["expressions"] += derive_expression_facts(path.name, world)
        facts["names"].append({"world": path.name, "cycle": world.find_cycle()})
        facts["seeds"].append({"world": path.name, "seed_error": find_seed_error(world)})
        facts["rules"] += derive_rule_facts(path.name, world)
        facts["actions"] += [{"world": path.name, "verb": a["verb"], "emits": a["emits"]} for a in world.actions.values()]
        facts["links"] += derive_link_facts(path.name, world)
        facts["projections"] += derive_projection_facts(path.name, world)
        facts["figures"] += derive_figure_facts(path.name, world)
        facts["object_types"] += derive_type_facts(path.name, world)
        facts["footprints"] += derive_footprint_facts(path.name, world)
        facts["instances"] += derive_instance_facts(path.name, world)
        facts["grounds"] += derive_ground_facts(path.name, world)
    return facts


def holds(condition: dict, record: dict) -> bool:
    op = condition["op"]
    value = record.get(condition.get("field", ""))
    if op == "empty":
        return not value
    if op == "nonempty":
        return bool(value)
    if op == "is_true":
        return value is True
    if op == "is_false":
        return value is False
    if op == "eq_value":
        return value == condition["value"]
    if op == "when":
        return not holds(condition["if"], record) or holds(condition["then"], record)
    if op == "all":
        return all(holds(part, record) for part in condition["of"])
    raise ValueError(f"rules.json uses unknown op {op}")


def judge(facts: dict[str, list[dict]], rules: list[dict]) -> dict:
    blind, fired = [], []
    for rule in rules:
        records = facts[rule["scans"]]
        if rule.get("kind") == "blind":
            if len(records) < rule["min"]:
                blind.append({"rule": rule["id"], "message": rule["message"]})
            continue
        for record in records:
            if not holds(rule["condition"], record):
                fired.append({"rule": rule["id"], "world": record["world"],
                              "message": rule["message"].format_map(DefaultDict(record))})
    worlds = [r["world"] for r in facts["worlds"]]
    invalid = sorted({f["world"] for f in fired})
    return {"blind": blind, "fired": fired, "valid": [w for w in worlds if w not in invalid], "invalid": invalid}


class DefaultDict(dict):
    def __missing__(self, key: str) -> str:
        return "{" + key + "}"


def run_validation(worlds_dir: Path) -> dict:
    return judge(derive_facts(worlds_dir), read_rules())


def resolve_parent(document, path: list):
    node = document
    for step in path[:-1]:
        node = select_step(node, step)
    return node, path[-1]


def select_step(node, step):
    if isinstance(step, dict):
        return next(item for item in node if all(item.get(k) == v for k, v in step.items()))
    return node[step]


def number_template(template, index: int):
    if isinstance(template, str):
        return template.replace("{i}", str(index))
    if isinstance(template, list):
        return [number_template(item, index) for item in template]
    if isinstance(template, dict):
        return {key: number_template(value, index) for key, value in template.items()}
    return template


def apply_mutation(worlds_dir: Path, mutation: dict) -> None:
    for edit in mutation["edits"]:
        if edit["op"] == "delete_all_worlds":
            for path in worlds_dir.glob("*.world.json"):
                path.unlink()
            continue
        target = worlds_dir / mutation["world"]
        if edit["op"] == "write_text":
            target.write_text(edit["value"], encoding="utf-8")
            continue
        document = read_decimal_json(target.read_text(encoding="utf-8"))
        parent, last = resolve_parent(document, edit["path"])
        if isinstance(last, dict):
            index = parent.index(select_step(parent, last))
            last = index
        if edit["op"] == "set":
            parent[last] = edit["value"]
        elif edit["op"] == "set_repeated":
            parent[last] = edit["unit"] * edit["times"] + edit["tail"]
        elif edit["op"] == "set_items":
            parent[last] = [number_template(edit["template"], index) for index in range(1, edit["times"] + 1)]
        elif edit["op"] == "delete":
            del parent[last]
        else:
            raise ValueError(f"mutations.json uses unknown op {edit['op']}")
        target.write_text(write_decimal_json(document), encoding="utf-8")


def run_mutation(mutation: dict, worlds_dir: Path = HERE / "worlds") -> dict:
    with tempfile.TemporaryDirectory() as scratch:
        copy_dir = Path(scratch) / "worlds"
        shutil.copytree(worlds_dir, copy_dir)
        apply_mutation(copy_dir, mutation)
        return run_validation(copy_dir)


def print_report(report: dict) -> int:
    for blind in report["blind"]:
        print(f"BLIND {blind['rule']}: {blind['message']}")
    for failure in report["fired"]:
        print(f"FAIL {failure['rule']}: {failure['message']}")
    print(f"{len(report['valid'])} worlds valid")
    print(f"{len(report['invalid'])} invalid")
    if report["blind"]:
        return EXIT_BLIND
    return 1 if report["invalid"] else 0


def tripped_rules(report: dict) -> set[str]:
    return {f["rule"] for f in report["fired"]} | {b["rule"] for b in report["blind"]}


def is_tripped_on(report: dict, rule: str, world: str | None) -> bool:
    """A world-less mutation (an emptied directory) trips its rule as BLIND; any other trips it on the world it broke."""
    if world is None:
        return rule in {b["rule"] for b in report["blind"]}
    return any(f["rule"] == rule and f["world"] == world for f in report["fired"]) and world in report["invalid"]


def is_mutation_caught(mutation: dict, mutated: dict, starting: dict) -> bool:
    """Caught: the rule trips on the broken copy and did not already trip on the worlds it was copied from."""
    if mutation["rule"] in tripped_rules(starting):
        return False
    return is_tripped_on(mutated, mutation["rule"], mutation["world"])


def print_starting_problems(starting: dict) -> None:
    for blind in starting["blind"]:
        print(f"STARTING WORLDS BLIND {blind['rule']}: {blind['message']}")
    for failure in starting["fired"]:
        print(f"STARTING WORLD INVALID {failure['rule']}: {failure['message']}")
    print("refusing to self-test: a broken copy proves a rule only when the worlds it was copied from are valid")


def print_self_test(worlds_dir: Path = HERE / "worlds", mutations: list[dict] | None = None) -> int:
    mutations = read_mutations() if mutations is None else mutations
    if not mutations:
        print("BLIND: mutations.json holds no broken copies, so no rule's power to fire was measured")
        print("0 of 0 broken copies caught")
        return EXIT_BLIND
    starting = run_validation(worlds_dir)
    if starting["fired"] or starting["blind"]:
        print_starting_problems(starting)
        print(f"0 of {len(mutations)} broken copies caught")
        return 1
    missed = 0
    for mutation in mutations:
        caught = is_mutation_caught(mutation, run_mutation(mutation, worlds_dir), starting)
        missed += 0 if caught else 1
        print(f"{'caught' if caught else 'MISSED'} {mutation['rule']}")
    print(f"{len(mutations) - missed} of {len(mutations)} broken copies caught")
    return 1 if missed else 0


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--worlds", default=str(HERE / "worlds"))
    parser.add_argument("--self-test", action="store_true", help="apply every mutation and report which rule caught it")
    options = parser.parse_args(argv)
    if options.self_test:
        return print_self_test(Path(options.worlds))
    return print_report(run_validation(Path(options.worlds)))


if __name__ == "__main__":
    sys.exit(main())
