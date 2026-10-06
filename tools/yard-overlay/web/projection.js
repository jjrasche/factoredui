const DEGREES_TO_RADIANS = Math.PI / 180;
const NEAR_PLANE_MM = 100;

export function focalLengthPx(camera) {
  return camera.width / 2 / Math.tan((camera.hfovDeg * DEGREES_TO_RADIANS) / 2);
}

function cameraBasis(camera) {
  const yaw = camera.yawDeg * DEGREES_TO_RADIANS;
  const pitch = camera.pitchDeg * DEGREES_TO_RADIANS;
  return {
    right: [Math.cos(yaw), -Math.sin(yaw), 0],
    up: [-Math.sin(yaw) * Math.sin(pitch), -Math.cos(yaw) * Math.sin(pitch), Math.cos(pitch)],
    forward: [Math.sin(yaw) * Math.cos(pitch), Math.cos(yaw) * Math.cos(pitch), Math.sin(pitch)],
  };
}

const dot = (a, b) => a[0] * b[0] + a[1] * b[1] + a[2] * b[2];

function toCameraSpace(camera, basis, point) {
  const offset = [point[0] - camera.position[0], point[1] - camera.position[1], point[2] - camera.position[2]];
  return [dot(offset, basis.right), dot(offset, basis.up), dot(offset, basis.forward)];
}

function toPicture(camera, focal, cameraPoint) {
  return [camera.width / 2 + (focal * cameraPoint[0]) / cameraPoint[2], camera.height / 2 - (focal * cameraPoint[1]) / cameraPoint[2]];
}

export function projectPoint(camera, point) {
  const cameraPoint = toCameraSpace(camera, cameraBasis(camera), point);
  if (cameraPoint[2] < NEAR_PLANE_MM) return null;
  const [x, y] = toPicture(camera, focalLengthPx(camera), cameraPoint);
  return { x, y, depthMm: cameraPoint[2] };
}

function crossNearPlane(inside, outside) {
  const share = (NEAR_PLANE_MM - inside[2]) / (outside[2] - inside[2]);
  return [0, 1, 2].map((axis) => inside[axis] + share * (outside[axis] - inside[axis]));
}

function clipToNearPlane(cameraPoints) {
  const kept = [];
  cameraPoints.forEach((current, index) => {
    const previous = cameraPoints[(index + cameraPoints.length - 1) % cameraPoints.length];
    const isCurrentAhead = current[2] >= NEAR_PLANE_MM;
    const isPreviousAhead = previous[2] >= NEAR_PLANE_MM;
    if (isCurrentAhead !== isPreviousAhead) kept.push(isCurrentAhead ? crossNearPlane(current, previous) : crossNearPlane(previous, current));
    if (isCurrentAhead) kept.push(current);
  });
  return kept;
}

export function projectPolygon(camera, points) {
  const basis = cameraBasis(camera);
  const focal = focalLengthPx(camera);
  const clipped = clipToNearPlane(points.map((point) => toCameraSpace(camera, basis, point)));
  return clipped.length >= 3 ? clipped.map((cameraPoint) => toPicture(camera, focal, cameraPoint)) : null;
}

export function projectPolyline(camera, points) {
  const basis = cameraBasis(camera);
  const focal = focalLengthPx(camera);
  const cameraPoints = points.map((point) => toCameraSpace(camera, basis, point));
  const runs = [];
  let run = [];
  for (let index = 0; index < cameraPoints.length; index += 1) {
    const point = cameraPoints[index];
    if (point[2] >= NEAR_PLANE_MM) {
      run.push(toPicture(camera, focal, point));
    } else if (run.length > 0) {
      runs.push(run);
      run = [];
    }
  }
  if (run.length > 0) runs.push(run);
  return runs.filter((kept) => kept.length >= 2);
}
