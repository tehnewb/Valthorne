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
  const h=valthorneHost,close=h.close;h.close=function(){globalThis.tiledLeaks=this.graphics.objects.size+this.graphics.images.size;return close.call(this);};
  const gl=h.graphics.gl,draw=gl.drawArraysInstanced;
  gl.drawArraysInstanced=function(...args){draw.apply(gl,args);const sample=(x,y)=>{const pixels=new Uint8Array(4);gl.readPixels(x,y,1,1,gl.RGBA,gl.UNSIGNED_BYTE,pixels);return [...pixels];};globalThis.tiledPixels={red:sample(80,280),green:sample(160,280),blue:sample(240,280),error:gl.getError()};gl.drawArraysInstanced=draw;};
 });
 await page.waitForFunction(()=>globalThis.tiledPixels,null,{timeout:5000});
 const pixels=await page.evaluate(()=>tiledPixels);assert.equal(pixels.error,0);
 assert(pixels.red[0]>240&&pixels.red[1]<10,JSON.stringify(pixels));assert(pixels.green[1]>240&&pixels.green[2]<10,JSON.stringify(pixels));assert(pixels.blue[2]>240&&pixels.blue[0]<10,JSON.stringify(pixels));
 const output=new URL('./build/verification/',import.meta.url);await mkdir(output,{recursive:true});await page.screenshot({path:fileURLToPath(new URL('common-tiled.png',output))});
 await waitForFixtureCompletion(page,{fixture:'common-tiled',messages,errors});assert.deepEqual(errors,[],messages.join('\n'));assert(messages.includes('COMMON_TILED_VALIDATED checks=17'),messages.join('\n'));assert(messages.includes('COMMON_TILED_RETURNED'),messages.join('\n'));assert.equal(await page.evaluate(()=>globalThis.tiledLeaks),0);
 for(const message of messages)if(message.startsWith('TILED_BENCHMARK'))console.log(message);
 console.log('COMMON_TILED_BROWSER_VALIDATED '+JSON.stringify(pixels));
}finally{await browser.close();}
