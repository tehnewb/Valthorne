import initJolt from './node_modules/jolt-physics/dist/jolt-physics.wasm-compat.js';
import {BrowserPhysicsWorld} from './public/physics-world.js';
import assert from 'node:assert/strict';
import {writeFile,mkdir} from 'node:fs/promises';
const J=await initJolt(),layers=Array(256).fill(1);
const world=()=>new BrowserPhysicsWorld(J,[128,4096,4096],layers,()=>{});
const values=(x,y,z,motion=2)=>[x,y,z,0,0,0,1,0,0,0,motion,0,.5,0,.05,.05,1,0,1,0,0,1];
function cycle(){
    const w=world();
    try{
        for(const [type,data] of [[0,[1,1,1]],[1,[.5]],[2,[.25,1]],[3,[.25,1]],[4,[0,0,0,1,0,0,0,1,0,0,0,1]],[5,[0,0,0,1,0,0,0,1,0]]])w.create(type,data,values(type*2,0,3,type===5?0:2));
        const ids=[...w.handles.keys()];w.joint(ids[0],ids[1],0,0,3,2,0,3,1,3);w.step(1/60);w.ray(0,0,5,0,0,-10,null);
    }finally{w.close();}
}
const keeper=world();
for(let i=0;i<10;i++)cycle();
const freeBefore=keeper.physics.sGetFreeMemory();
for(let i=0;i<100;i++)cycle();
const freeAfter=keeper.physics.sGetFreeMemory();
assert(freeAfter>=freeBefore-1024,`WASM memory retained after 100 close cycles: ${freeBefore-freeAfter} bytes`);
keeper.close();
const scenarios=[];
for(const count of [0,32,96]){
    const w=world();
    try{
        w.create(0,[100,100,1],values(0,0,-.5,0));
        for(let i=0;i<count;i++)w.create(0,[.5,.5,.5],values(i%8-4,Math.floor(i/8)%4-2,1+Math.floor(i/32)));
        for(let i=0;i<240;i++)w.step(1/60);
        const samples=[];
        for(let i=0;i<1200;i++){
            // Keep bodies active, so this is not merely a sleeping-body benchmark.
            if(i%30===0)for(const [id,b] of w.handles)if(!b.IsStatic())w.change(id,2,0,0,.5);
            const start=performance.now();w.step(1/60);for(const [id,b] of w.handles)if(!b.IsStatic())w.read(id,0);
            samples.push(performance.now()-start);
        }
        samples.sort((a,b)=>a-b);scenarios.push({dynamicBodies:count,steps:samples.length,medianMs:samples[600],p95Ms:samples[1140],meanMs:samples.reduce((a,b)=>a+b,0)/samples.length});
    }finally{w.close();}
}
const report={runtime:process.version,backend:'Jolt WASM 1.1.0, Node, single thread; step plus pose reads (not renderer or Java overhead)',retainedBytesAfter100WorldCycles:freeBefore-freeAfter,scenarios};
await mkdir('build/verification',{recursive:true});await writeFile('build/verification/physics-benchmark.json',JSON.stringify(report,null,2));console.log(JSON.stringify(report,null,2));
