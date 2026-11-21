/* SAYMA Mobile V6. Executive dashboard, shared PHP/MySQL integration and protected production mode. */
(()=>{'use strict';
const D=window.SAYMA,S=D.state,M=window.SAYMA_MOTION, $=s=>document.querySelector(s), esc=x=>String(x??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c])),fmt=x=>Number(x||0).toLocaleString('en-MY',{minimumFractionDigits:2,maximumFractionDigits:2}),rm=x=>'RM '+fmt(x),today=()=>new Date().toLocaleDateString('en-CA'),uid=()=>Date.now()+'-'+Math.random().toString(36).slice(2),months=['January','February','March','April','May','June','July','August','September','October','November','December'];
let permitView='paid',adminSalarySlipRows=[];
let gpsAdminTab='locations',gpsAdminDate=today(),gpsAdminSite='',gpsAdminLeaveStatus='pending',gpsAdminLocations=[],gpsAdminEditingLocation=null;
let adminProfile=(()=>{try{return JSON.parse(localStorage.getItem('sayma-profile-v5')||'{}')}catch{return {}}})(),tab='Home',modalType='',projectIdx=0,projectFinanceMonth=months[new Date().getMonth()],projectDocuments=[],docFilter='ALL',wIndex=0,kind='company',selectedYear=(()=>{let years=(S.permitYears||['2027']).slice().reverse();return years.find(y=>(S.workers||[]).some(w=>Number(S.permitPayments?.[w.passport]?.[y]?.paid||0)>0))||years[0]||'2027'})(),workerSearch='',docSearch='',gridType='claims',gridMonth='September',attendanceProject=0,attendanceMonth='September',attendanceDate=today(),docPreviewUrl=null,currentFileInfo=null,taxYear='',vaultItems=[];
function icon(t){const data={
 Home:'<path d="M3 10.2 12 3l9 7.2V20a1 1 0 0 1-1 1h-5.5v-6h-5v6H4a1 1 0 0 1-1-1z"/>',
 Workers:'<circle cx="8" cy="8" r="2.5"/><circle cx="16" cy="9" r="2"/><path d="M2.5 20v-2a5.5 5.5 0 0 1 11 0v2m3.5-7a4.5 4.5 0 0 1 4.5 4.5V20"/>',
 Sites:'<path d="M3 21V7l9-4 9 4v14M2 21h20M8 9h2m4 0h2M8 13h2m4 0h2M10 21v-5h4v5"/>',
 Finance:'<rect x="2.5" y="5" width="19" height="14" rx="3"/><path d="M2.5 10h19"/><circle cx="16.5" cy="14.5" r="1.3"/>',
 More:'<circle cx="12" cy="12" r="3"/><path d="m19.4 15 1.1 1.9-2.1 2.1-1.9-1.1a8 8 0 0 1-2 1l-.5 2.1h-3l-.5-2.1a8 8 0 0 1-2-1l-1.9 1.1-2.1-2.1L5.6 15a8 8 0 0 1-1-2L2.5 12l2.1-1a8 8 0 0 1 1-2L4.5 7.1 6.6 5l1.9 1.1a8 8 0 0 1 2-1L11 3h3l.5 2.1a8 8 0 0 1 2 1L18.4 5l2.1 2.1L19.4 9a8 8 0 0 1 1 2l1.1 1-1.1 1a8 8 0 0 1-1 2z"/>'
 };return `<svg width="21" height="21" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true">${data[t]||data.Home}</svg>`}
const btn=(label,act,cl='',attrs='')=>`<button type="button" class="new-btn ${cl}" data-act="${act}" ${attrs}>${label}</button>`;
const card=(html,cl='')=>`<section class="new-card ${cl}">${html}</section>`;
const empty=(title,desc='')=>`<div class="empty-state"><div class="empty-glyph">▧</div><b>${esc(title)}</b><p>${esc(desc)}</p></div>`;
const field=(n,label,value='',type='text',extra='')=>`<label class="new-field"><span>${esc(label)}</span><input name="${esc(n)}" type="${type}" value="${esc(value)}" ${extra}></label>`;
const area=(n,label,value='')=>`<label class="new-field"><span>${esc(label)}</span><textarea name="${esc(n)}" rows="4">${esc(value)}</textarea></label>`;
const select=(n,label,options,chosen)=>`<label class="new-field"><span>${esc(label)}</span><select name="${esc(n)}">${options.map(o=>{let v=typeof o==='string'?o:o.value,l=typeof o==='string'?o:o.label;return `<option value="${esc(v)}" ${String(v)===String(chosen)?'selected':''}>${esc(l)}</option>`}).join('')}</select></label>`;
const head=(t,s)=>`<div class="new-top"><div><div class="eyebrow">SAYMA BUILDERS SDN BHD</div><h1>${esc(t)}</h1><p>${esc(s)}</p></div><button type="button" class="avatar profile-shortcut" data-act="profile" aria-label="Open administrator profile">${esc(adminInitials())}</button></div>`;
function toast(msg,bad=false){let e=$('#toast');e.textContent=msg;e.className='visible'+(bad?' toast-bad':'');clearTimeout(toast.timeout);toast.timeout=setTimeout(()=>e.className='',3200)}
function nav(){return ['Home','Workers','Sites','Finance','More'].map(t=>`<button type="button" class="nav-item nav-${t.toLowerCase()} ${tab===t?'chosen':''}" data-tab="${t}" aria-label="Open ${t}" ${tab===t?'aria-current="page"':''}><span class="nav-icon">${icon(t)}</span><small>${t}</small></button>`).join('')}
function stats(){const a=S.workers||[],p=S.projects||[],total=a.reduce((t,w)=>t+Number(w.rate||0),0);return [['Active sites',p.filter(x=>/active/i.test(x.status)).length,'▤'],['Workers',a.length,'♙'],['Daily payroll',rm(total),'◉'],['Documents',D.online?D.docs.length:(S.documents||[]).length,'▧']]}

/* V5 dashboard: all amounts and staff data are calculated from the actual application state. */
function adminName(){return String(adminProfile.name||D.username||'Administrator').trim()||'Administrator'}
function adminInitials(){return adminName().split(/\s+/).map(w=>w[0]||'').slice(0,2).join('').toUpperCase()||'AD'}
function validAdminPhoto(){const p=String(adminProfile.photo||'');return p.length<=600000&&/^data:image\/(jpeg|png|webp);base64,[A-Za-z0-9+/=]+$/.test(p)?p:''}
function adminPhotoMarkup(){const p=validAdminPhoto();return p?`<img class="admin-avatar-photo" src="${esc(p)}" alt="Administrator photo">`:esc(adminInitials())}
function adminPhotoEditor(){return `<div class="admin-photo-editor"><div class="admin-photo-preview" id="adminPhotoPreview">${adminPhotoMarkup()}</div><div class="admin-photo-tools"><strong>Administrator photo</strong><label class="admin-photo-upload">Choose photo<input id="adminPhotoInput" name="photoFile" type="file" accept="image/*"></label><button type="button" class="admin-photo-remove" data-act="remove-admin-photo">Remove photo</button><small>Square photo, compressed and saved to the company database.</small></div></div>`}
async function compressAdminPhoto(file){
 if(!file||!String(file.type||'').startsWith('image/'))throw Error('Choose a valid image file.');
 if(file.size>12*1024*1024)throw Error('Please choose an image smaller than 12 MB.');
 const url=URL.createObjectURL(file);
 try{
  const img=new Image();
  await new Promise((resolve,reject)=>{img.onload=resolve;img.onerror=()=>reject(Error('Could not read this image.'));img.src=url});
  let side=512,canvas=document.createElement('canvas'),ctx=canvas.getContext('2d');
  const render=(s,q)=>{canvas.width=s;canvas.height=s;ctx=canvas.getContext('2d');ctx.fillStyle='#ffffff';ctx.fillRect(0,0,s,s);const scale=Math.max(s/img.naturalWidth,s/img.naturalHeight),w=img.naturalWidth*scale,h=img.naturalHeight*scale;ctx.drawImage(img,(s-w)/2,(s-h)/2,w,h);return canvas.toDataURL('image/jpeg',q)};
  let data=render(side,.78);
  if(data.length>570000)data=render(side,.66);
  if(data.length>590000){side=384;data=render(side,.68)}
  if(data.length>600000)throw Error('Photo is still too large after compression. Choose another image.');
  return data;
 }finally{URL.revokeObjectURL(url)}
}
function updateAdminPhotoPreview(){const el=$('#adminPhotoPreview');if(el)el.innerHTML=adminPhotoMarkup();render()}

function numberVal(v){let n=Number(String(v??0).replace(/[,RM\s]/g,''));return Number.isFinite(n)?n:0}
function shortRm(n){let val=Number(n||0);return 'RM '+(Math.abs(val)>=1000000?(val/1000000).toFixed(2)+'m':Math.abs(val)>=1000?(val/1000).toFixed(1)+'k':Math.round(val).toLocaleString('en-MY'))}
function snapshot(){
 let periods=[], totClaim=0,totSalary=0;
 for(const [project,data] of Object.entries(S.projectGrids||{})){
  for(const type of ['claims','salary'])for(const [month,sheet] of Object.entries(data[type]||{})){
   if(!periods.includes(month))periods.push(month);
  }
 }
 periods.sort((a,b)=>{let i=months.indexOf(a),j=months.indexOf(b);return (i<0?99:i)-(j<0?99:j)});
 let chosen=periods.find(m=>Object.values(S.projectGrids||{}).some(p=>p.claims?.[m]?.rows?.length||p.salary?.[m]?.rows?.length))||periods[0]||'';
 for(const group of Object.values(S.projectGrids||{}))for(const type of ['claims','salary']){
  let sheet=group[type]?.[chosen];if(!sheet)continue;
  const headers=(sheet.headers||[]).map(x=>String(x).toLowerCase());
  let idx=type==='claims'?headers.findIndex(x=>x.includes('total')):headers.findIndex(x=>x.includes('net pay'));
  for(const row of sheet.rows||[]){if(!Array.isArray(row)||!row.length)continue;
    let col=idx>=0&&idx<row.length?idx:row.length-1;
    if(type==='claims')totClaim+=numberVal(row[col]);else totSalary+=numberVal(row[col]);
  }
 }
 return {month:chosen,claim:totClaim,salary:totSalary,has:!!chosen};
}
function roleGroups(){const arr=Object.create(null);for(const w of S.workers||[]){const r=w.role||'Unassigned';if(!arr[r])arr[r]={label:r,count:0,pay:0};arr[r].count++;arr[r].pay+=Number(w.rate)||0}return Object.values(arr).sort((a,b)=>b.count-a.count||b.pay-a.pay)}
function permitTotals(year=selectedYear){let due=0,paid=0;for(const w of S.workers||[]){let a=S.permitPayments?.[w.passport]?.[year]||{};due+=Number(a.target)||0;paid+=Number(a.paid)||0}return {due,paid,remaining:Math.max(0,due-paid)}}
/* V6: worker-wise permit chart + collection gauge, calculated only from stored ledger. */
function permitChart(){
 const staff=S.workers||[], t=permitTotals(), mode=permitView;
 const series=staff.map((w,i)=>{const a=S.permitPayments?.[w.passport]?.[selectedYear]||{};return {name:String(w.name||'Worker').split(' ')[0],paid:Math.max(0,Number(a.paid)||0),target:Math.max(0,Number(a.target)||0),i}});
 const count=series.length; const actual=series.map(r=>mode==='paid'?r.paid:r.target), compare=series.map(r=>mode==='paid'?r.target:r.paid);
 const yMax=Math.max(1,...actual,...compare),ceil=Math.ceil(yMax/1000)*1000||1;const W=354,H=190,L=34,R=10,T=16,B=28;
 const x=i=>L+(W-L-R)*(count<2?.5:i/(count-1)), y=v=>T+(H-B-T)*(1-Math.max(0,v)/ceil);
 const pathOf=vs=>vs.length?'M '+vs.map((v,i)=>x(i).toFixed(1)+' '+y(v).toFixed(1)).join(' L '):'';
 const strokePath=pathOf(actual),compPath=pathOf(compare),base=H-B;
 const area=count?strokePath+' L '+x(count-1).toFixed(1)+' '+base+' L '+x(0).toFixed(1)+' '+base+' Z':'';
 const grids=[0,.5,1].map(f=>{const v=ceil*f,yy=y(v);return `<g><line x1="${L}" y1="${yy}" x2="${W-R}" y2="${yy}" stroke="#c8d4d3" stroke-dasharray="3 5"/><text x="${L-7}" y="${yy+3.5}" font-size="9" fill="#718d88" text-anchor="end">${v>=1000?(v/1000).toFixed(v%1000?1:0)+'k':Math.round(v)}</text></g>`}).join('');
 const labels=series.map((r,i)=>i===0||i===count-1||i%Math.max(1,Math.ceil(count/5))===0?`<text x="${x(i)}" y="${H-7}" font-size="8.5" fill="#6f8582" text-anchor="middle">${esc(r.name.slice(0,5).toUpperCase())}</text>`:'').join('');
 const nodes=series.map((r,i)=>`<g data-act="permit:${i}" tabindex="0" role="button" aria-label="Open ${esc(r.name)} permit details: ${rm(actual[i])}"><circle cx="${x(i)}" cy="${y(actual[i])}" r="9" fill="transparent"/><circle cx="${x(i)}" cy="${y(actual[i])}" r="3.25" fill="#fcfffe" stroke="#b77fba" stroke-width="2.4"/><title>${esc(staff[i].name)} · ${rm(actual[i])}</title></g>`).join('');
 const pct=t.due>0?Math.min(100,Math.max(0,t.paid/t.due*100)):0;
 const pointAngle=Math.PI*(1-pct/100),nx=126+72*Math.cos(pointAngle),ny=133-72*Math.sin(pointAngle);
 const gauge=`<svg viewBox="0 0 252 151" class="collection-gauge" role="img" aria-label="${Math.round(pct)} percent of permit target collected"><path d="M26 133 A100 100 0 0 1 226 133" fill="none" stroke="#e8dce8" stroke-width="19" stroke-linecap="round" pathLength="100"/><path d="M26 133 A100 100 0 0 1 226 133" fill="none" stroke="url(#v6Gauge)" stroke-width="19" stroke-linecap="round" pathLength="100" stroke-dasharray="${pct.toFixed(2)} 100"/><defs><linearGradient id="v6Gauge"><stop offset="0" stop-color="#b77fba"/><stop offset="1" stop-color="#78cbc5"/></linearGradient></defs><line x1="126" y1="133" x2="${nx.toFixed(1)}" y2="${ny.toFixed(1)}" stroke="#24535a" stroke-width="3" stroke-linecap="round"/><circle cx="126" cy="133" r="7" fill="#24535a"/></svg>`;
 return `<section class="permit-analysis"><div class="permit-chart-card"><div class="analytics-head"><div><div class="eyebrow">WORKFORCE PAYMENT TRACKER</div><h3>Permit payment collection</h3><p>${esc(selectedYear)} · Worker-wise paid vs. target</p></div><button type="button" class="analysis-year" data-act="chart-year" aria-label="Switch permit chart year">${esc(selectedYear)} ⇄</button></div><div class="graph-mode" role="group" aria-label="Permit chart view"><button type="button" data-act="chart:paid" class="${mode==='paid'?'active':''}" aria-pressed="${mode==='paid'}">Paid</button><button type="button" data-act="chart:target" class="${mode==='target'?'active':''}" aria-pressed="${mode==='target'}">Target</button></div><div class="chart-context"><span class="live-pulse"></span> ${shortRm(t.paid)} of ${shortRm(t.due)} collected</div>${count?`<svg class="permit-line-chart" viewBox="0 0 ${W} ${H}" role="img" aria-label="Actual worker permit payments and targets for ${esc(selectedYear)}"><defs><linearGradient id="v6Area" x1="0" y1="0" x2="0" y2="1"><stop offset="0" stop-color="#d89aaf" stop-opacity=".33"/><stop offset="1" stop-color="#d89aaf" stop-opacity=".015"/></linearGradient></defs>${grids}<path d="${compPath}" fill="none" stroke="#72b8b4" stroke-width="2" stroke-dasharray="5 5" opacity=".63"/><path d="${area}" fill="url(#v6Area)"/><path d="${strokePath}" fill="none" stroke="#b77fba" stroke-width="3.2" stroke-linejoin="round" stroke-linecap="round"/>${nodes}${labels}</svg>`:`<p class="chart-empty">Add workers to show permit payment data.</p>`}<div class="analytics-legend"><span><i class="lg-live"></i> ${mode==='paid'?'Collected':'Target'}</span><span><i class="lg-dashed"></i> ${mode==='paid'?'Target':'Collected'}</span><small>Tap a point for worker details</small></div><div class="analytic-numbers"><div><small>Total target</small><b>${shortRm(t.due)}</b></div><div><small>Collected</small><b>${shortRm(t.paid)}</b></div><div><small>Balance</small><b>${shortRm(t.remaining)}</b></div></div></div><div class="permit-gauge-card"><div class="gauge-top"><div><div class="eyebrow">COLLECTION GOAL</div><h3>Payment progress</h3><p>Share of total permits paid</p></div><span class="gauge-status ${pct>=80?'good':'pending'}">${pct>=100?'Reached':pct>=80?'On track':'In progress'}</span></div><div class="gauge-main">${gauge}<div class="gauge-number"><strong>${Math.round(pct)}%</strong><small>collected</small></div></div><div class="gauge-foot"><div><b>${shortRm(t.paid)}</b><small>Paid</small></div><div><b>${shortRm(t.due)}</b><small>Target</small></div><div><b>${shortRm(t.remaining)}</b><small>Due</small></div></div></div></section>`;
}
function dashboardChart(){let f=snapshot(),max=Math.max(f.claim,f.salary,1),plotTop=28,plotBase=175,plotH=133;const col=(n,x,color,label)=>{let h=n<=0?0:Math.max(5,n/max*plotH);return `<g><rect x="${x}" y="${plotBase-h}" width="66" height="${h}" rx="9" fill="${color}" opacity=".94"/><text x="${x+33}" y="${Math.max(18,plotBase-h-9)}" text-anchor="middle" font-size="12" font-weight="800" fill="#31545a">${esc(shortRm(n))}</text><text x="${x+33}" y="197" text-anchor="middle" font-size="11" fill="#7c8c8d">${esc(label)}</text></g>`};
 return `<section class="new-card insight-panel"><div class="insight-head"><div><div class="eyebrow">FINANCIAL OVERVIEW</div><h3>Claims & salary snapshot</h3><p>${f.has?esc(f.month)+' · Recorded project sheets':'Add a project claims or salary sheet to populate this graph'}</p></div>${btn('View sheets',S.projects.length?'project:0':'go:Sites','outline small')}</div>${f.has?`<div class="chart-stats"><div><small>Total claims</small><strong>${rm(f.claim)}</strong></div><div><small>Recorded salary</small><strong>${rm(f.salary)}</strong></div></div><div class="chart-shell"><svg class="dashboard-chart" viewBox="0 0 320 215" role="img" aria-label="${esc(f.month)} claims ${rm(f.claim)} compared with recorded salary ${rm(f.salary)}"><g stroke="#e8dce8" stroke-dasharray="4 6"><path d="M25 42H294M25 86H294M25 130H294M25 175H294"/></g>${col(f.claim,85,'#176e78','Claims')}${col(f.salary,197,'#d68fa9','Salary')}</svg></div><div class="chart-legend"><span><i class="legend-blue"></i> Claims</span><span><i class="legend-sky"></i> Salary</span><small>Actual saved entries</small></div>`:`<div class="chart-empty">No financial entries yet. Open a project and add a claims or salary sheet.</div>`}</section>`;
}
function dashboardRoles(){let groups=roleGroups();let total=(S.workers||[]).length||1;let shown=groups.slice(0,4);let extra=groups.slice(4);if(extra.length)shown.push({label:'Other roles',count:extra.reduce((t,v)=>t+v.count,0),pay:extra.reduce((t,v)=>t+v.pay,0)});
 return `<section class="new-card insight-panel role-panel"><div class="insight-head"><div><div class="eyebrow">TEAM INSIGHTS</div><h3>Workforce by trade</h3><p>Live breakdown of your worker directory</p></div>${btn('Workers','go:Workers','outline small')}</div><div class="role-bars">${shown.length?shown.map((r,i)=>`<div class="role-row"><div class="role-caption"><span>${esc(r.label)}</span><b>${r.count} <small>people</small></b></div><div class="role-track"><i style="width:${(r.count/total*100).toFixed(2)}%;--bar-index:${i}"></i></div></div>`).join(''):'<p class="subtext">Add a worker to see the breakdown.</p>'}</div><div class="role-footer"><b>${S.workers.length}</b> workers across <b>${groups.length}</b> trades</div></section>`;
}
function dashboardSearchResults(q){let v=String(q||'').trim().toLowerCase();if(!v)return `<div class="search-empty">Search by worker name, passport, trade or project name</div>`;
 let items=[];for(const [i,w] of (S.workers||[]).entries())if([w.name,w.passport,w.role,w.site].some(x=>String(x||'').toLowerCase().includes(v)))items.push({type:'WORKER',title:w.name,detail:(w.role||'Worker')+' · '+(w.passport||'No passport'),act:'worker:'+i});
 for(const [i,p] of (S.projects||[]).entries())if([p.name,p.location,p.status,p.supervisor].some(x=>String(x||'').toLowerCase().includes(v)))items.push({type:'PROJECT',title:p.name,detail:p.location||p.status||'',act:'project:'+i});
 for(const item of D.docs||[])if([item.title,item.original_name,item.category].some(x=>String(x||'').toLowerCase().includes(v)))items.push({type:'FILE',title:item.title||item.original_name,detail:item.category||'Company document',act:'doc-view:'+item.id});
 return items.length?items.slice(0,8).map(x=>`<button class="search-result" type="button" data-act="${esc(x.act)}"><span class="search-result-tag">${x.type}</span><span class="search-result-text"><b>${esc(x.title)}</b><small>${esc(x.detail)}</small></span><span class="search-arrow">↗</span></button>`).join(''):`<div class="search-empty">No matching worker, project or document found.</div>`;
}
function dashboardWorkspace(){let p=permitTotals(),flagged=S.workers.filter(w=>String(w.cidb||'').toUpperCase()==='NO').length,expiry=[];let now=new Date();now.setHours(0,0,0,0);
 for(const w of S.workers||[])for(const [lab,key] of [['Permit','permitExpiry'],['CIDB','cidbDate']]){if(!w[key])continue;let date=new Date(w[key]+'T00:00:00');let days=Math.ceil((date-now)/86400000);if(Number.isFinite(days)&&days<=45)expiry.push({name:w.name,lab,days})}
 expiry.sort((a,b)=>a.days-b.days);
 return `<section class="new-card insight-panel"><div class="insight-head"><div><div class="eyebrow">COMPLIANCE & PERMITS</div><h3>${esc(selectedYear)} permit overview</h3><p>Saved worker permit ledger</p></div>${btn('Manage','go:Finance','outline small')}</div><div class="permit-numbers"><div><small>Budget</small><b>${shortRm(p.due)}</b></div><div><small>Paid</small><b>${shortRm(p.paid)}</b></div><div><small>Remaining</small><b>${shortRm(p.remaining)}</b></div></div><div class="permit-bar" role="progressbar" aria-valuenow="${p.due?Math.round(p.paid/p.due*100):0}" aria-valuemin="0" aria-valuemax="100" aria-label="Permit payments completed"><i style="width:${p.due?Math.min(100,(p.paid/p.due)*100):0}%"></i></div><div class="compliance-notes">${flagged?`<div class="compliance-alert"><span>!</span><p><b>${flagged} CIDB records marked NO</b><small>Review worker compliance details</small></p>${btn('Review','go:Workers','text-btn')}</div>`:''}${expiry.slice(0,2).map(x=>`<div class="compliance-alert"><span>◷</span><p><b>${esc(x.name)} — ${x.lab}</b><small>${x.days<0?'Expired '+Math.abs(x.days)+' days ago':x.days+' days remaining'}</small></p></div>`).join('')}</div></section>`;
}
function home(){let projects=S.projects||[],w=S.workers||[],totalPayroll=w.reduce((n,x)=>n+Number(x.rate||0),0);let day=new Date().toLocaleDateString('en-MY',{weekday:'long',day:'numeric',month:'short',year:'numeric'});let active=projects.filter(p=>/active/i.test(p.status||'')).length;
 let perm=permitTotals(), expiring=0,now=new Date();now.setHours(0,0,0,0);for(const worker of w)for(const k of ['permitExpiry','cidbDate']){if(!worker[k])continue;let n=Math.ceil((new Date(worker[k]+'T00:00:00')-now)/86400000);if(Number.isFinite(n)&&n<=60)expiring++}
 let validCidb=w.filter(x=>String(x.cidb||'').toUpperCase()==='YES').length;
 return `<div class="dashboard-v7 dashboard-v6"><div class="dashboard-search"><svg viewBox="0 0 24 24" aria-hidden="true" fill="none" stroke="currentColor" stroke-width="1.8"><circle cx="10.8" cy="10.8" r="6.8"/><path d="m16 16 4.5 4.5" stroke-linecap="round"/></svg><input id="globalSearch" type="search" autocomplete="off" placeholder="Search workers, passport, project..." aria-label="Search across your workspace"/><kbd>⌘ K</kbd></div><div id="globalResults" class="global-results" hidden></div>
 <div class="dashboard-welcome"><div class="welcome-copy"><span class="eyebrow">YOUR SAYMA WORKSPACE</span><h1>Hi, ${esc(adminName().split(' ')[0])}<span class="welcome-stop">.</span></h1></div><span class="welcome-clock">${new Date().toLocaleTimeString('en-MY',{hour:'2-digit',minute:'2-digit',hour12:true})}</span><button class="welcome-avatar" data-act="profile" aria-label="Administrator profile">${adminPhotoMarkup()}</button></div>
 <section class="admin-hero-card"><div class="admin-hero-copy"><div class="admin-hero-kicker"><span class="admin-hero-live"></span> SAYMA BUILDERS · ADMIN WORKSPACE</div><h2>Your projects.<br>Your people. All in one place.</h2><p>Stay on top of your workforce, site activity and company performance.</p><div class="admin-hero-pills"><span><b>${w.length}</b> Workers</span><span><b>${active}</b> Active sites</span><span class="admin-hero-connection">${D.online?'● Connected':'○ Offline mode'}</span></div></div><div class="admin-hero-art" aria-hidden="true"><i class="admin-orb admin-orb-a"></i><i class="admin-orb admin-orb-b"></i><i class="admin-orb admin-orb-c"></i></div></section>
 <div class="section-title dashboard-title admin-quick-heading"><h3>Quick Links</h3><span class="micro-label">Tap to open</span></div><div class="admin-quick-grid">
 <button type="button" class="admin-quick-tile quick-aqua" data-act="go:Workers"><span class="admin-quick-icon">♟</span><strong>Workers</strong><small>Team directory</small></button>
 <button type="button" class="admin-quick-tile quick-rose" data-act="go:Sites"><span class="admin-quick-icon">▤</span><strong>Projects</strong><small>Site management</small></button>
 <button type="button" class="admin-quick-tile quick-orchid" data-act="worker-gps-attendance"><span class="admin-quick-icon">◷</span><strong>Attendance</strong><small>Daily records</small></button>
 <button type="button" class="admin-quick-tile quick-teal" data-act="go:Finance"><span class="admin-quick-icon">◈</span><strong>Payments</strong><small>Finance overview</small></button>
 </div>
 ${permitChart()}
 <div class="section-title dashboard-title"><h3>Executive summary</h3><span class="micro-label">Live from saved records</span></div><div class="executive-grid"><button type="button" class="executive-tile ex-green" data-act="go:Workers"><span class="ex-icon">♟</span><span class="ex-tag">${roleGroups().length} roles</span><b>${w.length}</b><small>Active workforce</small></button><button type="button" class="executive-tile ex-blue" data-act="go:Finance"><span class="ex-icon">◈</span><span class="ex-tag">${perm.due?Math.round(perm.paid/perm.due*100):0}% paid</span><b>${shortRm(perm.paid)}</b><small>Permit payments collected</small></button><button type="button" class="executive-tile ex-orange" data-act="reminders"><span class="ex-icon">⌛</span><span class="ex-tag">${expiring?'Attention':'Updated'}</span><b>${expiring}</b><small>Documents due ≤60 days</small></button><button type="button" class="executive-tile ex-violet" data-act="go:Workers"><span class="ex-icon">▣</span><span class="ex-tag">${validCidb}/${w.length}</span><b>${validCidb}</b><small>CIDB green cards marked valid</small></button></div>
 <section class="new-card admin-workspace"><div class="admin-circle">${adminPhotoMarkup()}</div><div class="admin-details"><div class="eyebrow">ACCOUNT ADMINISTRATOR</div><b>${esc(adminName())}</b><small>${D.online?('Connected user · '+esc(D.username||'Administrator')):'Local preview · Edit your display profile'}</small></div><button class="admin-action" data-act="profile" aria-label="Open admin profile">↗</button></section>
 <div class="section-title dashboard-title"><h3>Project financials</h3><span class="micro-label">Claims & payroll</span></div>${dashboardChart()}${dashboardRoles()}
 ${dashboardWorkspace()}
 <div class="section-title dashboard-title"><h3>Your projects</h3>${btn('All projects','go:Sites','text-btn')}</div>${projects.map((p,i)=>`<section class="new-card dashboard-project"><div class="project-stamp">${esc(p.status||'Project')}</div><div class="project-detail"><h3>${esc(p.name)}</h3><p>⌁ ${esc(p.location||'Location not set')}</p><div class="project-supervisor">Supervisor <b>${esc(p.supervisor||'Not assigned')}</b></div></div><button class="project-open" data-act="project:${i}" aria-label="Open ${esc(p.name)}">↗</button></section>`).join('')||empty('No projects added yet')}
 <div class="dashboard-footer">SAYMA BUILDERS SDN BHD · MANAGEMENT WORKSPACE</div></div>`;
}

function workerRow(w,i){return `<div class="new-line" data-act="worker:${i}"><div class="initial">${esc(w.name||'?').slice(0,1)}</div><div class="grow"><b>${esc(w.name)}</b><small>${esc(w.role||'Worker')} · ${esc(w.passport||'No passport')}</small></div><div class="right"><b>${rm(w.rate)}</b><small>/day</small></div><span class="chev">›</span></div>`}
function workers(){let arr=S.workers.map((w,i)=>({...w,i})).filter(w=>(w.name+' '+w.role+' '+w.passport+' '+w.site).toLowerCase().includes(workerSearch.toLowerCase()));return head('Workers',`${S.workers.length} workforce profiles`)+`<div class="search-wrap"><input id="workerSearch" placeholder="Search worker, role or passport…" value="${esc(workerSearch)}" aria-label="Search workers"></div><div class="section-title"><h3>Team directory</h3>${btn('+ Add worker','worker:new')}</div>${card(arr.length?arr.map(w=>workerRow(w,w.i)).join(''):empty('No matching workers'))}`}
function sites(){return head('Projects','Sites, claims & salary sheets')+`<div class="section-title"><h3>All projects</h3>${btn('+ New project','project:new')}</div>${S.projects.map((p,i)=>card(`<div class="eyebrow">${esc(p.status||'—')}</div><h2>${esc(p.name)}</h2><p>📍 ${esc(p.location||'—')}</p><div class="mini-line">Site supervisor <b>${esc(p.supervisor||'—')}</b></div><div class="button-row">${btn('Open project','project:'+i)}${btn('Edit','edit-project:'+i,'outline')}</div>`)).join('')||empty('No projects','Add your first site.')}`}
function finance(){let total=0,paid=0;for(let w of S.workers){let p=S.permitPayments[w.passport]?.[selectedYear]||{};total+=Number(p.target||0);paid+=Number(p.paid||0)}return head('Finance','Permit payments & payroll overview')+`<div class="year-row">${(S.permitYears||['2025','2027']).map(y=>btn(y,'year:'+y,y===selectedYear?'':'outline')).join('')}</div><div class="hero"><span>PERMIT BUDGET · ${esc(selectedYear)}</span><h2 id="permitBudgetTotal" style="color:#fff!important">${rm(total)}</h2><p>${rm(paid)} paid · ${rm(Math.max(total-paid,0))} outstanding</p><div class="progress"><div style="width:${total?Math.min(100,paid/total*100):0}%"></div></div></div><div class="section-title"><h3>Permit ledger</h3>${btn('Categories','categories','text-btn')}</div>${card(S.workers.map((w,i)=>{let r=S.permitPayments[w.passport]?.[selectedYear]||{};return `<div class="new-line"><div class="initial">${esc(w.name[0])}</div><div class="grow"><b>${esc(w.name)}</b><small>${rm(r.paid)} / ${rm(r.target)}</small></div>${btn('Manage','permit:'+i,'small')}</div>`}).join(''))}<div class="section-title"><h3>Other financials</h3></div>${card(`<div class="new-line" data-act="tax"><div class="initial">⌗</div><div class="grow"><b>Tax & revenue</b><small>LHDN and audit records</small></div><span class="chev">›</span></div><div class="new-line" data-act="go:Sites"><div class="initial">▤</div><div class="grow"><b>Claims & salary</b><small>Manage by project and month</small></div><span class="chev">›</span></div><div class="new-line" data-act="bank-requests"><div class="initial">▣</div><div class="grow"><b>Worker bank accounts</b><small>Review applications and assign RHB / CIMB details</small></div><span class="chev">›</span></div>`)}`}
function more(){let items=[['Documents','documents','Company documents, versions & expiry'],['Project Attendance','attendance','Manual project roster, hours and overtime'],['Attendance','worker-gps-attendance','Site locations, verified GPS entry/exit and leave requests'],['Tax records','tax','Annual tax & revenue'],['Letters','letters','Templates & print-ready letters'],['Documentation tools','tools','Saved links & utilities'],['Password vault','vault','Secure server-only credentials'],['Permit categories','categories','Manage fee types'],['Bank account requests','bank-requests','Review RHB/CIMB worker applications and add account details'],['Worker salary slips','salary-slips','Upload monthly payslips and publish them to each worker'],['Expiry reminders','reminders','Permit and CIDB due dates'],['Admin profile','profile','Account and password settings'],['Settings & sync','settings','API status and data backup']];return head('Workspace','All company tools and records')+`<div class="section-title"><h3>Management modules</h3></div>${card(items.map(([t,a,s])=>`<div class="new-line" data-act="${a}"><div class="initial">◈</div><div class="grow"><b>${esc(t)}</b><small>${esc(s)}</small></div><span class="chev">›</span></div>`).join(''))}<p class="footnote">${D.online?'Connected to original PHP API.':(window.SAYMA_PRODUCTION?'Sign in to the original portal to access company records. Production records are not stored offline.':'Offline demo: data and uploaded files remain on this device until a backend is connected.')}</p>`}

async function workerGpsAdminPage(tabName='locations'){
 gpsAdminTab=tabName||gpsAdminTab;
 if(!D.online){show('Attendance',empty('Admin server login required','Sign in to the Unified Admin Portal and connect the database before managing site locations or worker attendance.')+btn('Open Settings','settings','full'));return}
 show('Attendance',empty('Loading site locations and attendance…'),true);
 try{
  const loc=await D.request('admin_list_worker_attendance_locations');gpsAdminLocations=loc.locations||[];
  const names=[...new Set([...(S.projects||[]).map(x=>x.name),...gpsAdminLocations.map(x=>x.site_name)].filter(Boolean))].sort();
  const tabs=`<div class="filter-chips"><button class="filter-chip ${gpsAdminTab==='locations'?'active':''}" data-act="worker-gps-tab:locations">Site Locations</button><button class="filter-chip ${gpsAdminTab==='attendance'?'active':''}" data-act="worker-gps-tab:attendance">Verified Attendance</button><button class="filter-chip ${gpsAdminTab==='leave'?'active':''}" data-act="worker-gps-tab:leave">Leave Requests</button></div>`;
  let content='';
  if(gpsAdminTab==='locations'){
   const x=gpsAdminEditingLocation||{};
   content=`<div class="new-card"><div class="section-title"><h3>${x.id?'Edit approved location':'Add approved site location'}</h3></div><p class="footnote">The Site / Project name must match the worker's assigned site in Workforce & Permit. If no matching location is enabled, the worker will see NOT PERMITTED.</p><form id="workerGpsLocationForm">${field('site_name','Site / Project name',x.site_name||'','text','required maxlength="255" list="workerGpsSiteNames"')}<datalist id="workerGpsSiteNames">${names.map(n=>`<option value="${esc(n)}"></option>`).join('')}</datalist><div class="form-row">${field('latitude','Latitude',x.latitude??'','number','required step="any" min="-90" max="90"')}${field('longitude','Longitude',x.longitude??'','number','required step="any" min="-180" max="180"')}</div><div class="form-row">${field('radius_m','Allowed radius (metres)',x.radius_m||150,'number','required min="25" max="2000" step="1"')}${select('enabled','Status',[{value:'1',label:'Enabled'},{value:'0',label:'Disabled'}],x.id?(x.enabled?'1':'0'):'1')}</div><div class="button-row wrap">${btn('Use this device GPS','worker-gps-use-current','outline')}</div><button class="new-btn full" type="submit">${x.id?'Save location changes':'Save approved site location'}</button>${x.id?btn('Cancel edit','worker-gps-cancel-edit','outline full'):''}</form><p class="footnote">You can stand at the site and use current GPS, or enter latitude and longitude from Google Maps. Radius must be 25–2,000 metres.</p></div>
    ${section('Saved site geofences',gpsAdminLocations.map(x=>`<div class="file-row"><div class="file-icon">⌖</div><div class="grow"><b>${esc(x.site_name)}</b><small>${Number(x.latitude).toFixed(7)}, ${Number(x.longitude).toFixed(7)} · ${Number(x.radius_m)} m radius</small><small>${x.enabled?'Enabled — worker entry allowed inside radius':'Disabled — worker entry blocked'}</small></div><div class="doc-actions">${btn('Edit','worker-gps-edit:'+Number(x.id),'outline small')}${btn(x.enabled?'Disable':'Enable','worker-gps-toggle:'+Number(x.id),x.enabled?'danger small':'small')}</div></div>`).join('')||empty('No site locations configured','Add a site location above. Workers cannot clock in until their assigned site has an enabled geofence.'))}`;
  }else if(gpsAdminTab==='attendance'){
   const params={date:gpsAdminDate||today(),'limit':200};if(gpsAdminSite)params.site_name=gpsAdminSite;
   const r=await D.request('admin_list_worker_attendance',null,params);const rows=r.records||[];
   content=`<form id="workerGpsAttendanceFilterForm" class="new-card">${field('date','Attendance date',gpsAdminDate||today(),'date')}${select('site_name','Site',[{value:'',label:'All sites'},...names.map(n=>({value:n,label:n}))],gpsAdminSite)}<button class="new-btn full" type="submit">Filter attendance</button></form>${section('Verified worker attendance',rows.map(x=>`<article class="attendance-history-card"><div class="attendance-history-top"><b>${esc(x.worker_name)}</b><span class="status ${x.clock_out_at?'approved':(x.clock_in_at?'processing':'pending')}">${x.clock_out_at?'Completed':(x.clock_in_at?'Working':'Not started')}</span></div><div class="attendance-history-bottom"><small>${esc(x.passport_no)} · ${esc(x.site_name)} · ${esc(x.attendance_date)}</small></div><div class="attendance-history-times"><div><small>ENTRY</small><b>${esc(x.clock_in_at||'—')}</b></div><div><small>EXIT</small><b>${esc(x.clock_out_at||'—')}</b></div></div><div class="attendance-history-bottom"><small>GPS: ${x.clock_in_distance_m==null?'—':Math.round(Number(x.clock_in_distance_m))+' m from site'} · Accuracy ${x.clock_in_accuracy_m==null?'—':Math.round(Number(x.clock_in_accuracy_m))+' m'} · Radius ${Number(x.geofence_radius_m||0)} m</small></div><div class="button-row wrap">${x.has_entry_proof?btn('View entry photo','remote-file-view:admin_view_worker_attendance_proof:'+Number(x.id),'small'):''}${x.clock_in_lat!=null&&x.clock_in_lon!=null?`<a class="new-btn outline small" target="_blank" rel="noopener" href="https://www.google.com/maps?q=${encodeURIComponent(x.clock_in_lat+','+x.clock_in_lon)}">View GPS point ↗</a>`:''}</div></article>`).join('')||empty('No attendance records','No verified entry records match this date and site.'))}`;
  }else{
   const r=await D.request('admin_list_worker_leave_requests',null,{status:gpsAdminLeaveStatus});const rows=r.requests||[];
   content=`<form id="workerGpsLeaveFilterForm" class="new-card">${select('status','Request status',[{value:'pending',label:'Pending'},{value:'approved',label:'Approved'},{value:'rejected',label:'Rejected'},{value:'',label:'All requests'}],gpsAdminLeaveStatus)}<button class="new-btn full" type="submit">Filter requests</button></form>${section('Worker leave requests',rows.map(x=>`<article class="new-card"><div class="attendance-history-top"><b>${esc(x.worker_name)}</b><span class="status ${x.status==='approved'?'approved':(x.status==='rejected'?'rejected':'pending')}">${esc(String(x.status||'pending').toUpperCase())}</span></div><p class="footnote">${esc(x.passport_no)} · Submitted ${esc(x.submitted_at||'—')}</p><div class="detail-grid"><div><small>Start date</small><strong>${esc(x.start_date)}</strong></div><div><small>End date</small><strong>${esc(x.end_date)}</strong></div></div><p>${esc(x.reason)}</p>${x.admin_note?`<p class="footnote">Admin note: ${esc(x.admin_note)}</p>`:''}<div class="button-row wrap">${x.has_mc?btn('View MC','remote-file-view:admin_view_worker_leave_mc:'+Number(x.id),'outline small'):''}${x.status==='pending'?btn('Approve','worker-gps-leave-approve:'+Number(x.id),'small')+btn('Reject','worker-gps-leave-reject:'+Number(x.id),'danger small'):''}</div></article>`).join('')||empty('No leave requests','There are no requests for this status.'))}`;
  }
  show('Attendance',tabs+content,true);
 }catch(e){show('Attendance',empty('Could not load attendance module',e.message)+btn('Retry','worker-gps-attendance','full'),true)}
}
async function bankAccountsPage(){
 if(!D.online){toast('Sign in to the original Admin portal and connect to the server first.',true);return}
 try{
  const r=await D.request('admin_list_worker_bank_requests');
  const rows=Array.isArray(r.requests)?r.requests:[];
  const pending=rows.filter(x=>x.status==='pending').length;
  const processing=rows.filter(x=>x.status==='processing').length;
  const content=rows.length?rows.map(x=>{
   const status=String(x.status||'pending').toLowerCase();
   const actions=status==='pending'
    ? `<div class="button-row wrap">${btn('Approve · Processing','bank-approve:'+Number(x.id))}${btn('Reject','bank-reject:'+Number(x.id),'outline')}</div>`
    : status==='processing'
      ? `<div class="button-row">${btn('Add bank details','bank-details:'+Number(x.id))}</div>`
      : status==='approved'
        ? `<div class="bank-admin-details"><b>${esc(x.account_name||'')}</b><small>${esc(x.account_number||'')}</small></div><div class="button-row">${btn('Edit details','bank-details:'+Number(x.id),'outline')}</div>`
        : '';
   return `<div class="new-card bank-request-card"><div class="new-line"><div class="initial">▣</div><div class="grow"><b>${esc(x.worker_name||'Worker')}</b><small>${esc(x.passport_no||'')} · ${esc(x.bank_name||'')}</small></div><span class="status-pill ${esc(status)}">${esc(status)}</span></div><div class="bank-request-meta"><span>Requested</span><b>${esc(x.created_at||'—')}</b></div>${x.account_name&&status==='approved'?'':`<p class="footnote">Bank selected: ${esc(x.bank_name||'—')} · Worker ID: ${esc(x.passport_no||'—')}</p>`}${actions}</div>`;
  }).join('') : empty('No bank account requests yet','Worker applications for RHB and CIMB Bank will appear here.');
  show('Worker bank account requests',`<div class="bank-admin-summary"><div><small>Pending</small><b>${pending}</b></div><div><small>Processing</small><b>${processing}</b></div><div><small>Total</small><b>${rows.length}</b></div></div><p class="footnote">Approve a request to move it to Processing. Add the account holder name and number to publish the details to that worker's portal.</p>${content}`,true);
 }catch(e){toast(e.message||'Could not load bank account requests.',true)}
}

/* Worker Salary Slips — embedded in the Admin Mobile portal; shares api.php with Desktop. */
let adminSalarySlipSource='manual';
function salarySlipRM(value){return 'RM '+Number(value||0).toLocaleString('en-MY',{minimumFractionDigits:2,maximumFractionDigits:2});}
function salarySlipMonthLabel(value){const m=String(value||'').match(/^(\d{4})-(\d{2})$/);return m?new Date(Number(m[1]),Number(m[2])-1,1).toLocaleDateString('en-MY',{month:'long',year:'numeric'}):String(value||'—');}
function salarySlipAdminMessage(text,bad=false){const el=$('#adminSalarySlipMessage');if(!el)return;el.textContent=text;el.className='message show '+(bad?'error':'ok');}
function salarySlipParseAmount(text,pattern){
 const lines=String(text||'').replace(/\r/g,'\n').split(/\n+/).map(s=>s.replace(/\s+/g,' ').trim()).filter(Boolean);
 const amount=/\b(?:RM|MYR)?\s*([0-9][0-9,]*(?:\.\d{1,2})?)/i;
 for(let i=0;i<lines.length;i++){const match=pattern.exec(lines[i]);if(!match)continue;let found=lines[i].slice(match.index+match[0].length).match(amount);if(!found&&lines[i+1])found=lines[i+1].match(amount);if(found){const n=Number(found[1].replace(/,/g,''));if(Number.isFinite(n)&&n>0)return n;}}
 return 0;
}
function salarySlipExtractAmounts(text){return{
 net:salarySlipParseAmount(text,/\b(?:net\s*(?:pay|salary|wages|amount|payment|payable)?|nett\s*pay|take[\s-]*home(?:\s*pay)?)\b/i),
 gross:salarySlipParseAmount(text,/\bgross\s*(?:pay|salary|wages|amount)?\b/i),
 deductions:salarySlipParseAmount(text,/\b(?:total\s*)?(?:deductions?|less)\b/i)
};}
async function salarySlipLoadScript(src,globalName){
 if(window[globalName])return;
 await new Promise((resolve,reject)=>{const s=document.createElement('script');s.src=src;s.onload=resolve;s.onerror=()=>reject(Error('Could not load document analysis library.'));document.head.appendChild(s);});
 if(!window[globalName])throw Error('Document analysis library did not initialise.');
}
async function analyseAdminSalarySlip(file){
 const box=$('#adminSalarySlipAnalysis');if(!file||!box)return;
 if(file.size>12*1024*1024){salarySlipAdminMessage('File must be 12 MB or smaller.',true);return;}
 box.textContent='Analysing salary slip…';adminSalarySlipSource='manual';
 try{
  let text='';
  if(file.type==='application/pdf'||/\.pdf$/i.test(file.name)){
   await salarySlipLoadScript('https://cdnjs.cloudflare.com/ajax/libs/pdf.js/3.11.174/pdf.min.js','pdfjsLib');
   pdfjsLib.GlobalWorkerOptions.workerSrc='https://cdnjs.cloudflare.com/ajax/libs/pdf.js/3.11.174/pdf.worker.min.js';
   const doc=await pdfjsLib.getDocument({data:await file.arrayBuffer()}).promise,pages=[];
   for(let p=1;p<=Math.min(doc.numPages,5);p++){const page=await doc.getPage(p),content=await page.getTextContent();pages.push(content.items.map(x=>x.str).join(' '));}
   text=pages.join('\n');adminSalarySlipSource='pdf-text';
   if(text.trim().length<20){
    await salarySlipLoadScript('https://cdn.jsdelivr.net/npm/tesseract.js@5/dist/tesseract.min.js','Tesseract');
    const page=await doc.getPage(1),viewport=page.getViewport({scale:1.8}),canvas=document.createElement('canvas');canvas.width=viewport.width;canvas.height=viewport.height;
    await page.render({canvasContext:canvas.getContext('2d'),viewport}).promise;
    const ocr=await Tesseract.recognize(canvas,'eng');text=ocr.data.text||'';adminSalarySlipSource='ocr';
   }
  }else{
   await salarySlipLoadScript('https://cdn.jsdelivr.net/npm/tesseract.js@5/dist/tesseract.min.js','Tesseract');
   const ocr=await Tesseract.recognize(file,'eng');text=ocr.data.text||'';adminSalarySlipSource='ocr';
  }
  const values=salarySlipExtractAmounts(text);
  if(values.net)$('#adminSalarySlipNet').value=values.net.toFixed(2);
  if(values.gross)$('#adminSalarySlipGross').value=values.gross.toFixed(2);
  if(values.deductions)$('#adminSalarySlipDeductions').value=values.deductions.toFixed(2);
  $('#adminSalarySlipSource').value=adminSalarySlipSource;
  box.innerHTML='<b>Automatic analysis complete</b><p>'+(values.net?'Net Pay detected':'Net Pay not detected')+' · '+(values.gross?'Gross Pay detected':'Gross Pay not detected')+' · '+(values.deductions?'Deductions detected':'Deductions not detected')+'</p><small>Compare the amounts with the original slip before publishing.</small>';
  if(!values.net)salarySlipAdminMessage('Net Pay was not detected. Enter it manually and verify it.',true);
 }catch(error){
  adminSalarySlipSource='manual';if($('#adminSalarySlipSource'))$('#adminSalarySlipSource').value='manual';
  box.textContent='Automatic analysis unavailable. Enter the amounts manually and verify them against the original slip.';
  salarySlipAdminMessage(error.message||'Manual amount entry is available.',true);
 }
}
async function loadAdminSalarySlipRows(){
 const target=$('#adminSalarySlipRows');if(!target)return;
 target.innerHTML='<div class="empty-state">Loading published salary slips…</div>';
 const result=await D.request('admin_list_worker_salary_slips');
 const rows=Array.isArray(result.slips)?result.slips:[];adminSalarySlipRows=rows;
 $('#adminSalarySlipCount').textContent=String(rows.length);
 $('#adminSalarySlipWorkerCount').textContent=String(new Set(rows.map(x=>x.worker_account_id)).size);
 $('#adminSalarySlipNetTotal').textContent=salarySlipRM(rows.reduce((sum,x)=>sum+Number(x.net_amount||0),0));
 target.innerHTML=rows.length?rows.map(x=>`<div class="new-card salary-slip-admin-row">
  <div class="salary-slip-admin-top"><div class="grow"><b>${esc(x.worker_name)}</b><small>${esc(x.passport_no)} · ${esc(salarySlipMonthLabel(x.pay_period))}</small></div><strong>${salarySlipRM(x.net_amount)}</strong></div>
  <div class="salary-slip-admin-file">${esc(x.original_filename||'Salary slip')}</div>
  <div class="button-row wrap">${btn('View original','salary-slip-view:'+Number(x.id),'outline')}${btn('Delete','salary-slip-delete:'+Number(x.id),'danger')}</div>
 </div>`).join(''):'<div class="empty-state">No salary slips uploaded yet. Published slips will appear here.</div>';
}
async function workerSalarySlipsPage(){
 if(!D.online){const connected=await D.connect();if(!connected){toast('Sign in to the Admin Portal to manage worker salary slips.',true);return;}}
 try{
  const result=await D.request('admin_list_worker_salary_slip_workers');
  const workers=Array.isArray(result.workers)?result.workers:[];
  const workerOptions=workers.map(w=>`<option value="${Number(w.id)}">${esc(w.worker_name)} · ${esc(w.passport_no)}</option>`).join('');
  show('Worker salary slips',`
   <div class="salary-slip-admin-layout">
    <div class="new-card salary-slip-admin-form-card">
     <div class="section-title"><h3>Upload Salary Slip</h3><span class="status-pill">Admin</span></div>
     <p class="footnote">Upload a PDF or image. The portal will try to read Net Pay, Gross Pay and Deductions automatically. Verify all amounts before publishing.</p>
     <div id="adminSalarySlipMessage" class="message"></div>
     <form id="adminWorkerSalarySlipForm">
      <label class="new-field"><span>Worker</span><select id="adminSalarySlipWorker" name="worker_account_id" required><option value="">Select worker</option>${workerOptions}</select></label>
      <label class="new-field"><span>Salary Month</span><input id="adminSalarySlipPeriod" name="pay_period" type="month" required value="${today().slice(0,7)}"></label>
      <label class="new-field"><span>Salary Slip · PDF / JPG / PNG / WEBP</span><input id="adminSalarySlipFile" name="salary_slip" type="file" required accept=".pdf,.jpg,.jpeg,.png,.webp,application/pdf,image/jpeg,image/png,image/webp"></label>
      <div id="adminSalarySlipAnalysis" class="salary-slip-analysis">Choose a file to start automatic analysis.</div>
      <div class="salary-slip-admin-amounts">
       <label class="new-field"><span>Net Pay / Take-home (RM)</span><input id="adminSalarySlipNet" name="net_amount" type="number" min="0.01" step="0.01" required placeholder="0.00"></label>
       <label class="new-field"><span>Gross Pay (RM)</span><input id="adminSalarySlipGross" name="gross_amount" type="number" min="0" step="0.01" placeholder="0.00"></label>
       <label class="new-field"><span>Deductions (RM)</span><input id="adminSalarySlipDeductions" name="deductions_amount" type="number" min="0" step="0.01" placeholder="0.00"></label>
      </div>
      <input id="adminSalarySlipSource" type="hidden" name="analysis_source" value="manual">
      <label class="salary-slip-confirm"><input id="adminSalarySlipConfirm" type="checkbox" required><span>I have checked the figures against the original salary slip.</span></label>
      <button class="new-btn full" type="submit" id="adminSalarySlipSubmit">Upload & Publish to Worker</button>
     </form>
    </div>
    <div class="new-card salary-slip-admin-list-card">
     <div class="section-title"><h3>Published Salary Slips</h3>${btn('Refresh','salary-slip-refresh','outline')}</div>
     <div class="salary-slip-admin-summary">
      <div><small>Total slips</small><b id="adminSalarySlipCount">0</b></div>
      <div><small>Workers covered</small><b id="adminSalarySlipWorkerCount">0</b></div>
      <div><small>Net pay total</small><b id="adminSalarySlipNetTotal">RM 0.00</b></div>
     </div>
     <div id="adminSalarySlipRows"><div class="empty-state">Loading salary slip archive…</div></div>
     <p class="footnote">One salary slip per worker per month is stored. Uploading a corrected slip for the same month replaces the previous file.</p>
    </div>
   </div>`,true);
  const form=$('#adminWorkerSalarySlipForm');
  form.addEventListener('submit',async e=>{
   e.preventDefault();
   const formElement=e.currentTarget; // keep the form reference after await; currentTarget is not retained by the browser
   if(!$('#adminSalarySlipConfirm').checked){salarySlipAdminMessage('Please confirm the amounts match the original slip.',true);return;}
   const button=$('#adminSalarySlipSubmit');button.disabled=true;button.textContent='Uploading…';
   try{
    const data=new FormData(formElement);
    data.set('analysis_source',$('#adminSalarySlipSource').value||'manual');
    const response=await D.request('admin_upload_worker_salary_slip',data);
    salarySlipAdminMessage(response.message||'Salary slip published to the worker portal.');
    formElement.reset();
    $('#adminSalarySlipPeriod').value=today().slice(0,7);
    $('#adminSalarySlipAnalysis').textContent='Choose a file to start automatic analysis.';
    $('#adminSalarySlipSource').value='manual';
    await loadAdminSalarySlipRows();
   }catch(error){salarySlipAdminMessage(error.message||'Salary slip upload failed.',true);}
   finally{button.disabled=false;button.textContent='Upload & Publish to Worker';}
  });
  await loadAdminSalarySlipRows();
 }catch(error){toast(error.message||'Could not load salary slip module.',true);}
}

function render(animate=false){let fn={Home:home,Workers:workers,Sites:sites,Finance:finance,More:more}[tab];$('#app').innerHTML=fn();$('#bottomNav').innerHTML=nav();$('#appHeader').innerHTML=`<div class="header-id"><img class="header-logo" src="./assets/sayma-logo.png" alt="SAYMA Builders company logo"><div class="header-name"><b>SAYMA <span>BUILDERS</span></b><small>CONSTRUCTION MANAGEMENT</small></div></div><div class="header-right"><button class="header-search-btn" type="button" data-act="focus-search" aria-label="Search all workers and projects">⌕</button><button class="header-admin" type="button" data-act="profile" aria-label="Administrator profile"><span class="header-admin-avatar">${adminPhotoMarkup()}</span><span class="header-admin-meta"><b>${esc(adminName())}</b><small>${D.online?'● Connected':(window.SAYMA_PRODUCTION?'● Login required':'● Local mode')}</small></span></button></div>`;if(animate)M.animateMain()}
function pdfPreviewUrl(url,name){try{const u=new URL(url,location.href);u.searchParams.set('preview','pdf');u.searchParams.set('filename',fileSafeName(name||'SAYMA-document.pdf'));return u.href}catch(e){return url}}
function fileSafeName(name){return String(name||'document').replace(/[\\/:*?"<>|\r\n]/g,'_').slice(0,180)||'document'}
function fileIsPdf(blob,name){const type=String(blob?.type||'').toLowerCase();return type?type.includes('pdf'):/\.pdf$/i.test(String(name||''))}
function fileIsImage(blob,name){const type=String(blob?.type||'').toLowerCase();return type?type.startsWith('image/'):/\.(jpg|jpeg|png|webp|gif)$/i.test(String(name||''))}
function blobBase64(blob){return new Promise((resolve,reject)=>{const r=new FileReader();r.onload=()=>resolve(String(r.result||'').split(',')[1]||'');r.onerror=()=>reject(r.error||Error('Could not prepare the document for the app viewer.'));r.readAsDataURL(blob);})}
function renderFileBlob(blob){
 const info=currentFileInfo||{};const box=$('#filePreview');if(!box)return;
 if(docPreviewUrl){URL.revokeObjectURL(docPreviewUrl);docPreviewUrl=null;}
 const pdf=fileIsPdf(blob,info.filename),image=fileIsImage(blob,info.filename);
 if(pdf&&window.SaymaNative&&typeof window.SaymaNative.postMessage==='function'){
  box.innerHTML='<div class="file-native-open"><div class="empty-glyph">▤</div><b>Opening secure PDF viewer…</b><p>The PDF is opening inside the SAYMA app. Return here to download it or close this preview.</p></div>';
  blobBase64(blob).then(data=>{if(data.length>30*1024*1024)throw Error('PDF is too large for the in-app preview. Use Download instead.');window.SaymaNative.postMessage(JSON.stringify({action:'openPdfBase64',data,filename:fileSafeName(info.filename)}));}).catch(err=>{box.innerHTML=empty('Could not open PDF',err.message||'Try downloading the file.');});
  return;
 }
 docPreviewUrl=URL.createObjectURL(blob);
 if(pdf){const pdfSrc=info.previewUrl&&D.online?pdfPreviewUrl(info.previewUrl,info.filename):docPreviewUrl;box.innerHTML=`<iframe title="PDF document preview" class="preview-frame" src="${esc(pdfSrc)}#toolbar=1&navpanes=0&view=FitH"></iframe>`;}
 else if(image){box.innerHTML=`<img class="preview-image" alt="${esc(info.filename||'Document')}" src="${docPreviewUrl}">`;}
 else{box.innerHTML=empty('Preview is not available for this file type','Use Download to save and open this document.');}
}
function showFileBlob(title,blob,filename,downloadUrl='',previewUrl=''){
 currentFileInfo={blob,filename:fileSafeName(filename),downloadUrl,previewUrl};
 show(title,card(`<div class="file-viewer-meta"><b>${esc(currentFileInfo.filename)}</b><small>${esc(blob.type||'Document')} · ${(blob.size/1024).toFixed(0)} KB</small></div><div id="filePreview" class="file-preview"><div class="empty-state">Preparing preview…</div></div><div class="button-row wrap">${btn('Download','doc-download','full')}${btn('Close','close','outline')}</div>`),true);
 renderFileBlob(blob);
}
async function remoteFilePreview(action,params,filename,title='Document preview'){
 const url=D.apiUrl(action,params||{}),downloadParams={...(params||{}),download:1};
 show(title,card(`<div class="file-viewer-meta"><b>${esc(filename||'Document')}</b><small>Secure company document</small></div><div id="filePreview" class="file-preview"><div class="empty-state">Loading document securely…</div></div><div class="button-row wrap">${btn('Download','doc-download','full')}${btn('Close','close','outline')}</div>`),true);
 currentFileInfo={blob:null,filename:fileSafeName(filename),downloadUrl:D.apiUrl(action,downloadParams),previewUrl:url};
 try{
  const blob=typeof D.blob==='function'&&params&&params.id!==undefined
    ? await D.blob(action,params.id)
    : await (async()=>{const response=await fetch(url,{credentials:'same-origin',cache:'no-store',headers:{Accept:'application/pdf,image/*,application/octet-stream'}});if(!response.ok)throw Error('Could not load document (HTTP '+response.status+').');return response.blob()})();
  currentFileInfo.blob=blob;renderFileBlob(blob);
 }catch(error){const box=$('#filePreview');if(box)box.innerHTML=empty('Cannot preview document',error.message||'Please try again.');toast(error.message||'Document preview failed.',true);}
}
async function downloadCurrentFile(){
 const info=currentFileInfo;if(!info){toast('No document is ready to download.',true);return;}
 const name=fileSafeName(info.filename);
 try{
  if(window.SaymaNative&&typeof window.SaymaNative.postMessage==='function'&&info.downloadUrl){window.SaymaNative.postMessage(JSON.stringify({action:'downloadFile',url:info.downloadUrl,filename:name}));toast('Download started. Check your Downloads folder.');return;}
  if(window.SaymaNative&&typeof window.SaymaNative.postMessage==='function'&&info.blob){const data=await blobBase64(info.blob);window.SaymaNative.postMessage(JSON.stringify({action:'saveBase64File',data,filename:name,mimeType:info.blob.type||'application/octet-stream'}));toast('File saved to Downloads.');return;}
  if(info.downloadUrl){const a=document.createElement('a');a.href=info.downloadUrl;a.download=name;a.rel='noopener';document.body.appendChild(a);a.click();a.remove();return;}
  if(info.blob){download(info.blob,name);return;}
  throw Error('The document has not finished loading.');
 }catch(error){toast(error.message||'Download failed.',true);}
}
function openToolViewer(item){
 if(!item||!/^https:\/\//i.test(item.url||'')){toast('Invalid HTTPS tool URL',true);return;}
 const url=String(item.url);const safeName=esc(item.name||'Website');
 show('Website · '+(item.name||'Tool'),`<div class="tool-viewer-toolbar"><span>${safeName}</span><div class="button-row wrap">${btn('Refresh','tool-refresh','outline small')}${btn('Open in browser','tool-external','small')}</div></div><div class="tool-embed-note">This website is being opened inside the SAYMA portal. Some external websites block embedded viewing for security reasons; use “Open in browser” if the site refuses to load.</div><iframe id="mobileToolFrame" class="mobile-tool-frame" title="${safeName}" src="${esc(url)}" referrerpolicy="strict-origin-when-cross-origin" allow="clipboard-read; clipboard-write; geolocation; camera; microphone; fullscreen" allowfullscreen></iframe>`,true);
 currentToolUrl=url;
}
let currentToolUrl='';
function show(title,html,wide=false){modalType=title;$('#modalRoot').innerHTML=`<div class="overlay" id="overlay"><div class="sheet ${wide?'wide-sheet':''}" role="dialog" aria-modal="true" aria-label="${esc(title)}"><button class="close" data-act="close" aria-label="Close">×</button><div class="sheet-title"><div class="eyebrow">SAYMA BUILDERS</div><h2>${esc(title)}</h2></div>${html}</div></div>`;M.animateSheet()}
function close(immediate=false){if(docPreviewUrl){URL.revokeObjectURL(docPreviewUrl);docPreviewUrl=null;}currentFileInfo=null;currentToolUrl='';modalType='';let root=$('#modalRoot'),old=root.firstElementChild;if(!old)return;if(immediate||!M.exitSheet(old))root.innerHTML=''}
function section(title,html){return `<div class="section-title"><h3>${esc(title)}</h3></div>${html}`}
let tabSwitchSeq=0;
function switchTab(next){
 if(!['Home','Workers','Sites','Finance','More'].includes(next))return Promise.resolve();
 if(tab===next&&!modalType)return Promise.resolve();
 // Main tabs are local view switches: do not show the full-screen route loader.
 // Render immediately, then use a short compositor-friendly transition.
 const seq=++tabSwitchSeq;
 tab=next;
 close(true);
 render(false);
 if(seq===tabSwitchSeq) M.animateMain();
 return Promise.resolve();
}
const loadingModules={settings:'Settings & sync',profile:'Admin profile',reminders:'Expiry reminders',documents:'Company documents',attendance:'Project Attendance','worker-gps-attendance':'Attendance',tax:'Tax records',letters:'Official letters',tools:'Documentation tools',vault:'Password vault',categories:'Permit categories','doc-categories':'Document categories','doc-tax':'Tax documents'};
function screenLabel(a){
 if(loadingModules[a])return loadingModules[a];
 if(/^worker:\d+$/.test(a))return 'Worker profile';
 if(/^project:\d+$/.test(a))return 'Project details';
 if(/^permit:\d+$/.test(a))return 'Permit history';
 if(/^grid:|^open-grid:/.test(a))return 'Financial sheets';
 if(/^doc-view:|^view-worker-doc:/.test(a))return 'Document preview';
 if(/^claim-pdf-/.test(a))return 'Claim PDF';
 if(/^doc-history:/.test(a))return 'Document history';
 return null;
}
async function handleAction(a){let label=screenLabel(a);if(label)return M.route('Opening '+label,()=>action(a),{minDuration:290});return action(a)}

async function saved(msg='Saved'){try{await D.save();toast(D.online?msg+' · Synced to database':msg+' · Stored on device');render()}catch(e){toast((window.SAYMA_PRODUCTION?'Not saved — server connection required: ':'Saved locally, but server sync failed: ')+e.message,true);render()}}
function workerEditor(i){let w=i<0?{name:'',passport:'',role:'',site:S.projects[0]?.name||'Unassigned',rate:'',cidb:'NO',cidbDate:''}:S.workers[i];const projectSites=[...new Set(['Unassigned',...(S.projects||[]).map(p=>String(p.name||'').trim()).filter(Boolean),...(w.site?[String(w.site)]:[])])];show(i<0?'Add worker':'Edit worker',`<form id="workerForm" data-index="${i}">${field('name','Full name',w.name,'text','required')}${field('passport','Passport number',w.passport,'text','required')}${field('role','Role',w.role)}${select('site','Assign to Project / Site',projectSites,w.site||'Unassigned')}<p class="footnote" style="margin-top:-8px">Choose the worker’s actual assigned site. This is also used to check the GPS attendance geofence.</p>${field('rate','Daily rate (RM)',w.rate,'number','min="0" step="0.01"')}${select('cidb','CIDB status',['YES','NO'],w.cidb)}${field('cidbDate','CIDB date',w.cidbDate,'date')}${field('permitExpiry','Work permit expiry',w.permitExpiry||'','date')}${field('mobile','Mobile number',w.mobile||'','tel')}${field('email','Email',w.email||'','email')}${select('gender','Gender',['male','female'],w.gender||'male')}${select('remind','Expiry reminders',['YES','NO'],w.remind===false?'NO':'YES')}<button class="new-btn full" type="submit">Save worker</button></form>${i>=0?btn('Remove worker','remove-worker:'+i,'danger full'):''}`)}
async function workerProfile(i){wIndex=i;let w=S.workers[i];if(!w)return;show('Worker profile',`${card(`<div class="profile-head"><div class="profile-avatar">${esc(w.name[0])}</div><div><h2>${esc(w.name)}</h2><p>${esc(w.role||'Worker')} · ${esc(w.site||'—')}</p></div></div><div class="detail-grid">${[['Passport',w.passport],['Daily rate',rm(w.rate)],['CIDB',w.cidb],['CIDB date',w.cidbDate||'—'],['Permit expiry',w.permitExpiry||'—'],['Mobile',w.mobile||'—'],['Email',w.email||'—']].map(x=>`<div><small>${x[0]}</small><strong>${esc(x[1]||'—')}</strong></div>`).join('')}</div><div class="button-row">${btn('Edit worker & project assignment','edit-worker:'+i)}${btn('Permit history','permit:'+i,'outline')}</div>`)}${section('Worker documents',`<div id="workerDocs">${empty('Loading documents…')}</div>`)}${btn('+ Upload worker document','upload-worker-doc','full')}<p class="footnote">Passport, Visa, CIDB, permit and medical files are linked to this worker.</p>`);try{await D.workerDocs(w);if(modalType==='Worker profile')drawWorkerDocs()}catch(e){toast(e.message,true);if($('#workerDocs'))$('#workerDocs').innerHTML=empty('Could not load worker files',e.message)}}
function drawWorkerDocs(){let w=S.workers[wIndex],key=(w.passport||w.name||'').trim().toUpperCase(),d=D.workerDocsCache[key]||[];$('#workerDocs').innerHTML=d.length?d.map(x=>`<div class="file-row"><div class="file-icon">▧</div><div class="grow"><b>${esc(x.doc_type)}</b><small>${esc(x.original_name||'')}</small></div><button class="new-btn small" data-act="view-worker-doc:${esc(x.id)}">View</button><button class="icon-delete" data-act="delete-worker-doc:${esc(x.id)}">×</button></div>`).join(''):empty('No worker documents','Upload a file to view it here.')}
function projectEditor(i){let p=i<0?{name:'',location:'',supervisor:'',status:'Active Construction'}:S.projects[i];show(i<0?'New project':'Edit project',`<form id="projectForm" data-index="${i}">${field('name','Project name',p.name,'text','required')}${field('location','Location',p.location)}${select('supervisor','Supervisor',S.workers.map(x=>x.name),p.supervisor)}${select('status','Status',['Active Construction','On Hold','Completed'],p.status)}<button class="new-btn full">Save project</button></form>${i>=0?btn('Delete project','remove-project:'+i,'danger full'):''}`)}
function ensureProjectFinanceSheet(projectName,type,month){
 S.projectGrids[projectName]||={claims:{},salary:{}};
 S.projectGrids[projectName][type]||={};
 if(!S.projectGrids[projectName][type][month]){
  S.projectGrids[projectName][type][month]={
   headers:type==='salary'?(S.salaryHeaders||['Worker Name','Passport No','Total Payment (RM)']):['Item No','Description of Claim','Unit','Qty','Rate (MYR)','Total (MYR)'],
   rows:[]
  };
 }
 return S.projectGrids[projectName][type][month];
}
function projectSheetAmount(projectName,type,month){
 const sheet=S.projectGrids?.[projectName]?.[type]?.[month];
 if(!sheet)return 0;
 if(sheet.summaryTotal!==undefined&&sheet.summaryTotal!==null&&sheet.summaryTotal!=='')return Math.max(0,numberVal(sheet.summaryTotal));
 const headers=(sheet.headers||[]).map(x=>String(x||'').trim().toLowerCase());
 let idx=-1;
 if(type==='claims'){
  idx=headers.findIndex(x=>/^total\s*claim(?:\s*\(rm\))?$/.test(x));
  if(idx<0)idx=headers.findIndex(x=>/total\s*(claim|amount)|grand\s*total|total\s*\(myr\)/.test(x));
  if(idx<0&&headers.length>11)idx=11;
 }else{
  idx=headers.findIndex(x=>/^total\s*payment(?:\s*\(rm\))?$/.test(x));
  if(idx<0)idx=headers.findIndex(x=>/total\s*payment/.test(x));
  if(idx<0&&headers.length>10)idx=10;
  if(idx<0)idx=headers.findIndex(x=>/total\s*net|net\s*(pay|salary|amount|payment)/.test(x));
 }
 if(idx<0)idx=headers.length-1;
 return (sheet.rows||[]).reduce((sum,row)=>sum+numberVal(Array.isArray(row)?row[idx]:0),0);
}
function projectMonthTotals(projectName,month){
 const claim=projectSheetAmount(projectName,'claims',month),salary=projectSheetAmount(projectName,'salary',month);
 return {claim,salary,profit:claim-salary};
}
function projectProfitGraph(t){
 const max=Math.max(1,t.claim,t.salary,Math.abs(t.profit));
 const bar=(label,value,cls)=>`<div class="project-profit-bar-row"><div class="project-profit-bar-label"><span>${label}</span><b>${rm(value)}</b></div><div class="project-profit-track"><i class="${cls}" style="width:${Math.max(value===0?0:3,Math.min(100,Math.abs(value)/max*100)).toFixed(1)}%"></i></div></div>`;
 return `<div class="project-profit-chart"><div class="project-profit-chart-head"><div><b>Financial comparison</b><small>Claim − Salary = Profit</small></div><span>RM</span></div>${bar('Total Claim',t.claim,'claim')}${bar('Total Salary',t.salary,'salary')}${bar('Net Profit',t.profit,'profit '+(t.profit<0?'negative':''))}</div>`;
}
function projectDocumentRows(items,type){
 const rows=(items||[]).filter(x=>String(x.document_type||'').toUpperCase()===type);
 if(!rows.length)return '<p class="project-doc-empty">No files uploaded for this month.</p>';
 return rows.map(x=>`<div class="file-row project-finance-file"><div class="file-icon">${String(x.mime_type||'').includes('pdf')?'PDF':String(x.mime_type||'').startsWith('image/')?'IMG':'FILE'}</div><div class="grow"><b>${esc(x.original_name||'Project document')}</b><small>${esc(x.created_at||'')} · ${(Number(x.file_size||0)/1024).toFixed(0)} KB</small></div>${btn('View','project-doc-view:'+x.id,'outline small')}${btn('×','project-doc-delete:'+x.id,'danger small')}</div>`).join('');
}
async function projectDetail(i,month){
 projectIdx=i;let p=S.projects[i];if(!p)return;
 const data=S.projectGrids[p.name]||{claims:{},salary:{}};
 const current=months[new Date().getMonth()];
 projectFinanceMonth=month||((projectFinanceMonth&&months.includes(projectFinanceMonth))?projectFinanceMonth:current);
 if(!months.includes(projectFinanceMonth))projectFinanceMonth=current;
 const m=projectFinanceMonth,t=projectMonthTotals(p.name,m);
 projectDocuments=[];
 if(D.online){try{const d=await D.request('list_project_documents',null,{project:p.name,month:m});projectDocuments=d.items||[]}catch(e){console.warn('Project documents unavailable:',e);}}
 const claimSheet=ensureProjectFinanceSheet(p.name,'claims',m),salarySheet=ensureProjectFinanceSheet(p.name,'salary',m);
 const claimValue=claimSheet.summaryTotal!==undefined?claimSheet.summaryTotal:t.claim;
 const salaryValue=salarySheet.summaryTotal!==undefined?salarySheet.summaryTotal:t.salary;
 const monthOptions=months;
 show('Project workspace',`
  <div class="project-banner"><b>${esc(p.name)}</b><small>📍 ${esc(p.location||'—')} · ${esc(p.status||'—')}</small><span>Supervisor: ${esc(p.supervisor||'—')}</span></div>
  <section class="project-finance-overview">
   <div class="project-finance-overview-head"><div><span class="eyebrow">PROJECT FINANCIALS</span><h3>Profit overview</h3><small>Monthly calculation · Claim minus Salary</small></div>${select('month','Month',monthOptions,m).replace('name="month"','id="projectFinanceMonth" name="month"')}</div>
   <div class="project-profit-metrics"><div class="project-profit-metric claim"><small>Total Claim</small><b>${rm(t.claim)}</b></div><div class="project-profit-metric salary"><small>Total Salary</small><b>${rm(t.salary)}</b></div><div class="project-profit-metric profit ${t.profit<0?'negative':''}"><small>Total Profit</small><b>${rm(t.profit)}</b></div></div>
   ${projectProfitGraph(t)}
  </section>
  <div class="project-finance-entry-grid">
   <section class="project-finance-entry claim-entry"><div class="project-finance-entry-title"><b>Claim</b><small>Project claim amount and supporting file</small></div><form id="projectClaimTotalForm" data-type="claims" data-month="${esc(m)}">${field('total_amount','Total Claim (RM)',claimValue,'number','min="0" step="0.01" required')}<button class="new-btn full" type="submit">Save Claim Total</button></form>${btn('Upload Claim Document','project-doc-upload:CLAIM','outline full')}<div class="project-finance-docs">${projectDocumentRows(projectDocuments,'CLAIM')}</div></section>
   <section class="project-finance-entry salary-entry"><div class="project-finance-entry-title"><b>Salary</b><small>Salary total, salary claim and invoice</small></div><form id="projectSalaryTotalForm" data-type="salary" data-month="${esc(m)}">${field('total_amount','Total Salary (RM)',salaryValue,'number','min="0" step="0.01" required')}<button class="new-btn full" type="submit">Save Salary Total</button></form>${btn('Upload Salary Claim','project-doc-upload:SALARY_CLAIM','outline full')}${btn('Upload Salary Invoice','project-doc-upload:INVOICE','outline full')}<div class="project-finance-docs">${projectDocumentRows(projectDocuments,'SALARY_CLAIM')}${projectDocumentRows(projectDocuments,'INVOICE')}</div></section>
  </div>
  <div class="button-row wrap">${btn('Claims sheet','open-grid:claims')}${btn('Salary sheet','open-grid:salary','outline')}${btn('Project Attendance','attendance','outline')}</div>
  ${section('Available months',`<div class="project-months">${['claims','salary'].map(type=>`<h4>${type.toUpperCase()}</h4><div class="button-row wrap">${Object.keys(data[type]||{}).map(mm=>btn(mm,'grid:'+type+':'+encodeURIComponent(mm),'outline')).join('')||'<small>No sheets</small>'}</div>`).join('')}</div>`)}
  <p class="footnote">Claim and salary totals are saved in the central database. Profit is calculated automatically as Total Claim − Total Salary. Supporting PDF and image files are stored with this project and month.</p>`,true);
}
function projectDocumentUpload(type){
 if(!D.online){toast('Connect to the company server before uploading project files.',true);return;}
 const labels={CLAIM:'Claim document',SALARY_CLAIM:'Salary claim',INVOICE:'Salary invoice'};
 const p=S.projects[projectIdx];if(!p)return;
 show('Upload '+(labels[type]||'Project document'),`<p class="subtext">${esc(p.name)} · ${esc(projectFinanceMonth)}</p><form id="projectDocumentForm" data-type="${esc(type)}" data-month="${esc(projectFinanceMonth)}">${field('file','Choose PDF, JPG, PNG or other supported file','','file','required accept=".pdf,.jpg,.jpeg,.png,.webp,.xls,.xlsx,.doc,.docx"')}<p class="footnote">Maximum 20 MB. The file is saved in the project document archive and remains available across devices.</p><button class="new-btn full" type="submit">Upload & Save</button></form>`);
}
async function viewProjectDocument(id){
 const item=(projectDocuments||[]).find(x=>Number(x.id)===Number(id));
 const filename=item?.original_name||('Project-document-'+id);
 const url=D.online?D.apiUrl('view_project_document',{id}):'';
 try{const blob=await D.blob('view_project_document',id);showFileBlob('Project document',blob,filename,url?D.apiUrl('view_project_document',{id,download:1}):'',url);}
 catch(e){show('Project document',card(empty('Cannot preview project document',e.message))+btn('Close','close','full'),true);toast(e.message||'Could not open project document.',true);}
}
async function viewClaimPdf(claimId){
 const filename='Claim-'+fileSafeName(claimId)+'.pdf';
 const url=D.apiUrl('view_claim_pdf',{claim_id:String(claimId)});
 try{const blob=typeof D.claimPdfBlob==='function'?await D.claimPdfBlob(claimId):await (async()=>{const response=await fetch(url,{credentials:'same-origin',cache:'no-store'});if(!response.ok)throw Error(response.status===404?'No PDF is stored for this Claim ID.':'Could not load claim PDF (HTTP '+response.status+').');return response.blob()})();showFileBlob('Claim PDF',blob,filename,D.apiUrl('view_claim_pdf',{claim_id:String(claimId),download:1}),url);}
 catch(e){show('Claim PDF',card(empty('Cannot preview claim PDF',e.message))+btn('Close','close','full'),true);toast(e.message||'Could not open claim PDF.',true);}
}
async function viewRemoteWorkerFile(action,id,filename,title){await remoteFilePreview(action,{id:Number(id)},filename||'Worker document',title||'Worker document');}
function openGrid(type,month){gridType=type;let p=S.projects[projectIdx];let group=S.projectGrids[p.name]||{claims:{},salary:{}};let a=group[type]||{};gridMonth=month||Object.keys(a)[0]||'September';let current=a[gridMonth];if(!current){current={headers:type==='salary'?(S.salaryHeaders||['Worker Name','Passport No','Total Payment (RM)']):['Item No','Description of Claim','Unit','Qty','Rate (MYR)','Total (MYR)'],rows:[]};(S.projectGrids[p.name]||={claims:{},salary:{}})[type]||={};S.projectGrids[p.name][type][gridMonth]=current;D.persist()}
let headers=current.headers||[],claimIdCol=headers.findIndex(h=>String(h).trim().toLowerCase()==='claim id');show(`${type==='salary'?'Salary':'Claims'} · ${gridMonth}`,`<div class="project-banner"><b>${esc(p.name)}</b><small>${esc(type.toUpperCase())} · ${esc(gridMonth)}</small></div><div class="button-row wrap">${btn('◀ Months','project:'+projectIdx,'outline')}${btn('+ New month','month-new:'+type,'outline')}${btn('+ Row','grid-add-row')}${btn('CSV Export','grid-csv','outline')}</div><p class="muted tiny-copy">Scroll the table horizontally. Edit any cell, then press Save changes.${claimIdCol>=0?' Each Claim ID row can store or open its original PDF.':''}</p><form id="gridForm"><div class="scroll-table"><table class="editable-grid"><thead><tr>${headers.map(h=>`<th>${esc(h)}</th>`).join('')}<th></th></tr></thead><tbody>${(current.rows||[]).map((r,ri)=>`<tr>${headers.map((h,ci)=>`<td><input aria-label="${esc(h)} row ${ri+1}" name="cell_${ri}_${ci}" value="${esc(r[ci]||'')}"></td>`).join('')}<td>${claimIdCol>=0?btn('PDF','claim-pdf-upload:'+ri,'outline small')+btn('View','claim-pdf-view:'+ri,'outline small'):''}${btn('×','grid-del-row:'+ri,'danger small')}</td></tr>`).join('')}</tbody></table></div><button class="new-btn full" type="submit">Save changes</button></form>`,true)}
function gridRead(){let p=S.projects[projectIdx],sheet=S.projectGrids[p.name][gridType][gridMonth];if(!$('#gridForm'))return;sheet.rows=sheet.rows.map((row,ri)=>sheet.headers.map((h,ci)=>$('#gridForm').elements.namedItem('cell_'+ri+'_'+ci)?.value??row[ci]??''));}
function monthNew(type){show('Add financial month',`<form id="monthForm" data-type="${type}">${select('month','Month',months,months[new Date().getMonth()])}<button type="submit" class="new-btn full">Create sheet</button></form>`)}
function makeAttendance(){let projects=S.projects;attendanceProject=Math.min(attendanceProject,projects.length-1);let p=projects[attendanceProject];if(!p){show('Project Attendance',empty('No projects yet'));return}let mths=Object.keys(S.projectGrids[p.name]?.salary||{});if(!mths.includes(attendanceMonth))attendanceMonth=mths[0]||months[new Date().getMonth()];let key=`${p.name}||${attendanceMonth}||${attendanceDate}`,records=S.attendance[key]||{};let grid=S.projectGrids[p.name]?.salary?.[attendanceMonth];let names=grid?.rows?.map(r=>({name:r[0],passport:r[1]})).filter(x=>x.name)||[];if(!names.length)names=S.workers.filter(w=>w.site===p.name).map(w=>({name:w.name,passport:w.passport}));show('Project Attendance',`${select('project','Project',projects.map((x,i)=>({label:x.name,value:i})),attendanceProject).replace('name="project"','id="attendanceProject" name="project"')}${select('month','Salary month',mths.length?mths:[attendanceMonth],attendanceMonth).replace('name="month"','id="attendanceMonth" name="month"')}${field('date','Attendance date',attendanceDate,'date').replace('name="date"','id="attendanceDate" name="date"')}<form id="attendanceForm"><div class="att-head">${esc(p.name)} · ${esc(attendanceMonth)} <b>${names.length} workers</b></div>${names.map((w,i)=>{let a=records[w.passport||w.name]||{status:'Present',start:'08:00',close:'17:00',ot:'2.00',remarks:''};return `<div class="attendance-card"><div class="att-worker"><b>${esc(w.name)}</b><small>${esc(w.passport||'')}</small></div><input type="hidden" name="key_${i}" value="${esc(w.passport||w.name)}"><input type="hidden" name="name_${i}" value="${esc(w.name)}"><div class="att-fields">${select('status_'+i,'Status',['Present','Absent','Leave'],a.status)}${field('start_'+i,'Start',a.start,'time')}${field('close_'+i,'Finish',a.close,'time')}${field('ot_'+i,'OT hours',a.ot,'number','min="0" max="24" step="0.5"')}${field('remarks_'+i,'Remarks',a.remarks)}</div></div>`}).join('')||empty('No workers in this salary month')}<input type="hidden" name="count" value="${names.length}"><button class="new-btn full" type="submit">Save attendance</button></form>`,true)}
function permit(i){let w=S.workers[i];if(!w)return;let rec=S.permitPayments[w.passport]?.[selectedYear]||{target:0,paid:0,entries:[],costItems:[]};let costItems=Array.isArray(rec.costItems)?rec.costItems:[];let simpleTarget=!costItems.some(x=>!x.legacy&&x.id!=='legacy-base');let targetEditor=simpleTarget?section('Update target',`<form id="targetForm" data-index="${i}">${field('target','Target (RM)',rec.target,'number','min="0" step="0.01"')}<button class="new-btn full" type="submit">Save target</button></form>`):section('Target cost breakdown',`<p class="footnote">This target is calculated from the detailed permit cost items maintained in the Desktop portal. It is shown here as read-only to protect the existing breakdown. You can still record payments from Mobile.</p>`);show('Permit payment',`<div class="project-banner"><b>${esc(w.name)}</b><small>${esc(w.passport)} · ${esc(selectedYear)}</small></div><div class="detail-grid">${[['Target',rm(rec.target)],['Paid',rm(rec.paid)],['Balance',rm(Math.max(0,Number(rec.target)-Number(rec.paid)))],['Status',rec.status||'—']].map(([a,b])=>`<div><small>${a}</small><strong>${esc(b)}</strong></div>`).join('')}</div>${targetEditor}<div class="button-row wrap">${btn('Upload receipt','upload-payment-receipt:'+i,'outline')}${btn('View receipt','view-payment-receipt:'+i,'outline')}</div>${section('Add payment',`<form id="paymentForm" data-index="${i}">${select('category','Category',S.permitCategories||[],S.permitCategories?.[0])}${field('amount','Amount (RM)',0,'number','required min="0.01" step="0.01"')}${field('date','Payment date',today(),'date')}${field('note','Note','')}<button class="new-btn full" type="submit">Record payment</button></form>`)}${section('Payment history', (rec.entries||[]).length?(rec.entries||[]).map((e,k)=>`<div class="file-row"><div class="grow"><b>${esc(e.category||'Payment')}</b><small>${esc(e.date||'—')} · ${esc(e.note||'')}</small></div><b>${rm(e.amount)}</b>${btn('×','pay-delete:'+i+':'+k,'danger small')}</div>`).join(''):empty('No payment entries'))}`,true)}
function categories(){show('Permit categories',`<form id="permitCategoryForm">${field('category','New category','','text','required')}<button class="new-btn full" type="submit">Add category</button></form>${section('Current categories',card((S.permitCategories||[]).map((c,i)=>`<div class="file-row"><div class="grow"><b>${esc(c)}</b></div>${btn('×','permit-category-delete:'+i,'danger small')}</div>`).join('')))}`)}
function documents(){kind='company';const items=D.docs.filter(x=>(docFilter==='ALL'||x.category===docFilter)&&(x.title+' '+x.category+' '+x.original_name).toLowerCase().includes(docSearch.toLowerCase()));show('Company documents',`<div class="doc-summary"><div><strong>${D.docs.length}</strong><small>Files</small></div><div><strong>${D.categories.length}</strong><small>Categories</small></div><div><strong>${D.docs.filter(d=>d.expiry_date&&d.expiry_date<today()).length}</strong><small>Expired</small></div></div><div class="button-row">${btn('+ Upload file','upload-document')}${btn('Manage categories','doc-categories','outline')}</div><div class="search-wrap"><input id="docSearch" placeholder="Search company documents…" value="${esc(docSearch)}"></div><div class="filter-chips"><button class="filter-chip ${docFilter==='ALL'?'active':''}" data-act="doc-filter:ALL">All</button>${D.categories.map(c=>`<button class="filter-chip ${docFilter===c.key?'active':''}" data-act="doc-filter:${esc(c.key)}">${esc(c.label)}</button>`).join('')}</div>${section('Document register',items.length?items.map(x=>documentRow(x)).join(''):empty('No documents found','Upload SSM, CIDB, Tax, Insurance or other documents.'))}<p class="footnote">${D.online?'Files from your original database.':'Offline files are saved inside this browser (IndexedDB) and cannot be viewed on other devices until uploaded to the server.'}</p>`,true)}
function documentRow(d){let cat=D.categories.find(c=>c.key===d.category)?.label||d.category||'Other',isExpired=d.expiry_date&&d.expiry_date<today();return `<div class="file-row doc-item"><div class="file-icon">▧</div><div class="grow"><b>${esc(d.title||d.original_name||'Document')}</b><small>${esc(cat)} · v${esc(d.version||1)} · ${esc(d.original_name||'')}</small><small class="${isExpired?'red-text':''}">${d.expiry_date?'Expires '+esc(d.expiry_date):'No expiry date'}</small></div><div class="doc-actions">${btn('View','doc-view:'+esc(d.id),'small')}${btn('Edit','doc-edit:'+esc(d.id),'outline small')}</div></div>`}
function docForm(d){let edit=!!d;show(edit?'Edit document':'Upload company document',`<form id="docForm" data-id="${edit?esc(d.id):''}">${field('title','Document title',d?.title||'','text','required')}${select('category','Category',D.categories.map(x=>({label:x.label,value:x.key})),d?.category||D.categories[0]?.key)}<div class="form-row">${field('issue_date','Issue date',d?.issue_date||'','date')}${field('expiry_date','Expiry date',d?.expiry_date||'','date')}</div>${area('notes','Notes',d?.notes||'')}${field('file','File (PDF, image, Word, etc.)','','file',edit?'':'required')}<p class="footnote">Max 15 MB. ${edit?'Adding a file creates a new version. You can update metadata without replacing the file.':'Documents can be previewed and downloaded after upload.'}</p><button class="new-btn full" type="submit">${edit?'Save changes':'Upload document'}</button></form>${edit?`<div class="button-row wrap">${btn('View file','doc-view:'+esc(d.id),'outline')}${btn('Versions','doc-history:'+esc(d.id),'outline')}${btn('Delete','doc-delete:'+esc(d.id),'danger')}</div>`:''}`,true)}
function docCategories(){show('Document categories',`<form id="docCategoryForm">${field('label','New category','','text','required')}<button class="new-btn full" type="submit">Add category</button></form>${section('Categories',D.categories.map(c=>`<div class="file-row"><div class="grow"><b>${esc(c.label)}</b><small>${esc(c.key)}</small></div>${btn('Rename','doc-cat-rename:'+esc(c.key),'outline small')}</div>`).join(''))}`)}
async function docView(id,docKind='company',historical=false){
 let list=docKind==='worker'?(D.workerDocsCache[(S.workers[wIndex].passport||S.workers[wIndex].name||'').trim().toUpperCase()]||[]):D.docs;
 let item=list.find(x=>String(x.id)===String(id));
 if(!item&&historical)item={id,original_name:historical,title:historical};
 if(!item)return toast('Document not found',true);
 kind=docKind;
 const action=docKind==='worker'?'view_worker_document':'view_document';
 try{
  const blob=await D.documentBlob(item,docKind);
  if(!blob)throw Error('No file data found in this browser.');
  const downloadUrl=D.online?D.apiUrl(action,{id:item.id,download:1}):'';
  showFileBlob('Document preview',blob,item.original_name||item.title||'document',downloadUrl,D.online?D.apiUrl(action,{id:item.id}):'');
 }catch(e){show('Document preview',card(empty('Cannot preview document',e.message))+btn('Close','close','full'),true);toast(e.message,true)}
}
async function docHistory(id){let x=D.docs.find(d=>String(d.id)===String(id));if(!x)return;show('Document history',`<p class="subtext">${esc(x.title)}</p><div id="historyList">Loading versions…</div>`);try{let hist=await D.historyDoc(x);if(!$('#historyList'))return;$('#historyList').innerHTML=hist.map(x=>`<div class="file-row"><div class="file-icon">v${esc(x.version)}</div><div class="grow"><b>${esc(x.original_name||'File')}</b><small>${esc(x.uploaded_at||'')}</small></div>${btn('View','doc-history-view:'+esc(x.id),'outline small')}</div>`).join('')||empty('No history entries')}catch(e){toast(e.message,true)}}
function workerDocUpload(){let w=S.workers[wIndex];show('Upload worker document',`<p class="subtext">For ${esc(w.name)} · ${esc(w.passport||'')}</p><form id="workerDocForm">${select('doc_type','Document type',['Passport','Visa','CIDB','Work Permit','Medical / FOMEMA','Insurance','Other'],'Passport')}${field('file','File','','file','required')}<button class="new-btn full" type="submit">Upload and save</button></form><p class="footnote">Max 15 MB; linked to worker passport.</p>`)}
function taxes(){let x=(S.taxRecords||[]).slice().sort((a,b)=>Number(b.year||0)-Number(a.year||0));let latest=x[0];show('Tax & revenue',`<div class="doc-summary"><div><strong>${x.length}</strong><small>Years</small></div><div><strong>${latest?rm(latest.taxAmount):'—'}</strong><small>Tax payable</small></div></div><div class="section-title"><h3>Annual records</h3>${btn('+ Add','tax:new')}</div>${x.map((r,i)=>card(`<div class="file-row"><div class="grow"><b>${esc(r.year)} Tax year</b><small>Tax: ${rm(r.taxAmount)} · Revenue: ${rm(r.revenue)}</small><small>${esc(r.taxDocument||'')} ${esc(r.auditDocument||'')}</small></div>${btn('Edit','tax:'+i,'small')}</div>`)).join('')||empty('No tax records','Add annual tax and revenue figures manually.')}${btn('Open LHDN documents','doc-tax','outline full')}<p class="footnote">Original AI/OCR tax extraction requires its PHP backend service. Figures here can be entered manually and saved through the normal state API.</p>`,true)}
function taxEdit(i){let x=i<0?{year:String(new Date().getFullYear()),taxAmount:0,revenue:0,taxDocument:'',auditDocument:''}:S.taxRecords[i];show(i<0?'Add tax record':'Edit tax record',`<form id="taxForm" data-index="${i}">${field('year','Assessment year',x.year,'number','required min="2000" max="2100"')}${field('taxAmount','Tax payable (RM)',x.taxAmount,'number','min="0" step="0.01"')}${field('revenue','Revenue (RM)',x.revenue,'number','min="0" step="0.01"')}${field('taxDocument','Tax document reference',x.taxDocument)}${field('auditDocument','Audit document reference',x.auditDocument)}<button class="new-btn full" type="submit">Save tax record</button></form>${i>=0?btn('Delete record','tax-delete:'+i,'danger full'):''}`)}
function letters(){show('Official letters',`<div class="project-banner"><b>Letter Generator</b><small>Generate letters with original SAYMA templates, then print or save as PDF.</small></div>${btn('+ Create new letter','letter:new','full')}${section('Templates',Object.entries(S.templates||{}).map(([key,t])=>`<div class="file-row"><div class="file-icon">✉</div><div class="grow"><b>${esc(t.name||key)}</b><small>${esc(t.subject||'')}</small></div>${btn('Use','letter-type:'+esc(key),'small')}</div>`).join(''))}${section('Letter history',(S.letterHistory||[]).length?(S.letterHistory||[]).slice(0,50).map((x,i)=>`<div class="file-row"><div class="grow"><b>${esc(x.subject||x.templateName||'Official letter')}</b><small>${esc(x.referenceNo||'')} · ${esc(x.issueDate||'')}</small></div>${btn('View','letter-history:'+i,'outline small')}</div>`).join(''):empty('No letters created'))}`,true)}
function letterComposer(key='immigration'){let templates=S.templates||{},x=templates[key]||Object.values(templates)[0]||{name:'Custom letter',body:'',fields:[]};show('Create official letter',`<form id="letterForm" data-template="${esc(key)}">${select('template','Template',Object.entries(templates).map(([k,v])=>({label:v.name||k,value:k})),key).replace('name="template"','id="templateSelect" name="template"')}${field('date','Issue date',today(),'date')}${field('to','Recipient name',x.to||'','text','required')}${field('company','Recipient company',x.company||'')}${field('subject','Subject',x.subject||'','text','required')}${(x.fields||[]).map(f=>field('field_'+f.key,f.label||f.key,'',f.type==='date'?'date':'text')).join('')}${area('body','Letter body (placeholders supported)',x.body||'')}<button class="new-btn full" type="submit">Generate & save letter</button></form><p class="footnote">Use the Print/Save as PDF button on generated letter. This version does not reproduce the original branded PDF and QR verification exactly.</p>`,true)}
const htmlPrintable=(x)=>`<!doctype html><html lang="en"><head><meta charset="utf-8"><title>${esc(x.referenceNo)}</title><style>body{font:15px/1.65 Arial,sans-serif;padding:20mm;max-width:800px;margin:auto;color:#172133}.logo{color:#214a76;font-size:24px;font-weight:900}.head{border-bottom:3px solid #315e8c;padding-bottom:18px;margin-bottom:30px}.meta{display:flex;justify-content:space-between;margin-bottom:25px}.subject{margin:20px 0;text-transform:uppercase;font-weight:700;text-decoration:underline}.body{white-space:pre-wrap}footer{margin-top:60px;font-size:12px;color:#718399}@media print{body{padding:0}button{display:none}}</style></head><body><div class="head"><div class="logo">SAYMA BUILDERS SDN BHD</div><small>Official correspondence</small></div><div class="meta"><span>Ref: ${esc(x.referenceNo)}</span><span>${esc(x.issueDate)}</span></div><p>To: ${esc(x.recipientName)}</p><p>${esc(x.recipientCompany||'')}</p><div class="subject">${esc(x.subject)}</div><div class="body">${esc(x.body||'')}</div><p style="margin-top:45px">Yours faithfully,<br><b>For and on behalf of SAYMA BUILDERS SDN BHD</b></p><footer>Generated from SAYMA Mobile · Verify against the original portal before using as official legal correspondence.</footer></body></html>`;
function printLetter(item){const win=window.open('','_blank');if(!win){toast('Allow pop-ups to print the letter',true);return;}win.document.open();win.document.write(htmlPrintable(item));win.document.close();win.focus();setTimeout(()=>win.print(),400)}
function letterHistoryView(i){let x=S.letterHistory[i];if(!x)return;show('Official letter',`${card(`<div class="project-banner"><b>${esc(x.subject||'Official letter')}</b><small>${esc(x.referenceNo)} · ${esc(x.issueDate)}</small></div><p class="letter-display">${esc(x.body||'This historical letter was generated in the original system. Use its stored PDF if available.')}</p>${btn(x.pdfDataUri?'Open original PDF':'Print / Save as PDF','letter-print:'+i,'full')}`)}`)}
function toolPage(){show('Documentation tools',`${btn('+ Add tool','tool:new','full')}${section('Saved tools',(D.tools||[]).map((x,i)=>`<div class="file-row"><div class="file-icon">↗</div><div class="grow"><b>${esc(x.name)}</b><small>${esc(x.description||x.url||'')}</small></div>${btn('Open','tool-open:'+i,'small')}${btn('×','tool-delete:'+i,'danger small')}</div>`).join('')||empty('No tools','Add a secure HTTPS link to a frequently used website or utility.'))}`)}
function toolForm(){show('Add documentation tool',`<form id="toolForm">${field('name','Tool name','','text','required')}${field('url','URL (https://...)','https://','url','required')}${field('description','Description','')}<button class="new-btn full" type="submit">Save tool</button></form>`)}
async function vaultPage(){if(!D.online){show('Password vault',empty('Server login required','The vault uses your existing encrypted server storage. It is intentionally not copied into offline localStorage.')+btn('Configure API','settings','full'));return}show('Password vault',empty('Loading secure vault…'));try{const st=await D.vault('status');if(!st.configured){show('Password vault setup',`<form id="vaultSetup">${field('master_password','New master password','','password','required minlength="8"')}${field('confirm','Confirm master password','','password','required')}<button class="new-btn full">Set up vault</button></form>`);return}if(!st.pin_configured){show('Set vault PIN',`<form id="vaultPin">${field('master_password','Master password','','password','required')}${field('pin','Six-digit PIN','','password','required pattern="[0-9]{6}" maxlength="6"')}<button class="new-btn full">Save PIN</button></form>`);return}let r=await D.vault('list');vaultItems=r.items||[];show('Password vault',`${btn('+ Add credential','vault-add','full')}${section('Stored accounts',vaultItems.map((x,i)=>`<div class="file-row"><div class="file-icon">🔒</div><div class="grow"><b>${esc(x.title)}</b><small>${esc(x.category)} · ${esc(x.username||'')}</small></div>${btn('View','vault-view:'+i,'outline small')}${btn('Edit','vault-edit:'+i,'small')}</div>`).join('')||empty('No saved credentials'))}`,true)}catch(e){show('Password vault',empty('Vault error',e.message))}}
function vaultEdit(i){let x=i<0?{category:'Company',title:'',username:'',notes:''}:vaultItems[i];show(i<0?'Add credential':'Edit credential',`<form id="vaultForm" data-index="${i}">${field('category','Category',x.category)}${field('title','Title',x.title,'text','required')}${field('username','Username',x.username)}${field('password',i<0?'Password':'New password (optional)','','password',i<0?'required':'')}${area('notes','Notes',x.notes)}${field('pin','Six-digit master PIN','','password','required pattern="[0-9]{6}" maxlength="6"')}<button class="new-btn full" type="submit">Save to server vault</button></form>${i>=0?btn('Delete credential','vault-delete:'+i,'danger full'):''}<p class="footnote">The password and PIN are sent only to your authenticated PHP API and are not saved in this app.</p>`)}
async function profilePage(){if(!D.online){show('Admin profile',`<div class="project-banner"><b>${esc(adminName())}</b><small>Local-only display profile · Server login required for database saving</small></div><form id="localProfileForm">${adminPhotoEditor()}${field('name','Display name',adminProfile.name||'Administrator','text','required')}${field('mobile','Mobile (optional)',adminProfile.mobile||'','tel')}${field('email','Email (optional)',adminProfile.email||'','email')}<button class="new-btn full" type="submit">Save display profile</button></form><p class="footnote">Offline profile details stay on this device only. Connect to the company server to save your photo and profile to MySQL.</p>`);return}show('Admin profile',empty('Loading profile…'));try{let d=await D.extraRequest('profile.php','get'),p=d.profile||{};adminProfile=p;show('Admin profile',`<div class="project-banner"><b>${esc(p.name||d.username||'Administrator')}</b><small>Username: ${esc(d.username||'—')} · Last login: ${esc(d.last_login||'—')}</small></div><form id="profileForm">${adminPhotoEditor()}${field('name','Full name',p.name)}${field('mobile','Mobile',p.mobile,'tel')}${field('email','Email',p.email,'email')}<button class="new-btn full" type="submit">Save profile to database</button></form>${section('Change password',`<form id="passwordForm">${field('current','Current password','','password','required')}${field('new','New password (min 10 characters)','','password','required minlength="10"')}${field('repeat','Repeat password','','password','required')}<button class="new-btn full" type="submit">Update password</button></form>`)}${btn('Sign out','logout','danger full')}`)}catch(e){show('Admin profile',empty('Profile unavailable',e.message))}}
async function remindersPage(){let now=new Date(today()+'T00:00:00'),arr=[];for(let w of S.workers){for(let [label,key] of [['Permit','permitExpiry'],['CIDB','cidbDate']]){if(!w[key])continue;let days=Math.ceil((new Date(w[key]+'T00:00:00')-now)/86400000);if(days<=60)arr.push({name:w.name,document:label,days,date:w[key]})}}arr.sort((a,b)=>a.days-b.days);let info=`<div class="doc-summary"><div><strong>${arr.filter(x=>x.days<0).length}</strong><small>Expired</small></div><div><strong>${arr.filter(x=>x.days>=0).length}</strong><small>Due ≤ 60d</small></div><div><strong>${S.workers.length}</strong><small>Workers</small></div></div>${section('Upcoming dates',arr.map(x=>`<div class="file-row"><div class="file-icon">${x.days<0?'!':'◷'}</div><div class="grow"><b>${esc(x.name)}</b><small>${esc(x.document)} · ${esc(x.date)}</small></div><b class="${x.days<0?'red-text':''}">${x.days<0?Math.abs(x.days)+'d late':x.days+'d'}</b></div>`).join('')||empty('No expiring documents','Worker profiles need a permit expiry or CIDB date.'))}`;
 if(!D.online){show('Expiry reminders',info+`<p class="footnote">Automatic WhatsApp/email reminders require the original reminders.php and a configured server cron job. This offline panel only shows due dates.</p>`);return}
 try{let r=await D.extraRequest('reminders.php','get'),s=r.settings||{};show('Expiry reminders',info+`${section('Server reminder setup',`<form id="reminderForm">${select('enabled','Automatic reminders',[{value:'YES',label:'Enabled'},{value:'NO',label:'Disabled'}],s.enabled?'YES':'NO')}${field('daysBefore','Days before expiry (comma separated)',(s.daysBefore||[30,7,1]).join(','))}${area('template_text','Reminder message',s.template_text||'')}${field('email_subject','Email subject',s.email_subject||'')}${select('permitExpiry','Watch work permits',['YES','NO'],s.docs?.permitExpiry?'YES':'NO')}${select('cidbDate','Watch CIDB',['YES','NO'],s.docs?.cidbDate?'YES':'NO')}<button class="new-btn full" type="submit">Save reminder preferences</button></form>`)}${btn('Preview due reminders','reminders-preview','outline full')}<p class="footnote">WhatsApp/email credentials, voice messages and cron jobs must be configured on the original server. Save here will preserve existing server connection settings.</p>`)}catch(e){show('Expiry reminders',info+empty('Server reminder settings unavailable',e.message))}}
function settings(){show('Settings & sync',`<div class="status-panel ${D.online?'ok':''}"><b>${D.online?'● Database connected':(window.SAYMA_PRODUCTION?'● Login required':'● Offline / not connected')}</b><p>${D.online?'Logged in as '+esc(D.username):esc(D.error||'Sign in to the original company portal to access live company records.')}</p></div>${section('App experience',`<div class="experience-settings"><button type="button" class="experience-toggle" data-act="sound-toggle" aria-pressed="${M.sound}"><span><strong>Soft tap sounds</strong><small>Quiet click feedback when pressing buttons</small></span><span class="experience-switch"><i></i></span></button><button type="button" class="experience-toggle" data-act="motion-toggle" aria-pressed="${M.motion}"><span><strong>Smooth animations</strong><small>Screen transitions, preloader and sheet motion${M.isReduced?' · Device reduced-motion is on':''}</small></span><span class="experience-switch"><i></i></span></button></div>`)}<form id="settingsForm">${field('apiPath','API path (same website)',D.config.apiPath,'text','required')}${select('mode','Connection mode',window.SAYMA_PRODUCTION?[{value:'auto',label:'Auto connect'}]:[{value:'auto',label:'Auto connect'}, {value:'offline',label:'Offline only'}],D.config.mode)}<button class="new-btn full" type="submit">Save settings & test connection</button></form><div class="button-row wrap">${btn('Retry connection','sync-now','outline')}${btn(window.SAYMA_PRODUCTION?'Export data snapshot (no server files)':'Export full local backup','backup-export','outline')}${window.SAYMA_PRODUCTION?'':btn('Import backup','backup-import','outline')}</div>${D.online?`<p class="footnote">Connected to PHP `+esc(D.config.apiPath)+`. Your existing login session is used for database requests.</p>`:`<p class="footnote">To connect live data: upload the <strong>sayma-mobile-v2</strong> folder to your existing PHP website; set the API path to the actual <strong>api.php</strong>. Open the app through HTTPS while logged into your original portal. Do not overwrite the original api.php or config.php.</p>`}<p class="footnote">Backup contains offline file attachments (may be large). Existing online documents remain on the original server. Do not upload a backup from an untrusted source.</p>`,true)}
async function action(a){try{
 if(a==='close'){close();return}
 if(a==='sound-toggle'){M.toggleSound();settings();return}
 if(a==='motion-toggle'){M.toggleMotion();settings();return}
 if(a==='chart:paid'||a==='chart:target'){permitView=a.slice(6);let panel=document.querySelector('.permit-analysis');if(panel)panel.outerHTML=permitChart();return}
 if(a==='chart-year'){let years=S.permitYears||['2025','2027'];selectedYear=years[(years.indexOf(selectedYear)+1+years.length)%years.length];let panel=document.querySelector('.permit-analysis');if(panel)panel.outerHTML=permitChart();return}
 if(a.startsWith('go:')){await switchTab(a.slice(3));return}
 if(a==='focus-search'){if(tab!=='Home')await switchTab('Home');setTimeout(()=>{let input=$('#globalSearch');input?.focus();input?.scrollIntoView({behavior:'smooth',block:'center'})},120);return}if(a==='salary-slips'){await workerSalarySlipsPage();return}if(a==='salary-slip-refresh'){await loadAdminSalarySlipRows();toast('Salary slip archive refreshed');return}if(a.startsWith('salary-slip-view:')){let id=a.split(':')[1],row=(adminSalarySlipRows||[]).find(x=>Number(x.id)===Number(id));await viewRemoteWorkerFile('admin_view_worker_salary_slip',id,row?.original_filename||('Salary-slip-'+id+'.pdf'),'Salary slip');return}if(a.startsWith('salary-slip-delete:')){let id=a.split(':')[1];if(!confirm('Delete this salary slip from the worker portal archive?'))return;await D.request('admin_delete_worker_salary_slip',{id});await loadAdminSalarySlipRows();toast('Salary slip deleted');return}if(a==='bank-requests'){await bankAccountsPage();return}
 if(a.startsWith('bank-approve:')){let id=a.split(':')[1];if(!confirm('Approve this bank request and move it to Processing?'))return;await D.request('admin_review_worker_bank_request',{request_id:id,decision:'approve'});toast('Bank request moved to Processing');await bankAccountsPage();return}
 if(a.startsWith('bank-reject:')){let id=a.split(':')[1];if(!confirm('Reject this bank account request?'))return;await D.request('admin_review_worker_bank_request',{request_id:id,decision:'reject'});toast('Bank request rejected');await bankAccountsPage();return}
 if(a.startsWith('bank-details:')){let id=a.split(':')[1];let r=await D.request('admin_list_worker_bank_requests');let item=(r.requests||[]).find(x=>String(x.id)===String(id));if(!item)throw Error('Bank request not found');show('Add bank account details',`<form id="bankDetailsForm" data-id="${Number(id)}">${field('account_name','Account holder name',item.account_name||'')}${field('account_number','Bank account number',item.account_number||'','text','required inputmode="numeric" pattern="[0-9]{6,34}" minlength="6" maxlength="34"') }<p class="footnote">Saving these details marks the request as approved and makes the account details visible only to this worker and Admin.</p><button class="new-btn full" type="submit">Save & publish details</button></form>`,true);return}
 if(a==='settings'){settings();return}if(a==='profile'){await profilePage();return}if(a==='remove-admin-photo'){adminProfile.photo='';const preview=$('#adminPhotoPreview');if(preview)preview.innerHTML=adminPhotoMarkup();render();toast('Photo removed — save profile to update the database');return}if(a==='reminders'){await remindersPage();return}if(a==='logout'){if(confirm('Sign out of the original portal?')){if(window.SaymaNative&&window.SaymaNative.postMessage)window.SaymaNative.postMessage(JSON.stringify({action:'clearRememberedLogin'}));await D.request('logout');location.href=new URL('login.html',new URL(D.config.apiPath,location.href)).href;}return}if(a==='reminders-preview'){let r=await D.extraRequest('reminders.php','preview',{});toast((r.rows||[]).length+' due reminders found');return}if(a==='sync-now'){toast('Checking API connection…');let ok=await D.connect();render();settings();toast(ok?'Connected to original server':'Connection unavailable. Check settings.',!ok);return}
 if(a.startsWith('worker:')){let i=a.split(':')[1];i==='new'?workerEditor(-1):await workerProfile(Number(i));return}
 if(a.startsWith('edit-worker:')){workerEditor(Number(a.split(':')[1]));return}
 if(a.startsWith('remove-worker:')){let i=Number(a.split(':')[1]);if(!confirm('Delete this worker? Documents and permit records may still exist on the server.'))return;S.workers.splice(i,1);await saved('Worker removed');close();return}
 if(a.startsWith('project:')){let i=a.split(':')[1];if(i==='new')projectEditor(-1);else await projectDetail(Number(i));return}
 if(a.startsWith('project-doc-upload:')){projectDocumentUpload(a.slice('project-doc-upload:'.length));return}
 if(a.startsWith('project-doc-view:')){await viewProjectDocument(Number(a.slice('project-doc-view:'.length)));return}
 if(a.startsWith('project-doc-delete:')){const id=Number(a.slice('project-doc-delete:'.length));if(!confirm('Delete this project document permanently?'))return;await D.request('delete_project_document',{id});toast('Project document deleted');await projectDetail(projectIdx,projectFinanceMonth);return}
 if(a.startsWith('edit-project:')){projectEditor(Number(a.split(':')[1]));return}
 if(a.startsWith('remove-project:')){let i=Number(a.split(':')[1]);if(!confirm('Delete project and local financial tables? This cannot be undone.'))return;let p=S.projects[i];if(D.online){toast('For safety, delete this project from your original portal; server financial rows cannot be removed by this mobile API.',true);return;}delete S.projectGrids[p.name];S.projects.splice(i,1);await saved('Project removed');close();return}
 if(a.startsWith('open-grid:')){openGrid(a.slice(10),projectFinanceMonth);return}
 if(a.startsWith('grid:')){let [,type,enc]=a.split(':');openGrid(type,decodeURIComponent(enc));return}
 if(a==='grid-add-row'){gridRead();let sheet=S.projectGrids[S.projects[projectIdx].name][gridType][gridMonth];sheet.rows.push(sheet.headers.map(()=>'') );openGrid(gridType,gridMonth);return}
 if(a.startsWith('grid-del-row:')){if(!confirm('Delete this row?'))return;gridRead();S.projectGrids[S.projects[projectIdx].name][gridType][gridMonth].rows.splice(Number(a.slice(13)),1);openGrid(gridType,gridMonth);return}
 if(a.startsWith('month-new:')){monthNew(a.split(':')[1]);return}
 if(a==='grid-csv'){gridRead();let s=S.projectGrids[S.projects[projectIdx].name][gridType][gridMonth],rows=[s.headers,...s.rows];download(new Blob(['\ufeff'+rows.map(r=>r.map(x=>'"'+String(x??'').replace(/"/g,'""')+'"').join(',')).join('\r\n')],{type:'text/csv;charset=utf-8'}),`SAYMA-${gridType}-${gridMonth}.csv`);return}
 if(a==='worker-gps-attendance'){await workerGpsAdminPage('locations');return}
 if(a.startsWith('worker-gps-tab:')){await workerGpsAdminPage(a.split(':')[1]);return}
 if(a==='worker-gps-use-current'){
  if(!navigator.geolocation)throw Error('This browser does not support GPS location.');
  navigator.geolocation.getCurrentPosition(pos=>{const lat=document.querySelector('#workerGpsLocationForm input[name=latitude]'),lon=document.querySelector('#workerGpsLocationForm input[name=longitude]');if(lat)lat.value=pos.coords.latitude.toFixed(7);if(lon)lon.value=pos.coords.longitude.toFixed(7);toast('Current GPS coordinates filled. Verify that you are at the correct work site.');},err=>toast(err.message||'Could not detect current GPS location.',true),{enableHighAccuracy:true,maximumAge:0,timeout:20000});return
 }
 if(a.startsWith('worker-gps-edit:')){const id=Number(a.split(':')[1]);gpsAdminEditingLocation=gpsAdminLocations.find(x=>Number(x.id)===id)||null;await workerGpsAdminPage('locations');return}
 if(a==='worker-gps-cancel-edit'){gpsAdminEditingLocation=null;await workerGpsAdminPage('locations');return}
 if(a.startsWith('worker-gps-toggle:')){const id=Number(a.split(':')[1]),x=gpsAdminLocations.find(v=>Number(v.id)===id);if(!x)throw Error('Site location not found');if(!confirm((x.enabled?'Disable':'Enable')+' attendance for '+x.site_name+'?'))return;await D.request('admin_save_worker_attendance_location',{site_name:x.site_name,latitude:x.latitude,longitude:x.longitude,radius_m:x.radius_m,enabled:x.enabled?0:1});toast('Site attendance location updated');await workerGpsAdminPage('locations');return}
 if(a.startsWith('worker-gps-leave-approve:')||a.startsWith('worker-gps-leave-reject:')){const parts=a.split(':'),decision=parts[0].endsWith('approve')?'approve':'reject',id=Number(parts[1]);if(!confirm((decision==='approve'?'Approve':'Reject')+' this leave request?'))return;const note=prompt('Optional note for the worker:','')||'';await D.request('admin_review_worker_leave_request',{request_id:id,decision,admin_note:note});toast('Leave request '+(decision==='approve'?'approved':'rejected'));await workerGpsAdminPage('leave');return}
 if(a==='attendance'){makeAttendance();return}
 if(a.startsWith('year:')){selectedYear=a.split(':')[1];render();return}
 if(a.startsWith('permit:')){permit(Number(a.split(':')[1]));return}
 if(a.startsWith('pay-delete:')){let [,i,j]=a.split(':');let w=S.workers[Number(i)],rec=S.permitPayments[w.passport][selectedYear];if(!confirm('Remove this payment entry?'))return;rec.entries.splice(Number(j),1);rec.paid=rec.entries.reduce((t,x)=>t+Number(x.amount||0),0);await saved('Payment entry removed');permit(Number(i));return}
 if(a==='categories'){categories();return}
 if(a.startsWith('permit-category-delete:')){let i=Number(a.split(':')[1]);if(!confirm('Remove category? Existing payments retain their history.'))return;S.permitCategories.splice(i,1);await saved('Category removed');categories();return}
 if(a.startsWith('upload-payment-receipt:')){let i=Number(a.split(':')[1]),w=S.workers[i];if(!w)return;show('Upload permit receipt',`<p class="subtext">${esc(w.name)} · ${esc(selectedYear)}</p><form id="paymentReceiptForm" data-index="${i}">${field('file','Receipt (PDF, image or Word)','','file','required accept=".pdf,.jpg,.jpeg,.png,.webp,.doc,.docx"')}<p class="footnote">Maximum 15 MB. The receipt is stored with this worker in the company database.</p><button class="new-btn full" type="submit">Upload receipt</button></form>`);return}
 if(a.startsWith('view-payment-receipt:')){let i=Number(a.split(':')[1]),w=S.workers[i];if(!w)return;let docs=await D.workerDocs(w),item=docs.find(x=>x.doc_type==='Payment Receipt '+selectedYear);if(!item){toast('No receipt found for '+selectedYear,true);return}await docView(item.id,'worker');return}
 if(a.startsWith('claim-pdf-upload:')){let ri=Number(a.split(':')[1]),p=S.projects[projectIdx],sheet=S.projectGrids[p.name]?.[gridType]?.[gridMonth],col=sheet?.headers?.findIndex(h=>String(h).trim().toLowerCase()==='claim id'),claimId=sheet?.rows?.[ri]?.[col];if(!claimId){toast('Enter and save a Claim ID in this row first.',true);return}show('Upload claim PDF',`<p class="subtext">${esc(p.name)} · ${esc(gridMonth)} · ${esc(claimId)}</p><form id="claimPdfForm" data-row="${ri}">${field('file','Claim PDF','','file','required accept=".pdf,application/pdf"')}<p class="footnote">PDF only · Maximum 15 MB. A new upload replaces the PDF stored under this Claim ID, matching Desktop behaviour.</p><button class="new-btn full" type="submit">Upload claim PDF</button></form>`);return}
 if(a.startsWith('claim-pdf-view:')){let ri=Number(a.split(':')[1]),p=S.projects[projectIdx],sheet=S.projectGrids[p.name]?.[gridType]?.[gridMonth],col=sheet?.headers?.findIndex(h=>String(h).trim().toLowerCase()==='claim id'),claimId=sheet?.rows?.[ri]?.[col];if(!claimId){toast('This row has no Claim ID.',true);return}await viewClaimPdf(claimId);return}
 if(a==='documents'){await D.reloadDocs();docFilter='ALL';documents();return}
 if(a==='doc-tax'){await D.reloadDocs();docFilter='LHDN';documents();return}
 if(a==='upload-document'){docForm();return}
 if(a.startsWith('doc-filter:')){docFilter=a.slice(11);documents();return}
 if(a==='doc-categories'){docCategories();return}
 if(a.startsWith('doc-cat-rename:')){let k=a.slice(15),c=D.categories.find(x=>x.key===k);if(!c)return;let label=prompt('New category name',c.label);if(label?.trim()){await D.renameCategory(k,label.trim());toast('Category updated');docCategories()}return}
 if(a.startsWith('doc-edit:')){let x=D.docs.find(d=>String(d.id)===a.slice(9));if(x)docForm(x);return}
 if(a.startsWith('doc-view:')){await docView(a.slice(9));return}
 if(a.startsWith('doc-history:')){await docHistory(a.slice(12));return}
 if(a.startsWith('doc-history-view:')){await docView(a.slice(17),'company','Previous version');return}
 if(a==='doc-download'){await downloadCurrentFile();return}
 if(a.startsWith('doc-download:')){await downloadCurrentFile();return}
 if(a.startsWith('doc-delete:')){let x=D.docs.find(d=>String(d.id)===a.slice(11));if(!x||!confirm('Permanently delete this document and all server versions?'))return;await D.deleteDoc(x);toast('Document deleted');documents();return}
 if(a==='upload-worker-doc'){workerDocUpload();return}
 if(a.startsWith('view-worker-doc:')){await docView(a.slice(16),'worker');return}
 if(a.startsWith('delete-worker-doc:')){let k=(S.workers[wIndex].passport||S.workers[wIndex].name||'').trim().toUpperCase(),x=(D.workerDocsCache[k]||[]).find(d=>String(d.id)===a.slice(18));if(!x||!confirm('Delete this worker document?'))return;await D.deleteDoc(x,'worker');await workerProfile(wIndex);toast('Worker document deleted');return}
 if(a==='tax'){taxes();return}if(a.startsWith('tax:')){let k=a.split(':')[1];taxEdit(k==='new'?-1:Number(k));return}
 if(a.startsWith('tax-delete:')){let i=Number(a.slice(11));if(!confirm('Delete this record?'))return;S.taxRecords.splice(i,1);await saved('Tax record removed');taxes();return}
 if(a==='letters'){letters();return}if(a==='letter:new'){letterComposer();return}if(a.startsWith('letter-type:')){letterComposer(a.slice(12));return}if(a.startsWith('letter-history:')){letterHistoryView(Number(a.slice(15)));return}
 if(a.startsWith('letter-print:')){let x=S.letterHistory[Number(a.slice(13))];if(x?.pdfDataUri){window.open(x.pdfDataUri,'_blank','noopener');return}if(x)printLetter(x);return}
 if(a==='tools'){await D.reloadTools();toolPage();return}if(a==='tool:new'){toolForm();return}
 if(a.startsWith('tool-open:')){let x=D.tools[Number(a.slice(10))];openToolViewer(x);return}if(a==='tool-refresh'){let frame=$('#mobileToolFrame');if(frame&&currentToolUrl)frame.src=currentToolUrl;return}if(a==='tool-external'){if(currentToolUrl)location.href=currentToolUrl;return}if(a.startsWith('remote-file-view:')){let parts=a.split(':'),action=parts[1],id=parts[2],title=action.includes('leave_mc')?'Medical certificate':action.includes('attendance_proof')?'Attendance entry photo':'Worker document',filename=title+(action.includes('attendance_proof')?'.jpg':'.pdf');await viewRemoteWorkerFile(action,id,filename,title);return}
 if(a.startsWith('tool-delete:')){let x=D.tools[Number(a.slice(12))];if(!x||!confirm('Remove this tool?'))return;await D.deleteTool(x);toolPage();return}
 if(a==='vault'){await vaultPage();return}if(a==='vault-add'){vaultEdit(-1);return}if(a.startsWith('vault-edit:')){vaultEdit(Number(a.slice(11)));return}
 if(a.startsWith('vault-view:')){let x=vaultItems[Number(a.slice(11))];if(!x)return;let pin=prompt('Enter 6-digit master PIN to reveal password');if(!pin)return;let d=await D.vault('reveal',{id:x.id,pin});show('Secure credential',`<div class="project-banner"><b>${esc(x.title)}</b><small>Username: ${esc(x.username||'—')}</small></div>${field('password','Password',d.password,'text','readonly')}<p class="footnote">Keep this credential private. It is not saved to the app’s local database.</p>`);return}
 if(a.startsWith('vault-delete:')){let x=vaultItems[Number(a.slice(13))];if(!x||!confirm('Delete this encrypted credential from server?'))return;await D.vault('delete',{id:x.id});await vaultPage();return}
 if(a==='backup-export'){toast('Preparing backup including offline attachments…');const data=await D.exportData();download(new Blob([data],{type:'application/json'}),'SAYMA-backup-'+today()+'.json');toast('Backup downloaded');return}
 if(a==='backup-import'){$('#backupInput').click();return}
 }catch(e){console.error(e);toast(e.message||'Operation failed',true)}}
function download(v,name){if(v instanceof Blob&&window.SaymaNative&&typeof window.SaymaNative.postMessage==='function'){blobBase64(v).then(data=>window.SaymaNative.postMessage(JSON.stringify({action:'saveBase64File',data,filename:fileSafeName(name),mimeType:v.type||'application/octet-stream'}))).catch(e=>toast(e.message||'Could not save file',true));return}let url=typeof v==='string'?v:URL.createObjectURL(v),a=document.createElement('a');a.href=url;a.download=name;a.click();if(typeof v!=='string')setTimeout(()=>URL.revokeObjectURL(url),4000)}
async function formSave(f){const id=f.id,form=Object.fromEntries(new FormData(f).entries());try{
 if(id==='workerGpsLocationForm'){
  if(!D.online)throw Error('Admin server login required.');
  const payload={site_name:String(form.site_name||'').trim(),latitude:Number(form.latitude),longitude:Number(form.longitude),radius_m:Number(form.radius_m),enabled:form.enabled==='1'?1:0};
  if(!payload.site_name||!Number.isFinite(payload.latitude)||!Number.isFinite(payload.longitude)||!Number.isInteger(payload.radius_m)||payload.radius_m<25||payload.radius_m>2000)throw Error('Enter a valid site name, GPS coordinates and radius between 25 and 2,000 metres.');
  await D.request('admin_save_worker_attendance_location',payload);gpsAdminEditingLocation=null;toast('Approved site location saved.');await workerGpsAdminPage('locations');return
 }
 if(id==='workerGpsAttendanceFilterForm'){gpsAdminDate=form.date||today();gpsAdminSite=form.site_name||'';await workerGpsAdminPage('attendance');return}
 if(id==='workerGpsLeaveFilterForm'){gpsAdminLeaveStatus=form.status??'pending';await workerGpsAdminPage('leave');return}
 if(id==='bankDetailsForm'){await D.request('admin_save_worker_bank_account',{request_id:f.dataset.id,account_name:form.account_name,account_number:form.account_number});toast('Bank account details saved and published to the worker portal');await bankAccountsPage();return}
 if(id==='workerForm'){let i=Number(f.dataset.index),old=S.workers[i],pass=String(form.passport||'').trim();if(S.workers.some((x,k)=>k!==i&&x.passport===pass))throw Error('Passport already exists');form.site=String(form.site||'Unassigned').trim()||'Unassigned';if(form.site!=='Unassigned'&&!(S.projects||[]).some(p=>String(p.name)===form.site))throw Error('Select a valid project/site from the Projects list.');form.rate=Number(form.rate||0);form.remind=form.remind!=='NO';if(i<0){S.workers.push({...form,payments:{}})}else{if(old.passport!==pass){if(S.permitPayments[old.passport]){S.permitPayments[pass]=S.permitPayments[old.passport];delete S.permitPayments[old.passport]}}Object.assign(old,form)}await saved('Worker saved');close();return}
 if(id==='projectForm'){let i=Number(f.dataset.index),old=S.projects[i];form.name=String(form.name||'').trim();if(S.projects.some((x,k)=>k!==i&&x.name.toLowerCase()===form.name.toLowerCase()))throw Error('Project already exists');if(i<0){S.projects.push(form);S.projectGrids[form.name]={claims:{},salary:{}};}else{if(old.name!==form.name){if(D.online)await D.request('rename_project',{old_project_name:old.name,new_project_name:form.name});S.projectGrids[form.name]=S.projectGrids[old.name]||{claims:{},salary:{}};delete S.projectGrids[old.name];S.workers.forEach(w=>{if(w.site===old.name)w.site=form.name});for(const k of Object.keys(S.attendance||{})){if(k.startsWith(old.name+'||')){S.attendance[k.replace(old.name+'||',form.name+'||')]=S.attendance[k];delete S.attendance[k]}}}Object.assign(old,form)}await saved('Project saved');close();return}
 if(id==='projectClaimTotalForm'||id==='projectSalaryTotalForm'){
  if(!D.online)throw Error('Connect to the company server before saving project financial totals.');
  const p=S.projects[projectIdx],type=f.dataset.type,month=f.dataset.month||projectFinanceMonth,amount=Number(form.total_amount);
  if(!p||!['claims','salary'].includes(type)||!Number.isFinite(amount)||amount<0)throw Error('Enter a valid non-negative amount.');
  const sheet=ensureProjectFinanceSheet(p.name,type,month);sheet.summaryTotal=amount;
  await D.projectSave(p.name,type,month);await D.save();toast((type==='claims'?'Claim':'Salary')+' total saved to database');await projectDetail(projectIdx,month);return;
 }
 if(id==='projectDocumentForm'){
  if(!D.online)throw Error('Connect to the company server before uploading project documents.');
  const p=S.projects[projectIdx],file=f.querySelector('input[type=file]')?.files?.[0],type=f.dataset.type,month=f.dataset.month||projectFinanceMonth;
  if(!p||!file)throw Error('Choose a file to upload.');
  if(file.size>20*1024*1024)throw Error('File exceeds the 20 MB upload limit.');
  const fd=new FormData();fd.append('project',p.name);fd.append('month',month);fd.append('document_type',type);fd.append('file',file);
  await D.request('upload_project_document',fd);toast('Project document uploaded and saved');await projectDetail(projectIdx,month);return;
 }
 if(id==='gridForm'){gridRead();let p=S.projects[projectIdx];await D.projectSave(p.name,gridType,gridMonth);await saved('Financial sheet saved');openGrid(gridType,gridMonth);return}
 if(id==='monthForm'){let type=f.dataset.type,p=S.projects[projectIdx],month=form.month;projectFinanceMonth=month;S.projectGrids[p.name]||={claims:{},salary:{}};S.projectGrids[p.name][type]||={};if(S.projectGrids[p.name][type][month])throw Error('Month already exists');let old=Object.values(S.projectGrids[p.name][type])[0];S.projectGrids[p.name][type][month]={headers:old?.headers|| (type==='salary'?S.salaryHeaders:['Item No','Description of Claim','Unit','Qty','Rate (MYR)','Total (MYR)']),rows:[]};await saved('Month created');openGrid(type,month);return}
 if(id==='attendanceForm'){let p=S.projects[attendanceProject],key=`${p.name}||${attendanceMonth}||${attendanceDate}`;S.attendance[key]||={};for(let i=0;i<Number(form.count);i++){let workerKey=form['key_'+i];S.attendance[key][workerKey]={name:form['name_'+i],passport:workerKey,status:form['status_'+i],start:form['start_'+i],close:form['close_'+i],ot:form['ot_'+i],remarks:form['remarks_'+i]}}await saved('Attendance saved');makeAttendance();return}
 if(id==='targetForm'){let i=Number(f.dataset.index),w=S.workers[i];S.permitPayments[w.passport]||={};let rec=S.permitPayments[w.passport][selectedYear]||={target:0,paid:0,entries:[],costItems:[]};rec.target=Math.max(0,Number(form.target||0));rec.costItems=[{id:'legacy-base',part:'Existing total (previously saved)',amount:rec.target,date:'',legacy:true}];await saved('Permit target updated');permit(i);return}
 if(id==='paymentForm'){let i=Number(f.dataset.index),w=S.workers[i];S.permitPayments[w.passport]||={};let x=S.permitPayments[w.passport][selectedYear]||={target:0,paid:0,entries:[]};let v=Number(form.amount);if(!Number.isFinite(v)||v<=0)throw Error('Enter a valid amount');x.entries||=[];x.entries.push({category:form.category,amount:v,note:form.note,date:form.date});x.paid=Number(x.paid||0)+v;await saved('Payment recorded');permit(i);return}
 if(id==='permitCategoryForm'){let label=String(form.category).trim();if(S.permitCategories.some(x=>x.toLowerCase()===label.toLowerCase()))throw Error('Category exists');S.permitCategories.push(label);await saved('Category added');categories();return}
 if(id==='docForm'){let file=f.querySelector('input[type=file]')?.files[0],id=f.dataset.id,x=D.docs.find(d=>String(d.id)===id);if(!x&&!file)throw Error('Choose a document file');if(x)await D.editDoc(x,form,file);else await D.uploadDoc(form,file);toast(x?'Document updated':'Document uploaded');await D.reloadDocs();documents();return}
 if(id==='paymentReceiptForm'){let i=Number(f.dataset.index),w=S.workers[i],file=f.querySelector('input[type=file]')?.files[0];if(!w||!file)throw Error('Choose a receipt file');await D.uploadDoc({doc_type:'Payment Receipt '+selectedYear},file,'worker',w);toast('Payment receipt uploaded to server');permit(i);return}
 if(id==='claimPdfForm'){let ri=Number(f.dataset.row),p=S.projects[projectIdx],sheet=S.projectGrids[p.name]?.[gridType]?.[gridMonth],col=sheet?.headers?.findIndex(h=>String(h).trim().toLowerCase()==='claim id'),claimId=sheet?.rows?.[ri]?.[col],file=f.querySelector('input[type=file]')?.files[0];if(!claimId||!file)throw Error('Claim ID and PDF file are required');await D.uploadClaimPdf(p.name,gridMonth,claimId,file);toast('Claim PDF uploaded to server');openGrid(gridType,gridMonth);return}
 if(id==='workerDocForm'){let file=f.querySelector('input[type=file]')?.files[0];await D.uploadDoc(form,file,'worker',S.workers[wIndex]);await workerProfile(wIndex);toast('Worker document uploaded');return}
 if(id==='docCategoryForm'){await D.addCategory(form.label.trim());toast('Category added');docCategories();return}
 if(id==='taxForm'){let i=Number(f.dataset.index);form.year=String(form.year);form.taxAmount=Number(form.taxAmount||0);form.revenue=Number(form.revenue||0);if(i<0)S.taxRecords.push(form);else Object.assign(S.taxRecords[i],form);await saved('Tax record saved');taxes();return}
 if(id==='letterForm'){let template=S.templates[form.template]||{},values={};for(const field of template.fields||[])values[field.key]=form['field_'+field.key]||'';let resolved=(form.body||'').replace(/\{\{\s*([a-zA-Z0-9_]+)\s*\}\}/g,(_,k)=>values[k]||'');let d=form.date.split('-');let key=[d[2],d[1],d[0]?.slice(-2)].join('-');let serial=Number(S.letterSerialCounters[key]||0)+1;S.letterSerialCounters[key]=serial;let referenceNo=key+'-'+serial;let x={id:uid(),referenceNo,issueDate:form.date,recipientName:form.to,recipientCompany:form.company,subject:form.subject,body:resolved,templateKey:form.template,templateName:template.name||form.template,createdAt:new Date().toISOString()};S.letterHistory.unshift(x);S.letterVerificationRecords.push({referenceNo,issueDate:x.issueDate,subject:x.subject,recipientName:x.recipientName,companyName:x.recipientCompany,status:'ACTIVE',createdAt:x.createdAt});await saved('Letter saved');let i=S.letterHistory.indexOf(x);show('Letter generated',card(`<div class="project-banner"><b>${esc(x.subject)}</b><small>${esc(referenceNo)} · ${esc(x.issueDate)}</small></div><p class="letter-display">${esc(x.body)}</p>${btn('Print / Save as PDF','letter-print:'+i,'full')}`));return}
 if(id==='toolForm'){if(!/^https:\/\//i.test(form.url))throw Error('Only HTTPS links are allowed');await D.addTool(form);toast('Tool added');toolPage();return}
 if(id==='localProfileForm'){adminProfile={name:form.name,mobile:form.mobile,email:form.email,photo:adminProfile.photo||''};localStorage.setItem('sayma-profile-v5',JSON.stringify(adminProfile));toast('Display profile saved on device');render();profilePage();return}
 if(id==='profileForm'){await D.extraRequest('profile.php','save',{name:form.name,mobile:form.mobile,email:form.email,photo:adminProfile.photo||''});toast('Admin profile updated');await profilePage();render();return}
 if(id==='passwordForm'){if(form.new!==form.repeat)throw Error('New passwords do not match');await D.extraRequest('profile.php','change_password',{current:form.current,new:form.new});toast('Password changed');await profilePage();return}
 if(id==='reminderForm'){let old=await D.extraRequest('reminders.php','get'),s=old.settings||{};s.enabled=form.enabled==='YES';s.daysBefore=String(form.daysBefore||'').split(',').map(x=>Number(x.trim())).filter(x=>Number.isInteger(x)&&x>0&&x<=365);s.docs||={};s.docs.permitExpiry=form.permitExpiry==='YES';s.docs.cidbDate=form.cidbDate==='YES';s.template_text=form.template_text;s.email_subject=form.email_subject;await D.extraRequest('reminders.php','save',{settings_json:JSON.stringify(s)});toast('Reminder preferences saved');await remindersPage();return}
 if(id==='settingsForm'){D.changeConfig({apiPath:form.apiPath.trim(),mode:form.mode});let ok=await D.connect();render();settings();toast(ok?'Connected successfully':'Saved settings. API not connected.',!ok);return}
 if(id==='vaultSetup'){if(form.master_password!==form.confirm)throw Error('Passwords do not match');await D.vault('setup',{master_password:form.master_password});toast('Master password created');await vaultPage();return}
 if(id==='vaultPin'){if(!/^\d{6}$/.test(form.pin))throw Error('Enter six digits');await D.vault('set_pin',{master_password:form.master_password,pin:form.pin});toast('PIN saved');await vaultPage();return}
 if(id==='vaultForm'){let i=Number(f.dataset.index);let data={...form,id:i<0?'':vaultItems[i].id};await D.vault('save',data);toast('Credential saved securely');await vaultPage();return}
 }catch(e){console.error(e);toast(e.message||'Save failed',true)}}
document.addEventListener('click',e=>{
 let el=e.target.closest('[data-tab],[data-act],button');if(!el||el.disabled)return;
 // A quiet synthetic tap plays only after the person's own click, never automatically.
 M.tapSound();M.ripple(e,el);
 if(el.dataset.tab){e.preventDefault();switchTab(el.dataset.tab);return}
 if(el.dataset.act){e.preventDefault();handleAction(el.dataset.act)}
});
document.addEventListener('submit',e=>{if(e.target&&e.target.id==='adminWorkerSalarySlipForm'){e.preventDefault();return;}if(e.target.closest('form')){e.preventDefault();formSave(e.target)}});
document.addEventListener('change',async e=>{let v=e.target;if(v.id==='adminSalarySlipFile'){analyseAdminSalarySlip(v.files?.[0]);return}if(v.id==='adminPhotoInput'){const file=v.files?.[0];if(!file)return;try{adminProfile.photo=await compressAdminPhoto(file);updateAdminPhotoPreview();toast('Photo ready. Press Save profile to store it in MySQL')}catch(err){toast(err.message||'Photo upload failed',true)}v.value='';return}if(v.id==='projectFinanceMonth'){projectFinanceMonth=v.value;await projectDetail(projectIdx,projectFinanceMonth)}if(v.id==='attendanceProject'){attendanceProject=Number(v.value);makeAttendance()}if(v.id==='attendanceMonth'){attendanceMonth=v.value;makeAttendance()}if(v.id==='attendanceDate'){attendanceDate=v.value;makeAttendance()}if(v.id==='templateSelect'){letterComposer(v.value)}});
document.addEventListener('input',e=>{let v=e.target;if(v.id==='workerSearch'){workerSearch=v.value;let pos=v.selectionStart;render();let input=$('#workerSearch');input?.focus();input?.setSelectionRange(pos,pos)}if(v.id==='globalSearch'){let el=$('#globalResults');if(el){el.hidden=!v.value.trim();el.innerHTML=dashboardSearchResults(v.value)} }if(v.id==='docSearch'){docSearch=v.value;let pos=v.selectionStart;documents();let input=$('#docSearch');input?.focus();input?.setSelectionRange(pos,pos)}});
document.addEventListener('keydown',e=>{if((e.ctrlKey||e.metaKey)&&e.key.toLowerCase()==='k'){e.preventDefault();if(tab!=='Home')switchTab('Home').then(()=>$('#globalSearch')?.focus());else $('#globalSearch')?.focus();}});
$('#modalRoot').addEventListener('click',e=>{if(e.target.id==='overlay')close()});
const backup=document.createElement('input');backup.type='file';backup.accept='.json,application/json';backup.id='backupInput';backup.hidden=true;document.body.appendChild(backup);
backup.addEventListener('change',async e=>{let f=e.target.files[0];if(!f)return;try{const d=JSON.parse(await f.text());if(!confirm('Replace ALL local SAYMA data with this backup? Connected server records will not be overwritten automatically.'))return;await D.importData(d);toast('Backup imported');render();settings()}catch(err){toast('Import failed: '+err.message,true)}e.target.value=''});
window.addEventListener('sayma:connection',()=>{if(!modalType)render()});
window.addEventListener('sayma:state',()=>{if(!modalType)render()});
render();M.boot();if(location.protocol!=='file:'&&D.config.mode!=='offline'){D.connect().then(async ok=>{if(!ok&&typeof D.restoreOffline==='function'){try{await D.restoreOffline()}catch(e){console.warn('Offline snapshot restore failed',e)}}if(ok){const years=S.permitYears||[];if(years.length&&!years.includes(selectedYear)){selectedYear=years.find(y=>(S.workers||[]).some(w=>Number(S.permitPayments?.[w.passport]?.[y]?.paid||0)>0))||years[years.length-1]}try{let r=await D.extraRequest('profile.php','get');adminProfile=r.profile||adminProfile}catch(e){console.info('Admin display profile unavailable:',e.message)}}render();if(!ok&&D.error.includes('login'))toast('Login to your existing portal to enable database sync')}).catch(()=>render())}
})();
