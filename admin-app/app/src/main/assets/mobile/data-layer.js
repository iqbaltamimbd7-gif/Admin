/* SAYMA Mobile V6: same-origin PHP API integration, shared permit ledger and server document versioning. */
(function(){'use strict';
const JKEY='sayma-mobile-v2', LEGACY='sayma-mobile-v1', CONFKEY='sayma-mobile-config-v2';
const initial=window.SAYMA_SEED||{};
const clone=x=>JSON.parse(JSON.stringify(x));
const existing=()=>{if(window.SAYMA_PRODUCTION)return clone(initial);for(const key of [JKEY,LEGACY]){try{let v=JSON.parse(localStorage.getItem(key));if(v&&Array.isArray(v.workers))return v;}catch{}}return clone(initial)};
const state=existing();
for(const [k,v] of Object.entries(initial))if(state[k]===undefined)state[k]=clone(v);
state.letterSerialCounters ||= {};state.letterHistory ||= [];state.letterVerificationRecords ||= [];state.documents ||= [];state.documentationTools ||= [];
let config;try{config=JSON.parse(localStorage.getItem(CONFKEY))||{}}catch{config={}};
config.apiPath ||= /\/mobile(?:\/|$)/.test(location.pathname)||location.pathname.includes('/sayma-mobile')?'../api.php':'./api.php';
// Production portal is hosted under /portal/mobile/. Force the same-origin
// shared API path so stale settings from the former admin subdomain cannot win.
if(window.SAYMA_PRODUCTION && /\/portal\/mobile(?:\/|$)/.test(location.pathname)){
  config.apiPath='/portal/api.php';
  try{localStorage.setItem(CONFKEY,JSON.stringify(config));}catch(_){}
}
config.mode ||= 'auto';
let connected=false, username='', lastError='', dirty=!window.SAYMA_PRODUCTION && localStorage.getItem(JKEY+'-dirty')==='1', paymentLedgerSnapshot={};
let docs=[], categories=[], tools=[], wdocs={};
const defaults=[{key:'SSM',label:'SSM'},{key:'CIDB',label:'CIDB'},{key:'MAJLIS',label:'Majlis License'},{key:'EPF',label:'EPF'},{key:'SOCSO',label:'SOCSO / PERKESO'},{key:'LHDN',label:'LHDN / Tax'},{key:'INSURANCE',label:'Insurance'},{key:'OTHER',label:'Other'}];
const OFFLINE_DB='sayma-mobile-offline-v1';
let offlineDbPromise=null, cacheWritePromise=Promise.resolve(), syncBusy=false, connectBusy=false;
let pendingCountValue=0;
const STATE_KEYS=['workers','projects','projectGrids','permitPayments','permitYears','permitCategories','attendance','letterSerialCounters','letterHistory','letterVerificationRecords','companyDocumentCategories','taxRecords'];
function openOfflineDb(){
 if(offlineDbPromise)return offlineDbPromise;
 offlineDbPromise=new Promise((resolve,reject)=>{
  if(!window.indexedDB){reject(Error('Offline storage is unavailable on this device.'));return;}
  const req=indexedDB.open(OFFLINE_DB,1);
  req.onupgradeneeded=()=>{const db=req.result;if(!db.objectStoreNames.contains('kv'))db.createObjectStore('kv',{keyPath:'id'});if(!db.objectStoreNames.contains('queue'))db.createObjectStore('queue',{keyPath:'id'});};
  req.onsuccess=()=>resolve(req.result);req.onerror=()=>reject(req.error||Error('Could not open offline storage.'));
 });
 return offlineDbPromise;
}
async function dbGet(store,key){const db=await openOfflineDb();return new Promise((resolve,reject)=>{const tx=db.transaction(store,'readonly'),r=tx.objectStore(store).get(key);r.onsuccess=()=>resolve(r.result||null);r.onerror=()=>reject(r.error);});}
async function dbPut(store,value){const db=await openOfflineDb();return new Promise((resolve,reject)=>{const tx=db.transaction(store,'readwrite');tx.objectStore(store).put(value);tx.oncomplete=()=>resolve(true);tx.onerror=()=>reject(tx.error);});}
async function dbDelete(store,key){const db=await openOfflineDb();return new Promise((resolve,reject)=>{const tx=db.transaction(store,'readwrite');tx.objectStore(store).delete(key);tx.oncomplete=()=>resolve(true);tx.onerror=()=>reject(tx.error);});}
async function dbAll(store){const db=await openOfflineDb();return new Promise((resolve,reject)=>{const tx=db.transaction(store,'readonly'),r=tx.objectStore(store).getAll();r.onsuccess=()=>resolve(r.result||[]);r.onerror=()=>reject(r.error);});}
async function queueItems(){return (await dbAll('queue')).sort((a,b)=>String(a.createdAt).localeCompare(String(b.createdAt)));}
async function refreshPendingCount(){try{pendingCountValue=(await dbAll('queue')).length}catch{pendingCountValue=0}return pendingCountValue;}
async function queueAdd(type,body={}){
 const id=localId();await dbPut('queue',{id,type,body:clone(body),createdAt:new Date().toISOString(),status:'pending'});
 await refreshPendingCount();dispatch();return id;
}
async function queueRemove(id){await dbDelete('queue',id);await refreshPendingCount();dispatch();}
function statePayload(){return clone(Object.fromEntries(STATE_KEYS.map(k=>[k,state[k]])));}
async function cacheSnapshot(){
 const snap={id:'snapshot',state:clone(state),docs:clone(docs),categories:clone(categories),tools:clone(tools),wdocs:clone(wdocs),savedAt:new Date().toISOString()};
 // IndexedDB is the primary persistent offline store. Do not use localStorage for
 // the full production snapshot because company records can exceed its small quota.
 cacheWritePromise=cacheWritePromise.catch(()=>{}).then(()=>dbPut('kv',snap));
 return cacheWritePromise;
}
async function cacheExtra(script,action,value){await dbPut('kv',{id:'extra:'+script+':'+action,value:clone(value)});}
async function readExtra(script,action){const x=await dbGet('kv','extra:'+script+':'+action);return x?.value||null;}
async function restoreOffline(){
 try{
  const snap=await dbGet('kv','snapshot');
  if(snap?.state){
   for(const [k,v] of Object.entries(snap.state))state[k]=clone(v);
   docs=clone(snap.docs||state.documents||[]);
   categories=clone(snap.categories||state.documentCategoryObjects||defaults);
   tools=clone(snap.tools||state.documentationTools||[]);
   wdocs=clone(snap.wdocs||{});
   dirty=true;
   const savedAt=snap.savedAt?new Date(snap.savedAt).toLocaleString():'';
   lastError='Offline mode — last saved company data restored'+(savedAt?' ('+savedAt+')': '')+'.';
  }else{
   docs=state.documents||[];
   categories=state.documentCategoryObjects||defaults;
   tools=state.documentationTools||[];
   lastError='No saved company-data snapshot is available on this device yet. Connect online and leave the dashboard open until data sync completes.';
  }
  const drec=await dbGet('kv','docs');if(drec?.value)docs=clone(drec.value);
  const crec=await dbGet('kv','categories');if(crec?.value)categories=clone(crec.value);
  const trec=await dbGet('kv','tools');if(trec?.value)tools=clone(trec.value);
  const wrec=await dbGet('kv','wdocs');if(wrec?.value)wdocs=clone(wrec.value);
  await refreshPendingCount();normalizePaymentState();dispatch();
  window.dispatchEvent(new CustomEvent('sayma:state'));
  return !!snap?.state;
 }catch(e){
  lastError='Offline cache could not be restored: '+e.message;
  console.error('SAYMA offline restore failed',e);
  dispatch();
  return false;
 }
}
function cloneSafe(x){return x===undefined?undefined:clone(x);}
function deepEqual(a,b){return JSON.stringify(a)===JSON.stringify(b);}
function isObj(x){return x&&typeof x==='object'&&!Array.isArray(x);}
function mergeThreeWay(base,local,remote,path='',conflicts=[],preferLocal=false){
 if(deepEqual(local,base))return cloneSafe(remote);
 if(deepEqual(remote,base)||deepEqual(local,remote))return cloneSafe(local);
 if(isObj(base)&&isObj(local)&&isObj(remote)){
  const out={},keys=new Set([...Object.keys(base),...Object.keys(local),...Object.keys(remote)]);
  for(const k of keys){
   const b=base[k],l=local[k],r=remote[k];
   if(l===undefined&&b!==undefined&&deepEqual(r,b))continue;
   if(r===undefined&&b!==undefined&&deepEqual(l,b))continue;
   out[k]=mergeThreeWay(b,l,r,path?path+'.'+k:k,conflicts,preferLocal);
  }
  return out;
 }
 if(Array.isArray(base)&&Array.isArray(local)&&Array.isArray(remote)){
  const sample=[...base,...local,...remote].find(x=>isObj(x));
  const idKey=sample?['id','passport','passport_no','name','key','slug'].find(k=>sample[k]!==undefined):null;
  if(idKey){
   const map=x=>new Map(x.map(v=>[String(v?.[idKey]),v]));
   const bm=map(base),lm=map(local),rm=map(remote),keys=new Set([...bm.keys(),...lm.keys(),...rm.keys()]),out=[];
   for(const id of keys){
    const b=bm.get(id),l=lm.get(id),r=rm.get(id);
    if(l===undefined&&r===undefined)continue;
    if(l===undefined){
     if(b===undefined){if(r!==undefined)out.push(clone(r));continue;}
     if(deepEqual(r,b))continue;
     conflicts.push(path+'['+id+'] delete/edit');if(!preferLocal&&r!==undefined)out.push(clone(r));continue;
    }
    if(r===undefined){
     if(b===undefined){if(l!==undefined)out.push(clone(l));continue;}
     if(deepEqual(l,b))continue;
     conflicts.push(path+'['+id+'] edit/delete');if(preferLocal)out.push(clone(l));continue;
    }
    out.push(mergeThreeWay(b,l,r,path+'['+id+']',conflicts,preferLocal));
   }
   return out;
  }
 }
 conflicts.push(path||'state');
 return cloneSafe(preferLocal?local:remote);
}
async function queueFile(file){
 const id='offline-file-'+Date.now()+'-'+Math.random().toString(36).slice(2,9);
 await storeFile(id,file);return id;
}
async function syncPendingQueue(){
 if(!connected||syncBusy)return false;
 syncBusy=true;
 try{
  const items=await queueItems();
  for(const op of items){
   const b=op.body||{};
   try{
    if(op.type==='project_financial_save'){
     const remotePack=await request('get_project_financials');
     const remoteItem=(remotePack.items||[]).find(x=>x.project_name===b.project_name&&x.month_name===b.month_name&&x.data_type===b.data_type);
     const remoteData=remoteItem?.data??null,baseData=b.base??null,localData=b.data;
     const conflicts=[];
     let resolved=mergeThreeWay(baseData,localData,remoteData,`projectGrids.${b.project_name}.${b.data_type}.${b.month_name}`,conflicts,false);
     if(conflicts.length){
      const keepPhone=window.confirm('This project financial sheet was also changed on the server while this phone was offline. Press OK to keep the phone version for conflicting cells; Cancel keeps this change pending without overwriting the server.');
      if(!keepPhone)throw Error('Project financial conflict needs review. The offline copy is still pending.');
      resolved=mergeThreeWay(baseData,localData,remoteData,`projectGrids.${b.project_name}.${b.data_type}.${b.month_name}`,[],true);
     }
     await request('save_project_financial',{project_name:b.project_name,month_name:b.month_name,data_type:b.data_type,data_json:JSON.stringify(resolved)});
     state.projectGrids[b.project_name]||={claims:{},salary:{}};state.projectGrids[b.project_name][b.data_type]||={};state.projectGrids[b.project_name][b.data_type][b.month_name]=clone(resolved);
    }else if(op.type==='doc_upload'||op.type==='doc_edit'){
     const file=b.fileId?await readFile(b.fileId):null;
     if((op.type==='doc_upload'||b.fileId)&&!file)throw Error('Queued document file is missing.');
     const fd=new FormData();
     if(b.kind==='worker'){
      fd.append('worker_key',String(b.worker?.passport||b.worker?.name||'').trim().toUpperCase());
      fd.append('worker_name',b.worker?.name||'');fd.append('passport_no',b.worker?.passport||'');fd.append('doc_type',b.data?.doc_type||'Other');
     }else{
      for(const k of ['title','category','issue_date','expiry_date','notes'])fd.append(k,b.data?.[k]??b.item?.[k]??'');
      if(op.type==='doc_edit'&&!file)fd.append('id',b.item.id);
     }
     if(file)fd.append('file',file);
     const uploadResult=await request(b.kind==='worker'?'upload_worker_document':(file?'upload_document':'update_document'),fd);
     if(file&&b.kind==='worker'){
      const workerList=await request('list_worker_documents',null,{worker_key:String(b.worker?.passport||b.worker?.name||'').trim().toUpperCase()});
      const saved=(workerList.documents||[]).find(x=>x.doc_type===(b.data?.doc_type||'Other')&&x.original_name===file.name);
      if(saved)await storeFile('server-doc-view_worker_document-'+saved.id,file);
     }else if(file&&uploadResult.id)await storeFile('server-doc-view_document-'+uploadResult.id,file);
     if(b.metaId){
      if(b.kind==='worker'){
       state.workerDocuments=(state.workerDocuments||[]).filter(x=>String(x.id)!==String(b.metaId));
       for(const k of Object.keys(wdocs))wdocs[k]=(wdocs[k]||[]).filter(x=>String(x.id)!==String(b.metaId));
      }else state.documents=(state.documents||[]).filter(x=>String(x.id)!==String(b.metaId));
     }
     if(b.fileId)await dropFile(b.fileId);if(b.metaId)await dropFile(b.metaId);
    }else if(op.type==='doc_delete'){
     await request(b.kind==='worker'?'delete_worker_document':'delete_document',{id:b.item.id});
    }else if(op.type==='claim_pdf_upload'){
     const file=await readFile(b.fileId);if(!file)throw Error('Queued claim PDF is missing.');
     const fd=new FormData();fd.append('claim_id',String(b.claimId));fd.append('site',String(b.site));fd.append('month',String(b.month));fd.append('file',file);
     await request('upload_claim_pdf',fd);await storeFile('claim-pdf-'+String(b.claimId||''),file);await dropFile(b.fileId);
    }else if(op.type==='profile_save'){
     await extraRequest('profile.php','save',b.profile);
     await dbPut('kv',{id:'profile',value:b.profile});
     await cacheExtra('profile.php','get',{ok:true,profile:b.profile});
    }else if(op.type==='reminders_save'){
     await extraRequest('reminders.php','save',b.settings);
    }else if(op.type==='category_add'){
     await request('add_document_category',{label:b.label});
    }else if(op.type==='category_rename'){
     await request('rename_document_category',{key:b.key,label:b.label});
    }else if(op.type==='tool_add'){
     await request('add_documentation_tool',b.item);
    }else if(op.type==='tool_delete'){
     await request('delete_documentation_tool',{id:b.id});
    }
    await queueRemove(op.id);
   }catch(e){
    lastError='Sync paused: '+e.message+' · '+op.type;dispatch();break;
   }
  }
  if((await queueItems()).length===0){dirty=false;lastError='';await cacheSnapshot();}
  await refreshPendingCount();return pendingCountValue===0;
 }finally{syncBusy=false;dispatch();}
}
async function syncStateQueue(serverState){
 const op=(await queueItems()).find(x=>x.type==='state_save');if(!op)return serverState;
 const local=op.body?.state||statePayload(),base=op.body?.base||serverState||{},conflicts=[];
 let merged=mergeThreeWay(base,local,serverState||{},'state',conflicts,false);
 if(conflicts.length){
  const ok=window.confirm("Some records changed both on this device and on the server while offline. Press OK to keep this device's version for the conflicting fields and sync; Cancel to keep the changes pending for review.");
  if(!ok){lastError='Sync paused: '+conflicts.length+' data conflict(s) need review.';dispatch();return null;}
  merged=mergeThreeWay(base,local,serverState||{},'state',[],true);
 }
 await request('save_state',{state_json:JSON.stringify(merged)});
 for(const k of STATE_KEYS)if(merged[k]!==undefined)state[k]=clone(merged[k]);
 await queueRemove(op.id);dirty=false;
 await dbPut('kv',{id:'base-state',value:statePayload()});await cacheSnapshot();
 return merged;
}

function sameOriginApi(){let url=new URL(config.apiPath,location.href);if(!['http:','https:'].includes(url.protocol)||url.origin!==location.origin)throw Error('API path must be on the same website as the app.');return url;}
function apiUrl(action, params={}){const u=sameOriginApi();u.searchParams.set('action',action);for(const [k,v] of Object.entries(params))u.searchParams.set(k,v);return u.href;}
async function request(action,body=null,params={}){let opts={credentials:'same-origin',cache:'no-store',headers:{Accept:'application/json'}};if(body!==null){opts.method='POST';if(body instanceof FormData){if(!body.has('action'))body.append('action',action);opts.body=body;}else{opts.headers['Content-Type']='application/x-www-form-urlencoded; charset=UTF-8';opts.body=new URLSearchParams(body);}}let r;try{r=await fetch(apiUrl(action,params),opts)}catch(e){connected=false;lastError='Network unavailable — changes can be queued offline.';dispatch();throw e;}let data;try{data=await r.json()}catch{throw Error('API returned non-JSON response (HTTP '+r.status+'). Check the PHP path.')}if(!r.ok||!data.ok){const err=new Error(data.error||'API error (HTTP '+r.status+')');if(r.status===401||data.auth_required){connected=false;lastError='Login session expired. Sign in again; queued changes will remain on this device.';err.authRequired=true;dispatch();}throw err;}return data;}
async function extraRequest(script,action,body=null){
 if(!['profile.php','reminders.php'].includes(script))throw Error('Unsupported backend endpoint');
 if(!connected){
  if(body&&script==='profile.php'&&action==='save'){
   await dbPut('kv',{id:'profile',value:clone(body)});await cacheExtra('profile.php','get',{ok:true,profile:body});
   await queueAdd('profile_save',{profile:body});return {ok:true,pending:true,profile:body};
  }
  if(body&&script==='reminders.php'&&action==='save'){
   await queueAdd('reminders_save',{settings:body});
   await cacheExtra('reminders.php','get',{ok:true,settings:body.settings_json?JSON.parse(body.settings_json):{}});
   return {ok:true,pending:true};
  }
  const cached=await readExtra(script,action);if(cached)return cached;
  if(script==='profile.php'&&action==='get'){const p=await dbGet('kv','profile');if(p?.value)return {ok:true,profile:p.value};}
  throw Error('This information is not cached yet. Connect once while online to load it.');
 }
 let url=new URL(script,sameOriginApi());url.searchParams.set('action',action);
 let opts={credentials:'same-origin',cache:'no-store',headers:{Accept:'application/json'}};
 if(body){opts.method='POST';let fd=new FormData();fd.append('action',action);for(const [k,v] of Object.entries(body))fd.append(k,v);opts.body=fd}
 let res,d;
 try{res=await fetch(url,opts);d=await res.json().catch(()=>{throw Error(script+' returned invalid JSON')});}
 catch(e){
  if(body&&(!navigator.onLine||e instanceof TypeError)&&(script==='profile.php'&&action==='save'||script==='reminders.php'&&action==='save')){
   connected=false;lastError='Offline — write queued for sync.';dispatch();
   if(script==='profile.php'){await dbPut('kv',{id:'profile',value:clone(body)});await cacheExtra('profile.php','get',{ok:true,profile:body});await queueAdd('profile_save',{profile:body});}
   else await queueAdd('reminders_save',{settings:body});
   return {ok:true,pending:true};
  }
  throw e;
 }
 if(!res.ok||!d.ok){
  if(res.status===401||d.auth_required){
   connected=false;lastError='Login session expired. Sign in again; queued changes will remain on this device.';dispatch();
   if(body&&script==='profile.php'&&action==='save'){await dbPut('kv',{id:'profile',value:clone(body)});await cacheExtra('profile.php','get',{ok:true,profile:body});await queueAdd('profile_save',{profile:body});return {ok:true,pending:true};}
   if(body&&script==='reminders.php'&&action==='save'){await cacheExtra('reminders.php','get',{ok:true,settings:body.settings_json?JSON.parse(body.settings_json):{}});await queueAdd('reminders_save',{settings:body});return {ok:true,pending:true};}
   const err=new Error(d.error||script+' requires login');err.authRequired=true;throw err;
  }
  throw Error(d.error||script+' failed');
 }
 await cacheExtra(script,action,d);
 if(script==='profile.php'&&action==='get'&&d.profile){await dbPut('kv',{id:'profile',value:d.profile});}
 if(script==='profile.php'&&action==='save'&&body){await dbPut('kv',{id:'profile',value:clone(body)});await cacheExtra('profile.php','get',{ok:true,profile:body});}
 return d;
}
async function blob(action,id){const cacheKey='server-doc-'+action+'-'+id;if(!connected){const cached=await readFile(cacheKey);if(cached)return cached;throw Error('This file has not been downloaded on this device yet. Connect once to cache it for offline viewing.')}let r;try{r=await fetch(apiUrl(action,{id}),{credentials:'same-origin',cache:'no-store'})}catch(e){connected=false;dispatch();const cached=await readFile(cacheKey);if(cached)return cached;throw Error('Network unavailable and no offline copy exists for this file.')}if(r.status===401){connected=false;lastError='Login session expired. Sign in again.';dispatch();throw Error('Login session expired. Please sign in again.')}if(!r.ok)throw Error('Could not load file (HTTP '+r.status+').');const ct=r.headers.get('content-type')||'';if(ct.includes('application/json')){const d=await r.json();throw Error(d.error||'File unavailable')}const file=await r.blob();try{await storeFile(cacheKey,file)}catch(e){console.warn('Could not cache document for offline use',e)}return file}
async function uploadClaimPdf(site,month,claimId,file){
 if(!file)throw Error('Choose a PDF file');
 if(file.size>15*1024*1024)throw Error('PDF exceeds the 15 MB upload limit');
 if(file.type!=='application/pdf'&&!String(file.name||'').toLowerCase().endsWith('.pdf'))throw Error('Only PDF files are accepted');
 if(!connected){const fileId=await queueFile(file);await queueAdd('claim_pdf_upload',{site,month,claimId,fileId,fileName:file.name});return {pending:true};}
 const fd=new FormData();fd.append('claim_id',String(claimId||''));fd.append('site',String(site||''));fd.append('month',String(month||''));fd.append('file',file);
 try{const result=await request('upload_claim_pdf',fd);await storeFile('claim-pdf-'+String(claimId||''),file);return result;}catch(e){if(!isNetworkFailure(e)&&!e.authRequired)throw e;connected=false;const fileId=await queueFile(file);await queueAdd('claim_pdf_upload',{site,month,claimId,fileId,fileName:file.name});return {pending:true};}
}
async function claimPdfBlob(claimId){
 if(!connected){const cached=await readFile('claim-pdf-'+String(claimId||''));if(cached)return cached;throw Error('This Claim PDF has not been viewed on this device yet. Connect once to cache it for offline viewing.');}
 const url=apiUrl('view_claim_pdf',{claim_id:String(claimId||'')});const r=await fetch(url,{credentials:'same-origin',cache:'no-store'});
 if(!r.ok)throw Error(r.status===404?'No PDF is stored for this Claim ID.':'Could not load claim PDF (HTTP '+r.status+').');
 const ct=r.headers.get('content-type')||'';if(ct.includes('application/json')){const d=await r.json();throw Error(d.error||'Claim PDF unavailable')}
 const file=await r.blob();try{await storeFile('claim-pdf-'+String(claimId||''),file)}catch(e){}return file;
}

function persist(markDirty=true){
 if(!window.SAYMA_PRODUCTION){try{localStorage.setItem(JKEY,JSON.stringify(state));}catch(err){console.warn('Legacy local cache full',err);}}
 if(markDirty){dirty=true;if(!window.SAYMA_PRODUCTION)localStorage.setItem(JKEY+'-dirty','1');}
 cacheSnapshot().catch(err=>{lastError='Offline cache save failed: '+err.message;dispatch();});
 window.dispatchEvent(new CustomEvent('sayma:state'));
}

function normalizePaymentRecord(record){
 const r=clone(record||{});
 r.entries=Array.isArray(r.entries)?r.entries:[];
 r.costItems=Array.isArray(r.costItems)?r.costItems:[];
 if(!r.costItems.length && Number(r.target||0)>0){
  r.costItems.push({id:'legacy-base',part:'Existing total (previously saved)',amount:Number(r.target||0),date:'',legacy:true});
 }
 if(r.costItems.length){
  r.target=r.costItems.reduce((sum,item)=>sum+Math.max(0,Number(item.amount||0)),0);
 }else{
  r.target=Math.max(0,Number(r.target||0));
 }
 r.paid=Math.max(0,Number(r.paid||0));
 return r;
}
function normalizePaymentState(){
 state.permitPayments ||= {};
 for(const [passport,years] of Object.entries(state.permitPayments)){
  if(!years || typeof years!=='object') continue;
  for(const [year,record] of Object.entries(years)){
   if(!/^\d{4}$/.test(year)) continue;
   years[year]=normalizePaymentRecord(record);
  }
 }
}
async function syncWorkerPaymentLedger(){
 if(!connected) return;
 normalizePaymentState();
 const payments=state.permitPayments||{};
 for(const passport of Object.keys(payments).sort()){
  if(!passport || !payments[passport] || typeof payments[passport]!=='object') continue;
  for(const year of Object.keys(payments[passport]).sort()){
   if(!/^\d{4}$/.test(year)) continue;
   const record=normalizePaymentRecord(payments[passport][year]);
   const previous=paymentLedgerSnapshot?.[passport]?.[year];
   const encoded=JSON.stringify(record);
   if(previous && JSON.stringify(previous)===encoded) continue;
   await request('save_worker_payment_record',{
    passport,
    year,
    record_json:encoded
   });
   state.permitPayments[passport][year]=record;
   paymentLedgerSnapshot[passport] ||= {};
   paymentLedgerSnapshot[passport][year]=clone(record);
  }
 }
}
async function loadWorkerPaymentLedger(localPaymentChanges={}){
 const result=await request('get_worker_payment_ledger');
 const serverRecords=clone(result.records||{});
 const merged=clone(serverRecords);
 const legacy=clone(state.permitPayments||{});
 // Preserve records from the main state if a partially populated dedicated
 // ledger does not yet contain them. Existing server-ledger rows always win.
 for(const [passport,years] of Object.entries(legacy)){
  if(!years || typeof years!=='object') continue;
  merged[passport] ||= {};
  for(const [year,record] of Object.entries(years)){
   if(!/^\d{4}$/.test(year)) continue;
   if(Object.prototype.hasOwnProperty.call(localPaymentChanges[passport]||{},year)){
    const change=localPaymentChanges[passport][year],conflicts=[];
    const serverRecord=merged[passport][year];
    merged[passport][year]=mergeThreeWay(change.base,change.local,serverRecord,`permitPayments.${passport}.${year}`,conflicts,true);
    if(conflicts.length)lastError='Worker payment changes overlapped on the server. The phone version was kept for the conflicting fields.';
   }else if(merged[passport][year]===undefined) merged[passport][year]=clone(record);
  }
 }
 // Match the existing Desktop default for newly added workers without calling
 // initialize_worker_payment_2026 (that action also removes legacy years).
 const defaultCosts=[
  {part:'EPASS',amount:200},
  {part:'Levy',amount:2350},
  {part:'CIDB / Green Card',amount:200},
  {part:'Insurance',amount:300},
  {part:'Medical / FOMEMA',amount:500},
  {part:'Processing Fee',amount:700},
  {part:'EPF',amount:800}
 ];
 for(const worker of (state.workers||[])){
  const passport=String(worker.passport||'').trim();
  if(!passport) continue;
  merged[passport] ||= {};
  if(merged[passport]['2026']===undefined){
   const costItems=defaultCosts.map((item,index)=>({
    id:'default-2026-'+index,part:item.part,amount:item.amount,date:'',defaultRate:true
   }));
   merged[passport]['2026']={
    target:costItems.reduce((sum,item)=>sum+item.amount,0),
    paid:0,status:'',entries:[],costItems
   };
  }
 }
 state.permitPayments=merged;
 const years=new Set([
  ...(Array.isArray(result.years)?result.years.map(String):[]),
  ...(Array.isArray(state.permitYears)?state.permitYears.map(String):[]),
  ...Object.values(merged).flatMap(v=>Object.keys(v||{}))
 ]);
 // Mirror the Desktop's visible-year rule; records themselves are retained.
 state.permitYears=Array.from(years)
  .filter(y=>/^\d{4}$/.test(y)&&y!=='2025'&&y!=='2027')
  .sort();
 if(!state.permitYears.includes('2026') && (state.workers||[]).length) state.permitYears.push('2026');
 state.permitYears=Array.from(new Set(state.permitYears)).sort();
 paymentLedgerSnapshot=serverRecords;
 normalizePaymentState();
 await syncWorkerPaymentLedger();
}
let saving=Promise.resolve();
async function save(){
 normalizePaymentState();persist(true);
 const payload=statePayload();
 if(!connected){
  dirty=true;
  for(const q of await queueItems())if(q.type==='state_save')await queueRemove(q.id);
  const baseRec=await dbGet('kv','base-state');
  await queueAdd('state_save',{state:payload,base:baseRec?.value||null});
  await cacheSnapshot();return {offline:true,pending:true};
 }
 saving=saving.catch(()=>{}).then(async()=>{
  await request('save_state',{state_json:JSON.stringify(payload)});
  await syncWorkerPaymentLedger();dirty=false;
  for(const q of await queueItems())if(q.type==='state_save')await queueRemove(q.id);
  await dbPut('kv',{id:'base-state',value:payload});await cacheSnapshot();lastError='';
  window.dispatchEvent(new Event('sayma:connection'));return true;
 }).catch(async e=>{
  if(!isNetworkFailure(e)&&!e.authRequired){lastError='Save failed: '+e.message;dispatch();throw e;}
  lastError=e.authRequired?'Login required. Changes are saved locally and will sync after sign-in.':'Connection lost while saving. Changes are queued on this device.';
  const baseRec=await dbGet('kv','base-state');
  for(const q of await queueItems())if(q.type==='state_save')await queueRemove(q.id);
  await queueAdd('state_save',{state:payload,base:baseRec?.value||null});
  await cacheSnapshot();dispatch();return {offline:true,pending:true,error:e.message};
 });
 return saving;
}

async function projectSave(name,type,month){
 let data=state.projectGrids?.[name]?.[type]?.[month];if(!data)return {offline:!connected};
 const enqueue=async()=>{const baseRec=await dbGet('kv','base-state');const baseData=baseRec?.value?.projectGrids?.[name]?.[type]?.[month]??null;for(const q of await queueItems())if(q.type==='project_financial_save'&&q.body?.project_name===name&&q.body?.data_type===type&&q.body?.month_name===month)await queueRemove(q.id);await queueAdd('project_financial_save',{project_name:name,month_name:month,data_type:type,data:clone(data),base:clone(baseData)});await cacheSnapshot();return {offline:true,pending:true};};
 if(!connected)return enqueue();
 try{await request('save_project_financial',{project_name:name,month_name:month,data_type:type,data_json:JSON.stringify(data)});await cacheSnapshot();return {synced:true};}
 catch(e){if(!isNetworkFailure(e)&&!e.authRequired)throw e;connected=false;lastError='Project financial update saved locally and queued for sync.';dispatch();return enqueue();}
}

async function test(){
 if(config.mode==='offline'){connected=false;lastError='Offline mode selected';dispatch();return false;}
 try{
  let r=await request('setup_status');
  if(!r.logged_in){connected=false;username='';lastError='Server reachable — login required';dispatch();return false;}
  connected=true;username=r.username||'';lastError='';dispatch();return true;
 }catch(e){connected=false;lastError=e.message;dispatch();return false;}
}
function dispatch(){window.dispatchEvent(new Event('sayma:connection'));}
function isNetworkFailure(e){return !navigator.onLine||e instanceof TypeError||/failed to fetch|networkerror|network request failed/i.test(String(e?.message||''));}
window.addEventListener('offline',()=>{connected=false;lastError='Offline — showing the last saved data. Pending changes will sync when the connection returns.';dispatch();});
function ensureServerWrite(feature='this action'){if(!connected)lastError='Offline — '+feature+' will be queued on this device.';}
async function connect(){
 if(connectBusy)return connected;connectBusy=true;
 try{
  if(!await test()){await refreshPendingCount();return false;}
  try{
   const d=await request('get_state');
   const pendingState=(await queueItems()).find(x=>x.type==='state_save');
   const localPaymentChanges={};
   if(pendingState){
    const baseRec=await dbGet('kv','base-state');
    const basePayments=baseRec?.value?.permitPayments||{};
    const localPayments=state.permitPayments||{};
    for(const [passport,years] of Object.entries(localPayments)){
     if(!years||typeof years!=='object')continue;
     for(const [year,record] of Object.entries(years)){
      if(/^\d{4}$/.test(year)&&!deepEqual(record,basePayments?.[passport]?.[year])){
       localPaymentChanges[passport]||={};localPaymentChanges[passport][year]={base:cloneSafe(basePayments?.[passport]?.[year]),local:clone(record)};
      }
     }
    }
    const merged=await syncStateQueue(d.state&&Array.isArray(d.state.workers)?clone(d.state):{});
   if(!merged){connected=false;dispatch();return false;}
   }else if(d.state&&Array.isArray(d.state.workers)){
    for(const k of STATE_KEYS)if(d.state[k]!==undefined)state[k]=clone(d.state[k]);
    dirty=false;await dbPut('kv',{id:'base-state',value:statePayload()});persist(false);
   }
   // Save the core worker/project/finance state now. Optional document or tool
   // endpoints must not prevent the main company snapshot from being cached.
   await cacheSnapshot();
   try{await loadWorkerPaymentLedger(localPaymentChanges);await cacheSnapshot();}
   catch(e){console.warn('SAYMA payment ledger cache refresh failed; keeping core snapshot',e);}
   try{await reloadDocs();}catch(e){console.warn('SAYMA document refresh failed; keeping last cached documents',e);}
   try{await reloadTools();}catch(e){console.warn('SAYMA tools refresh failed; keeping last cached tools',e);}
   await cacheSnapshot();
   await syncPendingQueue();
   if(!connected){await refreshPendingCount();await cacheSnapshot();dispatch();return false;}
   // The backend stores project financial sheets in sb_project_financial_data,
   // separate from sb_app_state. Refresh them only after queued local sheets sync.
   try{
    const f=await request('get_project_financials');
    const pendingFinancials=await queueItems();
    for(const r of f.items||[]){
     if(!['salary','claims'].includes(r.data_type)||!r.data||!r.project_name||!r.month_name)continue;
     const stillPending=pendingFinancials.some(q=>q.type==='project_financial_save'&&q.body?.project_name===r.project_name&&q.body?.month_name===r.month_name&&q.body?.data_type===r.data_type);
     if(stillPending)continue;
     state.projectGrids[r.project_name]||={claims:{},salary:{}};
     state.projectGrids[r.project_name][r.data_type]||={};
     state.projectGrids[r.project_name][r.data_type][r.month_name]=r.data;
    }
   }catch(e){console.warn('Project financial sync:',e)}
   try{await reloadDocs();}catch(e){console.warn('SAYMA final document refresh failed',e);}
   try{await reloadTools();}catch(e){console.warn('SAYMA final tools refresh failed',e);}
   const remainingQueue=await queueItems();
   if(!remainingQueue.some(q=>q.type==='state_save'||q.type==='project_financial_save'))await dbPut('kv',{id:'base-state',value:statePayload()});
   await cacheSnapshot();
   lastError=pendingCountValue?(lastError||'Some changes are waiting to sync.'):'';
   dispatch();return true;
  }catch(e){lastError='Server load/sync failed: '+e.message;connected=false;dispatch();return false;}
 }finally{connectBusy=false;}
}
async function reloadDocs(){
 if(connected){
  const d=await request('list_documents');docs=d.documents||[];
  try{let c=await request('list_document_categories');categories=(c.categories||c.items||[]).map(x=>typeof x==='string'?{key:x,label:x}:x)}catch{categories=[]}
  const pending=await queueItems();
  for(const q of pending){
   if(q.type==='doc_delete'&&q.body?.kind!=='worker')docs=docs.filter(x=>String(x.id)!==String(q.body.item?.id));
   if(q.type==='doc_edit'&&q.body?.kind!=='worker'){const rec=docs.find(x=>String(x.id)===String(q.body.item?.id));if(rec)Object.assign(rec,q.body.data,{pending:true});}
   if(q.type==='doc_upload'&&q.body?.kind!=='worker'){const m=(state.documents||[]).find(x=>x.id===q.body.metaId);if(m&&!docs.some(x=>String(x.id)===String(m.id)))docs.push(m);}
   if(q.type==='category_add'&&!categories.some(c=>c.label===q.body.label))categories.push({key:q.body.label.trim().toUpperCase().replace(/[^A-Z0-9]/g,'_'),label:q.body.label,pending:true});
   if(q.type==='category_rename'){const c=categories.find(c=>c.key===q.body.key);if(c)c.label=q.body.label;}
  }
  const localPending=(state.documents||[]).filter(x=>x.local||x.pending);
  state.documents=clone(docs).concat(localPending.filter(x=>!docs.some(y=>String(y.id)===String(x.id))));
 }else{docs=state.documents||[];categories=state.documentCategoryObjects||categories||defaults;}
 if(!categories.length)categories=defaults;
 await dbPut('kv',{id:'docs',value:clone(docs)});await dbPut('kv',{id:'categories',value:clone(categories)});dispatch();
}
async function workerDocs(worker){
 const key=(worker.passport||worker.name||'').trim().toUpperCase();
 if(connected){
  let r=await request('list_worker_documents',null,{worker_key:key});wdocs[key]=r.documents||[];
  for(const q of await queueItems()){
   const qKey=String(q.body?.worker?.passport||q.body?.worker?.name||q.body?.item?.worker_key||'').trim().toUpperCase();
   if(q.type==='doc_delete'&&q.body?.kind==='worker'&&qKey===key)wdocs[key]=wdocs[key].filter(x=>String(x.id)!==String(q.body.item.id));
   if(q.type==='doc_upload'&&q.body?.kind==='worker'&&qKey===key){const m=(state.workerDocuments||[]).find(x=>x.id===q.body.metaId);if(m)wdocs[key].push(m);}
  }
 }else{
  const cached=Array.isArray(wdocs[key])?wdocs[key]:[];
  const local=(state.workerDocuments||[]).filter(x=>x.worker_key===key);
  wdocs[key]=cached.concat(local.filter(x=>!cached.some(y=>String(y.id)===String(x.id))));
 }
 await dbPut('kv',{id:'wdocs',value:clone(wdocs)});return wdocs[key];
}
async function reloadTools(){
 if(connected){
  try{tools=(await request('list_documentation_tools')).items||[]}catch{tools=[]}
  for(const q of await queueItems()){
   if(q.type==='tool_add'&&!tools.some(t=>t.url===q.body.item.url))tools.push({...q.body.item,pending:true});
   if(q.type==='tool_delete')tools=tools.filter(t=>String(t.id)!==String(q.body.id));
  }
 }else tools=state.documentationTools||[];
 await dbPut('kv',{id:'tools',value:clone(tools)});
}

function idb(){return new Promise((resolve,reject)=>{if(!indexedDB){reject(Error('IndexedDB not available. Use localhost or HTTPS for local document files.'));return;}let req=indexedDB.open('sayma-mobile-attachments-v2',1);req.onupgradeneeded=()=>req.result.createObjectStore('files');req.onsuccess=()=>resolve(req.result);req.onerror=()=>reject(req.error);})}
async function storeFile(id,file){let db=await idb();return new Promise((resolve,reject)=>{let tx=db.transaction('files','readwrite');tx.objectStore('files').put(file,id);tx.oncomplete=()=>{db.close();resolve()};tx.onerror=()=>{db.close();reject(tx.error)}})}
async function readFile(id){let db=await idb();return new Promise((resolve,reject)=>{let tx=db.transaction('files','readonly'),req=tx.objectStore('files').get(id);req.onsuccess=()=>{db.close();resolve(req.result||null)};req.onerror=()=>{db.close();reject(req.error)}})}
async function dropFile(id){let db=await idb();return new Promise((resolve,reject)=>{let tx=db.transaction('files','readwrite');tx.objectStore('files').delete(id);tx.oncomplete=()=>{db.close();resolve()};tx.onerror=()=>{db.close();reject(tx.error)}})}
function localId(){return 'local-'+Date.now()+'-'+Math.random().toString(36).slice(2,8)}
async function uploadDoc(data,file,kind='company',worker){
 if(!file)throw Error('Choose a file');if(file.size>15*1024*1024)throw Error('File exceeds 15 MB limit');
 if(connected){
  const fd=new FormData();
  if(kind==='worker'){fd.append('worker_key',(worker.passport||worker.name||'').trim().toUpperCase());fd.append('worker_name',worker.name||'');fd.append('passport_no',worker.passport||'');fd.append('doc_type',data.doc_type||'Other');}
  else{for(const k of ['title','category','issue_date','expiry_date','notes'])fd.append(k,data[k]||'');}
  fd.append('file',file);
  try{const result=await request(kind==='worker'?'upload_worker_document':'upload_document',fd);if(kind==='worker'){const list=await workerDocs(worker);const saved=list.find(x=>x.doc_type===(data.doc_type||'Other')&&x.original_name===file.name);if(saved)await storeFile('server-doc-view_worker_document-'+saved.id,file);}else{await reloadDocs();if(result.id)await storeFile('server-doc-view_document-'+result.id,file);}return {synced:true};}
  catch(e){if(!isNetworkFailure(e)&&!e.authRequired)throw e;connected=false;lastError='Upload queued — waiting for internet.';}
 }
 const meta={...data,id:localId(),original_name:file.name,uploaded_at:new Date().toISOString(),version:1,local:true,pending:true};
 const fileId=await queueFile(file);meta.fileId=fileId;await storeFile(meta.id,file);
 if(kind==='worker'){meta.worker_key=(worker.passport||worker.name||'').trim().toUpperCase();state.workerDocuments||=[];state.workerDocuments.push(meta);wdocs[meta.worker_key]||=[];wdocs[meta.worker_key].push(meta);}
 else{state.documents||=[];state.documents.push(meta);docs=state.documents;}
 await queueAdd('doc_upload',{kind,data:clone(data),worker:worker?{name:worker.name,passport:worker.passport}:null,fileId,metaId:meta.id});
 persist();await cacheSnapshot();return {pending:true,item:meta};
}
async function editDoc(item,data,file){
 if(connected){
  try{const fd=new FormData();for(const k of ['title','category','issue_date','expiry_date','notes'])fd.append(k,data[k]??item[k]??'');if(file){if(file.size>15*1024*1024)throw Error('File exceeds 15 MB');fd.append('file',file);const result=await request('upload_document',fd);if(result.id)await storeFile('server-doc-view_document-'+result.id,file);}else{fd.append('id',item.id);await request('update_document',fd);}await reloadDocs();return {synced:true};}
  catch(e){if(!isNetworkFailure(e)&&!e.authRequired)throw e;connected=false;lastError='Document update queued — waiting for internet or sign-in.';}
 }
 const rec=state.documents.find(x=>String(x.id)===String(item.id));if(!rec)throw Error('Document missing');
 if(rec.local){
  const q=(await queueItems()).find(x=>x.type==='doc_upload'&&x.body?.metaId===rec.id);
  if(!q)throw Error('Offline document upload queue entry is missing.');
  if(file){if(file.size>15*1024*1024)throw Error('File exceeds 15 MB');const newFileId=await queueFile(file);await dropFile(q.body.fileId);q.body.fileId=newFileId;await storeFile(rec.id,file);rec.original_name=file.name;rec.version=(rec.version||1)+1;}
  Object.assign(rec,data);q.body.data=clone({...q.body.data,...data});await dbPut('queue',q);persist();await cacheSnapshot();return {pending:true};
 }
 let fileId=null;if(file){if(file.size>15*1024*1024)throw Error('File exceeds 15 MB');fileId=await queueFile(file);const previous=await readFile(rec.id)||await readFile('server-doc-view_document-'+rec.id);if(previous)await storeFile(rec.id+'-v'+(rec.version||1),previous);await storeFile(rec.id,file);rec.original_name=file.name;rec.version=(rec.version||1)+1;rec.pending=true;}
 Object.assign(rec,data);await queueAdd('doc_edit',{item:clone(item),data:clone(data),fileId,metaId:rec.id,kind:'company'});
 persist();await reloadDocs();return {pending:true};
}
async function historyDoc(item){if(connected){let r=await request('document_history',null,{id:item.id});return r.history||[]}return [{id:item.id,version:item.version||1,original_name:item.original_name,uploaded_at:item.uploaded_at},...Array.from({length:(item.version||1)-1},(_,i)=>({id:item.id+'-v'+(i+1),version:i+1,original_name:'Saved previous version',uploaded_at:item.uploaded_at}))];}
async function documentBlob(item,kind='company'){if(connected)return blob(kind==='worker'?'view_worker_document':'view_document',item.id);if(item.local||item.pending){const local=await readFile(item.id);if(local)return local;}return await readFile('server-doc-'+(kind==='worker'?'view_worker_document':'view_document')+'-'+item.id)}
async function deleteDoc(item,kind='company'){
 if(connected){try{await request(kind==='worker'?'delete_worker_document':'delete_document',{id:item.id});if(kind==='company')await reloadDocs();return {synced:true};}catch(e){if(!isNetworkFailure(e)&&!e.authRequired)throw e;connected=false;}}
 if(String(item.id).startsWith('local-')){
  for(const q of await queueItems())if(q.body?.metaId===item.id){if(q.body.fileId)await dropFile(q.body.fileId);await queueRemove(q.id);}
 }else await queueAdd('doc_delete',{item:clone(item),kind});
 await dropFile(item.fileId||item.id);await dropFile('server-doc-'+(kind==='worker'?'view_worker_document':'view_document')+'-'+item.id);
 if(kind==='worker'){state.workerDocuments=(state.workerDocuments||[]).filter(x=>String(x.id)!==String(item.id));const key=item.worker_key||'';wdocs[key]=(wdocs[key]||[]).filter(x=>String(x.id)!==String(item.id));}
 else{state.documents=(state.documents||[]).filter(x=>String(x.id)!==String(item.id));docs=state.documents;}
 persist();await reloadDocs();return {pending:!connected};
}
async function addCategory(label){
 if(connected){try{await request('add_document_category',{label});await reloadDocs();return {synced:true};}catch(e){if(!isNetworkFailure(e)&&!e.authRequired)throw e;connected=false;}}
 const key=label.trim().toUpperCase().replace(/[^A-Z0-9]/g,'_');if(categories.some(x=>x.key===key||x.label.toLowerCase()===label.toLowerCase()))throw Error('Category exists');
 categories.push({key,label,pending:true});state.documentCategoryObjects=categories;await queueAdd('category_add',{label});persist();return {pending:true};
}
async function renameCategory(key,label){
 if(connected){try{await request('rename_document_category',{key,label});await reloadDocs();return {synced:true};}catch(e){if(!isNetworkFailure(e)&&!e.authRequired)throw e;connected=false;}}
 const c=categories.find(c=>c.key===key);if(!c)throw Error('Category not found');c.label=label;state.documentCategoryObjects=categories;await queueAdd('category_rename',{key,label});persist();return {pending:true};
}
async function addTool(item){
 if(connected){try{await request('add_documentation_tool',item);await reloadTools();return {synced:true};}catch(e){if(!isNetworkFailure(e)&&!e.authRequired)throw e;connected=false;}}
 const local={...item,id:localId(),pending:true};state.documentationTools||=[];state.documentationTools.push(local);tools=state.documentationTools;await queueAdd('tool_add',{item,localId:local.id});persist();return {pending:true};
}
async function deleteTool(item){
 if(connected){try{await request('delete_documentation_tool',{id:item.id});await reloadTools();return {synced:true};}catch(e){if(!isNetworkFailure(e)&&!e.authRequired)throw e;connected=false;}}
 state.documentationTools=(state.documentationTools||[]).filter(x=>x.id!==item.id);tools=state.documentationTools;
 if(String(item.id).startsWith('local-')){for(const q of await queueItems())if(q.type==='tool_add'&&q.body?.localId===item.id)await queueRemove(q.id);}
 else await queueAdd('tool_delete',{id:item.id});
 persist();return {pending:true};
}
async function vault(action,body){if(!connected)throw Error('Vault requires authenticated server API.');return request('vault_'+action,body)}
async function exportData(){const fileMeta=[];for(const doc of [...(state.documents||[]),...(state.workerDocuments||[])]){if(!doc.local)continue;let f=await readFile(doc.id);if(!f)continue;const data=await new Promise((resolve,reject)=>{let fr=new FileReader();fr.onload=()=>resolve(fr.result);fr.onerror=()=>reject(fr.error);fr.readAsDataURL(f)});fileMeta.push({id:doc.id,data,mime:f.type})}return JSON.stringify({format:'SAYMA_V2',exportedAt:new Date().toISOString(),state:clone(state),localFiles:fileMeta},null,2)}
async function importData(json){ensureServerWrite('importing a backup');if(!json?.state||!Array.isArray(json.state.workers)||!Array.isArray(json.state.projects))throw Error('Invalid SAYMA backup');for(const k in state)delete state[k];Object.assign(state,clone(json.state));for(const item of json.localFiles||[]){if(!String(item.data||'').startsWith('data:'))continue;const response=await fetch(item.data);await storeFile(item.id,await response.blob())}persist();await save();await reloadDocs();await reloadTools();}
window.SAYMA={state,config,save,persist,connect,test,request,extraRequest,apiUrl,blob,uploadClaimPdf,claimPdfBlob,projectSave,reloadDocs,reloadTools,workerDocs,uploadDoc,editDoc,historyDoc,documentBlob,deleteDoc,addCategory,renameCategory,addTool,deleteTool,vault,exportData,importData,restoreOffline,syncPendingQueue, get online(){return connected},get dirty(){return dirty},get username(){return username},get error(){return lastError},get docs(){return docs},get categories(){return categories},get tools(){return tools},get workerDocsCache(){return wdocs},get pendingCount(){return pendingCountValue},get offlineReady(){return !!window.indexedDB},changeConfig(c){Object.assign(config,c);localStorage.setItem(CONFKEY,JSON.stringify(config));dispatch();},get canUseServer(){return location.protocol!=='file:'}};
refreshPendingCount().then(()=>dispatch()).catch(()=>{});
})();
