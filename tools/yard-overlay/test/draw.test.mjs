import test from "node:test";
import assert from "node:assert/strict";
import { paintPlan, paintMarkers } from "../web/draw.js";

function recordingContext() {
  const calls = [];
  const record = (name) => (...args) => calls.push([name, ...args]);
  const context = { calls, fillStyle: "", strokeStyle: "", lineWidth: 0, font: "", textBaseline: "" };
  ["beginPath", "moveTo", "lineTo", "closePath", "fill", "stroke", "arc", "fillText", "strokeText"].forEach((name) => {
    context[name] = record(name);
  });
  return context;
}

const names = (context) => context.calls.map(([name]) => name);

test("a filled polygon is traced, closed and filled", () => {
  const context = recordingContext();
  paintPlan(context, { shapes: [{ kind: "polygon", points: [[0, 0], [10, 0], [10, 10]], fill: "red", stroke: null }], anchors: [] }, 1);
  assert.deepEqual(names(context), ["beginPath", "moveTo", "lineTo", "lineTo", "closePath", "fill"]);
  assert.equal(context.fillStyle, "red");
});

test("a polygon with both a fill and an outline is filled then stroked", () => {
  const context = recordingContext();
  paintPlan(context, { shapes: [{ kind: "polygon", points: [[0, 0], [10, 0], [10, 10]], fill: "red", stroke: "blue" }], anchors: [] }, 2);
  assert.deepEqual(names(context).slice(-2), ["fill", "stroke"]);
  assert.equal(context.lineWidth, 4);
});

test("a polyline is traced open and stroked", () => {
  const context = recordingContext();
  paintPlan(context, { shapes: [{ kind: "polyline", points: [[0, 0], [5, 5]], stroke: "blue", fill: null }], anchors: [] }, 1);
  assert.deepEqual(names(context), ["beginPath", "moveTo", "lineTo", "stroke"]);
});

test("shapes are painted in the order the plan lists them", () => {
  const context = recordingContext();
  const shapes = [
    { kind: "polygon", points: [[0, 0], [1, 0], [1, 1]], fill: "first", stroke: null },
    { kind: "polygon", points: [[0, 0], [1, 0], [1, 1]], fill: "second", stroke: null },
  ];
  const fills = [];
  const original = context.fill;
  context.fill = () => fills.push(context.fillStyle);
  paintPlan(context, { shapes, anchors: [] }, 1);
  assert.deepEqual(fills, ["first", "second"]);
  assert.equal(typeof original, "function");
});

test("each marker is a ring with its label beside it", () => {
  const context = recordingContext();
  paintMarkers(context, [{ screen: [40, 50], label: "Oak" }], 1);
  assert.ok(names(context).includes("arc"));
  assert.deepEqual(context.calls.find(([name]) => name === "fillText").slice(1, 2), ["Oak"]);
});
