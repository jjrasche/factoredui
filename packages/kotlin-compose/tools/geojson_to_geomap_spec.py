"""Convert a parcels GeoJSON (common-ground jolly-rd shape) into a geomap SDUI spec fixture.

Usage: python geojson_to_geomap_spec.py <parcels.geojson> <out_spec.json>

Fill colors follow common-ground's map score ramp (jolly-rd.html SCORE_RAMP),
with the leaflet fillOpacity baked into the alpha channel so the headless
render is visually comparable to the map receipts.
"""

import json
import sys

SCORE_RAMP = [
    (0.00, "#9aa7b4"),
    (0.05, "#f5d76e"),
    (0.20, "#f39c12"),
    (0.50, "#e74c3c"),
    (0.90, "#c0392b"),
]
UNSCORED_FILL = "#d5dbe1"
STROKE = "#2c3e50"
STROKE_WIDTH = 1.2


def score_color(score):
    if score is None:
        return UNSCORED_FILL
    color = SCORE_RAMP[0][1]
    for threshold, ramp_color in SCORE_RAMP:
        if score >= threshold:
            color = ramp_color
    return color


def fill_with_alpha(score):
    opacity = 0.15 if score is None else min(0.25 + score, 0.75)
    alpha = round(opacity * 255)
    return "#%02X%s" % (alpha, score_color(score).lstrip("#").upper())


def feature_rings(geometry):
    polygons = geometry["coordinates"] if geometry["type"] == "MultiPolygon" else [geometry["coordinates"]]
    rings = []
    for polygon in polygons:
        outer = polygon[0]
        rings.append([[round(lon, 7), round(lat, 7)] for lon, lat in outer])
    return rings


def convert(geojson_path, spec_path):
    with open(geojson_path) as geojson_file:
        collection = json.load(geojson_file)
    features = []
    for feature in collection["features"]:
        properties = feature.get("properties", {})
        score = properties.get("arbitrage_score_high")
        features.append({
            "id": properties.get("parcel_id", "unknown"),
            "rings": feature_rings(feature["geometry"]),
            "fill": fill_with_alpha(score),
            "stroke": STROKE,
            "stroke_width": STROKE_WIDTH,
            "label": properties.get("site_address"),
        })
    spec = {
        "spec_version": 1,
        "renderer_min": 1,
        "root": {
            "id": "jolly-rd-parcels",
            "type": "geomap",
            "props": {
                "layers": [{
                    "id": "parcels",
                    "kind": "fill",
                    "visible": True,
                    "features": features,
                }],
                "on_feature_tap": "map.featureTapped",
                "on_viewport_changed": "map.viewportChanged",
            },
        },
    }
    with open(spec_path, "w") as spec_file:
        json.dump(spec, spec_file, separators=(",", ":"))
    print(f"wrote {spec_path}: {len(features)} features")


if __name__ == "__main__":
    convert(sys.argv[1], sys.argv[2])
