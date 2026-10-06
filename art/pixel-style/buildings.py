import math

from model import Faces, Model
from palette import DOOR, FRAME, PLASTIC, ROOF_GREY, ROOF_RED, WALL_CREAM, WINDOW, WOOD



def block(low, high, ramp_, colour=None, layer=0):
    (f0, l0, z0), (f1, l1, z1) = low, high
    corners = {(a, b, c): (f0 if a == 0 else f1, l0 if b == 0 else l1, z0 if c == 0 else z1) for a in (0, 1) for b in (0, 1) for c in (0, 1)}
    faces = [(0, 0, 0), (1, 0, 0), (1, 1, 0), (0, 1, 0)], [(0, 0, 1), (1, 0, 1), (1, 1, 1), (0, 1, 1)]
    sides = [
        [(0, 0, 0), (1, 0, 0), (1, 0, 1), (0, 0, 1)],
        [(0, 1, 0), (1, 1, 0), (1, 1, 1), (0, 1, 1)],
        [(0, 0, 0), (0, 1, 0), (0, 1, 1), (0, 0, 1)],
        [(1, 0, 0), (1, 1, 0), (1, 1, 1), (1, 0, 1)],
    ]
    polygons = [[corners[key] for key in face] for face in list(faces) + sides]
    return Faces(polygons, ((f0 + f1) / 2, (l0 + l1) / 2, (z0 + z1) / 2), ramp_, colour, layer)


def flat_quad(points, colour, centre, layer=0):
    return Faces([points], centre, [colour] * 4, colour, layer)


HOOP_RISE = 7.0 / 12.0
RIB_SPACING = 4


def hoop_house(length_ft=24.0, width_ft=12.0):
    length, half, height = length_ft / 2, width_ft / 2, width_ft * HOOP_RISE
    steps = 12
    arc = [(math.cos(math.pi * i / steps) * half, math.sin(math.pi * i / steps) * height) for i in range(steps + 1)]
    strips = []
    for index in range(steps):
        (l0, z0), (l1, z1) = arc[index], arc[index + 1]
        strips.append([(-length, l0, z0), (length, l0, z0), (length, l1, z1), (-length, l1, z1)])
    caps = [[(side * length, l, z) for l, z in arc] for side in (-1, 1)]
    ribs = []
    for position in range(-int(length), int(length) + 1, RIB_SPACING):
        rib = [(position, l, z) for l, z in arc]
        ribs.append(Faces([[(position, rib[i][1] * 1.04, rib[i][2] * 1.04), (position, rib[i + 1][1] * 1.04, rib[i + 1][2] * 1.04), (position + 0.5, rib[i + 1][1] * 1.04, rib[i + 1][2] * 1.04), (position + 0.5, rib[i][1] * 1.04, rib[i][2] * 1.04)] for i in range(steps)], (0, 0, 0), FRAME, layer=1))
    parts = [Faces(strips, (0, 0, 0), PLASTIC), Faces(caps, (0, 0, 0), PLASTIC, PLASTIC[2])] + ribs
    parts += [flat_quad([(length + 0.05, -1.4, 0), (length + 0.05, 1.4, 0), (length + 0.05, 1.4, 4.4), (length + 0.05, -1.4, 4.4)], DOOR, (length, 0, 2))]
    return Model(parts=parts, footprint=(-length, -half, length, half), height=height)


WALL_FT = 9.0
RIDGE_RISE = 5.0 / 16.0
WINDOW_FT = 3.0


def window_positions(start, step, limit):
    positions = []
    position = start
    while position + WINDOW_FT <= limit:
        positions.append(position)
        position += step
    return positions


def commons(width_ft=20.0, depth_ft=16.0):
    half_f, half_l, wall = width_ft / 2, depth_ft / 2, WALL_FT
    ridge = wall + depth_ft * RIDGE_RISE
    walls = block((-half_f, -half_l, 0), (half_f, half_l, wall), WALL_CREAM)
    windows = []
    for position in window_positions(-half_f + 4.0, 4.5, half_f - 4.0):
        windows.append(flat_quad([(position, half_l + 0.05, 3.5), (position + 3.0, half_l + 0.05, 3.5), (position + 3.0, half_l + 0.05, 7.0), (position, half_l + 0.05, 7.0)], WINDOW, (position, half_l, 5)))
    for position in window_positions(-half_l + 3.0, 6.0, half_l - 4.0):
        windows.append(flat_quad([(half_f + 0.05, position, 3.5), (half_f + 0.05, position + 3.0, 3.5), (half_f + 0.05, position + 3.0, 7.0), (half_f + 0.05, position, 7.0)], WINDOW, (half_f, position, 5)))
    door_left = half_l - 3.0
    door = flat_quad([(half_f + 0.06, door_left, 0), (half_f + 0.06, door_left + 2.0, 0), (half_f + 0.06, door_left + 2.0, 6.2), (half_f + 0.06, door_left, 6.2)], DOOR, (half_f, door_left + 1.0, 3))
    overhang = 1.4
    gable = [
        [(-half_f - overhang, -half_l - overhang, wall), (half_f + overhang, -half_l - overhang, wall), (half_f + overhang, 0, ridge), (-half_f - overhang, 0, ridge)],
        [(-half_f - overhang, half_l + overhang, wall), (half_f + overhang, half_l + overhang, wall), (half_f + overhang, 0, ridge), (-half_f - overhang, 0, ridge)],
    ]
    thickness = [
        [(half_f + overhang, -half_l - overhang, wall), (half_f + overhang, half_l + overhang, wall), (half_f + overhang, half_l + overhang, wall - 0.6), (half_f + overhang, -half_l - overhang, wall - 0.6)],
        [(-half_f - overhang, half_l + overhang, wall), (half_f + overhang, half_l + overhang, wall), (half_f + overhang, half_l + overhang, wall - 0.6), (-half_f - overhang, half_l + overhang, wall - 0.6)],
    ]
    end_gable = [[(half_f, -half_l, wall), (half_f, half_l, wall), (half_f, 0, ridge - 1.6)]]
    parts = [walls] + windows + [door, Faces(end_gable, (0, 0, wall), WALL_CREAM), Faces(gable, (0, 0, wall - 3), ROOF_RED, layer=1), Faces(thickness, (0, 0, wall - 3), ROOF_RED, layer=1)]
    chimney_f, chimney_l = -0.6 * half_f, -0.6875 * half_l
    parts += [block((chimney_f, chimney_l, ridge - 2.2), (chimney_f + 1.6, chimney_l + 1.6, ridge + 2.2), ROOF_GREY, layer=2)]
    return Model(parts=parts, footprint=(-half_f, -half_l, half_f, half_l), height=ridge)


def shed(width_ft=10.0, depth_ft=8.0):
    half_f, half_l, low, high = width_ft / 2, depth_ft / 2, 7.0, 9.0
    walls = block((-half_f, -half_l, 0), (half_f, half_l, low), WOOD)
    planks = []
    for position in range(int(-half_f + 1), int(half_f), 2):
        planks.append(flat_quad([(position, half_l + 0.04, 0), (position + 0.12, half_l + 0.04, 0), (position + 0.12, half_l + 0.04, low), (position, half_l + 0.04, low)], WOOD[0], (position, half_l, 3)))
    door = flat_quad([(half_f + 0.05, -1.4, 0), (half_f + 0.05, 1.4, 0), (half_f + 0.05, 1.4, 6.0), (half_f + 0.05, -1.4, 6.0)], DOOR, (half_f, 0, 3))
    roof = [
        [(-half_f - 0.8, -half_l - 0.8, high), (half_f + 0.8, -half_l - 0.8, high), (half_f + 0.8, half_l + 0.8, low), (-half_f - 0.8, half_l + 0.8, low)],
    ]
    edge = [[(half_f + 0.8, -half_l - 0.8, high), (half_f + 0.8, half_l + 0.8, low), (half_f + 0.8, half_l + 0.8, low - 0.5), (half_f + 0.8, -half_l - 0.8, high - 0.5)]]
    parts = [walls] + planks + [door, Faces(roof, (0, 0, low - 2), ROOF_GREY, layer=1), Faces(edge, (0, 0, low - 2), ROOF_GREY, layer=1)]
    return Model(parts=parts, footprint=(-half_f, -half_l, half_f, half_l), height=high)


def fence(length=12.0):
    posts = [block((position - 0.2, -0.2, 0), (position + 0.2, 0.2, 3.6), WOOD) for position in range(-int(length / 2), int(length / 2) + 1, 4)]
    rails = [block((-length / 2, -0.12, height), (length / 2, 0.12, height + 0.35), WOOD) for height in (1.2, 2.6)]
    return Model(parts=posts + rails, footprint=(-length / 2, -0.3, length / 2, 0.3), height=3.6)


def fence_segment(length=5.0):
    posts = [block((end - 0.2, -0.2, 0), (end + 0.2, 0.2, 3.6), WOOD) for end in (-length / 2, length / 2)]
    rails = [block((-length / 2, -0.12, height), (length / 2, 0.12, height + 0.35), WOOD) for height in (1.2, 2.6)]
    return Model(parts=posts + rails, footprint=(-length / 2, -0.3, length / 2, 0.3), height=3.6)
