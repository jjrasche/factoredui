import { parseTwin, availableLayers, LAYER_LABELS, layerVersions } from "./twin.js";
import { cameraFor, buildDrawPlan, alignPlan } from "./plan.js";
import { paintPlan, paintMarkers } from "./draw.js";
import { solveAlignment, IDENTITY_ALIGNMENT } from "./align.js";
import { beginAlignFlow, tapDuringAlign, finishAlignFlow } from "./alignflow.js";
import { sceneMmFromGps, sceneHeadingDeg } from "./geo.js";
import { watchPosition, watchOrientation, startLiveCamera } from "./sensors.js";
import { saveCapture, listCaptures, newCaptureId } from "./store.js";
import { uploadWaitingCaptures } from "./upload.js";

const MM_PER_METRE = 1000;
const LANDSCAPE_HFOV_DEG = 69;
const SENSOR_ASPECT = 3 / 4;
const DEFAULT_EYE_HEIGHT_MM = 1500;
const ALIGN_REACH_CSS_PX = 44;
const MAX_CANVAS_SIDE_PX = 2048;
const STAGE_MAX_HEIGHT_SHARE = 0.62;
const DEFAULT_ASPECT = 4 / 3;
const YARD_START_DEPTH_SHARE = 0.3;
const APP_VERSION = "yard-overlay-1";

const byId = (id) => document.getElementById(id);
const canvas = byId("view");
const context = canvas.getContext("2d");

const state = {
  twin: null,
  twinSource: "",
  enabledLayers: new Set(),
  background: null,
  viewpoint: { xMm: 0, yMm: 0, heightMm: DEFAULT_EYE_HEIGHT_MM, yawDeg: 0, pitchDeg: 0, hfovDeg: LANDSCAPE_HFOV_DEG },
  alignment: IDENTITY_ALIGNMENT,
  alignFlow: finishAlignFlow(beginAlignFlow()),
  taps: [],
  isLive: false,
  gps: null,
  compass: null,
  stopSensors: [],
  stopCamera: null,
  isRedrawQueued: false,
};

function say(text) {
  byId("message").textContent = text;
}

function setStatus(id, text, tone) {
  const element = byId(id);
  element.textContent = text;
  element.className = tone ?? "";
}

function hfovForAspect(aspect) {
  if (aspect >= 1) return LANDSCAPE_HFOV_DEG;
  const landscape = (LANDSCAPE_HFOV_DEG * Math.PI) / 180;
  return (2 * Math.atan(Math.tan(landscape / 2) * SENSOR_ASPECT) * 180) / Math.PI;
}

function sourceSize() {
  if (!state.background) return { width: DEFAULT_ASPECT, height: 1 };
  return { width: state.background.width, height: state.background.height };
}

function sizeCanvasToSource() {
  const { width, height } = sourceSize();
  const aspect = width / height;
  const stageWidth = byId("stage").clientWidth || window.innerWidth;
  const cssWidth = Math.min(stageWidth, window.innerHeight * STAGE_MAX_HEIGHT_SHARE * aspect);
  const pixelRatio = window.devicePixelRatio || 1;
  const wantedWidth = cssWidth * pixelRatio;
  const wantedHeight = (cssWidth / aspect) * pixelRatio;
  const shrink = Math.min(1, MAX_CANVAS_SIDE_PX / Math.max(wantedWidth, wantedHeight));
  const pixelWidth = Math.round(wantedWidth * shrink);
  const pixelHeight = Math.round(wantedHeight * shrink);
  if (canvas.width !== pixelWidth || canvas.height !== pixelHeight) {
    canvas.width = pixelWidth;
    canvas.height = pixelHeight;
  }
  canvas.style.width = `${cssWidth}px`;
  canvas.style.height = `${cssWidth / aspect}px`;
  return pixelWidth / cssWidth;
}

function paintBackground() {
  context.fillStyle = "#101418";
  context.fillRect(0, 0, canvas.width, canvas.height);
  if (state.background) context.drawImage(state.background.source, 0, 0, canvas.width, canvas.height);
}

function currentCamera() {
  const view = state.viewpoint;
  return cameraFor({
    twin: state.twin,
    positionMm: [view.xMm, view.yMm],
    heightAboveGroundMm: view.heightMm,
    yawDeg: view.yawDeg,
    pitchDeg: view.pitchDeg,
    hfovDeg: view.hfovDeg,
    width: canvas.width,
    height: canvas.height,
  });
}

function isAligning() {
  return state.alignFlow.step !== "idle";
}

function drawNow() {
  state.isRedrawQueued = false;
  const pixelRatio = sizeCanvasToSource();
  paintBackground();
  if (!state.twin) return;
  const alignment = isAligning() ? IDENTITY_ALIGNMENT : state.alignment;
  const plan = alignPlan(buildDrawPlan(state.twin, currentCamera(), [...state.enabledLayers]), alignment);
  state.visibleAnchors = plan.anchors;
  paintPlan(context, plan, pixelRatio);
  if (isAligning()) paintMarkers(context, plan.anchors, pixelRatio);
  paintMarkers(context, state.alignFlow.pairs.map((pair) => ({ screen: pair.to, label: `${pair.anchorLabel} here` })), pixelRatio);
  if (state.background?.kind === "video") queueRedraw();
}

function queueRedraw() {
  if (state.isRedrawQueued) return;
  state.isRedrawQueued = true;
  requestAnimationFrame(drawNow);
}

const VIEWPOINT_CONTROLS = {
  "view-x": { read: (view) => view.xMm / MM_PER_METRE, write: (view, value) => (view.xMm = value * MM_PER_METRE), text: (value) => `${value.toFixed(1)} m` },
  "view-y": { read: (view) => view.yMm / MM_PER_METRE, write: (view, value) => (view.yMm = value * MM_PER_METRE), text: (value) => `${value.toFixed(1)} m` },
  "view-yaw": { read: (view) => view.yawDeg, write: (view, value) => (view.yawDeg = value), text: (value) => `${Math.round(value)}°` },
  "view-pitch": { read: (view) => view.pitchDeg, write: (view, value) => (view.pitchDeg = value), text: (value) => `${Math.round(value)}°` },
  "view-height": { read: (view) => view.heightMm / MM_PER_METRE, write: (view, value) => (view.heightMm = value * MM_PER_METRE), text: (value) => `${value.toFixed(1)} m` },
  "view-fov": { read: (view) => view.hfovDeg, write: (view, value) => (view.hfovDeg = value), text: (value) => `${Math.round(value)}°` },
};

function showViewpoint() {
  Object.entries(VIEWPOINT_CONTROLS).forEach(([id, control]) => {
    const value = control.read(state.viewpoint);
    byId(id).value = String(value);
    byId(`${id}-out`).textContent = control.text(value);
  });
}

function fitViewpointRanges() {
  const { x0, y0, x1, y1 } = state.twin.window;
  const margin = 20 * MM_PER_METRE;
  [["view-x", x0, x1], ["view-y", y0, y1]].forEach(([id, low, high]) => {
    byId(id).min = String(Math.floor((low - margin) / MM_PER_METRE));
    byId(id).max = String(Math.ceil((high + margin) / MM_PER_METRE));
  });
}

function bindViewpointControls() {
  Object.entries(VIEWPOINT_CONTROLS).forEach(([id, control]) => {
    byId(id).addEventListener("input", (event) => {
      control.write(state.viewpoint, Number(event.target.value));
      byId(`${id}-out`).textContent = control.text(Number(event.target.value));
      queueRedraw();
    });
  });
}

function bearingToTreesDeg(fromX, fromY) {
  const trees = state.twin.instances?.items ?? [];
  if (trees.length === 0) return 0;
  const meanX = trees.reduce((sum, tree) => sum + tree.x_mm, 0) / trees.length;
  const meanY = trees.reduce((sum, tree) => sum + tree.y_mm, 0) / trees.length;
  return (((Math.atan2(meanX - fromX, meanY - fromY) * 180) / Math.PI) + 360) % 360;
}

function standFacingTrees() {
  const { x0, y0, x1, y1 } = state.twin.window;
  const xMm = (x0 + x1) / 2;
  const yMm = y0 + (y1 - y0) * YARD_START_DEPTH_SHARE;
  state.viewpoint = { ...state.viewpoint, xMm, yMm, yawDeg: bearingToTreesDeg(xMm, yMm), pitchDeg: 0 };
}

function showLayerToggles() {
  const box = byId("layers");
  box.querySelectorAll("label").forEach((label) => label.remove());
  availableLayers(state.twin).forEach((layer) => {
    const label = document.createElement("label");
    const toggle = document.createElement("input");
    toggle.type = "checkbox";
    toggle.checked = state.enabledLayers.has(layer);
    toggle.addEventListener("change", () => {
      if (toggle.checked) state.enabledLayers.add(layer);
      else state.enabledLayers.delete(layer);
      queueRedraw();
    });
    label.append(toggle, LAYER_LABELS[layer]);
    box.append(label);
  });
}

async function fetchTwinText() {
  for (const path of ["data/yard-twin.json", "data/example-twin.json"]) {
    const response = await fetch(path).catch(() => null);
    if (response?.ok) return { text: await response.text(), source: path };
  }
  throw new Error("no twin found at data/yard-twin.json or data/example-twin.json");
}

async function loadTwin() {
  const { text, source } = await fetchTwinText();
  state.twin = parseTwin(text);
  state.twinSource = source;
  state.enabledLayers = new Set(availableLayers(state.twin));
  const georef = state.twin.georeference ? "georeferenced" : "no georeference";
  setStatus("status-twin", `Twin: ${source.replace("data/", "")} (${georef})`, state.twin.georeference ? "ok" : "warn");
  showLayerToggles();
  fitViewpointRanges();
  standFacingTrees();
  showViewpoint();
}

function stopLiveCamera() {
  state.stopCamera?.();
  state.stopCamera = null;
  byId("live-camera").setAttribute("aria-pressed", "false");
}

async function useBlobAsPhoto(blob, origin) {
  stopLiveCamera();
  const bitmap = await createImageBitmap(blob);
  state.background = { kind: "photo", source: bitmap, width: bitmap.width, height: bitmap.height, blob, origin };
  state.viewpoint.hfovDeg = hfovForAspect(bitmap.width / bitmap.height);
  resetAlignment();
  showViewpoint();
  say("Photo loaded. Tap Align and mark two things you can see in both the photo and the overlay.");
  queueRedraw();
}

async function toggleLiveCamera() {
  if (state.stopCamera) {
    stopLiveCamera();
    state.background = null;
    queueRedraw();
    return;
  }
  const video = byId("camera");
  try {
    state.stopCamera = await startLiveCamera(video);
  } catch (error) {
    say(`Camera unavailable: ${error.message}`);
    return;
  }
  state.background = { kind: "video", source: video, width: video.videoWidth, height: video.videoHeight, origin: "camera" };
  state.viewpoint.hfovDeg = hfovForAspect(video.videoWidth / video.videoHeight);
  byId("live-camera").setAttribute("aria-pressed", "true");
  showViewpoint();
  queueRedraw();
}

function resetAlignment() {
  state.alignment = IDENTITY_ALIGNMENT;
  state.taps = [];
  state.alignFlow = finishAlignFlow(beginAlignFlow());
  byId("align-done").hidden = true;
}

function startAlignment() {
  if (!state.twin) return;
  state.alignment = IDENTITY_ALIGNMENT;
  state.taps = [];
  state.alignFlow = beginAlignFlow();
  byId("align-done").hidden = false;
  say(state.alignFlow.message);
  queueRedraw();
}

function completeAlignment(pairs) {
  state.taps = pairs;
  state.alignment = solveAlignment(pairs.map(({ from, to }) => ({ from, to })));
  byId("align-done").hidden = true;
  say(pairs.length === 0 ? "Alignment cleared." : `Aligned from ${pairs.length} tap${pairs.length === 1 ? "" : "s"}.`);
}

function finishAlignmentEarly() {
  const finished = finishAlignFlow(state.alignFlow);
  state.alignFlow = finished;
  completeAlignment(finished.pairs);
  queueRedraw();
}

function onStageTap(event) {
  if (!isAligning()) return;
  const bounds = canvas.getBoundingClientRect();
  const scale = canvas.width / bounds.width;
  const point = [(event.clientX - bounds.left) * scale, (event.clientY - bounds.top) * scale];
  const wasIdle = state.alignFlow.step === "idle";
  state.alignFlow = tapDuringAlign(state.alignFlow, point, state.visibleAnchors ?? [], ALIGN_REACH_CSS_PX * scale);
  if (!wasIdle && state.alignFlow.step === "idle") completeAlignment(state.alignFlow.pairs);
  else say(state.alignFlow.message);
  queueRedraw();
}

function followFix(fix) {
  state.gps = fix;
  const gpsText = `GPS: ±${Math.round(fix.accuracyMetres)} m`;
  setStatus("status-gps", gpsText, fix.accuracyMetres <= 10 ? "ok" : "warn");
  const georef = state.twin?.georeference;
  if (!georef || isAligning()) return;
  const spot = sceneMmFromGps(georef, fix.latitude, fix.longitude);
  state.viewpoint.xMm = spot.xMm;
  state.viewpoint.yMm = spot.yMm;
  showViewpoint();
  queueRedraw();
}

function followCompass(orientation) {
  state.compass = orientation;
  setStatus("status-compass", `Compass: ${Math.round(orientation.headingDeg)}°`, orientation.isAbsolute ? "ok" : "warn");
  if (isAligning()) return;
  state.viewpoint.yawDeg = sceneHeadingDeg(orientation.headingDeg, state.twin?.georeference);
  state.viewpoint.pitchDeg = orientation.elevationDeg;
  showViewpoint();
  queueRedraw();
}

function toggleLiveSensors() {
  const button = byId("live-sensors");
  if (state.isLive) {
    state.stopSensors.forEach((stop) => stop());
    state.stopSensors = [];
    state.isLive = false;
    button.setAttribute("aria-pressed", "false");
    setStatus("status-gps", "GPS: off");
    setStatus("status-compass", "Compass: off");
    return;
  }
  state.isLive = true;
  button.setAttribute("aria-pressed", "true");
  state.stopSensors = [
    watchPosition(followFix, (reason) => setStatus("status-gps", `GPS: ${reason}`, "warn")),
    watchOrientation(followCompass, (reason) => say(reason)),
  ];
  if (!state.twin?.georeference) say("This twin has no georeference, so GPS cannot place you; the compass still turns the view. Set where you stand under Viewpoint.");
}

function frameAsBlob() {
  const frame = document.createElement("canvas");
  frame.width = state.background.width;
  frame.height = state.background.height;
  frame.getContext("2d").drawImage(state.background.source, 0, 0);
  return new Promise((resolve) => frame.toBlob(resolve, "image/jpeg", 0.92));
}

async function photoForCapture() {
  if (!state.background) return null;
  return state.background.kind === "video" ? frameAsBlob() : state.background.blob;
}

function normalised(point) {
  return [point[0] / canvas.width, point[1] / canvas.height];
}

function captureRecord(id, photo) {
  const view = state.viewpoint;
  return {
    id,
    created_at: new Date().toISOString(),
    app_version: APP_VERSION,
    source: state.background.origin,
    photo,
    photo_size: { width: state.background.width, height: state.background.height },
    twin: { source: state.twinSource, layer_versions: layerVersions(state.twin), georeference: state.twin.georeference ?? null },
    enabled_layers: [...state.enabledLayers],
    viewpoint: { position_mm: [view.xMm, view.yMm], eye_height_mm: view.heightMm, yaw_deg: view.yawDeg, pitch_deg: view.pitchDeg, hfov_deg: view.hfovDeg },
    gps: state.gps,
    compass: state.compass,
    alignment: { scale: state.alignment.scale, rotation_rad: state.alignment.rotationRad, translate_x: state.alignment.translateX, translate_y: state.alignment.translateY, canvas: { width: canvas.width, height: canvas.height } },
    taps: state.taps.map((tap) => ({ anchor_id: tap.anchorId, anchor_layer: tap.anchorLayer, anchor_label: tap.anchorLabel, overlay_at: normalised(tap.from), photo_at: normalised(tap.to) })),
    uploadedAt: null,
  };
}

async function saveCurrentCapture() {
  const photo = await photoForCapture();
  if (!photo) return say("Add a photo or start the live camera first.");
  const record = captureRecord(newCaptureId(), photo);
  await saveCapture(record);
  say(`Saved ${record.id} on this phone (${Math.round(photo.size / 1024)} KB, ${record.taps.length} alignment taps).`);
  await showUploadCount();
}

async function showUploadCount() {
  const waiting = (await listCaptures()).filter((capture) => !capture.uploadedAt).length;
  byId("upload").textContent = waiting === 0 ? "Upload" : `Upload (${waiting})`;
}

async function uploadNow() {
  const outcomes = await uploadWaitingCaptures();
  const sent = outcomes.filter((outcome) => outcome.isUploaded).length;
  const failed = outcomes.filter((outcome) => !outcome.isUploaded);
  if (outcomes.length === 0) say("Nothing waiting to upload.");
  else if (failed.length === 0) say(`Uploaded ${sent} capture${sent === 1 ? "" : "s"} to the PC.`);
  else say(`Uploaded ${sent}, ${failed.length} waiting: needs the cable, adb reverse and serve.py running (${failed[0].reason}).`);
  await showUploadCount();
}

async function showOfflineStatus() {
  if (!("serviceWorker" in navigator)) return setStatus("status-cache", "Offline: no service worker", "warn");
  await navigator.serviceWorker.register("sw.js");
  await navigator.serviceWorker.ready;
  const kept = await caches.match("index.html");
  setStatus("status-cache", kept ? "Offline: ready" : "Offline: not cached yet", kept ? "ok" : "warn");
  navigator.storage?.persist?.();
}

function bindControls() {
  byId("take-photo").addEventListener("change", (event) => event.target.files[0] && useBlobAsPhoto(event.target.files[0], "file"));
  byId("pick-photo").addEventListener("change", (event) => event.target.files[0] && useBlobAsPhoto(event.target.files[0], "file"));
  byId("live-camera").addEventListener("click", toggleLiveCamera);
  byId("align").addEventListener("click", startAlignment);
  byId("align-done").addEventListener("click", finishAlignmentEarly);
  byId("align-reset").addEventListener("click", () => {
    resetAlignment();
    say("Alignment cleared.");
    queueRedraw();
  });
  byId("live-sensors").addEventListener("click", toggleLiveSensors);
  byId("save").addEventListener("click", saveCurrentCapture);
  byId("upload").addEventListener("click", uploadNow);
  canvas.addEventListener("pointerdown", onStageTap);
  window.addEventListener("resize", queueRedraw);
  bindViewpointControls();
}

async function start() {
  bindControls();
  try {
    await loadTwin();
  } catch (error) {
    say(`Could not load the twin: ${error.message}`);
    return;
  }
  queueRedraw();
  await showUploadCount();
  await showOfflineStatus().catch((error) => setStatus("status-cache", `Offline: ${error.message}`, "warn"));
}

window.yardOverlay = { state, useBlobAsPhoto, drawNow, onStageTap, saveCurrentCapture, uploadNow, startAlignment };

start();
