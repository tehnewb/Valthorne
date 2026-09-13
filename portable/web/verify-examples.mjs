import {spawn} from 'node:child_process';
import {fileURLToPath} from 'node:url';
const cwd=fileURLToPath(new URL('./',import.meta.url));
const server=spawn(process.execPath,['serve.mjs'],{cwd,env:{...process.env,PORT:'0'},windowsHide:true,stdio:['ignore','pipe','inherit']});
try{
 const url=await new Promise((resolve,reject)=>{
  const timeout=setTimeout(()=>reject(new Error('Example server startup timed out')),10000);
  const fail=error=>{clearTimeout(timeout);reject(error);};
  server.once('error',fail);server.once('exit',code=>fail(new Error('Example server exited: '+code)));
  server.stdout.on('data',data=>{const match=String(data).match(/http:\/\/127\.0\.0\.1:\d+/);if(match){clearTimeout(timeout);resolve(match[0]);}});
 });
 const selected=process.argv.find(arg=>arg.startsWith('--only='))?.slice(7).split(',');
 for(const script of selected || ['verify.mjs','verify-arena.mjs','verify-audio-streaming.mjs','verify-animation.mjs','verify-compute.mjs','verify-platform-services.mjs'])await new Promise((resolve,reject)=>{
  const child=spawn(process.execPath,[script],{cwd,env:{...process.env,TEST_URL:url},windowsHide:true,stdio:'inherit'});
  child.once('error',reject);child.once('exit',code=>code===0?resolve():reject(new Error(script+' failed: '+code)));
 });
 console.log('BROWSER_EXAMPLES_VALIDATED');
}finally{server.kill();}
