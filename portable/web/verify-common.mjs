import {chromium} from '@playwright/test';
import assert from 'node:assert/strict';
const browser=await chromium.launch({channel:'chrome',headless:true});
const failure=process.argv.includes('--failure');
const physics=process.argv.includes('--physics');
try{
 const page=await browser.newPage(),errors=[],messages=[];
 page.on('pageerror',error=>errors.push(String(error)));page.on('console',message=>messages.push(message.text()));
 await page.goto(process.env.TEST_URL||'http://127.0.0.1:8095');
 if(!failure&&!physics){await page.waitForFunction(()=>globalThis.valthorneReady,null,{timeout:20000});await page.keyboard.press('b');await page.mouse.click(100,150,{button:'right'});}
 await page.waitForFunction(()=>globalThis.valthorneHost?.closed||globalThis.valthorneError,null,{timeout:20000});
 assert.equal(await page.evaluate(()=>globalThis.valthorneError),undefined,messages.join('\n'));
 if(physics){assert(messages.some(message=>message.includes('COMMON_PHYSICS_VALIDATED')),messages.join('\n'));console.log(messages.find(message=>message.includes('COMMON_PHYSICS_VALIDATED')));}
 else if(failure)assert(messages.some(message=>message.includes('COMMON_FAILURE_VALIDATED')));
 else{
     assert(messages.some(message=>message.includes('COMMON_API_VALIDATED updates=120 renders=120 tasks=1 events=1')));
     assert(messages.some(message=>message.includes('COMMON_INPUT keys=1 mouse=1')));
     assert(messages.findIndex(message=>message.includes('COMMON_MAIN_RETURNED'))>messages.findIndex(message=>message.includes('COMMON_API_VALIDATED')));
 }
 assert.deepEqual(errors,[]);console.log('COMMON_API_BROWSER_VALIDATED');
}finally{await browser.close();}
