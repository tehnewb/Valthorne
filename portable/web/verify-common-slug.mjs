import {browserTestOptions} from './browser-test-options.mjs';
import {waitForFixtureCompletion} from './browser-fixture-wait.mjs';
import {chromium} from '@playwright/test';
import assert from 'node:assert/strict';
import {mkdir} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
const browser=await chromium.launch(browserTestOptions);
try{
 const page=await browser.newPage({viewport:{width:640,height:480}}),messages=[],errors=[];
 page.on('console',m=>messages.push(m.text()));page.on('pageerror',e=>errors.push(String(e)));
 await page.goto(process.env.TEST_URL||'http://127.0.0.1:8095');
 await page.waitForFunction(()=>globalThis.valthorneHost?.graphics.gl&&document.querySelector('aside').hidden||globalThis.valthorneError,null,{timeout:20000});
 assert.equal(await page.evaluate(()=>globalThis.valthorneError),undefined,messages.join('\n'));
 await page.evaluate(()=>{
  const h=valthorneHost,close=h.close;h.close=function(){globalThis.slugLeaks=this.graphics.objects.size+this.graphics.images.size;return close.call(this);};
  const gl=h.graphics.gl,draw=gl.drawArraysInstanced;
  gl.drawArraysInstanced=function(...args){draw.apply(gl,args);const pixels=new Uint8Array(640*480*4);gl.readPixels(0,0,640,480,gl.RGBA,gl.UNSIGNED_BYTE,pixels);let white=0,yellow=0;for(let i=0;i<pixels.length;i+=4){if(pixels[i]>200&&pixels[i+1]>200){if(pixels[i+2]>200)white++;else yellow++;}}globalThis.slugPixels={white,yellow,error:gl.getError()};gl.drawArraysInstanced=draw;};
 });
 await page.waitForFunction(()=>globalThis.slugPixels,null,{timeout:5000});
 const pixels=await page.evaluate(()=>slugPixels);assert.equal(pixels.error,0);assert(pixels.white>1500&&pixels.yellow>500,JSON.stringify(pixels));
 const output=new URL('./build/verification/',import.meta.url);await mkdir(output,{recursive:true});await page.screenshot({path:fileURLToPath(new URL('common-slug.png',output))});
 await waitForFixtureCompletion(page,{fixture:'common-slug',messages,errors});assert.deepEqual(errors,[],messages.join('\n'));assert(messages.includes('COMMON_SLUG_VALIDATED checks=5'),messages.join('\n'));assert(messages.includes('COMMON_SLUG_RETURNED'),messages.join('\n'));assert.equal(await page.evaluate(()=>globalThis.slugLeaks),0);
 const width=Number(messages.find(message=>message.startsWith('COMMON_SLUG_READY width='))?.split('=')[1]);assert(Math.abs(width-90.36001)<.01,'Desktop/browser font advance mismatch: '+width);
 for(const message of messages)if(message.startsWith('SLUG_BENCHMARK'))console.log(message);
 console.log('COMMON_SLUG_BROWSER_VALIDATED '+JSON.stringify(pixels));
}finally{await browser.close();}
