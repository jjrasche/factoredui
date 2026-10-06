import { applyAlignment } from "./align.js";
import { projectPoint, projectPolygon, projectPolyline } from "./projection.js";
import { groundHeightAt, tileSizeMm } from "./twin.js";
import { surfaceColour, typeColour, WATER_FILL, FLOW_STROKE, BOUNDARY_STROKE } from "./palette.js";

const MAX_SURFACE_DISTANCE_MM = 150000;
const CROWN_RING_POINTS = 16;
const CROWN_HEIGHT_SHARE = 0.7;
const DEFAULT_TREE_HEIGHT_MM = 6000;
const DEFAULT_CROWN_SHARE = 0.25;
const FLOW_LIFT_MM = 20;

export function cameraFor({ twin, positionMm, heightAboveGroundMm, yawDeg, pitchDeg, hfovDeg, width, height }) {
  const groundMm = groundHeightAt(twin, positionMm[0], positionMm[1]);
  return { position: [positionMm[0], positionMm[1], groundMm + heightAboveGroundMm], yawDeg, pitchDeg, hfovDeg, width, height };
}

const horizontalDistanceSquared = (camera, x, y) => (x - camera.position[0]) ** 2 + (y - camera.position[1]) ** 2;

const typeOf = (twin, typeId) => twin.types.find((type) => type.id === typeId);

function tileCorners(twin, column, row, columns, rows) {
  const tile = tileSizeMm(twin);
  const x0 = twin.window.x0 + column * tile;
  const y0 = twin.window.y0 + row * tile;
  const x1 = x0 + columns * tile;
  const y1 = y0 + rows * tile;
  return [[x0, y0], [x1, y0], [x1, y1], [x0, y1]];
}

const liftToGround = (twin, corners, extraMm) => corners.map(([x, y]) => [x, y, groundHeightAt(twin, x, y) + extraMm]);

function surfaceShapes(twin, camera) {
  const layer = twin.surface;
  const classNames = new Map(layer.classes.map((entry) => [entry.id, entry.name]));
  const shapes = [];
  layer.class_ids.forEach((classId, index) => {
    const column = index % layer.cols;
    const row = Math.floor(index / layer.cols);
    const corners = tileCorners(twin, column, row, 1, 1);
    const centre = [(corners[0][0] + corners[2][0]) / 2, (corners[0][1] + corners[2][1]) / 2];
    if (horizontalDistanceSquared(camera, centre[0], centre[1]) > MAX_SURFACE_DISTANCE_MM ** 2) return;
    const points = projectPolygon(camera, liftToGround(twin, corners, 0));
    if (points) shapes.push({ layer: "surface", kind: "polygon", id: `cell-${column},${row}`, points, fill: surfaceColour(classNames.get(classId) ?? `class-${classId}`), stroke: null });
  });
  return shapes;
}

function waterShapes(twin, camera) {
  const shapes = [];
  twin.water.ponds.forEach((pond) => {
    pond.tiles.forEach((pondTile) => {
      const corners = tileCorners(twin, pondTile.col, pondTile.row, 1, 1).map(([x, y]) => [x, y, pond.level_mm]);
      const points = projectPolygon(camera, corners);
      if (points) shapes.push({ layer: "water", kind: "polygon", id: pond.id, points, fill: WATER_FILL, stroke: null });
    });
  });
  twin.water.flow.forEach((line) => {
    const lifted = line.points_mm.map(([x, y]) => [x, y, groundHeightAt(twin, x, y) + FLOW_LIFT_MM]);
    projectPolyline(camera, lifted).forEach((points) => shapes.push({ layer: "water", kind: "polyline", id: line.id, points, stroke: FLOW_STROKE, fill: null }));
  });
  return shapes;
}

function footprintHeightMm(twin, item) {
  return item.height_mm ?? typeOf(twin, item.type)?.height_mm ?? null;
}

function footprintShapes(twin, camera, item) {
  const floorCorners = liftToGround(twin, tileCorners(twin, item.col, item.row, item.width, item.height), 0);
  const floor = projectPolygon(camera, floorCorners);
  const shapes = [];
  const fill = typeColour(item.type, 0.35);
  const stroke = typeColour(item.type, 0.95);
  if (floor) shapes.push({ layer: "footprints", kind: "polygon", id: item.id, points: floor, fill, stroke });
  const heightMm = footprintHeightMm(twin, item);
  if (heightMm === null) return shapes;
  const roofCorners = floorCorners.map(([x, y, z]) => [x, y, z + heightMm]);
  const roof = projectPolygon(camera, roofCorners);
  if (roof) shapes.push({ layer: "footprints", kind: "polygon", id: item.id, points: roof, fill: null, stroke });
  floorCorners.forEach((corner, index) => {
    projectPolyline(camera, [corner, roofCorners[index]]).forEach((points) => shapes.push({ layer: "footprints", kind: "polyline", id: item.id, points, stroke, fill: null }));
  });
  return shapes;
}

function footprintCentre(twin, item) {
  const [first, , third] = tileCorners(twin, item.col, item.row, item.width, item.height);
  const x = (first[0] + third[0]) / 2;
  const y = (first[1] + third[1]) / 2;
  return [x, y, groundHeightAt(twin, x, y)];
}

function boundaryShapes(twin, camera) {
  return twin.boundaries.polygons.flatMap((polygon) => {
    const ring = [...polygon.points_mm, polygon.points_mm[0]].map(([x, y]) => [x, y, groundHeightAt(twin, x, y)]);
    return projectPolyline(camera, ring).map((points) => ({ layer: "boundaries", kind: "polyline", id: polygon.id, points, stroke: BOUNDARY_STROKE, fill: null }));
  });
}

function treeBaseMm(twin, tree) {
  return twin.ground ? groundHeightAt(twin, tree.x_mm, tree.y_mm) : tree.z_mm ?? 0;
}

function crownDisc(camera, tree, centreZ, radiusMm) {
  const towardTreeX = tree.x_mm - camera.position[0];
  const towardTreeY = tree.y_mm - camera.position[1];
  const distance = Math.hypot(towardTreeX, towardTreeY) || 1;
  const acrossX = towardTreeY / distance;
  const acrossY = -towardTreeX / distance;
  return Array.from({ length: CROWN_RING_POINTS }, (_, index) => {
    const angle = (index / CROWN_RING_POINTS) * 2 * Math.PI;
    return [tree.x_mm + radiusMm * Math.cos(angle) * acrossX, tree.y_mm + radiusMm * Math.cos(angle) * acrossY, centreZ + radiusMm * Math.sin(angle)];
  });
}

function treeShapes(twin, camera, tree) {
  const baseZ = treeBaseMm(twin, tree);
  const heightMm = tree.height_mm ?? typeOf(twin, tree.type)?.height_mm ?? DEFAULT_TREE_HEIGHT_MM;
  const crownMm = tree.crown_radius_mm ?? heightMm * DEFAULT_CROWN_SHARE;
  const stroke = typeColour(tree.type, 0.95);
  const shapes = [];
  projectPolyline(camera, [[tree.x_mm, tree.y_mm, baseZ], [tree.x_mm, tree.y_mm, baseZ + heightMm]]).forEach((points) =>
    shapes.push({ layer: "instances", kind: "polyline", id: tree.id, points, stroke, fill: null }),
  );
  const crown = projectPolygon(camera, crownDisc(camera, tree, baseZ + heightMm * CROWN_HEIGHT_SHARE, crownMm));
  if (crown) shapes.push({ layer: "instances", kind: "polygon", id: tree.id, points: crown, fill: typeColour(tree.type, 0.3), stroke });
  return shapes;
}

const farToNear = (camera, items, positionOf) =>
  [...items].sort((a, b) => horizontalDistanceSquared(camera, ...positionOf(b)) - horizontalDistanceSquared(camera, ...positionOf(a)));

function anchorFor(camera, layer, id, label, point) {
  const spot = projectPoint(camera, point);
  return spot ? { id, layer, label, screen: [spot.x, spot.y] } : null;
}

function footprintLayer(twin, camera) {
  const items = farToNear(camera, twin.footprints.items, (item) => footprintCentre(twin, item));
  return {
    shapes: items.flatMap((item) => footprintShapes(twin, camera, item)),
    anchors: items.map((item) => anchorFor(camera, "footprints", item.id, typeOf(twin, item.type)?.label ?? item.type, footprintCentre(twin, item))),
  };
}

function instanceLayer(twin, camera) {
  const items = farToNear(camera, twin.instances.items, (tree) => [tree.x_mm, tree.y_mm]);
  return {
    shapes: items.flatMap((tree) => treeShapes(twin, camera, tree)),
    anchors: items.map((tree) => anchorFor(camera, "instances", tree.id, typeOf(twin, tree.type)?.label ?? tree.type, [tree.x_mm, tree.y_mm, treeBaseMm(twin, tree)])),
  };
}

const LAYER_BUILDERS = {
  surface: (twin, camera) => ({ shapes: surfaceShapes(twin, camera), anchors: [] }),
  water: (twin, camera) => ({ shapes: waterShapes(twin, camera), anchors: [] }),
  footprints: footprintLayer,
  boundaries: (twin, camera) => ({ shapes: boundaryShapes(twin, camera), anchors: [] }),
  instances: instanceLayer,
};

export function buildDrawPlan(twin, camera, enabledLayers) {
  const built = Object.keys(LAYER_BUILDERS)
    .filter((layer) => enabledLayers.includes(layer) && twin[layer])
    .map((layer) => LAYER_BUILDERS[layer](twin, camera));
  return {
    shapes: built.flatMap((part) => part.shapes),
    anchors: built.flatMap((part) => part.anchors).filter(Boolean),
  };
}

export function alignPlan(plan, alignment) {
  return {
    shapes: plan.shapes.map((shape) => ({ ...shape, points: shape.points.map((point) => applyAlignment(alignment, point)) })),
    anchors: plan.anchors.map((anchor) => ({ ...anchor, screen: applyAlignment(alignment, anchor.screen) })),
  };
}
