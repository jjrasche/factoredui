import sys
from pathlib import Path

from canvas import scaled
from chrome import compose
from scene import build_scene

OUT = Path(sys.argv[1])

for width, unit, factor in ((32, 1, 3), (64, 2, 2)):
    scene = build_scene(width)
    scaled(compose(scene, unit).image, factor).save(OUT / f"chrome-{width}.png")
