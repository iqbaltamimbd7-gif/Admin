/* Cache only the public PWA shell. Authenticated API responses and documents are never cached. */
const CACHE='sayma-mobile-admin-project-finance-v15-doc-viewer';
const ASSETS=[
  './','./index.html','./styles.css?v=project-finance-v8','./motion.js',
  './app.js?v=project-finance-v8','./data-layer.js','./data/seed.js',
  './manifest.webmanifest','./assets/icon-192.png','./assets/icon-512.png','./assets/sayma-logo.png'
];
const PATHS=new Set(ASSETS.map(path=>new URL(path,self.registration.scope).pathname));
self.addEventListener('install',event=>event.waitUntil(
  caches.open(CACHE).then(cache=>cache.addAll(ASSETS)).then(()=>self.skipWaiting())
));
self.addEventListener('activate',event=>event.waitUntil(
  caches.keys().then(keys=>Promise.all(keys.filter(key=>key.startsWith('sayma-mobile-')&&key!==CACHE).map(key=>caches.delete(key)))).then(()=>self.clients.claim())
));
self.addEventListener('fetch',event=>{
  const url=new URL(event.request.url);
  if(event.request.method!=='GET'||url.origin!==self.location.origin||
     url.pathname.endsWith('.php')||url.searchParams.has('action')||!PATHS.has(url.pathname))return;
  event.respondWith(fetch(event.request).then(response=>{
    if(response.ok)caches.open(CACHE).then(cache=>cache.put(event.request,response.clone()));
    return response;
  }).catch(()=>caches.match(event.request)));
});
