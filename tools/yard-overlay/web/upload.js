import { listCaptures, saveCapture } from "./store.js";

async function putOnce(url, body, contentType) {
  const response = await fetch(url, { method: "PUT", body, headers: { "Content-Type": contentType } });
  if (!response.ok) throw new Error(`${url} answered ${response.status}`);
}

export function captureRecordOf(capture) {
  const { photo, uploadedAt, ...record } = capture;
  return { ...record, photo_file: "photo.jpg", photo_bytes: photo.size, photo_type: photo.type };
}

export async function uploadCapture(capture) {
  await putOnce(`/captures/${capture.id}/photo.jpg`, capture.photo, capture.photo.type || "image/jpeg");
  await putOnce(`/captures/${capture.id}/capture.json`, JSON.stringify(captureRecordOf(capture), null, 2), "application/json");
  await saveCapture({ ...capture, uploadedAt: new Date().toISOString() });
}

export async function uploadWaitingCaptures() {
  const waiting = (await listCaptures()).filter((capture) => !capture.uploadedAt);
  const outcomes = [];
  for (const capture of waiting) {
    try {
      await uploadCapture(capture);
      outcomes.push({ id: capture.id, isUploaded: true });
    } catch (error) {
      outcomes.push({ id: capture.id, isUploaded: false, reason: String(error.message ?? error) });
    }
  }
  return outcomes;
}
