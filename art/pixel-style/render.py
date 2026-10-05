import math

from canvas import Canvas
from iso import Iso
from model import Box, Disc, Faces, draw_model, rotate_box, to_ground

PAD = 4


def part_corners(facing, part):
    if isinstance(part, Box):
        box = rotate_box(facing, part)
        (x0, y0, z0), (x1, y1, z1) = box.low, box.high
        return [(x, y, z) for x in (x0, x1) for y in (y0, y1) for z in (z0, z1)]
    if isinstance(part, Disc):
        forward, lateral, up = part.centre
        points = []
        for side in (-part.thickness / 2, part.thickness / 2):
            for step in range(16):
                angle = step * math.pi * 2 / 16
                x, y = to_ground(facing, forward + math.cos(angle) * part.radius, lateral + side)
                points.append((x, y, up + math.sin(angle) * part.radius))
        return points
    if isinstance(part, Faces):
        return [to_ground(facing, f, l) + (z,) for polygon in part.polygons for f, l, z in polygon]
    return [to_ground(facing, f, l) + (z,) for f, l, z in part.base + part.top]


def model_bounds(iso, model, facing):
    points = []
    for part in model.parts:
        points += [iso.point(*corner) for corner in part_corners(facing, part)]
    f0, l0, f1, l1 = model.footprint
    for f in (f0, f1):
        for l in (l0, l1):
            x, y = to_ground(facing, f, l)
            sx, sy = iso.point(x, y)
            points.append((sx + model.height * iso.up * 0.55, sy + model.height * iso.up * 0.22))
            points.append((sx, sy))
    xs, ys = [p[0] for p in points], [p[1] for p in points]
    return min(xs), min(ys), max(xs), max(ys)


def render_model(model, facing, width, tile_feet=5.0, extras=None):
    iso = Iso(width, tile_feet)
    left, top, right, bottom = model_bounds(iso, model, facing)
    pad = PAD * 5 if extras else PAD
    canvas = Canvas(math.ceil(right - left) + pad * 2, math.ceil(bottom - top) + pad * 2)
    origin = (pad - left, pad - top)
    draw_model(canvas, iso, origin, model, facing)
    if extras:
        extras(canvas, iso, origin, facing)
    return canvas, origin
