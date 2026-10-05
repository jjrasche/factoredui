import sys
from pathlib import Path

from PIL import Image

from canvas import scaled
from trees import CLASSES, RADII

OUT = Path(sys.argv[1])
GRASS = (62, 150, 46, 255)

for width, factor in ((32, 2), (64, 1)):
    rows = []
    for make in CLASSES.values():
        images = [make(width, radius)[0].image for radius in RADII.values()]
        total = sum(image.width for image in images) + 12 * (len(images) + 1)
        height = max(image.height for image in images) + 24
        row = Image.new("RGBA", (total, height), GRASS)
        x = 12
        for image in images:
            row.alpha_composite(image, (x, height - 12 - image.height))
            x += image.width + 12
        rows.append(row)
    sheet = Image.new("RGBA", (max(r.width for r in rows), sum(r.height for r in rows)), GRASS)
    y = 0
    for image in rows:
        sheet.alpha_composite(image, (0, y))
        y += image.height
    scaled(sheet, factor).save(OUT / f"trees-{width}.png")
