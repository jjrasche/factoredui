"""Build worlds/parcel-lidar-sample.world.json from a plot-twin tree sample: one measured instance per lidar tree.

x_mm, y_mm are the apex return's plot-local millimetres (the frame's origin is the grid's south-west corner),
height_mm the apex return's height above ground, crown_radius_mm the sample's crown radius. Numbers are converted
in exact decimals, so 29.65 m is 29650 mm and not 29649.999999999996.
"""
from __future__ import annotations

import argparse
import json
import math
import sys
from decimal import Decimal
from pathlib import Path

HERE = Path(__file__).resolve().parent
DEFAULT_SAMPLE = Path("C:/Users/rasche_j/Documents/workspace/plot-twin/capture/receipts/tree-sample-for-van-build-v2.json")
DEFAULT_OUT = HERE / "worlds" / "parcel-lidar-sample.world.json"
RECEIPTS_REFERENCE = "plot-twin/capture/receipts"
TILE_FT = 25
TILE_MM = Decimal(TILE_FT) * Decimal("304.8")
CROWN_CAP_M = Decimal("6.0")
EPSG_PREFIX = "EPSG:"


def read_sample(path: Path) -> dict:
    return json.loads(path.read_text(encoding="utf-8"), parse_float=Decimal)


def metres_to_mm(metres: Decimal) -> int | float:
    millimetres = metres * 1000
    return int(millimetres) if millimetres == millimetres.to_integral_value() else float(millimetres)


def to_json_number(value: Decimal) -> int | float:
    return int(value) if value == value.to_integral_value() else float(value)


def frame_of(sample: dict) -> dict:
    frame = sample["frame"]
    if not frame["crs"].startswith(EPSG_PREFIX):
        raise ValueError(f"the sample's frame names {frame['crs']}, not an EPSG code")
    return {
        "epsg": int(frame["crs"].removeprefix(EPSG_PREFIX)),
        "origin_east_m": to_json_number(Decimal(frame["origin_easting_m"]).quantize(Decimal("0.001"))),
        "origin_north_m": to_json_number(Decimal(frame["origin_northing_m"]).quantize(Decimal("0.001"))),
        "x_axis": "east",
        "y_axis": "north",
    }


def source_tag(sample: dict) -> str:
    return (f"{sample['source']}; {sample['method']}; position is the apex return (highest return in the 3x3 cells "
            f"around the 1 m CHM peak), the crown apex and not the stem; the tree count is not validated against a stem map")


def crown_reason(crown_m: Decimal) -> str:
    walk = "half-height ray walk clipped to 1 to 6 m; no crown error has been measured on this parcel"
    if crown_m >= CROWN_CAP_M:
        return f"{walk}; 6000 is the clip, so the true radius is at least 6000 mm"
    return walk


def error_of(tree: dict) -> dict:
    return {
        "position_mm": {
            "value": None,
            "null_reason": "no detection error has been measured on this parcel (procedure step s9 not run)",
            "note": "the position is the crown apex; a published mean apex-to-stem offset for hardwoods is 950 mm "
                    "(row gatziolis10-apex-stem-offset, another study's site, not this parcel)",
        },
        "height_mm": {
            "value": None,
            "null_reason": "no field heights were measured on this parcel; the CHM peak and the apex return are two readings of the same returns, not a check of accuracy",
        },
        "crown_radius_mm": {"value": None, "null_reason": crown_reason(tree["crown_radius_meters"])},
    }


def instance_entry(index: int, tree: dict, tag: str, reference: str) -> dict:
    apex = tree["apex_return"]
    return {
        "id": f"tree-{index + 1:02d}",
        "action": "place_instance",
        "parameters": {
            "type": "lidar_tree",
            "x_mm": to_json_number(Decimal(apex["east_mm"])),
            "y_mm": to_json_number(Decimal(apex["north_mm"])),
            "height_mm": metres_to_mm(apex["height_above_ground_m"]),
            "crown_radius_mm": metres_to_mm(tree["crown_radius_meters"]),
            "provenance": "measured",
            "source": {"file": f"{reference}#/trees/{index}", "tag": tag},
            "error": error_of(tree),
        },
    }


def tiles_covering(extent_mm: Decimal) -> int:
    return math.floor(extent_mm / TILE_MM) + 1


def grid_of(sample: dict) -> dict:
    trees = sample["trees"]
    return {
        "cols": tiles_covering(max(Decimal(tree["apex_return"]["east_mm"]) for tree in trees)),
        "rows": tiles_covering(max(Decimal(tree["apex_return"]["north_mm"]) for tree in trees)),
        "tile_ft": TILE_FT, "shape": "square", "view": "top", "north": "row_0",
    }


def build_world(sample: dict, sample_name: str) -> dict:
    tag = source_tag(sample)
    reference = f"{RECEIPTS_REFERENCE}/{sample_name}"
    return {
        "id": "parcel-lidar-sample",
        "version": 1,
        "title": "Parcel lidar sample",
        "description": (f"Ten lidar trees of parcel {sample['parcel']} as measured instances at their apex-return millimetre "
                        f"positions, placed on the earth by the frame. The grid is the trees' bounding box from the frame "
                        f"origin, rounded up to whole 25 ft tiles; it is not the parcel boundary. Built by "
                        f"import_plot_twin_trees.py from {reference}."),
        "sprites": {"extensions": []},
        "grid": grid_of(sample),
        "frame": frame_of(sample),
        "clock": {"tick_unit": "day", "tick_length": 1},
        "object_types": [
            {"id": "lidar_tree", "label": "Lidar tree", "sprite": "tree", "color": "#2E7D32", "footprint": [1, 1], "tags": ["tree"]},
            {"id": "shed", "label": "Shed", "sprite": "block", "color": "#A0522D", "footprint": [1, 1],
             "footprint_mm": [2400, 3000], "height_mm": 2700, "tags": ["structure"]},
        ],
        "rules": [],
        "equations": [
            {"id": "tree_count", "expr": "count_instances('#tree')", "unit": "1"},
            {"id": "nearest_tree_pair", "expr": "min_distance_mm('#tree', '#tree')", "unit": "mm"},
        ],
        "stocks": [],
        "actions": [
            {"verb": "place", "label": "Place", "parameters": [{"name": "type", "type": "object_type"}, {"name": "col", "type": "col"}, {"name": "row", "type": "row"}], "emits": ["place"]},
            {"verb": "remove", "label": "Remove", "parameters": [{"name": "col", "type": "col"}, {"name": "row", "type": "row"}], "emits": ["remove"]},
            {"verb": "place_instance", "label": "Place at a point", "parameters": [{"name": "type", "type": "object_type"}, {"name": "x_mm", "type": "x_mm"}, {"name": "y_mm", "type": "y_mm"}, {"name": "rotation_deg", "type": "rotation_deg"}], "emits": ["place_instance"]},
            {"verb": "remove_instance", "label": "Remove a point object", "parameters": [{"name": "id", "type": "instance_id"}], "emits": ["remove_instance"]},
        ],
        "agents": [],
        "scoring": [],
        "links": [],
        "seed": [instance_entry(index, tree, tag, reference) for index, tree in enumerate(sample["trees"])],
    }


def render_world(world: dict) -> str:
    return json.dumps(world, indent=2, ensure_ascii=False) + "\n"


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("sample", type=Path, nargs="?", default=DEFAULT_SAMPLE, help="the plot-twin tree sample JSON")
    parser.add_argument("--out", type=Path, default=DEFAULT_OUT)
    options = parser.parse_args(argv)
    world = build_world(read_sample(options.sample), options.sample.name)
    options.out.write_text(render_world(world), encoding="utf-8")
    print(f"wrote {len(world['seed'])} measured instances to {options.out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
