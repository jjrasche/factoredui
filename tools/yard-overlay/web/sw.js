const SHELL_CACHE = "yard-overlay-shell";
const SHELL_FILES = [
  "./",
  "index.html",
  "app.js",
  "align.js",
  "alignflow.js",
  "draw.js",
  "geo.js",
  "palette.js",
  "plan.js",
  "projection.js",
  "sensors.js",
  "store.js",
  "twin.js",
  "upload.js",
  "data/example-twin.json",
];

self.addEventListener("install", (event) => {
  event.waitUntil(caches.open(SHELL_CACHE).then((cache) => cache.addAll(SHELL_FILES)).then(() => self.skipWaiting()));
});

self.addEventListener("activate", (event) => {
  event.waitUntil(self.clients.claim());
});

async function networkThenCache(request) {
  const cache = await caches.open(SHELL_CACHE);
  try {
    const fresh = await fetch(request);
    if (fresh.ok) await cache.put(request, fresh.clone());
    return fresh;
  } catch {
    const kept = await cache.match(request, { ignoreSearch: true });
    if (kept) return kept;
    throw new Error("offline and not cached");
  }
}

async function cacheThenRefresh(request) {
  const cache = await caches.open(SHELL_CACHE);
  const kept = await cache.match(request, { ignoreSearch: true });
  const refresh = fetch(request)
    .then((fresh) => (fresh.ok ? cache.put(request, fresh.clone()).then(() => fresh) : fresh))
    .catch(() => null);
  return kept ?? (await refresh) ?? Response.error();
}

self.addEventListener("fetch", (event) => {
  const { request } = event;
  const url = new URL(request.url);
  if (request.method !== "GET" || url.origin !== self.location.origin) return;
  if (url.pathname.includes("/captures/")) return;
  event.respondWith(url.pathname.includes("/data/") ? networkThenCache(request) : cacheThenRefresh(request));
});
