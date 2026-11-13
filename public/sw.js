const CACHE = 'synera-shell-v1';
const PRECACHE = ['/','/index.html','/icon.svg','/manifest.webmanifest'];
const publicAsset = (url) =>
  ['/', '/index.html', '/icon.svg', '/manifest.webmanifest'].includes(url.pathname) ||
  (url.pathname.startsWith('/assets/') && !url.pathname.endsWith('.mp4'));

self.addEventListener('install', (event) => {
  event.waitUntil(caches.open(CACHE).then((cache) => cache.addAll(PRECACHE)).then(() => self.skipWaiting()));
});
self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches.keys()
      .then((keys) => Promise.all(keys.filter((key) => key.startsWith('synera-shell-') && key !== CACHE).map((key) => caches.delete(key))))
      .then(() => self.clients.claim()),
  );
});
self.addEventListener('fetch', (event) => {
  const url = new URL(event.request.url);
  if (event.request.method !== 'GET' || url.origin !== self.location.origin ||
      event.request.headers.has('Authorization') || !publicAsset(url)) return;
  event.respondWith((async () => {
    const cache = await caches.open(CACHE);
    // Spring adds Vary: Origin to static responses. Only public shell assets can
    // ignore it; authenticated APIs and business data never enter this cache.
    const cached = await cache.match(event.request, {ignoreVary: true});
    if (url.pathname.startsWith('/assets/') && cached) return cached;
    try {
      const response = await fetch(event.request);
      if (response.ok) await cache.put(event.request, response.clone());
      return response;
    } catch {
      return cached || (event.request.mode === 'navigate'
        ? await cache.match('/index.html', {ignoreVary: true})
        : null) || Response.error();
    }
  })());
});
