import {browserTestOptions} from './browser-test-options.mjs';
import {waitForFixtureCompletion,waitForFixtureCondition} from './browser-fixture-wait.mjs';
import {chromium} from '@playwright/test';
import assert from 'node:assert/strict';
import {mkdir} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
const browser=await chromium.launch(browserTestOptions);
try{
 const page=await browser.newPage({viewport:{width:640,height:480}}),messages=[],errors=[];
 page.on('console',m=>{messages.push(m.text());if(['SPRITE_SHADOW_DISABLED','SPRITE_LIGHTS_DISABLED'].includes(m.text()))page.evaluate(()=>installSpriteProbe()).catch(e=>errors.push(String(e)));});page.on('pageerror',e=>errors.push(String(e)));
 await page.goto(process.env.TEST_URL||'http://127.0.0.1:8095');await page.waitForFunction(()=>globalThis.valthorneHost?.graphics.gl&&document.querySelector('aside').hidden||globalThis.valthorneError,null,{timeout:20000});
 assert.equal(await page.evaluate(()=>globalThis.valthorneError),undefined,messages.join('\n'));
 await page.evaluate(()=>{
  const h=valthorneHost,close=h.close;h.close=function(){globalThis.spriteLeaks=this.graphics.objects.size+this.graphics.images.size;return close.call(this);};
  const gl=h.graphics.gl,draw=gl.drawArraysInstanced;globalThis.spriteCaptures=[];
  globalThis.installSpriteProbe=()=>{gl.drawArraysInstanced=function(...args){draw.apply(gl,args);if(gl.getParameter(gl.FRAMEBUFFER_BINDING)!==null||args[3]<10)return;const pixels=new Uint8Array(640*480*4);gl.readPixels(0,0,640,480,gl.RGBA,gl.UNSIGNED_BYTE,pixels);spriteCaptures.push({pixels,error:gl.getError()});gl.drawArraysInstanced=draw;};};installSpriteProbe();
 });
 await page.waitForFunction(()=>spriteCaptures.length>0,null,{timeout:5000});
 const initial=await page.evaluate(()=>{const {pixels:p,error}=spriteCaptures[0];let green=0,overlay=0;for(let y=0;y<480;y++)for(let x=0;x<640;x++){const i=(y*640+x)*4;if(y<420&&p[i+1]>p[i]*2&&p[i+1]>100)green++;if(y>=440&&p[i+1]>180&&p[i+2]>p[i]&&p[i+1]>p[i+2])overlay++;}return{green,overlay,error};});
 assert.equal(initial.error,0);assert(initial.green>1000&&initial.overlay>100,JSON.stringify(initial));
 await waitForFixtureCondition(page,()=>spriteCaptures.length>=2,{fixture:'common-sprite-lighting',phase:'shadow-disabled',messages,errors});
 const output=new URL('./build/verification/',import.meta.url);await mkdir(output,{recursive:true});await page.screenshot({path:fileURLToPath(new URL('common-sprite-lighting.png',output))});
 await waitForFixtureCompletion(page,{fixture:'common-sprite-lighting',messages,errors});assert.deepEqual(errors,[],messages.join('\n'));assert(messages.includes('COMMON_SPRITE_LIGHTING_VALIDATED checks=4'),messages.join('\n'));assert(messages.includes('COMMON_SPRITE_LIGHTING_RETURNED'),messages.join('\n'));assert.equal(await page.evaluate(()=>spriteLeaks),0);
 const changes=await page.evaluate(()=>{if(spriteCaptures.length!==3)return{captures:spriteCaptures.length};let shadow=0,volume=0,overlayUpdates=0;const a=spriteCaptures[0].pixels,b=spriteCaptures[1].pixels,c=spriteCaptures[2].pixels;for(let i=0;i<640*420*4;i+=4){if(b[i]>a[i]+10&&Math.abs(a[i]-a[i+1])<5)shadow++;if(b[i+1]>c[i+1]+10&&b[i+1]>b[i]*2)volume++;}for(let i=640*440*4;i<a.length;i+=4)if(Math.abs(a[i]-b[i])+Math.abs(a[i+1]-b[i+1])+Math.abs(a[i+2]-b[i+2])>30)overlayUpdates++;const prefix=[18,32,46].map(x=>{let count=0;for(let y=440;y<468;y++)for(let xx=x;xx<x+14;xx++){const i=(y*640+xx)*4;if(b[i+1]>160&&b[i+1]>b[i+2])count++;}return count;});return{shadow,volume,overlayUpdates,prefix,errors:spriteCaptures.map(v=>v.error)};});
 assert.deepEqual(changes.errors,[0,0,0],JSON.stringify(changes));assert(changes.prefix.every(count=>count>30),JSON.stringify(changes));assert(changes.overlayUpdates>25,JSON.stringify(changes));assert(changes.shadow>100&&changes.volume>1000,JSON.stringify(changes));
 for(const message of messages)if(message.startsWith('SPRITE_LIGHTING_BENCHMARK'))console.log(message);console.log('COMMON_SPRITE_LIGHTING_BROWSER_VALIDATED '+JSON.stringify({initial,changes}));
}finally{await browser.close();}
