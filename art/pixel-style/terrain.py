from canvas import Canvas, hashed
from palette import DIRT, GRASS, PATH, SAND, WATER


def inside_diamond(x, y, width):
    half = width / 2
    return abs(x + 0.5 - half) / half + abs(y + 0.5 - half / 2) / (half / 2) <= 1.0


def textured_tile(width, ramp, seed, weights):
    canvas = Canvas(width, width // 2)
    for y in range(width // 2):
        for x in range(width):
            if not inside_diamond(x, y, width):
                continue
            roll = hashed(x, y, seed) % 100
            level, total = len(weights) - 1, 0
            for index, weight in enumerate(weights):
                total += weight
                if roll < total:
                    level = index
                    break
            canvas.put(x, y, ramp[level])
    return canvas


def scatter(canvas, width, seed, count, paint):
    rows = width // 2
    for index in range(count):
        x = hashed(index, 3, seed) % width
        y = hashed(index, 7, seed) % (rows - 2) + 1
        if inside_diamond(x, y, width) and inside_diamond(x, y - 1, width):
            paint(canvas, x, y)


def grass_tile(width, variant=0):
    tile = textured_tile(width, GRASS, 11 + variant, [9, 62, 17, 8, 4])

    def tuft(canvas, x, y):
        canvas.put(x, y, GRASS[1])
        canvas.put(x, y - 1, GRASS[4])

    scatter(tile, width, 21 + variant, max(2, width // 8), tuft)
    return tile


def path_tile(width, variant=0):
    tile = textured_tile(width, PATH, 31 + variant, [10, 20, 52, 13, 5])

    def pebble(canvas, x, y):
        canvas.put(x, y, PATH[0])
        canvas.put(x + 1, y, PATH[1])

    scatter(tile, width, 41 + variant, max(2, width // 10), pebble)
    return tile


def sand_tile(width, variant=0):
    return textured_tile(width, SAND, 51 + variant, [6, 18, 58, 14, 4])


def dirt_tile(width, variant=0):
    tile = textured_tile(width, DIRT, 61 + variant, [16, 24, 40, 14, 6])

    def clod(canvas, x, y):
        canvas.put(x, y, DIRT[0])
        canvas.put(x + 1, y, DIRT[0])
        canvas.put(x, y + 1, DIRT[1])

    scatter(tile, width, 71 + variant, max(2, width // 9), clod)
    return tile


def water_tile(width, frame):
    tile = textured_tile(width, WATER, 81, [0, 26, 60, 14, 0])
    rows = width // 2
    length = max(3, width // 8)
    for streak in range(max(3, width // 8)):
        y = (hashed(streak, 1, 91) % (rows - 4)) + 2
        start = (hashed(streak, 2, 91) + frame * (width // 8)) % width
        for step in range(length):
            x = (start + step) % width
            if inside_diamond(x, y, width):
                tile.put(x, y, WATER[3])
    for sparkle in range(max(1, width // 16)):
        x = (hashed(sparkle, 3, 97) + frame * 5) % width
        y = hashed(sparkle, 4, 97) % rows
        if inside_diamond(x, y, width):
            tile.put(x, y, WATER[4])
    return tile


def shore_tile(width, frame):
    tile = sand_tile(width, 3)
    water = water_tile(width, frame)
    rows = width // 2
    for y in range(rows):
        for x in range(width):
            if not inside_diamond(x, y, width):
                continue
            along = x / width + y / rows
            if along > 1.0:
                tile.put(x, y, water.get(x, y))
            elif along > 0.9 - (frame % 2) * 0.03:
                tile.put(x, y, WATER[4])
    return tile
