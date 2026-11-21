/* SAYMA Worker Portal — resilient offline static-asset cache */
const CACHE = 'sayma-worker-static-v6-mobile-doc-view-offline';
const CORE_FILES = [
  './login.html',
  './index.html',
  './css/worker.css',
  './css/worker.css?v=home-spacing-compact-v3',
  './js/auth.js',
  './js/offline-cache.js',
  './js/worker.js',
  './js/worker.js?v=mobile-doc-view-download-offline-v4',
  './assets/sayma-logo.png',
  './assets/icon-192.png',
  './assets/icon-512.png',
  './manifest.webmanifest',
  '../manifest.webmanifest'
];

self.addEventListener('install', (event) => {
  event.waitUntil(
    caches.open(CACHE)
      .then((cache) => cache.addAll(CORE_FILES))
      .then(() => self.skipWaiting())
  );
});

self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys()
      .then((keys) => Promise.all(
        keys
          .filter((key) => key.startsWith('sayma-worker-') && key !== CACHE)
          .map((key) => caches.delete(key))
      ))
      .then(() => self.clients.claim())
  );
});

self.addEventListener('fetch', (event) => {
  const request = event.request;
  const url = new URL(request.url);

  if (
    request.method !== 'GET' ||
    url.origin !== self.location.origin ||
    url.pathname.endsWith('.php') ||
    url.searchParams.has('action')
  ) {
    return;
  }

  event.respondWith(
    fetch(request)
      .then((response) => {
        if (response && response.ok) {
          const copy = response.clone();
          event.waitUntil(
            caches.open(CACHE).then((cache) => cache.put(request, copy))
          );
        }
        return response;
      })
      .catch(async () => {
        // Ignore query strings so versioned and unversioned CSS/JS URLs
        // can use the same previously cached static asset while offline.
        const cached = await caches.match(request, { ignoreSearch: true });
        if (cached) return cached;

        if (request.mode === 'navigate') {
          const path = url.pathname.toLowerCase();
          const fallback = path.endsWith('/login.html')
            ? './login.html'
            : './index.html';
          const shell = await caches.match(fallback);
          if (shell) return shell;
        }

        return new Response('Offline: this resource has not been cached yet.', {
          status: 503,
          statusText: 'Offline',
          headers: { 'Content-Type': 'text/plain; charset=utf-8' }
        });
      })
  );
});
