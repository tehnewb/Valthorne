import {chromium} from '@playwright/test';
import assert from 'node:assert/strict';
import {writeFile} from 'node:fs/promises';
import {browserTestOptions} from './browser-test-options.mjs';
const browser=await chromium.launch(browserTestOptions);
try{
 const page=await browser.newPage({viewport:{width:1280,height:800}}),errors=[];
 page.setDefaultTimeout(60000);
 page.on('pageerror',error=>errors.push(String(error)));
 const arenaUrl=new URL(process.env.TEST_URL||'http://127.0.0.1:8095');arenaUrl.searchParams.set('scene','arena');
 await page.goto(arenaUrl.href);
 await page.waitForFunction(()=>globalThis.valthorneReady||globalThis.valthorneError);
 assert.equal(await page.evaluate(()=>globalThis.valthorneError),undefined);
 await page.waitForFunction(()=>valthorneHost.assets.models.size===1);
 await page.waitForFunction(()=>valthorneHost.media.items.size===2&&valthorneHost.media.pending.size===0);
 const mediaBytes=await page.evaluate(()=>valthorneHost.media.bytes);assert(mediaBytes>0&&mediaBytes<65536);
 await page.waitForTimeout(500);
 const inputState=()=>page.evaluate(()=>({
  steps:valthorneHost.steps,z:valthorneHost.firstPerson?.z,keys:[...valthorneHost.platform.keys],
  captured:document.pointerLockElement===document.querySelector('#scene'),playing:document.body.classList.contains('playing'),
  focused:document.hasFocus(),activeElement:document.activeElement?.tagName,hidden:document.hidden,
  captureError:valthorneHost.platform.captureError??null,engineError:globalThis.valthorneError??null
 }));
 let movement;
 try{
  await page.getByRole('button',{name:'Enter arena'}).click();
  // The browser grants capture before dispatching pointerlockchange. Its platform
  // listener clears held input, then marks the page as playing; wait for that reset.
  await page.waitForFunction(()=>document.pointerLockElement===document.querySelector('#scene')&&document.body.classList.contains('playing'));
  await page.keyboard.down('w');
  try{
   await page.waitForFunction(()=>valthorneHost.platform.keys.has('KeyW'));
   const start=await inputState();
   // Measure from acknowledged input, not key dispatch or wall time. Sampling the
   // endpoint in the condition also excludes time spent sending the release event.
   const endpoint=await page.waitForFunction(start=>{
    const h=valthorneHost;
    if(globalThis.valthorneError)throw new Error(globalThis.valthorneError);
    if(!h.platform.keys.has('KeyW'))throw new Error('Movement input was cleared before 36 physics steps');
    return h.steps>=start.steps+36?{steps:h.steps,z:h.firstPerson.z}:false;
   },start);
   try{movement={start,end:await endpoint.jsonValue()};}finally{await endpoint.dispose();}
  }finally{await page.keyboard.up('w');}
 }catch(error){
  const state=await inputState();
  await writeFile('build/arena-input-failure.json',JSON.stringify({error:String(error),state,errors},null,2));
  throw new Error(`Arena input failed: ${error.message}; state=${JSON.stringify(state)}`,{cause:error});
 }
 const moved=movement.start.z-movement.end.z;assert(moved>1.5&&moved<3.5,`Movement after 36 physics steps: ${moved}; ${JSON.stringify(movement)}`);
 async function fire(){
  const shots=await page.evaluate(()=>valthorneHost.arenaShots);
  await page.mouse.down();
  try{await page.waitForFunction(before=>valthorneHost.arenaShots>before,shots);}
  finally{await page.mouse.up();}
 }
 await page.mouse.move(650,400);await fire();
 await page.waitForFunction(()=>valthorneHost.particles.items.length>0);
 const effects=await page.evaluate(()=>({particles:valthorneHost.particles.items.length,lights:valthorneHost.particles.lightCount,physics:valthorneHost.particles.items.filter(p=>p.object).length}));
 assert.equal(effects.particles,10);assert.equal(effects.lights,8);assert.equal(effects.physics,10);
 assert.equal(await page.evaluate(()=>valthorneHost.arenaAmmo),23);
 await page.keyboard.down('r');
 try{await page.waitForFunction(()=>valthorneHost.arenaAmmo===24);}
 finally{await page.keyboard.up('r');}
 await page.waitForFunction(()=>valthorneHost.particles.items.length===0);
 assert.equal(await page.evaluate(()=>valthorneHost.particles.lightCount),0);
 // Drive the same look accumulator that receives pointer-lock deltas, then shoot a real target.
 await page.evaluate(()=>{
     const h=valthorneHost,target=h.objects[7].body.GetPosition(),camera=h.firstPerson;
     const desired=Math.atan2(target.GetX()-camera.x,camera.z-target.GetZ());
     h.platform.look[0]=(desired-camera.yaw)/.0022;
     const elevation=Math.atan2(target.GetY()-camera.y,Math.hypot(target.GetX()-camera.x,target.GetZ()-camera.z));
     h.platform.look[1]=(camera.pitch-elevation)/.0022;
 });
 await page.waitForFunction(()=>valthorneHost.platform.look.every(value=>value===0));
 await fire();
 await page.waitForFunction(()=>valthorneHost.arenaHits>0);
 await page.screenshot({path:'build/web-arena.png'});
 await page.evaluate(()=>{for(let i=0;i<10;i++)valthorneHost.particles.burst(0,2,0,0xff6655,32,false,true);});
 assert.equal(await page.evaluate(()=>valthorneHost.particles.items.length),64);
 await page.evaluate(()=>document.exitPointerLock());
 await page.getByRole('button',{name:'Rebuild stack'}).click();
 await page.waitForFunction(()=>valthorneHost.assets.models.size===1&&valthorneHost.particles.items.length===0);
 assert.equal(await page.evaluate(()=>valthorneHost.objects.length),14);
 const times=await page.evaluate(async()=>{const result=[];let last=performance.now();for(let i=0;i<180;i++){await new Promise(requestAnimationFrame);const now=performance.now();result.push(now-last);last=now;}return result.sort((a,b)=>a-b);});
 const result={moved,effects,mediaBytes,meanFrameIntervalMs:times.reduce((a,b)=>a+b)/times.length,p95FrameIntervalMs:times[Math.floor(times.length*.95)]};
 const mediaFailures=await page.evaluate(async()=>{
   const media=valthorneHost.media,failures=[];
   const id=media.load('ui/crosshair.png',false,()=>{throw new Error('Cancelled media loaded');},reason=>failures.push(reason));media.cancel(id);media.cancel(id);
   const soundId=await new Promise((resolve,reject)=>media.load('audio/impact.wav',true,resolve,reject));
   const sound=media.get(soundId);const played=media.play(soundId,.1,0);media.release(soundId);media.release(soundId);
   const closed=(()=>{try{media.get(soundId);return false;}catch{return true;}})();
   return {failures,played,closed};
 });
 assert.deepEqual(mediaFailures,{failures:['cancelled'],played:true,closed:true});
 await page.evaluate(()=>valthorneHost.close());
 assert.equal(await page.evaluate(()=>valthorneHost.media.bytes),0);assert.equal(await page.evaluate(()=>valthorneHost.media.items.size),0);assert.deepEqual(errors,[]);
 await writeFile('build/arena-validation.json',JSON.stringify(result,null,2));console.log('ARENA_VALIDATED',JSON.stringify(result));
}finally{await browser.close();}
