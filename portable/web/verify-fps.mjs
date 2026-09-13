import {chromium,firefox,webkit} from '@playwright/test';
import {spawn} from 'node:child_process';
import {fileURLToPath} from 'node:url';
import {mkdir,writeFile} from 'node:fs/promises';
import assert from 'node:assert/strict';
import {browserTestOptions} from './browser-test-options.mjs';
const cwd=fileURLToPath(new URL('./',import.meta.url));process.chdir(cwd);
const server=spawn(process.execPath,['serve.mjs'],{cwd,env:{...process.env,PORT:'0'},windowsHide:true,stdio:['ignore','pipe','inherit']});
const browserName=process.env.TEST_BROWSER||'chrome';
const softwareGpu=process.env.WEBGPU_SOFTWARE==='1'&&['chrome','edge'].includes(browserName);
// Software runs exercise the same behavior but collect a shorter timing sample
// to leave CI time for the complete cross-platform compatibility suite.
const timingFrames=softwareGpu?60:360;
// Keep the full menu, including Exit, within its fixed-height layout while
// avoiding the normal high-resolution raster workload on CI's software GPU.
const viewport=softwareGpu?{width:1200,height:800}:{width:1600,height:960};
const engines={chrome:chromium,edge:chromium,firefox,webkit};
if(!engines[browserName]){server.kill();throw new Error('Unknown TEST_BROWSER: '+browserName);}
let browser,stage='startup';
try{
 browser=await engines[browserName].launch({...(browserName==='chrome'?{channel:'chrome'}:browserName==='edge'?{channel:'msedge'}:{}),headless:true,...(['chrome','edge'].includes(browserName)?{args:browserTestOptions.args}:{})});
 const url=await new Promise((resolve,reject)=>{server.on('error',reject);server.stdout.on('data',data=>{const match=String(data).match(/http:\/\/127\.0\.0\.1:\d+/);if(match)resolve(match[0]);});});
 const page=await browser.newPage({viewport}),errors=[],messages=[];
 await page.addInitScript(()=>{globalThis.fpsLabels=new Map();for(const Type of [globalThis.CanvasRenderingContext2D,globalThis.OffscreenCanvasRenderingContext2D].filter(Boolean)){const original=Type.prototype.fillText;Type.prototype.fillText=function(text,x,y,...args){const frame=globalThis.valthorneHost?.frames||0;if(globalThis.fpsLabelFrame!==frame){fpsLabels.clear();globalThis.fpsLabelFrame=frame;}const point=new DOMPoint(x,y).matrixTransform(this.getTransform());fpsLabels.set(String(text),{x:point.x,y:point.y});return original.call(this,text,x,y,...args);};}});
 page.on('pageerror',error=>{errors.push(String(error));console.log(error.stack||String(error));});page.on('console',m=>{messages.push(m.text());if(m.type()==='error')console.log(m.text());});
 await page.goto(url);await page.waitForFunction(()=>globalThis.valthorneReady||globalThis.valthorneError,null,{timeout:120000});
 assert.equal(await page.evaluate(()=>globalThis.valthorneError),undefined,messages.join('\n'));assert.deepEqual(errors,[]);
 // Observe completed rendering and actual fixed physics steps. The game caps its
 // update delta, so wall time is not simulation time on a software GPU.
 await page.evaluate(()=>{
  const h=valthorneHost,clock=globalThis.fpsSmoke={frames:0,seconds:0,steps:0};
  const observe=world=>{const step=world.step;world.step=function(dt){const result=step.call(this,dt);clock.seconds+=dt;clock.steps++;return result;};return world;};
  for(const world of h.physicsWorlds)observe(world);
  const create=h.createPhysicsWorld;h.createPhysicsWorld=function(...args){return observe(create.apply(this,args));};
  const renderer=[...h.sceneRenderers][0],render=renderer.render;
  renderer.render=function(projection,model,...args){globalThis.fpsCamera=Array.from(model);globalThis.fpsProjection=Array.from(projection);const result=render.call(this,projection,model,...args);clock.frames++;return result;};
 });
 const waitClock=async(field,amount)=>{
  const target=await page.evaluate(([field,amount])=>fpsSmoke[field]+amount,[field,amount]);
  await page.waitForFunction(([field,target])=>fpsSmoke[field]+1e-6>=target,[field,target],{timeout:field==='seconds'?180000:120000});
 };
 const frames=count=>waitClock('frames',count),simulate=seconds=>waitClock('seconds',seconds);
 await frames(2);await mkdir('build/verification',{recursive:true});await page.screenshot({path:'build/verification/full-fps.png'});
 async function click(text){await page.waitForFunction(text=>fpsLabels.has(text),text);const point=await page.evaluate(text=>fpsLabels.get(text),text);assert(point.x+12>=0&&point.x+12<viewport.width&&point.y+2>=0&&point.y+2<viewport.height,`Control outside viewport: ${text} ${JSON.stringify(point)}`);await page.mouse.click(point.x+12,point.y+2);}
 const player=()=>page.evaluate(()=>{const world=[...valthorneHost.physicsWorlds][0],body=[...world.handles.values()].find(b=>b.GetObjectLayer()===3),p=body.GetPosition();return [p.GetX(),p.GetY(),p.GetZ()];});
 await click('Enter arena');await page.waitForFunction(()=>document.pointerLockElement===document.querySelector('#scene'),null,{timeout:10000});
 // The first captured event must turn immediately; camera-local weapon placement
 // must remain fixed through yaw and pitch.
 await frames(1);
 const pose=()=>page.evaluate(()=>{const m=fpsCamera,r=[...valthorneHost.sceneRenderers][0];return {yaw:Math.atan2(-m[8],-m[9]),pitch:Math.asin(-m[10]),weapon:r.entries.slice(-2).map(e=>{const t=e.transform,local=[];for(let c=0;c<4;c++)for(let row=0;row<3;row++){let sum=0;for(let k=0;k<3;k++)sum+=m[row*4+k]*(t[c*4+k]-(c===3?m[12+k]:0));local.push(sum);}return local;})};});
 const initialPose=await pose();
 stage='mouse pose';
 for(const [dx,dy] of [[180,-100],[-360,200],[180,-100]]){
  const before=await pose();
  const eventFrame=await page.evaluate(([x,y])=>{const frame=fpsSmoke.frames;document.dispatchEvent(new MouseEvent('mousemove',{movementX:x,movementY:y}));return frame;},[dx,dy]);
  await page.waitForFunction(frame=>fpsSmoke.frames>frame,eventFrame,{timeout:120000});const after=await pose();
  // Legacy event positions are integer pixels; crossing zero can round by one.
  assert(Math.abs(after.yaw-before.yaw-dx*.0022)<.0023,`Mouse yaw lost or duplicated: ${JSON.stringify({dx,dy,before,after})}`);
  assert(Math.abs(after.pitch-before.pitch+dy*.0022)<.0023,'Mouse pitch lost or inverted');
  assert(after.weapon.every((part,i)=>part.every((v,j)=>Math.abs(v-initialPose.weapon[i][j])<.0001)),'Weapon drifted relative to camera');
 }
 stage='movement';
 const start=await player();await page.keyboard.down('w');await simulate(.6);await page.keyboard.up('w');const moved=await player();assert(moved[1]-start[1]>1.5,'Movement failed');
 stage='jump';
 const jumpStart=await player();await page.keyboard.press('Space');await simulate(.2);assert((await player())[2]>jumpStart[2]+.2,'Jump failed');await simulate(.9);
 stage='combat';
 const wide=await page.evaluate(()=>fpsProjection[5]);await page.mouse.down({button:'right'});await frames(1);assert((await page.evaluate(()=>fpsProjection[5]))>wide,'Aim zoom failed');await page.mouse.up({button:'right'});
 await page.mouse.down();await simulate(.45);await page.mouse.up();
 await page.keyboard.press('r');await page.keyboard.press('f');await page.keyboard.press('g');await simulate(.15);
 const combat=await page.evaluate(()=>{const r=[...valthorneHost.sceneRenderers][0],w=[...valthorneHost.physicsWorlds][0];return {lights:r.lightCount(),bodies:w.handles.size,effects:[...w.handles.values()].filter(b=>b.GetObjectLayer()===7).length,grenades:[...w.handles.values()].filter(b=>b.GetObjectLayer()===9).length,labels:[...fpsLabels.keys()]};});
 assert(combat.lights>4&&combat.effects>0&&combat.grenades===1,JSON.stringify(combat));await page.screenshot({path:'build/verification/full-fps-combat.png'});
 await simulate(2.8);assert.equal(await page.evaluate(()=>[...[...valthorneHost.physicsWorlds][0].handles.values()].filter(b=>b.GetObjectLayer()===9).length),0,'Grenade fuse');
 await page.keyboard.press('Escape');await page.waitForFunction(()=>fpsLabels.has('Resume run'));const paused=await player(),pausedClock=await page.evaluate(()=>fpsSmoke.seconds);await frames(3);assert.deepEqual(await player(),paused,'Pause must stop physics');assert.equal(await page.evaluate(()=>fpsSmoke.seconds),pausedClock,'Pause must stop physics steps');
 await click('Particle lights: on');await page.waitForFunction(()=>fpsLabels.has('Particle lights: off'));assert.equal(await page.evaluate(()=>[...valthorneHost.sceneRenderers][0].lightCount()),4);
 await click('Particle physics: Jolt');await page.waitForFunction(()=>fpsLabels.has('Particle physics: visual only'));await click('Particle physics: visual only');await click('Particle lights: off');
 stage='restart ownership';const restarts=[];
 for(let cycle=0;cycle<5;cycle++){
  await click('Restart run');await simulate(.3);await page.keyboard.press('Escape');await page.waitForFunction(()=>fpsLabels.has('Resume run'));
  restarts.push(await page.evaluate(()=>{const h=valthorneHost,r=[...h.sceneRenderers][0],w=[...h.physicsWorlds][0];return {worlds:h.physicsWorlds.size,bodies:w.handles.size,meshes:r.meshes.size,entries:r.entries.length,graphics:h.graphics.objects.size,free:w.physics.sGetFreeMemory(),heap:h.J.HEAP8.byteLength};}));
 }
 assert(restarts.every(s=>s.worlds===1&&s.bodies===restarts[0].bodies&&s.meshes===restarts[0].meshes&&s.graphics===restarts[0].graphics),JSON.stringify(restarts));assert(restarts.at(-1).free>=restarts[1].free-1024,JSON.stringify(restarts));
 const measure=()=>page.evaluate(async count=>{const times=[];let last=performance.now();for(let i=0;i<count;i++){await new Promise(requestAnimationFrame);const now=performance.now();times.push(now-last);last=now;}times.sort((a,b)=>a-b);return {frames:times.length,meanMs:times.reduce((a,b)=>a+b)/times.length,p95Ms:times[Math.floor(times.length*.95)]};},timingFrames);
 console.log('FULL_FPS_BEHAVIOR_VALIDATED '+JSON.stringify({combat,restarts}));
 stage='normal frame timing';await click('Resume run');const normalPlay=await measure();console.log('FULL_FPS_NORMAL_TIMING '+JSON.stringify(normalPlay));
 stage='combat frame timing';
 await page.mouse.down();await page.keyboard.press('f');await page.keyboard.press('g');const combatTiming=await measure();await page.mouse.up();
 const state={moved:moved[1]-start[1],combat,restarts,frameInterval:{normalPlay,combat:combatTiming},environment:{softwareGpu,...await page.evaluate(()=>{const gl=document.querySelector('#scene').getContext('webgl2'),debug=gl?.getExtension('WEBGL_debug_renderer_info');return {userAgent:navigator.userAgent,width:innerWidth,height:innerHeight,devicePixelRatio,renderer:gl?gl.getParameter(debug?debug.UNMASKED_RENDERER_WEBGL:gl.RENDERER):null};})}};
 stage='shutdown';
 await page.keyboard.press('Escape');await click('Exit arena');await page.waitForFunction(()=>valthorneHost.closed);assert.deepEqual(errors,[],messages.join('\n'));
 const remaining=await page.evaluate(()=>({worlds:valthorneHost.physicsWorlds.size,renderers:valthorneHost.sceneRenderers.size,graphics:valthorneHost.graphics.objects.size,yoga:valthorneHost.yoga.objects.size,nano:valthorneHost.nano.contexts.size}));assert(Object.values(remaining).every(n=>n===0),JSON.stringify(remaining));state.remaining=remaining;
 await writeFile(`build/verification/full-fps-${browserName}.json`,JSON.stringify(state,null,2));await writeFile('build/verification/full-fps.json',JSON.stringify(state,null,2));console.log('FULL_FPS_VALIDATED '+browserName+' '+JSON.stringify(state));
}catch(error){
 const page=browser?.contexts()[0]?.pages()[0];let details=null;await mkdir('build/verification',{recursive:true});
 if(page&&!page.isClosed()){
  await page.screenshot({path:`build/verification/full-fps-${browserName}-failure.png`,timeout:5000}).catch(()=>{});
  details=await page.evaluate(()=>({ready:globalThis.valthorneReady,error:String(globalThis.valthorneError||''),clock:globalThis.fpsSmoke,labels:[...(globalThis.fpsLabels?.keys()||[])],captureError:globalThis.valthorneHost?.platform.captureError})).catch(()=>null);
  console.log('BROWSER_FAILURE_STATE',details);
 }
 await writeFile(`build/verification/full-fps-${browserName}-failure.json`,JSON.stringify({browser:browserName,softwareGpu,stage,error:String(error),details},null,2));throw error;
}finally{await browser?.close();server.kill();}
