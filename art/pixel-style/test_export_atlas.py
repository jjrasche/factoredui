import json
import random
import unittest

from export_atlas import ATLAS_DIR, KOTLIN_OUT, build_atlas, indexed, kotlin_source, pack_bits, sprites_at, unpack_bits

ATLAS = build_atlas()


def sprites_of(scale):
    return ATLAS[0]["scales"][str(scale)]["sprites"]


def sprite_pixels(sheets, scale, entry):
    sheet = sheets[scale][entry["sheet"]]
    return sheet.crop((entry["x"], entry["y"], entry["x"] + entry["width"], entry["y"] + entry["height"]))


class AtlasLayoutTest(unittest.TestCase):
    def test_every_sprite_sits_inside_its_sheet_and_no_two_overlap(self):
        manifest, _ = ATLAS
        for scale, declared in manifest["scales"].items():
            for entry in declared["sprites"]:
                bounds = declared["sheets"][entry["sheet"]]
                self.assertLessEqual(entry["x"] + entry["width"], bounds["width"], entry["name"])
                self.assertLessEqual(entry["y"] + entry["height"], bounds["height"], entry["name"])
            for first in declared["sprites"]:
                for second in declared["sprites"]:
                    if first is second or first["sheet"] != second["sheet"]:
                        continue
                    apart = first["x"] + first["width"] <= second["x"] or second["x"] + second["width"] <= first["x"] or first["y"] + first["height"] <= second["y"] or second["y"] + second["height"] <= first["y"]
                    self.assertTrue(apart, f"{scale}: {first['name']} overlaps {second['name']}")

    def test_both_art_scales_carry_the_same_sprites_and_the_wider_tile_draws_them_larger(self):
        small = {entry["name"]: entry for entry in sprites_of(32)}
        large = {entry["name"]: entry for entry in sprites_of(64)}
        self.assertEqual(sorted(small), sorted(large))
        self.assertEqual(len(small), len(sprites_of(32)), "names are unique")
        for name, entry in small.items():
            self.assertGreater(large[name]["width"], entry["width"], name)

    def test_the_sheet_holds_each_sprite_pixel_for_pixel(self):
        _, sheets = ATLAS
        for scale in (32, 64):
            rendered = {sprite.name: sprite.image for sprite in sprites_at(scale)}
            for entry in sprites_of(scale):
                self.assertEqual(rendered[entry["name"]].tobytes(), sprite_pixels(sheets, scale, entry).tobytes(), entry["name"])

    def test_every_standing_sprite_is_drawn_just_above_its_ground_contact_anchor(self):
        _, sheets = ATLAS
        for scale in (32, 64):
            for entry in sprites_of(scale):
                if entry["kind"] == "pattern":
                    continue
                pixels = sprite_pixels(sheets, scale, entry)
                column = [pixels.getpixel((entry["anchor_x"], max(0, entry["anchor_y"] - lift)))[3] for lift in range(3)]
                self.assertGreater(max(column), 0, f"{scale} {entry['name']}")

    def test_ground_patterns_have_no_gaps(self):
        _, sheets = ATLAS
        for scale in (32, 64):
            for entry in sprites_of(scale):
                if entry["kind"] == "pattern":
                    alpha = sprite_pixels(sheets, scale, entry).split()[3]
                    self.assertEqual(255, min(alpha.tobytes()), entry["name"])

    def test_a_footprint_class_is_offered_in_several_sizes_and_all_four_facings(self):
        hoop_houses = [entry for entry in sprites_of(32) if entry["class"] == "hoop_house"]
        self.assertGreaterEqual(len({entry["size"] for entry in hoop_houses}), 3)
        self.assertEqual({"SE", "SW", "NW", "NE"}, {entry["facing"] for entry in hoop_houses})


class SwapTest(unittest.TestCase):
    def test_every_swap_ramp_matches_its_base_in_length_and_the_base_colours_are_in_the_sprite(self):
        manifest, sheets = ATLAS
        for swap, table in manifest["swaps"].items():
            base = table["ramps"][table["base"]]
            self.assertTrue(all(len(ramp) == len(base) for ramp in table["ramps"].values()), swap)
            for entry in (entry for entry in sprites_of(32) if entry["swap"] == swap):
                present = sprite_pixels(sheets, 32, entry).getcolors(maxcolors=1 << 16)
                colours = {"#%02X%02X%02X%02X" % (a, r, g, b) for _, (r, g, b, a) in present}
                self.assertTrue(colours & set(base), entry["name"])


class EncodingTest(unittest.TestCase):
    def test_pack_bits_round_trips_runs_literals_and_long_runs(self):
        noise = bytes(random.Random(7).randrange(256) for _ in range(1000))
        for data in (b"", b"\x05", b"\x01\x01", b"\x02" * 300, noise, noise[:200] + b"\x00" * 400 + noise[200:]):
            self.assertEqual(data, unpack_bits(pack_bits(data)))

    def test_an_indexed_sheet_decodes_back_to_its_pixels(self):
        _, sheets = ATLAS
        sheet = sheets[32][0]
        palette, indices = indexed(sheet)
        decoded = unpack_bits(pack_bits(indices))
        raw = sheet.tobytes()
        for position in range(0, len(decoded), 997):
            pixel = tuple(raw[position * 4:position * 4 + 4])
            self.assertEqual(pixel if pixel[3] else (0, 0, 0, 0), palette[decoded[position]])


class FreshnessTest(unittest.TestCase):
    def test_the_committed_manifest_and_kotlin_data_match_the_generators(self):
        manifest, sheets = ATLAS
        self.assertEqual(manifest, json.loads((ATLAS_DIR / "pixel-atlas.json").read_text(encoding="utf-8")))
        self.assertEqual(kotlin_source(manifest, sheets), KOTLIN_OUT.read_text(encoding="utf-8"))


if __name__ == "__main__":
    unittest.main()
