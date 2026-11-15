/* Service worker: keeps the app screens available when the internet is down.
   It never stores anything from /api, so business data is never cached here. */
const CACHE = 'inventory-shell-v1';
const SHELL = ['/', '/index.html', '/login.html', '/css/style.css', '/js/offline.js', '/js/app.js', '/manifest.json', '/icons/icon-192.png'];

self.addEventListener('install', event => {
  event.waitUntil(caches.open(CACHE).then(c => c.addAll(SHELL)).then(() => self.skipWaiting()));
});

self.addEventListener('activate', event => {
  event.waitUntil(
    caches.keys().then(keys => Promise.all(keys.filter(k => k !== CACHE).map(k => caches.delete(k))))
      .then(() => self.clients.claim()));
});

self.addEventListener('fetch', event => {
  const req = event.request;
  const url = new URL(req.url);
  if (req.method !== 'GET' || url.origin !== self.location.origin) return;
  if (url.pathname.startsWith('/api/') || url.pathname === '/login' || url.pathname === '/logout') return;
  // Network first (so updates arrive when online), cache when offline.
  event.respondWith(
    fetch(req).then(res => {
      if (res.ok) { const copy = res.clone(); caches.open(CACHE).then(c => c.put(req, copy)); }
      return res;
    }).catch(() => caches.match(req).then(hit => hit || caches.match('/index.html'))));
});
