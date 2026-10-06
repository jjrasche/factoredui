def hex_rgb(value):
    value = value.lstrip("#")
    return tuple(int(value[index:index + 2], 16) for index in (0, 2, 4)) + (255,)


def ramp(*values):
    return [hex_rgb(value) for value in values]


def shade(color, factor):
    return tuple(min(255, int(channel * factor)) for channel in color[:3]) + (color[3],)


def mix(first, second, amount):
    return tuple(int(a + (b - a) * amount) for a, b in zip(first, second))


GRASS = ramp("#2F7F26", "#3F9B2E", "#4DB33A", "#5CC845", "#7DDC5C")
PATH = ramp("#B8975A", "#C9A760", "#D8B878", "#E6CC94", "#F1DDAA")
SAND = ramp("#CDB877", "#D9C487", "#E8D49A", "#F0E0B0", "#F8EBC4")
DIRT = ramp("#6B4424", "#7A4F2A", "#8B5E34", "#9C6C3E", "#B27F4C")
WATER = ramp("#1F6FC0", "#2F8FE0", "#46A7F0", "#7CC8FF", "#CFEFFF")
BROADLEAF = ramp("#17521F", "#1F6B2A", "#2E8A36", "#54B04A", "#8AD266")
CONIFER = ramp("#123F2C", "#1B5A3A", "#247048", "#2F8A5A", "#58B27E")
UNKNOWN = ramp("#55681F", "#6E8B2E", "#8CA83A", "#A9C24F", "#CBDD80")
TRUNK = ramp("#4A2E17", "#6B4423", "#8A5A2F")
WALL_CREAM = ramp("#B7A98A", "#CDBF9E", "#E8DCC0", "#F6EDD6")
ROOF_RED = ramp("#7E241C", "#A5332A", "#C2433A", "#DE6A5C")
ROOF_GREY = ramp("#5C5F66", "#74777E", "#8F9299", "#B3B6BC")
WOOD = ramp("#6B4424", "#855731", "#9C6C3E", "#B8864F")
GLASS = ramp("#78A9C9", "#A5D0EA", "#D6EEFB", "#FFFFFF")
WINDOW = hex_rgb("#4C86B8")
DOOR = hex_rgb("#5A3A1E")
SKIN = ramp("#C98B64", "#E3A87C", "#F1C7A0")
SHIRTS = ramp("#D63B3B", "#2F6FD6", "#E8C23A", "#8E44AD")
TROUSERS = ramp("#2B3A55", "#4B3B2B")
COW_WHITE = ramp("#B9BEC4", "#E4E8EC", "#FFFFFF")
COW_PATCH = ramp("#1E1E22", "#3A3A42")
OUTLINE = hex_rgb("#1B1F26")
SHADOW = (0, 0, 0, 78)
CHROME = ramp("#14171C", "#20252D", "#2D343F", "#3C4553", "#566173", "#7C8798")
CHROME_ACCENT = hex_rgb("#E8B83A")
CHROME_TEXT = hex_rgb("#E8EDF4")
CHROME_GOOD = hex_rgb("#7DDC5C")

PLASTIC = ramp("#6E98B4", "#98BFD8", "#C4E0F0", "#F2FAFF")
FRAME = ramp("#7C8798", "#9AA5B4", "#C3CCD8", "#E8EDF4")
TITLE = ramp("#17394F", "#1F4F6B", "#2B6A8C", "#4A8FB8")
SHIRT_RAMPS = [ramp("#7E1E1E", "#A82828", "#D63B3B", "#F07070"), ramp("#1B3F7A", "#2859A8", "#2F6FD6", "#6FA4F2"), ramp("#8A6A12", "#B8901C", "#E8C23A", "#F6DE7A")]
TROUSER_RAMP = ramp("#1B2538", "#2B3A55", "#3C5072", "#58709A")
SKIN_RAMP = ramp("#9A6444", "#C98B64", "#E3A87C", "#F1C7A0")
HAIR_RAMP = ramp("#2A1A0E", "#3E2814", "#5A3A1E", "#7A5230")
COW_BODY_RAMP = ramp("#8A9096", "#B9BEC4", "#E4E8EC", "#FFFFFF")
COW_DARK_RAMP = ramp("#101014", "#1E1E22", "#2C2C34", "#3A3A42")
COW_SNOUT_RAMP = ramp("#9A6070", "#C58A9A", "#E5A8B6", "#F4C8D2")
TRACTOR_BODIES = {
    "Red": ramp("#6E1D1A", "#9B2A25", "#C73A33", "#E8625A"),
    "Green": ramp("#1E5A26", "#2C7A36", "#3F9B4A", "#6CC27A"),
    "Blue": ramp("#1B3F7A", "#2859A8", "#3B7BD6", "#6FA4F2"),
}
VAN_BODY = ramp("#9AA3AE", "#C8D0D8", "#E9EEF2", "#FFFFFF")
VAN_STRIPE = ramp("#1B3F7A", "#2859A8", "#3B7BD6", "#6FA4F2")
HUB_COLOUR = hex_rgb("#E6BE46")
SMOKE = hex_rgb("#C8CCD2")
DUST = hex_rgb("#966E46")
STATUS_BLUE = hex_rgb("#7DC8FF")
VOID = hex_rgb("#0E1116")
SHEET_BACKGROUND = hex_rgb("#14181E")
SHEET_BAND = hex_rgb("#202630")
SHEET_TITLE = hex_rgb("#E8EDF4")
SHEET_ACCENT = hex_rgb("#E8B83A")
SHEET_LABEL = hex_rgb("#D6DCE4")
