const SURFACE_COLOURS = {
  grass: "rgba(80, 170, 70, 0.35)",
  lawn: "rgba(80, 170, 70, 0.35)",
  gravel: "rgba(190, 180, 150, 0.45)",
  pavement: "rgba(150, 150, 160, 0.45)",
  soil: "rgba(140, 100, 60, 0.4)",
  forest: "rgba(30, 110, 50, 0.4)",
  canopy: "rgba(30, 110, 50, 0.4)",
  water: "rgba(60, 130, 230, 0.45)",
  building: "rgba(200, 90, 70, 0.45)",
};

const TYPE_HUES = 360;

function hashText(text) {
  let hash = 0;
  for (const letter of text) hash = (hash * 31 + letter.charCodeAt(0)) % 1_000_003;
  return hash;
}

export function surfaceColour(className) {
  return SURFACE_COLOURS[className] ?? `hsla(${hashText(className) % TYPE_HUES}, 55%, 55%, 0.4)`;
}

export function typeColour(typeId, alpha) {
  return `hsla(${hashText(typeId) % TYPE_HUES}, 70%, 50%, ${alpha})`;
}

export const WATER_FILL = "rgba(60, 130, 230, 0.55)";
export const FLOW_STROKE = "rgba(120, 200, 255, 0.95)";
export const BOUNDARY_STROKE = "rgba(255, 220, 60, 0.95)";
