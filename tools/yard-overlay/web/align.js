export const IDENTITY_ALIGNMENT = Object.freeze({ scale: 1, rotationRad: 0, translateX: 0, translateY: 0 });

const SAME_SPOT_PX = 1e-6;

export function applyAlignment(alignment, point) {
  const cosine = alignment.scale * Math.cos(alignment.rotationRad);
  const sine = alignment.scale * Math.sin(alignment.rotationRad);
  return [cosine * point[0] - sine * point[1] + alignment.translateX, sine * point[0] + cosine * point[1] + alignment.translateY];
}

function slideOnto(pair) {
  return { scale: 1, rotationRad: 0, translateX: pair.to[0] - pair.from[0], translateY: pair.to[1] - pair.from[1] };
}

function turnAndScaleBetween(first, second) {
  const fromX = second.from[0] - first.from[0];
  const fromY = second.from[1] - first.from[1];
  const toX = second.to[0] - first.to[0];
  const toY = second.to[1] - first.to[1];
  const fromLengthSquared = fromX * fromX + fromY * fromY;
  if (fromLengthSquared < SAME_SPOT_PX) return null;
  const realPart = (toX * fromX + toY * fromY) / fromLengthSquared;
  const imaginaryPart = (toY * fromX - toX * fromY) / fromLengthSquared;
  return { scale: Math.hypot(realPart, imaginaryPart), rotationRad: Math.atan2(imaginaryPart, realPart) };
}

export function solveAlignment(pairs) {
  if (pairs.length === 0) return IDENTITY_ALIGNMENT;
  const [first, second] = pairs;
  const turn = second ? turnAndScaleBetween(first, second) : null;
  if (!turn || turn.scale === 0) return slideOnto(first);
  const unplaced = { ...turn, translateX: 0, translateY: 0 };
  const [movedX, movedY] = applyAlignment(unplaced, first.from);
  return { ...turn, translateX: first.to[0] - movedX, translateY: first.to[1] - movedY };
}
