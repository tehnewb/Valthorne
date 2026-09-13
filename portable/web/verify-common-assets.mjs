import {chromium} from '@playwright/test';
import assert from 'node:assert/strict';
const browser=await chromium.launch({channel:'chrome',headless:true});
try{
 const page=await browser.newPage(),messages=[],errors=[];page.on('console',m=>messages.push(m.text()));page.on('pageerror',e=>errors.push(String(e)));
 await page.goto(process.env.TEST_URL||'http://127.0.0.1:8095');await page.waitForFunction(()=>document.querySelector('aside').hidden||globalThis.valthorneHost?.closed||globalThis.valthorneError,null,{timeout:20000});
 assert.equal(await page.evaluate(()=>valthorneHost.graphics.images.size+valthorneHost.graphics.objects.size+valthorneHost.fonts.fonts.size),0,'Mixed assets retained resources before host cleanup');
 await page.waitForFunction(()=>globalThis.valthorneHost?.closed||globalThis.valthorneError,null,{timeout:10000});
 assert.equal(await page.evaluate(()=>globalThis.valthorneError),undefined,messages.join('\n'));assert.deepEqual(errors,[],messages.join('\n'));
 assert(messages.some(message=>message.startsWith('COMMON_ASSETS_VALIDATED checks=31 ')),messages.join('\n'));assert(messages.includes('COMMON_ASSETS_RETURNED'),messages.join('\n'));
 assert.equal(await page.evaluate(()=>valthorneHost.graphics.images.size+valthorneHost.graphics.objects.size+valthorneHost.fonts.fonts.size),0);
 console.log('COMMON_ASSETS_BROWSER_VALIDATED');
}finally{await browser.close();}
