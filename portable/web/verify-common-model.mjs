import {chromium} from '@playwright/test';
import assert from 'node:assert/strict';
import {mkdir} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
const browser=await chromium.launch({channel:'chrome',headless:true});
try{
 const page=await browser.newPage({viewport:{width:640,height:480}}),messages=[],errors=[];
 page.on('console',m=>messages.push(m.text()));page.on('pageerror',e=>errors.push(String(e)));
 await page.goto(process.env.TEST_URL||'http://127.0.0.1:8095');await page.waitForFunction(()=>globalThis.valthorneHost?.sceneRenderers.size>0&&document.querySelector('aside').hidden);
 await page.evaluate(()=>{const h=valthorneHost,close=h.close;h.close=function(){globalThis.modelLeaks=this.sceneRenderers.size+this.graphics.objects.size+this.graphics.images.size;return close.call(this);};});
 await page.waitForTimeout(100);
 const state=await page.evaluate(()=>({renderers:valthorneHost.sceneRenderers.size,images:valthorneHost.graphics.images.size,error:valthorneHost.graphics.gl.getError()}));assert.equal(state.renderers,1);assert.equal(state.images,1);assert.equal(state.error,0);
 const output=new URL('./build/verification/',import.meta.url);await mkdir(output,{recursive:true});await page.screenshot({path:fileURLToPath(new URL('common-model.png',output))});
 await page.waitForFunction(()=>valthorneHost.closed,null,{timeout:10000});assert.deepEqual(errors,[],messages.join('\n'));assert(messages.includes('COMMON_MODEL_VALIDATED checks=7'),messages.join('\n'));assert(messages.includes('COMMON_MODEL_RETURNED'),messages.join('\n'));assert.equal(await page.evaluate(()=>globalThis.modelLeaks),0);
 console.log('COMMON_MODEL_BROWSER_VALIDATED '+JSON.stringify(state));
}finally{await browser.close();}
