import test from "node:test";
import assert from "node:assert/strict";
import { metresPerDegree, sceneMmFromGps, sceneHeadingDeg, viewFromOrientation } from "../web/geo.js";

const MM_PER_METRE = 1000;
const SPHERE_METRES_PER_DEGREE = (Math.PI / 180) * 6371008.8;
const ORIGIN = { origin_latitude: 42.9634, origin_longitude: -85.6681, north_rotation_deg: 0 };

const closeTo = (actual, expected, tolerance, label) =>
  assert.ok(Math.abs(actual - expected) <= tolerance, `${label}: ${actual} vs ${expected} (±${tolerance})`);

test("a degree of latitude is about 111 km and a degree of longitude shrinks with the cosine of latitude", () => {
  const { latitude, longitude } = metresPerDegree(42.9634);
  closeTo(latitude, SPHERE_METRES_PER_DEGREE, SPHERE_METRES_PER_DEGREE * 0.003, "latitude");
  closeTo(longitude, SPHERE_METRES_PER_DEGREE * Math.cos((42.9634 * Math.PI) / 180), SPHERE_METRES_PER_DEGREE * 0.003, "longitude");
});

test("a point north of the origin lands on scene +y when the scene is not rotated", () => {
  const north = sceneMmFromGps(ORIGIN, ORIGIN.origin_latitude + 0.001, ORIGIN.origin_longitude);
  closeTo(north.xMm, 0, 1, "x");
  closeTo(north.yMm, 111.09 * MM_PER_METRE, 300, "y");
});

test("a point east of the origin lands on scene +x when the scene is not rotated", () => {
  const east = sceneMmFromGps(ORIGIN, ORIGIN.origin_latitude, ORIGIN.origin_longitude + 0.001);
  closeTo(east.yMm, 0, 1, "y");
  closeTo(east.xMm, 81.5 * MM_PER_METRE, 300, "x");
});

test("when scene +y points east, a point east of the origin lands on scene +y", () => {
  const rotated = { ...ORIGIN, north_rotation_deg: 90 };
  const east = sceneMmFromGps(rotated, ORIGIN.origin_latitude, ORIGIN.origin_longitude + 0.001);
  closeTo(east.xMm, 0, 1, "x");
  closeTo(east.yMm, 81.5 * MM_PER_METRE, 300, "y");
});

test("when scene +y points east, a point north of the origin lands on scene -x", () => {
  const rotated = { ...ORIGIN, north_rotation_deg: 90 };
  const north = sceneMmFromGps(rotated, ORIGIN.origin_latitude + 0.001, ORIGIN.origin_longitude);
  closeTo(north.xMm, -111.09 * MM_PER_METRE, 300, "x");
  closeTo(north.yMm, 0, 1, "y");
});

test("a true heading becomes a scene heading by subtracting the scene's rotation from north", () => {
  assert.equal(sceneHeadingDeg(90, { ...ORIGIN, north_rotation_deg: 90 }), 0);
  assert.equal(sceneHeadingDeg(10, { ...ORIGIN, north_rotation_deg: 30 }), 340);
  assert.equal(sceneHeadingDeg(123, null), 123);
});

test("an upright phone facing each compass direction reads that heading and a level elevation", () => {
  const facing = (alpha) => viewFromOrientation(alpha, 90, 0);
  [[0, 0], [270, 90], [180, 180], [90, 270]].forEach(([alpha, heading]) => {
    const view = facing(alpha);
    closeTo(view.headingDeg, heading, 1e-9, `alpha ${alpha} heading`);
    closeTo(view.elevationDeg, 0, 1e-9, `alpha ${alpha} elevation`);
  });
});

test("tilting the top of an upright phone back raises the camera elevation by the tilt", () => {
  closeTo(viewFromOrientation(0, 110, 0).elevationDeg, 20, 1e-9, "up 20");
  closeTo(viewFromOrientation(0, 70, 0).elevationDeg, -20, 1e-9, "down 20");
});
