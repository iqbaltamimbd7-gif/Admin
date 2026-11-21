(()=>{'use strict';
const DB_NAME='sayma-worker-private-offline-v1',DB_VERSION=1,STORE='records';
let dbPromise=null;
function openDB(){
 if(dbPromise)return dbPromise;
 dbPromise=new Promise((resolve,reject)=>{
  if(!window.indexedDB){reject(new Error('Offline storage is not supported on this device.'));return;}
  const request=indexedDB.open(DB_NAME,DB_VERSION);
  request.onupgradeneeded=()=>{const db=request.result;if(!db.objectStoreNames.contains(STORE))db.createObjectStore(STORE,{keyPath:'key'});};
  request.onsuccess=()=>resolve(request.result);
  request.onerror=()=>reject(request.error||new Error('Could not open offline storage.'));
 });
 return dbPromise;
}
async function get(key){
 try{const db=await openDB();return await new Promise((resolve,reject)=>{const tx=db.transaction(STORE,'readonly'),r=tx.objectStore(STORE).get(String(key));r.onsuccess=()=>resolve(r.result?r.result.value:null);r.onerror=()=>reject(r.error);});}
 catch(e){console.warn('Worker offline read failed',e);return null;}
}
async function put(key,value){
 const db=await openDB();
 return new Promise((resolve,reject)=>{const tx=db.transaction(STORE,'readwrite');tx.objectStore(STORE).put({key:String(key),value,savedAt:new Date().toISOString()});tx.oncomplete=()=>resolve(true);tx.onerror=()=>reject(tx.error||new Error('Could not save offline data.'));});
}
async function remove(key){
 try{const db=await openDB();return await new Promise((resolve,reject)=>{const tx=db.transaction(STORE,'readwrite');tx.objectStore(STORE).delete(String(key));tx.oncomplete=()=>resolve(true);tx.onerror=()=>reject(tx.error);});}catch(e){return false;}
}
async function clear(){
 try{const db=await openDB();return await new Promise((resolve,reject)=>{const tx=db.transaction(STORE,'readwrite');tx.objectStore(STORE).clear();tx.oncomplete=()=>resolve(true);tx.onerror=()=>reject(tx.error);});}catch(e){return false;}
}
window.SAYMAWorkerOffline={get,put,remove,clear};
})();