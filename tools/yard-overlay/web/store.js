const DATABASE_NAME = "yard-overlay";
const DATABASE_VERSION = 1;
const CAPTURE_STORE = "captures";

function openDatabase() {
  return new Promise((resolve, reject) => {
    const request = indexedDB.open(DATABASE_NAME, DATABASE_VERSION);
    request.onupgradeneeded = () => request.result.createObjectStore(CAPTURE_STORE, { keyPath: "id" });
    request.onsuccess = () => resolve(request.result);
    request.onerror = () => reject(request.error);
  });
}

async function inCaptureStore(mode, action) {
  const database = await openDatabase();
  return new Promise((resolve, reject) => {
    const transaction = database.transaction(CAPTURE_STORE, mode);
    const request = action(transaction.objectStore(CAPTURE_STORE));
    transaction.oncomplete = () => resolve(request.result);
    transaction.onerror = () => reject(transaction.error);
    transaction.onabort = () => reject(transaction.error);
  });
}

export const saveCapture = (capture) => inCaptureStore("readwrite", (store) => store.put(capture));

export const listCaptures = () => inCaptureStore("readonly", (store) => store.getAll());

export const deleteCapture = (id) => inCaptureStore("readwrite", (store) => store.delete(id));

export function newCaptureId(now = new Date()) {
  const stamp = now.toISOString().replace(/[-:.TZ]/g, "").slice(0, 14);
  const suffix = crypto.getRandomValues(new Uint32Array(1))[0].toString(36);
  return `cap-${stamp}-${suffix}`;
}
