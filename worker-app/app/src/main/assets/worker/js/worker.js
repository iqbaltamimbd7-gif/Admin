(()=>{
'use strict';
const API='/portal/api.php';
const $=id=>document.getElementById(id);
const money=n=>'RM '+Number(n||0).toLocaleString('en-MY',{minimumFractionDigits:2,maximumFractionDigits:2});
const esc=s=>String(s??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const today=new Date();
const dateToday=[today.getFullYear(),String(today.getMonth()+1).padStart(2,'0'),String(today.getDate()).padStart(2,'0')].join('-');
let workerEntries=[],activeYear='',selectedEntry=null,bankRequests=[],salarySlips=[],activeSalaryYear='',selectedSalarySlip=null;
const OFFLINE_CACHEABLE=new Set(['worker_dashboard','worker_salary_slips','worker_bank_account']);
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
    if(payload.ok&&OFFLINE_CACHEABLE.has(action)&&Offline){try{await Offline.put('api:'+action,payload);}catch(cacheError){console.warn('Worker data could not be cached for offline use',cacheError);}}
    return payload;
  }catch(error){
    if(OFFLINE_CACHEABLE.has(action)&&Offline){
      const cached=await Offline.get('api:'+action);
      if(cached&&cached.ok)return {...cached,offline:true,from_cache:true};
    }
    return {ok:false,network_error:true,error:'No internet connection. Connect once to refresh this information.'};
  }
}

let salaryFileObjectUrl='';
function setWorkerOfflineMode(on){
 const el=$('workerOfflineNotice');
 if(el)el.hidden=!on;
}
async function salarySlipBlob(id){
 const key='file:salary-slip:'+String(id);
 if(Offline){
  const cached=await Offline.get(key);
  if(cached instanceof Blob)return cached;
 }
 const response=await fetch(API+'?action=worker_view_salary_slip&id='+encodeURIComponent(String(id)),{credentials:'same-origin',cache:'no-store'});
 if(!response.ok)throw new Error(response.status===401||response.status===403?'Please sign in again to view this salary slip.':'This salary slip is not available offline yet. Open it once while online to save it on this device.');
 const blob=await response.blob();
 if(Offline)await Offline.put(key,blob);
 return blob;
}

async function handlePaymentReceipt(id){
 try{
  const key='file:payment-receipt:'+String(id);
  let blob=Offline?await Offline.get(key):null;
  if(!(blob instanceof Blob)){
   const response=await fetch(API+'?action=worker_view_payment_receipt&id='+encodeURIComponent(String(id)),{credentials:'same-origin',cache:'no-store'});
   if(!response.ok)throw new Error('This receipt is not available offline yet. Open it once while online to save it on this device.');
   blob=await response.blob();
   if(Offline)await Offline.put(key,blob);
  }
  if(salaryFileObjectUrl)URL.revokeObjectURL(salaryFileObjectUrl);
  salaryFileObjectUrl=URL.createObjectURL(blob);
  $('salaryFileFrame').src=salaryFileObjectUrl;
  $('salaryFileModal').hidden=false;
 }catch(error){alert(error.message||'Could not open receipt.');}
}

async function handleSalarySlipFile(id,download){
 try{
  const blob=await salarySlipBlob(id);
  const slip=salarySlips.find(x=>Number(x.id)===Number(id));
  const filename=(slip?.original_filename||('SAYMA-Salary-Slip-'+id+'.pdf')).replace(/[\\\\/:*?"<>|]/g,'_');
  const url=URL.createObjectURL(blob);
  if(download){
   const a=document.createElement('a');a.href=url;a.download=filename;document.body.appendChild(a);a.click();a.remove();
   setTimeout(()=>URL.revokeObjectURL(url),60000);
  }else{
   if(salaryFileObjectUrl)URL.revokeObjectURL(salaryFileObjectUrl);
   salaryFileObjectUrl=url;
   $('salaryFileFrame').src=url;
   $('salaryFileModal').hidden=false;
  }
 }catch(error){alert(error.message||'Could not open salary slip.');}
}
function closeSalaryFile(){
 $('salaryFileModal').hidden=true;
 $('salaryFileFrame').src='about:blank';
 if(salaryFileObjectUrl){URL.revokeObjectURL(salaryFileObjectUrl);salaryFileObjectUrl='';}
}

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
}
document.querySelectorAll('[data-worker-tab]').forEach(b=>b.addEventListener('click',()=>workerTab(b.dataset.workerTab)));
function renderLedger(){const years=[...new Set(workerEntries.map(e=>e.year))].sort().reverse();if(!activeYear||!years.includes(activeYear))activeYear=years[0]||'';$('yearChips').innerHTML=years.map(y=>`<button type="button" class="year-chip ${y===activeYear?'active':''}" data-year="${esc(y)}">${esc(y)}</button>`).join('');const filtered=workerEntries.filter(e=>!activeYear||e.year===activeYear);if(!filtered.length){$('ledgerArea').innerHTML='<div class="empty">No payment records are available yet. Approved payments from Admin will appear here.</div>';return;}$('ledgerArea').innerHTML=filtered.map(e=>{const idx=workerEntries.indexOf(e);return `<button type="button" class="pay-row" data-payment-index="${idx}"><span class="pay-ico">▤</span><span class="pay-main"><b>${esc(e.note||'Payment recorded')}</b><small>${esc(dateLabel(e.date))} · ${esc(e.year)}</small></span><span class="pay-amount">${money(e.amount)}</span><span class="arrow">›</span></button>`;}).join('');}
function renderSubmissions(items){const html=items.length?items.map(x=>`<div class="submission-card"><div class="row"><b>${money(x.amount)} · ${esc(dateLabel(x.payment_date))}</b><span class="status ${esc(x.status)}">${esc(x.status)}</span></div><p>${esc(x.note||'No note provided')} · Submitted ${esc(x.submitted_at||'—')}</p>${x.admin_note?`<p>Admin note: ${esc(x.admin_note)}</p>`:''}${x.has_receipt?`<button class="link" type="button" data-payment-receipt="${Number(x.id)}">View uploaded receipt ↗</button>`:''}</div>`).join(''):'<div class="empty">You have not submitted any payment confirmations.</div>';$('submissionArea').innerHTML=html;$('submitSubmissionArea').innerHTML=html;}
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
  return API+'?action=worker_view_salary_slip&id='+encodeURIComponent(String(id))+(download?'&download=1':'');
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
      <div class="salary-slip-actions"><button type="button" class="salary-view-btn" data-salary-detail="${id}">View</button><button type="button" class="salary-download-btn" data-salary-file-download="${id}">Download</button></div>
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
  salarySlips=Array.isArray(r.slips)?r.slips:[];
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
  $('salaryDetailView').dataset.salaryFileView=String(slip.id);
  $('salaryDetailDownload').dataset.salaryFileDownload=String(slip.id);
  workerTab('slipDetail');
}

async function loadWorkerDashboard(targetTab='home'){
  const r=await api('worker_dashboard');
  if(!r.ok){if(r.worker_auth_required||r.must_set_password){location.replace('./login.html');return;}alert(r.error||'Could not load worker details.');return;}
  setWorkerOfflineMode(!!r.from_cache);
  const w=r.worker||{},payments=r.payments||{},submissions=r.submissions||[];
  const name=w.name||'Worker',role=w.role||'Worker',site=w.site||'No site assigned',status=w.status||'Active';
  const initials=name.trim().split(/\s+/).map(x=>x[0]||'').slice(0,2).join('').toUpperCase()||'W';
  $('workerName').textContent=name;$('workerAvatar').textContent=initials;$('profileAvatar').textContent=initials;$('profileName').textContent=name;
  $('workerSubtitle').textContent=[role,site!=='No site assigned'?site:''].filter(Boolean).join(' · ')||'Here is your personal work overview.';
  $('profileSubtitle').textContent=[role,'Worker account'].join(' · ');$('workerPassport').textContent=w.passport||'—';$('workerEmail').textContent=w.email||'—';$('workerMobile').textContent=w.mobile||'—';$('workerRole').textContent=role;$('workerSite').textContent=site;$('workerStatus').textContent=status;
  $('homeSite').textContent=site;$('homeRole').textContent=role;$('homeStatus').textContent=status;$('projectSiteTitle').textContent=site;$('projectRoleText').textContent=role+' · '+status;$('projectSite').textContent=site;$('projectRole').textContent=role;$('projectStatus').textContent=status;
  const years=Object.keys(payments);const totalPaid=years.reduce((sum,y)=>sum+Number(payments[y]?.paid||0),0);const totalTarget=years.reduce((sum,y)=>sum+Number(payments[y]?.target||0),0);const due=Math.max(totalTarget-totalPaid,0);
  workerEntries=flattenLedger(payments);$('homeTotalPaid').textContent=money(totalPaid);$('homeOutstanding').textContent=money(due);$('payslipTotal').textContent=money(totalPaid);$('payslipCount').textContent=workerEntries.length+' payment record'+(workerEntries.length===1?'':'s');
  const latest=workerEntries[0];$('homeLatestDate').textContent=latest?dateLabel(latest.date):'No payment record yet';$('homeLatestDescription').textContent=latest?(latest.note||'Payment received'):'Your payment activity';$('homeLatestAmount').textContent=latest?money(latest.amount):money(totalPaid);
  renderLedger();renderSubmissions(submissions);await loadSalarySlips();await loadBankAccount();workerTab(targetTab);
}
async function loadBankAccount(){
  const area=$('bankAccountArea');if(!area)return;area.innerHTML='<div class="bank-loading">Loading your bank account information…</div>';
  const r=await api('worker_bank_account',{},'GET');
  if(!r.ok){area.innerHTML=`<div class="bank-empty">${esc(r.error||'Could not load bank account information.')}</div>`;return;}
  bankRequests=Array.isArray(r.requests)?r.requests:[];const latest=bankRequests[0];
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
  const receipt=e.target.closest('[data-payment-receipt]');
  if(receipt){handlePaymentReceipt(Number(receipt.dataset.paymentReceipt));return;}
  const fileView=e.target.closest('[data-salary-file-view]');
  if(fileView){handleSalarySlipFile(Number(fileView.dataset.salaryFileView),false);return;}
  const fileDownload=e.target.closest('[data-salary-file-download]');
  if(fileDownload){handleSalarySlipFile(Number(fileDownload.dataset.salaryFileDownload),true);return;}
  const slip=e.target.closest('[data-salary-detail]');
  if(slip){openSalarySlipDetail(Number(slip.dataset.salaryDetail));return;}
  const pay=e.target.closest('[data-payment-index]');
  if(pay){openPaymentDetail(Number(pay.dataset.paymentIndex));return;}
  const yr=e.target.closest('[data-year]');
  if(yr){activeYear=yr.dataset.year;renderLedger();}
});
$('salaryYearSelect').addEventListener('change',e=>{activeSalaryYear=e.target.value;renderSalarySlips();});
$('salaryDetailView').addEventListener('click',()=>handleSalarySlipFile(Number($('salaryDetailView').dataset.salaryFileView),false));
$('salaryDetailDownload').addEventListener('click',()=>handleSalarySlipFile(Number($('salaryDetailDownload').dataset.salaryFileDownload),true));
$('salaryFileClose').addEventListener('click',closeSalaryFile);
$('salaryFileModal').addEventListener('click',e=>{if(e.target.id==='salaryFileModal')closeSalaryFile();});
window.addEventListener('online',()=>{setWorkerOfflineMode(false);loadWorkerDashboard('home');});
$('paymentForm').addEventListener('submit',async e=>{e.preventDefault();clearMessage('paymentMsg');const fd=new FormData();fd.set('amount',$('receivedAmount').value);fd.set('payment_date',$('receivedDate').value);fd.set('note',$('receivedNote').value);if($('receivedReceipt').files[0])fd.set('receipt',$('receivedReceipt').files[0]);const btn=$('paymentSubmitBtn');busy(btn,true);const r=await api('worker_submit_payment',fd);busy(btn,false,'Submit for Admin approval');if(!r.ok){message('paymentMsg',r.error||'Submission failed.','error');return;}$('paymentForm').reset();$('receivedDate').value=dateToday;message('paymentMsg',r.message||'Sent to Admin for approval.','ok');await loadWorkerDashboard('submit');});
$('workerLogout').addEventListener('click',async()=>{await api('worker_logout');if(Offline)await Offline.clear();location.replace('./login.html');});
if('serviceWorker' in navigator && location.protocol!=='file:'){navigator.serviceWorker.register('./sw.js').catch(()=>{});}
async function init(){
  $('receivedDate').value=dateToday;
  const s=await api('worker_session',{},'GET');
  if(!s.ok){
    if(s.network_error&&Offline){
      const cached=await Offline.get('api:worker_dashboard');
      if(cached&&cached.ok){setWorkerOfflineMode(true);await loadWorkerDashboard('home');return;}
    }
    if(!s.network_error&&Offline)await Offline.clear();
    location.replace('./login.html');return;
  }
  if(s.must_set_password){location.replace('./login.html?setup=1');return;}
  await loadWorkerDashboard('home');
}
init();
})();
