const CACHE='sayma-worker-portal-offline-cache-v1';
const FILES=['./login.html','./index.html','./css/worker.css?v=worker-offline-1','./js/offline-cache.js','./js/auth.js','./js/worker.js?v=worker-offline-1','./assets/sayma-logo.png','./assets/icon-192.png','./assets/icon-512.png','./manifest.webmanifest'];
self.addEventListener('install',event=>event.waitUntil(caches.open(CACHE).then(cache=>cache.addAll(FILES)).then(()=>self.skipWaiting())));
self.addEventListener('activate',event=>event.waitUntil(caches.keys().then(keys=>Promise.all(keys.filter(key=>key.startsWith('sayma-worker-')&&key!==CACHE).map(key=>caches.delete(key)))).then(()=>self.clients.claim())));
self.addEventListener('fetch',event=>{
 const url=new URL(event.request.url);
 if(event.request.method!=='GET'||url.origin!==self.location.origin||url.pathname.endsWith('.php')||url.searchParams.has('action'))return;
 event.respondWith(fetch(event.request).then(response=>{
   if(response.ok)caches.open(CACHE).then(cache=>cache.put(event.request,response.clone()));
   return response;
 }).catch(()=>caches.match(event.request)));
});
