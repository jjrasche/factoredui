"""Derive the parcel world's per-use constants from the task model and the banked research rows.

Writes twin-constants.json and prints every figure with the inputs it came from. A figure no source supports is
null with a declared reason. Deterministic: the same sources give the same file byte for byte.
`--check` writes nothing and exits 1 when the committed file differs from a fresh derivation.
"""
from __future__ import annotations

import argparse
import json
import re
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
WORLD = HERE / "worlds" / "parcel-five-acre.world.json"
OUTPUT = HERE / "twin-constants.json"
DEFAULT_WORKTREES = HERE.parents[2]

TASK_MODEL = "van-life-65-task-model/design/task-model"
ROW_STORES = {
    "consolidated": "consolidate-rows/research/rows.jsonl",
    "economy": "research-economy-inputs/research/economy/rows.jsonl",
    "livestock": "research-livestock-inputs/research/livestock/rows.jsonl",
    "heat-network": "research-heat-network-inputs/research/heat-network/rows.jsonl",
    "dm-grazing": "research-dm-grazing-model/research/dm-grazing/rows.jsonl",
}

SQ_FT_PER_ACRE = 43560.0
KG_PER_LB = 0.45359237
LB_PER_SHORT_TON = 2000.0
MINUTES_PER_HOUR = 60.0

USES = ("paddock", "hoop_house", "commons_building", "van_pad", "path", "pond", "woodland_tree")
FIELDS = (
    ("hours_per_year_per_tile", "hours_basis", "hour/tile/year"),
    ("hours_per_year_per_use", "hours_per_use_basis", "hour/year"),
    ("capex_usd_per_tile", "capex_basis", "usd/tile"),
    ("capex_usd_per_use", "capex_per_use_basis", "usd"),
    ("yield_kg_dm_per_tile_per_year", "yield_basis", "kg/tile/year"),
)

USE_RESOURCES = {
    "paddock": (["cattle_herd", "grassland"],
                "a paddock is grazed grass: the twin's pasture simulation grazes a cow (declared cow_head, the leader "
                "cow) on grassland"),
    "hoop_house": (["hoop_house", "hoop_house_crop"], "the house and the crop grown in it"),
    "commons_building": ([], "no task's resource is a building; education-program-teach and "
                             "rehabilitation-program-session name no place and no equipment"),
    "van_pad": ([], "no task's resource is a van pad or a vehicle parking surface"),
    "path": ([], "no task's resource is a path or lane surface"),
    "pond": ([], "no task's resource is a pond or open water; water_system is pipework"),
    "woodland_tree": (["food_forest"],
                      "declared: trees on the parcel are the food forest's tree crops; the orchard's apple rows are "
                      "full-production orchard figures, not woodland"),
}

NULL_CAPEX = {
    "paddock": {
        "reason": "no banked price for livestock fence per foot (row cap-prices-lives-null); the energizer and tank "
                  "are one per herd, carried in capex_usd_per_use; the other match prices piglets",
        "rows": ["cap-prices-lives-null"], "search": r"fence|polywire|\bposts?\b",
        "reviewed": ["cap-fence-energizer", "mi-agh-heartspasture-prices"]},
    "hoop_house": {
        "reason": "no banked price for a hoop house (row cap-prices-lives-null); the priced rows that name one "
                  "price a thermal screen or curtain, or name none, and service life is banked without a price",
        "rows": ["cap-prices-lives-null"], "search": r"hoop ?house|high tunnel",
        "reviewed": ["answer-best-automated-curtain", "answer-regional-manufacture-blocker",
                     "screen-umass-hoophouse-cable-under-1-dollar"]},
    "commons_building": {
        "reason": "the one banked building-scale price is the wood gasifier boiler of task boiler-stoke, and no task "
                  "or row places that boiler in the commons building, so it is not charged here",
        "rows": ["cap-wood-gasifier"], "search": r"gasif|boiler|building cost|cost to build|commons",
        "reviewed": ["cap-wood-gasifier", "g-answer-biochar"]},
    "van_pad": {"reason": "no priced row for gravel, base or a pad surface", "rows": [],
                "search": r"gravel|crushed|driveway|road base", "reviewed": []},
    "path": {"reason": "no priced row for gravel, chips or a walkway surface; the one wood-chip match prices nothing",
             "rows": [], "search": r"gravel|wood ?chips|walkway", "reviewed": ["modified-pain-mound-cpn"]},
    "pond": {"reason": "no priced row for a pond, a liner or excavation", "rows": [],
             "search": r"pond|liner|excavat", "reviewed": []},
    "woodland_tree": {"reason": "no priced row for nursery stock", "rows": [],
                      "search": r"nursery|seedling|bare.?root|sapling|tree stock", "reviewed": []},
}
PRICED = re.compile(r"\$\s?\d")

NULL_YIELD = {
    "hoop_house": "the hoop house yields tomatoes, not forage dry matter (row synth-hoop-house-output carries "
                  "marketable lb, outside this field)",
    "commons_building": "a building yields no forage",
    "van_pad": "a van pad yields no forage",
    "path": "a path yields no forage",
    "pond": "a pond yields no forage",
    "woodland_tree": "no banked yield for the food forest's tree crops, and they are not forage",
}


# ---------------------------------------------------------------- leaves


def read_jsonl(path: Path) -> list[dict]:
    return [json.loads(line) for line in path.read_text(encoding="utf-8").splitlines() if line.strip()]


def row_key(row: dict) -> str:
    return row.get("id") or "claim:" + row.get("claim", "")[:48]


def figure(value: float, **basis) -> tuple[float, dict]:
    return value, basis


def declared(reason: str, rows: list[str] | None = None) -> tuple[None, dict]:
    basis = {"declared": reason}
    if rows:
        basis["rows"] = rows
    return None, basis


def number_in_quote(row: dict, pattern: str) -> float:
    found = re.search(pattern, row["quote"])
    if found is None:
        raise ValueError(f"row {row['id']}: quote does not contain {pattern!r}")
    return float(found.group(1).replace(",", ""))


def format_basis(basis: dict) -> str:
    return "; ".join(f"{key} {', '.join(value) if isinstance(value, list) else value}" for key, value in basis.items())


# ---------------------------------------------------------------- sources


class Sources:
    """The task model and every row store, read once."""

    def __init__(self, worktrees: Path):
        model = worktrees / TASK_MODEL
        self.tasks = read_jsonl(model / "tasks.jsonl")
        self.task_hours = {entry["task"]: entry for entry in read_jsonl(model / "task_hours.jsonl")}
        self.bindings = {entry["constant"]: entry for entry in read_jsonl(model / "bindings.jsonl")}
        self.declared = {entry["id"]: entry for entry in
                         json.loads((model / "declared.json").read_text(encoding="utf-8"))["declared"]}
        self.rows: dict[str, dict] = {}
        for store, relative in ROW_STORES.items():
            for row in read_jsonl(worktrees / relative):
                self.rows.setdefault(row_key(row), row)
        self.priced = {key: row for key, row in self.rows.items() if PRICED.search(row.get("claim", ""))}

    def find_priced(self, pattern: str) -> list[str]:
        matcher = re.compile(pattern, re.I)
        return sorted(key for key, row in self.priced.items() if matcher.search(row.get("claim", "")))

    def row(self, row_id: str) -> dict:
        if row_id not in self.rows:
            raise KeyError(f"row {row_id} is in no store under {sorted(ROW_STORES)}")
        return self.rows[row_id]

    def tasks_of(self, resources: list[str]) -> list[dict]:
        return [task for task in self.tasks if task["resource"] in resources]

    def tasks_with_equipment(self, item: str) -> list[str]:
        return sorted(task["id"] for task in self.tasks
                      if any(item in (mode or {}).get("equipment", []) for mode in task["modes"].values()))


def read_tile(world_path: Path) -> dict:
    grid = json.loads(world_path.read_text(encoding="utf-8"))["grid"]
    sq_ft = float(grid["tile_ft"]) ** 2
    return {"tile_ft": float(grid["tile_ft"]), "sq_ft": sq_ft, "acres": sq_ft / SQ_FT_PER_ACRE,
            "basis": f"grid.tile_ft {grid['tile_ft']:g} of {world_path.name}, squared; {SQ_FT_PER_ACRE:g} sq ft per acre"}


# ---------------------------------------------------------------- concepts


def count_task_coverage(sources: Sources, resources: list[str]) -> dict:
    mapped = sources.tasks_of(resources)
    covered_by_hours = {covered for entry in sources.task_hours.values() for covered in entry["covers"]}
    cited = sorted(task["id"] for task in mapped if task["id"] in sources.task_hours)
    covered = sorted(task["id"] for task in mapped if task["id"] in covered_by_hours and task["id"] not in cited)
    uncited = sorted(task["id"] for task in mapped if task["id"] not in cited and task["id"] not in covered)
    return {"resources": resources, "tasks": len(mapped), "cited": cited, "covered_by_cited": covered,
            "uncited": uncited}


def derive_herd_move_hours(sources: Sources) -> tuple[float, dict]:
    entry = sources.task_hours["cattle-move"]
    season = sources.declared[entry["scale"][0]]
    recomputed = entry["labor_point"] * season["value"] / MINUTES_PER_HOUR
    if abs(recomputed - entry["hours_per_year"]) > 1e-6:
        raise ValueError(f"task_hours cattle-move {entry['hours_per_year']} != {recomputed}")
    sources.row(entry["row"])
    return figure(entry["hours_per_year"], tasks=["cattle-move"], rows=[entry["row"]], twin=[season["id"]],
                  arithmetic=f"{entry['labor_point']:g} min/day x {season['value']:g} {season['id']} / 60 = "
                             f"{recomputed:g} h/year",
                  assumes="one herd, moved daily whatever the paddock area: the row times a move, not an acre, "
                          "so the figure is charged once when any paddock exists; horse-herd-move cites the same row "
                          "and the same 15 minutes")


def derive_house_hours_per_tile(sources: Sources, resources: list[str], tile: dict) -> tuple[float, dict]:
    entries = [sources.task_hours[task["id"]] for task in sources.tasks_of(resources) if task["id"] in sources.task_hours]
    row_ids = sorted({entry["row"] for entry in entries})
    house_row = sources.row("uky-tunnel-labor-hours")
    length_ft = number_in_quote(house_row, r"(\d+)-foot x \d+-foot")
    width_ft = number_in_quote(house_row, r"\d+-foot x (\d+)-foot")
    house_hours = sum(entry["hours_per_year"] for entry in entries)
    house_sq_ft = length_ft * width_ft
    per_tile = house_hours / house_sq_ft * tile["sq_ft"]
    terms = " + ".join(f"{entry['task']} {entry['hours_per_year']:g}" for entry in entries)
    return figure(per_tile, tasks=[entry["task"] for entry in entries], rows=row_ids,
                  arithmetic=f"({terms}) = {house_hours:g} h per {length_ft:g} x {width_ft:g} ft house "
                             f"= {house_sq_ft:g} sq ft; {house_hours:g} / {house_sq_ft:g} x {tile['sq_ft']:g} sq ft/tile "
                             f"= {per_tile:.6g} h/tile/year",
                  assumes="labor scales with growing area, and one season a year (the twin's task_hours already "
                          "takes hours_per_house_per_season as hours_per_year)")


def derive_paddock_capex(sources: Sources) -> tuple[float, dict]:
    energizer = sources.row("cap-fence-energizer")
    tank = sources.row("cap-stock-tank-110")
    energizer_usd = number_in_quote(energizer, r"\$([\d,]+\.\d{2})")
    rated_acres = number_in_quote(energizer, r"(\d+) Acre")
    tank_usd = number_in_quote(tank, r"\$([\d,]+\.\d{2})")
    fenced_with_energizer = sorted(set(sources.tasks_with_equipment("fence_energizer"))
                                   & set(sources.tasks_with_equipment("paddock_fence")))
    tank_tasks = sources.tasks_with_equipment("stock_tank")
    total = round(energizer_usd + tank_usd, 2)
    return figure(total, rows=[energizer["id"], tank["id"]],
                  tasks=fenced_with_energizer + tank_tasks,
                  arithmetic=f"energizer {energizer_usd:.2f} + stock tank {tank_usd:.2f} = {total:.2f} usd, once",
                  assumes=f"the energizer is rated {rated_acres:g} acres, so one serves every paddock on the parcel; "
                          f"the task model pairs fence_energizer with paddock_fence in "
                          f"{', '.join(fenced_with_energizer)} and names stock_tank in {', '.join(tank_tasks)}, a batch "
                          f"held in paddock_fence; one tank for one herd; up-front price only, because no row gives "
                          f"either item a service life (cap-prices-lives-null)")


def derive_paddock_yield(sources: Sources, tile: dict) -> tuple[float, dict]:
    binding = sources.bindings["mi_grass_clover_tons_per_acre"]
    row = sources.row(binding["row"])
    if f"{binding['value']:.1f} tons/acre" not in row["quote"]:
        raise ValueError(f"row {row['id']}: quote does not state {binding['value']} tons/acre")
    kg_per_ton = LB_PER_SHORT_TON * KG_PER_LB
    per_tile = binding["value"] * kg_per_ton * tile["acres"]
    return figure(per_tile, rows=[row["id"]], twin=[binding["constant"], "eq-aux-stocking-static"],
                  arithmetic=f"{binding['value']:g} ton/acre/year x {LB_PER_SHORT_TON:g} lb/ton x {KG_PER_LB} kg/lb "
                             f"x {tile['sq_ft']:g}/{SQ_FT_PER_ACRE:g} acre/tile = {per_tile:.6g} kg/tile/year",
                  assumes="a US short ton (the source is a US SARE report; the engine's ton is 2000 lb); the quote "
                          "says 'tons/acre' without 'dry matter' and the twin reads it as dry matter (binding "
                          "quantity annual_herbage_growth; row syn-cow-strip-sqft)")


def declare_missing_price(sources: Sources, use: str) -> tuple[None, dict]:
    spec = NULL_CAPEX[use]
    value, basis = declared(spec["reason"], rows=spec["rows"] or None)
    basis["searched"] = f"/{spec['search']}/ over the claims of {len(sources.priced)} priced rows in {len(ROW_STORES)} stores"
    basis["matches"] = sources.find_priced(spec["search"])
    return value, basis


def derive_paddock(sources: Sources, tile: dict) -> dict:
    fence_life = sources.row("cap-life-fence-visual")
    fence_years = number_in_quote(fence_life, r"Fence\s+(\d+)")
    return {
        "hours_per_year_per_tile": declared(
            "the only cited paddock labor, cattle-move, is 15 minutes a day per herd and does not grow with paddock "
            "area; it is carried once per use in hours_per_year_per_use", rows=["uw-moving-cattle-15-min"]),
        "hours_per_year_per_use": derive_herd_move_hours(sources),
        "capex_usd_per_tile": declare_missing_price(sources, "paddock"),
        "capex_usd_per_use": derive_paddock_capex(sources),
        "yield_kg_dm_per_tile_per_year": derive_paddock_yield(sources, tile),
        "notes": f"fence service life {fence_years:g} years ({fence_life['id']}, an NRCS practice lifespan, not a "
                 f"measured life) waits for a per-foot fence price",
    }


def derive_hoop_house(sources: Sources, tile: dict) -> dict:
    life_row = sources.row("cap-life-high-tunnel-visual")
    life_years = number_in_quote(life_row, r"High Tunnel System\s+sq ft\s+(\d+)")
    return {
        "hours_per_year_per_tile": derive_house_hours_per_tile(sources, USE_RESOURCES["hoop_house"][0], tile),
        "hours_per_year_per_use": declared("hoop-house hours scale with the house's tiles; none is fixed per use"),
        "capex_usd_per_tile": declare_missing_price(sources, "hoop_house"),
        "capex_usd_per_use": declare_missing_price(sources, "hoop_house"),
        "yield_kg_dm_per_tile_per_year": declared(NULL_YIELD["hoop_house"]),
        "notes": f"service life {life_years:g} years ({life_row['id']}, an NRCS practice lifespan, not a measured "
                 f"life) waits for a hoop-house price",
    }


def derive_unsourced_use(sources: Sources, use: str, coverage: dict) -> dict:
    if coverage["tasks"]:
        hours_reason = f"none of the {coverage['tasks']} mapped tasks carries a cited labor figure"
    else:
        hours_reason = f"no task maps to this use: {USE_RESOURCES[use][1]}"
    return {
        "hours_per_year_per_tile": declared(hours_reason),
        "hours_per_year_per_use": declared(hours_reason),
        "capex_usd_per_tile": declare_missing_price(sources, use),
        "capex_usd_per_use": declare_missing_price(sources, use),
        "yield_kg_dm_per_tile_per_year": declared(NULL_YIELD[use]),
        "notes": "",
    }


def assemble_use(use: str, derived: dict, coverage: dict) -> dict:
    record = {}
    for field, basis_field, _unit in FIELDS:
        value, basis = derived[field]
        record[field] = value
        record[basis_field] = basis
    record["task_coverage"] = coverage
    record["mapping"] = USE_RESOURCES[use][1]
    record["notes"] = derived["notes"]
    return record


# ---------------------------------------------------------------- orchestrator


def derive_constants(worktrees: Path, world_path: Path = WORLD) -> dict:
    sources = Sources(worktrees)
    tile = read_tile(world_path)
    uses = {}
    for use in USES:
        coverage = count_task_coverage(sources, USE_RESOURCES[use][0])
        if use == "paddock":
            derived = derive_paddock(sources, tile)
        elif use == "hoop_house":
            derived = derive_hoop_house(sources, tile)
        else:
            derived = derive_unsourced_use(sources, use, coverage)
        uses[use] = assemble_use(use, derived, coverage)
    mapped_resources = {resource for resources, _reason in USE_RESOURCES.values() for resource in resources}
    return {
        "purpose": "per-use annual figures for the parcel world, each traced to task ids, row ids and arithmetic, "
                   "or null with a declared reason; written by derive_twin_constants.py, never by hand",
        "sources": {"task_model": TASK_MODEL, "row_stores": ROW_STORES,
                    "root": "the .git-worktrees directory that holds this worktree"},
        "units": {field: unit for field, _basis, unit in FIELDS},
        "conversions": {"sq_ft_per_acre": SQ_FT_PER_ACRE, "lb_per_short_ton": LB_PER_SHORT_TON,
                        "kg_per_lb": KG_PER_LB, "basis": "definitions"},
        "tile": tile,
        "task_count": len(sources.tasks),
        "unmapped_tasks": sorted(task["id"] for task in sources.tasks if task["resource"] not in mapped_resources),
        "uses": uses,
    }


def describe_constants(constants: dict) -> list[str]:
    lines = [f"tile {constants['tile']['sq_ft']:g} sq_ft = {constants['tile']['acres']} acre ({constants['tile']['basis']})"]
    for use, record in constants["uses"].items():
        coverage = record["task_coverage"]
        lines.append(f"{use} tasks {coverage['tasks']}: cited {len(coverage['cited'])}, covered "
                     f"{len(coverage['covered_by_cited'])}, no cited hours {len(coverage['uncited'])} "
                     f"{coverage['uncited']}")
        for field, basis_field, unit in FIELDS:
            value = record[field]
            shown = "null" if value is None else f"{value:g} {unit}"
            lines.append(f"  {field} = {shown}  basis: {format_basis(record[basis_field])}")
    lines.append(f"tasks mapped to no world use: {len(constants['unmapped_tasks'])} of {constants['task_count']}")
    return lines


def render_json(constants: dict) -> str:
    return json.dumps(constants, indent=2) + "\n"


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--worktrees", default=str(DEFAULT_WORKTREES), help="directory holding the source worktrees")
    parser.add_argument("--out", default=str(OUTPUT))
    parser.add_argument("--check", action="store_true", help="compare with the committed file and write nothing")
    options = parser.parse_args(argv)
    constants = derive_constants(Path(options.worktrees))
    print("\n".join(describe_constants(constants)))
    rendered = render_json(constants)
    out = Path(options.out)
    if options.check:
        is_current = out.exists() and out.read_text(encoding="utf-8") == rendered
        print(f"{out.name} {'matches' if is_current else 'DIFFERS FROM'} a fresh derivation")
        return 0 if is_current else 1
    out.write_text(rendered, encoding="utf-8")
    print(f"wrote {out.name}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
