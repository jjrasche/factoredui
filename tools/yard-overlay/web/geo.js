const DEGREES_TO_RADIANS = Math.PI / 180;
const MM_PER_METRE = 1000;

export function metresPerDegree(latitudeDeg) {
  const phi = latitudeDeg * DEGREES_TO_RADIANS;
  return {
    latitude: 111132.92 - 559.82 * Math.cos(2 * phi) + 1.175 * Math.cos(4 * phi),
    longitude: 111412.84 * Math.cos(phi) - 93.5 * Math.cos(3 * phi),
  };
}

export function sceneMmFromGps(georeference, latitudeDeg, longitudeDeg) {
  const perDegree = metresPerDegree(georeference.origin_latitude);
  const northMm = (latitudeDeg - georeference.origin_latitude) * perDegree.latitude * MM_PER_METRE;
  const eastMm = (longitudeDeg - georeference.origin_longitude) * perDegree.longitude * MM_PER_METRE;
  const rotation = georeference.north_rotation_deg * DEGREES_TO_RADIANS;
  return {
    xMm: eastMm * Math.cos(rotation) - northMm * Math.sin(rotation),
    yMm: northMm * Math.cos(rotation) + eastMm * Math.sin(rotation),
  };
}

export function sceneHeadingDeg(trueHeadingDeg, georeference) {
  const rotation = georeference ? georeference.north_rotation_deg : 0;
  return (((trueHeadingDeg - rotation) % 360) + 360) % 360;
}

export function viewFromOrientation(alphaDeg, betaDeg, gammaDeg) {
  const alpha = alphaDeg * DEGREES_TO_RADIANS;
  const beta = betaDeg * DEGREES_TO_RADIANS;
  const gamma = gammaDeg * DEGREES_TO_RADIANS;
  const east = -Math.cos(alpha) * Math.sin(gamma) - Math.sin(alpha) * Math.sin(beta) * Math.cos(gamma);
  const north = -Math.sin(alpha) * Math.sin(gamma) + Math.cos(alpha) * Math.sin(beta) * Math.cos(gamma);
  const up = -Math.cos(beta) * Math.cos(gamma);
  return {
    headingDeg: (((Math.atan2(east, north) / DEGREES_TO_RADIANS) % 360) + 360) % 360,
    elevationDeg: Math.asin(Math.max(-1, Math.min(1, up))) / DEGREES_TO_RADIANS,
  };
}
