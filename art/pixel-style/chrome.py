from canvas import Canvas
from palette import BROADLEAF, CHROME, CHROME_ACCENT, CHROME_GOOD, CHROME_TEXT, DIRT, GLASS, OUTLINE, PATH, ROOF_RED, STATUS_BLUE, TITLE, TRUNK, WALL_CREAM, WATER, WOOD
from pixtext import pixel_text



def bevel(canvas, x, y, w, h, face, light, dark, u, raised=True):
    canvas.rect(x, y, x + w, y + h, face)
    top_left, bottom_right = (light, dark) if raised else (dark, light)
    canvas.rect(x, y, x + w, y + u, top_left)
    canvas.rect(x, y, x + u, y + h, top_left)
    canvas.rect(x, y + h - u, x + w, y + h, bottom_right)
    canvas.rect(x + w - u, y, x + w, y + h, bottom_right)


def outlined(icon):
    result = Canvas(icon.size[0], icon.size[1])
    result.image.alpha_composite(icon.image)
    for y in range(icon.size[1]):
        for x in range(icon.size[0]):
            if icon.get(x, y)[3] != 0:
                continue
            for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                nx, ny = x + dx, y + dy
                if 0 <= nx < icon.size[0] and 0 <= ny < icon.size[1] and icon.get(nx, ny)[3] != 0:
                    result.put(x, y, OUTLINE)
                    break
    return result


def icon_fence(u):
    icon = Canvas(16 * u, 16 * u)
    for post in (2, 7, 12):
        icon.rect(post * u, 4 * u, (post + 2) * u, 14 * u, WOOD[2])
        icon.rect(post * u, 4 * u, (post + 1) * u, 14 * u, WOOD[3])
    for rail in (6, 10):
        icon.rect(1 * u, rail * u, 15 * u, (rail + 2) * u, WOOD[1])
    return outlined(icon)


def icon_hoop(u):
    icon = Canvas(16 * u, 16 * u)
    icon.ellipse((8 * u, 11 * u), 7 * u, 7 * u, GLASS[1])
    icon.rect(1 * u, 11 * u, 15 * u, 14 * u, GLASS[1])
    icon.ellipse((7 * u, 9 * u), 4 * u, 4 * u, GLASS[2])
    for rib in (4, 8, 12):
        icon.rect(rib * u, 6 * u, (rib + 1) * u, 14 * u, GLASS[0])
    return outlined(icon)


def icon_commons(u):
    icon = Canvas(16 * u, 16 * u)
    icon.rect(3 * u, 8 * u, 14 * u, 15 * u, WALL_CREAM[2])
    icon.rect(10 * u, 8 * u, 14 * u, 15 * u, WALL_CREAM[1])
    icon.polygon([(1 * u, 8 * u), (8 * u, 2 * u), (16 * u - 1, 8 * u)], ROOF_RED[2])
    icon.polygon([(8 * u, 2 * u), (16 * u - 1, 8 * u), (11 * u, 8 * u)], ROOF_RED[1])
    icon.rect(6 * u, 10 * u, 8 * u, 15 * u, DIRT[0])
    icon.rect(9 * u, 10 * u, 11 * u, 12 * u, WATER[2])
    return outlined(icon)


def icon_path(u):
    icon = Canvas(16 * u, 16 * u)
    icon.polygon([(8 * u, 3 * u), (15 * u, 8 * u), (8 * u, 13 * u), (1 * u, 8 * u)], PATH[2])
    icon.polygon([(8 * u, 6 * u), (12 * u, 8 * u), (8 * u, 10 * u), (4 * u, 8 * u)], PATH[3])
    icon.rect(6 * u, 7 * u, 7 * u, 8 * u, PATH[0])
    icon.rect(10 * u, 8 * u, 11 * u, 9 * u, PATH[0])
    return outlined(icon)


def icon_pond(u):
    icon = Canvas(16 * u, 16 * u)
    icon.polygon([(8 * u, 3 * u), (15 * u, 8 * u), (8 * u, 13 * u), (1 * u, 8 * u)], WATER[2])
    icon.polygon([(8 * u, 5 * u), (13 * u, 8 * u), (8 * u, 11 * u), (3 * u, 8 * u)], WATER[1])
    icon.rect(5 * u, 7 * u, 8 * u, 8 * u, WATER[4])
    icon.rect(9 * u, 9 * u, 11 * u, 10 * u, WATER[3])
    return outlined(icon)


def icon_tree(u):
    icon = Canvas(16 * u, 16 * u)
    icon.rect(7 * u, 10 * u, 9 * u, 15 * u, TRUNK[1])
    icon.ellipse((8 * u, 7 * u), 6 * u, 6 * u, BROADLEAF[2])
    icon.ellipse((6 * u, 5 * u), 3 * u, 3 * u, BROADLEAF[3])
    icon.ellipse((10 * u, 9 * u), 3 * u, 2 * u, BROADLEAF[1])
    return outlined(icon)


ICONS = [("Paddock", icon_fence), ("Hoop house", icon_hoop), ("Commons", icon_commons), ("Path", icon_path), ("Pond", icon_pond), ("Tree", icon_tree)]


def window(canvas, x, y, w, h, title, u):
    bevel(canvas, x, y, w, h, CHROME[2], CHROME[4], CHROME[0], u)
    canvas.rect(x + 2 * u, y + 2 * u, x + w - 2 * u, y + 12 * u, TITLE[1])
    canvas.rect(x + 2 * u, y + 2 * u, x + w - 2 * u, y + 3 * u, TITLE[3])
    canvas.rect(x + 2 * u, y + 11 * u, x + w - 2 * u, y + 12 * u, TITLE[0])
    pixel_text(canvas, x + 5 * u, y + 3 * u, title, CHROME_TEXT, 8 * u + 1)
    canvas.rect(x + w - 11 * u, y + 4 * u, x + w - 4 * u, y + 10 * u, CHROME[3])
    canvas.rect(x + w - 10 * u, y + 5 * u, x + w - 5 * u, y + 6 * u, CHROME_TEXT)


def status_bar(canvas, x, y, w, h, readouts, u):
    bevel(canvas, x, y, w, h, CHROME[1], CHROME[3], CHROME[0], u, raised=False)
    step = w // (len(readouts) + 1)
    for index, (label, value, colour) in enumerate(readouts):
        left = x + 6 * u + index * step
        canvas.rect(left, y + 4 * u, left + 6 * u, y + 10 * u, colour)
        canvas.rect(left, y + 4 * u, left + 6 * u, y + 5 * u, CHROME_TEXT)
        label_width, _ = pixel_text(canvas, left + 9 * u, y + 3 * u, label, CHROME[5], 8 * u + 1)
        pixel_text(canvas, left + 12 * u + label_width, y + 3 * u, value, CHROME_TEXT, 8 * u + 1)


def toolbar(canvas, x, y, selected, u):
    size = 24 * u
    gap = 3 * u
    width = len(ICONS) * (size + gap) + gap + 2 * u
    bevel(canvas, x, y, width, size + gap * 2, CHROME[2], CHROME[4], CHROME[0], u)
    for index, (_, make) in enumerate(ICONS):
        left = x + gap + 2 * u + index * (size + gap)
        top = y + gap
        pressed = index == selected
        bevel(canvas, left, top, size, size, CHROME[1] if pressed else CHROME[3], CHROME[4] if not pressed else CHROME[0], CHROME[0] if not pressed else CHROME[4], u, raised=not pressed)
        if pressed:
            canvas.rect(left + u, top + u, left + size - u, top + 2 * u, CHROME_ACCENT)
        icon = make(u)
        canvas.paste(icon, left + (size - icon.size[0]) // 2, top + (size - icon.size[1]) // 2 + (u if pressed else 0))
    return width


def panel_lines(canvas, x, y, lines, u):
    for index, (text, colour) in enumerate(lines):
        pixel_text(canvas, x, y + index * 11 * u, text, colour, 8 * u + 2)


def button(canvas, x, y, w, h, label, u, accent=False):
    bevel(canvas, x, y, w, h, CHROME_ACCENT if accent else CHROME[3], CHROME_TEXT if accent else CHROME[4], CHROME[0], u)
    pixel_text(canvas, x + 4 * u, y + 2 * u, label, CHROME[0] if accent else CHROME_TEXT, 8 * u + 1)


def compose(scene, u):
    margin = 6 * u
    status_h, bar_h, panel_w = 16 * u, 36 * u, 132 * u
    width = scene.width + panel_w + margin * 3 + 4 * u
    height = status_h + scene.height + bar_h + margin * 4
    canvas = Canvas(width, height, CHROME[0])
    status_bar(
        canvas, margin, margin, width - margin * 2, status_h,
        [("CAPITAL", "$220", CHROME_ACCENT), ("LABOUR", "87 h/yr", CHROME_GOOD), ("PASTURE", "0.4 t/yr", STATUS_BLUE)], u,
    )
    field_y = margin * 2 + status_h
    bevel(canvas, margin, field_y, scene.width + 4 * u, scene.height + 4 * u, CHROME[0], CHROME[0], CHROME[4], u, raised=False)
    canvas.paste(scene, margin + 2 * u, field_y + 2 * u)
    panel_x = margin * 2 + scene.width + 4 * u
    window(canvas, panel_x, field_y, panel_w, scene.height + 4 * u, "PLAN", u)
    panel_lines(
        canvas, panel_x + 6 * u, field_y + 18 * u,
        [("My plan", CHROME_ACCENT), ("  viewing", CHROME[5]), ("Alt 1", CHROME_TEXT), ("Alt 2", CHROME_TEXT), ("", CHROME_TEXT), ("Pond +1,250 sq ft", CHROME_GOOD), ("Labour +49 h/yr", CHROME_GOOD)],
        u,
    )
    button(canvas, panel_x + 6 * u, field_y + scene.height - 28 * u, 52 * u, 12 * u, "New alt", u, accent=True)
    button(canvas, panel_x + 64 * u, field_y + scene.height - 28 * u, 52 * u, 12 * u, "Save", u)
    toolbar(canvas, margin, height - bar_h - margin, 4, u)
    return canvas
