import { viewFromOrientation } from "./geo.js";

const ABSOLUTE_ORIENTATION_EVENT = "deviceorientationabsolute";
const RELATIVE_ORIENTATION_EVENT = "deviceorientation";

export function watchPosition(onFix, onFailure) {
  if (!navigator.geolocation) {
    onFailure("this browser has no geolocation");
    return () => {};
  }
  const watchId = navigator.geolocation.watchPosition(
    (position) =>
      onFix({
        latitude: position.coords.latitude,
        longitude: position.coords.longitude,
        accuracyMetres: position.coords.accuracy,
        altitudeMetres: position.coords.altitude,
        timestamp: position.timestamp,
      }),
    (error) => onFailure(error.message),
    { enableHighAccuracy: true, maximumAge: 1000, timeout: 20000 },
  );
  return () => navigator.geolocation.clearWatch(watchId);
}

function orientationFrom(event, isAbsolute) {
  if (event.alpha === null || event.beta === null || event.gamma === null) return null;
  return { alpha: event.alpha, beta: event.beta, gamma: event.gamma, isAbsolute, ...viewFromOrientation(event.alpha, event.beta, event.gamma) };
}

export function watchOrientation(onOrientation, onFailure) {
  const eventName = "ondeviceorientationabsolute" in window ? ABSOLUTE_ORIENTATION_EVENT : RELATIVE_ORIENTATION_EVENT;
  const isAbsolute = eventName === ABSOLUTE_ORIENTATION_EVENT;
  const listener = (event) => {
    const orientation = orientationFrom(event, isAbsolute);
    if (orientation) onOrientation(orientation);
  };
  window.addEventListener(eventName, listener, true);
  if (!isAbsolute) onFailure("this browser gives no absolute compass, so the heading is relative to where the phone started");
  return () => window.removeEventListener(eventName, listener, true);
}

export async function startLiveCamera(videoElement) {
  const stream = await navigator.mediaDevices.getUserMedia({ video: { facingMode: "environment" }, audio: false });
  videoElement.srcObject = stream;
  await videoElement.play();
  return () => stream.getTracks().forEach((track) => track.stop());
}
