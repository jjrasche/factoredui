import math

from canvas import Canvas, hashed
from iso import SHADOW, Iso
from palette import BROADLEAF, CONIFER, TRUNK, UNKNOWN

RADII = {"S": 4.0, "M": 8.0, "L": 12.0, "XL": 16.0}
PAD = 6


def new_canvas(iso, radius, height_feet):
    crown = radius * iso.sphere
    width = math.ceil(crown * 3.4) + PAD * 2
    height = math.ceil(height_feet * iso.up + crown * 0.9) + PAD * 2
    canvas = Canvas(width, height)
    return canvas, (width / 2 - crown * 0.35, height - PAD - crown * 0.75)


def drop_shadow(canvas, foot, crown, reach):
    canvas.ellipse((foot[0] + reach, foot[1] + reach * 0.18), crown * 1.05, crown * 0.52, SHADOW)


def trunk(canvas, foot, crown, rise):
    thickness = max(2, round(crown * 0.16))
    canvas.rect(foot[0] - thickness / 2, foot[1] - rise, foot[0] + thickness / 2, foot[1], TRUNK[1])
    canvas.rect(foot[0] + thickness / 2 - 1, foot[1] - rise, foot[0] + thickness / 2, foot[1], TRUNK[0])
    canvas.rect(foot[0] - thickness / 2, foot[1] - rise, foot[0] - thickness / 2 + 1, foot[1], TRUNK[2])


def lobe(canvas, centre, radius, palette, seed):
    cx, cy = centre
    canvas.ellipse((cx + radius * 0.1, cy + radius * 0.12), radius, radius * 0.94, palette[0])
    canvas.ellipse((cx, cy), radius * 0.95, radius * 0.88, palette[1])
    canvas.ellipse((cx - radius * 0.1, cy - radius * 0.12), radius * 0.8, radius * 0.72, palette[2])
    canvas.ellipse((cx - radius * 0.28, cy - radius * 0.32), radius * 0.46, radius * 0.4, palette[3])
    for index in range(max(4, int(radius * radius / 6))):
        dx = hashed(index, 1, seed) % 200 / 100.0 - 1.0
        dy = hashed(index, 2, seed) % 200 / 100.0 - 1.0
        if dx * dx + dy * dy < 0.8:
            canvas.put(cx + dx * radius, cy + dy * radius, palette[4] if dx + dy < -0.3 else palette[1])


def broadleaf(width, radius):
    iso = Iso(width)
    crown = radius * iso.sphere
    canvas, foot = new_canvas(iso, radius, radius * 3.1)
    drop_shadow(canvas, foot, crown, crown * 0.6)
    trunk(canvas, foot, crown, radius * 1.4 * iso.up)
    centre = (foot[0], foot[1] - radius * 2.0 * iso.up)
    for index, (dx, dy, scale) in enumerate([(0.0, 0.0, 0.86), (-0.55, 0.2, 0.6), (0.55, 0.26, 0.58), (-0.12, -0.5, 0.58), (0.3, 0.55, 0.46)]):
        lobe(canvas, (centre[0] + dx * crown * 0.7, centre[1] + dy * crown * 0.6), crown * scale, BROADLEAF, index + 3)
    return canvas, foot


def conifer(width, radius):
    iso = Iso(width)
    crown = radius * iso.sphere
    canvas, foot = new_canvas(iso, radius, radius * 4.4)
    drop_shadow(canvas, foot, crown * 0.9, crown * 0.9)
    trunk(canvas, foot, crown, radius * 0.7 * iso.up)
    tiers = 3 if radius <= 8 else 5
    full = radius * 4.2 * iso.up
    tier_height = full / (tiers * 0.62 + 0.38)
    for tier in range(tiers):
        scale = 1.0 - tier * (0.64 / tiers)
        half = crown * 0.95 * scale
        bottom = foot[1] - radius * 0.5 * iso.up - tier * tier_height * 0.62
        top = bottom - tier_height
        canvas.polygon([(foot[0], top), (foot[0] - half, bottom), (foot[0], bottom + half * 0.3)], CONIFER[3])
        canvas.polygon([(foot[0], top), (foot[0] + half, bottom), (foot[0], bottom + half * 0.3)], CONIFER[1])
        canvas.polygon([(foot[0] - half, bottom), (foot[0], bottom + half * 0.3), (foot[0] + half, bottom), (foot[0], bottom + half * 0.12)], CONIFER[0])
        canvas.line((foot[0], top), (foot[0] - half * 0.55, top + (bottom - top) * 0.6), CONIFER[4])
    return canvas, foot


def unknown(width, radius):
    iso = Iso(width)
    crown = radius * iso.sphere
    canvas, foot = new_canvas(iso, radius, radius * 3.0)
    drop_shadow(canvas, foot, crown, crown * 0.6)
    trunk(canvas, foot, crown, radius * 1.3 * iso.up)
    centre = (foot[0], foot[1] - radius * 1.9 * iso.up)
    lobe(canvas, centre, crown * 0.88, UNKNOWN, 17)
    lobe(canvas, (centre[0] - crown * 0.55, centre[1] + crown * 0.22), crown * 0.52, UNKNOWN, 19)
    lobe(canvas, (centre[0] + crown * 0.6, centre[1] + crown * 0.28), crown * 0.46, UNKNOWN, 23)
    for index in range(max(5, int(radius * 2))):
        dx = (hashed(index, 5, 29) % 200 / 100.0 - 1.0) * crown * 0.9
        dy = (hashed(index, 6, 29) % 200 / 100.0 - 1.0) * crown * 0.7
        canvas.put(centre[0] + dx, centre[1] + dy, UNKNOWN[4])
    return canvas, foot


CLASSES = {"Broadleaf": broadleaf, "Conifer": conifer, "Unknown": unknown}
