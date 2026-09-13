import {spawn} from 'node:child_process';
import {fileURLToPath} from 'node:url';
const root=fileURLToPath(new URL('../../',import.meta.url)),web=fileURLToPath(new URL('./',import.meta.url));
const desktop=process.argv.includes('--desktop');
const selected=process.argv.find(arg=>arg.startsWith('--only='))?.slice(7).split(',');
const server=spawn(process.execPath,['serve.mjs'],{cwd:web,env:{...process.env,PORT:'0'},windowsHide:true,stdio:['ignore','pipe','inherit']});
const url=await new Promise((resolve,reject)=>{server.once('error',reject);server.once('exit',code=>reject(new Error('Test server exited: '+code)));server.stdout.on('data',data=>{const match=String(data).match(/http:\/\/127\.0\.0\.1:\d+/);if(match)resolve(match[0]);});});
function run(command,args,cwd=root){return new Promise((resolve,reject)=>{const child=spawn(command,args,{cwd,windowsHide:true,env:{...process.env,TEST_URL:url},stdio:'inherit'});child.once('error',reject);child.once('exit',code=>code===0?resolve():reject(new Error(`${command} failed (${code})`)));});}
function gradle(task,target,main,resources){
    const args=['-p','portable',task,`-Ptarget=${target}`,`-PapplicationMain=compatibility.${main}`];
    if(resources)args.push(`-PapplicationResources=${resources}`);
    return process.platform==='win32'?run('cmd.exe',['/d','/s','/c',`gradlew.bat ${args.join(' ')}`]):run('./gradlew',args);
}
try{
    let verified=0;
    for(const [main,script,flags,resources] of [
        ['CommonApplication','verify-common.mjs',[]],['FailureApplication','verify-common.mjs',['--failure']],
        ['CommonGraphicsApplication','verify-common-graphics.mjs',[]],
        ['CommonAudioApplication','verify-common-audio.mjs',[],'portable/compatibility/assets'],['CommonFontApplication','verify-common-font.mjs',[]],
        ['CommonFileApplication','verify-common-files.mjs',[]],
        ['CommonPathTracingApplication','verify-common-pathtracing.mjs',[]],
        ['CommonComputeApplication','verify-common-compute.mjs',[]],['CommonWindowApplication','verify-common-window.mjs',[]],
        ['CommonUIApplication','verify-common-ui.mjs',[]],['CommonModelApplication','verify-common-model.mjs',[]],
        ['CommonLightingApplication','verify-common-lighting.mjs',[]],
        ['CommonResourceApplication','verify-common-resources.mjs',[],'portable/compatibility/assets'],
        ['CommonTiledApplication','verify-common-tiled.mjs',[],'portable/compatibility/assets'],
        ['CommonAssetsApplication','verify-common-assets.mjs',[]],
        ['CommonSlugApplication','verify-common-slug.mjs',[]],
        ['CommonRasterApplication','verify-common-raster.mjs',[]],
        ['CommonLegacy2DApplication','verify-common-legacy2d.mjs',[]],
        ['CommonRadianceApplication','verify-common-radiance.mjs',[]],
        ['CommonSpriteLightingApplication','verify-common-sprite-lighting.mjs',[]],
        ['CommonPhysicsApplication','verify-common.mjs',['--physics']],['CommonSceneApplication','verify-common-scene.mjs',[]]
    ]){
        if(selected&&!selected.includes(main))continue;
        if(desktop)await gradle('runGame','desktop',main,resources);
        await gradle('buildGame','web',main,resources);await run(process.execPath,[script,...flags],web);
        verified++;
    }
    if(!verified)throw new Error('No compatibility fixtures selected');
    await run(process.execPath,['benchmark-physics.mjs'],web);
    console.log('UNCHANGED_SOURCE_SUITE_VALIDATED fixtures='+verified+(selected?' selected':' full'));
}finally{
    server.kill();
    // Leave the user's normal lab/arena distribution runnable after validation.
    if(process.platform==='win32')await run('cmd.exe',['/d','/s','/c','gradlew.bat -p portable :web:webDist']);
    else await run('./gradlew',['-p','portable',':web:webDist']);
}
