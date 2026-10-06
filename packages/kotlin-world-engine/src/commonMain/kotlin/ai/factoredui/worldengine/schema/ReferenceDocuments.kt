package ai.factoredui.worldengine.schema

internal const val WORLD_SCHEMA_JSON: String = """{
  "${'$'}schema": "https://json-schema.org/draft/2020-12/schema",
  "${'$'}id": "world.schema.json",
  "title": "world file",
  "description": "One world: what can exist on its grid, the rules an action must pass, the equations and scores read from its state, and the actions a person can take. Every expression is a string in the expression language engine_ref.py parses; nothing here executes code.",
  "type": "object",
  "required": ["id", "version", "title", "sprites", "grid", "clock", "object_types", "rules", "equations", "stocks", "actions", "agents", "scoring", "links", "seed"],
  "additionalProperties": false,
  "properties": {
    "id": {"${'$'}ref": "#/${'$'}defs/identifier"},
    "version": {"type": "integer", "minimum": 1},
    "title": {"type": "string", "minLength": 1},
    "description": {"type": "string"},
    "sprites": {
      "description": "The generic art set is closed: flat, block, tree, arch, water, fence. A world may declare extensions, each drawn from one generic base so any renderer can fall back to the base.",
      "type": "object",
      "required": ["extensions"],
      "additionalProperties": false,
      "properties": {
        "extensions": {
          "type": "array",
          "items": {
            "type": "object",
            "required": ["id", "base"],
            "additionalProperties": false,
            "properties": {
              "id": {"${'$'}ref": "#/${'$'}defs/identifier"},
              "base": {"enum": ["flat", "block", "tree", "arch", "water", "fence"]},
              "note": {"type": "string"}
            }
          }
        }
      }
    },
    "grid": {
      "type": "object",
      "required": ["cols", "rows", "tile_ft", "shape"],
      "additionalProperties": false,
      "properties": {
        "cols": {"type": "integer", "minimum": 1, "maximum": 512},
        "rows": {"type": "integer", "minimum": 1, "maximum": 512},
        "tile_ft": {"type": "number", "minimum": 0.1},
        "shape": {"enum": ["square"], "description": "hex adjacency is not specified yet; see README open questions"},
        "view": {"enum": ["iso", "top"]},
        "north": {"enum": ["row_0"], "description": "row 0 is the north edge; south is increasing row"}
      }
    },
    "frame": {
      "description": "Optional: places the grid on the earth. The origin is the grid's south-west corner in the named EPSG coordinate reference, in metres; x runs east and y north, and an instance at x_mm, y_mm stands at origin + (x_mm, y_mm) / 1000. Without a frame, instance positions are local millimetres from that same corner.",
      "type": "object",
      "required": ["epsg", "origin_east_m", "origin_north_m", "x_axis", "y_axis"],
      "additionalProperties": false,
      "properties": {
        "epsg": {"type": "integer", "minimum": 1},
        "origin_east_m": {"type": "number"},
        "origin_north_m": {"type": "number"},
        "x_axis": {"const": "east"},
        "y_axis": {"const": "north"}
      }
    },
    "ground": {
      "description": "Optional: the ground surface as (cols + 1) x (rows + 1) vertex heights in mm, row by row from the north edge (vertex row 0), west to east. Tile col,row has corners NW = vertex col,row, NE = col+1,row, SW = col,row+1, SE = col+1,row+1. validate.py refuses a count other than (cols + 1) x (rows + 1) (ground-size) and a height outside -500000 to 5000000 mm (ground-range). dig and raise change a tile's four corners; ground never changes tile counts or areas. A world without ground behaves as if the key were absent everywhere.",
      "type": "object",
      "required": ["unit", "datum", "source", "heights_mm", "error"],
      "additionalProperties": false,
      "properties": {
        "unit": {"const": "mm"},
        "datum": {"type": "string", "minLength": 1, "description": "the vertical datum the heights are measured from, for example NAVD88, or 'local'"},
        "source": {"type": "string", "minLength": 1, "description": "where the surface came from: a file and the converter that read it, or a stated fixture"},
        "heights_mm": {"type": "array", "items": {"type": "number"}},
        "error": {
          "type": "object",
          "required": ["vertical_mm"],
          "additionalProperties": false,
          "properties": {"vertical_mm": {"${'$'}ref": "#/${'$'}defs/error_figure"}}
        },
        "note": {"type": "string"}
      }
    },
    "clock": {
      "type": "object",
      "required": ["tick_unit", "tick_length"],
      "additionalProperties": false,
      "properties": {
        "tick_unit": {"${'$'}ref": "#/${'$'}defs/unit"},
        "tick_length": {"type": "number", "minimum": 0}
      }
    },
    "object_types": {"type": "array", "minItems": 1, "items": {"${'$'}ref": "#/${'$'}defs/object_type"}},
    "rules": {"type": "array", "items": {"${'$'}ref": "#/${'$'}defs/rule"}},
    "equations": {"type": "array", "items": {"${'$'}ref": "#/${'$'}defs/equation"}},
    "stocks": {"type": "array", "items": {"${'$'}ref": "#/${'$'}defs/stock"}},
    "actions": {"type": "array", "minItems": 1, "items": {"${'$'}ref": "#/${'$'}defs/action"}},
    "agents": {"type": "array", "items": {"${'$'}ref": "#/${'$'}defs/agent"}},
    "scoring": {"type": "array", "items": {"${'$'}ref": "#/${'$'}defs/score"}},
    "links": {"type": "array", "maxItems": 1, "items": {"${'$'}ref": "#/${'$'}defs/link"}},
    "seed": {"type": "array", "items": {"${'$'}ref": "#/${'$'}defs/seed_entry"}}
  },
  "${'$'}defs": {
    "identifier": {"type": "string", "pattern": "^[a-z][a-z0-9_-]*${'$'}"},
    "expression": {"type": "string", "minLength": 1, "maxLength": 4000},
    "unit": {"type": "string", "pattern": "^([a-z_]+(\\^[0-9]+)?(\\s*[*/]\\s*[a-z_]+(\\^[0-9]+)?)*|1)${'$'}"},
    "source": {
      "description": "Where a figure comes from: a twin id (equation, binding or task_hours row of the task model), a research row id, or a declared placeholder with its reason.",
      "type": "object",
      "additionalProperties": false,
      "properties": {
        "twin": {"type": "string", "minLength": 1},
        "binding": {"type": "string", "minLength": 1},
        "row": {"type": "string", "minLength": 1},
        "placeholder": {"const": true},
        "reason": {"type": "string", "minLength": 1}
      },
      "anyOf": [
        {"required": ["twin"]},
        {"required": ["row"]},
        {"required": ["placeholder", "reason"]}
      ]
    },
    "property": {
      "type": "object",
      "required": ["name", "kind", "default"],
      "additionalProperties": false,
      "properties": {
        "name": {"${'$'}ref": "#/${'$'}defs/identifier"},
        "type": {"enum": ["number", "text"]},
        "unit": {"${'$'}ref": "#/${'$'}defs/unit"},
        "default": {"type": ["number", "string"]},
        "enum": {"type": "array", "items": {"type": "string"}},
        "kind": {"enum": ["physical", "design", "state", "price", "labor", "yield", "regulation", "demographic"]},
        "source": {"${'$'}ref": "#/${'$'}defs/source"},
        "note": {"type": "string"}
      }
    },
    "object_type": {
      "description": "A type is sized in tiles (footprint), in millimetres (footprint_mm, x then y), or both. With only footprint_mm, each axis covers ceil(footprint_mm / (tile_ft x 304.8)) tiles, computed exactly on the decimals as written; with both, they must agree on that or validate.py refuses the world (footprint-mm-agrees).",
      "type": "object",
      "required": ["id", "label", "sprite", "color"],
      "additionalProperties": false,
      "properties": {
        "id": {"${'$'}ref": "#/${'$'}defs/identifier"},
        "label": {"type": "string", "minLength": 1},
        "sprite": {"${'$'}ref": "#/${'$'}defs/identifier"},
        "color": {"type": "string", "pattern": "^#[0-9A-Fa-f]{6}${'$'}"},
        "footprint": {"type": "array", "items": {"type": "integer", "minimum": 1, "maximum": 64}, "minItems": 2, "maxItems": 2},
        "footprint_mm": {"type": "array", "items": {"type": "number", "exclusiveMinimum": 0}, "minItems": 2, "maxItems": 2},
        "height_mm": {"type": "number", "exclusiveMinimum": 0},
        "tags": {"type": "array", "items": {"${'$'}ref": "#/${'$'}defs/identifier"}},
        "properties": {"type": "array", "items": {"${'$'}ref": "#/${'$'}defs/property"}}
      },
      "anyOf": [{"required": ["footprint"]}, {"required": ["footprint_mm"]}]
    },
    "rule": {
      "description": "Checked against the state an action would produce. on place or remove: `tile` is the acted-on object. on always: checked after every place and remove for every object the rule targets, with `tile` bound to each in turn, so an invariant holds through removals, merges and reverts too. A `require` that evaluates false refuses the action with `message`. An `effect` sets one property of the acted-on object once, after every require holds; effects never trigger rules.",
      "type": "object",
      "required": ["id", "on"],
      "additionalProperties": false,
      "properties": {
        "id": {"${'$'}ref": "#/${'$'}defs/identifier"},
        "on": {"enum": ["place", "remove", "always", "place_instance", "remove_instance", "dig", "raise"], "description": "a place_instance or remove_instance rule binds `instance` (the acted-on instance) instead of `tile`, and sets no effect; a dig or raise rule binds `tile` to the acted tile, typed by the object on it if any, so applies_to targets that object and a rule with no target applies to every tile"},
        "scope": {"enum": ["self", "child"], "description": "child: exported for worlds that link to this one and inherit it by id"},
        "applies_to": {"type": "array", "items": {"${'$'}ref": "#/${'$'}defs/identifier"}},
        "applies_to_tag": {"${'$'}ref": "#/${'$'}defs/identifier"},
        "require": {"${'$'}ref": "#/${'$'}defs/expression"},
        "message": {"type": "string"},
        "effect": {
          "type": "object",
          "required": ["set", "to"],
          "additionalProperties": false,
          "properties": {
            "set": {"${'$'}ref": "#/${'$'}defs/identifier"},
            "to": {"${'$'}ref": "#/${'$'}defs/expression"}
          }
        },
        "source": {"${'$'}ref": "#/${'$'}defs/source"},
        "note": {"type": "string"}
      },
      "anyOf": [{"required": ["require"]}, {"required": ["effect"]}]
    },
    "equation": {
      "type": "object",
      "required": ["id", "expr", "unit"],
      "additionalProperties": false,
      "properties": {
        "id": {"${'$'}ref": "#/${'$'}defs/identifier"},
        "expr": {"${'$'}ref": "#/${'$'}defs/expression"},
        "unit": {"${'$'}ref": "#/${'$'}defs/unit"},
        "source": {"${'$'}ref": "#/${'$'}defs/source"},
        "note": {"type": "string"}
      }
    },
    "stock": {
      "description": "State the tick clock advances: every tick evaluates every stock's `next` against the previous values, all at once.",
      "type": "object",
      "required": ["id", "unit", "initial", "next"],
      "additionalProperties": false,
      "properties": {
        "id": {"${'$'}ref": "#/${'$'}defs/identifier"},
        "unit": {"${'$'}ref": "#/${'$'}defs/unit"},
        "initial": {"type": "number"},
        "next": {"${'$'}ref": "#/${'$'}defs/expression"},
        "source": {"${'$'}ref": "#/${'$'}defs/source"},
        "note": {"type": "string"}
      }
    },
    "action": {
      "type": "object",
      "required": ["verb", "parameters", "emits"],
      "additionalProperties": false,
      "properties": {
        "verb": {"enum": ["place", "remove", "tick", "enroll", "opt_in", "place_instance", "remove_instance", "dig", "raise"]},
        "label": {"type": "string"},
        "parameters": {
          "type": "array",
          "items": {
            "type": "object",
            "required": ["name", "type"],
            "additionalProperties": false,
            "properties": {
              "name": {"${'$'}ref": "#/${'$'}defs/identifier"},
              "type": {"enum": ["object_type", "col", "row", "count", "agent_type", "agent_id", "attributes", "properties", "boolean",
                                "x_mm", "y_mm", "rotation_deg", "instance_id", "depth_mm", "height_mm"]}
            }
          }
        },
        "emits": {"type": "array", "items": {"enum": ["place", "remove", "tick", "enroll", "opt_in", "place_instance", "remove_instance", "dig", "raise"]}}
      }
    },
    "agent": {
      "description": "A person-type object. Synthetic agents are drawn from demographics and replaced as real people opt in. `utility` decides an agent's projected vote and `weight` its distance weight; projected_support() reads them, and nothing binding may.",
      "type": "object",
      "required": ["type", "label", "attributes", "weight", "utility", "vote", "source"],
      "additionalProperties": false,
      "properties": {
        "type": {"${'$'}ref": "#/${'$'}defs/identifier"},
        "label": {"type": "string"},
        "attributes": {
          "type": "array",
          "items": {
            "type": "object",
            "required": ["name", "unit"],
            "additionalProperties": false,
            "properties": {
              "name": {"${'$'}ref": "#/${'$'}defs/identifier"},
              "unit": {"${'$'}ref": "#/${'$'}defs/unit"},
              "default": {"type": "number"},
              "note": {"type": "string"}
            }
          }
        },
        "weight": {"${'$'}ref": "#/${'$'}defs/expression"},
        "utility": {"${'$'}ref": "#/${'$'}defs/expression"},
        "vote": {"const": "projection"},
        "source": {"${'$'}ref": "#/${'$'}defs/source"}
      }
    },
    "score": {
      "type": "object",
      "required": ["id", "label", "expr", "unit"],
      "additionalProperties": false,
      "properties": {
        "id": {"${'$'}ref": "#/${'$'}defs/identifier"},
        "label": {"type": "string"},
        "expr": {"${'$'}ref": "#/${'$'}defs/expression"},
        "unit": {"${'$'}ref": "#/${'$'}defs/unit"},
        "binding": {"type": "boolean", "description": "true when the score decides an outcome; a binding score may not read projected_support()"},
        "source": {"${'$'}ref": "#/${'$'}defs/source"},
        "note": {"type": "string"}
      }
    },
    "link": {
      "type": "object",
      "required": ["parent", "parcel", "as", "inherit"],
      "additionalProperties": false,
      "properties": {
        "parent": {"type": "string", "pattern": "\\.world\\.json${'$'}"},
        "parcel": {"${'$'}ref": "#/${'$'}defs/identifier"},
        "as": {"${'$'}ref": "#/${'$'}defs/identifier"},
        "inherit": {"type": "array", "items": {"${'$'}ref": "#/${'$'}defs/identifier"}}
      }
    },
    "seed_entry": {
      "description": "Events the world ships with, replayed before any log; a seed place's id becomes the placed object's id. A seed place_instance is a measured instance: the one place measured instances live.",
      "anyOf": [{"${'$'}ref": "#/${'$'}defs/seed_event"}, {"${'$'}ref": "#/${'$'}defs/seed_instance"}]
    },
    "seed_event": {
      "type": "object",
      "required": ["id", "action", "parameters"],
      "additionalProperties": false,
      "properties": {
        "id": {"${'$'}ref": "#/${'$'}defs/identifier"},
        "actor": {"type": "string"},
        "action": {"enum": ["place", "remove", "tick", "enroll", "opt_in"]},
        "parameters": {"type": "object"},
        "note": {"type": "string"}
      }
    },
    "seed_instance": {
      "description": "A measured instance: a fact the seed records, so no rule is checked against it. Its id is the instance id, and never has the shape of an event id (e<n>), which a proposed instance takes. Proposed instances come only from place_instance events.",
      "type": "object",
      "required": ["id", "action", "parameters"],
      "additionalProperties": false,
      "properties": {
        "id": {"type": "string", "pattern": "^(?!e[0-9]+${'$'})[a-z][a-z0-9_-]*${'$'}"},
        "actor": {"type": "string"},
        "action": {"const": "place_instance"},
        "parameters": {"${'$'}ref": "#/${'$'}defs/measured_instance"},
        "note": {"type": "string"}
      }
    },
    "measured_instance": {
      "description": "Positions in millimetres from the grid's south-west corner, x east and y north, at no tile's snap. height_mm defaults to the type's. source and error are required by validate.py (instance-source, instance-error), not here, so a missing one is named.",
      "type": "object",
      "required": ["type", "x_mm", "y_mm", "provenance"],
      "additionalProperties": false,
      "properties": {
        "type": {"${'$'}ref": "#/${'$'}defs/identifier"},
        "x_mm": {"type": "number"},
        "y_mm": {"type": "number"},
        "z_mm": {"type": "number"},
        "rotation_deg": {"type": "number"},
        "height_mm": {"type": "number", "exclusiveMinimum": 0},
        "crown_radius_mm": {"type": "number", "exclusiveMinimum": 0},
        "provenance": {"const": "measured"},
        "source": {"${'$'}ref": "#/${'$'}defs/instance_source"},
        "error": {"${'$'}ref": "#/${'$'}defs/instance_error"},
        "note": {"type": "string"}
      }
    },
    "instance_source": {
      "description": "Where a measured instance was measured: a twin id, a research row id, a file reference, or a stated source tag.",
      "type": "object",
      "additionalProperties": false,
      "properties": {
        "twin": {"type": "string", "minLength": 1},
        "row": {"type": "string", "minLength": 1},
        "file": {"type": "string", "minLength": 1},
        "tag": {"type": "string", "minLength": 1}
      },
      "anyOf": [{"required": ["twin"]}, {"required": ["row"]}, {"required": ["file"]}, {"required": ["tag"]}]
    },
    "instance_error": {
      "type": "object",
      "additionalProperties": false,
      "properties": {
        "position_mm": {"${'$'}ref": "#/${'$'}defs/error_figure"},
        "height_mm": {"${'$'}ref": "#/${'$'}defs/error_figure"},
        "crown_radius_mm": {"${'$'}ref": "#/${'$'}defs/error_figure"}
      }
    },
    "error_figure": {
      "description": "A number with its source, or null with null_reason; validate.py refuses either half missing (instance-error-reason).",
      "type": "object",
      "additionalProperties": false,
      "properties": {
        "value": {"type": ["number", "null"]},
        "source": {"${'$'}ref": "#/${'$'}defs/source"},
        "null_reason": {"type": "string", "minLength": 1},
        "note": {"type": "string"}
      }
    }
  }
}
"""

internal const val EVENTS_SCHEMA_JSON: String = """{
  "${'$'}schema": "https://json-schema.org/draft/2020-12/schema",
  "${'$'}id": "events.schema.json",
  "title": "event log",
  "description": "An append-only log of events forming a tree by `parent`. A branch's state is the fold of the chain from the world's seed to the branch head; nothing else is state. `touches` and `removed` are written by the engine when it commits an event, never by the caller.",
  "type": "object",
  "required": ["world", "events"],
  "additionalProperties": false,
  "properties": {
    "world": {"type": "string"},
    "events": {"type": "array", "items": {"${'$'}ref": "#/${'$'}defs/event"}}
  },
  "${'$'}defs": {
    "event": {
      "type": "object",
      "required": ["id", "parent", "world", "branch", "actor", "action", "parameters", "timestamp", "touches"],
      "additionalProperties": false,
      "properties": {
        "id": {"type": "string", "pattern": "^e[0-9]+${'$'}"},
        "parent": {"type": ["string", "null"], "description": "the event this one follows on its branch; null only at the root"},
        "world": {"type": "string"},
        "branch": {"type": "string", "minLength": 1},
        "actor": {"type": "string", "minLength": 1, "description": "a person id, an agent id, or 'clock'"},
        "action": {"enum": ["place", "remove", "tick", "enroll", "opt_in", "branch", "merge", "revert", "endorse", "place_instance", "remove_instance", "dig", "raise"]},
        "parameters": {"type": "object"},
        "timestamp": {"type": "string", "pattern": "^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(\\.[0-9]+)?Z${'$'}"},
        "touches": {
          "type": "array",
          "items": {"type": "string", "pattern": "^([0-9]+,[0-9]+|clock|agent:.+|instance:.+|ground:[0-9]+,[0-9]+)${'$'}"},
          "description": "tiles as 'col,row', 'clock' for a tick, 'agent:<id>' for an agent, 'instance:<id>' for an instance, 'ground:<vertex col>,<vertex row>' for each of the four corner vertices a dig or raise changes; the merge and revert conflict rules compare these"
        },
        "removed_instance": {
          "type": "object",
          "description": "on a remove_instance: the whole record that stood there, provenance, source and error included",
          "required": ["id", "type", "x_mm", "y_mm", "z_mm", "rotation_deg", "height_mm", "crown_radius_mm", "provenance", "source", "error"]
        },
        "removed": {
          "type": "object",
          "description": "on a remove: what stood there, so a revert can put it back",
          "required": ["type", "col", "row", "properties"],
          "additionalProperties": false,
          "properties": {
            "type": {"type": "string"},
            "col": {"type": "integer"},
            "row": {"type": "integer"},
            "properties": {"type": "object"}
          }
        }
      }
    },
    "place_parameters": {
      "type": "object",
      "required": ["type", "col", "row"],
      "properties": {"type": {"type": "string"}, "col": {"type": "integer"}, "row": {"type": "integer"}, "properties": {"type": "object"}}
    },
    "remove_parameters": {
      "type": "object",
      "required": ["col", "row"],
      "properties": {"col": {"type": "integer"}, "row": {"type": "integer"}}
    },
    "place_instance_parameters": {
      "description": "a proposed instance at a free millimetre position from the grid's south-west corner, x east and y north; its id is the event id and its height the type's height_mm",
      "type": "object",
      "required": ["type", "x_mm", "y_mm"],
      "properties": {"type": {"type": "string"}, "x_mm": {"type": "number"}, "y_mm": {"type": "number"}, "rotation_deg": {"type": "number"}}
    },
    "dig_parameters": {
      "description": "lowers tile col,row's four corner vertices by depth_mm each, once; depth_mm is a number above 0 and at most 50000",
      "type": "object",
      "required": ["col", "row", "depth_mm"],
      "properties": {"col": {"type": "integer"}, "row": {"type": "integer"}, "depth_mm": {"type": "number", "exclusiveMinimum": 0, "maximum": 50000}}
    },
    "raise_parameters": {
      "description": "lifts tile col,row's four corner vertices by height_mm each, once; height_mm is a number above 0 and at most 50000",
      "type": "object",
      "required": ["col", "row", "height_mm"],
      "properties": {"col": {"type": "integer"}, "row": {"type": "integer"}, "height_mm": {"type": "number", "exclusiveMinimum": 0, "maximum": 50000}}
    },
    "remove_instance_parameters": {
      "type": "object",
      "required": ["id"],
      "properties": {"id": {"type": "string"}}
    },
    "tick_parameters": {
      "type": "object",
      "required": ["n"],
      "properties": {"n": {"type": "integer", "minimum": 1, "maximum": 100000}}
    },
    "enroll_parameters": {
      "type": "object",
      "required": ["agent_type", "agent_id"],
      "properties": {"agent_type": {"type": "string"}, "agent_id": {"type": "string"}, "synthetic": {"type": "boolean"}, "attributes": {"type": "object"}}
    },
    "opt_in_parameters": {
      "description": "a synthetic agent is replaced by the real person it stood for: synthetic becomes false and stated attributes overwrite drawn ones",
      "type": "object",
      "required": ["agent_id"],
      "properties": {"agent_id": {"type": "string"}, "attributes": {"type": "object"}}
    },
    "branch_parameters": {
      "description": "branch(name, from): a new head at `from`. A proposal is a branch with proposal true.",
      "type": "object",
      "required": ["name", "from", "proposal"],
      "properties": {"name": {"type": "string"}, "from": {"type": ["string", "null"]}, "proposal": {"type": "boolean"}}
    },
    "merge_parameters": {
      "description": "merge(branch): the source events not yet applied on the target, in source order. Refused when any of them touches a tile the target changed since the two diverged, or when replaying them breaks a rule. A proposal merges only after an endorsement.",
      "type": "object",
      "required": ["branch", "events"],
      "properties": {"branch": {"type": "string"}, "events": {"type": "array", "items": {"type": "string"}}}
    },
    "revert_parameters": {
      "description": "revert(event): applies the inverse of a place, remove, dig or raise (a dig's inverse is a raise of the same amount on the same tile, and back), as an action checked against the rules. Refused when a later event touched the same tiles or vertices.",
      "type": "object",
      "required": ["event", "undo"],
      "properties": {"event": {"type": "string"}, "undo": {"type": "object", "required": ["action", "parameters"]}}
    },
    "endorse_parameters": {
      "description": "an endorsement of the proposal branch it is appended to. weight_class is a vote class from the matchmaking vocabulary; class weights are null there, so the engine tallies by class and never sums. A synthetic agent cannot endorse.",
      "type": "object",
      "required": ["weight_class"],
      "properties": {"weight_class": {"enum": ["on_site", "nearby", "supporting"]}}
    }
  }
}
"""

internal const val VALIDATION_RULES_JSON: String = """{
  "purpose": "every refusal validate.py makes of a world file, as data: the set it scans, the condition each record must meet, and the message; validate.py derives the records and one generic runner judges them, and mutations.json holds one broken copy per rule that must trip it",
  "ops": "empty, nonempty, is_true, is_false, eq_value, when (if, then), all",
  "rules": [
    {
      "id": "blind-worlds",
      "kind": "blind",
      "scans": "worlds",
      "min": 1,
      "message": "no *.world.json under the worlds directory, so nothing below was checked"
    },
    {
      "id": "world-parses",
      "scans": "worlds",
      "condition": {"op": "is_true", "field": "parse_ok"},
      "message": "{world}: is not JSON: {parse_error}"
    },
    {
      "id": "number-exponent-too-large",
      "scans": "worlds",
      "condition": {"op": "empty", "field": "oversized_number"},
      "message": "{world}: holds a number the engine will not read, because exact arithmetic on it would not be bounded: {oversized_number}"
    },
    {
      "id": "world-schema",
      "scans": "worlds",
      "condition": {"op": "empty", "field": "schema_errors"},
      "message": "{world}: does not match world.schema.json: {schema_errors}"
    },
    {
      "id": "expression-parses",
      "scans": "expressions",
      "condition": {"op": "empty", "field": "syntax_errors"},
      "message": "{world} {where}: is not in the expression grammar: {syntax_errors}"
    },
    {
      "id": "unknown-word",
      "scans": "expressions",
      "condition": {"op": "empty", "field": "unknown_words"},
      "message": "{world} {where}: uses a word the world does not declare: {unknown_words}"
    },
    {
      "id": "unit-mismatch",
      "scans": "expressions",
      "condition": {"op": "empty", "field": "unit_errors"},
      "message": "{world} {where}: units or types do not agree: {unit_errors}"
    },
    {
      "id": "expression-bound",
      "scans": "expressions",
      "condition": {"op": "is_false", "field": "over_bound"},
      "message": "{world} {where}: exceeds the node or depth bound, so its evaluation cost is not bounded by the world: {bound_errors}"
    },
    {
      "id": "name-cycle",
      "scans": "names",
      "condition": {"op": "empty", "field": "cycle"},
      "message": "{world}: equations, scores or agents refer to each other in a cycle, which would never finish evaluating: {cycle}"
    },
    {
      "id": "seed-replays",
      "scans": "seeds",
      "condition": {"op": "empty", "field": "seed_error"},
      "message": "{world}: the seed the world ships with breaks its own rules: {seed_error}"
    },
    {
      "id": "rule-message",
      "scans": "rules",
      "condition": {
        "op": "when",
        "if": {"op": "eq_value", "field": "kind", "value": "require"},
        "then": {"op": "nonempty", "field": "message"}
      },
      "message": "{world} rule {id}: refuses without saying why; a require rule needs a message"
    },
    {
      "id": "unknown-target",
      "scans": "rules",
      "condition": {"op": "empty", "field": "unknown_targets"},
      "message": "{world} rule {id}: applies to types or tags the world does not declare: {unknown_targets}"
    },
    {
      "id": "action-emits",
      "scans": "actions",
      "condition": {"op": "nonempty", "field": "emits"},
      "message": "{world} action {verb}: emits no events, so taking it would change nothing in the log"
    },
    {
      "id": "link-resolves",
      "scans": "links",
      "condition": {"op": "empty", "field": "problems"},
      "message": "{world} link to {parent}: dangles: {problems}"
    },
    {
      "id": "link-fits",
      "scans": "links",
      "condition": {"op": "is_true", "field": "fits"},
      "message": "{world} link to {parent}: the grid covers {extent_sq_ft} sq ft, more than the {parent_extent_sq_ft} sq ft of the parcel it links to"
    },
    {
      "id": "projection-not-binding",
      "scans": "projections",
      "condition": {"op": "is_false", "field": "binding"},
      "message": "{world} {where}: a binding outcome reads projected_support(); an agent's assumed vote is a projection and never binding"
    },
    {
      "id": "figure-sourced",
      "scans": "figures",
      "condition": {"op": "is_true", "field": "has_source"},
      "message": "{world} {type}.{property}: a {kind} figure needs a twin id, a row id, or a placeholder with its reason"
    },
    {
      "id": "sprite-known",
      "scans": "object_types",
      "condition": {"op": "is_true", "field": "sprite_known"},
      "message": "{world} type {type}: sprite {sprite} is neither generic nor a declared extension"
    },
    {
      "id": "footprint-too-large",
      "scans": "object_types",
      "condition": {"op": "is_false", "field": "footprint_too_large"},
      "message": "{world} type {type}: its footprint covers more than 2000000 tiles, so placing it could not be checked in bounded time"
    },
    {
      "id": "footprint-mm-agrees",
      "scans": "footprints",
      "condition": {"op": "is_true", "field": "agrees"},
      "message": "{world} type {type}: footprint {footprint} tiles, but footprint_mm spans {derived} tiles of this grid (ceil of footprint_mm / (tile_ft x 304.8))"
    },
    {
      "id": "instance-id-unique",
      "scans": "instances",
      "condition": {"op": "is_true", "field": "is_unique"},
      "message": "{world} instance {id}: two seed instances share this id, so one would overwrite the other"
    },
    {
      "id": "instance-source",
      "scans": "instances",
      "condition": {"op": "is_true", "field": "has_source"},
      "message": "{world} instance {id}: a measured instance needs a source: a twin id, a row id, a file or a stated tag"
    },
    {
      "id": "instance-error",
      "scans": "instances",
      "condition": {"op": "empty", "field": "missing_error_fields"},
      "message": "{world} instance {id}: a measured instance needs an error object; missing {missing_error_fields}"
    },
    {
      "id": "instance-error-reason",
      "scans": "instances",
      "condition": {"op": "empty", "field": "unreasoned"},
      "message": "{world} instance {id}: error {unreasoned} must be a number with a source or null with a null_reason"
    },
    {
      "id": "ground-size",
      "scans": "grounds",
      "condition": {"op": "is_true", "field": "size_agrees"},
      "message": "{world} ground: {length} heights, but a {cols} x {rows} grid has (cols + 1) x (rows + 1) = {expected_length} vertices"
    },
    {
      "id": "ground-range",
      "scans": "grounds",
      "condition": {"op": "empty", "field": "out_of_range"},
      "message": "{world} ground: heights at vertex indices {out_of_range} lie outside -500000 to 5000000 mm"
    },
    {
      "id": "ground-error-reason",
      "scans": "grounds",
      "condition": {"op": "is_true", "field": "error_reasoned"},
      "message": "{world} ground: error.vertical_mm must be a number with a source or null with a null_reason"
    }
  ]
}
"""
