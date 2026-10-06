"""Reference interpreter for world files: the executable specification the Kotlin engine must match.

Loads a world, replays an event log, applies an action only after its rules hold, advances the tick clock,
and reports counts, areas, equations, stocks and scores. Standard library only.
"""
from __future__ import annotations

import argparse
import copy
import json
import math
import re
import sys
from decimal import Decimal, InvalidOperation
from fractions import Fraction
from pathlib import Path

HERE = Path(__file__).resolve().parent

GENERIC_SPRITES = ("flat", "block", "tree", "arch", "water", "fence")
VOTE_CLASSES = ("on_site", "nearby", "supporting")
DIRECTIONS = {"north": (0, -1), "south": (0, 1), "east": (1, 0), "west": (-1, 0)}
SOURCED_KINDS = ("price", "labor", "yield", "regulation", "demographic")
INSTANCE_VERBS = ("place_instance", "remove_instance")
GROUND_VERBS = ("dig", "raise")
WORLD_VERBS = ("place", "remove", "tick", "enroll", "opt_in") + INSTANCE_VERBS + GROUND_VERBS
GOVERNANCE_VERBS = ("branch", "merge", "revert", "endorse")
MERGEABLE_VERBS = ("place", "remove", "tick", "enroll", "opt_in", "revert") + INSTANCE_VERBS + GROUND_VERBS
GROUND_FUNCTIONS = ("ground_mm", "min_ground_mm", "max_ground_mm", "slope_pct")
TILE_GROUND_FUNCTIONS = ("ground_mm", "slope_pct")
FUNCTIONS = ("count", "sum", "neighbors", "side", "edge", "distance", "if", "min", "max", "projected_support",
             "count_instances", "min_distance_mm") + GROUND_FUNCTIONS
GROUND_AMOUNT_FIELDS = {"dig": "depth_mm", "raise": "height_mm"}
MAX_GROUND_CHANGE_MM = 50000
GROUND_FLOOR_MM = -500000
GROUND_CEILING_MM = 5000000
BUILTIN_NAMES = ("tile", "tile_area", "now", "tick_length", "instance")
MAX_NODES = 256
MAX_DEPTH = 24
MAX_TICKS_PER_EVENT = 100_000
MAX_INSTANCES = 5000
MAX_NUMBER_EXPONENT = 400
MAX_FOOTPRINT_TILES = 2_000_000
NUMBER_EXPONENT_RULE = "number-exponent-too-large"
FOOTPRINT_RULE = "footprint-too-large"
EVENT_ID_RULE = "event-id-duplicate"
MM_PER_FT = Fraction("304.8")
ERROR_FIELDS = ("position_mm", "height_mm", "crown_radius_mm")
RENDERED_INSTANCE_FIELDS = ("id", "type", "x_mm", "y_mm", "z_mm", "rotation_deg", "height_mm", "crown_radius_mm", "provenance")

BASE_DIMS = ("ft", "lb", "usd", "hour", "tile", "head")
UNIT_TABLE = {
    "ft": (1.0, {"ft": 1}),
    "mm": (1.0 / 304.8, {"ft": 1}),
    "sq_ft": (1.0, {"ft": 2}),
    "acre": (43560.0, {"ft": 2}),
    "lb": (1.0, {"lb": 1}),
    "ton": (2000.0, {"lb": 1}),
    "usd": (1.0, {"usd": 1}),
    "second": (1.0 / 3600.0, {"hour": 1}),
    "minute": (1.0 / 60.0, {"hour": 1}),
    "hour": (1.0, {"hour": 1}),
    "day": (24.0, {"hour": 1}),
    "year": (8760.0, {"hour": 1}),
    "tile": (1.0, {"tile": 1}),
    "head": (1.0, {"head": 1}),
}
DIMENSIONLESS = (0,) * len(BASE_DIMS)
UNIT_SHAPE = re.compile(r"[a-z_]+(\^\d+)?(\s*[*/]\s*[a-z_]+(\^\d+)?)*")
UNIT_PART = re.compile(r"([*/]?)\s*([a-z_]+)(?:\^(\d+))?")


class ExprError(Exception):
    """An expression the language refuses; kind is syntax, unknown_word, unit_mismatch, bound or cycle."""

    def __init__(self, kind: str, message: str):
        super().__init__(message)
        self.kind = kind
        self.message = message


class Refusal(Exception):
    """An action the world refused; the log is left exactly as it was."""

    def __init__(self, rule: str, message: str):
        super().__init__(f"{rule}: {message}")
        self.rule = rule
        self.message = message


class WorldError(Exception):
    """A world file that does not load cleanly."""


class NumberExponentTooLarge(WorldError):
    """A JSON number whose decimal exponent passes the bound, refused before any arithmetic reads it."""


# ---------------------------------------------------------------- numbers as written


class WrittenNumber(float):
    """A JSON number: the double nearest it, carrying the decimal it was written as for the comparisons that must be exact."""

    def __new__(cls, written: Decimal):
        number = super().__new__(cls, float(written))
        number.written = written
        return number


def parse_decimal(text: str) -> Decimal | None:
    """The decimal a numeral writes, or None when its exponent is past what a Decimal can hold."""
    try:
        return Decimal(text)
    except InvalidOperation:
        return None


def is_exponent_too_large(written: Decimal | None) -> bool:
    """The exponent is the power of ten of the leading digit as written (Decimal.adjusted, BigDecimal precision - scale - 1)."""
    return written is None or abs(written.adjusted()) > MAX_NUMBER_EXPONENT


def describe_numeral(text: str) -> str:
    if len(text) <= 24:
        return text
    return f"{text[:12]}... ({len(text)} characters)"


def describe_exponent_refusal(text: str) -> str:
    return f"{NUMBER_EXPONENT_RULE}: {describe_numeral(text)} has a decimal exponent beyond ±{MAX_NUMBER_EXPONENT}"


def read_json_float(text: str) -> WrittenNumber:
    written = parse_decimal(text)
    if is_exponent_too_large(written):
        raise NumberExponentTooLarge(describe_exponent_refusal(text))
    return WrittenNumber(written)


def read_json_int(text: str) -> int:
    if is_exponent_too_large(parse_decimal(text)):
        raise NumberExponentTooLarge(describe_exponent_refusal(text))
    return int(text)


def read_json(text: str):
    """JSON whose every number is bounded by its exponent and keeps the decimal it was written as."""
    return json.loads(text, parse_float=read_json_float, parse_int=read_json_int)


# ---------------------------------------------------------------- units


def parse_unit(text: str | None) -> tuple[float, tuple[int, ...]]:
    cleaned = (text or "").strip()
    if cleaned in ("", "1"):
        return 1.0, DIMENSIONLESS
    if not UNIT_SHAPE.fullmatch(cleaned):
        raise ExprError("syntax", f"unit '{cleaned}' is not name(^n) joined by * or /")
    factor = 1.0
    exponents = [0] * len(BASE_DIMS)
    for operator, name, power in UNIT_PART.findall(cleaned):
        if name not in UNIT_TABLE:
            raise ExprError("unknown_word", f"unit '{name}' is not in the unit table")
        sign = -1 if operator == "/" else 1
        times = int(power) if power else 1
        unit_factor, unit_dims = UNIT_TABLE[name]
        factor *= unit_factor ** (sign * times)
        for base, exponent in unit_dims.items():
            exponents[BASE_DIMS.index(base)] += sign * times * exponent
    return factor, tuple(exponents)


def describe_dim(dim: tuple[int, ...]) -> str:
    parts = [f"{base}^{power}" if power != 1 else base for base, power in zip(BASE_DIMS, dim) if power]
    return " ".join(parts) if parts else "dimensionless"


def combine_dims(left: tuple[int, ...], right: tuple[int, ...], sign: int) -> tuple[int, ...]:
    return tuple(a + sign * b for a, b in zip(left, right))


# ---------------------------------------------------------------- expression grammar
#
# expr    := or
# or      := and ("or" and)*
# and     := not ("and" not)*
# not     := "not" not | compare
# compare := sum (("<" | "<=" | ">" | ">=" | "==" | "!=") sum)?
# sum     := term (("+" | "-") term)*
# term    := unary (("*" | "/") unary)*
# unary   := "-" unary | atom
# atom    := NUMBER ("[" UNIT "]")? | STRING | "true" | "false" | NAME | NAME "(" (expr ("," expr)*)? ")" | "(" expr ")"

TOKEN = re.compile(
    r"""\s*(?:
    (?P<number>\d+(?:\.\d+)?)
   |(?P<unit>\[[^\]\[]*\])
   |(?P<string>'[^']*')
   |(?P<name>[A-Za-z_][A-Za-z0-9_]*(?:\.[A-Za-z_][A-Za-z0-9_]*)?)
   |(?P<op><=|>=|==|!=|[-+*/<>(),])
    )""",
    re.X,
)
KEYWORDS = ("and", "or", "not", "true", "false")
COMPARATORS = ("<", "<=", ">", ">=", "==", "!=")


def tokenize_expression(text: str) -> list[tuple[str, str]]:
    tokens = []
    position = 0
    while position < len(text):
        if text[position:].strip() == "":
            break
        match = TOKEN.match(text, position)
        if not match or match.end() == position:
            raise ExprError("syntax", f"unexpected character at {position}: {text[position:position + 12]!r}")
        kind = match.lastgroup
        tokens.append((kind, match.group(kind)))
        position = match.end()
    return tokens


class ExpressionParser:
    def __init__(self, text: str):
        self.tokens = tokenize_expression(text)
        self.at = 0
        self.depth = 0
        self.nodes = 0

    def parse(self):
        node = self.parse_or()
        if self.at < len(self.tokens):
            raise ExprError("syntax", f"unexpected {self.tokens[self.at][1]!r} after a complete expression")
        return node

    def peek(self) -> str | None:
        return self.tokens[self.at][1] if self.at < len(self.tokens) else None

    def take(self) -> tuple[str, str]:
        if self.at >= len(self.tokens):
            raise ExprError("syntax", "expression ends early")
        token = self.tokens[self.at]
        self.at += 1
        return token

    def expect(self, value: str) -> None:
        kind, text = self.take()
        if text != value:
            raise ExprError("syntax", f"expected {value!r}, found {text!r}")

    def make(self, *node):
        self.nodes += 1
        if self.nodes > MAX_NODES:
            raise ExprError("bound", f"expression exceeds {MAX_NODES} nodes")
        return node

    def descend(self) -> None:
        self.depth += 1
        if self.depth > MAX_DEPTH:
            raise ExprError("bound", f"expression nests deeper than {MAX_DEPTH}")

    def parse_or(self):
        self.descend()
        node = self.parse_and()
        while self.peek() == "or":
            self.take()
            node = self.make("or", node, self.parse_and())
        self.depth -= 1
        return node

    def parse_and(self):
        node = self.parse_not()
        while self.peek() == "and":
            self.take()
            node = self.make("and", node, self.parse_not())
        return node

    def parse_not(self):
        if self.peek() == "not":
            self.take()
            self.descend()
            operand = self.parse_not()
            self.depth -= 1
            return self.make("not", operand)
        return self.parse_compare()

    def parse_compare(self):
        node = self.parse_sum()
        if self.peek() in COMPARATORS:
            operator = self.take()[1]
            node = self.make("cmp", operator, node, self.parse_sum())
        return node

    def parse_sum(self):
        node = self.parse_term()
        while self.peek() in ("+", "-"):
            operator = self.take()[1]
            node = self.make("arith", operator, node, self.parse_term())
        return node

    def parse_term(self):
        node = self.parse_unary()
        while self.peek() in ("*", "/"):
            operator = self.take()[1]
            node = self.make("arith", operator, node, self.parse_unary())
        return node

    def parse_unary(self):
        if self.peek() == "-":
            self.take()
            self.descend()
            operand = self.parse_unary()
            self.depth -= 1
            return self.make("neg", operand)
        return self.parse_atom()

    def parse_atom(self):
        kind, text = self.take()
        if kind == "number":
            written = parse_decimal(text)
            if is_exponent_too_large(written):
                raise ExprError(NUMBER_EXPONENT_RULE, describe_exponent_refusal(text))
            unit_text = ""
            if self.at < len(self.tokens) and self.tokens[self.at][0] == "unit":
                unit_text = self.take()[1][1:-1]
            factor, dim = parse_unit(unit_text)
            return self.make("num", float(written) * factor, dim)
        if kind == "string":
            return self.make("str", text[1:-1])
        if kind == "name" and text in ("true", "false"):
            return self.make("bool", text == "true")
        if kind == "name" and text in KEYWORDS:
            raise ExprError("syntax", f"{text!r} cannot start an operand")
        if kind == "name":
            if self.peek() == "(":
                return self.parse_call(text)
            return self.make("name", text)
        if text == "(":
            node = self.parse_or()
            self.expect(")")
            return node
        raise ExprError("syntax", f"unexpected {text!r}")

    def parse_call(self, function: str):
        if function not in FUNCTIONS:
            raise ExprError("unknown_word", f"function '{function}' is not in the closed vocabulary {FUNCTIONS}")
        self.expect("(")
        arguments = []
        if self.peek() != ")":
            arguments.append(self.parse_or())
        while self.peek() == ",":
            self.take()
            arguments.append(self.parse_or())
        self.expect(")")
        return self.make("call", function, tuple(arguments))


def parse_expression(text: str):
    if not isinstance(text, str) or not text.strip():
        raise ExprError("syntax", "expression is empty")
    return ExpressionParser(text).parse()


def walk_nodes(node):
    yield node
    tag = node[0]
    if tag in ("arith", "cmp"):
        yield from walk_nodes(node[2])
        yield from walk_nodes(node[3])
    elif tag in ("and", "or"):
        yield from walk_nodes(node[1])
        yield from walk_nodes(node[2])
    elif tag in ("not", "neg"):
        yield from walk_nodes(node[1])
    elif tag == "call":
        for argument in node[2]:
            yield from walk_nodes(argument)


def referenced_names(node) -> set[str]:
    return {item[1] for item in walk_nodes(node) if item[0] == "name"}


def projected_agent_types(node) -> set[str]:
    return {
        item[2][0][1]
        for item in walk_nodes(node)
        if item[0] == "call" and item[1] == "projected_support" and item[2] and item[2][0][0] == "str"
    }


# ---------------------------------------------------------------- static checking

NUM = "num"
BOOL = ("bool",)
STR = ("str",)
TILE = ("tile",)
INSTANCE = ("instance",)
TILE_DIM = parse_unit("tile")[1]
FT_DIM = parse_unit("ft")[1]


def num_type(dim: tuple[int, ...]) -> tuple:
    return (NUM, dim)


def describe_type(value_type: tuple) -> str:
    return describe_dim(value_type[1]) if value_type[0] == NUM else value_type[0]


class Scope:
    """What names, uses and functions an expression at one site of a world may use."""

    def __init__(self, world: "World", site: str, agent_type: str | None = None):
        self.world = world
        self.site = site
        self.agent_type = agent_type

    def type_of_name(self, name: str) -> tuple:
        world = self.world
        if name == "tile":
            if self.site == "rule":
                return TILE
            raise ExprError("unknown_word", "'tile' exists only inside a rule")
        if name == "instance":
            if self.site == "instance_rule":
                return INSTANCE
            raise ExprError("unknown_word", "'instance' exists only inside a place_instance or remove_instance rule")
        if name == "tile_area":
            return num_type(parse_unit("sq_ft/tile")[1])
        if name in ("now", "tick_length"):
            return num_type(parse_unit("hour")[1])
        if "." in name:
            return self.type_of_dotted(name)
        if name in world.equations:
            return num_type(parse_unit(world.equations[name]["unit"])[1])
        if name in world.stocks:
            return num_type(parse_unit(world.stocks[name]["unit"])[1])
        if name in world.scoring and self.site in ("scoring", "agent"):
            return num_type(parse_unit(world.scoring[name]["unit"])[1])
        raise ExprError("unknown_word", f"name '{name}' is not a built-in, equation, stock or score here")

    def type_of_dotted(self, name: str) -> tuple:
        head, member = name.split(".", 1)
        world = self.world
        if head == "self":
            if self.site != "agent":
                raise ExprError("unknown_word", "'self' exists only inside an agent's weight or utility")
            attributes = {a["name"]: a for a in world.agents[self.agent_type].get("attributes", [])}
            if member not in attributes:
                raise ExprError("unknown_word", f"agent {self.agent_type} has no attribute '{member}'")
            return num_type(parse_unit(attributes[member].get("unit"))[1])
        if world.link_alias and head == world.link_alias and world.linked_type is not None:
            return property_type(world.linked_type, member, name)
        if head in world.types:
            return property_type(world.types[head], member, name)
        raise ExprError("unknown_word", f"'{head}' in '{name}' is not an object type or a link alias")

    def is_known_use(self, use: str) -> bool:
        if use == "any":
            return True
        if use.startswith("#"):
            return use[1:] in self.world.tags
        return use in self.world.types

    def property_dim(self, prop: str) -> tuple[int, ...]:
        dims = set()
        for object_type in self.world.types.values():
            for spec in object_type.get("properties", []):
                if spec["name"] == prop and spec.get("type") != "text":
                    dims.add(parse_unit(spec.get("unit"))[1])
        if not dims:
            raise ExprError("unknown_word", f"no object type has a numeric property '{prop}'")
        if len(dims) > 1:
            raise ExprError("unit_mismatch", f"property '{prop}' is declared in different units across types")
        return dims.pop()


def property_type(object_type: dict, member: str, name: str) -> tuple:
    for spec in object_type.get("properties", []):
        if spec["name"] == member:
            if spec.get("type") == "text":
                return STR
            return num_type(parse_unit(spec.get("unit"))[1])
    raise ExprError("unknown_word", f"'{name}': type {object_type['id']} has no property '{member}'")


def check_expression(node, scope: Scope) -> tuple:
    tag = node[0]
    if tag == "num":
        return num_type(node[2])
    if tag == "str":
        return STR
    if tag == "bool":
        return BOOL
    if tag == "name":
        return scope.type_of_name(node[1])
    if tag == "neg":
        operand = check_expression(node[1], scope)
        require_number(operand, "negation")
        return operand
    if tag == "not":
        require_boolean(check_expression(node[1], scope), "not")
        return BOOL
    if tag in ("and", "or"):
        require_boolean(check_expression(node[1], scope), tag)
        require_boolean(check_expression(node[2], scope), tag)
        return BOOL
    if tag == "arith":
        return check_arithmetic(node, scope)
    if tag == "cmp":
        return check_comparison(node, scope)
    if tag == "call":
        return check_call(node[1], node[2], scope)
    raise ExprError("syntax", f"unknown node {tag}")


def require_number(value_type: tuple, where: str) -> None:
    if value_type[0] != NUM:
        raise ExprError("unit_mismatch", f"{where} needs a number, found {describe_type(value_type)}")


def require_boolean(value_type: tuple, where: str) -> None:
    if value_type != BOOL:
        raise ExprError("unit_mismatch", f"{where} needs true or false, found {describe_type(value_type)}")


def check_arithmetic(node, scope: Scope) -> tuple:
    operator, left, right = node[1], check_expression(node[2], scope), check_expression(node[3], scope)
    require_number(left, operator)
    require_number(right, operator)
    if operator in ("+", "-"):
        if left[1] != right[1]:
            raise ExprError("unit_mismatch", f"'{operator}' joins {describe_dim(left[1])} and {describe_dim(right[1])}")
        return left
    return num_type(combine_dims(left[1], right[1], 1 if operator == "*" else -1))


def check_comparison(node, scope: Scope) -> tuple:
    operator, left, right = node[1], check_expression(node[2], scope), check_expression(node[3], scope)
    if left[0] == NUM and right[0] == NUM:
        if left[1] != right[1]:
            raise ExprError("unit_mismatch", f"'{operator}' compares {describe_dim(left[1])} with {describe_dim(right[1])}")
        return BOOL
    if left == right and left in (STR, BOOL) and operator in ("==", "!="):
        return BOOL
    raise ExprError("unit_mismatch", f"'{operator}' cannot compare {describe_type(left)} with {describe_type(right)}")


def literal_use(argument, scope: Scope, function: str) -> str:
    if argument[0] != "str":
        raise ExprError("syntax", f"{function} takes a quoted use, not a computed value")
    if not scope.is_known_use(argument[1]):
        raise ExprError("unknown_word", f"{function}: use '{argument[1]}' is not an object type, #tag or 'any'")
    return argument[1]


def require_tile(argument, scope: Scope, function: str) -> None:
    if check_expression(argument, scope) != TILE:
        raise ExprError("unit_mismatch", f"{function} takes 'tile' as its first argument")


def require_instance_set(argument, scope: Scope, function: str) -> None:
    if argument[0] == "str":
        literal_use(argument, scope, function)
    elif check_expression(argument, scope) != INSTANCE:
        raise ExprError("unit_mismatch", f"{function} takes a quoted use or 'instance'")


def require_arity(function: str, arguments, allowed: tuple[int, ...]) -> None:
    if len(arguments) not in allowed:
        raise ExprError("syntax", f"{function} takes {' or '.join(map(str, allowed))} arguments, found {len(arguments)}")


def check_call(function: str, arguments, scope: Scope) -> tuple:
    if function == "count":
        require_arity(function, arguments, (1,))
        literal_use(arguments[0], scope, function)
        return num_type(TILE_DIM)
    if function == "sum":
        require_arity(function, arguments, (1,))
        if arguments[0][0] != "str":
            raise ExprError("syntax", "sum takes a quoted property name")
        return num_type(scope.property_dim(arguments[0][1]))
    if function == "neighbors":
        require_arity(function, arguments, (2, 3))
        require_tile(arguments[0], scope, function)
        radius = check_expression(arguments[1], scope)
        if radius != num_type(TILE_DIM):
            raise ExprError("unit_mismatch", f"neighbors radius must be in tile, found {describe_type(radius)}")
        if len(arguments) == 3:
            literal_use(arguments[2], scope, function)
        return num_type(TILE_DIM)
    if function == "side":
        require_arity(function, arguments, (3,))
        require_tile(arguments[0], scope, function)
        if arguments[1][0] != "str" or arguments[1][1] not in DIRECTIONS:
            raise ExprError("unknown_word", f"side direction must be one of {tuple(DIRECTIONS)}")
        literal_use(arguments[2], scope, function)
        return num_type(TILE_DIM)
    if function == "edge":
        require_arity(function, arguments, (1,))
        require_tile(arguments[0], scope, function)
        return num_type(FT_DIM)
    if function == "distance":
        require_arity(function, arguments, (2,))
        for argument in arguments:
            if argument[0] == "str":
                literal_use(argument, scope, function)
            else:
                require_tile(argument, scope, function)
        return num_type(FT_DIM)
    if function == "if":
        require_arity(function, arguments, (3,))
        require_boolean(check_expression(arguments[0], scope), "if condition")
        chosen, otherwise = check_expression(arguments[1], scope), check_expression(arguments[2], scope)
        if chosen != otherwise:
            raise ExprError("unit_mismatch", f"if branches differ: {describe_type(chosen)} and {describe_type(otherwise)}")
        return chosen
    if function in ("min", "max"):
        require_arity(function, arguments, (2,))
        left, right = check_expression(arguments[0], scope), check_expression(arguments[1], scope)
        require_number(left, function)
        if left != right:
            raise ExprError("unit_mismatch", f"{function} joins {describe_type(left)} and {describe_type(right)}")
        return left
    if function == "projected_support":
        require_arity(function, arguments, (1,))
        if arguments[0][0] != "str" or arguments[0][1] not in scope.world.agents:
            raise ExprError("unknown_word", "projected_support takes a quoted agent type the world declares")
        return num_type(DIMENSIONLESS)
    if function == "count_instances":
        require_arity(function, arguments, (1,))
        literal_use(arguments[0], scope, function)
        return num_type(DIMENSIONLESS)
    if function == "min_distance_mm":
        require_arity(function, arguments, (2,))
        for argument in arguments:
            require_instance_set(argument, scope, function)
        return num_type(FT_DIM)
    if function in GROUND_FUNCTIONS:
        return check_ground_call(function, arguments, scope)
    raise ExprError("unknown_word", f"function '{function}' is not in the closed vocabulary")


def check_ground_call(function: str, arguments, scope: Scope) -> tuple:
    require_arity(function, arguments, (0,))
    if scope.world.ground is None:
        raise ExprError("unknown_word", f"{function}(): world {scope.world.id} declares no ground")
    if function in TILE_GROUND_FUNCTIONS and scope.site != "rule":
        raise ExprError("unknown_word", f"{function}() reads the tile a rule is bound to; it exists only inside a rule")
    return num_type(DIMENSIONLESS) if function == "slope_pct" else num_type(FT_DIM)


# ---------------------------------------------------------------- world


class World:
    """A parsed world file. Expressions are parsed on first use; diagnose() reports every problem."""

    def __init__(self, path: str | Path, chain: tuple[str, ...] = ()):
        self.path = Path(path).resolve()
        self.doc = read_json(self.path.read_text(encoding="utf-8"))
        doc = self.doc
        self.id = doc["id"]
        self.grid = doc["grid"]
        self.cols, self.rows = int(self.grid["cols"]), int(self.grid["rows"])
        self.tile_ft = float(self.grid["tile_ft"])
        self.frame = doc.get("frame")
        self.ground = doc.get("ground")
        self.types = {t["id"]: t for t in doc.get("object_types", [])}
        self.tags = {tag for t in self.types.values() for tag in t.get("tags", [])}
        self.equations = {e["id"]: e for e in doc.get("equations", [])}
        self.stocks = {s["id"]: s for s in doc.get("stocks", [])}
        self.scoring = {s["id"]: s for s in doc.get("scoring", [])}
        self.actions = {a["verb"]: a for a in doc.get("actions", [])}
        self.agents = {a["type"]: a for a in doc.get("agents", [])}
        clock = doc.get("clock", {"tick_unit": "day", "tick_length": 1})
        self.tick_hours = float(clock["tick_length"]) * parse_unit(clock["tick_unit"])[0]
        self.link = (doc.get("links") or [None])[0]
        self.link_alias = self.link.get("as") if self.link else None
        self.link_problems: list[str] = []
        self.parent: World | None = None
        self.linked_instance: dict | None = None
        self.linked_type: dict | None = None
        self.ast_cache: dict[str, object] = {}
        self.inherited_rules: list[dict] = []
        if self.link:
            self.resolve_link(chain + (str(self.path),))
        self.rules = [r for r in doc.get("rules", []) if r.get("scope", "self") == "self"] + self.inherited_rules
        self.seed_cache: State | None = None

    def resolve_link(self, chain: tuple[str, ...]) -> None:
        parent_path = (self.path.parent / self.link["parent"]).resolve()
        if str(parent_path) in chain:
            self.link_problems.append(f"link cycle through {parent_path.name}")
            return
        if not parent_path.exists():
            self.link_problems.append(f"parent world {self.link['parent']} does not exist")
            return
        try:
            self.parent = World(parent_path, chain)
            parent_state = self.parent.seed_state()
        except (Refusal, ExprError, WorldError, KeyError, ValueError, TypeError) as problem:
            self.parent = None
            self.link_problems.append(f"parent seed does not replay: {problem}")
            return
        self.linked_instance = parent_state.instances.get(self.link["parcel"])
        if self.linked_instance is None:
            self.link_problems.append(f"parcel {self.link['parcel']} is not placed in {self.link['parent']}")
        else:
            self.linked_type = self.parent.types[self.linked_instance["type"]]
        exported = {r["id"]: r for r in self.parent.doc.get("rules", []) if r.get("scope") == "child"}
        for rule_id in self.link.get("inherit", []):
            if rule_id in exported:
                self.inherited_rules.append(dict(exported[rule_id], inherited_from=self.parent.id))
            else:
                self.link_problems.append(f"inherited rule {rule_id} is not a child-scope rule of {self.parent.id}")

    def ast(self, text: str):
        if text not in self.ast_cache:
            self.ast_cache[text] = parse_expression(text)
        return self.ast_cache[text]

    def extent_sq_ft(self) -> float:
        return self.cols * self.rows * self.tile_ft ** 2

    def tile_mm(self) -> Fraction:
        return exact(self.grid["tile_ft"]) * MM_PER_FT

    def extent_mm(self) -> tuple[Fraction, Fraction]:
        return self.cols * self.tile_mm(), self.rows * self.tile_mm()

    def linked_extent_sq_ft(self) -> float | None:
        if not self.linked_instance or not self.parent:
            return None
        return len(self.linked_instance["tiles"]) * self.parent.tile_ft ** 2

    def property_spec(self, type_id: str, name: str) -> dict | None:
        return next((p for p in self.types[type_id].get("properties", []) if p["name"] == name), None)

    def default_properties(self, type_id: str) -> dict:
        return {spec["name"]: stored_property(spec, spec.get("default")) for spec in self.types[type_id].get("properties", [])}

    def seed_state(self) -> "State":
        if self.seed_cache is None:
            state = State()
            if self.ground is not None:
                state.ground = list(self.ground["heights_mm"])
            for stock in self.stocks.values():
                factor = parse_unit(stock["unit"])[0]
                state.stocks[stock["id"]] = float(stock["initial"]) * factor
            for entry in self.doc.get("seed", []):
                if entry["action"] == "place_instance":
                    seat_measured_instance(self, state, entry)
                    continue
                event = {
                    "id": entry["id"],
                    "actor": entry.get("actor", f"world:{self.id}"),
                    "action": entry["action"],
                    "parameters": entry.get("parameters", {}),
                }
                state = apply_event(self, state, event, lambda event_id: None)
            self.seed_cache = state
        return self.seed_cache.copy()

    def expression_sites(self) -> list[dict]:
        """Every expression in the world with the scope it is checked in and the type it must produce."""
        sites = []
        for rule in self.doc.get("rules", []) + self.inherited_rules:
            where = f"rules.{rule['id']}"
            if rule in self.inherited_rules:
                where = f"inherited.{rule['id']}"
            site = "instance_rule" if rule.get("on") in INSTANCE_VERBS else "rule"
            if "require" in rule:
                sites.append({"where": f"{where}.require", "text": rule["require"], "scope": Scope(self, site), "expect": BOOL})
            if "effect" in rule:
                sites.append({"where": f"{where}.effect", "text": rule["effect"].get("to", ""), "scope": Scope(self, site),
                              "expect": self.effect_type(rule)})
        for equation in self.equations.values():
            sites.append({"where": f"equations.{equation['id']}", "text": equation["expr"], "scope": Scope(self, "equation"),
                          "expect_unit": equation["unit"]})
        for stock in self.stocks.values():
            sites.append({"where": f"stocks.{stock['id']}", "text": stock["next"], "scope": Scope(self, "stock"),
                          "expect_unit": stock["unit"]})
        for score in self.scoring.values():
            sites.append({"where": f"scoring.{score['id']}", "text": score["expr"], "scope": Scope(self, "scoring"),
                          "expect_unit": score["unit"], "binding": bool(score.get("binding", False))})
        for agent in self.agents.values():
            for field in ("weight", "utility"):
                sites.append({"where": f"agents.{agent['type']}.{field}", "text": agent[field],
                              "scope": Scope(self, "agent", agent["type"]), "expect_unit": "1"})
        return sites

    def effect_type(self, rule: dict) -> tuple | None:
        target = rule["effect"].get("set")
        if rule.get("on") in INSTANCE_VERBS:
            return ("missing", f"an instance carries no properties, so an {rule['on']} rule cannot set '{target}'")
        for type_id in rule.get("applies_to", []):
            if type_id in self.types:
                spec = self.property_spec(type_id, target)
                if spec is None:
                    return ("missing", f"type {type_id} has no property '{target}'")
                return STR if spec.get("type") == "text" else num_type(parse_unit(spec.get("unit"))[1])
        return None

    def name_graph(self) -> dict[str, set[str]]:
        graph: dict[str, set[str]] = {}
        named = {**{f"equation:{k}": v["expr"] for k, v in self.equations.items()},
                 **{f"scoring:{k}": v["expr"] for k, v in self.scoring.items()}}
        for agent in self.agents.values():
            named[f"agent:{agent['type']}"] = f"({agent['weight']}) + ({agent['utility']})"
        for node_id, text in named.items():
            try:
                tree = self.ast(text)
            except ExprError:
                graph[node_id] = set()
                continue
            edges = set()
            for name in referenced_names(tree):
                if name in self.equations:
                    edges.add(f"equation:{name}")
                elif name in self.scoring:
                    edges.add(f"scoring:{name}")
            edges |= {f"agent:{agent_type}" for agent_type in projected_agent_types(tree)}
            graph[node_id] = edges
        return graph

    def find_cycle(self) -> list[str]:
        graph = self.name_graph()
        colour = {node: "white" for node in graph}
        trail: list[str] = []

        def visit(node: str) -> list[str]:
            colour[node] = "grey"
            trail.append(node)
            for target in sorted(graph.get(node, ())):
                if colour.get(target) == "grey":
                    return trail[trail.index(target):] + [target]
                if colour.get(target) == "white":
                    found = visit(target)
                    if found:
                        return found
            trail.pop()
            colour[node] = "black"
            return []

        for node in sorted(graph):
            if colour[node] == "white":
                found = visit(node)
                if found:
                    return found
        return []

    def diagnose(self) -> list[dict]:
        findings = [{"where": "links", "kind": "link", "message": problem} for problem in self.link_problems]
        findings += [{"where": "ground", "kind": "ground", "message": problem} for problem in ground_problems(self)]
        findings += [{"where": f"object_types.{type_id}", "kind": FOOTPRINT_RULE,
                      "message": f"type {type_id} covers more than {MAX_FOOTPRINT_TILES} tiles"}
                     for type_id in self.types if is_footprint_too_large(self, type_id)]
        cycle = self.find_cycle()
        if cycle:
            findings.append({"where": "names", "kind": "cycle", "message": " -> ".join(cycle)})
        for site in self.expression_sites():
            problem = check_site(site)
            if problem:
                findings.append({"where": site["where"], **problem})
        return findings


def check_site(site: dict) -> dict | None:
    try:
        tree = site["scope"].world.ast(site["text"])
        produced = check_expression(tree, site["scope"])
        if "expect_unit" in site:
            wanted = num_type(parse_unit(site["expect_unit"])[1])
        else:
            wanted = site.get("expect")
        if wanted and wanted[0] == "missing":
            return {"kind": "unknown_word", "message": wanted[1]}
        if wanted is not None and produced != wanted:
            return {"kind": "unit_mismatch", "message": f"produces {describe_type(produced)}, declared {describe_type(wanted)}"}
    except ExprError as problem:
        return {"kind": problem.kind, "message": problem.message}
    return None


def expected_ground_length(world: "World") -> int:
    return (world.cols + 1) * (world.rows + 1)


def is_number(value) -> bool:
    return isinstance(value, (int, float)) and not isinstance(value, bool)


def ground_out_of_range(heights: list) -> list[int]:
    return [index for index, height in enumerate(heights)
            if is_number(height) and not GROUND_FLOOR_MM <= height <= GROUND_CEILING_MM]


def ground_problems(world: "World") -> list[str]:
    """What keeps a ground from being read: a vertex count other than (cols + 1) x (rows + 1), or a height out of range."""
    if world.ground is None:
        return []
    heights = world.ground["heights_mm"]
    problems = []
    if len(heights) != expected_ground_length(world):
        problems.append(f"{len(heights)} heights, but a {world.cols} x {world.rows} grid has (cols + 1) x (rows + 1) = "
                        f"{expected_ground_length(world)} vertices")
    outside = ground_out_of_range(heights)
    if outside:
        problems.append(f"heights at vertex indices {outside} lie outside {GROUND_FLOOR_MM} to {GROUND_CEILING_MM} mm")
    return problems


def tile_corner_indices(world: "World", col: int, row: int) -> list[int]:
    """A tile's four vertices in row order, NW, NE, SW, SE; vertex row 0 is the north edge."""
    vertex_cols = world.cols + 1
    return [row * vertex_cols + col, row * vertex_cols + col + 1, (row + 1) * vertex_cols + col, (row + 1) * vertex_cols + col + 1]


def tile_corner_names(col: int, row: int) -> list[str]:
    return [f"ground:{vc},{vr}" for vr in (row, row + 1) for vc in (col, col + 1)]


def tile_mean_mm(world: "World", heights: list, col: int, row: int) -> float:
    return sum(heights[index] for index in tile_corner_indices(world, col, row)) / 4


def tile_slope_pct(nw: float, ne: float, sw: float, se: float, side_mm: float) -> float:
    """The tile split on its SW-NE diagonal: the steeper of the two triangle planes' gradient magnitudes, as a percentage."""
    south_east_triangle = math.hypot(se - sw, ne - se)
    north_west_triangle = math.hypot(ne - nw, nw - sw)
    return 100 * max(south_east_triangle, north_west_triangle) / side_mm


def exact(value) -> Fraction:
    """A JSON number as the decimal it is written as, so tile edges and grid edges compare without binary rounding."""
    if isinstance(value, WrittenNumber):
        return Fraction(value.written)
    return Fraction(str(value))


def tiles_spanned(length_mm, tile_mm: Fraction) -> int:
    return math.ceil(exact(length_mm) / tile_mm)


def derived_footprint(world: "World", type_id: str) -> list[int]:
    return [tiles_spanned(length, world.tile_mm()) for length in world.types[type_id]["footprint_mm"]]


def footprint_of(world: "World", type_id: str) -> list[int]:
    object_type = world.types[type_id]
    if "footprint" in object_type:
        return object_type["footprint"]
    return derived_footprint(world, type_id)


def declared_footprints(world: "World", type_id: str) -> list[list[int]]:
    """Every tile size a type states: its footprint, and the one its footprint_mm spans."""
    object_type = world.types[type_id]
    sizes = [object_type["footprint"]] if "footprint" in object_type else []
    if "footprint_mm" in object_type:
        sizes.append(derived_footprint(world, type_id))
    return sizes


def is_footprint_too_large(world: "World", type_id: str) -> bool:
    return any(width * height > MAX_FOOTPRINT_TILES for width, height in declared_footprints(world, type_id))


def format_mm(value) -> str:
    return f"{float(value):.3f}".rstrip("0").rstrip(".")


def stored_property(spec: dict, value):
    if spec.get("type") == "text":
        return value
    return float(value) * parse_unit(spec.get("unit"))[0]


def load_schema(name: str) -> dict:
    return json.loads((HERE / name).read_text(encoding="utf-8"))


def is_json_type(instance, type_name: str) -> bool:
    if type_name == "object":
        return isinstance(instance, dict)
    if type_name == "array":
        return isinstance(instance, list)
    if type_name == "string":
        return isinstance(instance, str)
    if type_name == "boolean":
        return isinstance(instance, bool)
    if type_name == "null":
        return instance is None
    if type_name == "integer":
        return isinstance(instance, int) and not isinstance(instance, bool)
    if type_name == "number":
        return isinstance(instance, (int, float)) and not isinstance(instance, bool)
    return False


def resolve_ref(root: dict, reference: str) -> dict:
    node = root
    for part in reference.lstrip("#/").split("/"):
        node = node[part]
    return node


def schema_errors(instance, schema: dict, root: dict | None = None, where: str = "$") -> list[str]:
    """The subset of JSON Schema the two schemas use: $ref, anyOf, const, enum, type, required, properties,
    additionalProperties, items, min/maxItems, min/maxLength, pattern, minimum, exclusiveMinimum, maximum."""
    root = root or schema
    if "$ref" in schema:
        return schema_errors(instance, resolve_ref(root, schema["$ref"]), root, where)
    errors: list[str] = []
    if "anyOf" in schema and all(schema_errors(instance, option, root, where) for option in schema["anyOf"]):
        errors.append(f"{where}: matches none of its allowed shapes")
    if "const" in schema and instance != schema["const"]:
        errors.append(f"{where}: must be {schema['const']!r}")
    if "enum" in schema and instance not in schema["enum"]:
        errors.append(f"{where}: {instance!r} is not one of {schema['enum']}")
    if "type" in schema:
        allowed = schema["type"] if isinstance(schema["type"], list) else [schema["type"]]
        if not any(is_json_type(instance, t) for t in allowed):
            return errors + [f"{where}: expected {'/'.join(allowed)}"]
    if isinstance(instance, dict):
        for key in schema.get("required", []):
            if key not in instance:
                errors.append(f"{where}: missing '{key}'")
        declared = schema.get("properties", {})
        for key, value in instance.items():
            if key in declared:
                errors.extend(schema_errors(value, declared[key], root, f"{where}.{key}"))
            elif schema.get("additionalProperties") is False:
                errors.append(f"{where}: unexpected '{key}'")
    if isinstance(instance, list):
        if len(instance) < schema.get("minItems", 0):
            errors.append(f"{where}: needs at least {schema['minItems']} items")
        if "maxItems" in schema and len(instance) > schema["maxItems"]:
            errors.append(f"{where}: allows at most {schema['maxItems']} items")
        if "items" in schema:
            for index, item in enumerate(instance):
                errors.extend(schema_errors(item, schema["items"], root, f"{where}[{index}]"))
    if isinstance(instance, str):
        if len(instance) < schema.get("minLength", 0):
            errors.append(f"{where}: shorter than {schema['minLength']}")
        if "maxLength" in schema and len(instance) > schema["maxLength"]:
            errors.append(f"{where}: longer than {schema['maxLength']}")
        if "pattern" in schema and not re.search(schema["pattern"], instance):
            errors.append(f"{where}: does not match {schema['pattern']}")
    if is_json_type(instance, "number"):
        if "minimum" in schema and instance < schema["minimum"]:
            errors.append(f"{where}: below {schema['minimum']}")
        if "exclusiveMinimum" in schema and instance <= schema["exclusiveMinimum"]:
            errors.append(f"{where}: not above {schema['exclusiveMinimum']}")
        if "maximum" in schema and instance > schema["maximum"]:
            errors.append(f"{where}: above {schema['maximum']}")
    return errors


def load_world(path: str | Path) -> World:
    document = read_json(Path(path).read_text(encoding="utf-8"))
    problems = schema_errors(document, load_schema("world.schema.json"))
    if problems:
        raise WorldError(f"{Path(path).name} does not match world.schema.json: {problems[:3]}")
    world = World(path)
    findings = world.diagnose()
    if findings:
        raise WorldError("; ".join(f"{f['where']}: {f['kind']}: {f['message']}" for f in findings))
    return world


# ---------------------------------------------------------------- state


class State:
    """The world at one point of the log: placed instances, the cells they cover, stocks, clock and agents."""

    def __init__(self):
        self.instances: dict[str, dict] = {}
        self.cells: dict[tuple[int, int], str] = {}
        self.stocks: dict[str, float] = {}
        self.ticks = 0
        self.agents: dict[str, dict] = {}
        self.endorsements: list[dict] = []
        self.instance_layer: dict[str, dict] = {}
        self.ground: list | None = None
        self.ground_version = 0

    def copy(self) -> "State":
        return copy.deepcopy(self)

    def add_instance(self, instance: dict) -> None:
        self.instances[instance["id"]] = instance
        for tile in instance["tiles"]:
            self.cells[tuple(tile)] = instance["id"]

    def drop_instance(self, instance_id: str) -> dict:
        instance = self.instances.pop(instance_id)
        for tile in instance["tiles"]:
            self.cells.pop(tuple(tile), None)
        return instance

    def instance_at(self, col: int, row: int) -> dict | None:
        instance_id = self.cells.get((col, row))
        return self.instances.get(instance_id) if instance_id else None

    def snapshot(self) -> dict:
        canonical = {
            "cells": sorted([c, r, self.instances[i]["type"]] for (c, r), i in self.cells.items()),
            "stocks": {k: round(v, 9) for k, v in sorted(self.stocks.items())},
            "ticks": self.ticks,
            "agents": {k: v for k, v in sorted(self.agents.items())},
            "endorsements": list(self.endorsements),
        }
        if self.instance_layer:
            canonical["instances"] = dict(sorted(self.instance_layer.items()))
        if self.ground is not None:
            canonical["ground"] = {"heights_mm": list(self.ground), "version": self.ground_version}
        return canonical


def footprint_tiles(world: World, type_id: str, col: int, row: int) -> list[tuple[int, int]]:
    width, height = footprint_of(world, type_id)
    return [(col + dx, row + dy) for dy in range(height) for dx in range(width)]


def is_on_grid(world: World, tile: tuple[int, int]) -> bool:
    return 0 <= tile[0] < world.cols and 0 <= tile[1] < world.rows


def is_footprint_on_grid(world: World, type_id: str, col: int, row: int) -> bool:
    """Every tile of the footprint is on the grid, decided from its two corners so a huge footprint is never enumerated."""
    width, height = footprint_of(world, type_id)
    return is_on_grid(world, (col, row)) and is_on_grid(world, (col + width - 1, row + height - 1))


# ---------------------------------------------------------------- evaluation


class Evaluation:
    """Evaluates parsed expressions against one state. Names memoise; if() evaluates only its chosen branch.

    None is not-measured: it propagates through arithmetic, comparison, min, max and an if condition, and and/or/not
    are three-valued (Kleene)."""

    def __init__(self, world: World, state: State, tile_instance: dict | None = None, agent: dict | None = None,
                 acted_instance: dict | None = None):
        self.world = world
        self.state = state
        self.tile_instance = tile_instance
        self.agent = agent
        self.acted_instance = acted_instance
        self.memo: dict[str, float] = {}

    def value_of(self, node):
        tag = node[0]
        if tag in ("num", "str", "bool"):
            return node[1]
        if tag == "name":
            return self.value_of_name(node[1])
        if tag == "neg":
            operand = self.value_of(node[1])
            return None if operand is None else -operand
        if tag == "not":
            operand = self.value_of(node[1])
            return None if operand is None else not operand
        if tag == "and":
            return self.conjunction(node)
        if tag == "or":
            return self.disjunction(node)
        if tag == "arith":
            return self.arithmetic(node[1], self.value_of(node[2]), self.value_of(node[3]))
        if tag == "cmp":
            return compare_values(node[1], self.value_of(node[2]), self.value_of(node[3]))
        if tag == "call":
            return self.call(node[1], node[2])
        raise ExprError("syntax", f"unknown node {tag}")

    def conjunction(self, node):
        left = self.value_of(node[1])
        if left is False:
            return False
        right = self.value_of(node[2])
        if right is False:
            return False
        return None if left is None or right is None else True

    def disjunction(self, node):
        left = self.value_of(node[1])
        if left is True:
            return True
        right = self.value_of(node[2])
        if right is True:
            return True
        return None if left is None or right is None else False

    @staticmethod
    def arithmetic(operator: str, left: float | None, right: float | None) -> float | None:
        if left is None or right is None:
            return None
        if operator == "+":
            return left + right
        if operator == "-":
            return left - right
        if operator == "*":
            return left * right
        if right == 0:
            raise ExprError("divide_by_zero", "division by zero; guard it with if()")
        return left / right

    def value_of_name(self, name: str):
        world = self.world
        if name == "tile":
            return self.tile_instance
        if name == "instance":
            return self.acted_instance
        if name == "tile_area":
            return world.tile_ft ** 2
        if name == "now":
            return self.state.ticks * world.tick_hours
        if name == "tick_length":
            return world.tick_hours
        if "." in name:
            return self.value_of_dotted(name)
        if name in self.state.stocks:
            return self.state.stocks[name]
        if name not in self.memo:
            if name in world.equations:
                self.memo[name] = self.value_of(world.ast(world.equations[name]["expr"]))
            elif name in world.scoring:
                self.memo[name] = self.value_of(world.ast(world.scoring[name]["expr"]))
            else:
                raise ExprError("unknown_word", f"name '{name}'")
        return self.memo[name]

    def value_of_dotted(self, name: str):
        head, member = name.split(".", 1)
        world = self.world
        if head == "self":
            return self.agent["attributes"][member]
        if world.link_alias and head == world.link_alias and world.linked_instance is not None:
            return world.linked_instance["props"][member]
        return world.default_properties(head)[member]

    def tiles_of(self, argument) -> set[tuple[int, int]]:
        if argument[0] == "str":
            return {tile for tile, instance_id in self.state.cells.items() if self.matches_use(instance_id, argument[1])}
        return {tuple(tile) for tile in self.value_of(argument)["tiles"]}

    def choose(self, arguments):
        condition = self.value_of(arguments[0])
        if condition is None:
            return None
        return self.value_of(arguments[1]) if condition else self.value_of(arguments[2])

    def instances_of(self, argument) -> list[dict]:
        if argument[0] == "str":
            return [record for _, record in sorted(self.state.instance_layer.items())
                    if type_matches_use(self.world, record["type"], argument[1])]
        return [self.value_of(argument)]

    def matches_use(self, instance_id: str | None, use: str) -> bool:
        if instance_id is None:
            return False
        type_id = self.state.instances[instance_id]["type"]
        if use == "any":
            return True
        if use.startswith("#"):
            return use[1:] in self.world.types[type_id].get("tags", [])
        return type_id == use

    def call(self, function: str, arguments):
        state = self.state
        if function == "if":
            return self.choose(arguments)
        if function in ("min", "max"):
            values = [self.value_of(a) for a in arguments]
            if None in values:
                return None
            return min(values) if function == "min" else max(values)
        if function == "count_instances":
            return float(len(self.instances_of(arguments[0])))
        if function == "min_distance_mm":
            return nearest_instance_gap_ft(self.instances_of(arguments[0]), self.instances_of(arguments[1]))
        if function == "count":
            return float(sum(1 for i in state.cells.values() if self.matches_use(i, arguments[0][1])))
        if function == "sum":
            return float(sum(i["props"].get(arguments[0][1], 0.0) for i in state.instances.values()
                             if isinstance(i["props"].get(arguments[0][1], 0.0), (int, float))))
        if function == "neighbors":
            return self.count_neighbors(arguments)
        if function == "side":
            return self.count_side(arguments)
        if function == "edge":
            return self.edge_clearance()
        if function == "distance":
            return self.nearest_distance(self.tiles_of(arguments[0]), self.tiles_of(arguments[1]))
        if function == "projected_support":
            return self.projected_support(arguments[0][1])
        if function in GROUND_FUNCTIONS:
            return self.ground_value(function)
        raise ExprError("unknown_word", f"function '{function}'")

    def ground_value(self, function: str) -> float | None:
        heights = self.state.ground
        if function == "min_ground_mm":
            return min(heights) / 304.8
        if function == "max_ground_mm":
            return max(heights) / 304.8
        if self.tile_instance is None:
            return None
        tiles = [tuple(tile) for tile in self.tile_instance["tiles"]]
        if function == "ground_mm":
            return sum(tile_mean_mm(self.world, heights, c, r) for c, r in tiles) / len(tiles) / 304.8
        side_mm = float(self.world.tile_mm())
        return max(tile_slope_pct(*(heights[i] for i in tile_corner_indices(self.world, c, r)), side_mm) for c, r in tiles)

    def count_neighbors(self, arguments) -> float:
        footprint = {tuple(t) for t in self.tile_instance["tiles"]}
        radius = int(self.value_of(arguments[1]))
        use = arguments[2][1] if len(arguments) == 3 else "any"
        ring = {
            (col, row)
            for col in range(self.world.cols)
            for row in range(self.world.rows)
            if (col, row) not in footprint
            and min(abs(col - fc) + abs(row - fr) for fc, fr in footprint) <= radius
        }
        return float(sum(1 for tile in ring if self.matches_use(self.state.cells.get(tile), use)))

    def count_side(self, arguments) -> float:
        footprint = {tuple(t) for t in self.tile_instance["tiles"]}
        step_col, step_row = DIRECTIONS[arguments[1][1]]
        beside = {(c + step_col, r + step_row) for c, r in footprint} - footprint
        use = arguments[2][1]
        return float(sum(1 for tile in beside if is_on_grid(self.world, tile)
                         and self.matches_use(self.state.cells.get(tile), use)))

    def edge_clearance(self) -> float:
        world = self.world
        clearance = min(min(c, r, world.cols - 1 - c, world.rows - 1 - r) for c, r in self.tile_instance["tiles"])
        return clearance * world.tile_ft

    def nearest_distance(self, first: set, second: set) -> float:
        if not first or not second:
            return math.inf
        return min(math.dist(a, b) for a in first for b in second) * self.world.tile_ft

    def projected_support(self, agent_type: str) -> float:
        spec = self.world.agents[agent_type]
        weight_tree, utility_tree = self.world.ast(spec["weight"]), self.world.ast(spec["utility"])
        total = approving = 0.0
        for agent in sorted(self.state.agents.values(), key=lambda a: a["id"]):
            if agent["type"] != agent_type:
                continue
            per_agent = Evaluation(self.world, self.state, agent=agent)
            weight = per_agent.value_of(weight_tree)
            total += weight
            if per_agent.value_of(utility_tree) > 0:
                approving += weight
        return approving / total if total > 0 else 0.0


def type_matches_use(world: World, type_id: str, use: str) -> bool:
    if use == "any":
        return True
    if use.startswith("#"):
        return use[1:] in world.types[type_id].get("tags", [])
    return type_id == use


def planar_distance_mm(first: dict, second: dict) -> float:
    """Coordinates become doubles before they are subtracted, then the root of the summed squares, so every port gets one double."""
    east = float(first["x_mm"]) - float(second["x_mm"])
    north = float(first["y_mm"]) - float(second["y_mm"])
    return math.sqrt(east * east + north * north)


def nearest_instance_gap_ft(first: list[dict], second: list[dict]) -> float | None:
    """Least centre-to-centre distance in the plane over pairs of two different instances; None when no pair exists."""
    gaps = (planar_distance_mm(a, b) for a in first for b in second if a["id"] != b["id"])
    nearest = min(gaps, default=None)
    return None if nearest is None else nearest / 304.8


def compare_values(operator: str, left, right) -> bool | None:
    if left is None or right is None:
        return None
    if operator == "<":
        return left < right
    if operator == "<=":
        return left <= right
    if operator == ">":
        return left > right
    if operator == ">=":
        return left >= right
    if operator == "==":
        return left == right
    return left != right


# ---------------------------------------------------------------- applying events


def rule_targets(world: World, rule: dict, type_id: str | None) -> bool:
    if type_id is None:
        return "applies_to" not in rule and "applies_to_tag" not in rule
    if "applies_to" in rule:
        return type_id in rule["applies_to"]
    if "applies_to_tag" in rule:
        return rule["applies_to_tag"] in world.types[type_id].get("tags", [])
    return True


def rule_applies(world: World, rule: dict, verb: str, type_id: str) -> bool:
    return rule.get("on", "place") == verb and rule_targets(world, rule, type_id)


def check_rules(world: World, state: State, verb: str, instance: dict) -> None:
    """A place or remove rule is checked on the acted-on object; an always rule on every object it targets."""
    for rule in world.rules:
        if "require" not in rule:
            continue
        if rule.get("on") == "always":
            subjects = [i for _, i in sorted(state.instances.items()) if rule_targets(world, rule, i["type"])]
        elif rule_applies(world, rule, verb, instance["type"]):
            subjects = [instance]
        else:
            subjects = []
        tree = world.ast(rule["require"])
        for subject in subjects:
            if Evaluation(world, state, **rule_binding(subject)).value_of(tree) is False:
                raise Refusal(rule["id"], rule.get("message") or "refused")


def rule_binding(subject: dict) -> dict:
    """A tile object binds `tile`; an instance, which has a point and no tiles, binds `instance`."""
    if "x_mm" in subject:
        return {"acted_instance": subject}
    return {"tile_instance": subject}


def apply_effects(world: World, state: State, verb: str, instance: dict) -> None:
    for rule in world.rules:
        if "effect" not in rule or not rule_applies(world, rule, verb, instance["type"]):
            continue
        value = Evaluation(world, state, tile_instance=instance).value_of(world.ast(rule["effect"]["to"]))
        state.instances[instance["id"]]["props"][rule["effect"]["set"]] = value


def require_world_verb(world: World, verb: str) -> None:
    if verb not in world.actions:
        raise Refusal("unknown-action", f"world {world.id} declares no action '{verb}'")


def apply_place(world: World, state: State, event: dict, lookup) -> State:
    require_world_verb(world, "place")
    parameters = event["parameters"]
    type_id = parameters["type"]
    if type_id not in world.types:
        raise Refusal("unknown-type", f"no object type '{type_id}'")
    if not is_footprint_on_grid(world, type_id, int(parameters["col"]), int(parameters["row"])):
        raise Refusal("off-grid", f"{type_id} at {parameters['col']},{parameters['row']} leaves the grid")
    tiles = footprint_tiles(world, type_id, int(parameters["col"]), int(parameters["row"]))
    occupied = [tile for tile in tiles if tile in state.cells]
    if occupied:
        raise Refusal("occupied", f"tile {occupied[0][0]},{occupied[0][1]} already holds {state.instance_at(*occupied[0])['type']}; remove it first")
    properties = world.default_properties(type_id)
    for name, value in parameters.get("properties", {}).items():
        spec = world.property_spec(type_id, name)
        if spec is None:
            raise Refusal("unknown-property", f"{type_id} has no property '{name}'")
        properties[name] = stored_property(spec, value)
    instance = {"id": event["id"], "type": type_id, "col": int(parameters["col"]), "row": int(parameters["row"]),
                "tiles": [list(t) for t in tiles], "props": properties}
    candidate = state.copy()
    candidate.add_instance(instance)
    check_rules(world, candidate, "place", instance)
    apply_effects(world, candidate, "place", instance)
    return candidate


def apply_remove(world: World, state: State, event: dict, lookup) -> State:
    require_world_verb(world, "remove")
    parameters = event["parameters"]
    instance = state.instance_at(int(parameters["col"]), int(parameters["row"]))
    if instance is None:
        raise Refusal("empty-tile", f"nothing to remove at {parameters['col']},{parameters['row']}")
    candidate = state.copy()
    removed = candidate.drop_instance(instance["id"])
    check_rules(world, candidate, "remove", removed)
    return candidate


def instance_type(world: World, type_id: str) -> dict:
    if type_id not in world.types:
        raise Refusal("unknown-type", f"no object type '{type_id}'")
    return world.types[type_id]


def instance_height(object_type: dict, own_height):
    height = own_height if own_height is not None else object_type.get("height_mm")
    if height is None:
        raise Refusal("instance-needs-height", f"{object_type['id']} has no height_mm and the action gives none")
    return height


def proposed_instance(world: World, event: dict) -> dict:
    parameters = event["parameters"]
    object_type = instance_type(world, parameters["type"])
    return {"id": event["id"], "type": object_type["id"], "x_mm": parameters["x_mm"], "y_mm": parameters["y_mm"],
            "z_mm": 0, "rotation_deg": parameters.get("rotation_deg", 0), "height_mm": instance_height(object_type, None),
            "crown_radius_mm": None, "provenance": "proposed", "source": None, "error": None}


def measured_instance(world: World, entry: dict) -> dict:
    parameters = entry["parameters"]
    object_type = instance_type(world, parameters["type"])
    return {"id": entry["id"], "type": object_type["id"], "x_mm": parameters["x_mm"], "y_mm": parameters["y_mm"],
            "z_mm": parameters.get("z_mm", 0), "rotation_deg": parameters.get("rotation_deg", 0),
            "height_mm": instance_height(object_type, parameters.get("height_mm")),
            "crown_radius_mm": parameters.get("crown_radius_mm"), "provenance": "measured",
            "source": parameters.get("source"), "error": parameters.get("error")}


def is_inside_grid_mm(world: World, x_mm, y_mm) -> bool:
    width, height = world.extent_mm()
    return 0 <= exact(x_mm) < width and 0 <= exact(y_mm) < height


def check_instance_room(world: World, state: State, record: dict) -> None:
    if not is_inside_grid_mm(world, record["x_mm"], record["y_mm"]):
        width, height = world.extent_mm()
        raise Refusal("instance-off-grid", f"{record['type']} at {format_mm(record['x_mm'])},{format_mm(record['y_mm'])} mm "
                                           f"lies outside the {format_mm(width)} x {format_mm(height)} mm grid")
    if len(state.instance_layer) >= MAX_INSTANCES:
        raise Refusal("instance-cap", f"the instance layer already holds {MAX_INSTANCES} instances, the cap")


def seat_measured_instance(world: World, state: State, entry: dict) -> None:
    """A measured instance is a fact the seed records, not an action, so no rule is checked against it."""
    record = measured_instance(world, entry)
    check_instance_room(world, state, record)
    state.instance_layer[record["id"]] = record


def apply_place_instance(world: World, state: State, event: dict, lookup) -> State:
    require_world_verb(world, "place_instance")
    record = proposed_instance(world, event)
    candidate = state.copy()
    check_instance_room(world, candidate, record)
    candidate.instance_layer[event["id"]] = record
    check_rules(world, candidate, "place_instance", record)
    return candidate


def apply_remove_instance(world: World, state: State, event: dict, lookup) -> State:
    require_world_verb(world, "remove_instance")
    instance_id = event["parameters"]["id"]
    if instance_id not in state.instance_layer:
        raise Refusal("unknown-instance", f"no instance '{instance_id}' to remove")
    candidate = state.copy()
    removed = candidate.instance_layer.pop(instance_id)
    check_rules(world, candidate, "remove_instance", removed)
    return candidate


def ground_amount(verb: str, parameters: dict):
    field = GROUND_AMOUNT_FIELDS[verb]
    amount = parameters.get(field)
    if not is_number(amount):
        raise Refusal("ground-amount", f"{verb} needs {field} as a number of millimetres")
    if not 0 < amount <= MAX_GROUND_CHANGE_MM:
        raise Refusal("ground-amount", f"{verb} needs {field} above 0 and at most {MAX_GROUND_CHANGE_MM} mm, found {format_mm(amount)}")
    return amount


def check_ground_range(verb: str, col: int, row: int, corners: list) -> None:
    for height in corners:
        if not GROUND_FLOOR_MM <= height <= GROUND_CEILING_MM:
            raise Refusal("ground-range", f"{verb} at {col},{row} would take ground to {format_mm(height)} mm, "
                                          f"outside {GROUND_FLOOR_MM} to {GROUND_CEILING_MM} mm")


def ground_subject(state: State, col: int, row: int) -> dict:
    """What a dig or raise rule binds as `tile`: the acted tile, typed by the object on it, if any."""
    occupant = state.instance_at(col, row)
    return {"id": None, "type": occupant["type"] if occupant else None, "col": col, "row": row, "tiles": [[col, row]]}


def apply_ground_change(world: World, state: State, event: dict, lookup) -> State:
    """dig lowers and raise lifts a tile's four corner vertices by the amount, each once; then the rules are checked."""
    verb = event["action"]
    if verb not in world.actions:
        raise Refusal("unknown-action", f"no action '{verb}'")
    if world.ground is None:
        raise Refusal("no-ground", f"world {world.id} has no ground to dig or raise")
    parameters = event["parameters"]
    col, row = int(parameters["col"]), int(parameters["row"])
    if not is_on_grid(world, (col, row)):
        raise Refusal("off-grid", f"tile {col},{row} is not on the {world.cols} x {world.rows} grid")
    amount = ground_amount(verb, parameters)
    change = -amount if verb == "dig" else amount
    candidate = state.copy()
    corners = tile_corner_indices(world, col, row)
    for index in corners:
        candidate.ground[index] = candidate.ground[index] + change
    check_ground_range(verb, col, row, [candidate.ground[index] for index in corners])
    candidate.ground_version += 1
    check_rules(world, candidate, verb, ground_subject(candidate, col, row))
    return candidate


def apply_tick(world: World, state: State, event: dict, lookup) -> State:
    require_world_verb(world, "tick")
    steps = int(event["parameters"].get("n", 1))
    if not 1 <= steps <= MAX_TICKS_PER_EVENT:
        raise Refusal("tick-bound", f"a tick event advances 1 to {MAX_TICKS_PER_EVENT} ticks")
    candidate = state.copy()
    for _ in range(steps):
        evaluation = Evaluation(world, candidate)
        following = {stock_id: evaluation.value_of(world.ast(stock["next"])) for stock_id, stock in world.stocks.items()}
        candidate.stocks.update(following)
        candidate.ticks += 1
    return candidate


def stored_attributes(world: World, agent_type: str, attributes: dict) -> dict:
    specs = {a["name"]: a for a in world.agents[agent_type].get("attributes", [])}
    unknown = sorted(set(attributes) - set(specs))
    if unknown:
        raise Refusal("unknown-attribute", f"agent type {agent_type} has no attribute {unknown}")
    return {name: float(value) * parse_unit(specs[name].get("unit"))[0] for name, value in attributes.items()}


def apply_enroll(world: World, state: State, event: dict, lookup) -> State:
    require_world_verb(world, "enroll")
    parameters = event["parameters"]
    agent_type, agent_id = parameters["agent_type"], parameters["agent_id"]
    if agent_type not in world.agents:
        raise Refusal("unknown-agent-type", f"no agent type '{agent_type}'")
    if agent_id in state.agents:
        raise Refusal("agent-exists", f"agent {agent_id} is already enrolled")
    defaults = {a["name"]: a.get("default", 0) for a in world.agents[agent_type].get("attributes", [])}
    candidate = state.copy()
    candidate.agents[agent_id] = {
        "id": agent_id,
        "type": agent_type,
        "synthetic": bool(parameters.get("synthetic", True)),
        "attributes": stored_attributes(world, agent_type, {**defaults, **parameters.get("attributes", {})}),
    }
    return candidate


def apply_opt_in(world: World, state: State, event: dict, lookup) -> State:
    require_world_verb(world, "opt_in")
    parameters = event["parameters"]
    agent = state.agents.get(parameters["agent_id"])
    if agent is None:
        raise Refusal("unknown-agent", f"no agent {parameters['agent_id']} to replace")
    candidate = state.copy()
    replaced = candidate.agents[agent["id"]]
    replaced["synthetic"] = False
    replaced["attributes"].update(stored_attributes(world, agent["type"], parameters.get("attributes", {})))
    return candidate


def apply_endorse(world: World, state: State, event: dict, lookup) -> State:
    actor = event["actor"]
    weight_class = event["parameters"].get("weight_class")
    agent = state.agents.get(actor)
    if agent is not None and agent["synthetic"]:
        raise Refusal("projection-not-binding", f"{actor} is a synthetic agent; its vote is a projection and cannot endorse")
    if weight_class not in VOTE_CLASSES:
        raise Refusal("unknown-weight-class", f"weight class must be one of {VOTE_CLASSES}")
    candidate = state.copy()
    candidate.endorsements.append({"actor": actor, "weight_class": weight_class})
    return candidate


def apply_revert(world: World, state: State, event: dict, lookup) -> State:
    undo = event["parameters"]["undo"]
    return apply_event(world, state, {"id": event["id"], "actor": event["actor"], "action": undo["action"],
                                      "parameters": undo["parameters"]}, lookup)


def apply_merge(world: World, state: State, event: dict, lookup) -> State:
    for event_id in event["parameters"]["events"]:
        try:
            state = apply_event(world, state, lookup(event_id), lookup)
        except Refusal as refusal:
            raise Refusal(refusal.rule, f"merging {event_id}: {refusal.message}") from None
    return state


def apply_branch(world: World, state: State, event: dict, lookup) -> State:
    return state


HANDLERS = {
    "place": apply_place,
    "remove": apply_remove,
    "place_instance": apply_place_instance,
    "remove_instance": apply_remove_instance,
    "dig": apply_ground_change,
    "raise": apply_ground_change,
    "tick": apply_tick,
    "enroll": apply_enroll,
    "opt_in": apply_opt_in,
    "endorse": apply_endorse,
    "revert": apply_revert,
    "merge": apply_merge,
    "branch": apply_branch,
}


def apply_event(world: World, state: State, event: dict, lookup) -> State:
    handler = HANDLERS.get(event["action"])
    if handler is None:
        raise Refusal("unknown-action", f"no action '{event['action']}'")
    return handler(world, state, event, lookup)


# ---------------------------------------------------------------- the log


class Log:
    """An append-only event DAG. A branch's state is the fold of the chain from the seed to its head."""

    def __init__(self, world: World):
        self.world = world
        self.events: list[dict] = []
        self.by_id: dict[str, dict] = {}
        self.heads: dict[str, str | None] = {"main": None}
        self.branch_meta: dict[str, dict] = {"main": {"from": None, "proposal": False}}
        self.states: dict[str | None, State] = {None: world.seed_state()}

    @classmethod
    def load(cls, world: World, document: dict) -> "Log":
        problems = schema_errors(document, load_schema("events.schema.json"))
        if problems:
            raise WorldError(f"event log does not match events.schema.json: {problems[:3]}")
        repeated = first_repeated_event_id(document["events"])
        if repeated is not None:
            raise WorldError(f"{EVENT_ID_RULE}: event id {repeated} appears twice in the log")
        log = cls(world)
        for event in document["events"]:
            log.replay(event)
        return log

    def dump(self) -> dict:
        return {"world": self.world.id, "events": copy.deepcopy(self.events)}

    def replay(self, event: dict) -> None:
        if event["world"] != self.world.id:
            raise WorldError(f"event {event['id']} belongs to world {event['world']}")
        if event["id"] in self.by_id:
            raise WorldError(f"{EVENT_ID_RULE}: event id {event['id']} appears twice")
        if event["action"] == "branch":
            start = event["parameters"]["from"]
            if event["parent"] != start or (start is not None and start not in self.by_id):
                raise WorldError(f"branch event {event['id']} does not start where it says")
            self.open_branch(event)
            return
        if self.heads.get(event["branch"], "missing") != event["parent"]:
            raise WorldError(f"event {event['id']} does not extend the head of branch {event['branch']}")
        governance = self.recheck_governance(event)
        if governance:
            raise WorldError(f"event {event['id']} does not replay: {governance}")
        result = self.commit({k: v for k, v in event.items() if k not in ("touches", "removed", "removed_instance")})
        if isinstance(result, Refusal):
            raise WorldError(f"event {event['id']} does not replay: {result}")

    def lookup(self, event_id: str) -> dict:
        return self.by_id[event_id]

    def chain(self, head: str | None) -> list[dict]:
        events = []
        while head is not None:
            event = self.by_id[head]
            events.append(event)
            head = event["parent"]
        return list(reversed(events))

    def fold(self, head: str | None) -> State:
        state = self.world.seed_state()
        for event in self.chain(head):
            state = apply_event(self.world, state, event, self.lookup)
        return state

    def state_of(self, branch: str = "main") -> State:
        return self.states[self.heads[branch]].copy()

    def flatten(self, chain: list[dict]) -> list[dict]:
        primitive = []
        for event in chain:
            if event["action"] == "merge":
                primitive.extend(self.flatten([self.by_id[i] for i in event["parameters"]["events"]]))
            elif event["action"] in MERGEABLE_VERBS:
                primitive.append(event)
        return primitive

    def next_event_id(self) -> str:
        """One past the highest e<n> held, which is e<count + 1> for a log the engine wrote and never a reused id after a load."""
        return f"e{max((int(event['id'][1:]) for event in self.events), default=0) + 1}"

    def draft(self, branch: str, actor: str, action: str, parameters: dict, timestamp: str) -> dict:
        return {
            "id": self.next_event_id(),
            "parent": self.heads[branch],
            "world": self.world.id,
            "branch": branch,
            "actor": actor,
            "action": action,
            "parameters": parameters,
            "timestamp": timestamp,
        }

    def commit(self, event: dict):
        prior = self.states[event["parent"]]
        try:
            following = apply_event(self.world, prior, event, self.lookup)
        except Refusal as refusal:
            return refusal
        event["touches"] = self.touches_of(event, prior)
        if event["action"] == "remove":
            removed = prior.instance_at(int(event["parameters"]["col"]), int(event["parameters"]["row"]))
            event["removed"] = {"type": removed["type"], "col": removed["col"], "row": removed["row"],
                                "properties": self.declared_properties(removed)}
        if event["action"] == "remove_instance":
            event["removed_instance"] = copy.deepcopy(prior.instance_layer[event["parameters"]["id"]])
        self.append(event, following)
        return event

    def declared_properties(self, instance: dict) -> dict:
        declared = {}
        for spec in self.world.types[instance["type"]].get("properties", []):
            value = instance["props"].get(spec["name"])
            declared[spec["name"]] = value if spec.get("type") == "text" else value / parse_unit(spec.get("unit"))[0]
        return declared

    def append(self, event: dict, state: State) -> None:
        self.events.append(event)
        self.by_id[event["id"]] = event
        self.heads[event["branch"]] = event["id"]
        self.states[event["id"]] = state

    def touches_of(self, event: dict, prior: State) -> list[str]:
        action, parameters = event["action"], event["parameters"]
        if action == "place":
            tiles = footprint_tiles(self.world, parameters["type"], int(parameters["col"]), int(parameters["row"]))
            return [f"{c},{r}" for c, r in tiles]
        if action == "remove":
            instance = prior.instance_at(int(parameters["col"]), int(parameters["row"]))
            return [f"{c},{r}" for c, r in instance["tiles"]]
        if action == "place_instance":
            return [f"instance:{event['id']}"]
        if action == "remove_instance":
            return [f"instance:{parameters['id']}"]
        if action in GROUND_VERBS:
            return tile_corner_names(int(parameters["col"]), int(parameters["row"]))
        if action == "tick":
            return ["clock"]
        if action in ("enroll", "opt_in"):
            return [f"agent:{parameters['agent_id']}"]
        if action == "revert":
            return list(self.by_id[parameters["event"]]["touches"])
        if action == "merge":
            return sorted({t for i in parameters["events"] for t in self.by_id[i]["touches"]})
        return []

    def attempt(self, branch: str, actor: str, action: str, parameters: dict, timestamp: str):
        if branch not in self.heads:
            return Refusal("unknown-branch", f"no branch '{branch}'")
        if action in ("branch", "merge", "revert"):
            return Refusal("governance-verb", f"use Log.{action}() for '{action}'")
        if action == "endorse" and not self.branch_meta[branch]["proposal"]:
            return Refusal("not-a-proposal", f"branch {branch} is not a proposal")
        return self.commit(self.draft(branch, actor, action, parameters, timestamp))

    def branch(self, name: str, source: str, actor: str, timestamp: str, proposal: bool = False):
        if name in self.heads:
            return Refusal("branch-exists", f"branch {name} already exists")
        start = self.heads[source] if source in self.heads else source
        if start is not None and start not in self.by_id:
            return Refusal("unknown-branch", f"no branch or event '{source}'")
        event = {"id": self.next_event_id(), "parent": start, "world": self.world.id, "branch": name,
                 "actor": actor, "action": "branch", "parameters": {"name": name, "from": start, "proposal": proposal},
                 "timestamp": timestamp}
        self.open_branch(event)
        return event

    def open_branch(self, event: dict) -> None:
        name = event["parameters"]["name"]
        if name in self.heads:
            raise WorldError(f"branch {name} is opened twice")
        event["touches"] = []
        self.branch_meta[name] = {"from": event["parameters"]["from"], "proposal": bool(event["parameters"]["proposal"])}
        self.events.append(event)
        self.by_id[event["id"]] = event
        self.heads[name] = event["id"]
        self.states[event["id"]] = self.states[event["parameters"]["from"]]

    def endorsements_on(self, branch: str) -> list[dict]:
        return [e for e in self.chain(self.heads[branch]) if e["action"] == "endorse" and e["branch"] == branch]

    def plan_merge(self, source: str, into: str):
        """The source events a merge would apply, or the refusal: the conflict rule lives here and nowhere else."""
        if source not in self.heads or into not in self.heads:
            return Refusal("unknown-branch", f"merge needs two branches, got {source} and {into}")
        if self.branch_meta[source]["proposal"] and not self.endorsements_on(source):
            return Refusal("proposal-unendorsed", f"proposal {source} carries no endorsement from a real person")
        target_chain, source_chain = self.chain(self.heads[into]), self.chain(self.heads[source])
        target_applied = {e["id"] for e in target_chain} | {e["id"] for e in self.flatten(target_chain)}
        source_applied = {e["id"] for e in source_chain} | {e["id"] for e in self.flatten(source_chain)}
        incoming = [e for e in self.flatten(source_chain) if e["id"] not in target_applied]
        if not incoming:
            return Refusal("nothing-to-merge", f"{into} already holds every event of {source}")
        diverged = [e for e in self.flatten(target_chain) if e["id"] not in source_applied]
        clash = sorted({t for e in incoming for t in e["touches"]} & {t for e in diverged for t in e["touches"]})
        if clash:
            return Refusal("merge-conflict", f"both branches changed {', '.join(clash)} since they split")
        return [e["id"] for e in incoming]

    def merge(self, source: str, into: str, actor: str, timestamp: str):
        plan = self.plan_merge(source, into)
        if isinstance(plan, Refusal):
            return plan
        return self.commit(self.draft(into, actor, "merge", {"branch": source, "events": plan}, timestamp))

    def plan_revert(self, event_id: str, branch: str):
        """The inverse a revert would apply, or the refusal."""
        applied = self.flatten(self.chain(self.heads[branch]))
        ids = [e["id"] for e in applied]
        if event_id not in ids:
            return Refusal("revert-unknown", f"{event_id} is not applied on {branch}")
        target = self.by_id[event_id]
        if target["action"] not in ("place", "remove") + GROUND_VERBS:
            return Refusal("revert-unsupported", f"only place and remove revert; branch from before {event_id} instead")
        if any(e["action"] == "revert" and e["parameters"]["event"] == event_id for e in applied):
            return Refusal("already-reverted", f"{event_id} is already reverted")
        later = applied[ids.index(event_id) + 1:]
        clash = sorted(set(target["touches"]) & {t for e in later for t in e["touches"]})
        if clash:
            return Refusal("revert-conflict", f"{', '.join(clash)} changed after {event_id}")
        if target["action"] == "place":
            return {"action": "remove", "parameters": {"col": target["parameters"]["col"], "row": target["parameters"]["row"]}}
        if target["action"] in GROUND_VERBS:
            return inverse_ground_change(target["action"], target["parameters"])
        return {"action": "place", "parameters": dict(target["removed"])}

    def revert(self, event_id: str, branch: str, actor: str, timestamp: str):
        if branch not in self.heads:
            return Refusal("unknown-branch", f"no branch '{branch}'")
        plan = self.plan_revert(event_id, branch)
        if isinstance(plan, Refusal):
            return plan
        return self.commit(self.draft(branch, actor, "revert", {"event": event_id, "undo": plan}, timestamp))

    def recheck_governance(self, event: dict) -> str | None:
        """On replay, a governance event must be one the live methods would have produced."""
        action, parameters = event["action"], event["parameters"]
        if action == "endorse" and not self.branch_meta.get(event["branch"], {}).get("proposal"):
            return f"endorse on {event['branch']}, which is not a proposal"
        if action == "merge":
            plan = self.plan_merge(parameters["branch"], event["branch"])
            if isinstance(plan, Refusal) or plan != parameters["events"]:
                return f"merge of {parameters['branch']} is not the merge the conflict rule allows ({plan})"
        if action == "revert":
            plan = self.plan_revert(parameters["event"], event["branch"])
            if isinstance(plan, Refusal) or plan != parameters["undo"]:
                return f"revert of {parameters['event']} is not the revert the rules allow ({plan})"
        return None


def first_repeated_event_id(events: list[dict]) -> str | None:
    seen: set[str] = set()
    for event in events:
        if event["id"] in seen:
            return event["id"]
        seen.add(event["id"])
    return None


def inverse_ground_change(verb: str, parameters: dict) -> dict:
    inverse = "raise" if verb == "dig" else "dig"
    return {"action": inverse, "parameters": {"col": parameters["col"], "row": parameters["row"],
                                              GROUND_AMOUNT_FIELDS[inverse]: parameters[GROUND_AMOUNT_FIELDS[verb]]}}


# ---------------------------------------------------------------- outputs and the renderer contract


def convert_to_unit(value: float | None, unit: str) -> float | None:
    if value is None:
        return None
    return value / parse_unit(unit)[0]


def report_outputs(world: World, state: State) -> dict:
    evaluation = Evaluation(world, state)
    counts = {type_id: 0 for type_id in world.types}
    for instance_id in state.cells.values():
        counts[state.instances[instance_id]["type"]] += 1
    outputs = {
        "counts": counts,
        "areas": {type_id: count * world.tile_ft ** 2 for type_id, count in counts.items()},
        "equations": {k: convert_to_unit(evaluation.value_of(world.ast(v["expr"])), v["unit"]) for k, v in world.equations.items()},
        "stocks": {k: convert_to_unit(state.stocks[k], v["unit"]) for k, v in world.stocks.items()},
        "scoring": {k: convert_to_unit(evaluation.value_of(world.ast(v["expr"])), v["unit"]) for k, v in world.scoring.items()},
        "ticks": state.ticks,
    }
    if state.ground is not None:
        outputs["ground"] = {"version": state.ground_version, "min_mm": min(state.ground), "max_mm": max(state.ground)}
    return outputs


def render_props(world: World, state: State) -> dict:
    props = tilemap_props(world, state)
    props["units"] = "mm"
    props["instances"] = [{field: record[field] for field in RENDERED_INSTANCE_FIELDS}
                          for _, record in sorted(state.instance_layer.items())]
    if world.frame is not None:
        props["frame"] = dict(world.frame)
    if state.ground is not None:
        props.update(ground_props(world, state))
    return props


def ground_props(world: World, state: State) -> dict:
    base = world.ground["heights_mm"]
    return {
        "ground": {"unit": "mm", "datum": world.ground["datum"], "source": world.ground["source"],
                   "vertex_cols": world.cols + 1, "vertex_rows": world.rows + 1,
                   "heights_mm": list(state.ground), "version": state.ground_version},
        "ground_base": {"heights_mm": list(base), "cut_fill_mm": [now - seed for now, seed in zip(state.ground, base)]},
    }


def tilemap_props(world: World, state: State) -> dict:
    outputs = report_outputs(world, state)
    return {
        "cols": world.cols,
        "rows": world.rows,
        "shape": world.grid.get("shape", "square"),
        "view": world.grid.get("view", "iso"),
        "tile_area": world.tile_ft ** 2,
        "uses": [{"id": t["id"], "label": t["label"], "color": t["color"], "sprite": t["sprite"]} for t in world.types.values()],
        "cells": [{"col": c, "row": r, "use": state.instances[i]["type"]} for (c, r), i in sorted(state.cells.items())],
        "counts": outputs["counts"],
        "areas": outputs["areas"],
    }


def action_for_tap(state: State, col: int, row: int, brush: str | None) -> tuple[str, dict] | Refusal:
    occupant = state.instance_at(col, row)
    if occupant is None:
        if brush is None:
            return Refusal("no-brush", "nothing selected to place")
        return ("place", {"type": brush, "col": col, "row": row})
    if brush is None or occupant["type"] == brush:
        return ("remove", {"col": col, "row": row})
    return Refusal("occupied", f"tile {col},{row} holds {occupant['type']}; tap with its own brush to remove it first")


# ---------------------------------------------------------------- command line


def demo_path(world: World) -> Path:
    return HERE / "demos" / f"{world.id}.demo.json"


def run_script(world: World, steps: list[dict], log: Log | None = None) -> tuple[Log, list[str]]:
    log = log or Log(world)
    lines = []
    for index, step in enumerate(steps):
        timestamp = step.get("timestamp", f"2026-10-05T00:{index // 60:02d}:{index % 60:02d}Z")
        actor, verb = step.get("actor", "jim"), step["do"]
        if verb == "branch":
            result = log.branch(step["name"], step.get("from", "main"), actor, timestamp, step.get("proposal", False))
        elif verb == "merge":
            result = log.merge(step["branch"], step.get("into", "main"), actor, timestamp)
        elif verb == "revert":
            result = log.revert(step["event"], step.get("on", "main"), actor, timestamp)
        else:
            result = log.attempt(step.get("on", "main"), actor, verb, step.get("parameters", {}), timestamp)
        if isinstance(result, Refusal):
            lines.append(f"refused {verb} {json.dumps(step.get('parameters', {}), sort_keys=True)} by {result.rule}: {result.message}")
        else:
            lines.append(f"applied {result['id']} {verb} on {result['branch']}")
    return log, lines


def format_outputs(world: World, outputs: dict) -> list[str]:
    lines = []
    for type_id, count in outputs["counts"].items():
        lines.append(f"count {type_id} {count} tiles {outputs['areas'][type_id]:g} sq_ft")
    for section, declared in (("equation", world.equations), ("stock", world.stocks), ("score", world.scoring)):
        values = outputs[{"equation": "equations", "stock": "stocks", "score": "scoring"}[section]]
        for key, value in values.items():
            shown = "not-measured" if value is None else f"{value:.6g}"
            lines.append(f"{section} {key} {shown} {declared[key]['unit']}")
    lines.append(f"ticks {outputs['ticks']}")
    if "ground" in outputs:
        ground = outputs["ground"]
        lines.append(f"ground version {ground['version']} min {format_mm(ground['min_mm'])} mm max {format_mm(ground['max_mm'])} mm")
    return lines


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--world", required=True)
    parser.add_argument("--log", help="replay this event log before anything else")
    parser.add_argument("--demo", action="store_true", help="run demos/<world id>.demo.json")
    parser.add_argument("--ticks", type=int, default=0)
    parser.add_argument("--branch", default="main")
    parser.add_argument("--render", action="store_true", help="print the tilemap props the renderer draws")
    parser.add_argument("--save-log", help="write the resulting event log here")
    options = parser.parse_args(argv)

    world = load_world(options.world)
    log = Log.load(world, read_json(Path(options.log).read_text(encoding="utf-8"))) if options.log else Log(world)
    print(f"world {world.id}: {world.cols} x {world.rows} tiles of {world.tile_ft:g} ft")
    if options.demo:
        steps = json.loads(demo_path(world).read_text(encoding="utf-8"))["steps"]
        log, lines = run_script(world, steps, log)
        print("\n".join(lines))
    if options.ticks:
        result = log.attempt(options.branch, "clock", "tick", {"n": options.ticks}, "2026-10-05T23:59:59Z")
        print(f"refused tick by {result.rule}: {result.message}" if isinstance(result, Refusal) else f"applied {result['id']} tick")
    state = log.state_of(options.branch)
    print("\n".join(format_outputs(world, report_outputs(world, state))))
    if options.render:
        print(json.dumps(render_props(world, state), indent=2))
    if options.save_log:
        Path(options.save_log).write_text(json.dumps(log.dump(), indent=2), encoding="utf-8")
    return 0


if __name__ == "__main__":
    sys.exit(main())
