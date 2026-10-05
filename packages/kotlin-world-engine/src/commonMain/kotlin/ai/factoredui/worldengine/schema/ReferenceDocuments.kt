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
      "type": "object",
      "required": ["id", "label", "sprite", "color", "footprint"],
      "additionalProperties": false,
      "properties": {
        "id": {"${'$'}ref": "#/${'$'}defs/identifier"},
        "label": {"type": "string", "minLength": 1},
        "sprite": {"${'$'}ref": "#/${'$'}defs/identifier"},
        "color": {"type": "string", "pattern": "^#[0-9A-Fa-f]{6}${'$'}"},
        "footprint": {"type": "array", "items": {"type": "integer", "minimum": 1, "maximum": 64}, "minItems": 2, "maxItems": 2},
        "tags": {"type": "array", "items": {"${'$'}ref": "#/${'$'}defs/identifier"}},
        "properties": {"type": "array", "items": {"${'$'}ref": "#/${'$'}defs/property"}}
      }
    },
    "rule": {
      "description": "Checked against the state an action would produce. on place or remove: `tile` is the acted-on object. on always: checked after every place and remove for every object the rule targets, with `tile` bound to each in turn, so an invariant holds through removals, merges and reverts too. A `require` that evaluates false refuses the action with `message`. An `effect` sets one property of the acted-on object once, after every require holds; effects never trigger rules.",
      "type": "object",
      "required": ["id", "on"],
      "additionalProperties": false,
      "properties": {
        "id": {"${'$'}ref": "#/${'$'}defs/identifier"},
        "on": {"enum": ["place", "remove", "always"]},
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
        "verb": {"enum": ["place", "remove", "tick", "enroll", "opt_in"]},
        "label": {"type": "string"},
        "parameters": {
          "type": "array",
          "items": {
            "type": "object",
            "required": ["name", "type"],
            "additionalProperties": false,
            "properties": {
              "name": {"${'$'}ref": "#/${'$'}defs/identifier"},
              "type": {"enum": ["object_type", "col", "row", "count", "agent_type", "agent_id", "attributes", "properties", "boolean"]}
            }
          }
        },
        "emits": {"type": "array", "items": {"enum": ["place", "remove", "tick", "enroll", "opt_in"]}}
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
      "description": "Events the world ships with, replayed before any log; a seed place's id becomes the placed object's id.",
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
        "action": {"enum": ["place", "remove", "tick", "enroll", "opt_in", "branch", "merge", "revert", "endorse"]},
        "parameters": {"type": "object"},
        "timestamp": {"type": "string", "pattern": "^[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}(\\.[0-9]+)?Z${'$'}"},
        "touches": {
          "type": "array",
          "items": {"type": "string", "pattern": "^([0-9]+,[0-9]+|clock|agent:.+)${'$'}"},
          "description": "tiles as 'col,row', 'clock' for a tick, 'agent:<id>' for an agent; the merge and revert conflict rules compare these"
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
      "description": "revert(event): applies the inverse of a place or remove, as an action checked against the rules. Refused when a later event touched the same tiles.",
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
    }
  ]
}
"""
