from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

HERE = Path(__file__).resolve().parent
SHOTS = HERE.parent
FONT = "C:/Windows/Fonts/segoeui.ttf"
FONT_BOLD = "C:/Windows/Fonts/segoeuib.ttf"
RED = (235, 64, 52)
PAPER = (248, 246, 240)
INK = (30, 30, 34)
MUTED = (96, 96, 104)


def font(size, bold=False):
    return ImageFont.truetype(FONT_BOLD if bold else FONT, size)


def wrap(draw, text, face, width):
    lines, line = [], ""
    for word in text.split():
        trial = f"{line} {word}".strip()
        if draw.textlength(trial, font=face) <= width:
            line = trial
        else:
            lines.append(line)
            line = word
    if line:
        lines.append(line)
    return lines


def marker(draw, at, number):
    x, y = at
    draw.ellipse((x - 15, y - 15, x + 15, y + 15), fill=RED, outline=(255, 255, 255), width=2)
    draw.text((x, y), str(number), font=font(18, True), fill=(255, 255, 255), anchor="mm")


def notes_block(draw, origin, width, notes):
    x, y = origin
    for number, issue, change in notes:
        draw.ellipse((x, y, x + 30, y + 30), fill=RED)
        draw.text((x + 15, y + 15), str(number), font=font(18, True), fill=(255, 255, 255), anchor="mm")
        for index, line in enumerate(wrap(draw, issue, font(19, True), width - 50)):
            draw.text((x + 44, y + index * 25), line, font=font(19, True), fill=INK)
        y += 25 * len(wrap(draw, issue, font(19, True), width - 50))
        for line in wrap(draw, "Change: " + change, font(18), width - 50):
            draw.text((x + 44, y), line, font=font(18), fill=MUTED)
            y += 24
        y += 14
    return y


def annotated(shot, markers, notes, out, title):
    image = Image.open(SHOTS / shot).convert("RGB")
    scratch = Image.new("RGB", (image.width, 10))
    measure = ImageDraw.Draw(scratch)
    column = image.width // 2 - 40
    left, right = notes[: (len(notes) + 1) // 2], notes[(len(notes) + 1) // 2:]
    height = max(
        notes_height(measure, left, column),
        notes_height(measure, right, column),
    )
    sheet = Image.new("RGB", (image.width, 60 + image.height + height + 30), PAPER)
    draw = ImageDraw.Draw(sheet)
    draw.text((24, 14), title, font=font(26, True), fill=INK)
    sheet.paste(image, (0, 60))
    for number, at in markers:
        marker(draw, (at[0], at[1] + 60), number)
    notes_block(draw, (24, 60 + image.height + 24), column, left)
    notes_block(draw, (image.width // 2 + 20, 60 + image.height + 24), column, right)
    sheet.save(HERE / out)


def notes_height(draw, notes, width):
    total = 0
    for _, issue, change in notes:
        total += 25 * len(wrap(draw, issue, font(19, True), width - 50))
        total += 24 * len(wrap(draw, "Change: " + change, font(18), width - 50)) + 14
    return total + 24


def table(out, title, rows, widths, header):
    scratch = ImageDraw.Draw(Image.new("RGB", (10, 10)))
    body = font(17)
    heights = []
    for row in rows:
        cells = [len(wrap(scratch, text, body, width - 20)) for text, width in zip(row, widths)]
        heights.append(max(cells) * 23 + 20)
    total_width = sum(widths)
    sheet = Image.new("RGB", (total_width, 130 + sum(heights) + 20), PAPER)
    draw = ImageDraw.Draw(sheet)
    draw.text((24, 16), title, font=font(26, True), fill=INK)
    x = 0
    for text, width in zip(header, widths):
        draw.rectangle((x, 70, x + width, 108), fill=(40, 44, 52))
        draw.text((x + 10, 79), text, font=font(18, True), fill=(255, 255, 255))
        x += width
    y = 108
    for row, height in zip(rows, heights):
        x = 0
        for index, (text, width) in enumerate(zip(row, widths)):
            draw.rectangle((x, y, x + width, y + height), outline=(200, 198, 190))
            face = font(17, True) if index == 0 else body
            for line_index, line in enumerate(wrap(draw, text, face, width - 20)):
                draw.text((x + 10, y + 10 + line_index * 23), line, font=face, fill=INK)
            x += width
        y += height
    sheet.save(HERE / out)


OVERVIEW = [
    (1, "Dead space: a diamond floating in a black window", "fit the parcel to the window, rotate tall parcels 90 degrees, show neighbours and the road in muted tones instead of void."),
    (2, "The ground is a flat checkerboard slab with no relation to the real land", "drape the aerial image over the ground and shade it from the elevation model; give the slab an edge and a skirt."),
    (3, "Trees are 6 to 9 pixel clip-art, three variants, grey halo pixels in dark mode", "shaded crown solids (soft ellipsoids with a light direction), a ground shadow, colour by height; the pixel sprites stay as the low-detail fallback."),
    (4, "Buildings are two flat side tones and a lighter roof: no light, no shadow, no material", "bake directional shading and a soft contact shadow; give each type a material colour set from the reference art."),
    (5, "A full-width Theme dropdown is the first thing you see", "remove it from the map area (developer control); theme follows the window."),
    (6, "Default purple Material buttons clash with the earth palette, labels wrap ('Save plan')", "neutral outline buttons, one accent from the reference, fixed minimum widths."),
    (7, "Panel hierarchy is flat: same small size for headings, notes and figures; grey boxes for every score", "a three-step type scale, dividers between sections, figures right-aligned with units in a lighter weight."),
    (8, "No scale bar, north arrow, coordinates or labels on the map", "overlay a scale bar and north arrow in the corner; label an object on hover or tap."),
]

LIDAR = [
    (1, "Ten trees on a 5 x 32 strip use about 12 percent of the width", "rotate the parcel to run along the longer window axis and zoom to it."),
    (2, "Two overlapping pairs read as one smudge; positions are right, the sprites hide it", "crown solids with transparency or an outline so overlap reads as two trees."),
    (3, "Every tree is the same size class although crowns differ (a crown radius is in the data)", "scale by crown radius and height (already scaled by radius; add height as vertical size) and tint by height."),
    (4, "Nothing says what the strip is: no ground image, no road, no boundary line", "aerial underlay and a parcel outline from the frame (EPSG) already in the world file."),
    (5, "Tree record only shows after a tap and the tree is not highlighted", "a selection ring and a callout line from the tree to its card."),
    (6, "Top-down view has the same sprites as the isometric one", "in top view draw crown discs with a drop shadow, not side-view trees."),
]

COMPARE = [
    (1, "Light mode: the ground is a neon yellow-green and the paddock tint is almost the ground colour", "derive ground from the aerial or a muted neutral, and keep use colours at least 30 percent apart in lightness."),
    (2, "The paddock fence is thin and low; the sheep are 3 pixels", "heavier fence line and a ground fill for the paddock area; sheep sized to the footprint."),
    (3, "Panel is a very different grey and weight from the map: two designs side by side", "one surface colour system for map background and panel."),
    (4, "Selected brush chip is a grey box with bold text: it looks pressed but not chosen", "filled accent with a check mark."),
]

CAPABILITY_HEADER = ["Look", "Can it?", "How, and the catch", "Cannot yet", "What it needs"]
CAPABILITY = [
    ("Aerial colour underlay", "Yes", "Draw an image into the ground plane with a skew transform (the image and SVG loader is already there); top view is a plain draw. Catch: one affine transform, so no perspective warp; large images need tiling.", "Live map tiles (no tile fetcher or cache).", "A georeferenced image per parcel and a path to it in the world or presentation file; the EPSG frame is already in the world file."),
    ("Shaded terrain from elevation", "Yes, in steps", "Per-vertex colour triangles (the old scene3d terrain already batches these) shaded by slope against a light direction; or per-tile shading and a vertical lift in isometric.", "Smooth continuous lighting at 5 ft on a 65 x 130 grid without a cost check; any terrain the world file does not carry.", "An elevation field: the world file has z per instance only, no ground surface; a DEM (lidar ground grid) in the world or presentation file."),
    ("Crown solids instead of flat pixel sprites", "Yes, as shaded 2D forms", "A radial-gradient ellipsoid, a trunk, a ground shadow ellipse and a light direction; sized from crown radius and height. Reads as soft 3D.", "True solids with correct overlap and intersection (no depth buffer in the 2D painter).", "Nothing new in the data; art direction for shape and colour."),
    ("Lighting and shadows", "Baked only", "A fixed light direction baked into side tones, gradients and contact shadows.", "Moving sun, cast shadows on terrain and other objects, per-pixel lighting.", "A real 3D renderer: the separate primitive and the GPU research spike."),
    ("Rotating the view (0 / 90 / 180 / 270)", "Yes for geometry", "Rotate the grid coordinates before projecting; prisms, fences, water and picture footprints follow.", "Sprites drawn from one angle look wrong when rotated.", "One picture per facing, or billboards that face the camera."),
    ("Any angle or orbit", "No", "Not with the 2D tilemap.", "Free orbit and perspective.", "The 3D primitive (scene3d is a flat-colour painter and is not the base)."),
]


if __name__ == "__main__":
    annotated(
        "03-several-uses-placed-dark.png",
        [(1, (240, 200)), (2, (760, 650)), (3, (632, 382)), (4, (407, 533)), (5, (586, 33)), (6, (1330, 133)), (7, (1455, 372)), (8, (1050, 800))],
        OVERVIEW,
        "critique-1-overview-dark.png",
        "What looks weakest: the parcel builder in dark mode (build bd9480dd)",
    )
    annotated(
        "09-lidar-sample-dark.png",
        [(1, (330, 450)), (2, (518, 280)), (3, (527, 600)), (4, (810, 500)), (5, (1300, 400)), (6, (523, 812))],
        LIDAR,
        "critique-2-lidar-trees.png",
        "What looks weakest: the ten lidar trees (top-down view, dark)",
    )
    annotated(
        "08-placed-light.png",
        [(1, (560, 500)), (2, (633, 470)), (3, (1130, 500)), (4, (330, 111))],
        COMPARE,
        "critique-3-light-mode.png",
        "What looks weakest: light mode",
    )
    table(
        "critique-4-what-the-renderer-can-and-cannot-do.png",
        "A more convincing look: what the renderer can and cannot do today",
        CAPABILITY,
        [250, 190, 470, 330, 420],
        CAPABILITY_HEADER,
    )
