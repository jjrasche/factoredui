import re
import unittest
from pathlib import Path

from buildings import commons, fence, hoop_house, shed
from creatures import cow, person
from machines import tractor, tractor_effects, van
from model import FACINGS
from render import render_model
from scene import build_scene
from trees import CLASSES, RADII

HERE = Path(__file__).resolve().parent
COLOUR_LITERAL = re.compile(r"#[0-9A-Fa-f]{6}\b|\(\s*\d{1,3},\s*\d{1,3},\s*\d{1,3},\s*\d{1,3}\s*\)")
LITERAL_FREE = ("buildings.py", "chrome.py", "creatures.py", "machines.py", "scene.py", "sheet.py", "terrain.py", "trees.py", "iso.py", "model.py", "render.py", "export_atlas.py")
ALLOWED = {}


def opaque_pixels(canvas):
    alpha = canvas.image.split()[3]
    return sum(1 for value in alpha.getdata() if value > 150)


class PaletteDisciplineTest(unittest.TestCase):
    def test_no_module_but_the_palette_writes_a_colour_literal(self):
        stray = []
        for name in LITERAL_FREE:
            for match in COLOUR_LITERAL.finditer((HERE / name).read_text(encoding="utf-8")):
                if match.group(0) not in ALLOWED.get(name, set()):
                    stray.append(f"{name}: {match.group(0)}")
        self.assertEqual([], stray)


class EverySpriteDrawsTest(unittest.TestCase):
    def test_every_model_draws_something_in_every_facing(self):
        models = [hoop_house(), commons(), shed(), fence(10.0), van(), person(0), cow(0), cow(0, grazing=True), tractor("Red", "idle"), tractor("Green", "working")]
        for width in (32, 64):
            for model in models:
                for facing in FACINGS:
                    canvas, _ = render_model(model, facing, width)
                    self.assertGreater(opaque_pixels(canvas), 40, f"{width} {facing}")

    def test_the_working_tractor_differs_from_the_idle_one_by_its_implement_and_effects(self):
        idle, _ = render_model(tractor("Red", "idle"), "SE", 32)
        working, _ = render_model(tractor("Red", "working"), "SE", 32, extras=tractor_effects("working"))
        self.assertGreater(opaque_pixels(working), opaque_pixels(idle))

    def test_a_palette_swapped_tractor_has_the_same_silhouette_and_different_colours(self):
        red, _ = render_model(tractor("Red", "idle"), "SE", 32)
        blue, _ = render_model(tractor("Blue", "idle"), "SE", 32)
        self.assertEqual(red.image.split()[3].tobytes(), blue.image.split()[3].tobytes())
        self.assertNotEqual(red.image.tobytes(), blue.image.tobytes())

    def test_every_tree_class_and_size_draws_and_larger_crowns_draw_more(self):
        for make in CLASSES.values():
            counts = [opaque_pixels(make(32, radius)[0]) for radius in RADII.values()]
            self.assertEqual(sorted(counts), counts)
            self.assertEqual(len(set(counts)), len(counts))

    def test_the_playfield_scene_builds_at_both_tile_sizes(self):
        for width in (32, 64):
            scene = build_scene(width)
            self.assertGreater(opaque_pixels(scene), scene.width * scene.height // 3)


if __name__ == "__main__":
    unittest.main()
