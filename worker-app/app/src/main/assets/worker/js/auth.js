(()=>{
'use strict';
const API='/portal/api.php';
const $=id=>document.getElementById(id);
function show(id){document.querySelectorAll('.view').forEach(v=>v.classList.remove('active'));const el=$(id);if(el)el.classList.add('active');window.scrollTo({top:0,behavior:'smooth'});}
function message(id,text,type='info'){const el=$(id);if(!el)return;el.textContent=text;el.className='message show '+type;}
function clearMessage(id){const el=$(id);if(el){el.className='message';el.textContent='';}}
function busy(btn,on,label){if(!btn)return;if(on){btn.dataset.old=btn.textContent;btn.disabled=true;btn.textContent='Please wait…';}else{btn.disabled=false;btn.textContent=label||btn.dataset.old||'Continue';}}
async function api(action,data={},method='POST'){
  const opts={method,credentials:'same-origin'};
  if(method==='GET'){
    const qs=new URLSearchParams({action,...data});
    const res=await fetch(API+'?'+qs.toString(),opts);
    return await res.json().catch(()=>({ok:false,error:'Invalid server response.'}));
  }
  if(data instanceof FormData){data.set('action',action);opts.body=data;}
  else{const body=new URLSearchParams();body.set('action',action);Object.entries(data||{}).forEach(([k,v])=>body.set(k,String(v)));opts.body=body;}
  const res=await fetch(API,opts);
  return await res.json().catch(()=>({ok:false,error:'Server returned an invalid response.'}));
}
function resetLogin(){
  $('workerIdentifierForm').classList.remove('hidden');$('workerPasswordForm').classList.add('hidden');
  $('loginStep1').classList.add('on');$('loginStep2').classList.remove('on');
  $('workerIdentifier').value='';$('workerPassword').value='';clearMessage('workerLoginMsg');
}
$('workerIdentifierForm').addEventListener('submit',e=>{
  e.preventDefault();const identifier=$('workerIdentifier').value.trim();
  if(!identifier){message('workerLoginMsg','Enter your email address or mobile number.','error');return;}
  $('identifierPreview').textContent=identifier;$('workerIdentifierForm').classList.add('hidden');$('workerPasswordForm').classList.remove('hidden');$('loginStep2').classList.add('on');$('workerPassword').focus();
});
$('changeIdentifier').addEventListener('click',resetLogin);
$('workerPasswordForm').addEventListener('submit',async e=>{
  e.preventDefault();clearMessage('workerLoginMsg');const btn=$('workerLoginBtn');busy(btn,true);
  const r=await api('worker_login',{identifier:$('workerIdentifier').value.trim(),password:$('workerPassword').value});busy(btn,false,'Sign In');
  if(!r.ok){message('workerLoginMsg',r.error||'Sign in failed.','error');return;}
  if(r.must_set_password){if(window.SAYMAWorkerOffline)await window.SAYMAWorkerOffline.clear();message('setPasswordMsg',r.message||'Create your own password to continue.','info');show('workerSetPasswordView');return;}
  if(window.SAYMAWorkerOffline)await window.SAYMAWorkerOffline.clear();
  location.replace('./index.html');
});
$('openRegister').addEventListener('click',()=>{clearMessage('registerMsg');show('workerRegisterView');});
$('backToWorkerLogin').addEventListener('click',()=>{resetLogin();show('workerLoginView');});
$('workerRegisterForm').addEventListener('submit',async e=>{
  e.preventDefault();clearMessage('registerMsg');const p=$('regPassword').value;
  if(p.length<10){message('registerMsg','Use at least 10 characters for your password.','error');return;}
  if(p!==$('regPassword2').value){message('registerMsg','Passwords do not match.','error');return;}
  const btn=$('registerBtn');busy(btn,true);
  const r=await api('worker_register',{name:$('regName').value.trim(),passport:$('regPassport').value.trim(),email:$('regEmail').value.trim(),mobile:$('regMobile').value.trim(),password:p});busy(btn,false,'Submit registration');
  if(!r.ok){message('registerMsg',r.error||'Registration failed.','error');return;}
  $('workerRegisterForm').reset();message('registerMsg',r.message||'Registration submitted. Please wait for Admin approval.','ok');
});
$('workerSetPasswordForm').addEventListener('submit',async e=>{
  e.preventDefault();clearMessage('setPasswordMsg');const p=$('newWorkerPassword').value;
  if(p.length<10){message('setPasswordMsg','Use at least 10 characters.','error');return;}
  if(p!==$('newWorkerPassword2').value){message('setPasswordMsg','Passwords do not match.','error');return;}
  const btn=$('setPasswordBtn');busy(btn,true);
  const r=await api('worker_set_password',{password:p});busy(btn,false,'Save password & continue');
  if(!r.ok){message('setPasswordMsg',r.error||'Could not save password.','error');return;}
  if(window.SAYMAWorkerOffline)await window.SAYMAWorkerOffline.clear();
  location.replace('./index.html');
});
$('logoutFromPasswordSetup').addEventListener('click',async()=>{await api('worker_logout');if(window.SAYMAWorkerOffline)await window.SAYMAWorkerOffline.clear();resetLogin();show('workerLoginView');});
if('serviceWorker' in navigator && location.protocol!=='file:'){navigator.serviceWorker.register('./sw.js').catch(()=>{});}
async function init(){
  const params=new URLSearchParams(location.search);
  const r=await api('worker_session',{},'GET');
  if(r.ok){
    if(r.must_set_password){message('setPasswordMsg','Create your own password to finish first-time access.','info');show('workerSetPasswordView');}
    else location.replace('./index.html');
    return;
  }
  show('workerLoginView');
  if(params.get('registered')==='1')message('workerLoginMsg','Registration received. Wait for Admin approval before signing in.','info');
}
init();
})();
