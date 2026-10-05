import math

SHADOW = (0, 0, 0, 78)
TILE_FEET = 5.0
VERTICAL_RATIO = 1.2247


class Iso:
    def __init__(self, width, tile_feet=TILE_FEET):
        self.width = width
        self.tile_feet = tile_feet
        self.across = width / (2 * tile_feet)
        self.up = self.across * VERTICAL_RATIO
        self.sphere = self.across * math.sqrt(2)

    def point(self, x, y, z=0.0):
        return ((x - y) * self.across, (x + y) * self.across / 2 - z * self.up)

    def polygon(self, canvas, origin, corners, fill):
        ox, oy = origin
        canvas.polygon([(ox + sx, oy + sy) for sx, sy in (self.point(*corner) for corner in corners)], fill)

    def line(self, canvas, origin, start, end, fill):
        ox, oy = origin
        a, b = self.point(*start), self.point(*end)
        canvas.line((ox + a[0], oy + a[1]), (ox + b[0], oy + b[1]), fill)

    def put(self, canvas, origin, at, fill):
        sx, sy = self.point(*at)
        canvas.put(origin[0] + sx, origin[1] + sy, fill)


def hull(points):
    points = sorted(set((round(x), round(y)) for x, y in points))
    if len(points) <= 2:
        return points

    def cross(origin, a, b):
        return (a[0] - origin[0]) * (b[1] - origin[1]) - (a[1] - origin[1]) * (b[0] - origin[0])

    lower, upper = [], []
    for point in points:
        while len(lower) >= 2 and cross(lower[-2], lower[-1], point) <= 0:
            lower.pop()
        lower.append(point)
    for point in reversed(points):
        while len(upper) >= 2 and cross(upper[-2], upper[-1], point) <= 0:
            upper.pop()
        upper.append(point)
    return lower[:-1] + upper[:-1]


def ground_shadow(canvas, iso, origin, footprint, height):
    ox, oy = origin
    x0, y0, x1, y1 = footprint
    corners = [iso.point(x0, y0), iso.point(x1, y0), iso.point(x1, y1), iso.point(x0, y1)]
    reach = height * iso.up * 0.55
    shifted = [(sx + reach, sy + reach * 0.4) for sx, sy in corners]
    canvas.polygon(hull([(ox + sx, oy + sy) for sx, sy in corners + shifted]), SHADOW)
