import {chromium}from'@playwright/test';import assert from'node:assert/strict';
const browser=await chromium.launch({channel:process.env.BROWSER_CHANNEL||'chrome',headless:true});
try{
 const page=await browser.newPage({viewport:{width:900,height:700}});
 await page.route('**/platform-test.html',r=>r.fulfill({contentType:'text/html',body:'<!doctype html><meta charset="utf-8"><style>#scene{width:100%;height:100%}</style><canvas id="scene"></canvas><script type="module">import {BrowserPlatform} from "./platform.js";globalThis.platformTest=new BrowserPlatform(document.querySelector("#scene"));platformTest.window.fullscreen(true);</script>'}));
 await page.goto((process.env.TEST_URL||'http://127.0.0.1:8095')+'/platform-test.html');
 const files=await page.evaluate(async()=>{
  const {BrowserFiles}=await import('./files.js'),files=await new BrowserFiles().initialize();await files.create('/','saves',true);await files.create('/saves','state',false);
  const handle=files.open('/saves/state',true,true,true);files.write(handle,new Uint8Array([1,2,3]));await files.flush(handle);
  await files.move('/saves','/saves/state','renamed');files.seek(handle,0);files.write(handle,new Uint8Array([4]));await files.flush(handle);
  if(files.get('/saves/renamed').bytes[0]!==4)throw new Error('Open handle did not follow rename');
  const transaction=files.db.transaction.bind(files.db);files.db.transaction=(name,mode)=>{if(mode==='readwrite')throw new DOMException('Injected quota failure','QuotaExceededError');return transaction(name,mode);};
  files.write(handle,new Uint8Array([5]));let failed=false;try{await files.close(handle);}catch(error){failed=String(error).includes('quota');}if(!failed||handle.closed||!handle.dirty)throw new Error('Failed commit reported success');
  files.db.transaction=transaction;await files.flush(handle);
  const retained=files.residentBytes;await files.remove('/saves/renamed');if(files.residentBytes!==retained)throw new Error('Unlinked open file escaped memory accounting');await files.close(handle);if(files.residentBytes)throw new Error('Unlinked file memory was retained');
  await files.create('/saves','limit',false);const limited=files.open('/saves/limit',true,true,true);let bounded=false;try{files.resize(limited,files.maxFileBytes+1);}catch{bounded=true;}if(!bounded||files.residentBytes)throw new Error('File budget was not enforced');await files.close(limited);await files.remove('/saves/limit');await files.remove('/saves');files.db.close();return {failedCommitRejected:failed,budgetEnforced:bounded,residentBytes:files.residentBytes};
 });
 await page.evaluate(async()=>{const w=platformTest.window;w.size(400,300);w.position(50,60);w.minimize();if(w.stage.style.visibility!=='hidden')throw new Error('Minimize');w.restore();w.maximize();if(w.width!==innerWidth||w.height!==innerHeight)throw new Error('Maximize');w.restore();if(w.width!==400||w.height!==300)throw new Error('Restore');});
 assert.equal(await page.evaluate(()=>platformTest.window.pendingFullscreen),true);
 await page.locator('#scene').click({position:{x:100,y:100}});await page.waitForFunction(()=>document.fullscreenElement===platformTest.window.stage);
 await page.evaluate(()=>platformTest.window.fullscreen(false));await page.waitForFunction(()=>!document.fullscreenElement);
 const windowState=await page.evaluate(()=>{const p=platformTest;const result={fullscreenEntered:true,pending:p.window.pendingFullscreen};p.close();result.removed=!document.querySelector('#valthorne-window');return result;});assert.equal(windowState.pending,false);assert.equal(windowState.removed,true);
 console.log('PLATFORM_SERVICES_VALIDATED '+JSON.stringify({files,window:windowState}));
}finally{await browser.close();}
