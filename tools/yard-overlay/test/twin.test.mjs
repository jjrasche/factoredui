import test from "node:test";
import assert from "node:assert/strict";
import { parseTwin, availableLayers, groundHeightAt, tileSizeMm, layerVersions } from "../web/twin.js";

const baseTwin = () => ({
  units: "mm",
  cols: 2,
  rows: 2,
  window: { x0: 0, y0: 0, x1: 2000, y1: 2000 },
  types: [],
});

const withGround = () => ({
  ...baseTwin(),
  ground: { version: 3, heights_mm: [0, 100, 200, 0, 100, 200, 0, 100, 200] },
});

test("a twin in millimetres with a window and a grid parses", () => {
  const twin = parseTwin(JSON.stringify(baseTwin()));
  assert.equal(twin.cols, 2);
});

test("a twin in any other unit is refused with the unit named", () => {
  assert.throws(() => parseTwin(JSON.stringify({ ...baseTwin(), units: "ft" })), /millimetres.*ft/);
});

test("a twin without a window is refused", () => {
  const { window: _, ...noWindow } = baseTwin();
  assert.throws(() => parseTwin(JSON.stringify(noWindow)), /window/);
});

test("text that is not JSON is refused as not a twin", () => {
  assert.throws(() => parseTwin("<html>"), /not valid JSON/);
});

test("only layers that hold something can be toggled", () => {
  const twin = {
    ...baseTwin(),
    surface: { version: 1, class_ids: [1], classes: [{ id: 1, name: "grass" }] },
    footprints: { version: 1, items: [] },
    instances: { version: 2, items: [{ id: "t1" }] },
    water: { version: 1, ponds: [], flow: [] },
  };
  assert.deepEqual(availableLayers(twin), ["surface", "instances"]);
});

test("the tile size is the window width over the column count", () => {
  assert.equal(tileSizeMm(baseTwin()), 1000);
});

test("ground height is read between vertices by bilinear interpolation", () => {
  const twin = withGround();
  assert.equal(groundHeightAt(twin, 0, 0), 0);
  assert.equal(groundHeightAt(twin, 1000, 0), 100);
  assert.equal(groundHeightAt(twin, 500, 0), 50);
  assert.equal(groundHeightAt(twin, 1500, 1000), 150);
});

test("ground height outside the window holds the nearest edge value", () => {
  const twin = withGround();
  assert.equal(groundHeightAt(twin, -5000, 0), 0);
  assert.equal(groundHeightAt(twin, 9000, 0), 200);
});

test("a twin with no ground layer is flat at zero", () => {
  assert.equal(groundHeightAt(baseTwin(), 700, 700), 0);
});

test("each layer's version is reported so a capture can say which twin it was drawn from", () => {
  const twin = { ...withGround(), instances: { version: 9, items: [{ id: "t" }] } };
  assert.deepEqual(layerVersions(twin), { ground: 3, instances: 9 });
});
