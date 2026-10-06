const OUTLINE_PX = 2;
const MARKER_RADIUS_PX = 9;
const LABEL_OFFSET_PX = 14;
const LABEL_FONT_PX = 13;

function tracePath(context, points, isClosed) {
  context.beginPath();
  points.forEach(([x, y], index) => (index === 0 ? context.moveTo(x, y) : context.lineTo(x, y)));
  if (isClosed) context.closePath();
}

function paintShape(context, shape, pixelRatio) {
  tracePath(context, shape.points, shape.kind === "polygon");
  if (shape.fill) {
    context.fillStyle = shape.fill;
    context.fill();
  }
  if (shape.stroke) {
    context.strokeStyle = shape.stroke;
    context.lineWidth = OUTLINE_PX * pixelRatio;
    context.stroke();
  }
}

export function paintPlan(context, plan, pixelRatio) {
  plan.shapes.forEach((shape) => paintShape(context, shape, pixelRatio));
}

export function paintMarkers(context, markers, pixelRatio) {
  context.font = `${LABEL_FONT_PX * pixelRatio}px system-ui, sans-serif`;
  context.textBaseline = "middle";
  markers.forEach((marker) => {
    const [x, y] = marker.screen;
    context.beginPath();
    context.arc(x, y, MARKER_RADIUS_PX * pixelRatio, 0, 2 * Math.PI);
    context.strokeStyle = "white";
    context.lineWidth = OUTLINE_PX * pixelRatio;
    context.stroke();
    context.lineWidth = 3 * pixelRatio;
    context.strokeStyle = "rgba(0, 0, 0, 0.7)";
    context.strokeText(marker.label, x + LABEL_OFFSET_PX * pixelRatio, y);
    context.fillStyle = "white";
    context.fillText(marker.label, x + LABEL_OFFSET_PX * pixelRatio, y);
  });
}
