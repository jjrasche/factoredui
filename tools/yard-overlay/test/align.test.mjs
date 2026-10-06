import test from "node:test";
import assert from "node:assert/strict";
import { solveAlignment, applyAlignment, IDENTITY_ALIGNMENT } from "../web/align.js";

const closeTo = (actual, expected, label) => assert.ok(Math.abs(actual - expected) < 1e-9, `${label}: ${actual} vs ${expected}`);

test("with no taps the overlay is left where the sensors put it", () => {
  assert.deepEqual(solveAlignment([]), IDENTITY_ALIGNMENT);
  assert.deepEqual(applyAlignment(IDENTITY_ALIGNMENT, [12, 34]), [12, 34]);
});

test("one tap slides the overlay so the tapped item sits under the tap", () => {
  const alignment = solveAlignment([{ from: [100, 200], to: [130, 190] }]);
  const [x, y] = applyAlignment(alignment, [100, 200]);
  closeTo(x, 130, "x");
  closeTo(y, 190, "y");
  const [shiftedX, shiftedY] = applyAlignment(alignment, [0, 0]);
  closeTo(shiftedX, 30, "other x shifts the same");
  closeTo(shiftedY, -10, "other y shifts the same");
});

test("two taps fix scale, rotation and offset so both items land under their taps", () => {
  const pairs = [
    { from: [0, 0], to: [10, 10] },
    { from: [1, 0], to: [10, 12] },
  ];
  const alignment = solveAlignment(pairs);
  closeTo(alignment.scale, 2, "scale");
  closeTo(alignment.rotationRad, Math.PI / 2, "rotation");
  pairs.forEach(({ from, to }) => {
    const [x, y] = applyAlignment(alignment, from);
    closeTo(x, to[0], "x");
    closeTo(y, to[1], "y");
  });
  const [x, y] = applyAlignment(alignment, [0, 1]);
  closeTo(x, 8, "a third point follows the same turn: x");
  closeTo(y, 10, "a third point follows the same turn: y");
});

test("two taps on the same projected spot cannot fix a scale, so only the offset is kept", () => {
  const alignment = solveAlignment([
    { from: [5, 5], to: [8, 9] },
    { from: [5, 5], to: [20, 20] },
  ]);
  assert.equal(alignment.scale, 1);
  assert.equal(alignment.rotationRad, 0);
});

test("only the first two taps are used", () => {
  const pairs = [
    { from: [0, 0], to: [0, 0] },
    { from: [1, 0], to: [2, 0] },
    { from: [0, 1], to: [100, 100] },
  ];
  closeTo(solveAlignment(pairs).scale, 2, "scale");
});
