(()=>{
'use strict';
const API='../api.php';
const $=id=>document.getElementById(id);
const money=n=>'RM '+Number(n||0).toLocaleString('en-MY',{minimumFractionDigits:2,maximumFractionDigits:2});
const esc=s=>String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const today=new Date();
const dateToday=[today.getFullYear(),String(today.getMonth()+1).padStart(2,'0'),String(today.getDate()).padStart(2,'0')].join('-');
let workerEntries=[],activeYear='',selectedEntry=null,bankRequests=[],salarySlips=[],activeSalaryYear='',selectedSalarySlip=null;
const OFFLINE_CACHEABLE=new Set(['worker_dashboard','worker_salary_slips','worker_bank_account','worker_leave_requests','worker_attendance']);
const Offline=window.SAYMAWorkerOffline;
async function api(action,data={},method='POST'){
  const opts={method,credentials:'same-origin',cache:'no-store'};
  try{
    let res;
    if(method==='GET'){const qs=new URLSearchParams({action,...data});res=await fetch(API+'?'+qs.toString(),opts);}
    else{
      if(data instanceof FormData){data.set('action',action);opts.body=data;}
      else{const body=new URLSearchParams();body.set('action',action);Object.entries(data||{}).forEach(([k,v])=>body.set(k,String(v)));opts.body=body;}
      res=await fetch(API,opts);
    }
    const payload=await res.json().catch(()=>({ok:false,error:'Server returned an invalid response.'}));
    if(payload.ok&&OFFLINE_CACHEABLE.has(action)&&Offline){try{await Offline.put('api:'+action,payload);}catch(cacheError){console.warn('Worker offline cache write failed',cacheError);}}
    return payload;
  }catch(error){
    if(OFFLINE_CACHEABLE.has(action)&&Offline){const cached=await Offline.get('api:'+action);if(cached&&cached.ok)return {...cached,offline:true,from_cache:true};}
    return {ok:false,network_error:true,error:'No internet connection. Connect once to refresh this information.'};
  }
}
function setWorkerOfflineMode(on){const el=$('workerOfflineNotice');if(el)el.hidden=!on;}
let workerFileObjectUrl='',currentWorkerFile=null;
async function getWorkerFileBlob(action,id,download=false){
 const key='file:'+action+':'+String(id);
 const cached=Offline&&!download?await Offline.get(key):null;
 if(cached instanceof Blob&&!navigator.onLine)return cached;
 const url=API+'?action='+encodeURIComponent(action)+'&id='+encodeURIComponent(String(id))+(download?'&download=1':'');
 try{
  const response=await fetch(url,{credentials:'same-origin',cache:'no-store'});
  if(!response.ok)throw Error(response.status===401||response.status===403?'Please sign in again to view this file.':'Could not load document (HTTP '+response.status+').');
  const blob=await response.blob();if(Offline&&!download){try{await Offline.put(key,blob);}catch(e){console.warn('Worker document could not be cached',e);}}
  return blob;
 }catch(error){if(cached instanceof Blob){setWorkerOfflineMode(true);return cached;}throw error;}
}
function workerBlobBase64(blob){return new Promise((resolve,reject)=>{const r=new FileReader();r.onload=()=>resolve(String(r.result||'').split(',')[1]||'');r.onerror=()=>reject(r.error||Error('Could not prepare document.'));r.readAsDataURL(blob);});}
async function openWorkerFile(action,id,filename){
 try{
  const blob=await getWorkerFileBlob(action,id,false);currentWorkerFile={action,id,filename:String(filename||'SAYMA-document')};
  const modal=$('workerFileModal'),body=$('workerFileBody');if(!modal||!body)throw Error('Document viewer is unavailable.');
  modal.hidden=false;body.innerHTML='<div class="worker-file-loading">Preparing document preview…</div>';
  const fileType=String(blob.type||'').toLowerCase();
  const isPdf=fileType?fileType.includes('pdf'):/\.pdf$/i.test(currentWorkerFile.filename);
  const isImage=fileType?fileType.startsWith('image/'):/\.(jpg|jpeg|png|webp|gif)$/i.test(currentWorkerFile.filename);
  if(isPdf&&window.SaymaNative&&typeof window.SaymaNative.postMessage==='function'){
   const data=await workerBlobBase64(blob);if(data.length>30*1024*1024)throw Error('PDF is too large for in-app preview. Use Download instead.');
   body.innerHTML='<div class="worker-file-loading"><b>Opening PDF inside SAYMA…</b><p>Return to this screen to download or close the document.</p></div>';
   window.SaymaNative.postMessage(JSON.stringify({action:'openPdfBase64',data,filename:currentWorkerFile.filename}));return;
  }
  if(workerFileObjectUrl)URL.revokeObjectURL(workerFileObjectUrl);workerFileObjectUrl=URL.createObjectURL(blob);
  const previewParams=new URLSearchParams({action,id:String(id),preview:'pdf',filename:currentWorkerFile.filename});const previewUrl=navigator.onLine&&$('workerOfflineNotice')?.hidden?new URL(API+'?'+previewParams.toString(),location.href).href:workerFileObjectUrl;
  body.innerHTML=isPdf?`<iframe class="worker-file-frame" title="Document preview" src="${previewUrl}"></iframe>`:isImage?`<img class="worker-file-image" src="${workerFileObjectUrl}" alt="${esc(currentWorkerFile.filename)}">`:'<div class="worker-file-loading"><b>Preview is not available for this file type.</b><p>Use Download to save and open the file.</p></div>';
 }catch(error){alert(error.message||'Could not open document.');}
}
async function downloadWorkerFile(){
 if(!currentWorkerFile){alert('No document is selected.');return;}
 try{
  const action=currentWorkerFile.action,id=currentWorkerFile.id,name=String(currentWorkerFile.filename||'document').replace(/[\\/:*?"<>|]/g,'_');
  if(window.SaymaNative&&typeof window.SaymaNative.postMessage==='function'&&navigator.onLine){window.SaymaNative.postMessage(JSON.stringify({action:'downloadFile',url:new URL(API+'?action='+encodeURIComponent(action)+'&id='+encodeURIComponent(String(id))+'&download=1',location.href).href,filename:name}));return;}
  const blob=await getWorkerFileBlob(action,id,false);
  if(window.SaymaNative&&typeof window.SaymaNative.postMessage==='function'){window.SaymaNative.postMessage(JSON.stringify({action:'saveBase64File',data:await workerBlobBase64(blob),filename:name,mimeType:blob.type||'application/octet-stream'}));return;}
  const url=URL.createObjectURL(blob),a=document.createElement('a');a.href=url;a.download=name;document.body.appendChild(a);a.click();a.remove();setTimeout(()=>URL.revokeObjectURL(url),60000);
 }catch(error){alert(error.message||'Download failed.');}
}
function closeWorkerFile(){const modal=$('workerFileModal');if(modal)modal.hidden=true;const body=$('workerFileBody');if(body)body.innerHTML='';if(workerFileObjectUrl){URL.revokeObjectURL(workerFileObjectUrl);workerFileObjectUrl='';}currentWorkerFile=null;}

function message(id,text,type='info'){const el=$(id);if(!el)return;el.textContent=text;el.className='message show '+type;}
function clearMessage(id){const el=$(id);if(el){el.className='message';el.textContent='';}}
function busy(btn,on,label){if(!btn)return;if(on){btn.dataset.old=btn.textContent;btn.disabled=true;btn.textContent='Please wait…';}else{btn.disabled=false;btn.textContent=label||btn.dataset.old||'Submit';}}
function dateLabel(raw){if(!raw)return 'Date not recorded';const s=String(raw).slice(0,10),m=s.match(/^(\d{4})-(\d{2})-(\d{2})$/);if(!m)return s;return new Date(+m[1],+m[2]-1,+m[3]).toLocaleDateString('en-MY',{day:'numeric',month:'short',year:'numeric'});}
function flattenLedger(payments){const out=[];Object.entries(payments||{}).forEach(([year,rec])=>(Array.isArray(rec?.entries)?rec.entries:[]).forEach((entry,index)=>out.push({...entry,year:String(year),date:String(entry.date||entry.paymentDate||entry.payment_date||''),amount:Number(entry.amount||0),note:String(entry.note||entry.description||entry.part||'Payment recorded by Admin'),_key:String(entry.id||year+'-'+index)})));out.sort((a,b)=>String(b.date).localeCompare(String(a.date)));return out;}
function workerTab(tab){
  document.querySelectorAll('.worker-screen').forEach(s=>s.classList.remove('active'));
  const target=$('workerScreen'+tab.charAt(0).toUpperCase()+tab.slice(1));
  if(target)target.classList.add('active');
  const navTab=tab==='slipDetail'?'payslips':(['paymentHistory','detail','submit','bank','projects'].includes(tab)?'':tab);
  document.querySelectorAll('.worker-nav [data-worker-tab]').forEach(b=>b.classList.toggle('active',b.dataset.workerTab===navTab));
  window.scrollTo({top:0,behavior:'smooth'});
  if(tab==='bank')loadBankAccount();
  if(tab==='payslips')loadSalarySlips();
  if(tab==='leave')loadLeaveRequests();
  if(tab==='clockin')loadAttendance();
}
document.querySelectorAll('[data-worker-tab]').forEach(b=>b.addEventListener('click',()=>workerTab(b.dataset.workerTab)));
function renderLedger(){const years=[...new Set(workerEntries.map(e=>e.year))].sort().reverse();if(!activeYear||!years.includes(activeYear))activeYear=years[0]||'';$('yearChips').innerHTML=years.map(y=>`<button type="button" class="year-chip ${y===activeYear?'active':''}" data-year="${esc(y)}">${esc(y)}</button>`).join('');const filtered=workerEntries.filter(e=>!activeYear||e.year===activeYear);if(!filtered.length){$('ledgerArea').innerHTML='<div class="empty">No payment records are available yet. Approved payments from Admin will appear here.</div>';return;}$('ledgerArea').innerHTML=filtered.map(e=>{const idx=workerEntries.indexOf(e);return `<button type="button" class="pay-row" data-payment-index="${idx}"><span class="pay-ico">▤</span><span class="pay-main"><b>${esc(e.note||'Payment recorded')}</b><small>${esc(dateLabel(e.date))} · ${esc(e.year)}</small></span><span class="pay-amount">${money(e.amount)}</span><span class="arrow">›</span></button>`;}).join('');}
function renderSubmissions(items){const html=items.length?items.map(x=>`<div class="submission-card"><div class="row"><b>${money(x.amount)} · ${esc(dateLabel(x.payment_date))}</b><span class="status ${esc(x.status)}">${esc(x.status)}</span></div><p>${esc(x.note||'No note provided')} · Submitted ${esc(x.submitted_at||'—')}</p>${x.admin_note?`<p>Admin note: ${esc(x.admin_note)}</p>`:''}${x.has_receipt?`<button class="link" type="button" data-worker-file="worker_view_payment_receipt" data-file-id="${Number(x.id)}" data-file-name="Payment-receipt-${Number(x.id)}">View uploaded receipt ↗</button>`:''}</div>`).join(''):'<div class="empty">You have not submitted any payment confirmations.</div>';$('submissionArea').innerHTML=html;$('submitSubmissionArea').innerHTML=html;}
function openPaymentDetail(index){const entry=workerEntries[index];if(!entry)return;selectedEntry=entry;$('detailAmount').textContent=money(entry.amount);$('detailDate').textContent=dateLabel(entry.date);$('detailDateRow').textContent=dateLabel(entry.date);$('detailDescription').textContent=entry.note||'Payment recorded by Admin';$('detailYear').textContent=entry.year||'—';$('detailReference').textContent=entry.id?String(entry.id):'Admin payment ledger';workerTab('detail');}

function salaryMonthLabel(period){
  const m=String(period||'').match(/^(\d{4})-(\d{2})$/);
  if(!m)return String(period||'—');
  return new Date(Number(m[1]),Number(m[2])-1,1).toLocaleDateString('en-MY',{month:'long',year:'numeric'});
}
function salaryDateLabel(raw){
  if(!raw)return '—';
  const d=new Date(String(raw).replace(' ','T'));
  return Number.isNaN(d.getTime())?String(raw):d.toLocaleDateString('en-MY',{day:'numeric',month:'short',year:'numeric'});
}
function salarySlipUrl(id,download=false){
  return '../api.php?action=worker_view_salary_slip&id='+encodeURIComponent(String(id))+(download?'&download=1':'');
}
function renderSalarySlips(){
  const years=[...new Set(salarySlips.map(x=>String(x.pay_period||'').slice(0,4)).filter(Boolean))].sort().reverse();
  if(!activeSalaryYear||!years.includes(activeSalaryYear))activeSalaryYear=years[0]||'';
  const select=$('salaryYearSelect');
  if(select){
    const prior=select.value;
    select.innerHTML='<option value="">All years</option>'+years.map(y=>`<option value="${esc(y)}">${esc(y)}</option>`).join('');
    select.value=years.includes(prior)?prior:activeSalaryYear;
  }
  const filtered=salarySlips.filter(x=>!activeSalaryYear||String(x.pay_period||'').startsWith(activeSalaryYear+'-'));
  $('salarySlipCount').textContent=filtered.length+' Payslip'+(filtered.length===1?'':'s')+' Available';
  $('salaryYearLabel').textContent=activeSalaryYear||'All years';
  const total=salarySlips.reduce((sum,x)=>sum+Number(x.net_amount||0),0);
  if($('homeLatestSalary'))$('homeLatestSalary').textContent=money(salarySlips[0]?.net_amount||0);
  if($('homeLatestSalaryMonth'))$('homeLatestSalaryMonth').textContent=salarySlips[0]?salaryMonthLabel(salarySlips[0].pay_period):'No payslip uploaded yet';
  if(!$('salarySlipList'))return;
  if(!filtered.length){
    $('salarySlipList').innerHTML='<div class="empty salary-empty">No payslips are available for this year yet. Your monthly salary slips will appear here after Admin uploads them.</div>';
    return;
  }
  $('salarySlipList').innerHTML=filtered.map(s=>{
    const id=Number(s.id),period=String(s.pay_period||''),month=salaryMonthLabel(period);
    return `<article class="salary-slip-row">
      <div class="salary-slip-month-icon">▤</div>
      <div class="salary-slip-main"><b>${esc(month)}</b><small>Uploaded ${esc(salaryDateLabel(s.uploaded_at))}</small><strong>${money(s.net_amount)} <em>Take-home pay</em></strong></div>
      <div class="salary-slip-actions"><button type="button" class="salary-view-btn" data-worker-file="worker_view_salary_slip" data-file-id="${id}" data-file-name="${esc(s.original_filename||('Salary-slip-'+id+'.pdf'))}">View</button><button type="button" class="salary-download-btn" data-worker-download="worker_view_salary_slip" data-file-id="${id}" data-file-name="${esc(s.original_filename||('Salary-slip-'+id+'.pdf'))}">Download</button></div>
    </article>`;
  }).join('');
}
async function loadSalarySlips(){
  const area=$('salarySlipList');
  if(area)area.innerHTML='<div class="empty">Loading your payslip archive…</div>';
  const r=await api('worker_salary_slips',{},'GET');
  if(!r.ok){
    if(r.worker_auth_required||r.must_set_password){location.replace('./login.html');return;}
    if(area)area.innerHTML=`<div class="empty">${esc(r.error||'Could not load salary slips.')}</div>`;
    return;
  }
  setWorkerOfflineMode(!!r.from_cache||!!r.offline);salarySlips=Array.isArray(r.slips)?r.slips:[];
  salarySlips.sort((a,b)=>String(b.pay_period||'').localeCompare(String(a.pay_period||''))||String(b.uploaded_at||'').localeCompare(String(a.uploaded_at||'')));
  renderSalarySlips();
}
function openSalarySlipDetail(id){
  const slip=salarySlips.find(x=>Number(x.id)===Number(id));if(!slip)return;
  selectedSalarySlip=slip;
  $('salaryDetailNet').textContent=money(slip.net_amount);
  $('salaryDetailGross').textContent=money(slip.gross_amount);
  $('salaryDetailDeductions').textContent=money(slip.deductions_amount);
  $('salaryDetailPeriod').textContent=salaryMonthLabel(slip.pay_period);
  $('salaryDetailUploaded').textContent=salaryDateLabel(slip.uploaded_at);
  $('salaryDetailFilename').textContent=slip.original_filename||'Salary slip document';
  $('salaryDetailView').dataset.workerFile='worker_view_salary_slip';$('salaryDetailView').dataset.fileId=String(slip.id);$('salaryDetailView').dataset.fileName=slip.original_filename||('Salary-slip-'+slip.id+'.pdf');
  $('salaryDetailDownload').dataset.workerDownload='worker_view_salary_slip';$('salaryDetailDownload').dataset.fileId=String(slip.id);$('salaryDetailDownload').dataset.fileName=slip.original_filename||('Salary-slip-'+slip.id+'.pdf');
  workerTab('slipDetail');
}

function safeWorkerPhotoUrl(raw){
  const value=String(raw||'').trim();
  if(!value)return '';
  if(/^data:image\/(?:jpeg|png|webp);base64,/i.test(value))return value;
  try{
    const url=new URL(value,location.href);
    if(url.origin!==location.origin||!['http:','https:'].includes(url.protocol))return '';
    return url.href;
  }catch(_){return '';}
}
function setWorkerAvatar(el,initials,photoUrl){
  if(!el)return;
  el.replaceChildren();
  const src=safeWorkerPhotoUrl(photoUrl);
  if(!src){el.textContent=initials;el.classList.remove('has-photo');return;}
  const img=document.createElement('img');
  img.alt='Worker profile photo';
  img.decoding='async';
  img.referrerPolicy='same-origin';
  img.onload=()=>el.classList.add('has-photo');
  img.onerror=()=>{el.classList.remove('has-photo');el.replaceChildren(document.createTextNode(initials));};
  img.src=src;
  el.appendChild(img);
}
async function loadWorkerDashboard(targetTab='home'){
  const r=await api('worker_dashboard');
  if(!r.ok){if(r.worker_auth_required||r.must_set_password){location.replace('./login.html');return;}alert(r.error||'Could not load worker details.');return;}
  setWorkerOfflineMode(!!r.from_cache||!!r.offline);const w=r.worker||{},payments=r.payments||{},submissions=r.submissions||[];
  const name=w.name||'Worker',role=w.role||'Worker',site=w.site||'No site assigned',status=w.status||'Active';
  const initials=name.trim().split(/\s+/).map(x=>x[0]||'').slice(0,2).join('').toUpperCase()||'W';
  $('workerName').textContent=name;const workerPhoto=w.photo_url||w.profile_photo_url||w.photoUrl||w.photo||'';setWorkerAvatar($('workerAvatar'),initials,workerPhoto);setWorkerAvatar($('profileAvatar'),initials,workerPhoto);$('profileName').textContent=name;
  $('workerSubtitle').textContent=[role,site!=='No site assigned'?site:''].filter(Boolean).join(' · ')||'Here is your personal work overview.';
  $('profileSubtitle').textContent=[role,'Worker account'].join(' · ');$('workerPassport').textContent=w.passport||'—';$('workerEmail').textContent=w.email||'—';$('workerMobile').textContent=w.mobile||'—';$('workerRole').textContent=role;$('workerSite').textContent=site;$('workerStatus').textContent=status;
  $('homeSite').textContent=site;$('homeRole').textContent=role;$('homeStatus').textContent=status;$('projectSiteTitle').textContent=site;$('projectRoleText').textContent=role+' · '+status;$('projectSite').textContent=site;$('projectRole').textContent=role;$('projectStatus').textContent=status;
  const years=Object.keys(payments);const totalPaid=years.reduce((sum,y)=>sum+Number(payments[y]?.paid||0),0);const totalTarget=years.reduce((sum,y)=>sum+Number(payments[y]?.target||0),0);const due=Math.max(totalTarget-totalPaid,0);
  workerEntries=flattenLedger(payments);$('homeTotalPaid').textContent=money(totalPaid);$('homeOutstanding').textContent=money(due);$('payslipTotal').textContent=money(totalPaid);$('payslipCount').textContent=workerEntries.length+' payment record'+(workerEntries.length===1?'':'s');
  const latest=workerEntries[0];$('homeLatestDate').textContent=latest?dateLabel(latest.date):'No payment record yet';$('homeLatestDescription').textContent=latest?(latest.note||'Payment received'):'Your payment activity';$('homeLatestAmount').textContent=latest?money(latest.amount):money(totalPaid);
  renderLedger();renderSubmissions(submissions);await loadSalarySlips();workerTab(targetTab);
}

/* ================= LEAVE REQUESTS ================= */
function renderLeaveRequests(items){
  const area=$('leaveRequestArea');if(!area)return;
  if(!items.length){area.innerHTML='<div class="empty">You have not submitted any leave requests yet.</div>';return;}
  area.innerHTML=items.map(x=>{
    const status=String(x.status||'pending').toLowerCase();
    return `<article class="leave-request-card">
      <div class="leave-request-head"><b>${esc(dateLabel(x.start_date))} – ${esc(dateLabel(x.end_date))}</b><span class="status ${esc(status)}">${esc(status)}</span></div>
      <p>${esc(x.reason||'No explanation provided')}</p>
      <div class="leave-request-meta"><small>Submitted ${esc(x.submitted_at||'—')}</small>${x.has_mc?`<button class="link" type="button" data-worker-file="worker_view_leave_mc" data-file-id="${Number(x.id)}" data-file-name="Leave-MC-${Number(x.id)}">View MC ↗</button>`:'<small>No MC attached</small>'}</div>
      ${x.admin_note?`<div class="leave-admin-note"><b>Admin note</b><p>${esc(x.admin_note)}</p></div>`:''}
    </article>`;
  }).join('');
}
async function loadLeaveRequests(){
  const area=$('leaveRequestArea');if(area)area.innerHTML='<div class="empty">Loading leave requests…</div>';
  const r=await api('worker_leave_requests',{},'GET');
  if(!r.ok){if(r.worker_auth_required||r.must_set_password){location.replace('./login.html');return;}if(area)area.innerHTML=`<div class="empty">${esc(r.error||'Could not load leave requests.')}</div>`;return;}
  setWorkerOfflineMode(!!r.from_cache||!!r.offline);renderLeaveRequests(Array.isArray(r.requests)?r.requests:[]);
}

/* ================= SITE ATTENDANCE ================= */
let pendingAttendanceLocation=null;
function attendanceTimeLabel(raw){
  if(!raw)return '—';
  const value=String(raw).replace(' ','T');
  const d=new Date(value);
  return Number.isNaN(d.getTime())?String(raw):d.toLocaleTimeString('en-MY',{hour:'2-digit',minute:'2-digit',hour12:true});
}
function attendanceGeoPosition(){
  return new Promise((resolve,reject)=>{
    if(!navigator.geolocation){reject(new Error('Location access is not supported on this device.'));return;}
    navigator.geolocation.getCurrentPosition(
      pos=>resolve({latitude:Number(pos.coords.latitude),longitude:Number(pos.coords.longitude),accuracy_m:Number(pos.coords.accuracy||0)}),
      err=>{
        const messages={
          1:'Location permission was denied. Allow precise location access in your phone settings.',
          2:'Your current location could not be detected. Turn on GPS and try again.',
          3:'Location request timed out. Move to an open area and try again.'
        };
        reject(new Error(messages[err.code]||'Could not get your current location.'));
      },
      {enableHighAccuracy:true,maximumAge:0,timeout:20000}
    );
  });
}
function setAttendanceMessage(text,type='info'){
  const el=$('attendanceMsg');if(!el)return;
  el.textContent=text;el.className='message show '+type;
}
function clearAttendanceMessage(){
  const el=$('attendanceMsg');if(el){el.textContent='';el.className='message';}
}
function renderAttendance(r){
  const todayRecord=r.today||null;
  $('attendanceDate').textContent=dateLabel(r.date||dateToday);
  $('attendanceSite').textContent=r.site_name?('Assigned site: '+r.site_name):'No work site is assigned to your profile.';
  const configured=!!r.location_configured;
  const hasEntry=!!todayRecord?.clock_in_at;
  const hasExit=!!todayRecord?.clock_out_at;
  $('attendanceEntryTime').textContent=attendanceTimeLabel(todayRecord?.clock_in_at);
  $('attendanceExitTime').textContent=attendanceTimeLabel(todayRecord?.clock_out_at);
  const state=$('attendanceStatus');
  state.textContent=hasEntry?(hasExit?'Completed':'Working'):(!configured?'Not permitted':'Not started');
  state.className='attendance-state '+(hasEntry?(hasExit?'done':'working'):(!configured?'blocked':'waiting'));
  $('clockInBtn').hidden=hasEntry||!configured;
  $('clockInBtn').disabled=!configured;
  $('clockOutBtn').hidden=!hasEntry||hasExit;
  $('clockOutBtn').disabled=false;
  $('attendanceCameraBtn').hidden=true;
  if(!configured&&!hasEntry){
    $('attendanceHint').textContent='NOT PERMITTED — Admin has not configured an approved location for your assigned site yet. Please contact Admin.';
    $('clockInBtn').hidden=false;$('clockInBtn').disabled=true;$('clockInBtn').textContent='Not permitted';
  }else if(hasEntry){
    $('attendanceHint').textContent=hasExit?'Today’s attendance is complete.':'Your entry is recorded. Tap Exit when you finish work.';
    $('clockInBtn').hidden=true;
  }else{
    $('attendanceHint').textContent='Allow precise location and take a live site photo. Entry is accepted only inside the Admin-approved site area.';
    $('clockInBtn').textContent='📍 Entry';
  }
  const area=$('attendanceHistory');
  const records=Array.isArray(r.records)?r.records:[];
  if(!records.length){area.innerHTML='<div class="empty">No attendance records yet. Your verified entry and exit times will appear here.</div>';return;}
  area.innerHTML=records.map(x=>`<article class="attendance-history-card">
    <div class="attendance-history-top"><b>${esc(dateLabel(x.date))}</b><span class="status ${x.status==='completed'?'approved':(x.status==='working'?'processing':'pending')}">${esc(x.status==='completed'?'Completed':(x.status==='working'?'Working':'Not started'))}</span></div>
    <div class="attendance-history-times"><div><small>ENTRY</small><b>${esc(attendanceTimeLabel(x.clock_in_at))}</b></div><div><small>EXIT</small><b>${esc(attendanceTimeLabel(x.clock_out_at))}</b></div></div>
    <div class="attendance-history-bottom"><small>${esc(x.site_name||'Assigned site')}</small>${x.has_entry_proof?`<button class="link" type="button" data-worker-file="worker_view_attendance_proof" data-file-id="${Number(x.id)}" data-file-name="Attendance-entry-${Number(x.id)}.jpg">View entry photo ↗</button>`:''}</div>
  </article>`).join('');
}
async function loadAttendance(){
  const area=$('attendanceHistory');if(area)area.innerHTML='<div class="empty">Loading attendance records…</div>';
  const r=await api('worker_attendance',{},'GET');
  if(!r.ok){if(r.worker_auth_required||r.must_set_password){location.replace('./login.html');return;}if(area)area.innerHTML=`<div class="empty">${esc(r.error||'Could not load attendance.')}</div>`;return;}
  setWorkerOfflineMode(!!r.from_cache||!!r.offline);pendingAttendanceLocation=null;renderAttendance(r);
}
async function beginClockIn(){
  clearAttendanceMessage();
  const btn=$('clockInBtn');busy(btn,true,'Checking location…');
  try{
    const position=await attendanceGeoPosition();
    const fd=new FormData();fd.set('latitude',String(position.latitude));fd.set('longitude',String(position.longitude));fd.set('accuracy_m',String(position.accuracy_m));
    const check=await api('worker_attendance_check_location',fd);
    if(!check.ok){setAttendanceMessage(check.error||'NOT PERMITTED — this location is not approved.','error');busy(btn,false,'📍 Entry');return;}
    pendingAttendanceLocation=position;
    $('attendanceCameraBtn').hidden=false;
    $('attendanceHint').textContent='Location verified within the approved site area. Now tap “Take Entry Photo” and capture your photo.';
    setAttendanceMessage('Location verified · '+check.distance_m+' m from the approved site point. Take your entry photo to finish clock-in.','ok');
    busy(btn,false,'📍 Entry');btn.hidden=true;
  }catch(error){
    setAttendanceMessage(error.message||'Could not verify your location.','error');busy(btn,false,'📍 Entry');
  }
}
async function submitClockInPhoto(file){
  if(!pendingAttendanceLocation){setAttendanceMessage('Please verify your location first by tapping Entry.','error');return;}
  if(!file||!String(file.type||'').startsWith('image/')){setAttendanceMessage('Please capture an entry photo using your camera.','error');return;}
  const fd=new FormData();
  fd.set('latitude',String(pendingAttendanceLocation.latitude));
  fd.set('longitude',String(pendingAttendanceLocation.longitude));
  fd.set('accuracy_m',String(pendingAttendanceLocation.accuracy_m));
  fd.set('proof',file,file.name||'entry-photo.jpg');
  const btn=$('attendanceCameraBtn');busy(btn,true,'Uploading proof…');
  const r=await api('worker_clock_in',fd);
  busy(btn,false,'📷 Take Entry Photo');
  if(!r.ok){setAttendanceMessage(r.error||'Clock-in could not be saved.','error');return;}
  pendingAttendanceLocation=null;setAttendanceMessage(r.message||'Entry recorded successfully.','ok');
  await loadAttendance();
}
async function performClockOut(){
  clearAttendanceMessage();
  const btn=$('clockOutBtn');busy(btn,true,'Recording exit…');
  const r=await api('worker_clock_out',{});
  busy(btn,false,'↗ Exit');
  if(!r.ok){setAttendanceMessage(r.error||'Clock-out could not be saved.','error');return;}
  setAttendanceMessage(r.message||'Exit recorded successfully.','ok');await loadAttendance();
}

async function loadBankAccount(){
  const area=$('bankAccountArea');if(!area)return;area.innerHTML='<div class="bank-loading">Loading your bank account information…</div>';
  const r=await api('worker_bank_account',{},'GET');
  if(!r.ok){area.innerHTML=`<div class="bank-empty">${esc(r.error||'Could not load bank account information.')}</div>`;return;}
  setWorkerOfflineMode(!!r.from_cache||!!r.offline);bankRequests=Array.isArray(r.requests)?r.requests:[];const latest=bankRequests[0];
  if(!latest){$('bankApplyToggle').disabled=false;$('bankApplyToggle').textContent='Apply Bank Account';area.innerHTML='<div class="bank-empty"><b>No bank account request yet</b><br>Apply for an RHB or CIMB account. Your request will be sent securely to Admin.</div>';return;}
  const status=String(latest.status||'pending').toLowerCase();const statusLabel=status.charAt(0).toUpperCase()+status.slice(1);const activeRequest=['pending','processing'].includes(status);
  $('bankApplyToggle').disabled=activeRequest;$('bankApplyToggle').textContent=activeRequest?'Request under review':(status==='approved'?'Bank account approved':'Apply Bank Account');
  if(status==='approved'&&latest.account_number){
    area.innerHTML=`<div class="bank-account-card"><div class="bank-caption">SAYMA BUILDERS · WORKER BANK ACCOUNT</div><h3>${esc(latest.bank_name)} BANK</h3><p>Bank account details assigned to your worker profile</p><div class="bank-account-number">${esc(latest.account_number)}</div><div class="bank-account-meta"><div><small>ACCOUNT HOLDER</small><b>${esc(latest.account_name)}</b></div><div><small>STATUS</small><b>Active · Approved</b></div></div></div><div class="bank-status-card"><h3>Account activated</h3><p>Your bank account details have been approved by Admin and are now available in your portal.</p></div>`;
  }else{
    const desc=status==='processing'?'Admin approved your request. Your bank account is being prepared. The details will appear here after Admin adds them.':status==='rejected'?'Your request was not approved. You may submit a new request if needed.':'Your request has reached Admin and is waiting for review.';
    area.innerHTML=`<div class="bank-status-card"><div class="worker-content-head"><div><h3>${esc(latest.bank_name)} Bank request</h3><p>${esc(statusLabel)}</p></div><span class="status ${esc(status)}">${esc(status)}</span></div><p>${esc(desc)}</p><div class="detail-row"><span class="detail-label">Request date</span><b>${esc(latest.created_at||'—')}</b></div>${latest.admin_note?`<div class="detail-row"><span class="detail-label">Admin note</span><b>${esc(latest.admin_note)}</b></div>`:''}</div>`;
  }
  if(bankRequests.length>1){area.innerHTML+=`<div class="bank-status-card"><h3>Previous requests</h3>${bankRequests.slice(1).map(x=>`<div class="detail-row"><span class="detail-label">${esc(x.bank_name)} · ${esc(x.created_at||'')}</span><b><span class="status ${esc(x.status)}">${esc(x.status)}</span></b></div>`).join('')}</div>`;}
}
$('bankApplyToggle').addEventListener('click',()=>{$('bankRequestForm').classList.toggle('hidden');$('bankApplyToggle').textContent=$('bankRequestForm').classList.contains('hidden')?'Apply Bank Account':'Close bank request';});
$('bankApplyCancel').addEventListener('click',()=>{$('bankRequestForm').classList.add('hidden');$('bankApplyToggle').textContent='Apply Bank Account';});
$('refreshBankAccount').addEventListener('click',loadBankAccount);
$('bankRequestForm').addEventListener('submit',async e=>{e.preventDefault();clearMessage('bankRequestMsg');const bankName=$('bankName').value;if(!bankName){message('bankRequestMsg','Please select RHB Bank or CIMB Bank.','error');return;}const btn=$('bankRequestBtn');busy(btn,true);const r=await api('worker_apply_bank_account',{bank_name:bankName});busy(btn,false,'Send Request to Admin');if(!r.ok){message('bankRequestMsg',r.error||'Could not send your request.','error');return;}$('bankRequestForm').reset();$('bankRequestForm').classList.add('hidden');$('bankApplyToggle').textContent='Request under review';message('bankRequestMsg',r.message||'Request sent to Admin.','ok');await loadBankAccount();});
document.addEventListener('click',e=>{
  const slip=e.target.closest('[data-salary-detail]');
  if(slip){openSalarySlipDetail(Number(slip.dataset.salaryDetail));return;}
  const pay=e.target.closest('[data-payment-index]');
  if(pay){openPaymentDetail(Number(pay.dataset.paymentIndex));return;}
  const yr=e.target.closest('[data-year]');
  if(yr){activeYear=yr.dataset.year;renderLedger();}
});
$('salaryYearSelect').addEventListener('change',e=>{activeSalaryYear=e.target.value;renderSalarySlips();});

$('leaveStartDate').value=dateToday;
$('leaveEndDate').min=dateToday;
$('leaveStartDate').addEventListener('change',()=>{$('leaveEndDate').min=$('leaveStartDate').value||dateToday;if($('leaveEndDate').value&&$('leaveEndDate').value<$('leaveStartDate').value)$('leaveEndDate').value=$('leaveStartDate').value;});
$('leaveForm').addEventListener('submit',async e=>{
  e.preventDefault();clearMessage('leaveMsg');
  const fd=new FormData();fd.set('start_date',$('leaveStartDate').value);fd.set('end_date',$('leaveEndDate').value);fd.set('reason',$('leaveReason').value);
  if($('leaveMc').files[0])fd.set('mc',$('leaveMc').files[0]);
  const btn=$('leaveSubmitBtn');busy(btn,true,'Submitting…');
  const r=await api('worker_submit_leave',fd);busy(btn,false,'Submit Leave Request');
  if(!r.ok){message('leaveMsg',r.error||'Could not submit leave request.','error');return;}
  $('leaveForm').reset();$('leaveStartDate').value=dateToday;$('leaveEndDate').min=dateToday;
  message('leaveMsg',r.message||'Leave request sent to Admin.','ok');await loadLeaveRequests();
});
$('refreshLeaveRequests').addEventListener('click',loadLeaveRequests);
$('clockInBtn').addEventListener('click',beginClockIn);
$('attendanceCameraBtn').addEventListener('click',()=>$('attendanceProofInput').click());
$('attendanceProofInput').addEventListener('change',async e=>{
  const file=e.target.files&&e.target.files[0];if(file)await submitClockInPhoto(file);e.target.value='';
});
$('clockOutBtn').addEventListener('click',performClockOut);
$('refreshAttendance').addEventListener('click',loadAttendance);
$('paymentForm').addEventListener('submit',async e=>{e.preventDefault();clearMessage('paymentMsg');const fd=new FormData();fd.set('amount',$('receivedAmount').value);fd.set('payment_date',$('receivedDate').value);fd.set('note',$('receivedNote').value);if($('receivedReceipt').files[0])fd.set('receipt',$('receivedReceipt').files[0]);const btn=$('paymentSubmitBtn');busy(btn,true);const r=await api('worker_submit_payment',fd);busy(btn,false,'Submit for Admin approval');if(!r.ok){message('paymentMsg',r.error||'Submission failed.','error');return;}$('paymentForm').reset();$('receivedDate').value=dateToday;message('paymentMsg',r.message||'Sent to Admin for approval.','ok');await loadWorkerDashboard('submit');});
$('workerLogout').addEventListener('click',async()=>{if(window.SaymaNative&&window.SaymaNative.postMessage)window.SaymaNative.postMessage(JSON.stringify({action:'clearRememberedLogin'}));await api('worker_logout');localStorage.removeItem('sayma-worker-offline-auth');localStorage.removeItem('sayma-worker-offline-identifier');if(Offline)await Offline.clear();location.replace('../login.html');});
if('serviceWorker' in navigator && location.protocol!=='file:'){navigator.serviceWorker.register('./sw.js').catch(()=>{});}
async function init(){
  $('receivedDate').value=dateToday;
  const s=await api('worker_session',{},'GET');
  if(!s.ok){
    const cached=Offline?await Offline.get('api:worker_dashboard'):null;
    const remembered=localStorage.getItem('sayma-worker-offline-auth')==='1';
    if(s.network_error&&remembered&&cached&&cached.ok){setWorkerOfflineMode(true);await loadWorkerDashboard('home');return;}
    location.replace('../login.html');return;
  }
  localStorage.setItem('sayma-worker-offline-auth','1');
  if(s.must_set_password){location.replace('./login.html?setup=1');return;}
  await loadWorkerDashboard('home');
}
init();
window.addEventListener('online',()=>{setWorkerOfflineMode(false);loadWorkerDashboard('home');});
document.addEventListener('click',async e=>{
 const view=e.target.closest('[data-worker-file]');
 if(view){await openWorkerFile(view.dataset.workerFile,view.dataset.fileId,view.dataset.fileName);return;}
 const down=e.target.closest('[data-worker-download]');
 if(down){currentWorkerFile={action:down.dataset.workerDownload,id:down.dataset.fileId,filename:down.dataset.fileName};await downloadWorkerFile();return;}
 if(e.target.closest('#workerFileClose')){closeWorkerFile();return;}
 if(e.target.closest('#workerFileDownload')){await downloadWorkerFile();return;}
});
})();
