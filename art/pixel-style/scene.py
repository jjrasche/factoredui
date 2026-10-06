import math

from buildings import commons, fence, hoop_house, shed
from canvas import Canvas, hashed
from creatures import cow, person
from machines import tractor
from render import render_model
from palette import VOID
from terrain import grass_tile, path_tile, shore_tile, water_tile
from trees import CLASSES, RADII

GRID = 12
HEADROOM = 3.4
PATH_TILES = {(c, 5) for c in range(0, 12)} | {(7, r) for r in range(1, 5)}
WATER_TILES = {(c, r) for c in range(9, 12) for r in range(0, 3)}
SHORE_TILES = {(8, 0), (8, 1), (8, 2), (9, 3), (10, 3), (11, 3)}


def screen_of(c, r, width, origin):
    return origin[0] + (c - r) * width / 2, origin[1] + (c + r) * width / 4


def paddock_fence(width):
    top = [(c + 1.0, 7.0, "SE") for c in (1, 3)]
    bottom = [(c + 1.0, 11.0, "SE") for c in (1, 3)]
    left = [(1.0, r + 1.0, "SW") for r in (7, 9)]
    right = [(5.0, r + 1.0, "SW") for r in (7, 9)]
    return [(c, r, "model", (lambda f=facing: render_model(fence(10.0), f, width))) for c, r, facing in top + bottom + left + right]


def placements(width):
    tree = lambda kind, size: (lambda: CLASSES[kind](width, RADII[size]))
    return paddock_fence(width) + [
        (5.6, 2.4, "model", lambda: render_model(hoop_house(), "SE", width)),
        (9.0, 9.0, "model", lambda: render_model(commons(), "SW", width)),
        (1.7, 5.9, "model", lambda: render_model(shed(), "SE", width)),
        (9.6, 4.2, "model", lambda: render_model(tractor("Red", "working"), "SW", width)),
        (4.0, 4.5, "model", lambda: render_model(person(1, 1), "SE", width)),
        (2.4, 8.4, "model", lambda: render_model(cow(0, grazing=True), "SE", width)),
        (3.8, 9.4, "model", lambda: render_model(cow(2), "SW", width)),
        (4.2, 8.0, "model", lambda: render_model(cow(1), "NW", width)),
        (0.8, 1.0, "tree", tree("Conifer", "M")),
        (3.0, 0.8, "tree", tree("Unknown", "S")),
        (1.2, 3.4, "tree", tree("Broadleaf", "M")),
        (11.0, 11.0, "tree", tree("Broadleaf", "S")),
        (6.6, 9.8, "tree", tree("Conifer", "S")),
        (7.2, 6.8, "tree", tree("Broadleaf", "S")),
    ]


def build_scene(width):
    field_w = GRID * width
    field_h = GRID * width // 2
    headroom = int(HEADROOM * width)
    scene = Canvas(field_w, field_h + headroom, VOID)
    origin = (field_w / 2, headroom)
    for r in range(GRID):
        for c in range(GRID):
            x, y = screen_of(c, r, width, origin)
            if (c, r) in WATER_TILES:
                tile = water_tile(width, (c + r) % 4)
            elif (c, r) in SHORE_TILES:
                tile = shore_tile(width, (c + r) % 2)
            elif (c, r) in PATH_TILES:
                tile = path_tile(width, hashed(c, r) % 3)
            else:
                tile = grass_tile(width, hashed(c, r) % 4)
            scene.paste(tile, x - width / 2, y)
    drawn = []
    for c, r, kind, make in placements(width):
        canvas, anchor = make()
        x, y = screen_of(c, r, width, origin)
        drawn.append((c + r, x - anchor[0], y - anchor[1], canvas))
    for _, x, y, canvas in sorted(drawn, key=lambda item: item[0]):
        scene.paste(canvas, x, y)
    return scene
