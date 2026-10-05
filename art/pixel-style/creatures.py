from model import Box, Model, slot
from palette import COW_PATCH, COW_WHITE, SHIRTS, SKIN, TROUSERS, ramp

SHIRT_RAMPS = [ramp("#7E1E1E", "#A82828", "#D63B3B", "#F07070"), ramp("#1B3F7A", "#2859A8", "#2F6FD6", "#6FA4F2"), ramp("#8A6A12", "#B8901C", "#E8C23A", "#F6DE7A")]
TROUSER_PAINT = slot(ramp("#1B2538", "#2B3A55", "#3C5072", "#58709A"))
SKIN_PAINT = slot(ramp("#9A6444", "#C98B64", "#E3A87C", "#F1C7A0"))
HAIR_PAINT = slot(ramp("#2A1A0E", "#3E2814", "#5A3A1E", "#7A5230"))
COW_BODY = slot(ramp("#8A9096", "#B9BEC4", "#E4E8EC", "#FFFFFF"))
COW_DARK = slot(ramp("#101014", "#1E1E22", "#2C2C34", "#3A3A42"))
COW_SNOUT = slot(ramp("#9A6070", "#C58A9A", "#E5A8B6", "#F4C8D2"))

STRIDE = (0.45, 0.0, -0.45, 0.0)
LIFT = (0.0, 0.5, 0.0, 0.5)


def person(frame, shirt=0):
    shirt_paint = slot(SHIRT_RAMPS[shirt])
    swing = STRIDE[frame]
    lift = LIFT[frame]
    parts = [
        Box((swing - 0.3, -0.75, lift), (swing + 0.3, -0.15, 2.7 + lift * 0.0), TROUSER_PAINT, "leg_a"),
        Box((-swing - 0.3, 0.15, 0.0), (-swing + 0.3, 0.75, 2.7), TROUSER_PAINT, "leg_b"),
        Box((-0.4, -0.85, 2.6), (0.4, 0.85, 4.6), shirt_paint, "torso"),
        Box((-swing - 0.25, -1.3, 2.9), (-swing + 0.25, -0.85, 4.4), shirt_paint, "arm_a"),
        Box((swing - 0.25, 0.85, 2.9), (swing + 0.25, 1.3, 4.4), shirt_paint, "arm_b"),
        Box((-swing - 0.2, -1.25, 2.4), (-swing + 0.2, -0.9, 2.95), SKIN_PAINT, "hand_a"),
        Box((swing - 0.2, 0.9, 2.4), (swing + 0.2, 1.25, 2.95), SKIN_PAINT, "hand_b"),
        Box((-0.4, -0.4, 4.6), (0.4, 0.4, 5.4), SKIN_PAINT, "head"),
        Box((-0.45, -0.45, 5.2), (0.35, 0.45, 5.75), HAIR_PAINT, "hair"),
    ]
    return Model(parts=parts, footprint=(-0.8, -1.3, 0.8, 1.3), height=5.75)


def cow(frame, grazing=False):
    swing = STRIDE[frame % 4]
    lift = LIFT[frame % 4]
    head_low = grazing
    head = Box((2.0, -0.55, 0.7 if head_low else 3.0), (3.5, 0.55, 2.0 if head_low else 4.5), COW_BODY, "head")
    snout = Box((3.5, -0.4, 0.4 if head_low else 3.0), (3.9, 0.4, 1.0 if head_low else 3.7), COW_SNOUT, "snout")
    neck = Box((1.6, -0.6, 2.8), (2.6, 0.6, 4.4), COW_BODY, "neck") if not head_low else Box((1.6, -0.6, 1.4), (2.6, 0.6, 4.4), COW_BODY, "neck")
    parts = [
        Box((-2.6, -1.0, 2.0), (2.0, 1.0, 4.4), COW_BODY, "body"),
        Box((-1.9, 0.95, 3.0), (-0.6, 1.1, 4.3), COW_DARK, "patch_a"),
        Box((0.2, -1.1, 2.4), (1.4, -0.95, 3.6), COW_DARK, "patch_b"),
        Box((-1.0, -0.7, 4.38), (0.4, 0.5, 4.5), COW_DARK, "patch_top"),
        neck,
        head,
        snout,
        Box((2.1, -0.75, 4.2 if not head_low else 2.0), (2.5, -0.55, 4.9 if not head_low else 2.4), COW_DARK, "horn_a"),
        Box((-2.9, -0.15, 2.4), (-2.6, 0.15, 4.0), COW_DARK, "tail"),
        Box((1.5 + swing, -0.95, lift), (2.0 + swing, -0.45, 2.1), COW_BODY, "leg_fa"),
        Box((1.5 - swing, 0.45, 0.0), (2.0 - swing, 0.95, 2.1), COW_BODY, "leg_fb"),
        Box((-2.3 - swing, -0.95, 0.0), (-1.8 - swing, -0.45, 2.1), COW_BODY, "leg_ba"),
        Box((-2.3 + swing, 0.45, lift), (-1.8 + swing, 0.95, 2.1), COW_BODY, "leg_bb"),
    ]
    return Model(parts=parts, footprint=(-2.9, -1.0, 3.9, 1.0), height=4.9)
