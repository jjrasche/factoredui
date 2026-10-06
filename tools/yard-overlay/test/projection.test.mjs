import test from "node:test";
import assert from "node:assert/strict";
import { projectPoint, projectPolygon, focalLengthPx } from "../web/projection.js";

const level = { position: [0, 0, 0], yawDeg: 0, pitchDeg: 0, hfovDeg: 90, width: 1000, height: 500 };

const closeTo = (actual, expected, label) => assert.ok(Math.abs(actual - expected) < 1e-6, `${label}: ${actual} vs ${expected}`);

test("a 90 degree field of view over 1000 px has a 500 px focal length", () => {
  closeTo(focalLengthPx(level), 500, "focal length");
});

test("a point straight ahead lands in the middle of the picture", () => {
  const spot = projectPoint(level, [0, 1000, 0]);
  closeTo(spot.x, 500, "x");
  closeTo(spot.y, 250, "y");
});

test("a point to the right lands right of centre, and a point above lands above centre", () => {
  const right = projectPoint(level, [1000, 1000, 0]);
  closeTo(right.x, 1000, "right x");
  const above = projectPoint(level, [0, 1000, 500]);
  closeTo(above.y, 0, "above y");
});

test("a point behind the camera does not project", () => {
  assert.equal(projectPoint(level, [0, -1000, 0]), null);
});

test("turning the camera clockwise to yaw 90 makes scene +x the way ahead", () => {
  const spot = projectPoint({ ...level, yawDeg: 90 }, [1000, 0, 0]);
  closeTo(spot.x, 500, "x");
  closeTo(spot.y, 250, "y");
});

test("tilting the camera up 45 degrees puts a point 45 degrees up the sight line in the middle", () => {
  const spot = projectPoint({ ...level, pitchDeg: 45 }, [0, 1000, 1000]);
  closeTo(spot.x, 500, "x");
  closeTo(spot.y, 250, "y");
});

test("the camera height lowers everything else: a ground point under the horizon lands below centre", () => {
  const spot = projectPoint({ ...level, position: [0, 0, 1500] }, [0, 3000, 0]);
  closeTo(spot.y, 250 + 500 * (1500 / 3000), "y");
});

test("a polygon with a corner behind the camera is cut at the near plane instead of dropped", () => {
  const groundSquare = [[-1000, -1000, -1500], [1000, -1000, -1500], [1000, 3000, -1500], [-1000, 3000, -1500]];
  const outline = projectPolygon(level, groundSquare);
  assert.ok(outline && outline.length >= 3, "the part ahead of the camera is kept");
  outline.forEach(([x, y]) => assert.ok(Number.isFinite(x) && Number.isFinite(y), "every kept corner is finite"));
});

test("a polygon wholly behind the camera is dropped", () => {
  const behind = [[0, -1000, 0], [1000, -1000, 0], [1000, -2000, 0]];
  assert.equal(projectPolygon(level, behind), null);
});

test("a polygon wholly ahead keeps every corner", () => {
  const ahead = [[0, 1000, 0], [1000, 1000, 0], [1000, 2000, 0]];
  assert.equal(projectPolygon(level, ahead).length, 3);
});
