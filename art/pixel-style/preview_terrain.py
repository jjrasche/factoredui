import sys
from pathlib import Path

from PIL import Image

from canvas import scaled
from terrain import dirt_tile, grass_tile, path_tile, sand_tile, shore_tile, water_tile

OUT = Path(sys.argv[1])

for width, factor in ((32, 6), (64, 3)):
    tiles = [grass_tile(width, 0), grass_tile(width, 1), path_tile(width), sand_tile(width), dirt_tile(width), water_tile(width, 0), water_tile(width, 2), shore_tile(width, 0)]
    step = width + 4
    sheet = Image.new("RGBA", (step * len(tiles), width // 2 + 8), (40, 40, 48, 255))
    for index, tile in enumerate(tiles):
        sheet.alpha_composite(tile.image, (index * step + 2, 4))
    scaled(sheet, factor).save(OUT / f"terrain-{width}.png")
