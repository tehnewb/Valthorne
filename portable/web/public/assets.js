// Bounded, self-contained GLB loading. Fetch is asynchronous; native decoding is
// synchronous on the render thread so cancellation never destroys in-use native assets.
export class BrowserAssets {
    constructor(F,engine,scene) {
        this.F=F;this.engine=engine;this.scene=scene;
        this.pending=new Map();this.models=new Map();this.nextId=1;this.closed=false;
        this.maxBytes=8*1024*1024;this.maxModels=8;this.sourceBytes=0;
        // Reuse providers instead of allocating new native providers for every load.
        this.provider=new F.gltfio$UbershaderProvider(engine);
        this.loader=new F.gltfio$AssetLoader(engine,this.provider);
        this.resources=new F.gltfio$ResourceLoader(engine,true);
        this.stb=new F.gltfio$StbProvider(engine);
        this.ktx=new F.gltfio$Ktx2Provider(engine);
        this.resources.addStbProvider('image/jpeg',this.stb);
        this.resources.addStbProvider('image/png',this.stb);
        this.resources.addKtx2Provider('image/ktx2',this.ktx);
    }
    load(uri,success,failure) {
        const id=this.nextId++,controller=new AbortController();
        const request={controller,success,failure};
        const url=new URL(uri,location.href);
        if(this.closed||this.clearing)throw new Error('Asset service is closed or clearing');
        if(url.origin!==location.origin||!['http:','https:'].includes(url.protocol))throw new Error('Assets must be served from this origin');
        if(this.models.size+this.pending.size>=this.maxModels)throw new Error('Model budget of 8 reached');
        this.pending.set(id,request);
        request.timer=setTimeout(()=>this.finishFailure(id,'Asset load timed out'),15000);
        this.fetchBytes(url,controller.signal).then(bytes=>{
            if(!this.pending.has(id))return;
            let asset=null,entities=[],entityVector=null;
            try {
                this.validate(bytes);
                asset=this.loader.createAsset(bytes);
                if(!asset)throw new Error('Filament rejected the GLB');
                const uris=asset._getResourceUris();
                try{if(uris.size())throw new Error('External resources are not supported; embed textures and buffers in the GLB');}
                finally{uris.delete();}
                if(!this.resources.loadResources(asset))throw new Error('Model resource decoding failed');
                entityVector=asset._getEntities();
                for(let i=0;i<entityVector.size();i++)entities.push(entityVector.get(i));
                const renderables=this.engine.getRenderableManager();
                for(const entity of entities){
                    // The tested ANGLE/NVIDIA path loses its context in imported
                    // ubershader shadow receiving. Keep casting and direct lighting.
                    if(renderables.hasComponent(entity)){
                        const instance=renderables.getInstance(entity);
                        try{renderables.setReceiveShadows(instance,false);}finally{instance.delete();}
                    }
                    this.scene.addEntity(entity);
                }
                const animator=asset.getInstance().getAnimator();
                asset.releaseSourceData();
                this.models.set(id,{asset,entities,entityVector,animator,bytes:bytes.byteLength});
                this.sourceBytes+=bytes.byteLength;
            }catch(error){
                for(const entity of entities)this.scene.remove(entity);
                if(asset)this.loader.destroyAsset(asset);
                if(entityVector)entityVector.delete();
                this.finishFailure(id,String(error.message||error));return;
            }
            clearTimeout(request.timer);this.pending.delete(id);
            // Ownership transfer is complete before entering application callbacks.
            request.delivered=true;request.success(id);
        }).catch(error=>{
            if(this.pending.has(id))this.finishFailure(id,String(error.message||error));
            else if(request.delivered)queueMicrotask(()=>{throw error;});
        });
        return id;
    }
    async fetchBytes(url,signal) {
        const response=await fetch(url,{signal});
        if(!response.ok)throw new Error(`HTTP ${response.status}: ${url.pathname}`);
        if(Number(response.headers.get('content-length'))>this.maxBytes)throw new Error('GLB exceeds 8 MiB source budget');
        const reader=response.body.getReader(),chunks=[];let length=0;
        try{
            for(;;){const {done,value}=await reader.read();if(done)break;
                length+=value.byteLength;if(length>this.maxBytes)throw new Error('GLB exceeds 8 MiB source budget');chunks.push(value);}
        }finally{await reader.cancel();reader.releaseLock();}
        const bytes=new Uint8Array(length);let offset=0;
        for(const chunk of chunks){bytes.set(chunk,offset);offset+=chunk.length;}
        return bytes;
    }
    validate(bytes) {
        if(bytes.length<20)throw new Error('Truncated GLB');
        const view=new DataView(bytes.buffer,bytes.byteOffset,bytes.byteLength);
        if(view.getUint32(0,true)!==0x46546c67||view.getUint32(4,true)!==2||view.getUint32(8,true)!==bytes.length)
            throw new Error('Expected a binary glTF 2.0 file with a valid length');
        const jsonLength=view.getUint32(12,true);
        if(view.getUint32(16,true)!==0x4e4f534a||jsonLength>bytes.length-20)throw new Error('Invalid GLB JSON chunk');
        const json=JSON.parse(new TextDecoder().decode(bytes.subarray(20,20+jsonLength)));
        if((json.nodes?.length||0)>2048||(json.meshes?.length||0)>512)throw new Error('GLB scene complexity exceeds budget');
        if([...json.buffers||[],...json.images||[]].some(resource=>resource.uri))throw new Error('GLB must embed all resources');
        // File size alone does not bound decoded geometry (e.g. sparse accessors).
        if((json.accessors||[]).some(accessor=>!Number.isInteger(accessor.count)||accessor.count<0||accessor.count>1000000))
            throw new Error('GLB accessor exceeds decoded element budget');
    }
    finishFailure(id,reason) {
        const request=this.pending.get(id);if(!request)return;
        this.pending.delete(id);clearTimeout(request.timer);request.controller.abort();
        try{request.failure(reason);}catch(error){queueMicrotask(()=>{throw error;});}
    }
    cancel(id){this.finishFailure(id,'cancelled');}
    get(id){const model=this.models.get(id);if(!model)throw new Error('Model handle is closed or scene was cleared');return model;}
    transform(id,x,y,z,yaw,scale){
        const model=this.get(id),c=Math.cos(yaw)*scale,s=Math.sin(yaw)*scale;
        const manager=this.engine.getTransformManager(),instance=manager.getInstance(model.asset.getRoot());
        try{manager.setTransform(instance,[c,0,-s,0,0,scale,0,0,s,0,c,0,x,y,z,1]);}finally{instance.delete();}
    }
    animationCount(id){return this.get(id).animator.getAnimationCount();}
    animate(id,clip,time){const model=this.get(id);if(!Number.isInteger(clip)||clip<0||clip>=this.animationCount(id)||!Number.isFinite(time)||time<0)throw new Error('Invalid animation');model.animator.applyAnimation(clip,time);model.animator.updateBoneMatrices();}
    release(id){
        const model=this.models.get(id);if(!model)return;
        this.models.delete(id);this.sourceBytes-=model.bytes;
        for(const entity of model.entities)this.scene.remove(entity);
        this.loader.destroyAsset(model.asset);
        model.entityVector.delete();
    }
    clear(){
        if(this.clearing)return;this.clearing=true;
        try{for(const id of [...this.pending.keys()])this.cancel(id);for(const id of [...this.models.keys()])this.release(id);}
        finally{this.clearing=false;}
    }
    close(){if(this.closed)return;this.closed=true;this.clear();this.resources.delete();this.loader.delete();this.provider.destroyMaterials();this.provider.delete();this.stb.delete();this.ktx.delete();}
}
