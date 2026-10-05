import sys
from pathlib import Path

from PIL import Image

from buildings import commons, fence, hoop_house, shed
from canvas import scaled
from model import FACINGS
from render import render_model

OUT = Path(sys.argv[1])
GRASS = (62, 150, 46, 255)

for width, factor in ((32, 3), (64, 2)):
    makers = [hoop_house, commons, shed, fence]
    rows = []
    for make in makers:
        images = [render_model(make(), facing, width)[0].image for facing in FACINGS]
        total = sum(image.width for image in images) + 10 * (len(images) + 1)
        height = max(image.height for image in images) + 20
        row = Image.new("RGBA", (total, height), GRASS)
        x = 10
        for image in images:
            row.alpha_composite(image, (x, height - 10 - image.height))
            x += image.width + 10
        rows.append(row)
    sheet = Image.new("RGBA", (max(r.width for r in rows), sum(r.height for r in rows)), GRASS)
    y = 0
    for image in rows:
        sheet.alpha_composite(image, (0, y))
        y += image.height
    scaled(sheet, factor).save(OUT / f"buildings-{width}.png")
