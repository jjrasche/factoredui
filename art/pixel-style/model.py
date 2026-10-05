import math
from dataclasses import dataclass, field

from iso import ground_shadow

FACINGS = ("SE", "SW", "NW", "NE")


def to_ground(facing, forward, lateral):
    if facing == "SE":
        return forward, lateral
    if facing == "SW":
        return -lateral, forward
    if facing == "NW":
        return -forward, -lateral
    return lateral, -forward


@dataclass
class Box:
    low: tuple
    high: tuple
    paint: tuple
    name: str = ""


@dataclass
class Disc:
    centre: tuple
    radius: float
    thickness: float
    paint: tuple
    hub: tuple = None


@dataclass
class Prism:
    base: list
    top: list
    paint: tuple


LIGHT = (-0.25, 0.55, 0.8)
VIEW = (1.0, 1.0, 1.0)
TONE_THRESHOLDS = ((0.7, 3), (0.45, 2), (0.15, 1))


@dataclass
class Faces:
    polygons: list
    centre: tuple
    ramp: list
    colour: tuple = None
    layer: int = 0
    bias: float = 0.0


def subtract(a, b):
    return (a[0] - b[0], a[1] - b[1], a[2] - b[2])


def cross(a, b):
    return (a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0])


def dot(a, b):
    return a[0] * b[0] + a[1] * b[1] + a[2] * b[2]


def unit(vector):
    length = math.sqrt(dot(vector, vector)) or 1.0
    return (vector[0] / length, vector[1] / length, vector[2] / length)


def face_tone(normal):
    brightness = 0.25 + 0.75 * max(0.0, dot(normal, unit(LIGHT)))
    for threshold, tone in TONE_THRESHOLDS:
        if brightness >= threshold:
            return tone
    return 0


def faces_in_ground(facing, faces):
    placed = []
    centre = to_ground(facing, faces.centre[0], faces.centre[1]) + (faces.centre[2],)
    for polygon in faces.polygons:
        points = [to_ground(facing, f, l) + (z,) for f, l, z in polygon]
        normal = unit(cross(subtract(points[1], points[0]), subtract(points[2], points[0])))
        if dot(normal, subtract(points[0], centre)) < 0:
            normal = (-normal[0], -normal[1], -normal[2])
        if dot(normal, VIEW) <= 0:
            continue
        depth = (faces.layer, sum(p[0] + p[1] for p in points) / len(points) + faces.bias, sum(p[2] for p in points) / len(points))
        colour = faces.colour if faces.colour else faces.ramp[face_tone(normal)]
        placed.append((depth, points, colour))
    return placed


@dataclass
class Model:
    parts: list = field(default_factory=list)
    footprint: tuple = (0.0, 0.0, 1.0, 1.0)
    height: float = 1.0


def slot(ramp, top=3, left=2, right=1):
    return (ramp[top], ramp[left], ramp[right])


def rotate_box(facing, box):
    (f0, l0, z0), (f1, l1, z1) = box.low, box.high
    corners = [to_ground(facing, f, l) for f in (f0, f1) for l in (l0, l1)]
    xs, ys = [c[0] for c in corners], [c[1] for c in corners]
    return Box((min(xs), min(ys), z0), (max(xs), max(ys), z1), box.paint, box.name)


def box_depth(box):
    (x0, y0, z0), (x1, y1, z1) = box.low, box.high
    return ((x0 + x1) / 2 + (y0 + y1) / 2, z0)


def draw_box(canvas, iso, origin, box):
    (x0, y0, z0), (x1, y1, z1) = box.low, box.high
    top, left, right = box.paint
    iso.polygon(canvas, origin, [(x0, y1, z0), (x1, y1, z0), (x1, y1, z1), (x0, y1, z1)], left)
    iso.polygon(canvas, origin, [(x1, y0, z0), (x1, y1, z0), (x1, y1, z1), (x1, y0, z1)], right)
    iso.polygon(canvas, origin, [(x0, y0, z1), (x1, y0, z1), (x1, y1, z1), (x0, y1, z1)], top)


def disc_points(facing, disc, lateral):
    forward, _, up = disc.centre
    points = []
    for step in range(16):
        angle = step * math.pi * 2 / 16
        f = forward + math.cos(angle) * disc.radius
        z = up + math.sin(angle) * disc.radius
        x, y = to_ground(facing, f, lateral)
        points.append((x, y, z))
    return points


def draw_disc(canvas, iso, origin, facing, disc):
    _, lateral, _ = disc.centre
    top, left, right = disc.paint
    outer = lateral + disc.thickness / 2
    inner = lateral - disc.thickness / 2
    near, far = (outer, inner)
    sx, sy = to_ground(facing, 0, 1)
    if sx + sy < 0:
        near, far = inner, outer
    iso.polygon(canvas, origin, disc_points(facing, disc, far), right)
    iso.polygon(canvas, origin, disc_points(facing, disc, near), left)
    if disc.hub:
        hub = Disc(disc.centre, disc.radius * 0.42, disc.thickness, (disc.hub, disc.hub, disc.hub))
        iso.polygon(canvas, origin, disc_points(facing, hub, near), disc.hub)


def disc_depth(facing, disc):
    forward, lateral, up = disc.centre
    x, y = to_ground(facing, forward, lateral)
    return (x + y, up - disc.radius)


def draw_prism(canvas, iso, origin, facing, prism):
    ground = [to_ground(facing, f, l) + (z,) for f, l, z in prism.base]
    raised = [to_ground(facing, f, l) + (z,) for f, l, z in prism.top]
    top, left, right = prism.paint
    for index in range(len(ground)):
        nxt = (index + 1) % len(ground)
        a, b = ground[index], ground[nxt]
        shade = left if (b[0] - a[0]) + (b[1] - a[1]) > 0 or a[1] > b[1] else right
        iso.polygon(canvas, origin, [a, b, raised[nxt], raised[index]], shade)
    iso.polygon(canvas, origin, raised, top)


def prism_depth(facing, prism):
    cx = sum(f for f, _, _ in prism.base) / len(prism.base)
    cl = sum(l for _, l, _ in prism.base) / len(prism.base)
    x, y = to_ground(facing, cx, cl)
    return (x + y, min(z for _, _, z in prism.base))


def draw_model(canvas, iso, origin, model, facing, shadow=True):
    if shadow:
        f0, l0, f1, l1 = model.footprint
        corners = [to_ground(facing, f, l) for f in (f0, f1) for l in (l0, l1)]
        xs, ys = [c[0] for c in corners], [c[1] for c in corners]
        ground_shadow(canvas, iso, origin, (min(xs), min(ys), max(xs), max(ys)), model.height)
    drawables = []
    for part in model.parts:
        if isinstance(part, Box):
            rotated = rotate_box(facing, part)
            drawables.append(((0,) + box_depth(rotated), lambda r=rotated: draw_box(canvas, iso, origin, r)))
        elif isinstance(part, Disc):
            drawables.append(((0,) + disc_depth(facing, part), lambda p=part: draw_disc(canvas, iso, origin, facing, p)))
        elif isinstance(part, Faces):
            for depth, points, colour in faces_in_ground(facing, part):
                drawables.append((depth, lambda pts=points, c=colour: iso.polygon(canvas, origin, pts, c)))
        else:
            drawables.append(((0,) + prism_depth(facing, part), lambda p=part: draw_prism(canvas, iso, origin, facing, p)))
    for _, painter in sorted(drawables, key=lambda item: item[0]):
        painter()
