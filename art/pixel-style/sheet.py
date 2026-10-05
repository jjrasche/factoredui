import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

from buildings import commons, fence, hoop_house, shed
from canvas import Canvas, hashed, scaled
from chrome import compose
from creatures import cow, person
from machines import TRACTOR_BODIES, tractor, tractor_effects, van
from model import FACINGS
from palette import BROADLEAF, CHROME, CONIFER, DIRT, GRASS, PATH, ROOF_RED, SAND, UNKNOWN, WATER, WOOD
from render import render_model
from scene import build_scene
from terrain import dirt_tile, grass_tile, path_tile, sand_tile, shore_tile, water_tile
from trees import CLASSES, RADII

OUT = Path(sys.argv[1])
CONFIGS = ((32, 3), (32, 4), (64, 2))
MARGIN = 14
GAP = 14
LABEL_FONT = "C:/Windows/Fonts/segoeui.ttf"
LABEL_BOLD = "C:/Windows/Fonts/segoeuib.ttf"


def grass_background(width, height):
    canvas = Canvas(width, height)
    for y in range(height):
        for x in range(width):
            roll = hashed(x, y, 5) % 100
            canvas.put(x, y, GRASS[2] if roll < 70 else GRASS[1] if roll < 86 else GRASS[3] if roll < 96 else GRASS[4])
    return canvas.image


class Section:
    def __init__(self, title, factor, background="grass"):
        self.title = title
        self.factor = factor
        self.background = background
        self.rows = []

    def row(self, heading, cells):
        self.rows.append((heading, cells))

    def render(self, path):
        factor = self.factor
        heading_h = 34
        label_h = 26
        row_sizes = []
        for heading, cells in self.rows:
            cell_h = max(image.height for image, _ in cells) * factor
            cell_w = sum(image.width * factor + GAP * factor for image, _ in cells) + MARGIN * factor
            row_sizes.append((heading_h + cell_h + label_h + 10, cell_w, cell_h))
        width = max(size[1] for size in row_sizes) + MARGIN
        height = sum(size[0] for size in row_sizes) + 56 + MARGIN
        sheet = Image.new("RGBA", (width, height), (20, 24, 30, 255))
        draw = ImageDraw.Draw(sheet)
        title_font = ImageFont.truetype(LABEL_BOLD, 28)
        head_font = ImageFont.truetype(LABEL_BOLD, 20)
        label_font = ImageFont.truetype(LABEL_FONT, 17)
        draw.text((MARGIN, 12), self.title, font=title_font, fill=(232, 237, 244))
        y = 56
        for (heading, cells), (row_h, row_w, cell_h) in zip(self.rows, row_sizes):
            draw.text((MARGIN, y), heading, font=head_font, fill=(232, 184, 58))
            band_top = y + heading_h
            band = grass_background(width - MARGIN * 2, cell_h + 8) if self.background == "grass" else Image.new("RGBA", (width - MARGIN * 2, cell_h + 8), (32, 38, 48, 255))
            sheet.alpha_composite(scaled(band, 1), (MARGIN, band_top))
            x = MARGIN + MARGIN * factor // 2
            for image, label in cells:
                big = scaled(image, factor)
                sheet.alpha_composite(big, (x, band_top + 4 + cell_h - big.height))
                draw.text((x, band_top + cell_h + 12), label, font=label_font, fill=(214, 220, 228))
                x += big.width + GAP * factor
            y += row_h
        sheet.save(path)


def tile_cells(width):
    cells = [(grass_tile(width, 0).image, "grass A"), (grass_tile(width, 1).image, "grass B"), (path_tile(width).image, "path"), (sand_tile(width).image, "sand"), (dirt_tile(width).image, "dirt")]
    return cells


def water_cells(width):
    return [(water_tile(width, frame).image, f"water {frame + 1}") for frame in range(4)] + [(shore_tile(width, frame).image, f"shore edge {frame + 1}") for frame in range(2)]


def patch(width):
    columns, rows = 9, 7
    canvas = Canvas(columns * width, rows * width // 2 + 4, (20, 24, 30, 255))
    for r in range(rows):
        for c in range(columns):
            if not (0 <= c - r + 3 < 9):
                continue
            x = (c - r) * width / 2 + columns * width / 2 - width / 2 + width * 0.0
            y = (c + r) * width / 4
            roll = hashed(c, r) % 10
            tile = path_tile(width, roll % 3) if c == 4 else grass_tile(width, roll % 4) if roll < 8 else sand_tile(width)
            canvas.paste(tile, x, y)
    return canvas.image


def sized(model_image):
    return model_image.image


def feet_label(name, radius):
    return f"{name}, crown radius {radius:g} ft"


def sections(width, factor):
    ground = Section("Ground and water", factor)
    ground.row("Terrain textures (no grid lines)", tile_cells(width))
    ground.row("Water frames and shore edge", water_cells(width))
    ground.row("Tiling patch", [(patch(width), "grass, path, sand mixed")])
    trees = Section("Trees: three classes, four crown sizes", factor)
    for name, make in CLASSES.items():
        trees.row(name if name != "Unknown" else "Unknown (no species in the data)", [(make(width, radius)[0].image, f"{size}: radius {radius:g} ft") for size, radius in RADII.items()])
    things = Section("Buildings and machines", factor)
    for title, make in (("Hoop house", hoop_house), ("Commons", commons), ("Shed", shed), ("Fence", lambda: fence(10.0)), ("Van (about 15 ft)", van)):
        things.row(title, [(render_model(make(), facing, width)[0].image, facing) for facing in FACINGS])
    for body in TRACTOR_BODIES:
        for state in ("idle", "working"):
            things.row(f"Tractor, {body.lower()} body, {state}", [(render_model(tractor(body, state), facing, width, extras=tractor_effects(state))[0].image, facing) for facing in FACINGS])
    living = Section("People and animals: walk cycles in four facings", factor)
    living.row("Person, palette-swapped shirts", [(render_model(person(0, shirt), "SE", width)[0].image, name) for shirt, name in enumerate(("red", "blue", "yellow"))])
    for facing in FACINGS:
        living.row(f"Person walking {facing}", [(render_model(person(frame, 0), facing, width)[0].image, f"frame {frame + 1}") for frame in range(4)])
    for facing in FACINGS:
        living.row(f"Cow walking {facing}", [(render_model(cow(frame), facing, width)[0].image, f"frame {frame + 1}") for frame in range(4)] + [(render_model(cow(0, grazing=True), facing, width)[0].image, "grazing")])
    return [("A-ground-and-water", ground), ("B-trees", trees), ("C-buildings-and-machines", things), ("D-people-and-animals", living)]


def palette_sheet(path, factor):
    groups = [("Grass", GRASS), ("Path", PATH), ("Sand", SAND), ("Dirt", DIRT), ("Water", WATER), ("Broadleaf", BROADLEAF), ("Conifer", CONIFER), ("Unknown crown", UNKNOWN), ("Roof red", ROOF_RED), ("Wood", WOOD), ("UI chrome", CHROME)]
    swatch = 74
    sheet = Image.new("RGBA", (MARGIN * 2 + 6 * swatch + 190, 70 + len(groups) * (swatch + 12)), (20, 24, 30, 255))
    draw = ImageDraw.Draw(sheet)
    draw.text((MARGIN, 14), "Palette (every sprite and texture uses only these ramps)", font=ImageFont.truetype(LABEL_BOLD, 26), fill=(232, 237, 244))
    for row, (name, ramp_) in enumerate(groups):
        y = 64 + row * (swatch + 12)
        draw.text((MARGIN, y + swatch // 2 - 12), name, font=ImageFont.truetype(LABEL_BOLD, 20), fill=(232, 184, 58))
        for index, color in enumerate(ramp_):
            x = 190 + index * swatch
            draw.rectangle((x, y, x + swatch - 4, y + swatch - 4), fill=color)
            draw.text((x + 6, y + swatch - 26), "#%02X%02X%02X" % color[:3], font=ImageFont.truetype(LABEL_FONT, 12), fill=(0, 0, 0) if sum(color[:3]) > 380 else (255, 255, 255))
    sheet.save(path)


if __name__ == "__main__":
    for width, factor in CONFIGS:
        tag = f"{width}px-tiles-shown-at-{factor}x"
        for name, section in sections(width, factor):
            section.render(OUT / f"{name}--{tag}.png")
        unit = 1 if width == 32 else 2
        scaled(compose(build_scene(width), unit).image, factor).save(OUT / f"E-chrome-and-playfield--{tag}.png")
    palette_sheet(OUT / "F-palette.png", 1)
