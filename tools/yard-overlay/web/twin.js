export const LAYER_ORDER = ["surface", "water", "footprints", "boundaries", "instances"];

export const LAYER_LABELS = {
  surface: "Land cover",
  water: "Water",
  footprints: "Footprints",
  boundaries: "Boundaries",
  instances: "Trees",
};

const LAYER_CONTENT_KEYS = {
  surface: ["class_ids"],
  water: ["ponds", "flow"],
  footprints: ["items"],
  boundaries: ["polygons"],
  instances: ["items"],
};

export function parseTwin(text) {
  let twin;
  try {
    twin = JSON.parse(text);
  } catch {
    throw new Error("the twin is not valid JSON");
  }
  if (twin.units !== "mm") throw new Error(`the twin must be in millimetres, not ${twin.units}`);
  if (!twin.window || !Number.isFinite(twin.cols) || !Number.isFinite(twin.rows)) throw new Error("the twin needs a window, cols and rows");
  return twin;
}

function hasContent(layer, keys) {
  return Boolean(layer) && keys.some((key) => Array.isArray(layer[key]) && layer[key].length > 0);
}

export function availableLayers(twin) {
  return LAYER_ORDER.filter((id) => hasContent(twin[id], LAYER_CONTENT_KEYS[id]));
}

export function tileSizeMm(twin) {
  return (twin.window.x1 - twin.window.x0) / twin.cols;
}

const clamp = (value, low, high) => Math.min(high, Math.max(low, value));

export function groundHeightAt(twin, xMm, yMm) {
  if (!twin.ground) return 0;
  const tile = tileSizeMm(twin);
  const column = clamp((xMm - twin.window.x0) / tile, 0, twin.cols);
  const row = clamp((yMm - twin.window.y0) / tile, 0, twin.rows);
  const column0 = Math.min(Math.floor(column), twin.cols - 1);
  const row0 = Math.min(Math.floor(row), twin.rows - 1);
  const stride = twin.cols + 1;
  const heights = twin.ground.heights_mm;
  const along = column - column0;
  const down = row - row0;
  const top = heights[row0 * stride + column0] * (1 - along) + heights[row0 * stride + column0 + 1] * along;
  const bottom = heights[(row0 + 1) * stride + column0] * (1 - along) + heights[(row0 + 1) * stride + column0 + 1] * along;
  return top * (1 - down) + bottom * down;
}

export function layerVersions(twin) {
  const versions = {};
  ["ground", ...LAYER_ORDER].forEach((id) => {
    if (twin[id] && Number.isFinite(twin[id].version) && (id === "ground" || availableLayers(twin).includes(id))) versions[id] = twin[id].version;
  });
  return versions;
}
