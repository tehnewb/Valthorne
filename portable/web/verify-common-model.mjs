import {chromium} from '@playwright/test';
import assert from 'node:assert/strict';
import {mkdir,writeFile} from 'node:fs/promises';
import {fileURLToPath} from 'node:url';
import {browserTestOptions} from './browser-test-options.mjs';
import {fixtureState,waitForFixtureCompletion} from './browser-fixture-wait.mjs';
const output=new URL('./build/verification/',import.meta.url);
const result={fixture:'common-model',phase:'launch',status:'running',softwareGpu:process.env.WEBGPU_SOFTWARE==='1'};
const messages=[],errors=[];
let page;
const browser=await chromium.launch(browserTestOptions);
try{
 page=await browser.newPage({viewport:{width:640,height:480}});
 page.on('console',m=>messages.push(m.text()));page.on('pageerror',e=>errors.push(String(e)));
 result.phase='startup';
 await page.goto(process.env.TEST_URL||'http://127.0.0.1:8095');await page.waitForFunction(()=>(globalThis.valthorneHost?.sceneRenderers.size>0&&document.querySelector('aside')?.hidden)||globalThis.valthorneError);
 assert.equal(await page.evaluate(()=>globalThis.valthorneError),undefined,messages.join('\n'));
 await page.evaluate(()=>{const h=valthorneHost,close=h.close;h.close=function(){globalThis.modelLeaks=this.sceneRenderers.size+this.graphics.objects.size+this.graphics.images.size;return close.call(this);};});
 result.phase='render';
 await page.waitForFunction(()=>valthorneHost.frames>=2||globalThis.valthorneError||valthorneHost.closed);
 const state=await page.evaluate(()=>({renderers:valthorneHost.sceneRenderers.size,images:valthorneHost.graphics.images.size,error:valthorneHost.graphics.gl.getError()}));result.renderState=state;assert.equal(state.renderers,1);assert.equal(state.images,1);assert.equal(state.error,0);
 await mkdir(output,{recursive:true});await page.screenshot({path:fileURLToPath(new URL('common-model.png',output))});
 result.phase='completion';
 result.finalState=await waitForFixtureCompletion(page,{fixture:'common-model',messages,errors});
 result.phase='assertions';
 assert.deepEqual(errors,[],messages.join('\n'));assert(messages.includes('COMMON_MODEL_VALIDATED checks=7'),messages.join('\n'));assert(messages.includes('COMMON_MODEL_RETURNED'),messages.join('\n'));assert.equal(await page.evaluate(()=>globalThis.modelLeaks),0);
 assert.equal(result.finalState.renderedFrames,180,'The complete 180-frame model fixture must run');
 result.status='passed';
 console.log('COMMON_MODEL_BROWSER_VALIDATED '+JSON.stringify(state));
}catch(error){
 result.status='failed';result.failure=error.stack||String(error);
 result.finalState=await fixtureState(page).catch(error=>({probeError:String(error)}));
 throw error;
}finally{
 result.messages=messages;result.errors=errors;
 await mkdir(output,{recursive:true});await writeFile(new URL('common-model.json',output),JSON.stringify(result,null,2));
 await browser.close();
}
