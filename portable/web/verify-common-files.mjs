import {chromium} from '@playwright/test';
import assert from 'node:assert/strict';
const browser=await chromium.launch({channel:process.env.BROWSER_CHANNEL||'chrome',headless:true});
try{
 const page=await browser.newPage(),messages=[],errors=[];
 page.on('console',m=>messages.push(m.text()));page.on('pageerror',e=>errors.push(e.stack||String(e)));
 for(let generation=1;generation<=2;generation++){
  await page.goto(process.env.TEST_URL||'http://127.0.0.1:8095');await page.waitForFunction(()=>globalThis.valthorneHost?.closed||globalThis.valthorneError,null,{timeout:30000});
  assert.equal(await page.evaluate(()=>globalThis.valthorneError),undefined,messages.join('\n'));assert.deepEqual(errors,[],messages.join('\n'));
  assert(messages.includes(`COMMON_FILES_VALIDATED checks=12 generation=${generation}`),messages.join('\n'));
 }
 const persisted=await page.evaluate(async()=>{const files=valthorneFiles;const n=files.get('/.codex-temp/portable-file-fixture/counter.bin');return {value:new TextDecoder().decode(n.bytes.subarray(0,n.length)),temporary:[...files.nodes.keys()].filter(p=>p.startsWith('/tmp/'))};});
 assert.equal(persisted.value,'2');assert.deepEqual(persisted.temporary,[]);
 console.log('PERSISTENT_JAVA_FILES_VALIDATED reloads=2 checks=24');
}finally{await browser.close();}
