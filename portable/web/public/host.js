import { BrowserAssets } from './assets.js';
import { loadPhysicsRuntime } from './physics-runtime.js';
import { BrowserPlatform } from './platform.js';
import { BrowserParticles } from './particles.js';
import { BrowserMedia } from './media.js';
import { BrowserPhysicsWorld } from './physics-world.js';
import { BrowserSceneRenderer } from './scene-renderer.js';
import { BrowserGraphics } from './graphics.js';
import { BrowserAudio } from './audio-backend.js';
import { BrowserFonts } from './fonts.js';
import { BrowserYoga } from './yoga-backend.js';
import { BrowserNano } from './nano-backend.js';
import { BrowserFiles } from './files.js';
import { BrowserCompute } from './compute.js';

const canvas = document.querySelector('#scene');
const status = document.querySelector('#stats') || document.querySelector('#status');
const errorBox = document.querySelector('#error');
const isMobile = () => typeof matchMedia === 'function' && (matchMedia('(pointer: coarse)').matches || matchMedia('(hover: none)').matches);
const getDevicePixelRatio = () => {
  const deviceRatio = Number(window.devicePixelRatio) || 1;
  const maxRatio = isMobile() ? 3 : 2;
  return Math.max(1, Math.min(deviceRatio, maxRatio));
};
function fail(error) {
    errorBox.textContent = `Unable to run the scene: ${error.message || error}`;
    errorBox.style.display = 'block';
    globalThis.valthorneError = String(error.stack || error);
    console.error(error);
}

function cube(F, engine) {
    const positions = [], normals = [], indices = [];
    const faces = [
        [[1,0,0], [[1,-1,-1],[1,1,-1],[1,1,1],[1,-1,1]]],
        [[-1,0,0], [[-1,-1,1],[-1,1,1],[-1,1,-1],[-1,-1,-1]]],
        [[0,1,0], [[-1,1,-1],[-1,1,1],[1,1,1],[1,1,-1]]],
        [[0,-1,0], [[-1,-1,1],[-1,-1,-1],[1,-1,-1],[1,-1,1]]],
        [[0,0,1], [[-1,-1,1],[1,-1,1],[1,1,1],[-1,1,1]]],
        [[0,0,-1], [[1,-1,-1],[-1,-1,-1],[-1,1,-1],[1,1,-1]]]
    ];
    for (const [normal, vertices] of faces) {
        const base = positions.length / 3;
        for (const vertex of vertices) {positions.push(...vertex); normals.push(...normal);}
        indices.push(base,base+1,base+2,base,base+2,base+3);
    }
    const orientationBuilder = new F.SurfaceOrientation$Builder();
    orientationBuilder.vertexCount(24);
    orientationBuilder.normals(new Float32Array(normals), 0);
    const orientation = orientationBuilder.build();
    const tangents = orientation.getQuatsFloat4(24);
    const vb = F.VertexBuffer.Builder().vertexCount(24).bufferCount(2)
        .attribute(F.VertexAttribute.POSITION,0,F.VertexBuffer$AttributeType.FLOAT3,0,12)
        .attribute(F.VertexAttribute.TANGENTS,1,F.VertexBuffer$AttributeType.FLOAT4,0,16).build(engine);
    vb.setBufferAt(engine,0,new Float32Array(positions)); vb.setBufferAt(engine,1,tangents);
    orientation.delete();
    const ib = F.IndexBuffer.Builder().indexCount(36).bufferType(F.IndexBuffer$IndexType.USHORT).build(engine);
    ib.setBuffer(engine,new Uint16Array(indices));
    return {vb,ib};
}

class BrowserHost {
    constructor(J, F) {
        this.J=J; this.F=F; this.objects=[]; this.materials=new Map(); this.closed=false;
        this.physicsWorlds=new Set();
        this.sceneRenderers=new Set();
        this.graphics=new BrowserGraphics(this);
        this.audioBackend=new BrowserAudio(this);this.fonts=new BrowserFonts();
        this.yoga=new BrowserYoga();
        this.nano=new BrowserNano(this);
        this.nextBodyId=1;this.bodyHandles=new Map();this.materialRefs=new Map();this.platform=new BrowserPlatform(canvas);
        this.commands=0; this.paused=false; this.frames=0; this.last=0; this.time=0; this.reportTime=0;
        this.yaw=.65; this.pitch=.42; this.distance=19; this.matrix=new Float32Array(16);
        if(J){
        const settings = new J.JoltSettings();
        settings.mMaxBodies=128; settings.mMaxBodyPairs=2048; settings.mMaxContactConstraints=2048;
        const filter = new J.ObjectLayerPairFilterTable(2);
        filter.EnableCollision(0,1); filter.EnableCollision(1,1);
        const bp = new J.BroadPhaseLayerInterfaceTable(2,2);
        const stationary = new J.BroadPhaseLayer(0), moving = new J.BroadPhaseLayer(1);
        bp.MapObjectToBroadPhaseLayer(0,stationary); bp.MapObjectToBroadPhaseLayer(1,moving);
        J.destroy(stationary); J.destroy(moving);
        settings.mObjectLayerPairFilter=filter; settings.mBroadPhaseLayerInterface=bp;
        settings.mObjectVsBroadPhaseLayerFilter=new J.ObjectVsBroadPhaseLayerFilterTable(bp,2,filter,2);
        this.physics=new J.JoltInterface(settings); J.destroy(settings);
        this.bodies=this.physics.GetPhysicsSystem().GetBodyInterface();
        }
        const e=this.engine=F.Engine.create(canvas, {antialias:false});
        this.scene=e.createScene(); this.view=e.createView(); this.renderer=e.createRenderer(); this.swap=e.createSwapChain();
        this.cameraEntity=F.EntityManager.get().create(); this.camera=e.createCamera(this.cameraEntity);
        this.camera.setExposure(8,1/125,100);
        this.view.setScene(this.scene); this.view.setCamera(this.camera); this.view.setSampleCount(1);
        this.renderer.setClearOptions({clearColor:[.028,.043,.065,1],clear:true});
        this.material=e.createMaterial('lit.filamat'); this.mesh=cube(F,e);
        const sh = new Float32Array(27); sh.set([.7,.8,1]);
        this.ambient=F.IndirectLight.Builder().irradianceSh(3,sh).intensity(8000).build(e);
        this.scene.setIndirectLight(this.ambient);
        this.sun=F.EntityManager.get().create();
        F.LightManager.Builder(F.LightManager$Type.DIRECTIONAL).color([1,.9,.78]).intensity(90000)
            .direction([-.5,-1,-.6]).castShadows(true).build(e,this.sun); this.scene.addEntity(this.sun);
        this.fill=F.EntityManager.get().create();
        F.LightManager.Builder(F.LightManager$Type.DIRECTIONAL).color([.32,.48,.72]).intensity(16000)
            .direction([.7,-.2,.5]).castShadows(false).build(e,this.fill); this.scene.addEntity(this.fill);
        this.point=F.EntityManager.get().create();
        F.LightManager.Builder(F.LightManager$Type.POINT).color([.2,.8,1]).intensity(90000).falloff(20)
            .position([0,5,4]).castShadows(false).build(e,this.point); this.scene.addEntity(this.point);
        this.assets=new BrowserAssets(F,e,this.scene);
        this.media=new BrowserMedia(this);
        this.particles=new BrowserParticles(this);
        this.input();
    }
    color(rgb) {return [(rgb>>16&255)/255,(rgb>>8&255)/255,(rgb&255)/255];}
    createPhysicsWorld(capacities,layers,contact){
        if(this.closed)throw new Error('Backend is closed');
        if(!this.J)throw new Error('Physics is disabled for this export; enable webPhysics and export again.');
        const world=new BrowserPhysicsWorld(this.J,capacities,layers,contact);
        this.physicsWorlds.add(world);world.onClose=()=>this.physicsWorlds.delete(world);return world;
    }
    createSceneRenderer(){
        if(this.closed)throw new Error('Backend is closed');
        const renderer=new BrowserSceneRenderer(this);this.sceneRenderers.add(renderer);
        renderer.onClose=()=>this.sceneRenderers.delete(renderer);return renderer;
    }
    box(x,y,z,hx,hy,hz,rgb,dynamic) {this.createBox(x,y,z,[hx,hy,hz],rgb,dynamic);}
    createBox(x,y,z,scale,rgb,dynamic,locked=false,overrideMaterial=null) {
        if(this.closed)throw new Error('Backend is closed');
        if(!this.J)throw new Error('Physics is disabled for this export; enable webPhysics and export again.');
        if(![x,y,z,...scale].every(Number.isFinite)||scale.some(value=>value<=0))throw new Error('Invalid box');
        if(this.objects.length>=128)throw new Error('Browser body budget of 128 reached');
        const {J,F,engine:e}=this;
        const extent=new J.Vec3(...scale), position=new J.RVec3(x,y,z), rotation=new J.Quat(0,0,0,1);
        const shape=new J.BoxShape(extent,Math.min(.02,...scale));
        const settings=new J.BodyCreationSettings(shape,position,rotation,dynamic?J.EMotionType_Dynamic:J.EMotionType_Static,dynamic?1:0);
        settings.mRestitution=.12; settings.mFriction=.65;
        if(locked)settings.mAllowedDOFs=J.EAllowedDOFs_TranslationX|J.EAllowedDOFs_TranslationY|J.EAllowedDOFs_TranslationZ;
        const body=this.bodies.CreateBody(settings);
        J.destroy(settings); J.destroy(extent); J.destroy(position); J.destroy(rotation);
        this.bodies.AddBody(body.GetID(),dynamic?J.EActivation_Activate:J.EActivation_DontActivate);
        let material=overrideMaterial||this.materials.get(rgb);
        if (!material) {material=this.material.createInstance(); material.setColor3Parameter('baseColor',F.RgbType.sRGB,this.color(rgb)); this.materials.set(rgb,material);}
        if(!overrideMaterial)this.materialRefs.set(rgb,(this.materialRefs.get(rgb)||0)+1);
        const entity=F.EntityManager.get().create();
        F.RenderableManager.Builder(1).boundingBox({center:[0,0,0],halfExtent:[1,1,1]})
            .material(0,material).geometry(0,F.RenderableManager$PrimitiveType.TRIANGLES,this.mesh.vb,this.mesh.ib)
            .castShadows(dynamic&&!overrideMaterial).receiveShadows(!overrideMaterial).build(e,entity);
        this.scene.addEntity(entity);
        const object={id:this.nextBodyId++,body,entity,scale,dynamic,rgb,overrideMaterial};
        this.objects.push(object);this.bodyHandles.set(object.id,object);return object;
    }
    getBody(id){const object=this.bodyHandles.get(id);if(!object)throw new Error('Body handle is closed or scene was cleared');return object;}
    bodyComponent(id,axis){const body=this.getBody(id).body,p=axis===3?body.GetLinearVelocity():body.GetPosition();return axis===0?p.GetX():axis===2?p.GetZ():p.GetY();}
    changeBody(id,operation,x,y,z){
        if(![x,y,z].every(Number.isFinite))throw new Error('Invalid body vector');
        const {body}=this.getBody(id),J=this.J,v=operation===2?new J.RVec3(x,y,z):new J.Vec3(x,y,z);
        try{if(operation===0)this.bodies.SetLinearVelocity(body.GetID(),v);else if(operation===1)this.bodies.AddImpulse(body.GetID(),v);else this.bodies.SetPosition(body.GetID(),v,J.EActivation_Activate);}finally{J.destroy(v);}
    }
    releaseBody(id){
        const object=this.bodyHandles.get(id);if(!object)return;
        this.bodyHandles.delete(id);this.objects.splice(this.objects.indexOf(object),1);
        this.bodies.RemoveBody(object.body.GetID());this.bodies.DestroyBody(object.body.GetID());
        this.scene.remove(object.entity);this.engine.destroyEntity(object.entity);this.F.EntityManager.get().destroy(object.entity);
        if(!object.overrideMaterial){const refs=this.materialRefs.get(object.rgb)-1;if(refs)this.materialRefs.set(object.rgb,refs);else{this.engine.destroyMaterialInstance(this.materials.get(object.rgb));this.materials.delete(object.rgb);this.materialRefs.delete(object.rgb);}}
    }
    setCamera(x,y,z,yaw,pitch,fov){
        if(![x,y,z,yaw,pitch,fov].every(Number.isFinite)||fov<10||fov>150)throw new Error('Invalid camera');
        this.firstPerson={x,y,z,yaw,pitch,fov};
    }
    rayDistance(x,y,z,dx,dy,dz,range,ignore=0){
        if(![x,y,z,dx,dy,dz,range].every(Number.isFinite)||range<=0||range>10000||Math.abs(Math.hypot(dx,dy,dz)-1)>.001)throw new Error('Invalid ray');
        const J=this.J,owned=[];const own=value=>(owned.push(value),value);
        try{
            const origin=own(new J.RVec3(x,y,z)),direction=own(new J.Vec3(dx*range,dy*range,dz*range));
            const ray=own(new J.RRayCast(origin,direction)),settings=own(new J.RayCastSettings()),collector=own(new J.CastRayClosestHitCollisionCollector());
            const bp=own(new J.BroadPhaseLayerFilter()),layer=own(new J.ObjectLayerFilter()),shape=own(new J.ShapeFilter());
            const body=own(ignore?new J.IgnoreSingleBodyFilter(this.getBody(ignore).body.GetID()):new J.BodyFilter());
            this.physics.GetPhysicsSystem().GetNarrowPhaseQuery().CastRay(ray,settings,collector,bp,layer,body,shape);
            this.lastHitId=0;
            if(collector.HadHit()){const nativeId=collector.mHit.mBodyID.GetIndexAndSequenceNumber();for(const object of this.objects)if(object.body.GetID().GetIndexAndSequenceNumber()===nativeId){this.lastHitId=object.id;break;}}
            return collector.HadHit()?collector.mHit.mFraction*range:-1;
        }finally{for(let i=owned.length-1;i>=0;i--)J.destroy(owned[i]);}
    }
    castRay(x,y,z,dx,dy,dz,range,ignore){const distance=this.rayDistance(x,y,z,dx,dy,dz,range,ignore);return distance<0?null:{id:this.lastHitId,distance};}
    step(delta) {if(this.physics)this.physics.Step(delta,1);this.particles.step(delta); this.steps=(this.steps||0)+1;}
    light(x,y,z,rgb,intensity) {
        const manager=this.engine.getLightManager(), instance=manager.getInstance(this.point);
        manager.setPosition(instance,[x,y,z]); manager.setColor(instance,this.color(rgb)); manager.setIntensity(instance,intensity);
        instance.delete();
    }
    transform(object) {
        const p=object.body.GetPosition(), q=object.body.GetRotation();
        const x=q.GetX(),y=q.GetY(),z=q.GetZ(),w=q.GetW(),[sx,sy,sz]=object.scale,m=this.matrix;
        m[0]=(1-2*(y*y+z*z))*sx; m[1]=2*(x*y+z*w)*sx; m[2]=2*(x*z-y*w)*sx; m[3]=0;
        m[4]=2*(x*y-z*w)*sy; m[5]=(1-2*(x*x+z*z))*sy; m[6]=2*(y*z+x*w)*sy; m[7]=0;
        m[8]=2*(x*z+y*w)*sz; m[9]=2*(y*z-x*w)*sz; m[10]=(1-2*(x*x+y*y))*sz; m[11]=0;
        m[12]=p.GetX(); m[13]=p.GetY(); m[14]=p.GetZ(); m[15]=1;
        const manager=this.engine.getTransformManager(), instance=manager.getInstance(object.entity);
        manager.setTransform(instance,m); instance.delete();
    }
    render() {
        const ratio=getDevicePixelRatio(),w=Math.max(1,Math.round(canvas.clientWidth*ratio)),h=Math.max(1,Math.round(canvas.clientHeight*ratio));
        if (canvas.width!==w||canvas.height!==h) {canvas.width=w;canvas.height=h;this.view.setViewport([0,0,w,h]);}
        this.camera.setProjectionFov(this.firstPerson?.fov||45,w/h,.1,100,this.F.Camera$Fov.VERTICAL);
        const d=this.distance*Math.max(1,Math.sqrt(h/w)),c=Math.cos(this.pitch);
        this.camera.lookAt([Math.sin(this.yaw)*c*d,3+Math.sin(this.pitch)*d,Math.cos(this.yaw)*c*d],[0,2,0],[0,1,0]);
        if(this.firstPerson){const {x,y,z,yaw,pitch}=this.firstPerson,cp=Math.cos(pitch);this.camera.lookAt([x,y,z],[x+Math.sin(yaw)*cp,y+Math.sin(pitch),z-Math.cos(yaw)*cp],[0,1,0]);}
        for (const object of this.objects) this.transform(object);
        this.renderer.render(this.swap,this.view);
        this.frames++; globalThis.valthorneReady=true;
    }
    clear() {
        this.particles.clear();
        this.assets.clear();
        for (const id of this.bodyHandles.keys())this.releaseBody(id);
        this.firstPerson=null;
        for (const material of this.materials.values()) this.engine.destroyMaterialInstance(material);
        this.materials.clear();
    }
    input() {
        canvas.addEventListener('webglcontextlost',e=>{e.preventDefault();this.contextLost=true;cancelAnimationFrame(this.raf);this.platform.resetInput();fail(new Error('Graphics context lost. Reload to restore the scene.'));});
        if (!document.querySelector('#enter')) return;
        const arena=new URLSearchParams(location.search).get('scene')==='arena';
        document.querySelector('#enter').hidden=!arena;
        document.querySelector('#drop').hidden=arena;
        if(arena){document.querySelector('h1').textContent='First-person integration arena';document.querySelector('#instructions').textContent='Enter arena to capture the mouse. WASD move, Shift sprint, Space jump, R reload, Escape release mouse.';}
        document.querySelector('#enter').onclick=()=>{this.platform.capture(true);this.platform.unlockAudio();};
        document.querySelector('#drop').onclick=()=>this.commands|=1;
        document.querySelector('#reset').onclick=()=>this.commands|=2;
        document.querySelector('#pause').onclick=()=>{this.paused=!this.paused;document.querySelector('#pause').textContent=this.paused?'Resume simulation':'Pause simulation';};
        let draggingId=null,lastX=0,lastY=0;
        const updateDrag=e=>{
            if (draggingId!==e.pointerId) return;
            e.preventDefault();
            this.yaw-=(e.clientX-lastX)*.006;
            this.pitch=Math.max(.05,Math.min(1.3,this.pitch+(e.clientY-lastY)*.006));
            lastX=e.clientX;lastY=e.clientY;
        };
        const startDrag=e=>{
            if(arena||this.applicationMode||draggingId!==null) return;
            draggingId=e.pointerId;lastX=e.clientX;lastY=e.clientY;
            if (canvas.setPointerCapture) {try {canvas.setPointerCapture(e.pointerId);} catch (error) { }}
        };
        const stopDrag=e=>{
            if (draggingId===null||draggingId!==e.pointerId) return;
            if (canvas.releasePointerCapture && canvas.hasPointerCapture?.(e.pointerId)) {
                try {canvas.releasePointerCapture(e.pointerId);} catch (error) {}
            }
            draggingId=null;
        };
        canvas.addEventListener('pointerdown', startDrag, {passive:false, signal:this.platform.events.signal});
        window.addEventListener('pointermove', updateDrag, {passive:false, signal:this.platform.events.signal});
        window.addEventListener('pointerup', stopDrag, {passive:false, signal:this.platform.events.signal});
        window.addEventListener('pointercancel', stopDrag, {passive:false, signal:this.platform.events.signal});
        canvas.addEventListener('lostpointercapture', () => {draggingId=null;}, {signal:this.platform.events.signal});
        canvas.addEventListener('wheel',e=>{if(this.applicationMode)return;e.preventDefault();this.distance=Math.max(7,Math.min(40,this.distance*Math.exp(e.deltaY*.001)));},{passive:false,signal:this.platform.events.signal});
    }
    connect(frame,shutdown) {
        document.querySelectorAll('button').forEach(button=>button.disabled=false);
        const animate=now=>{
            if(this.closed||this.contextLost)return;
            try {
                const dt=this.last?(now-this.last)/1000:0;this.last=now;this.time+=dt;
                const commands=this.commands|((this.paused||document.hidden)?4:0);this.commands=0;
                frame(dt,commands,parseInt((document.querySelector('#color')?.value || '#65d9ff').slice(1),16));
                if(!this.closed&&!this.contextLost)this.raf=requestAnimationFrame(animate);
            }catch(error){cancelAnimationFrame(this.raf);fail(error);}
        };
        this.raf=requestAnimationFrame(animate);
        window.addEventListener('pagehide',e=>{if(!e.persisted)shutdown();});
        document.addEventListener('visibilitychange',()=>{this.last=0;});
    }
    connectApplication(frame,shutdown){
        this.applicationMode=true;
        for (const element of document.querySelectorAll('aside,header,footer')) element.hidden=true;
        const profile=new URLSearchParams(location.search).has('profileMemory');
        let nextSample=0;
        this.connect(dt=>{
            frame(dt);
            if(profile&&performance.now()>=nextSample){
                nextSample=performance.now()+5000;
                console.info('VALTHORNE_MEMORY '+JSON.stringify({
                    heapUsed:performance.memory?.usedJSHeapSize??null,
                    heapTotal:performance.memory?.totalJSHeapSize??null,
                    filamentHeap:this.F.HEAPU8?.byteLength??null,
                    physicsHeap:this.J?.HEAPU8?.byteLength??0,
                    layoutHandles:this.yoga.objects.size,
                    graphicsHandles:this.graphics.objects.size,
                    images:this.graphics.images.size,
                    vectorContexts:this.nano.contexts.size,
                    renderers:[...this.sceneRenderers].map(r=>({meshes:r.meshes.size,entries:r.entries.length,lights:r.lights.length})),
                    canvas:[canvas.width,canvas.height]
                }));
            }
        },shutdown);
    }
    report(bodies,seconds) {
        this.javaBodies=bodies; this.javaSeconds=seconds;
        if(this.time-this.reportTime<.5)return;
        const fps=Math.round((this.frames-(this.reportFrames||0))/(this.time-this.reportTime));
        let dynamic=0;for(const object of this.objects)if(object.dynamic)dynamic++;
        if (status) status.textContent=`${fps} FPS · ${dynamic} dynamic bodies · ${this.assets.models.size} models · ${this.particles.items.length} particles · ${this.particles.lightCount} particle lights · ${seconds}s simulation`;
        this.reportTime=this.time;this.reportFrames=this.frames;
    }
    close() {
        this.compute?.close();
        for(const renderer of this.sceneRenderers)renderer.close();
        this.graphics.close();
        this.audioBackend.close();this.fonts.close();
        this.nano.close();
        this.yoga.close();
        for(const world of this.physicsWorlds)world.close();
        if(this.closed)return;this.closed=true;cancelAnimationFrame(this.raf);this.media.close();this.platform.close();this.assets.close();this.clear();this.particles.close();
        const e=this.engine;
        for(const entity of [this.sun,this.fill,this.point]){this.scene.remove(entity);e.destroyEntity(entity);this.F.EntityManager.get().destroy(entity);}
        e.destroyView(this.view);e.destroyScene(this.scene);e.destroyRenderer(this.renderer);e.destroySwapChain(this.swap);
        e.destroyCameraComponent(this.cameraEntity);this.F.EntityManager.get().destroy(this.cameraEntity);
        e.destroyMaterial(this.material);e.destroyVertexBuffer(this.mesh.vb);e.destroyIndexBuffer(this.mesh.ib);e.destroyIndirectLight(this.ambient);
        this.F.Engine.destroy(e);if(this.physics)this.J.destroy(this.physics);
    }
}

try {
    if (!globalThis.WebAssembly) throw new Error('This browser does not support WebAssembly');
    const physicsEnabled=document.querySelector('meta[name="valthorne-physics"]')?.content!=='disabled';
    const physicsRuntime=loadPhysicsRuntime(physicsEnabled);
    const [J,files]=await Promise.all([physicsRuntime,new BrowserFiles().initialize(),new Promise((resolve,reject)=>{
        if(!globalThis.Filament) {reject(new Error('Filament runtime could not be loaded'));return;}
        const timer=setTimeout(()=>reject(new Error('Filament startup timed out')),30000);
        Filament.init(['lit.filamat','particle.filamat','engine-surface.filamat','engine-alpha.filamat','engine-glass.filamat'],()=>{clearTimeout(timer);resolve();});
    })]);
    globalThis.valthorneFiles=files;
    globalThis.valthorneHost=new BrowserHost(J,Filament);
    valthorneHost.compute=await BrowserCompute.create(valthorneHost);
    main();
} catch(error) {fail(error);}
