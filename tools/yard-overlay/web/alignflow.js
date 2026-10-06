export const ALIGN_PAIRS_NEEDED = 2;

export function nearestAnchor(anchors, point, maxDistance) {
  let nearest = null;
  let nearestDistance = maxDistance;
  anchors.forEach((anchor) => {
    const distance = Math.hypot(anchor.screen[0] - point[0], anchor.screen[1] - point[1]);
    if (distance <= nearestDistance) {
      nearest = anchor;
      nearestDistance = distance;
    }
  });
  return nearest;
}

export function beginAlignFlow() {
  return { step: "pickAnchor", pairs: [], pendingAnchor: null, message: "Tap a tree or footprint in the overlay that you can also see in the photo." };
}

export function finishAlignFlow(flow) {
  return { ...flow, step: "idle", pendingAnchor: null, message: "" };
}

function pickAnchorStep(flow, point, anchors, reachPx) {
  const anchor = nearestAnchor(anchors, point, reachPx);
  if (!anchor) return { ...flow, message: "Nothing in the overlay is near that tap; tap closer to a tree or footprint." };
  return { ...flow, step: "placeOnPhoto", pendingAnchor: anchor, message: `Now tap where that ${anchor.label.toLowerCase()} really is in the photo.` };
}

function placeOnPhotoStep(flow, point) {
  const anchor = flow.pendingAnchor;
  const pair = { from: [...anchor.screen], to: point, anchorId: anchor.id, anchorLayer: anchor.layer, anchorLabel: anchor.label };
  const next = { ...flow, pairs: [...flow.pairs, pair], pendingAnchor: null };
  if (next.pairs.length >= ALIGN_PAIRS_NEEDED) return finishAlignFlow(next);
  return { ...next, step: "pickAnchor", message: "Pick a second item, as far from the first as you can." };
}

export function tapDuringAlign(flow, point, anchors, reachPx) {
  if (flow.step === "pickAnchor") return pickAnchorStep(flow, point, anchors, reachPx);
  if (flow.step === "placeOnPhoto") return placeOnPhotoStep(flow, point);
  return flow;
}
