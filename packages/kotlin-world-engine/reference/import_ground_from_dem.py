"""Build a world's ground from a DEM: (cols + 1) x (rows + 1) vertex heights in mm, sampled bilinearly at tile corners.

The DEM is a grid of elevations in metres with square cells of cell_m, its outer south-west corner at dem_origin
(east, north) in the world frame's coordinate reference, or in the world's local metres when it has no frame. A
cell's value stands at its centre. Vertex vc, vr (vertex row 0 the north edge) stands at the frame origin plus
(vc x tile, (rows - vr) x tile); its height is the bilinear interpolation of the four cell centres around it, held
to the edge cells within half a cell of the DEM's edge, rounded to the nearest millimetre. A vertex outside the DEM
is refused.

`python import_ground_from_dem.py --demo` rebuilds worlds/parcel-ground-demo.world.json from plot-twin's 1 m ground.
"""
from __future__ import annotations

import argparse
import csv
import json
import math
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
PLOT_TWIN_COE = Path("C:/Users/rasche_j/Documents/workspace/plot-twin/.git-worktrees/pt-coe/capture/data/coe")
DEFAULT_DEM = PLOT_TWIN_COE / "probe" / "terrain_arrays.npz"
DEFAULT_FRAME = PLOT_TWIN_COE / "parcel_extract.json"
DEMO_OUT = HERE / "worlds" / "parcel-ground-demo.world.json"
DEM_ARRAY = "ground_1m"
DEM_CELL_M = 1.0
METRES_PER_FOOT = 0.3048
EPSG_PREFIX = "EPSG:"
DEMO_TILE_FT = 10
DEMO_COLS = 8
DEMO_ROWS = 8
DEMO_WINDOW_SW_LOCAL_M = (595.0, 49.0)
HEIGHTS_PLACEHOLDER = "__heights_mm__"


# ---------------------------------------------------------------- reading a DEM


def read_dem(path: Path, array: str | None = None) -> list[list[float]]:
    """Rows of elevations in metres, in the order the file stores them."""
    if path.suffix == ".csv":
        with path.open(newline="", encoding="utf-8") as handle:
            return [[float(value) for value in row] for row in csv.reader(handle) if row]
    import numpy
    loaded = numpy.load(path)
    grid = loaded[array] if path.suffix == ".npz" else loaded
    return grid.tolist()


def rows_south_first(dem: list[list[float]], dem_row0: str) -> list[list[float]]:
    if dem_row0 not in ("south", "north"):
        raise ValueError(f"dem_row0 is 'south' or 'north', not {dem_row0!r}")
    return dem if dem_row0 == "south" else list(reversed(dem))


# ---------------------------------------------------------------- sampling


def clamp(value: float, low: float, high: float) -> float:
    return min(max(value, low), high)


def sample_bilinear(south_rows: list[list[float]], cell_m: float, east_m: float, north_m: float) -> float:
    """The DEM at a point given in metres east and north of the DEM's outer south-west corner."""
    row_count, col_count = len(south_rows), len(south_rows[0])
    if not (0 <= east_m <= col_count * cell_m and 0 <= north_m <= row_count * cell_m):
        raise ValueError(f"a vertex at {east_m:.3f}, {north_m:.3f} m from the DEM's south-west corner lies outside the DEM "
                         f"({col_count * cell_m:g} x {row_count * cell_m:g} m)")
    across = clamp(east_m / cell_m - 0.5, 0, col_count - 1)
    up = clamp(north_m / cell_m - 0.5, 0, row_count - 1)
    west_col, south_row = min(int(across), col_count - 2), min(int(up), row_count - 2)
    east_share, north_share = across - west_col, up - south_row
    south_edge = south_rows[south_row][west_col] * (1 - east_share) + south_rows[south_row][west_col + 1] * east_share
    north_edge = south_rows[south_row + 1][west_col] * (1 - east_share) + south_rows[south_row + 1][west_col + 1] * east_share
    return south_edge * (1 - north_share) + north_edge * north_share


def vertex_offsets_m(world: dict, dem_origin: tuple[float, float]) -> list[tuple[float, float]]:
    """Each vertex, row by row from the north edge, in metres east and north of the DEM's south-west corner."""
    grid = world["grid"]
    frame = world.get("frame") or {"origin_east_m": 0.0, "origin_north_m": 0.0}
    tile_m = grid["tile_ft"] * METRES_PER_FOOT
    east_shift = frame["origin_east_m"] - dem_origin[0]
    north_shift = frame["origin_north_m"] - dem_origin[1]
    return [(east_shift + vc * tile_m, north_shift + (grid["rows"] - vr) * tile_m)
            for vr in range(grid["rows"] + 1) for vc in range(grid["cols"] + 1)]


def build_ground(world: dict, dem: list[list[float]], dem_origin: tuple[float, float], cell_m: float, dem_row0: str,
                 datum: str, source: str, null_reason: str) -> dict:
    south_rows = rows_south_first(dem, dem_row0)
    heights = [round(sample_bilinear(south_rows, cell_m, east, north) * 1000) for east, north in vertex_offsets_m(world, dem_origin)]
    return {"unit": "mm", "datum": datum, "source": source, "heights_mm": heights,
            "error": {"vertical_mm": {"value": None, "null_reason": null_reason}}}


def with_ground(world: dict, ground: dict) -> dict:
    """The world with its ground replaced, placed right after the grid and frame."""
    ordered = {}
    for key, value in world.items():
        if key == "ground":
            continue
        ordered[key] = value
        if key == ("frame" if "frame" in world else "grid"):
            ordered["ground"] = ground
    return ordered


# ---------------------------------------------------------------- the demo world from plot-twin's 1 m ground


def plot_twin_frame() -> tuple[int, float, float]:
    frame = json.loads(DEFAULT_FRAME.read_text(encoding="utf-8"))["frame"]
    return int(frame["crs"].removeprefix(EPSG_PREFIX)), frame["origin_easting_m"], frame["origin_northing_m"]


def plot_twin_dem_origin() -> tuple[float, float]:
    """The 1 m raster's outer south-west corner in UTM: plot-twin's frame origin plus the raster's stored extent."""
    import numpy
    extent = numpy.load(DEFAULT_DEM)["extent"].tolist()
    _, origin_east, origin_north = plot_twin_frame()
    return origin_east + extent[0], origin_north + extent[1]


def demo_frame() -> dict:
    epsg, origin_east, origin_north = plot_twin_frame()
    return {"epsg": epsg, "origin_east_m": round(origin_east + DEMO_WINDOW_SW_LOCAL_M[0], 3),
            "origin_north_m": round(origin_north + DEMO_WINDOW_SW_LOCAL_M[1], 3), "x_axis": "east", "y_axis": "north"}


DEMO_SOURCE = ("plot-twin capture/data/coe/probe/terrain_arrays.npz, array ground_1m: class-2 lidar returns of "
               "USGS_LPC_MI_31County_2016_A16 averaged per 1 m cell and gap-filled by a 2-cell gaussian "
               "(capture/scripts/probe_parcel_hydrology.py), rows stored south first; sampled bilinearly at tile corners "
               "by import_ground_from_dem.py")
DEMO_NULL_REASON = ("the survey states 60 mm RMSE (raw NVA, 18 checkpoints in flat open terrain; Isabella_LAS.xml vertaccv) "
                    "for the lidar surface; the error of a vertex interpolated from the gap-filled 1 m mean raster has not "
                    "been measured")


def demo_world_skeleton() -> dict:
    return {
        "id": "parcel-ground-demo",
        "version": 1,
        "title": "Parcel ground demo",
        "description": (f"{DEMO_COLS} x {DEMO_ROWS} tiles of {DEMO_TILE_FT} ft on the Wise Rd parcel, {DEMO_WINDOW_SW_LOCAL_M[0]:g} m east "
                        f"and {DEMO_WINDOW_SW_LOCAL_M[1]:g} m north of plot-twin's frame origin, with the lidar ground under "
                        f"them: the surface falls toward the north and holds a closed low basin inside the edge. Built by "
                        f"import_ground_from_dem.py --demo from plot-twin's 1 m ground raster. A commons building needs ground "
                        f"no steeper than 5 percent; dig and raise reshape the surface."),
        "sprites": {"extensions": []},
        "grid": {"cols": DEMO_COLS, "rows": DEMO_ROWS, "tile_ft": DEMO_TILE_FT, "shape": "square", "view": "iso", "north": "row_0"},
        "frame": demo_frame(),
        "clock": {"tick_unit": "day", "tick_length": 1},
        "object_types": [
            {"id": "commons_building", "label": "Commons building", "sprite": "block", "color": "#8E6E53", "footprint": [1, 1],
             "height_mm": 4000, "tags": ["structure"]},
            {"id": "garden", "label": "Garden bed", "sprite": "flat", "color": "#9CCC65", "footprint": [1, 1]},
        ],
        "rules": [
            {"id": "commons-on-gentle-ground", "on": "always", "applies_to": ["commons_building"], "require": "slope_pct() <= 5",
             "message": "a commons building needs ground no steeper than 5 percent"},
        ],
        "equations": [
            {"id": "lowest_ground", "expr": "min_ground_mm()", "unit": "mm"},
            {"id": "highest_ground", "expr": "max_ground_mm()", "unit": "mm"},
            {"id": "relief", "expr": "max_ground_mm() - min_ground_mm()", "unit": "mm"},
        ],
        "stocks": [],
        "actions": [
            {"verb": "place", "label": "Place", "parameters": [{"name": "type", "type": "object_type"}, {"name": "col", "type": "col"}, {"name": "row", "type": "row"}], "emits": ["place"]},
            {"verb": "remove", "label": "Remove", "parameters": [{"name": "col", "type": "col"}, {"name": "row", "type": "row"}], "emits": ["remove"]},
            {"verb": "dig", "label": "Dig", "parameters": [{"name": "col", "type": "col"}, {"name": "row", "type": "row"}, {"name": "depth_mm", "type": "depth_mm"}], "emits": ["dig"]},
            {"verb": "raise", "label": "Raise", "parameters": [{"name": "col", "type": "col"}, {"name": "row", "type": "row"}, {"name": "height_mm", "type": "height_mm"}], "emits": ["raise"]},
        ],
        "agents": [],
        "scoring": [],
        "links": [],
        "seed": [],
    }


def build_demo_world() -> dict:
    skeleton = demo_world_skeleton()
    ground = build_ground(skeleton, read_dem(DEFAULT_DEM, DEM_ARRAY), plot_twin_dem_origin(), DEM_CELL_M, "south",
                          "NAVD88 (GEOID12B)", DEMO_SOURCE, DEMO_NULL_REASON)
    return with_ground(skeleton, ground)


# ---------------------------------------------------------------- writing


def render_world(world: dict) -> str:
    """JSON with two-space indent, the vertex heights one vertex row per line."""
    heights = world["ground"]["heights_mm"]
    vertex_cols = world["grid"]["cols"] + 1
    placeholder = {**world, "ground": {**world["ground"], "heights_mm": HEIGHTS_PLACEHOLDER}}
    text = json.dumps(placeholder, indent=2, ensure_ascii=False) + "\n"
    lines = [", ".join(str(h) for h in heights[start:start + vertex_cols]) for start in range(0, len(heights), vertex_cols)]
    block = "[\n      " + ",\n      ".join(lines) + "\n    ]"
    return text.replace(json.dumps(HEIGHTS_PLACEHOLDER), block)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--demo", action="store_true", help="rebuild worlds/parcel-ground-demo.world.json from plot-twin")
    parser.add_argument("--world", type=Path, help="the world whose grid and frame the ground is built for")
    parser.add_argument("--dem", type=Path, help="a .csv, .npy or .npz of elevations in metres")
    parser.add_argument("--array", help="the array inside an .npz")
    parser.add_argument("--dem-origin-east-m", type=float)
    parser.add_argument("--dem-origin-north-m", type=float)
    parser.add_argument("--cell-m", type=float)
    parser.add_argument("--dem-row0", choices=("south", "north"), default="north")
    parser.add_argument("--datum")
    parser.add_argument("--source")
    parser.add_argument("--error-null-reason", help="why the vertex error is not a number")
    parser.add_argument("--out", type=Path)
    options = parser.parse_args(argv)
    if options.demo:
        world, out = build_demo_world(), options.out or DEMO_OUT
    else:
        required = ("world", "dem", "dem_origin_east_m", "dem_origin_north_m", "cell_m", "datum", "source", "error_null_reason", "out")
        missing = [name for name in required if getattr(options, name) is None]
        if missing:
            parser.error(f"without --demo these are required: {', '.join('--' + m.replace('_', '-') for m in missing)}")
        target = json.loads(options.world.read_text(encoding="utf-8"))
        ground = build_ground(target, read_dem(options.dem, options.array), (options.dem_origin_east_m, options.dem_origin_north_m),
                              options.cell_m, options.dem_row0, options.datum, options.source, options.error_null_reason)
        world, out = with_ground(target, ground), options.out
    out.write_text(render_world(world), encoding="utf-8")
    heights = world["ground"]["heights_mm"]
    print(f"wrote {len(heights)} vertex heights, {min(heights)} to {max(heights)} mm, to {out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
