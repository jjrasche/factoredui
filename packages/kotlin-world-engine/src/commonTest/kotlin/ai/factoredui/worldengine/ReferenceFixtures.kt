package ai.factoredui.worldengine

const val PARCEL_WORLD_JSON: String = """{
  "id": "parcel-five-acre",
  "version": 1,
  "title": "Five-acre parcel",
  "description": "The offered portion as a 13 by 26 grid of 25 ft tiles (625 sq ft each, 4.85 acres), the seven uses of the farm, the rules each placement must pass, and the figures the task-model twin can back. It links to a locality parcel and inherits that locality's structure setback.",
  "sprites": {"extensions": []},
  "grid": {"cols": 13, "rows": 26, "tile_ft": 25, "shape": "square", "view": "iso", "north": "row_0"},
  "clock": {"tick_unit": "day", "tick_length": 1},
  "object_types": [
    {
      "id": "paddock",
      "label": "Paddock",
      "sprite": "fence",
      "color": "#9CCB6B",
      "footprint": [1, 1],
      "tags": ["open"],
      "properties": [
        {"name": "annual_forage_yield", "unit": "ton/acre/year", "default": 4.0, "kind": "yield",
         "source": {"twin": "eq-aux-stocking-static", "binding": "mi_grass_clover_tons_per_acre", "row": "yield-mi-grass-clover-4.0t"},
         "note": "the twin's bound Michigan grass-clover annual herbage figure"},
        {"name": "forage_dm_per_tile", "unit": "lb/tile/year", "default": 114.7842056932966, "kind": "yield",
         "source": {"twin": "twin-constants.json#paddock.yield_kg_dm_per_tile_per_year", "row": "yield-mi-grass-clover-4.0t"},
         "note": "52.0652398989899 kg dry matter per tile a year, shown in lb because the engine's unit table has no kg"},
        {"name": "labor_hours_per_use", "unit": "hour/year", "default": 38.25, "kind": "labor",
         "source": {"twin": "twin-constants.json#paddock.hours_per_year_per_use", "row": "uw-moving-cattle-15-min"},
         "note": "one herd moved daily through the grazing season, charged once when any paddock exists"},
        {"name": "capex_per_use", "unit": "usd", "default": 219.98, "kind": "price",
         "source": {"twin": "twin-constants.json#paddock.capex_usd_per_use", "row": "cap-fence-energizer"},
         "note": "energizer 129.99 and stock tank 89.99 (row cap-stock-tank-110), up front, once"}
      ]
    },
    {
      "id": "hoop_house",
      "label": "Hoop house",
      "sprite": "arch",
      "color": "#E8EEF4",
      "footprint": [2, 1],
      "tags": ["structure"],
      "properties": [
        {"name": "labor_hours_per_tile", "unit": "hour/tile/year", "default": 24.576822916666668, "kind": "labor",
         "source": {"twin": "twin-constants.json#hoop_house.hours_per_year_per_tile", "row": "uky-tunnel-labor-hours"},
         "note": "75.5 hours a year for the row's 96 by 20 ft house, scaled by area to one 625 sq ft tile"}
      ]
    },
    {
      "id": "commons_building",
      "label": "Commons building",
      "sprite": "block",
      "color": "#C98B5A",
      "footprint": [2, 2],
      "tags": ["structure", "tall"]
    },
    {
      "id": "van_pad",
      "label": "Van pad",
      "sprite": "flat",
      "color": "#B7B0A4",
      "footprint": [1, 1],
      "tags": ["parking"]
    },
    {
      "id": "path",
      "label": "Path",
      "sprite": "flat",
      "color": "#D9C9A3",
      "footprint": [1, 1],
      "tags": ["access", "open"]
    },
    {
      "id": "pond",
      "label": "Pond",
      "sprite": "water",
      "color": "#5B9BD5",
      "footprint": [1, 1],
      "tags": ["water", "open"]
    },
    {
      "id": "woodland_tree",
      "label": "Woodland tree",
      "sprite": "tree",
      "color": "#3F7D3A",
      "footprint": [1, 1],
      "tags": ["tall"]
    }
  ],
  "rules": [
    {
      "id": "van-pad-needs-path",
      "on": "always",
      "applies_to": ["van_pad"],
      "require": "neighbors(tile, 1 [tile], 'path') >= 1 [tile]",
      "message": "a van pad needs a path on one of its four sides"
    },
    {
      "id": "hoop-house-open-south",
      "on": "place",
      "applies_to": ["hoop_house"],
      "require": "side(tile, 'south', '#tall') == 0 [tile]",
      "message": "a hoop house needs an open south side: nothing tall directly south of it"
    },
    {
      "id": "tall-not-south-of-hoop-house",
      "on": "place",
      "applies_to_tag": "tall",
      "require": "side(tile, 'north', 'hoop_house') == 0 [tile]",
      "message": "a tall object would shade the hoop house directly north of it"
    }
  ],
  "equations": [
    {
      "id": "pasture_yield",
      "expr": "count('paddock') * tile_area * paddock.annual_forage_yield",
      "unit": "ton/year",
      "source": {"twin": "eq-aux-stocking-static", "binding": "mi_grass_clover_tons_per_acre"},
      "note": "paddock area times the twin's annual herbage figure: the static stocking equation's forage term, not the twin's day-by-day growth"
    }
  ],
  "stocks": [
    {
      "id": "standing_forage",
      "unit": "ton",
      "initial": 0,
      "next": "standing_forage + pasture_yield * tick_length",
      "source": {"placeholder": true, "reason": "linear accrual of the annual figure, with no grazing; the twin's eq-state-herbage_mass driven by eq-aux-growth replaces it"}
    }
  ],
  "actions": [
    {"verb": "place", "label": "Place", "parameters": [{"name": "type", "type": "object_type"}, {"name": "col", "type": "col"}, {"name": "row", "type": "row"}], "emits": ["place"]},
    {"verb": "remove", "label": "Remove", "parameters": [{"name": "col", "type": "col"}, {"name": "row", "type": "row"}], "emits": ["remove"]},
    {"verb": "tick", "label": "Advance days", "parameters": [{"name": "n", "type": "count"}], "emits": ["tick"]},
    {"verb": "enroll", "label": "Add a neighbour", "parameters": [{"name": "agent_type", "type": "agent_type"}, {"name": "agent_id", "type": "agent_id"}, {"name": "synthetic", "type": "boolean"}, {"name": "attributes", "type": "attributes"}], "emits": ["enroll"]},
    {"verb": "opt_in", "label": "A real person takes the place of a synthetic one", "parameters": [{"name": "agent_id", "type": "agent_id"}, {"name": "attributes", "type": "attributes"}], "emits": ["opt_in"]}
  ],
  "agents": [
    {
      "type": "neighbor",
      "label": "Neighbour",
      "attributes": [
        {"name": "distance_ft", "unit": "ft", "default": 1320},
        {"name": "wants_food", "unit": "1", "default": 0},
        {"name": "van_aversion", "unit": "1", "default": 0}
      ],
      "weight": "1 / (1 + self.distance_ft / 1320 [ft])",
      "utility": "self.wants_food * pasture_yield / 1 [ton/year] - self.van_aversion * count('van_pad') / 1 [tile]",
      "vote": "projection",
      "source": {"placeholder": true, "reason": "no choices are collected yet; the matchmaking design's estimate waits for 50 people at 10 tasks each, so these coefficients are a shape, not a fit"}
    }
  ],
  "scoring": [
    {"id": "area_paddock", "label": "Paddock area", "expr": "count('paddock') * tile_area", "unit": "sq_ft"},
    {"id": "area_hoop_house", "label": "Hoop house area", "expr": "count('hoop_house') * tile_area", "unit": "sq_ft"},
    {"id": "area_commons_building", "label": "Commons building area", "expr": "count('commons_building') * tile_area", "unit": "sq_ft"},
    {"id": "area_van_pad", "label": "Van pad area", "expr": "count('van_pad') * tile_area", "unit": "sq_ft"},
    {"id": "area_path", "label": "Path area", "expr": "count('path') * tile_area", "unit": "sq_ft"},
    {"id": "area_pond", "label": "Pond area", "expr": "count('pond') * tile_area", "unit": "sq_ft"},
    {"id": "area_woodland_tree", "label": "Woodland tree area", "expr": "count('woodland_tree') * tile_area", "unit": "sq_ft"},
    {"id": "pasture_yield_annual", "label": "Pasture yield", "expr": "pasture_yield", "unit": "ton/year",
     "source": {"twin": "eq-aux-stocking-static"}},
    {"id": "hoop_house_labor", "label": "Hoop house labour", "expr": "count('hoop_house') * hoop_house.labor_hours_per_tile", "unit": "hour/year",
     "source": {"twin": "twin-constants.json#hoop_house.hours_per_year_per_tile", "row": "uky-tunnel-labor-hours"}},
    {"id": "labor_hours_total", "label": "Labour hours", "expr": "hoop_house_labor + if(count('paddock') > 0 [tile], paddock.labor_hours_per_use, 0 [hour/year])", "unit": "hour/year",
     "source": {"twin": "twin-constants.json"},
     "note": "only the uses with a cited labor figure: hoop-house tiles and one herd move; every other use counts zero until a row times it"},
    {"id": "capex_floor", "label": "Sourced capital floor", "expr": "if(count('paddock') > 0 [tile], paddock.capex_per_use, 0 [usd])", "unit": "usd",
     "source": {"twin": "twin-constants.json#paddock.capex_usd_per_use"},
     "note": "a floor: banked up-front prices only; the hoop house, buildings, pads, paths, pond and trees have no banked price and count zero"},
    {"id": "paddock_dm_yield", "label": "Paddock dry matter", "expr": "count('paddock') * paddock.forage_dm_per_tile", "unit": "lb/year",
     "source": {"twin": "twin-constants.json#paddock.yield_kg_dm_per_tile_per_year", "row": "yield-mi-grass-clover-4.0t"}},
    {"id": "neighbor_support", "label": "Projected neighbour support", "expr": "projected_support('neighbor')", "unit": "1",
     "binding": false, "note": "a projection from synthetic and opted-in neighbours; never a vote"}
  ],
  "links": [
    {"parent": "locality-stub.world.json", "parcel": "parcel-a", "as": "parcel", "inherit": ["structure-setback"]}
  ],
  "seed": [
    {"id": "neighbor-1", "action": "enroll", "parameters": {"agent_type": "neighbor", "agent_id": "neighbor-1", "synthetic": true, "attributes": {"distance_ft": 600, "wants_food": 1, "van_aversion": 0}},
     "note": "illustrative synthetic neighbour; no Census draw has run"},
    {"id": "neighbor-2", "action": "enroll", "parameters": {"agent_type": "neighbor", "agent_id": "neighbor-2", "synthetic": true, "attributes": {"distance_ft": 1500, "wants_food": 0, "van_aversion": 1}},
     "note": "illustrative synthetic neighbour; no Census draw has run"},
    {"id": "neighbor-3", "action": "enroll", "parameters": {"agent_type": "neighbor", "agent_id": "neighbor-3", "synthetic": true, "attributes": {"distance_ft": 3000, "wants_food": 1, "van_aversion": 0}},
     "note": "illustrative synthetic neighbour; no Census draw has run"}
  ]
}
"""

const val LOCALITY_WORLD_JSON: String = """{
  "id": "locality-stub",
  "version": 1,
  "title": "Locality stub",
  "description": "A township at the zoom of parcels and roads. It exports one rule to the parcels that link to it (a structure setback) and shows one parcel-level override. Every figure is a declared placeholder: this is not Bowne Township's ordinance.",
  "sprites": {
    "extensions": [
      {"id": "road", "base": "flat", "note": "a paved or gravel road segment"}
    ]
  },
  "grid": {"cols": 10, "rows": 10, "tile_ft": 330, "shape": "square", "view": "top", "north": "row_0"},
  "clock": {"tick_unit": "year", "tick_length": 1},
  "object_types": [
    {
      "id": "road",
      "label": "Road",
      "sprite": "road",
      "color": "#8A8A8A",
      "footprint": [1, 1],
      "tags": ["access"]
    },
    {
      "id": "parcel",
      "label": "Parcel",
      "sprite": "flat",
      "color": "#C9D9A6",
      "footprint": [1, 2],
      "tags": ["land"],
      "properties": [
        {"name": "zoning", "type": "text", "default": "AG", "enum": ["AG", "R1"], "kind": "regulation",
         "source": {"placeholder": true, "reason": "stub district; the real district comes from the township's zoning map"}},
        {"name": "structure_setback", "unit": "ft", "default": 50, "kind": "regulation",
         "source": {"placeholder": true, "reason": "stub district setback; no banked ordinance row is cited here"}}
      ]
    },
    {
      "id": "census_block",
      "label": "Census block",
      "sprite": "flat",
      "color": "#E4D7F2",
      "footprint": [1, 1],
      "tags": ["demographics"],
      "properties": [
        {"name": "population", "unit": "head", "default": 0, "kind": "demographic",
         "source": {"placeholder": true, "reason": "no Census draw has run; ACS block-group counts belong here and synthetic agents are drawn from them"}}
      ]
    }
  ],
  "rules": [
    {
      "id": "parcel-needs-road",
      "on": "place",
      "applies_to": ["parcel"],
      "require": "neighbors(tile, 1 [tile], 'road') >= 1 [tile]",
      "message": "a parcel must touch a road"
    },
    {
      "id": "structure-setback",
      "on": "place",
      "scope": "child",
      "applies_to_tag": "structure",
      "require": "edge(tile) >= parcel.structure_setback",
      "message": "a structure must stand at least the parcel's setback from the lot line",
      "source": {"placeholder": true, "reason": "the shape of a setback rule; its distance is the parcel's structure_setback"}
    }
  ],
  "equations": [],
  "stocks": [],
  "actions": [
    {"verb": "place", "label": "Place", "parameters": [{"name": "type", "type": "object_type"}, {"name": "col", "type": "col"}, {"name": "row", "type": "row"}, {"name": "properties", "type": "properties"}], "emits": ["place"]},
    {"verb": "remove", "label": "Remove", "parameters": [{"name": "col", "type": "col"}, {"name": "row", "type": "row"}], "emits": ["remove"]},
    {"verb": "tick", "label": "Advance", "parameters": [{"name": "n", "type": "count"}], "emits": ["tick"]}
  ],
  "agents": [],
  "scoring": [
    {"id": "parcel_area", "label": "Parcel area", "expr": "count('parcel') * tile_area", "unit": "acre"},
    {"id": "population", "label": "Population", "expr": "sum('population')", "unit": "head"}
  ],
  "links": [],
  "seed": [
    {"id": "road-0", "action": "place", "parameters": {"type": "road", "col": 0, "row": 0}},
    {"id": "road-1", "action": "place", "parameters": {"type": "road", "col": 0, "row": 1}},
    {"id": "road-2", "action": "place", "parameters": {"type": "road", "col": 0, "row": 2}},
    {"id": "road-3", "action": "place", "parameters": {"type": "road", "col": 0, "row": 3}},
    {"id": "parcel-a", "action": "place", "parameters": {"type": "parcel", "col": 1, "row": 0},
     "note": "takes the district setback"},
    {"id": "parcel-b", "action": "place", "parameters": {"type": "parcel", "col": 1, "row": 2, "properties": {"structure_setback": 25}},
     "note": "parcel-level override: an illustrative variance halving the setback, not a real record"},
    {"id": "block-1", "action": "place", "parameters": {"type": "census_block", "col": 5, "row": 5}}
  ]
}
"""

const val DUNGEON_WORLD_JSON: String = """{
  "id": "dungeon-tiny",
  "version": 1,
  "title": "Tiny dungeon",
  "description": "A second world on the same engine: five-foot squares, walls, floor, doors, monsters and treasure. Nothing in the engine knows about farms or dungeons.",
  "sprites": {
    "extensions": [
      {"id": "figure", "base": "block", "note": "a standing creature"},
      {"id": "chest", "base": "block", "note": "a treasure chest"}
    ]
  },
  "grid": {"cols": 12, "rows": 8, "tile_ft": 5, "shape": "square", "view": "top", "north": "row_0"},
  "clock": {"tick_unit": "second", "tick_length": 6},
  "object_types": [
    {"id": "wall", "label": "Wall", "sprite": "block", "color": "#4A4A55", "footprint": [1, 1], "tags": ["solid"]},
    {"id": "floor", "label": "Floor", "sprite": "flat", "color": "#C2B280", "footprint": [1, 1], "tags": ["walkable"]},
    {"id": "door", "label": "Door", "sprite": "arch", "color": "#7B4A2A", "footprint": [1, 1], "tags": ["walkable"]},
    {"id": "monster", "label": "Monster", "sprite": "figure", "color": "#B03A2E", "footprint": [1, 1], "tags": ["creature"]},
    {
      "id": "treasure",
      "label": "Treasure",
      "sprite": "chest",
      "color": "#D4AF37",
      "footprint": [1, 1],
      "tags": ["loot"],
      "properties": [
        {"name": "guarded", "unit": "1", "default": 0, "kind": "state", "note": "1 when a monster stood beside it as it was placed"}
      ]
    }
  ],
  "rules": [
    {
      "id": "door-connects-floors",
      "on": "place",
      "applies_to": ["door"],
      "require": "side(tile, 'north', 'floor') + side(tile, 'south', 'floor') == 2 [tile] or side(tile, 'east', 'floor') + side(tile, 'west', 'floor') == 2 [tile]",
      "message": "a door must connect two floor tiles on opposite sides"
    },
    {
      "id": "monster-stands-by-floor",
      "on": "place",
      "applies_to": ["monster"],
      "require": "neighbors(tile, 1 [tile], 'floor') >= 1 [tile]",
      "message": "a monster must stand beside a floor tile"
    },
    {
      "id": "treasure-guarded",
      "on": "place",
      "applies_to": ["treasure"],
      "effect": {"set": "guarded", "to": "if(neighbors(tile, 1 [tile], 'monster') >= 1 [tile], 1, 0)"}
    }
  ],
  "equations": [],
  "stocks": [],
  "actions": [
    {"verb": "place", "label": "Place", "parameters": [{"name": "type", "type": "object_type"}, {"name": "col", "type": "col"}, {"name": "row", "type": "row"}], "emits": ["place"]},
    {"verb": "remove", "label": "Remove", "parameters": [{"name": "col", "type": "col"}, {"name": "row", "type": "row"}], "emits": ["remove"]},
    {"verb": "tick", "label": "Next round", "parameters": [{"name": "n", "type": "count"}], "emits": ["tick"]}
  ],
  "agents": [],
  "scoring": [
    {"id": "treasure_per_monster", "label": "Treasure per monster", "expr": "if(count('monster') > 0 [tile], count('treasure') / count('monster'), 0)", "unit": "1"},
    {"id": "guarded_treasure", "label": "Guarded treasure", "expr": "sum('guarded')", "unit": "1"},
    {"id": "walkable_area", "label": "Walkable area", "expr": "count('#walkable') * tile_area", "unit": "sq_ft"}
  ],
  "links": [],
  "seed": []
}
"""

const val LIDAR_WORLD_JSON: String = """{
  "id": "parcel-lidar-sample",
  "version": 1,
  "title": "Parcel lidar sample",
  "description": "Ten lidar trees of parcel 04003630009000 (Isaac, 11157 W Jolly Rd, Delta Twp, MI) as measured instances at their apex-return millimetre positions, placed on the earth by the frame. The grid is the trees' bounding box from the frame origin, rounded up to whole 25 ft tiles; it is not the parcel boundary. Built by import_plot_twin_trees.py from plot-twin/capture/receipts/tree-sample-for-van-build-v2.json.",
  "sprites": {
    "extensions": []
  },
  "grid": {
    "cols": 5,
    "rows": 32,
    "tile_ft": 25,
    "shape": "square",
    "view": "top",
    "north": "row_0"
  },
  "frame": {
    "epsg": 26916,
    "origin_east_m": 695000.7,
    "origin_north_m": 4728383.3,
    "x_axis": "east",
    "y_axis": "north"
  },
  "clock": {
    "tick_unit": "day",
    "tick_length": 1
  },
  "object_types": [
    {
      "id": "lidar_tree",
      "label": "Lidar tree",
      "sprite": "tree",
      "color": "#2E7D32",
      "footprint": [
        1,
        1
      ],
      "tags": [
        "tree"
      ]
    },
    {
      "id": "shed",
      "label": "Shed",
      "sprite": "block",
      "color": "#A0522D",
      "footprint": [
        1,
        1
      ],
      "footprint_mm": [
        2400,
        3000
      ],
      "height_mm": 2700,
      "tags": [
        "structure"
      ]
    }
  ],
  "rules": [],
  "equations": [
    {
      "id": "tree_count",
      "expr": "count_instances('#tree')",
      "unit": "1"
    },
    {
      "id": "nearest_tree_pair",
      "expr": "min_distance_mm('#tree', '#tree')",
      "unit": "mm"
    }
  ],
  "stocks": [],
  "actions": [
    {
      "verb": "place",
      "label": "Place",
      "parameters": [
        {
          "name": "type",
          "type": "object_type"
        },
        {
          "name": "col",
          "type": "col"
        },
        {
          "name": "row",
          "type": "row"
        }
      ],
      "emits": [
        "place"
      ]
    },
    {
      "verb": "remove",
      "label": "Remove",
      "parameters": [
        {
          "name": "col",
          "type": "col"
        },
        {
          "name": "row",
          "type": "row"
        }
      ],
      "emits": [
        "remove"
      ]
    },
    {
      "verb": "place_instance",
      "label": "Place at a point",
      "parameters": [
        {
          "name": "type",
          "type": "object_type"
        },
        {
          "name": "x_mm",
          "type": "x_mm"
        },
        {
          "name": "y_mm",
          "type": "y_mm"
        },
        {
          "name": "rotation_deg",
          "type": "rotation_deg"
        }
      ],
      "emits": [
        "place_instance"
      ]
    },
    {
      "verb": "remove_instance",
      "label": "Remove a point object",
      "parameters": [
        {
          "name": "id",
          "type": "instance_id"
        }
      ],
      "emits": [
        "remove_instance"
      ]
    }
  ],
  "agents": [],
  "scoring": [],
  "links": [],
  "seed": [
    {
      "id": "tree-01",
      "action": "place_instance",
      "parameters": {
        "type": "lidar_tree",
        "x_mm": 12147,
        "y_mm": 238379,
        "height_mm": 29650,
        "crown_radius_mm": 6000,
        "provenance": "measured",
        "source": {
          "file": "plot-twin/capture/receipts/tree-sample-for-van-build-v2.json#/trees/0",
          "tag": "USGS 3DEP QL2 MI_31County_2016_A16, flown 2017-12-01 to 2018-04-23 per the vendor metadata (leaf-off, inferred); extract_features.py with the ground grid row order corrected (branch fix/ground-north-south-flip): 102 trees; every 10th in height order, tallest first; position is the apex return (highest return in the 3x3 cells around the 1 m CHM peak), the crown apex and not the stem; the tree count is not validated against a stem map"
        },
        "error": {
          "position_mm": {
            "value": null,
            "null_reason": "no detection error has been measured on this parcel (procedure step s9 not run)",
            "note": "the position is the crown apex; a published mean apex-to-stem offset for hardwoods is 950 mm (row gatziolis10-apex-stem-offset, another study's site, not this parcel)"
          },
          "height_mm": {
            "value": null,
            "null_reason": "no field heights were measured on this parcel; the CHM peak and the apex return are two readings of the same returns, not a check of accuracy"
          },
          "crown_radius_mm": {
            "value": null,
            "null_reason": "half-height ray walk clipped to 1 to 6 m; no crown error has been measured on this parcel; 6000 is the clip, so the true radius is at least 6000 mm"
          }
        }
      }
    },
    {
      "id": "tree-02",
      "action": "place_instance",
      "parameters": {
        "type": "lidar_tree",
        "x_mm": 3175,
        "y_mm": 229566,
        "height_mm": 24920,
        "crown_radius_mm": 5030,
        "provenance": "measured",
        "source": {
          "file": "plot-twin/capture/receipts/tree-sample-for-van-build-v2.json#/trees/1",
          "tag": "USGS 3DEP QL2 MI_31County_2016_A16, flown 2017-12-01 to 2018-04-23 per the vendor metadata (leaf-off, inferred); extract_features.py with the ground grid row order corrected (branch fix/ground-north-south-flip): 102 trees; every 10th in height order, tallest first; position is the apex return (highest return in the 3x3 cells around the 1 m CHM peak), the crown apex and not the stem; the tree count is not validated against a stem map"
        },
        "error": {
          "position_mm": {
            "value": null,
            "null_reason": "no detection error has been measured on this parcel (procedure step s9 not run)",
            "note": "the position is the crown apex; a published mean apex-to-stem offset for hardwoods is 950 mm (row gatziolis10-apex-stem-offset, another study's site, not this parcel)"
          },
          "height_mm": {
            "value": null,
            "null_reason": "no field heights were measured on this parcel; the CHM peak and the apex return are two readings of the same returns, not a check of accuracy"
          },
          "crown_radius_mm": {
            "value": null,
            "null_reason": "half-height ray walk clipped to 1 to 6 m; no crown error has been measured on this parcel"
          }
        }
      }
    },
    {
      "id": "tree-03",
      "action": "place_instance",
      "parameters": {
        "type": "lidar_tree",
        "x_mm": 15416,
        "y_mm": 220615,
        "height_mm": 22290,
        "crown_radius_mm": 5760,
        "provenance": "measured",
        "source": {
          "file": "plot-twin/capture/receipts/tree-sample-for-van-build-v2.json#/trees/2",
          "tag": "USGS 3DEP QL2 MI_31County_2016_A16, flown 2017-12-01 to 2018-04-23 per the vendor metadata (leaf-off, inferred); extract_features.py with the ground grid row order corrected (branch fix/ground-north-south-flip): 102 trees; every 10th in height order, tallest first; position is the apex return (highest return in the 3x3 cells around the 1 m CHM peak), the crown apex and not the stem; the tree count is not validated against a stem map"
        },
        "error": {
          "position_mm": {
            "value": null,
            "null_reason": "no detection error has been measured on this parcel (procedure step s9 not run)",
            "note": "the position is the crown apex; a published mean apex-to-stem offset for hardwoods is 950 mm (row gatziolis10-apex-stem-offset, another study's site, not this parcel)"
          },
          "height_mm": {
            "value": null,
            "null_reason": "no field heights were measured on this parcel; the CHM peak and the apex return are two readings of the same returns, not a check of accuracy"
          },
          "crown_radius_mm": {
            "value": null,
            "null_reason": "half-height ray walk clipped to 1 to 6 m; no crown error has been measured on this parcel"
          }
        }
      }
    },
    {
      "id": "tree-04",
      "action": "place_instance",
      "parameters": {
        "type": "lidar_tree",
        "x_mm": 4308,
        "y_mm": 201145,
        "height_mm": 21240,
        "crown_radius_mm": 5380,
        "provenance": "measured",
        "source": {
          "file": "plot-twin/capture/receipts/tree-sample-for-van-build-v2.json#/trees/3",
          "tag": "USGS 3DEP QL2 MI_31County_2016_A16, flown 2017-12-01 to 2018-04-23 per the vendor metadata (leaf-off, inferred); extract_features.py with the ground grid row order corrected (branch fix/ground-north-south-flip): 102 trees; every 10th in height order, tallest first; position is the apex return (highest return in the 3x3 cells around the 1 m CHM peak), the crown apex and not the stem; the tree count is not validated against a stem map"
        },
        "error": {
          "position_mm": {
            "value": null,
            "null_reason": "no detection error has been measured on this parcel (procedure step s9 not run)",
            "note": "the position is the crown apex; a published mean apex-to-stem offset for hardwoods is 950 mm (row gatziolis10-apex-stem-offset, another study's site, not this parcel)"
          },
          "height_mm": {
            "value": null,
            "null_reason": "no field heights were measured on this parcel; the CHM peak and the apex return are two readings of the same returns, not a check of accuracy"
          },
          "crown_radius_mm": {
            "value": null,
            "null_reason": "half-height ray walk clipped to 1 to 6 m; no crown error has been measured on this parcel"
          }
        }
      }
    },
    {
      "id": "tree-05",
      "action": "place_instance",
      "parameters": {
        "type": "lidar_tree",
        "x_mm": 2642,
        "y_mm": 196695,
        "height_mm": 20010,
        "crown_radius_mm": 4220,
        "provenance": "measured",
        "source": {
          "file": "plot-twin/capture/receipts/tree-sample-for-van-build-v2.json#/trees/4",
          "tag": "USGS 3DEP QL2 MI_31County_2016_A16, flown 2017-12-01 to 2018-04-23 per the vendor metadata (leaf-off, inferred); extract_features.py with the ground grid row order corrected (branch fix/ground-north-south-flip): 102 trees; every 10th in height order, tallest first; position is the apex return (highest return in the 3x3 cells around the 1 m CHM peak), the crown apex and not the stem; the tree count is not validated against a stem map"
        },
        "error": {
          "position_mm": {
            "value": null,
            "null_reason": "no detection error has been measured on this parcel (procedure step s9 not run)",
            "note": "the position is the crown apex; a published mean apex-to-stem offset for hardwoods is 950 mm (row gatziolis10-apex-stem-offset, another study's site, not this parcel)"
          },
          "height_mm": {
            "value": null,
            "null_reason": "no field heights were measured on this parcel; the CHM peak and the apex return are two readings of the same returns, not a check of accuracy"
          },
          "crown_radius_mm": {
            "value": null,
            "null_reason": "half-height ray walk clipped to 1 to 6 m; no crown error has been measured on this parcel"
          }
        }
      }
    },
    {
      "id": "tree-06",
      "action": "place_instance",
      "parameters": {
        "type": "lidar_tree",
        "x_mm": 7874,
        "y_mm": 193012,
        "height_mm": 21320,
        "crown_radius_mm": 6000,
        "provenance": "measured",
        "source": {
          "file": "plot-twin/capture/receipts/tree-sample-for-van-build-v2.json#/trees/5",
          "tag": "USGS 3DEP QL2 MI_31County_2016_A16, flown 2017-12-01 to 2018-04-23 per the vendor metadata (leaf-off, inferred); extract_features.py with the ground grid row order corrected (branch fix/ground-north-south-flip): 102 trees; every 10th in height order, tallest first; position is the apex return (highest return in the 3x3 cells around the 1 m CHM peak), the crown apex and not the stem; the tree count is not validated against a stem map"
        },
        "error": {
          "position_mm": {
            "value": null,
            "null_reason": "no detection error has been measured on this parcel (procedure step s9 not run)",
            "note": "the position is the crown apex; a published mean apex-to-stem offset for hardwoods is 950 mm (row gatziolis10-apex-stem-offset, another study's site, not this parcel)"
          },
          "height_mm": {
            "value": null,
            "null_reason": "no field heights were measured on this parcel; the CHM peak and the apex return are two readings of the same returns, not a check of accuracy"
          },
          "crown_radius_mm": {
            "value": null,
            "null_reason": "half-height ray walk clipped to 1 to 6 m; no crown error has been measured on this parcel; 6000 is the clip, so the true radius is at least 6000 mm"
          }
        }
      }
    },
    {
      "id": "tree-07",
      "action": "place_instance",
      "parameters": {
        "type": "lidar_tree",
        "x_mm": 7251,
        "y_mm": 79818,
        "height_mm": 17160,
        "crown_radius_mm": 6000,
        "provenance": "measured",
        "source": {
          "file": "plot-twin/capture/receipts/tree-sample-for-van-build-v2.json#/trees/6",
          "tag": "USGS 3DEP QL2 MI_31County_2016_A16, flown 2017-12-01 to 2018-04-23 per the vendor metadata (leaf-off, inferred); extract_features.py with the ground grid row order corrected (branch fix/ground-north-south-flip): 102 trees; every 10th in height order, tallest first; position is the apex return (highest return in the 3x3 cells around the 1 m CHM peak), the crown apex and not the stem; the tree count is not validated against a stem map"
        },
        "error": {
          "position_mm": {
            "value": null,
            "null_reason": "no detection error has been measured on this parcel (procedure step s9 not run)",
            "note": "the position is the crown apex; a published mean apex-to-stem offset for hardwoods is 950 mm (row gatziolis10-apex-stem-offset, another study's site, not this parcel)"
          },
          "height_mm": {
            "value": null,
            "null_reason": "no field heights were measured on this parcel; the CHM peak and the apex return are two readings of the same returns, not a check of accuracy"
          },
          "crown_radius_mm": {
            "value": null,
            "null_reason": "half-height ray walk clipped to 1 to 6 m; no crown error has been measured on this parcel; 6000 is the clip, so the true radius is at least 6000 mm"
          }
        }
      }
    },
    {
      "id": "tree-08",
      "action": "place_instance",
      "parameters": {
        "type": "lidar_tree",
        "x_mm": 7371,
        "y_mm": 105405,
        "height_mm": 18590,
        "crown_radius_mm": 5260,
        "provenance": "measured",
        "source": {
          "file": "plot-twin/capture/receipts/tree-sample-for-van-build-v2.json#/trees/7",
          "tag": "USGS 3DEP QL2 MI_31County_2016_A16, flown 2017-12-01 to 2018-04-23 per the vendor metadata (leaf-off, inferred); extract_features.py with the ground grid row order corrected (branch fix/ground-north-south-flip): 102 trees; every 10th in height order, tallest first; position is the apex return (highest return in the 3x3 cells around the 1 m CHM peak), the crown apex and not the stem; the tree count is not validated against a stem map"
        },
        "error": {
          "position_mm": {
            "value": null,
            "null_reason": "no detection error has been measured on this parcel (procedure step s9 not run)",
            "note": "the position is the crown apex; a published mean apex-to-stem offset for hardwoods is 950 mm (row gatziolis10-apex-stem-offset, another study's site, not this parcel)"
          },
          "height_mm": {
            "value": null,
            "null_reason": "no field heights were measured on this parcel; the CHM peak and the apex return are two readings of the same returns, not a check of accuracy"
          },
          "crown_radius_mm": {
            "value": null,
            "null_reason": "half-height ray walk clipped to 1 to 6 m; no crown error has been measured on this parcel"
          }
        }
      }
    },
    {
      "id": "tree-09",
      "action": "place_instance",
      "parameters": {
        "type": "lidar_tree",
        "x_mm": 32996,
        "y_mm": 145308,
        "height_mm": 14220,
        "crown_radius_mm": 3320,
        "provenance": "measured",
        "source": {
          "file": "plot-twin/capture/receipts/tree-sample-for-van-build-v2.json#/trees/8",
          "tag": "USGS 3DEP QL2 MI_31County_2016_A16, flown 2017-12-01 to 2018-04-23 per the vendor metadata (leaf-off, inferred); extract_features.py with the ground grid row order corrected (branch fix/ground-north-south-flip): 102 trees; every 10th in height order, tallest first; position is the apex return (highest return in the 3x3 cells around the 1 m CHM peak), the crown apex and not the stem; the tree count is not validated against a stem map"
        },
        "error": {
          "position_mm": {
            "value": null,
            "null_reason": "no detection error has been measured on this parcel (procedure step s9 not run)",
            "note": "the position is the crown apex; a published mean apex-to-stem offset for hardwoods is 950 mm (row gatziolis10-apex-stem-offset, another study's site, not this parcel)"
          },
          "height_mm": {
            "value": null,
            "null_reason": "no field heights were measured on this parcel; the CHM peak and the apex return are two readings of the same returns, not a check of accuracy"
          },
          "crown_radius_mm": {
            "value": null,
            "null_reason": "half-height ray walk clipped to 1 to 6 m; no crown error has been measured on this parcel"
          }
        }
      }
    },
    {
      "id": "tree-10",
      "action": "place_instance",
      "parameters": {
        "type": "lidar_tree",
        "x_mm": 6135,
        "y_mm": 18485,
        "height_mm": 12440,
        "crown_radius_mm": 4300,
        "provenance": "measured",
        "source": {
          "file": "plot-twin/capture/receipts/tree-sample-for-van-build-v2.json#/trees/9",
          "tag": "USGS 3DEP QL2 MI_31County_2016_A16, flown 2017-12-01 to 2018-04-23 per the vendor metadata (leaf-off, inferred); extract_features.py with the ground grid row order corrected (branch fix/ground-north-south-flip): 102 trees; every 10th in height order, tallest first; position is the apex return (highest return in the 3x3 cells around the 1 m CHM peak), the crown apex and not the stem; the tree count is not validated against a stem map"
        },
        "error": {
          "position_mm": {
            "value": null,
            "null_reason": "no detection error has been measured on this parcel (procedure step s9 not run)",
            "note": "the position is the crown apex; a published mean apex-to-stem offset for hardwoods is 950 mm (row gatziolis10-apex-stem-offset, another study's site, not this parcel)"
          },
          "height_mm": {
            "value": null,
            "null_reason": "no field heights were measured on this parcel; the CHM peak and the apex return are two readings of the same returns, not a check of accuracy"
          },
          "crown_radius_mm": {
            "value": null,
            "null_reason": "half-height ray walk clipped to 1 to 6 m; no crown error has been measured on this parcel"
          }
        }
      }
    }
  ]
}
"""

const val PARCEL_DEMO_JSON: String = """{
  "world": "parcel-five-acre",
  "steps": [
    {"do": "place", "parameters": {"type": "path", "col": 6, "row": 0}},
    {"do": "place", "parameters": {"type": "path", "col": 6, "row": 1}},
    {"do": "place", "parameters": {"type": "path", "col": 6, "row": 2}},
    {"do": "place", "parameters": {"type": "path", "col": 6, "row": 3}},
    {"do": "place", "parameters": {"type": "path", "col": 6, "row": 4}},
    {"do": "place", "parameters": {"type": "path", "col": 6, "row": 5}},
    {"do": "place", "parameters": {"type": "van_pad", "col": 7, "row": 2}},
    {"do": "place", "parameters": {"type": "van_pad", "col": 5, "row": 3}},
    {"do": "place", "parameters": {"type": "van_pad", "col": 10, "row": 10}},
    {"do": "place", "parameters": {"type": "commons_building", "col": 7, "row": 4}},
    {"do": "place", "parameters": {"type": "commons_building", "col": 0, "row": 10}},
    {"do": "place", "parameters": {"type": "hoop_house", "col": 2, "row": 8}},
    {"do": "place", "parameters": {"type": "woodland_tree", "col": 2, "row": 9}},
    {"do": "place", "parameters": {"type": "paddock", "col": 9, "row": 12}},
    {"do": "place", "parameters": {"type": "paddock", "col": 10, "row": 12}},
    {"do": "place", "parameters": {"type": "paddock", "col": 11, "row": 12}},
    {"do": "place", "parameters": {"type": "paddock", "col": 9, "row": 13}},
    {"do": "place", "parameters": {"type": "paddock", "col": 10, "row": 13}},
    {"do": "place", "parameters": {"type": "paddock", "col": 11, "row": 13}},
    {"do": "place", "parameters": {"type": "paddock", "col": 9, "row": 14}},
    {"do": "place", "parameters": {"type": "paddock", "col": 10, "row": 14}},
    {"do": "place", "parameters": {"type": "paddock", "col": 11, "row": 14}},
    {"do": "place", "parameters": {"type": "paddock", "col": 9, "row": 15}},
    {"do": "place", "parameters": {"type": "paddock", "col": 10, "row": 15}},
    {"do": "place", "parameters": {"type": "paddock", "col": 11, "row": 15}},
    {"do": "place", "parameters": {"type": "pond", "col": 3, "row": 20}},
    {"do": "place", "parameters": {"type": "pond", "col": 4, "row": 20}},
    {"do": "place", "parameters": {"type": "woodland_tree", "col": 10, "row": 22}},
    {"do": "place", "parameters": {"type": "woodland_tree", "col": 11, "row": 22}},
    {"do": "place", "parameters": {"type": "woodland_tree", "col": 10, "row": 23}},
    {"do": "remove", "parameters": {"col": 5, "row": 3}},
    {"do": "branch", "name": "proposal-more-trees", "from": "main", "proposal": true},
    {"do": "place", "on": "proposal-more-trees", "parameters": {"type": "woodland_tree", "col": 10, "row": 24}},
    {"do": "merge", "branch": "proposal-more-trees", "into": "main"},
    {"do": "endorse", "on": "proposal-more-trees", "actor": "neighbor-1", "parameters": {"weight_class": "nearby"}},
    {"do": "endorse", "on": "proposal-more-trees", "actor": "jim", "parameters": {"weight_class": "on_site"}},
    {"do": "merge", "branch": "proposal-more-trees", "into": "main"},
    {"do": "tick", "actor": "clock", "parameters": {"n": 365}}
  ]
}
"""

const val DUNGEON_DEMO_JSON: String = """{
  "world": "dungeon-tiny",
  "steps": [
    {"do": "place", "parameters": {"type": "wall", "col": 0, "row": 0}},
    {"do": "place", "parameters": {"type": "wall", "col": 1, "row": 0}},
    {"do": "place", "parameters": {"type": "wall", "col": 2, "row": 0}},
    {"do": "place", "parameters": {"type": "wall", "col": 3, "row": 0}},
    {"do": "place", "parameters": {"type": "wall", "col": 4, "row": 0}},
    {"do": "place", "parameters": {"type": "wall", "col": 5, "row": 0}},
    {"do": "place", "parameters": {"type": "wall", "col": 6, "row": 0}},
    {"do": "place", "parameters": {"type": "wall", "col": 7, "row": 0}},
    {"do": "place", "parameters": {"type": "wall", "col": 8, "row": 0}},
    {"do": "place", "parameters": {"type": "wall", "col": 9, "row": 0}},
    {"do": "place", "parameters": {"type": "floor", "col": 1, "row": 1}},
    {"do": "place", "parameters": {"type": "floor", "col": 2, "row": 1}},
    {"do": "place", "parameters": {"type": "floor", "col": 3, "row": 1}},
    {"do": "place", "parameters": {"type": "floor", "col": 4, "row": 1}},
    {"do": "place", "parameters": {"type": "floor", "col": 6, "row": 1}},
    {"do": "place", "parameters": {"type": "floor", "col": 7, "row": 1}},
    {"do": "place", "parameters": {"type": "floor", "col": 8, "row": 1}},
    {"do": "place", "parameters": {"type": "door", "col": 5, "row": 1}},
    {"do": "place", "parameters": {"type": "door", "col": 5, "row": 4}},
    {"do": "place", "parameters": {"type": "monster", "col": 2, "row": 2}},
    {"do": "place", "parameters": {"type": "monster", "col": 10, "row": 6}},
    {"do": "place", "parameters": {"type": "treasure", "col": 3, "row": 2}},
    {"do": "place", "parameters": {"type": "treasure", "col": 8, "row": 2}},
    {"do": "tick", "actor": "clock", "parameters": {"n": 10}}
  ]
}
"""

const val MUTATIONS_JSON: String = """{
  "purpose": "one broken copy of the worlds directory per rule in rules.json; test_validate.py applies each to a copy and requires the named rule to fire and the named world to come out invalid",
  "path": "keys into the world document; a string is a key, an integer an index, and an object picks the list member whose fields equal it",
  "mutations": [
    {
      "rule": "blind-worlds",
      "world": null,
      "edits": [{"op": "delete_all_worlds"}]
    },
    {
      "rule": "world-parses",
      "world": "dungeon-tiny.world.json",
      "edits": [{"op": "write_text", "value": "{ \"id\": \"dungeon-tiny\", "}]
    },
    {
      "rule": "world-schema",
      "world": "dungeon-tiny.world.json",
      "edits": [{"op": "set", "path": ["grid", "cols"], "value": "twelve"}]
    },
    {
      "rule": "expression-parses",
      "world": "parcel-five-acre.world.json",
      "edits": [{"op": "set", "path": ["rules", {"id": "van-pad-needs-path"}, "require"], "value": "neighbors(tile, 1 [tile], 'path') >= ; while"}]
    },
    {
      "rule": "unknown-word",
      "world": "parcel-five-acre.world.json",
      "edits": [{"op": "set", "path": ["rules", {"id": "van-pad-needs-path"}, "require"], "value": "neighbors(tile, 1 [tile], 'driveway') >= 1 [tile]"}]
    },
    {
      "rule": "unit-mismatch",
      "world": "parcel-five-acre.world.json",
      "edits": [{"op": "set", "path": ["equations", {"id": "pasture_yield"}, "expr"], "value": "count('paddock') * paddock.annual_forage_yield"}]
    },
    {
      "rule": "expression-bound",
      "world": "dungeon-tiny.world.json",
      "edits": [{"op": "set_repeated", "path": ["scoring", {"id": "guarded_treasure"}, "expr"], "unit": "sum('guarded') + ", "times": 150, "tail": "sum('guarded')"}]
    },
    {
      "rule": "name-cycle",
      "world": "parcel-five-acre.world.json",
      "edits": [{"op": "set", "path": ["equations", {"id": "pasture_yield"}, "expr"], "value": "pasture_yield + count('paddock') * tile_area * paddock.annual_forage_yield"}]
    },
    {
      "rule": "seed-replays",
      "world": "locality-stub.world.json",
      "edits": [{"op": "set", "path": ["seed", {"id": "parcel-b"}, "parameters", "col"], "value": 8}]
    },
    {
      "rule": "rule-message",
      "world": "dungeon-tiny.world.json",
      "edits": [{"op": "set", "path": ["rules", {"id": "door-connects-floors"}, "message"], "value": ""}]
    },
    {
      "rule": "unknown-target",
      "world": "parcel-five-acre.world.json",
      "edits": [{"op": "set", "path": ["rules", {"id": "van-pad-needs-path"}, "applies_to"], "value": ["vanpad"]}]
    },
    {
      "rule": "action-emits",
      "world": "dungeon-tiny.world.json",
      "edits": [{"op": "set", "path": ["actions", {"verb": "place"}, "emits"], "value": []}]
    },
    {
      "rule": "link-resolves",
      "world": "parcel-five-acre.world.json",
      "edits": [{"op": "set", "path": ["links", 0, "parcel"], "value": "parcel-z"}]
    },
    {
      "rule": "link-fits",
      "world": "parcel-five-acre.world.json",
      "edits": [{"op": "set", "path": ["grid", "rows"], "value": 60}]
    },
    {
      "rule": "projection-not-binding",
      "world": "parcel-five-acre.world.json",
      "edits": [{"op": "set", "path": ["scoring", {"id": "neighbor_support"}, "binding"], "value": true}]
    },
    {
      "rule": "projection-not-binding",
      "world": "parcel-five-acre.world.json",
      "edits": [
        {"op": "set", "path": ["equations"], "value": [
          {"id": "pasture_yield", "expr": "count('paddock') * tile_area * paddock.annual_forage_yield", "unit": "ton/year", "source": {"twin": "eq-aux-stocking-static"}},
          {"id": "support_now", "expr": "projected_support('neighbor')", "unit": "1"}
        ]},
        {"op": "set", "path": ["rules", {"id": "van-pad-needs-path"}, "require"], "value": "support_now >= 0.5"}
      ]
    },
    {
      "rule": "figure-sourced",
      "world": "parcel-five-acre.world.json",
      "edits": [{"op": "delete", "path": ["object_types", {"id": "hoop_house"}, "properties", {"name": "labor_hours_per_tile"}, "source"]}]
    },
    {
      "rule": "sprite-known",
      "world": "parcel-five-acre.world.json",
      "edits": [{"op": "set", "path": ["object_types", {"id": "pond"}, "sprite"], "value": "lake"}]
    },
    {
      "rule": "footprint-mm-agrees",
      "world": "parcel-lidar-sample.world.json",
      "edits": [{"op": "set", "path": ["object_types", {"id": "shed"}, "footprint_mm"], "value": [8000, 3000]}]
    },
    {
      "rule": "instance-id-unique",
      "world": "parcel-lidar-sample.world.json",
      "edits": [{"op": "set", "path": ["seed", {"id": "tree-02"}, "id"], "value": "tree-01"}]
    },
    {
      "rule": "instance-source",
      "world": "parcel-lidar-sample.world.json",
      "edits": [{"op": "delete", "path": ["seed", {"id": "tree-01"}, "parameters", "source"]}]
    },
    {
      "rule": "instance-error",
      "world": "parcel-lidar-sample.world.json",
      "edits": [{"op": "delete", "path": ["seed", {"id": "tree-01"}, "parameters", "error", "crown_radius_mm"]}]
    },
    {
      "rule": "instance-error-reason",
      "world": "parcel-lidar-sample.world.json",
      "edits": [{"op": "set", "path": ["seed", {"id": "tree-01"}, "parameters", "error", "height_mm"], "value": {"value": null}}]
    },
    {
      "rule": "instance-error-reason",
      "world": "parcel-lidar-sample.world.json",
      "edits": [{"op": "set", "path": ["seed", {"id": "tree-01"}, "parameters", "error", "position_mm"], "value": {"value": 950}}]
    },
    {
      "rule": "world-schema",
      "world": "parcel-lidar-sample.world.json",
      "edits": [{"op": "set", "path": ["frame", "y_axis"], "value": "south"}]
    },
    {
      "rule": "seed-replays",
      "world": "parcel-lidar-sample.world.json",
      "edits": [{"op": "set", "path": ["seed", {"id": "tree-01"}, "parameters", "y_mm"], "value": -1}]
    }
  ]
}
"""
