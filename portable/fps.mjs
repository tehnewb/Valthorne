import {spawn} from 'node:child_process';
import {fileURLToPath} from 'node:url';
import path from 'node:path';

const root=fileURLToPath(new URL('../',import.meta.url));
const examples=process.env.VALTHORNE_EXAMPLES_DIR||'../Valthorne-examples';
const [action='run',target='web']=process.argv.slice(2);
if(!['build','run'].includes(action)||!['web','desktop'].includes(target)){
 console.error('Usage: node portable/fps.mjs [build|run] [web|desktop]');process.exit(2);
}
function run(command,args,cwd=root){return new Promise((resolve,reject)=>{
 const child=spawn(command,args,{cwd,stdio:'inherit',windowsHide:true});
 child.once('error',reject);child.once('exit',(code,signal)=>code===0?resolve():reject(new Error(`${command} failed: ${signal||code}`)));
});}
function gradle(args,cwd=root){return process.platform==='win32'
 ?run('cmd.exe',['/d','/s','/c','gradlew.bat '+args.join(' ')],cwd)
 :run('./gradlew',args,cwd);}
const sources=['FpsArena','FpsArenaWorld','FpsArenaEffects','FpsCombatModels','FpsEnvironmentModels','PhysicsStudioModels'];
const selection=['-p','portable',`-Ptarget=${target}`,
 '-PapplicationMain=valthorne.examples.fps.FpsArena',`-PapplicationSources=${examples}/src/main/java`,
 '-PapplicationIncludes='+sources.map(name=>`valthorne/examples/${name==='PhysicsStudioModels'?'assets':'fps'}/${name}.java`).join(','),
 `-PapplicationResources=${examples}/src/main/resources`];
try{
 if(target==='desktop')await gradle([`-PvalthorneDir=${path.relative(path.resolve(root,examples),root)}`,...(action==='run'?['runFpsArena']:['classes'])],path.resolve(root,examples));
 else{
  await gradle([...selection,action==='run'&&target==='desktop'?'runGame':'buildGame']);
  if(target==='web'&&action!=='build')await run(process.execPath,['serve.mjs'],fileURLToPath(new URL('./web/',import.meta.url)));
 }
}catch(error){console.error(error.message);process.exitCode=1;}
