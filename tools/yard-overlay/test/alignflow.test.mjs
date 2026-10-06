import test from "node:test";
import assert from "node:assert/strict";
import { beginAlignFlow, tapDuringAlign, finishAlignFlow, nearestAnchor, ALIGN_PAIRS_NEEDED } from "../web/alignflow.js";

const anchors = [
  { id: "oak-1", layer: "instances", label: "Oak", screen: [100, 100] },
  { id: "shed-1", layer: "footprints", label: "Shed", screen: [300, 120] },
];

test("the nearest anchor within reach of a tap is found, and none beyond it", () => {
  assert.equal(nearestAnchor(anchors, [110, 105], 40).id, "oak-1");
  assert.equal(nearestAnchor(anchors, [200, 300], 40), null);
  assert.equal(nearestAnchor([], [0, 0], 40), null);
});

test("an alignment starts by asking for an overlay item", () => {
  const flow = beginAlignFlow();
  assert.equal(flow.step, "pickAnchor");
  assert.deepEqual(flow.pairs, []);
});

test("tapping an overlay item moves on to asking where it really is", () => {
  const flow = tapDuringAlign(beginAlignFlow(), [105, 102], anchors, 40);
  assert.equal(flow.step, "placeOnPhoto");
  assert.equal(flow.pendingAnchor.id, "oak-1");
});

test("tapping empty space while picking stays on the pick step and says why", () => {
  const flow = tapDuringAlign(beginAlignFlow(), [200, 400], anchors, 40);
  assert.equal(flow.step, "pickAnchor");
  assert.match(flow.message, /closer/);
});

test("tapping the photo records the item's projected spot against the tapped spot", () => {
  const picked = tapDuringAlign(beginAlignFlow(), [105, 102], anchors, 40);
  const placed = tapDuringAlign(picked, [130, 90], anchors, 40);
  assert.equal(placed.step, "pickAnchor");
  assert.deepEqual(placed.pairs, [{ from: [100, 100], to: [130, 90], anchorId: "oak-1", anchorLayer: "instances", anchorLabel: "Oak" }]);
});

test("after the second photo tap the flow is done", () => {
  let flow = beginAlignFlow();
  flow = tapDuringAlign(flow, [100, 100], anchors, 40);
  flow = tapDuringAlign(flow, [130, 90], anchors, 40);
  flow = tapDuringAlign(flow, [300, 120], anchors, 40);
  flow = tapDuringAlign(flow, [320, 110], anchors, 40);
  assert.equal(flow.pairs.length, ALIGN_PAIRS_NEEDED);
  assert.equal(flow.step, "idle");
});

test("finishing early keeps the taps made so far", () => {
  let flow = tapDuringAlign(beginAlignFlow(), [100, 100], anchors, 40);
  flow = tapDuringAlign(flow, [130, 90], anchors, 40);
  const finished = finishAlignFlow(flow);
  assert.equal(finished.step, "idle");
  assert.equal(finished.pairs.length, 1);
});

test("an idle flow ignores taps", () => {
  const idle = finishAlignFlow(beginAlignFlow());
  assert.equal(tapDuringAlign(idle, [100, 100], anchors, 40), idle);
});
