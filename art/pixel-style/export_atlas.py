import base64
import json
import sys
from dataclasses import dataclass, field
from pathlib import Path

from PIL import Image

from buildings import commons, fence_segment, hoop_house, shed
from canvas import Canvas, hashed
from creatures import cow, person
from machines import tractor, tractor_effects, van
from model import FACINGS
from palette import DIRT, GRASS, PATH, SAND, SHIRT_RAMPS, TRACTOR_BODIES, TRANSPARENT, WATER
from render import render_model
from terrain import dirt_tile, grass_tile, path_tile, sand_tile, water_tile
from trees import CLASSES, RADII

HERE = Path(__file__).resolve().parent
REPO = HERE.parents[1]
ATLAS_DIR = HERE / "atlas"
KOTLIN_OUT = REPO / "packages/kotlin-compose-schema/src/commonMain/kotlin/ai/factoredui/compose/pixel/PixelAtlasData.kt"

ART_WIDTHS = (32, 64)
ART_TILE_FEET = 5.0
SHEET_SIDE = 2048
GUTTER = 1
PATTERN_CELLS = 4
WATER_CELLS = 2
WATER_FRAMES = 4
KOTLIN_CHUNK = 60000

HOOP_SIZES = ((24.0, 12.0), (36.0, 18.0), (48.0, 24.0), (60.0, 30.0))
COMMONS_SIZES = ((20.0, 16.0), (30.0, 24.0), (40.0, 32.0), (50.0, 40.0))
SHED_SIZES = ((10.0, 8.0), (15.0, 12.0), (20.0, 16.0))
COW_POSES = ((0, False), (1, False), (2, False), (3, False), (0, True))
TRACTOR_STATES = ("idle", "working")
BASE_TRACTOR = "Red"


@dataclass
class Sprite:
    kind: str
    cls: str
    image: Image.Image
    anchor: tuple
    size: str = ""
    facing: str = ""
    frame: int = 0
    footprint_ft: tuple = (0.0, 0.0)
    height_ft: float = 0.0
    swap: str = ""
    placed: dict = field(default_factory=dict)

    @property
    def name(self):
        parts = [self.cls, self.size, self.facing, str(self.frame) if self.frame or self.kind == "pattern" else ""]
        return "/".join(part for part in parts if part)


def wrapped_pattern(width, cells, tile_of, base):
    period_w, period_h = cells * width, cells * width // 2
    canvas = Canvas(period_w * 3, period_h * 3, base)
    for r in range(-cells * 2, cells * 4):
        for c in range(-cells * 2, cells * 4):
            x = (c - r) * width / 2 - width / 2 + period_w
            y = (c + r) * width / 4 + period_h
            if -width <= x < period_w * 3 and -width <= y < period_h * 3:
                canvas.paste(tile_of(c, r), x, y)
    return canvas.image.crop((period_w, period_h, period_w * 2, period_h * 2))


def lattice_key(c, r, cells):
    return (c - r) % (cells * 2), (c + r) % (cells * 2)


def textured_pattern(width, make, variants, seed, base):
    tiles = [make(width, variant) for variant in range(variants)]

    def tile_of(c, r):
        u, v = lattice_key(c, r, PATTERN_CELLS)
        return tiles[hashed(u, v, seed) % variants]

    return wrapped_pattern(width, PATTERN_CELLS, tile_of, base)


def water_pattern(width, frame):
    tiles = [water_tile(width, step) for step in range(WATER_FRAMES)]
    return wrapped_pattern(width, WATER_CELLS, lambda c, r: tiles[(frame + c + r) % WATER_FRAMES], WATER[2])


def ground_sprites(width):
    textures = (("grass", grass_tile, 4, GRASS), ("path", path_tile, 3, PATH), ("sand", lambda w, v: sand_tile(w, v), 2, SAND), ("dirt", dirt_tile, 3, DIRT))
    sprites = [Sprite("pattern", f"ground-{name}", textured_pattern(width, make, variants, index + 1, ramp[2]), (0, 0)) for index, (name, make, variants, ramp) in enumerate(textures)]
    sprites += [Sprite("pattern", "ground-water", water_pattern(width, frame), (0, 0), frame=frame) for frame in range(WATER_FRAMES)]
    return sprites


def model_sprite(cls, model, width, facing, size="", frame=0, extras=None, swap=""):
    canvas, origin = render_model(model, facing, width, extras=extras)
    f0, l0, f1, l1 = model.footprint
    return Sprite("model", cls, canvas.image, (round(origin[0]), round(origin[1])), size, facing, frame, (f1 - f0, l1 - l0), model.height, swap)


def size_label(forward, lateral):
    return f"{forward:g}x{lateral:g}"


def building_sprites(width):
    sprites = []
    for cls, make, sizes in (("hoop_house", hoop_house, HOOP_SIZES), ("commons", commons, COMMONS_SIZES), ("shed", shed, SHED_SIZES)):
        for forward, lateral in sizes:
            sprites += [model_sprite(cls, make(forward, lateral), width, facing, size_label(forward, lateral)) for facing in FACINGS]
    sprites += [model_sprite("fence", fence_segment(ART_TILE_FEET), width, facing) for facing in FACINGS]
    sprites += [model_sprite("van", van(), width, facing) for facing in FACINGS]
    return sprites


def living_sprites(width):
    sprites = []
    for state in TRACTOR_STATES:
        sprites += [model_sprite("tractor", tractor(BASE_TRACTOR, state), width, facing, state, extras=tractor_effects(state), swap="tractor") for facing in FACINGS]
    for frame in range(4):
        sprites += [model_sprite("person", person(frame, 0), width, facing, frame=frame, swap="shirt") for facing in FACINGS]
    for frame, grazing in COW_POSES:
        size = "grazing" if grazing else "walking"
        sprites += [model_sprite("cow", cow(frame, grazing=grazing), width, facing, size, frame) for facing in FACINGS]
    return sprites


def tree_sprites(width):
    sprites = []
    for cls, make in CLASSES.items():
        for size, radius in RADII.items():
            canvas, foot = make(width, radius)
            sprites.append(Sprite("tree", f"tree-{cls.lower()}", canvas.image, (round(foot[0]), round(foot[1])), size, footprint_ft=(radius * 2, radius * 2), height_ft=radius))
    return sprites


def sprites_at(width):
    return ground_sprites(width) + building_sprites(width) + living_sprites(width) + tree_sprites(width)


def pack(sprites):
    sheets = []
    x = y = shelf = 0
    for sprite in sorted(sprites, key=lambda item: (-item.image.height, item.name)):
        w, h = sprite.image.width, sprite.image.height
        if x + w > SHEET_SIDE:
            x, y, shelf = 0, y + shelf + GUTTER, 0
        if not sheets or y + h > SHEET_SIDE:
            sheets.append([])
            x = y = shelf = 0
        sprite.placed = {"sheet": len(sheets) - 1, "x": x, "y": y}
        sheets[-1].append(sprite)
        x += w + GUTTER
        shelf = max(shelf, h)
    return [compose_sheet(members) for members in sheets]


def compose_sheet(members):
    width = max(s.placed["x"] + s.image.width for s in members)
    height = max(s.placed["y"] + s.image.height for s in members)
    sheet = Image.new("RGBA", (width, height), TRANSPARENT)
    for sprite in members:
        sheet.alpha_composite(sprite.image, (sprite.placed["x"], sprite.placed["y"]))
    return sheet


def manifest_entry(sprite, art_width):
    return {
        "name": sprite.name,
        "kind": sprite.kind,
        "class": sprite.cls,
        "size": sprite.size,
        "facing": sprite.facing,
        "frame": sprite.frame,
        "sheet": sprite.placed["sheet"],
        "x": sprite.placed["x"],
        "y": sprite.placed["y"],
        "width": sprite.image.width,
        "height": sprite.image.height,
        "anchor_x": sprite.anchor[0],
        "anchor_y": sprite.anchor[1],
        "art_tile_width": art_width,
        "footprint_ft": [sprite.footprint_ft[0], sprite.footprint_ft[1]],
        "height_ft": sprite.height_ft,
        "swap": sprite.swap,
    }


def hex_of(colour):
    return "#%02X%02X%02X%02X" % (colour[3], colour[0], colour[1], colour[2])


def swap_table():
    return {
        "tractor": {"base": BASE_TRACTOR, "ramps": {name: [hex_of(colour) for colour in ramp] for name, ramp in TRACTOR_BODIES.items()}},
        "shirt": {"base": "0", "ramps": {str(index): [hex_of(colour) for colour in ramp] for index, ramp in enumerate(SHIRT_RAMPS)}},
    }


def build_atlas():
    scales = {}
    sheets = {}
    for width in ART_WIDTHS:
        sprites = sprites_at(width)
        sheets[width] = pack(sprites)
        scales[str(width)] = {
            "art_tile_width": width,
            "art_tile_feet": ART_TILE_FEET,
            "sheets": [{"width": sheet.width, "height": sheet.height} for sheet in sheets[width]],
            "sprites": [manifest_entry(sprite, width) for sprite in sorted(sprites, key=lambda item: item.name)],
        }
    return {"version": 1, "scales": scales, "swaps": swap_table()}, sheets


def indexed(sheet):
    palette = []
    lookup = {}
    indices = bytearray()
    raw = sheet.tobytes()
    for offset in range(0, len(raw), 4):
        pixel = tuple(raw[offset:offset + 4])
        colour = pixel if pixel[3] else TRANSPARENT
        if colour not in lookup:
            lookup[colour] = len(palette)
            palette.append(colour)
        indices.append(lookup[colour])
    if len(palette) > 256:
        raise ValueError(f"sheet holds {len(palette)} colours, more than one byte indexes")
    return palette, bytes(indices)


def pack_bits(data):
    out = bytearray()
    index = 0
    while index < len(data):
        run = 1
        while index + run < len(data) and run < 128 and data[index + run] == data[index]:
            run += 1
        if run >= 3:
            out += bytes((256 - run + 1, data[index]))
            index += run
            continue
        start = index
        while index < len(data) and index - start < 128:
            if index + 2 < len(data) and data[index] == data[index + 1] == data[index + 2]:
                break
            index += 1
        out.append(index - start - 1)
        out += data[start:index]
    return bytes(out)


def unpack_bits(data):
    out = bytearray()
    index = 0
    while index < len(data):
        header = data[index]
        if header < 128:
            out += data[index + 1:index + 2 + header]
            index += 2 + header
        else:
            out += bytes((data[index + 1],)) * (257 - header)
            index += 2
    return bytes(out)


def argb_of(colour):
    value = (colour[3] << 24) | (colour[0] << 16) | (colour[1] << 8) | colour[2]
    return value - (1 << 32) if value >= 1 << 31 else value


def kotlin_strings(text):
    chunks = [text[start:start + KOTLIN_CHUNK] for start in range(0, len(text), KOTLIN_CHUNK)] or [""]
    return "listOf(\n" + "".join(f'        "{chunk}",\n' for chunk in chunks) + "    )"


def kotlin_sheet(sheet):
    palette, indices = indexed(sheet)
    encoded = base64.b64encode(pack_bits(indices)).decode("ascii")
    colours = ", ".join(str(argb_of(colour)) for colour in palette)
    return f"EncodedSheet(\n    width = {sheet.width},\n    height = {sheet.height},\n    palette = intArrayOf({colours}),\n    packedIndices = {kotlin_strings(encoded)},\n)"


def kotlin_source(manifest, sheets):
    manifest_text = base64.b64encode(json.dumps(manifest, separators=(",", ":")).encode("utf-8")).decode("ascii")
    lines = ["package ai.factoredui.compose.pixel", "", f"internal val PIXEL_MANIFEST_BASE64: List<String> = {kotlin_strings(manifest_text)}", ""]
    for width, members in sheets.items():
        body = ",\n".join(kotlin_sheet(sheet) for sheet in members)
        lines += [f"internal val PIXEL_SHEETS_{width}: List<EncodedSheet> = listOf(\n{body},\n)", ""]
    return "\n".join(lines)


def write_atlas(manifest, sheets, atlas_dir=ATLAS_DIR, kotlin_out=KOTLIN_OUT):
    atlas_dir.mkdir(parents=True, exist_ok=True)
    for width, members in sheets.items():
        for index, sheet in enumerate(members):
            sheet.save(atlas_dir / f"pixel-atlas-{width}-{index}.png")
    (atlas_dir / "pixel-atlas.json").write_text(json.dumps(manifest, indent=1) + "\n", encoding="utf-8")
    kotlin_out.parent.mkdir(parents=True, exist_ok=True)
    kotlin_out.write_text(kotlin_source(manifest, sheets), encoding="utf-8")


if __name__ == "__main__":
    built_manifest, built_sheets = build_atlas()
    write_atlas(built_manifest, built_sheets)
    for scale, members in built_sheets.items():
        print(scale, [sheet.size for sheet in members], len(built_manifest["scales"][str(scale)]["sprites"]), "sprites")
    print(KOTLIN_OUT, KOTLIN_OUT.stat().st_size, "bytes", file=sys.stderr)
