import {chromium,firefox,webkit} from '@playwright/test';
import {spawn} from 'node:child_process';
import {fileURLToPath} from 'node:url';
import {mkdir,writeFile} from 'node:fs/promises';
import assert from 'node:assert/strict';
const cwd=fileURLToPath(new URL('./',import.meta.url));process.chdir(cwd);
const server=spawn(process.execPath,['serve.mjs'],{cwd,env:{...process.env,PORT:'0'},windowsHide:true,stdio:['ignore','pipe','inherit']});
const browserName=process.env.TEST_BROWSER||'chrome';
const engines={chrome:chromium,edge:chromium,firefox,webkit};
if(!engines[browserName]){server.kill();throw new Error('Unknown TEST_BROWSER: '+browserName);}
let browser;
try{
 browser=await engines[browserName].launch({...(browserName==='chrome'?{channel:'chrome'}:browserName==='edge'?{channel:'msedge'}:{}),headless:true});
 const url=await new Promise((resolve,reject)=>{server.on('error',reject);server.stdout.on('data',data=>{const match=String(data).match(/http:\/\/127\.0\.0\.1:\d+/);if(match)resolve(match[0]);});});
 const page=await browser.newPage({viewport:{width:1600,height:960}}),errors=[],messages=[];
 await page.addInitScript(()=>{globalThis.fpsLabels=new Map();for(const Type of [globalThis.CanvasRenderingContext2D,globalThis.OffscreenCanvasRenderingContext2D].filter(Boolean)){const original=Type.prototype.fillText;Type.prototype.fillText=function(text,x,y,...args){const frame=globalThis.valthorneHost?.frames||0;if(globalThis.fpsLabelFrame!==frame){fpsLabels.clear();globalThis.fpsLabelFrame=frame;}const point=new DOMPoint(x,y).matrixTransform(this.getTransform());fpsLabels.set(String(text),{x:point.x,y:point.y});return original.call(this,text,x,y,...args);};}});
 page.on('pageerror',error=>{errors.push(String(error));console.log(error.stack||String(error));});page.on('console',m=>{messages.push(m.text());if(m.type()==='error')console.log(m.text());});
 await page.goto(url);await page.waitForFunction(()=>globalThis.valthorneReady||globalThis.valthorneError,null,{timeout:120000});
 assert.equal(await page.evaluate(()=>globalThis.valthorneError),undefined,messages.join('\n'));assert.deepEqual(errors,[]);
 await page.waitForTimeout(1500);await mkdir('build/verification',{recursive:true});await page.screenshot({path:'build/verification/full-fps.png'});
 await page.evaluate(()=>{const renderer=[...valthorneHost.sceneRenderers][0],render=renderer.render;renderer.render=function(projection,model,...args){globalThis.fpsCamera=Array.from(model);globalThis.fpsProjection=Array.from(projection);return render.call(this,projection,model,...args);};});
 async function click(text){await page.waitForFunction(text=>fpsLabels.has(text),text);const point=await page.evaluate(text=>fpsLabels.get(text),text);await page.mouse.click(point.x+12,point.y+2);}
 const player=()=>page.evaluate(()=>{const world=[...valthorneHost.physicsWorlds][0],body=[...world.handles.values()].find(b=>b.GetObjectLayer()===3),p=body.GetPosition();return [p.GetX(),p.GetY(),p.GetZ()];});
 await click('Enter arena');await page.waitForFunction(()=>document.pointerLockElement===document.querySelector('#scene'),null,{timeout:10000});
 // The first captured event must turn immediately; camera-local weapon placement
 // must remain fixed through yaw and pitch.
 await page.waitForTimeout(100);
 const pose=()=>page.evaluate(()=>{const m=fpsCamera,r=[...valthorneHost.sceneRenderers][0];return {yaw:Math.atan2(-m[8],-m[9]),pitch:Math.asin(-m[10]),weapon:r.entries.slice(-2).map(e=>{const t=e.transform,local=[];for(let c=0;c<4;c++)for(let row=0;row<3;row++){let sum=0;for(let k=0;k<3;k++)sum+=m[row*4+k]*(t[c*4+k]-(c===3?m[12+k]:0));local.push(sum);}return local;})};});
 const initialPose=await pose();
 for(const [dx,dy] of [[180,-100],[-360,200],[180,-100]]){
  const before=await pose();await page.evaluate(([x,y])=>document.dispatchEvent(new MouseEvent('mousemove',{movementX:x,movementY:y})),[dx,dy]);await page.waitForTimeout(50);const after=await pose();
  // Legacy event positions are integer pixels; crossing zero can round by one.
  assert(Math.abs(after.yaw-before.yaw-dx*.0022)<.0023,`Mouse yaw lost or duplicated: ${JSON.stringify({dx,dy,before,after})}`);
  assert(Math.abs(after.pitch-before.pitch+dy*.0022)<.0023,'Mouse pitch lost or inverted');
  assert(after.weapon.every((part,i)=>part.every((v,j)=>Math.abs(v-initialPose.weapon[i][j])<.0001)),'Weapon drifted relative to camera');
 }
 const start=await player();await page.keyboard.down('w');await page.waitForTimeout(600);await page.keyboard.up('w');const moved=await player();assert(moved[1]-start[1]>1.5,'Movement failed');
 const jumpStart=await player();await page.keyboard.press('Space');await page.waitForTimeout(200);assert((await player())[2]>jumpStart[2]+.2,'Jump failed');await page.waitForTimeout(900);
 const wide=await page.evaluate(()=>fpsProjection[5]);await page.mouse.down({button:'right'});await page.waitForTimeout(100);assert((await page.evaluate(()=>fpsProjection[5]))>wide,'Aim zoom failed');await page.mouse.up({button:'right'});
 await page.mouse.down();await page.waitForTimeout(450);await page.mouse.up();
 await page.keyboard.press('r');await page.keyboard.press('f');await page.keyboard.press('g');await page.waitForTimeout(150);
 const combat=await page.evaluate(()=>{const r=[...valthorneHost.sceneRenderers][0],w=[...valthorneHost.physicsWorlds][0];return {lights:r.lightCount(),bodies:w.handles.size,effects:[...w.handles.values()].filter(b=>b.GetObjectLayer()===7).length,grenades:[...w.handles.values()].filter(b=>b.GetObjectLayer()===9).length,labels:[...fpsLabels.keys()]};});
 assert(combat.lights>4&&combat.effects>0&&combat.grenades===1,JSON.stringify(combat));await page.screenshot({path:'build/verification/full-fps-combat.png'});
 await page.waitForTimeout(2800);assert.equal(await page.evaluate(()=>[...[...valthorneHost.physicsWorlds][0].handles.values()].filter(b=>b.GetObjectLayer()===9).length),0,'Grenade fuse');
 await page.keyboard.press('Escape');await page.waitForFunction(()=>fpsLabels.has('Resume run'));const paused=await player();await page.waitForTimeout(250);assert.deepEqual(await player(),paused,'Pause must stop physics');
 await click('Particle lights: on');await page.waitForFunction(()=>fpsLabels.has('Particle lights: off'));assert.equal(await page.evaluate(()=>[...valthorneHost.sceneRenderers][0].lightCount()),4);
 await click('Particle physics: Jolt');await page.waitForFunction(()=>fpsLabels.has('Particle physics: visual only'));await click('Particle physics: visual only');await click('Particle lights: off');
 const restarts=[];
 for(let cycle=0;cycle<5;cycle++){
  await click('Restart run');await page.waitForTimeout(300);await page.keyboard.press('Escape');await page.waitForFunction(()=>fpsLabels.has('Resume run'));
  restarts.push(await page.evaluate(()=>{const h=valthorneHost,r=[...h.sceneRenderers][0],w=[...h.physicsWorlds][0];return {worlds:h.physicsWorlds.size,bodies:w.handles.size,meshes:r.meshes.size,entries:r.entries.length,graphics:h.graphics.objects.size,free:w.physics.sGetFreeMemory(),heap:h.J.HEAP8.byteLength};}));
 }
 assert(restarts.every(s=>s.worlds===1&&s.bodies===restarts[0].bodies&&s.meshes===restarts[0].meshes&&s.graphics===restarts[0].graphics),JSON.stringify(restarts));assert(restarts.at(-1).free>=restarts[1].free-1024,JSON.stringify(restarts));
 const measure=()=>page.evaluate(async()=>{const times=[];let last=performance.now();for(let i=0;i<360;i++){await new Promise(requestAnimationFrame);const now=performance.now();times.push(now-last);last=now;}times.sort((a,b)=>a-b);return {frames:times.length,meanMs:times.reduce((a,b)=>a+b)/times.length,p95Ms:times[Math.floor(times.length*.95)]};});
 await click('Resume run');const normalPlay=await measure();
 await page.mouse.down();await page.keyboard.press('f');await page.keyboard.press('g');const combatTiming=await measure();await page.mouse.up();
 const state={moved:moved[1]-start[1],combat,restarts,frameInterval:{normalPlay,combat:combatTiming},environment:await page.evaluate(()=>({userAgent:navigator.userAgent,width:innerWidth,height:innerHeight,devicePixelRatio}))};
 await page.keyboard.press('Escape');await click('Exit arena');await page.waitForFunction(()=>valthorneHost.closed);assert.deepEqual(errors,[],messages.join('\n'));
 const remaining=await page.evaluate(()=>({worlds:valthorneHost.physicsWorlds.size,renderers:valthorneHost.sceneRenderers.size,graphics:valthorneHost.graphics.objects.size,yoga:valthorneHost.yoga.objects.size,nano:valthorneHost.nano.contexts.size}));assert(Object.values(remaining).every(n=>n===0),JSON.stringify(remaining));state.remaining=remaining;
 await writeFile(`build/verification/full-fps-${browserName}.json`,JSON.stringify(state,null,2));await writeFile('build/verification/full-fps.json',JSON.stringify(state,null,2));console.log('FULL_FPS_VALIDATED '+browserName+' '+JSON.stringify(state));
}catch(error){
 const page=browser?.contexts()[0]?.pages()[0];await mkdir('build/verification',{recursive:true});
 if(page&&!page.isClosed()){
  await page.screenshot({path:`build/verification/full-fps-${browserName}-failure.png`,timeout:5000}).catch(()=>{});
  console.log('BROWSER_FAILURE_STATE',await page.evaluate(()=>({ready:globalThis.valthorneReady,error:String(globalThis.valthorneError||''),labels:[...(globalThis.fpsLabels?.keys()||[])],captureError:globalThis.valthorneHost?.platform.captureError})).catch(()=>null));
 }
 await writeFile(`build/verification/full-fps-${browserName}-failure.json`,JSON.stringify({browser:browserName,error:String(error)},null,2));throw error;
}finally{await browser?.close();server.kill();}
