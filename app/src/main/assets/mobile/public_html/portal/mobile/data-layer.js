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
if(window.SAYMA_PRODUCTION && /\/mobile(?:\/|$)/.test(location.pathname) && config.apiPath==='./api.php') config.apiPath='../api.php';
config.mode ||= 'auto';
let connected=false, username='', lastError='', dirty=!window.SAYMA_PRODUCTION && localStorage.getItem(JKEY+'-dirty')==='1', paymentLedgerSnapshot={};
let docs=[], categories=[], tools=[], wdocs={};
const defaults=[{key:'SSM',label:'SSM'},{key:'CIDB',label:'CIDB'},{key:'MAJLIS',label:'Majlis License'},{key:'EPF',label:'EPF'},{key:'SOCSO',label:'SOCSO / PERKESO'},{key:'LHDN',label:'LHDN / Tax'},{key:'INSURANCE',label:'Insurance'},{key:'OTHER',label:'Other'}];
function sameOriginApi(){let url=new URL(config.apiPath,location.href);if(!['http:','https:'].includes(url.protocol)||url.origin!==location.origin)throw Error('API path must be on the same website as the app.');return url;}
function apiUrl(action, params={}){const u=sameOriginApi();u.searchParams.set('action',action);for(const [k,v] of Object.entries(params))u.searchParams.set(k,v);return u.href;}
async function request(action,body=null,params={}){let opts={credentials:'same-origin',cache:'no-store',headers:{Accept:'application/json'}};if(body!==null){opts.method='POST';if(body instanceof FormData){if(!body.has('action'))body.append('action',action);opts.body=body;}else{opts.headers['Content-Type']='application/x-www-form-urlencoded; charset=UTF-8';opts.body=new URLSearchParams(body);}}const r=await fetch(apiUrl(action,params),opts);let data;try{data=await r.json()}catch{throw Error('API returned non-JSON response (HTTP '+r.status+'). Check the PHP path.')}if(!r.ok||!data.ok)throw Error(data.error||'API error (HTTP '+r.status+')');return data;}
async function extraRequest(script,action,body=null){if(!connected)throw Error('Connect to original PHP server to use '+script);if(!['profile.php','reminders.php'].includes(script))throw Error('Unsupported backend endpoint');let url=new URL(script,sameOriginApi());url.searchParams.set('action',action);let opts={credentials:'same-origin',cache:'no-store',headers:{Accept:'application/json'}};if(body){opts.method='POST';let fd=new FormData();fd.append('action',action);for(const [k,v] of Object.entries(body))fd.append(k,v);opts.body=fd}let res=await fetch(url,opts);let d=await res.json().catch(()=>{throw Error(script+' returned invalid JSON')});if(!res.ok||!d.ok)throw Error(d.error||script+' failed');return d}
async function blob(action,id){const r=await fetch(apiUrl(action,{id}),{credentials:'same-origin',cache:'no-store'});if(!r.ok)throw Error('Could not load file (HTTP '+r.status+').');const ct=r.headers.get('content-type')||'';if(ct.includes('application/json')){const d=await r.json();throw Error(d.error||'File unavailable')}return r.blob()}
async function uploadClaimPdf(site,month,claimId,file){
 ensureServerWrite('uploading claim PDFs');
 if(!file) throw Error('Choose a PDF file');
 if(file.size>15*1024*1024) throw Error('PDF exceeds the 15 MB upload limit');
 if(file.type!=='application/pdf'&&!String(file.name||'').toLowerCase().endsWith('.pdf')) throw Error('Only PDF files are accepted');
 const fd=new FormData();
 fd.append('claim_id',String(claimId||''));
 fd.append('site',String(site||''));
 fd.append('month',String(month||''));
 fd.append('file',file);
 return request('upload_claim_pdf',fd);
}
async function claimPdfBlob(claimId){
 ensureServerWrite('viewing claim PDFs');
 const url=apiUrl('view_claim_pdf',{claim_id:String(claimId||'')});
 const r=await fetch(url,{credentials:'same-origin',cache:'no-store'});
 if(!r.ok) throw Error(r.status===404?'No PDF is stored for this Claim ID.':'Could not load claim PDF (HTTP '+r.status+').');
 const ct=r.headers.get('content-type')||'';
 if(ct.includes('application/json')){const d=await r.json();throw Error(d.error||'Claim PDF unavailable')}
 return r.blob();
}

function persist(markDirty=true){if(window.SAYMA_PRODUCTION){window.dispatchEvent(new CustomEvent('sayma:state'));return;}try{localStorage.setItem(JKEY,JSON.stringify(state));}catch(err){console.warn('Local cache full (large PDF history may be responsible)',err);lastError='Local cache is full. Export a backup before closing the app.';}if(markDirty){dirty=true;localStorage.setItem(JKEY+'-dirty','1');}window.dispatchEvent(new CustomEvent('sayma:state'));}

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
async function loadWorkerPaymentLedger(){
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
   if(merged[passport][year]===undefined) merged[passport][year]=clone(record);
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
function save(){
 if(window.SAYMA_PRODUCTION&&!connected) return Promise.reject(Error('Server login required to save data in deployed edition.'));
 normalizePaymentState();
 persist(true);
 if(connected){
  saving=saving.catch(()=>{}).then(async()=>{
   const keys=['workers','projects','projectGrids','permitPayments','permitYears','permitCategories','attendance','letterSerialCounters','letterHistory','letterVerificationRecords','companyDocumentCategories','taxRecords'];
   const payload=JSON.stringify(Object.fromEntries(keys.map(k=>[k,state[k]])));
   await request('save_state',{state_json:payload});
   await syncWorkerPaymentLedger();
   dirty=false;
   if(!window.SAYMA_PRODUCTION) localStorage.removeItem(JKEY+'-dirty');
   lastError='';
   window.dispatchEvent(new Event('sayma:connection'));
   return true;
  }).catch(e=>{
   lastError='Save failed: '+e.message;
   window.dispatchEvent(new Event('sayma:connection'));
   throw e;
  });
  return saving;
 }
 return Promise.resolve({offline:true});
}
async function projectSave(name,type,month){
 if(!connected) return;
 let data=state.projectGrids?.[name]?.[type]?.[month];
 if(!data) return;
 await request('save_project_financial',{project_name:name,month_name:month,data_type:type,data_json:JSON.stringify(data)});
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
function ensureServerWrite(feature='this action'){if(window.SAYMA_PRODUCTION&&!connected)throw Error('Sign in to the original company portal and connect the server before '+feature+'. Production records are not saved offline.');}
async function connect(){
 if(!await test()) return false;
 try{
  const d=await request('get_state');
  if(d.state&&Array.isArray(d.state.workers)){
   if(dirty&&!confirm('There are unsynced local changes. Load server data and replace your local view? Cancel to keep the local edits.')){
    connected=false;lastError='Local changes preserved. Export a backup before switching.';dispatch();return false;
   }
   for(const k of ['workers','projects','projectGrids','permitPayments','permitYears','permitCategories','attendance','letterSerialCounters','letterHistory','letterVerificationRecords','companyDocumentCategories','taxRecords']){
    if(d.state[k]!==undefined) state[k]=d.state[k];
   }
   dirty=false;
   if(!window.SAYMA_PRODUCTION) localStorage.removeItem(JKEY+'-dirty');
   persist(false);
  }
  await loadWorkerPaymentLedger();
  try{
   const f=await request('get_project_financials');
   for(const r of f.items||[]){
    if(!['salary','claims'].includes(r.data_type)||!r.data||!r.project_name||!r.month_name) continue;
    state.projectGrids[r.project_name]||={claims:{},salary:{}};
    state.projectGrids[r.project_name][r.data_type]||={};
    const previous=state.projectGrids[r.project_name][r.data_type][r.month_name];
    if((previous?.rows||[]).length>(r.data?.rows||[]).length) continue;
    state.projectGrids[r.project_name][r.data_type][r.month_name]=r.data;
   }
   persist(false);
  }catch(e){console.warn('Project financial sync:',e)}
  await reloadDocs();
  await reloadTools();
  lastError='';
  dispatch();
  return true;
 }catch(e){
  lastError='Server load failed: '+e.message;
  connected=false;dispatch();return false;
 }
}
async function reloadDocs(){if(connected){const d=await request('list_documents');docs=d.documents||[];try{let c=await request('list_document_categories');categories=(c.categories||c.items||[]).map(x=>typeof x==='string'?{key:x,label:x}:x)}catch{categories=[]}}else{docs=state.documents||[];categories=state.documentCategoryObjects||defaults}if(!categories.length)categories=defaults;dispatch();}
async function workerDocs(worker){const key=(worker.passport||worker.name||'').trim().toUpperCase();if(connected){let r=await request('list_worker_documents',null,{worker_key:key});wdocs[key]=r.documents||[];}else{wdocs[key]=(state.workerDocuments||[]).filter(x=>x.worker_key===key);}return wdocs[key];}
async function reloadTools(){if(connected){try{tools=(await request('list_documentation_tools')).items||[]}catch{tools=[]}}else tools=state.documentationTools||[];}
function idb(){return new Promise((resolve,reject)=>{if(!indexedDB){reject(Error('IndexedDB not available. Use localhost or HTTPS for local document files.'));return;}let req=indexedDB.open('sayma-mobile-attachments-v2',1);req.onupgradeneeded=()=>req.result.createObjectStore('files');req.onsuccess=()=>resolve(req.result);req.onerror=()=>reject(req.error);})}
async function storeFile(id,file){let db=await idb();return new Promise((resolve,reject)=>{let tx=db.transaction('files','readwrite');tx.objectStore('files').put(file,id);tx.oncomplete=()=>{db.close();resolve()};tx.onerror=()=>{db.close();reject(tx.error)}})}
async function readFile(id){let db=await idb();return new Promise((resolve,reject)=>{let tx=db.transaction('files','readonly'),req=tx.objectStore('files').get(id);req.onsuccess=()=>{db.close();resolve(req.result||null)};req.onerror=()=>{db.close();reject(req.error)}})}
async function dropFile(id){let db=await idb();return new Promise((resolve,reject)=>{let tx=db.transaction('files','readwrite');tx.objectStore('files').delete(id);tx.oncomplete=()=>{db.close();resolve()};tx.onerror=()=>{db.close();reject(tx.error)}})}
function localId(){return 'local-'+Date.now()+'-'+Math.random().toString(36).slice(2,8)}
async function uploadDoc(data,file,kind='company',worker){ensureServerWrite('uploading documents');if(!file)throw Error('Choose a file');if(file.size>15*1024*1024)throw Error('File exceeds 15 MB limit');if(connected){let fd=new FormData();if(kind==='worker'){fd.append('worker_key',(worker.passport||worker.name||'').trim().toUpperCase());fd.append('worker_name',worker.name||'');fd.append('passport_no',worker.passport||'');fd.append('doc_type',data.doc_type||'Other');}else{for(const k of ['title','category','issue_date','expiry_date','notes'])fd.append(k,data[k]||'');}fd.append('file',file);await request(kind==='worker'?'upload_worker_document':'upload_document',fd);if(kind==='worker')await workerDocs(worker);else await reloadDocs();return;}
const meta={...data,id:localId(),original_name:file.name,uploaded_at:new Date().toISOString(),version:1,local:true};await storeFile(meta.id,file);if(kind==='worker'){meta.worker_key=(worker.passport||worker.name||'').trim().toUpperCase();state.workerDocuments||=[];state.workerDocuments.push(meta);await workerDocs(worker)}else{state.documents.push(meta);docs=state.documents;}persist();return meta;}
async function editDoc(item,data,file){ensureServerWrite('editing company documents');if(connected){const fd=new FormData();for(const k of ['title','category','issue_date','expiry_date','notes'])fd.append(k,data[k]??item[k]??'');if(file){if(file.size>15*1024*1024)throw Error('File exceeds 15 MB');fd.append('file',file);/* upload_document creates the next version for the same category/title and preserves the previous row */await request('upload_document',fd);}else{fd.append('id',item.id);await request('update_document',fd);}await reloadDocs();return;}const rec=state.documents.find(x=>String(x.id)===String(item.id));if(!rec)throw Error('Document missing');if(file){if(file.size>15*1024*1024)throw Error('File exceeds 15 MB');await storeFile(rec.id+'-v'+rec.version,await readFile(rec.id));await storeFile(rec.id,file);rec.version=(rec.version||1)+1;rec.original_name=file.name;}Object.assign(rec,data);persist();}
async function historyDoc(item){if(connected){let r=await request('document_history',null,{id:item.id});return r.history||[]}return [{id:item.id,version:item.version||1,original_name:item.original_name,uploaded_at:item.uploaded_at},...Array.from({length:(item.version||1)-1},(_,i)=>({id:item.id+'-v'+(i+1),version:i+1,original_name:'Saved previous version',uploaded_at:item.uploaded_at}))];}
async function documentBlob(item,kind='company'){if(connected)return blob(kind==='worker'?'view_worker_document':'view_document',item.id);return await readFile(item.id)}
async function deleteDoc(item,kind='company'){ensureServerWrite('deleting documents');if(connected){await request(kind==='worker'?'delete_worker_document':'delete_document',{id:item.id});if(kind==='company')await reloadDocs();return;}await dropFile(item.id);if(kind==='worker')state.workerDocuments=(state.workerDocuments||[]).filter(x=>String(x.id)!==String(item.id));else state.documents=state.documents.filter(x=>String(x.id)!==String(item.id));persist();await reloadDocs()}
async function addCategory(label){ensureServerWrite('changing document categories');if(connected){await request('add_document_category',{label});await reloadDocs();}else{let key=label.trim().toUpperCase().replace(/[^A-Z0-9]/g,'_');if(categories.some(x=>x.key===key||x.label.toLowerCase()===label.toLowerCase()))throw Error('Category exists');categories.push({key,label});state.documentCategoryObjects=categories;persist()}}
async function renameCategory(key,label){ensureServerWrite('renaming document categories');if(connected){await request('rename_document_category',{key,label});await reloadDocs();}else{categories.find(c=>c.key===key).label=label;state.documentCategoryObjects=categories;persist()}}
async function addTool(item){ensureServerWrite('adding documentation tools');if(connected){await request('add_documentation_tool',item);await reloadTools()}else{item.id=localId();state.documentationTools.push(item);tools=state.documentationTools;persist()}}
async function deleteTool(item){ensureServerWrite('deleting documentation tools');if(connected){await request('delete_documentation_tool',{id:item.id});await reloadTools()}else{state.documentationTools=state.documentationTools.filter(x=>x.id!==item.id);tools=state.documentationTools;persist()}}
async function vault(action,body){if(!connected)throw Error('Vault requires authenticated server API.');return request('vault_'+action,body)}
async function exportData(){const fileMeta=[];for(const doc of [...(state.documents||[]),...(state.workerDocuments||[])]){if(!doc.local)continue;let f=await readFile(doc.id);if(!f)continue;const data=await new Promise((resolve,reject)=>{let fr=new FileReader();fr.onload=()=>resolve(fr.result);fr.onerror=()=>reject(fr.error);fr.readAsDataURL(f)});fileMeta.push({id:doc.id,data,mime:f.type})}return JSON.stringify({format:'SAYMA_V2',exportedAt:new Date().toISOString(),state:clone(state),localFiles:fileMeta},null,2)}
async function importData(json){ensureServerWrite('importing a backup');if(!json?.state||!Array.isArray(json.state.workers)||!Array.isArray(json.state.projects))throw Error('Invalid SAYMA backup');for(const k in state)delete state[k];Object.assign(state,clone(json.state));for(const item of json.localFiles||[]){if(!String(item.data||'').startsWith('data:'))continue;const response=await fetch(item.data);await storeFile(item.id,await response.blob())}persist();await reloadDocs();await reloadTools();}
window.SAYMA={state,config,save,persist,connect,test,request,extraRequest,apiUrl,blob,uploadClaimPdf,claimPdfBlob,projectSave,reloadDocs,reloadTools,workerDocs,uploadDoc,editDoc,historyDoc,documentBlob,deleteDoc,addCategory,renameCategory,addTool,deleteTool,vault,exportData,importData, get online(){return connected},get dirty(){return dirty},get username(){return username},get error(){return lastError},get docs(){return docs},get categories(){return categories},get tools(){return tools},get workerDocsCache(){return wdocs},changeConfig(c){Object.assign(config,c);localStorage.setItem(CONFKEY,JSON.stringify(config));dispatch();},get canUseServer(){return location.protocol!=='file:'}};
reloadDocs().catch(()=>{});reloadTools().catch(()=>{});
})();
