/* Only cache public PWA shell. NEVER cache authenticated API responses or documents. */
const CACHE='sayma-mobile-v6-offline-r42-portal';
const ASSETS=['./','./index.html','./styles.css?v=offline-first-r42-portal','./motion.js','./app.js?v=offline-first-r42-portal','./data-layer.js','./data/seed.js','./manifest.webmanifest','./assets/icon-192.png','./assets/icon-512.png','./assets/sayma-logo.png'];
const PATHS=new Set(ASSETS.map(p=>new URL(p,self.registration.scope).pathname));
self.addEventListener('install',e=>e.waitUntil(caches.open(CACHE).then(c=>c.addAll(ASSETS)).then(()=>self.skipWaiting())));
self.addEventListener('activate',e=>e.waitUntil(caches.keys().then(keys=>Promise.all(keys.filter(k=>k.startsWith('sayma-mobile-')&&k!==CACHE).map(k=>caches.delete(k)))).then(()=>self.clients.claim())));
self.addEventListener('fetch',e=>{
 const url=new URL(e.request.url);
 if(e.request.method!=='GET'||url.origin!==self.location.origin||url.pathname.endsWith('.php')||url.searchParams.has('action')||!PATHS.has(url.pathname))return;
 e.respondWith(fetch(e.request).then(r=>{if(r.ok)caches.open(CACHE).then(c=>c.put(e.request,r.clone()));return r}).catch(()=>caches.match(e.request)));
});
