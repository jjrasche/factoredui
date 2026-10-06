from model import Box, Disc, Model, slot, to_ground
from palette import CHROME, DUST, GLASS, HUB_COLOUR, SMOKE, TRACTOR_BODIES, VAN_BODY, VAN_STRIPE

TIRE = (CHROME[3], CHROME[2], CHROME[0])
DARK = (CHROME[2], CHROME[1], CHROME[0])
GLASS_PAINT = (GLASS[2], GLASS[1], GLASS[0])
IMPLEMENT = (CHROME[5], CHROME[4], CHROME[3])
TINE = (CHROME[4], CHROME[3], CHROME[2])


def tractor(body_name, state):
    body = TRACTOR_BODIES[body_name]
    paint = slot(body)
    deep = slot(body, 2, 1, 0)
    parts = [
        Box((-4.6, -1.5, 1.4), (5.6, 1.5, 2.6), deep, "chassis"),
        Box((1.2, -1.3, 2.6), (5.8, 1.3, 4.9), paint, "hood"),
        Box((5.6, -1.0, 2.8), (5.9, 1.0, 4.3), DARK, "grille"),
        Box((4.5, 0.55, 4.9), (4.9, 0.95, 7.2), DARK, "exhaust"),
        Box((-4.8, -2.0, 2.6), (0.6, 2.0, 8.2), paint, "cab"),
        Box((0.6, -1.7, 4.0), (0.8, 1.7, 7.7), GLASS_PAINT, "windscreen"),
        Box((-4.5, -2.05, 4.2), (0.2, -1.95, 7.7), GLASS_PAINT, "window_a"),
        Box((-4.5, 1.95, 4.2), (0.2, 2.05, 7.7), GLASS_PAINT, "window_b"),
        Box((-5.2, -2.4, 8.2), (1.0, 2.4, 8.7), DARK, "roof"),
        Disc((-3.0, -2.7, 3.0), 3.0, 1.4, TIRE, HUB_COLOUR),
        Disc((-3.0, 2.7, 3.0), 3.0, 1.4, TIRE, HUB_COLOUR),
        Disc((4.0, -2.0, 1.8), 1.8, 1.0, TIRE, HUB_COLOUR),
        Disc((4.0, 2.0, 1.8), 1.8, 1.0, TIRE, HUB_COLOUR),
    ]
    if state in ("working", "hitched"):
        lift = 0.0 if state == "working" else 1.6
        parts += [
            Box((-9.4, -3.0, 0.6 + lift), (-5.2, 3.0, 1.8 + lift), IMPLEMENT, "frame"),
            Box((-5.2, -0.5, 1.0 + lift), (-4.8, 0.5, 1.6 + lift), DARK, "hitch"),
        ]
        parts += [Box((-9.4 - 0.5, lateral, 0.0 + lift), (-9.0, lateral + 0.5, 1.0 + lift), TINE, "tine") for lateral in (-2.6, -1.4, -0.2, 1.0, 2.2)]
    return Model(parts=parts, footprint=(-5.2 if state == "idle" else -9.4, -3.0, 6.0, 3.0), height=8.7)


def van(body_name="White"):
    body, stripe = VAN_BODY, VAN_STRIPE
    parts = [
        Box((-9.0, -3.0, 1.6), (6.5, 3.0, 4.2), slot(body), "lower"),
        Box((-9.0, -3.05, 3.0), (6.5, 3.05, 3.6), slot(stripe), "stripe"),
        Box((-9.0, -2.9, 4.2), (2.0, 2.9, 7.6), slot(body), "box"),
        Box((2.0, -2.9, 4.2), (6.0, 2.9, 6.0), slot(body, 2, 1, 0), "cab"),
        Box((2.1, -2.95, 4.4), (5.6, -2.85, 5.8), GLASS_PAINT, "window_a"),
        Box((2.1, 2.85, 4.4), (5.6, 2.95, 5.8), GLASS_PAINT, "window_b"),
        Box((5.9, -2.6, 4.3), (6.1, 2.6, 5.9), GLASS_PAINT, "windscreen"),
        Disc((-5.5, -3.0, 1.5), 1.5, 0.9, TIRE, HUB_COLOUR),
        Disc((-5.5, 3.0, 1.5), 1.5, 0.9, TIRE, HUB_COLOUR),
        Disc((3.8, -3.0, 1.5), 1.5, 0.9, TIRE, HUB_COLOUR),
        Disc((3.8, 3.0, 1.5), 1.5, 0.9, TIRE, HUB_COLOUR),
    ]
    return Model(parts=parts, footprint=(-9.0, -3.0, 6.5, 3.0), height=7.6)


def dither_puff(canvas, centre, radius, colour):
    cx, cy = centre
    for y in range(int(cy - radius), int(cy + radius) + 1):
        for x in range(int(cx - radius), int(cx + radius) + 1):
            if (x - cx) ** 2 + (y - cy) ** 2 <= radius * radius and (x + y) % 2 == 0:
                canvas.put(x, y, colour)


def tractor_effects(state):
    if state != "working":
        return None

    def draw(canvas, iso, origin, facing):
        ox, oy = origin
        smoke = SMOKE
        exhaust = to_ground(facing, 4.7, 0.75) + (7.4,)
        for step, radius in enumerate((1.2, 1.8, 2.5)):
            sx, sy = iso.point(exhaust[0], exhaust[1], exhaust[2] + step * 1.7)
            dither_puff(canvas, (ox + sx + step * 1.5, oy + sy), radius * iso.across / 3.2 * 2.4, smoke)
        dust = DUST
        rear = to_ground(facing, -10.4, 0.0) + (0.4,)
        for step in range(3):
            lateral = (step - 1) * 2.2
            gx, gy = to_ground(facing, -10.4 - step * 0.6, lateral)
            sx, sy = iso.point(gx, gy, 0.8 + step * 0.4)
            dither_puff(canvas, (ox + sx, oy + sy), (1.4 + step * 0.6) * iso.across / 3.2 * 2.2, dust)

    return draw
