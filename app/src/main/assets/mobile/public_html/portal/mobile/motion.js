/* SAYMA Mobile V4 • professional motion system.
   Calm transitions, branded loading screen and subtle tap feedback.
   No external libraries, downloads, trackers or audio files. */
(()=>{'use strict';
const prefsKey='sayma-experience-v4';
let prefs={sound:true,motion:true},audio=null,navToken=0,loaderTimer=0,loader=null;
try{
  const old=JSON.parse(localStorage.getItem('sayma-experience-v3')||'{}');
  const val=JSON.parse(localStorage.getItem(prefsKey)||'{}');
  const src=Object.keys(val).length?val:old;
  if(typeof src.sound==='boolean')prefs.sound=src.sound;
  if(typeof src.motion==='boolean')prefs.motion=src.motion;
}catch(_){}
const reduced=()=>!!(window.matchMedia&&window.matchMedia('(prefers-reduced-motion: reduce)').matches);
const motionOn=()=>prefs.motion&&!reduced();
const delay=t=>new Promise(resolve=>setTimeout(resolve,t));
function store(){try{localStorage.setItem(prefsKey,JSON.stringify(prefs))}catch(_){}}

function tapSound(){
 if(!prefs.sound)return;
 try{
  const AC=window.AudioContext||window.webkitAudioContext;if(!AC)return;
  audio ||= new AC();if(audio.state==='suspended')audio.resume().catch(()=>{});
  const t=audio.currentTime;
  const gain=audio.createGain();
  const hi=audio.createOscillator();
  const low=audio.createOscillator();
  hi.type='sine';low.type='sine';
  hi.frequency.setValueAtTime(760,t);hi.frequency.exponentialRampToValueAtTime(590,t+.028);
  low.frequency.setValueAtTime(255,t);low.frequency.exponentialRampToValueAtTime(205,t+.035);
  gain.gain.setValueAtTime(.0001,t);gain.gain.exponentialRampToValueAtTime(.0105,t+.004);gain.gain.exponentialRampToValueAtTime(.0001,t+.052);
  hi.connect(gain);low.connect(gain);gain.connect(audio.destination);
  hi.start(t);low.start(t);hi.stop(t+.055);low.stop(t+.045);
  hi.onended=()=>{try{hi.disconnect();low.disconnect();gain.disconnect()}catch(_){}};
 }catch(_){}
}

function ripple(e,el){
 if(!motionOn()||!el||el.disabled)return;
 const allowed=el.matches('.nav-item,.new-btn,.more-tile,.new-line[data-act],.filter-chip,.status-button,.close,.experience-toggle');
 if(!allowed)return;
 const box=el.getBoundingClientRect();if(box.width<1||box.height<1)return;
 const x=(typeof e.clientX==='number'&&e.clientX?e.clientX:box.left+box.width/2)-box.left;
 const y=(typeof e.clientY==='number'&&e.clientY?e.clientY:box.top+box.height/2)-box.top;
 const s=document.createElement('span');s.className='tap-ripple';s.style.left=x+'px';s.style.top=y+'px';
 el.classList.add('tap-surface');el.appendChild(s);
 s.addEventListener('animationend',()=>s.remove(),{once:true});setTimeout(()=>s.remove(),520);
}

function loaderElement(){
 if(loader?.isConnected)return loader;
 loader=document.createElement('div');loader.id='routeLoader';loader.className='route-loader';
 loader.setAttribute('role','status');loader.setAttribute('aria-live','polite');loader.setAttribute('aria-label','Loading screen');
 loader.innerHTML=`
   <div class="loader-grid" aria-hidden="true"></div>
   <div class="loader-glow loader-glow-a" aria-hidden="true"></div>
   <div class="loader-glow loader-glow-b" aria-hidden="true"></div>
   <div class="loader-stage">
     <div class="loader-mark" aria-hidden="true">
       <span class="loader-mark-frame"></span>
       <img src="./assets/sayma-logo.png" alt="" class="loader-mark-logo" />
       <span class="loader-mark-scan"></span>
     </div>
     <div class="loading-brand">SAYMA <span>BUILDERS</span></div>
     <div class="loading-kicker">CONSTRUCTION MANAGEMENT</div>
     <div class="loading-title" id="loadingTitle">Preparing workspace</div>
     <div class="loading-progress" aria-hidden="true"><i></i></div>
     <div class="loading-foot"><span>SECURE WORKSPACE</span><b>●</b><span>SYNC READY</span></div>
   </div>`;
 (document.querySelector('#appShell')||document.body).appendChild(loader);
 return loader;
}

function begin(label='Opening workspace',boot=false){
 if(!motionOn())return null;
 clearTimeout(loaderTimer);const t=++navToken,el=loaderElement();
 el.querySelector('#loadingTitle').textContent=label;
 el.classList.toggle('is-boot',!!boot);
 el.classList.remove('is-hidden');
 requestAnimationFrame(()=>requestAnimationFrame(()=>el.classList.add('is-visible')));
 return t;
}

function finish(t){
 if(!t||t!==navToken||!loader)return;
 const current=loader;
 current.classList.remove('is-visible');current.classList.add('is-hidden');
 loaderTimer=setTimeout(()=>{
   if(current===loader&&current.classList.contains('is-hidden')){current.remove();loader=null}
 },430);
}

function prepExit(){
 if(!motionOn())return;
 const el=document.querySelector('#app');if(!el)return;
 el.classList.remove('screen-enter');el.classList.add('screen-exit');
}

function animateMain(){
 const el=document.querySelector('#app');if(!el)return;
 if(!motionOn()){
   el.getAnimations?.().forEach(a=>a.cancel());
   el.classList.remove('screen-exit','screen-enter');
   return;
 }
 // A short transform/opacity-only animation stays on the compositor.
 // No blur, large stagger, artificial loading delay or layout animation.
 try{
   el.getAnimations?.().forEach(a=>a.cancel());
   if(typeof el.animate==='function'){
     el.animate(
       [
         {opacity:.94,transform:'translate3d(0,7px,0)'},
         {opacity:1,transform:'translate3d(0,0,0)'}
       ],
       {duration:190,easing:'cubic-bezier(.22,.61,.36,1)',fill:'both'}
     );
   }else{
     el.classList.remove('screen-exit','screen-enter');
     void el.offsetWidth;
     el.classList.add('screen-enter');
     setTimeout(()=>el.classList.remove('screen-enter'),220);
   }
 }catch(_){}
}

function animateSheet(){
 if(!motionOn())return;
 const el=document.querySelector('#overlay');if(!el)return;
 el.classList.remove('sheet-exit');el.classList.add('sheet-enter');
 setTimeout(()=>el.classList.remove('sheet-enter'),520);
}
function exitSheet(overlay){
 if(!overlay||!motionOn())return false;
 overlay.classList.remove('sheet-enter');overlay.classList.add('sheet-exit');
 setTimeout(()=>overlay.remove(),245);return true;
}

async function route(label,callback,{minDuration=360,screen=false}={}){
 if(!motionOn()){await callback();return;}
 if(screen)prepExit();
 await delay(72);
 const t=begin(label,false),start=performance.now();
 try{
   await delay(120);
   await callback();
   const rest=Math.max(0,Math.min(520,minDuration-(performance.now()-start)));
   if(rest)await delay(rest);
 }finally{
   finish(t);
 }
}

function boot(){
 if(!motionOn())return;
 const t=begin('Preparing your workspace',true);
 setTimeout(()=>finish(t),880);
}

window.SAYMA_MOTION={
 get sound(){return prefs.sound},get motion(){return prefs.motion},get isReduced(){return reduced()},get enabled(){return motionOn()},
 toggleSound(){prefs.sound=!prefs.sound;store()},
 toggleMotion(){prefs.motion=!prefs.motion;store();document.documentElement.classList.toggle('motion-off',!motionOn());if(!motionOn())finish(navToken)},
 tapSound,ripple,route,animateMain,animateSheet,exitSheet,boot
};
document.documentElement.classList.toggle('motion-off',!motionOn());
})();
