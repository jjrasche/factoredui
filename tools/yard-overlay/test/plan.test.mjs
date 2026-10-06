import test from "node:test";
import assert from "node:assert/strict";
import { buildDrawPlan, cameraFor, alignPlan } from "../web/plan.js";

const tile = 1000;

const yardTwin = () => ({
  units: "mm",
  cols: 4,
  rows: 4,
  window: { x0: 0, y0: 0, x1: 4 * tile, y1: 4 * tile },
  types: [
    { id: "shed", label: "Shed", height_mm: 2400 },
    { id: "oak", label: "Oak", height_mm: 9000 },
  ],
  surface: {
    version: 1,
    cols: 4,
    rows: 4,
    classes: [{ id: 1, name: "grass" }, { id: 2, name: "gravel" }],
    class_ids: [1, 1, 1, 1, 1, 2, 2, 1, 1, 2, 2, 1, 1, 1, 1, 1],
  },
  footprints: { version: 1, items: [{ id: "shed-1", type: "shed", col: 1, row: 1, width: 2, height: 1, height_mm: null }] },
  instances: {
    version: 1,
    items: [
      { id: "near-oak", type: "oak", x_mm: 3000, y_mm: 2000, z_mm: 0, height_mm: 6000, crown_radius_mm: 1500 },
      { id: "far-oak", type: "oak", x_mm: 1000, y_mm: 3500, z_mm: 0, height_mm: 6000, crown_radius_mm: 1500 },
    ],
  },
  water: { version: 1, ponds: [{ id: "p1", level_mm: 0, tiles: [{ col: 0, row: 3, depth_mm: 300 }] }], flow: [{ id: "f1", points_mm: [[0, 0], [2000, 2000]], kind: "swale" }] },
});

const southOfTheYard = () =>
  cameraFor({ twin: yardTwin(), positionMm: [2000, -6000], heightAboveGroundMm: 1600, yawDeg: 0, pitchDeg: 0, hfovDeg: 70, width: 800, height: 600 });

const everyLayer = ["surface", "water", "footprints", "boundaries", "instances"];

test("the camera stands at the given height above the ground under it", () => {
  const twin = { ...yardTwin(), ground: { version: 1, heights_mm: Array(25).fill(500) } };
  const camera = cameraFor({ twin, positionMm: [2000, -6000], heightAboveGroundMm: 1600, yawDeg: 0, pitchDeg: 0, hfovDeg: 70, width: 800, height: 600 });
  assert.equal(camera.position[2], 2100);
});

test("every enabled layer contributes shapes and a disabled layer contributes none", () => {
  const plan = buildDrawPlan(yardTwin(), southOfTheYard(), everyLayer);
  const drawn = new Set(plan.shapes.map((shape) => shape.layer));
  assert.deepEqual([...drawn].sort(), ["footprints", "instances", "surface", "water"]);
  const withoutTrees = buildDrawPlan(yardTwin(), southOfTheYard(), ["surface", "footprints"]);
  assert.ok(!withoutTrees.shapes.some((shape) => shape.layer === "instances"));
  assert.ok(!withoutTrees.anchors.some((anchor) => anchor.layer === "instances"));
});

test("land cover is one filled polygon per cell, coloured by its class", () => {
  const plan = buildDrawPlan(yardTwin(), southOfTheYard(), ["surface"]);
  assert.equal(plan.shapes.length, 16);
  const colours = new Set(plan.shapes.map((shape) => shape.fill));
  assert.equal(colours.size, 2);
});

test("a footprint is a four-cornered polygon at its tiles and offers its centre as an anchor", () => {
  const plan = buildDrawPlan(yardTwin(), southOfTheYard(), ["footprints"]);
  const floor = plan.shapes.find((shape) => shape.kind === "polygon" && shape.id === "shed-1");
  assert.equal(floor.points.length, 4);
  const anchor = plan.anchors.find((candidate) => candidate.id === "shed-1");
  assert.equal(anchor.label, "Shed");
});

test("a footprint with a height, from the item or its type, is drawn as a box with vertical edges", () => {
  const plan = buildDrawPlan(yardTwin(), southOfTheYard(), ["footprints"]);
  assert.ok(plan.shapes.some((shape) => shape.kind === "polyline" && shape.id === "shed-1"), "vertical edges");
  assert.ok(plan.shapes.filter((shape) => shape.id === "shed-1" && shape.kind === "polygon").length >= 2, "floor and roof");
});

test("a tree is a trunk line plus a crown ring and anchors at its foot", () => {
  const plan = buildDrawPlan(yardTwin(), southOfTheYard(), ["instances"]);
  const near = plan.shapes.filter((shape) => shape.id === "near-oak");
  assert.deepEqual(near.map((shape) => shape.kind).sort(), ["polygon", "polyline"]);
  assert.equal(plan.anchors.filter((anchor) => anchor.layer === "instances").length, 2);
});

test("trees are drawn far to near so a near tree covers a far one", () => {
  const plan = buildDrawPlan(yardTwin(), southOfTheYard(), ["instances"]);
  const order = plan.shapes.map((shape) => shape.id);
  assert.ok(order.indexOf("far-oak") < order.indexOf("near-oak"));
});

test("an item wholly behind the camera is not drawn and offers no anchor", () => {
  const behind = cameraFor({ twin: yardTwin(), positionMm: [2000, 9000], heightAboveGroundMm: 1600, yawDeg: 0, pitchDeg: 0, hfovDeg: 70, width: 800, height: 600 });
  const plan = buildDrawPlan(yardTwin(), behind, everyLayer);
  assert.equal(plan.shapes.length, 0);
  assert.equal(plan.anchors.length, 0);
});

test("an alignment moves every shape point and every anchor by the same transform", () => {
  const plan = buildDrawPlan(yardTwin(), southOfTheYard(), ["footprints"]);
  const shifted = alignPlan(plan, { scale: 1, rotationRad: 0, translateX: 10, translateY: -5 });
  assert.equal(shifted.shapes[0].points[0][0], plan.shapes[0].points[0][0] + 10);
  assert.equal(shifted.anchors[0].screen[1], plan.anchors[0].screen[1] - 5);
});
