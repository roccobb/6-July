const CACHE_NAME = "finnish-word-v2";
const APP_SHELL = [
  ".",
  "index.html",
  "styles.css",
  "web/app.js",
  "manifest.webmanifest",
  "privacy.html",
  "app/src/main/assets/words.json",
  "web/icons/icon-180.png",
  "web/icons/icon-192.png",
  "web/icons/icon-512.png"
];

self.addEventListener("install", (event) => {
  event.waitUntil(caches.open(CACHE_NAME).then((cache) => cache.addAll(APP_SHELL)));
});

self.addEventListener("activate", (event) => {
  event.waitUntil(
    caches.keys().then((keys) =>
      Promise.all(keys.filter((key) => key !== CACHE_NAME).map((key) => caches.delete(key)))
    )
  );
});

self.addEventListener("fetch", (event) => {
  if (event.request.method !== "GET") {
    return;
  }
  event.respondWith(
    caches.match(event.request).then((cached) => {
      return cached || fetch(event.request);
    })
  );
});
