import {chromium} from '@playwright/test';
import assert from 'node:assert/strict';
import {mkdir} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
const browser=await chromium.launch({channel:'chrome',headless:true});
try{
 const page=await browser.newPage({viewport:{width:640,height:480}}),messages=[],errors=[];
 page.on('console',m=>{const text=m.text();messages.push(text);if(text.startsWith('LIGHTING_BENCHMARK phase=1'))page.evaluate(()=>globalThis.installLightProbe()).catch(e=>errors.push(String(e)));});page.on('pageerror',e=>errors.push(String(e)));
 await page.goto(process.env.TEST_URL||'http://127.0.0.1:8095');await page.waitForFunction(()=>globalThis.valthorneHost?.graphics.gl&&document.querySelector('aside').hidden);
 await page.evaluate(()=>{
  const h=valthorneHost,close=h.close;h.close=function(){globalThis.lightingLeaks=this.graphics.objects.size;return close.call(this);};
  const gl=h.graphics.gl,draw=gl.drawArrays;globalThis.lightSamples=[];
  globalThis.installLightProbe=()=>{let remaining=3;gl.drawArrays=function(...args){draw.apply(gl,args);if(gl.getParameter(gl.FRAMEBUFFER_BINDING)!==null)return;
   const sample=(x,y)=>{const p=new Uint8Array(4);gl.readPixels(x,y,1,1,gl.RGBA,gl.UNSIGNED_BYTE,p);return [...p];};
   lightSamples.push({lit:sample(180,240),shadow:sample(380,240),blue:sample(530,320),error:gl.getError()});
   if(--remaining===0)gl.drawArrays=draw;
  };};installLightProbe();
 });
 await page.waitForFunction(()=>globalThis.lightSamples?.length>2);
 const initial=await page.evaluate(()=>lightSamples[1]);assert(initial.lit[0]>180,JSON.stringify(initial));assert(initial.shadow[0]<40,JSON.stringify(initial));assert(initial.blue[2]>initial.blue[0]+70,JSON.stringify(initial));
 const output=new URL('./build/verification/',import.meta.url);await mkdir(output,{recursive:true});await page.screenshot({path:fileURLToPath(new URL('common-lighting.png',output))});
 await page.waitForFunction(()=>valthorneHost.closed,null,{timeout:15000});assert.deepEqual(errors,[],messages.join('\n'));
 assert(messages.includes('COMMON_LIGHTING_VALIDATED checks=6'),messages.join('\n'));assert(messages.includes('COMMON_LIGHTING_RETURNED'),messages.join('\n'));assert.equal(await page.evaluate(()=>globalThis.lightingLeaks),0);
 const samples=await page.evaluate(()=>lightSamples);assert(samples.some(s=>s.shadow[0]>100),'Disabling shadows did not illuminate the occluded point');assert(samples.every(s=>s.error===0),'WebGL reported an error');
 for(const message of messages)if(message.startsWith('LIGHTING_BENCHMARK'))console.log(message);
 console.log('COMMON_LIGHTING_BROWSER_VALIDATED '+JSON.stringify(initial));
}finally{await browser.close();}
