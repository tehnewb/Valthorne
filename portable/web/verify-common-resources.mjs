import {chromium} from '@playwright/test';
import assert from 'node:assert/strict';
const browser=await chromium.launch({channel:'chrome',headless:true});
try{
 const page=await browser.newPage(),messages=[],errors=[];page.on('console',m=>messages.push(m.text()));page.on('pageerror',e=>errors.push(String(e)));
 await page.goto(process.env.TEST_URL||'http://127.0.0.1:8095');await page.waitForFunction(()=>globalThis.valthorneHost?.closed||globalThis.valthorneError,null,{timeout:20000});
 assert.equal(await page.evaluate(()=>globalThis.valthorneError),undefined,messages.join('\n'));assert.deepEqual(errors,[],messages.join('\n'));
 assert(messages.includes('COMMON_RESOURCE_VALIDATED checks=6'),messages.join('\n'));assert(messages.includes('COMMON_RESOURCE_RETURNED'),messages.join('\n'));
 console.log('COMMON_RESOURCES_BROWSER_VALIDATED');
}finally{await browser.close();}
