import sys
from pathlib import Path

from PIL import Image

from canvas import scaled
from creatures import cow, person
from machines import TRACTOR_BODIES, tractor, van
from model import FACINGS
from render import render_model
from terrain import grass_tile

OUT = Path(sys.argv[1])


def row(images, gap, background):
    width = sum(image.width for image in images) + gap * (len(images) + 1)
    height = max(image.height for image in images) + gap * 2
    sheet = Image.new("RGBA", (width, height), background)
    x = gap
    for image in images:
        sheet.alpha_composite(image, (x, height - gap - image.height))
        x += image.width + gap
    return sheet


for width, factor in ((32, 3), (64, 2)):
    rows = []
    rows.append(row([render_model(person(frame), facing, width)[0].image for facing in FACINGS for frame in (0, 1)], 6, (62, 150, 46, 255)))
    rows.append(row([render_model(cow(frame), facing, width)[0].image for facing in FACINGS for frame in (0, 1)], 6, (62, 150, 46, 255)))
    for body in TRACTOR_BODIES:
        rows.append(row([render_model(tractor(body, state), facing, width)[0].image for facing in FACINGS for state in ("idle", "working")], 6, (62, 150, 46, 255)))
    rows.append(row([render_model(van(), facing, width)[0].image for facing in FACINGS], 6, (62, 150, 46, 255)))
    sheet = Image.new("RGBA", (max(r.width for r in rows), sum(r.height for r in rows)), (62, 150, 46, 255))
    y = 0
    for image in rows:
        sheet.alpha_composite(image, (0, y))
        y += image.height
    scaled(sheet, factor).save(OUT / f"models-{width}.png")
