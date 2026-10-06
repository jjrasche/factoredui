import json
import random
import unittest

from export_atlas import ATLAS_DIR, KOTLIN_OUT, VARIANTS, build_atlas, indexed, kotlin_source, pack_bits, sprites_at, unpack_bits, variant_id

ATLAS = build_atlas()
VARIANT_IDS = [variant_id(width, feet) for width, feet in VARIANTS]
COARSE = variant_id(32, 5.0)


def sprites_of(variant):
    return ATLAS[0]["scales"][variant]["sprites"]


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

    def test_every_variant_carries_the_same_sprites_and_more_pixels_per_foot_draws_them_larger(self):
        by_density = sorted(VARIANTS, key=lambda variant: variant[0] / variant[1])
        named = [{entry["name"]: entry for entry in sprites_of(variant_id(*variant))} for variant in by_density]
        for sprites in named:
            self.assertEqual(sorted(named[0]), sorted(sprites))
        self.assertEqual(len(named[0]), len(sprites_of(COARSE)), "names are unique")
        for sparse, dense in zip(named, named[1:]):
            for name, entry in sparse.items():
                if entry["kind"] != "pattern":
                    self.assertGreater(dense[name]["width"], entry["width"], name)

    def test_the_twenty_five_foot_rung_draws_a_tree_a_fifth_the_size_of_the_five_foot_one(self):
        five = {entry["name"]: entry for entry in sprites_of(COARSE)}["tree-unknown/XL"]
        twenty_five = {entry["name"]: entry for entry in sprites_of(variant_id(32, 25.0))}["tree-unknown/XL"]
        self.assertEqual(25.0, twenty_five["art_tile_feet"])
        self.assertLess(twenty_five["height"], five["height"] / 3)

    def test_the_sheet_holds_each_sprite_pixel_for_pixel(self):
        _, sheets = ATLAS
        for scale, (width, feet) in zip(VARIANT_IDS, VARIANTS):
            rendered = {sprite.name: sprite.image for sprite in sprites_at(width, feet)}
            for entry in sprites_of(scale):
                self.assertEqual(rendered[entry["name"]].tobytes(), sprite_pixels(sheets, scale, entry).tobytes(), entry["name"])

    def test_every_standing_sprite_is_drawn_just_above_its_ground_contact_anchor(self):
        _, sheets = ATLAS
        for scale in VARIANT_IDS:
            for entry in sprites_of(scale):
                if entry["kind"] == "pattern":
                    continue
                pixels = sprite_pixels(sheets, scale, entry)
                self.assertTrue(0 <= entry["anchor_x"] < entry["width"] and 0 < entry["anchor_y"] <= entry["height"], f"{scale} {entry['name']} anchor outside")
                column = [pixels.getpixel((entry["anchor_x"], max(0, entry["anchor_y"] - lift)))[3] for lift in range(3)]
                self.assertGreater(max(column), 0, f"{scale} {entry['name']}")

    def test_ground_patterns_have_no_gaps(self):
        _, sheets = ATLAS
        for scale in VARIANT_IDS:
            for entry in sprites_of(scale):
                if entry["kind"] == "pattern":
                    alpha = sprite_pixels(sheets, scale, entry).split()[3]
                    self.assertEqual(255, min(alpha.tobytes()), entry["name"])

    def test_a_footprint_class_is_offered_in_several_sizes_and_all_four_facings(self):
        hoop_houses = [entry for entry in sprites_of(COARSE) if entry["class"] == "hoop_house"]
        self.assertGreaterEqual(len({entry["size"] for entry in hoop_houses}), 3)
        self.assertEqual({"SE", "SW", "NW", "NE"}, {entry["facing"] for entry in hoop_houses})


class SwapTest(unittest.TestCase):
    def test_every_swap_ramp_matches_its_base_in_length_and_the_base_colours_are_in_the_sprite(self):
        manifest, sheets = ATLAS
        for swap, table in manifest["swaps"].items():
            base = table["ramps"][table["base"]]
            self.assertTrue(all(len(ramp) == len(base) for ramp in table["ramps"].values()), swap)
            for entry in (entry for entry in sprites_of(COARSE) if entry["swap"] == swap):
                present = sprite_pixels(sheets, COARSE, entry).getcolors(maxcolors=1 << 16)
                colours = {"#%02X%02X%02X%02X" % (a, r, g, b) for _, (r, g, b, a) in present}
                self.assertTrue(colours & set(base), entry["name"])


class EncodingTest(unittest.TestCase):
    def test_pack_bits_round_trips_runs_literals_and_long_runs(self):
        noise = bytes(random.Random(7).randrange(256) for _ in range(1000))
        for data in (b"", b"\x05", b"\x01\x01", b"\x02" * 300, noise, noise[:200] + b"\x00" * 400 + noise[200:]):
            self.assertEqual(data, unpack_bits(pack_bits(data)))

    def test_an_indexed_sheet_decodes_back_to_its_pixels(self):
        _, sheets = ATLAS
        sheet = sheets[COARSE][0]
        palette, indices = indexed(sheet)
        decoded = unpack_bits(pack_bits(indices))
        raw = sheet.tobytes()
        for position in range(0, len(decoded), 997):
            self.assertEqual(tuple(raw[position * 4:position * 4 + 4]), palette[decoded[position]])


class FreshnessTest(unittest.TestCase):
    def test_the_committed_manifest_and_kotlin_data_match_the_generators(self):
        manifest, sheets = ATLAS
        self.assertEqual(manifest, json.loads((ATLAS_DIR / "pixel-atlas.json").read_text(encoding="utf-8")))
        self.assertEqual(kotlin_source(manifest, sheets), KOTLIN_OUT.read_text(encoding="utf-8"))


if __name__ == "__main__":
    unittest.main()
