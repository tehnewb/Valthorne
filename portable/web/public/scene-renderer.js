// Filament implementation of Valthorne's existing Scene3D rendering API.
const isMobile = () => typeof matchMedia === 'function' && (matchMedia('(pointer: coarse)').matches || matchMedia('(hover: none)').matches);
const getPixelRatio = () => {
    const deviceRatio = Number(window.devicePixelRatio) || 1;
    const maxRatio = isMobile() ? 3 : 2;
    return Math.max(1, Math.min(deviceRatio, maxRatio));
};
export class BrowserSceneRenderer {
    constructor(host){
        this.host=host;this.F=host.F;this.engine=host.engine;this.entries=[];this.lights=[];this.meshes=new Set();this.closed=false;
        const F=this.F,e=this.engine;
        this.scene=e.createScene();this.view=e.createView();this.cameraEntity=F.EntityManager.get().create();this.camera=e.createCamera(this.cameraEntity);
        this.view.setScene(this.scene);this.view.setCamera(this.camera);this.camera.setExposure(4,1/60,100);
        this.materials=['surface','alpha','glass'].map(name=>e.createMaterial('engine-'+name+'.filamat'));
        this.white=F.Texture.Builder().width(1).height(1).levels(1).sampler(F.Texture$Sampler.SAMPLER_2D).format(F.Texture$InternalFormat.RGBA8).build(e);
        this.white.setImage(e,0,F.PixelBuffer(new Uint8Array([255,255,255,255]),F.PixelDataFormat.RGBA,F.PixelDataType.UBYTE));
        this.sampler=new F.TextureSampler(F.MinFilter.LINEAR,F.MagFilter.LINEAR,F.WrapMode.REPEAT);
        const sh=new Float32Array(27);sh.set([.7,.8,1]);
        this.ambient=F.IndirectLight.Builder().irradianceSh(3,sh).intensity(8000).build(e);this.scene.setIndirectLight(this.ambient);
        this.quality(1);this.antialias(true);
    }
    check(){if(this.closed)throw new Error('Renderer is closed');}
    mesh(data,indices,count,bounds){
        this.check();const F=this.F,e=this.engine;
        const p=new Float32Array(count*3),n=new Float32Array(count*3),c=new Float32Array(count*4),uv=new Float32Array(count*2);
        for(let i=0;i<count;i++){for(let j=0;j<3;j++){p[i*3+j]=data[i*12+j];n[i*3+j]=data[i*12+3+j];}for(let j=0;j<4;j++)c[i*4+j]=data[i*12+6+j];uv[i*2]=data[i*12+10];uv[i*2+1]=data[i*12+11];}
        const builder=new F.SurfaceOrientation$Builder();builder.vertexCount(count);builder.normals(n,0);const orientation=builder.build();
        const vb=F.VertexBuffer.Builder().vertexCount(count).bufferCount(4)
            .attribute(F.VertexAttribute.POSITION,0,F.VertexBuffer$AttributeType.FLOAT3,0,12)
            .attribute(F.VertexAttribute.TANGENTS,1,F.VertexBuffer$AttributeType.FLOAT4,0,16)
            .attribute(F.VertexAttribute.COLOR,2,F.VertexBuffer$AttributeType.FLOAT4,0,16)
            .attribute(F.VertexAttribute.UV0,3,F.VertexBuffer$AttributeType.FLOAT2,0,8).build(e);
        vb.setBufferAt(e,0,p);vb.setBufferAt(e,1,orientation.getQuatsFloat4(count));vb.setBufferAt(e,2,c);vb.setBufferAt(e,3,uv);orientation.delete();
        const ib=F.IndexBuffer.Builder().indexCount(indices.length).bufferType(count<=65535?F.IndexBuffer$IndexType.USHORT:F.IndexBuffer$IndexType.UINT).build(e);
        ib.setBuffer(e,count<=65535?new Uint16Array(indices):new Uint32Array(indices));
        const mesh={vb,ib,bounds:{center:bounds.slice(0,3),halfExtent:bounds.slice(3,6)}};this.meshes.add(mesh);return mesh;
    }
    releaseMesh(mesh){if(!this.meshes.delete(mesh))return;this.engine.destroyVertexBuffer(mesh.vb);this.engine.destroyIndexBuffer(mesh.ib);}
    entry(index,mesh,kind,transform,state,visible,texture=0){
        this.check();const F=this.F,e=this.engine;let item=this.entries[index];
        if(item&&(item.mesh!==mesh||item.kind!==kind)){this.releaseEntry(item);item=null;}
        if(!item){
            const material=this.materials[kind].createInstance(),entity=F.EntityManager.get().create();
            material.setTextureParameter('albedo',this.white,this.sampler);
            F.RenderableManager.Builder(1).boundingBox(mesh.bounds).material(0,material)
                .geometry(0,F.RenderableManager$PrimitiveType.TRIANGLES,mesh.vb,mesh.ib).build(e,entity);
            item={entity,material,mesh,kind,transform:new Float32Array(16).fill(NaN),state:new Float32Array(state.length).fill(NaN),visible:false,light:null,lightState:null};this.entries[index]=item;
        }
        const image=texture?this.host.graphics.filament(texture):null;
        let textureChanged=item.texture!==texture||item.textureVersion!==image?.version;
        if(textureChanged){
            const filters={9728:'NEAREST',9729:'LINEAR',9984:'NEAREST_MIPMAP_NEAREST',9985:'LINEAR_MIPMAP_NEAREST',9986:'NEAREST_MIPMAP_LINEAR',9987:'LINEAR_MIPMAP_LINEAR'};
            const sampler=image?new F.TextureSampler(F.MinFilter[filters[image.min]],F.MagFilter[filters[image.mag]],F.WrapMode.CLAMP_TO_EDGE):this.sampler;
            try{item.material.setTextureParameter('albedo',image?.native||this.white,sampler);}finally{if(image)sampler.delete();}
            item.texture=texture;item.textureVersion=image?.version;
        }
        if(visible!==item.visible){if(visible)this.scene.addEntity(item.entity);else this.scene.remove(item.entity);item.visible=visible;}
        let changed=false;for(let i=0;i<16;i++)if(item.transform[i]!==transform[i]){changed=true;break;}
        if(changed){const manager=e.getTransformManager(),instance=manager.getInstance(item.entity);try{manager.setTransform(instance,transform);}finally{instance.delete();}item.transform.set(transform);}
        let materialChanged=false;for(let i=0;i<state.length;i++)if(item.state[i]!==state[i]){materialChanged=true;break;}
        if(materialChanged){
            const m=item.material;m.setFloat4Parameter('tint',state.subarray(0,4));m.setFloat3Parameter('emission',state.subarray(4,7));
            m.setFloatParameter('roughness',state[7]);m.setFloatParameter('metallic',state[8]);m.setFloatParameter('cutoff',state[9]);
            m.setDepthWrite(!!state[16]);m.setDepthCulling(!!state[17]);m.setDoubleSided(!state[18]);
            if(kind===2){m.setFloatParameter('transmission',state[12]);m.setFloatParameter('ior',state[13]);m.setFloatParameter('thickness',mesh.bounds.halfExtent[2]*2);}
            const manager=e.getRenderableManager(),instance=manager.getInstance(item.entity);
            try{manager.setCastShadows(instance,!!state[10]);manager.setReceiveShadows(instance,!!state[11]);}finally{instance.delete();}
            item.state.set(state);
        }
        if(state[14]>0&&state[15]&&visible&&(state[19]>0||state[20]>0||state[21]>0)){
            const center=mesh.bounds.center,x=center[0],y=center[1],z=center[2];
            const light=item.lightState||(item.lightState=new Float32Array(9));
            light[0]=transform[0]*x+transform[4]*y+transform[8]*z+transform[12];light[1]=transform[1]*x+transform[5]*y+transform[9]*z+transform[13];light[2]=transform[2]*x+transform[6]*y+transform[10]*z+transform[14];
            light[3]=state[19];light[4]=state[20];light[5]=state[21];light[6]=state[14]*1000;light[7]=30;light[8]=1;
            item.light=this.syncLight(item.light,light);
        }else if(item.light){this.releaseLight(item.light);item.light=null;}
        return changed||materialChanged||textureChanged;
    }
    syncLight(light,data){
        const F=this.F,e=this.engine;
        if(light&&light.data[8]!==data[8]){this.releaseLight(light);light=null;}
        if(!light){const entity=F.EntityManager.get().create();F.LightManager.Builder(F.LightManager$Type.POINT).color([1,1,1]).intensity(0).falloff(10).castShadows(!!data[8]).build(e,entity);this.scene.addEntity(entity);light={entity,data:new Float32Array(9).fill(NaN)};light.position=light.data.subarray(0,3);light.color=light.data.subarray(3,6);}
        let changed=false;for(let i=0;i<9;i++)if(light.data[i]!==data[i]){changed=true;break;}
        if(changed){light.data.set(data);const manager=e.getLightManager(),instance=manager.getInstance(light.entity);try{manager.setPosition(instance,light.position);manager.setColor(instance,light.color);manager.setIntensity(instance,data[6]);manager.setFalloff(instance,data[7]);}finally{instance.delete();}}
        return light;
    }
    light(index,data){this.lights[index]=this.syncLight(this.lights[index],data);}
    trim(entries,lights){while(this.entries.length>entries)this.releaseEntry(this.entries.pop());while(this.lights.length>lights)this.releaseLight(this.lights.pop());}
    releaseLight(light){this.scene.remove(light.entity);this.engine.destroyEntity(light.entity);this.F.EntityManager.get().destroy(light.entity);}
    releaseEntry(item){if(item.light)this.releaseLight(item.light);this.scene.remove(item.entity);this.engine.destroyEntity(item.entity);this.F.EntityManager.get().destroy(item.entity);this.engine.destroyMaterialInstance(item.material);}
    lightCount(){return this.lights.length+this.entries.reduce((count,item)=>count+(item.light?1:0),0);}
    render(projection,model,near,far){
        this.check();const h=this.host,canvas=h.platform.canvas||document.querySelector('#scene');
        h.graphics.context();
        h.graphics.resize();
        const ratio=getPixelRatio(),w=Math.max(1,Math.round(canvas.clientWidth*ratio)),height=Math.max(1,Math.round(canvas.clientHeight*ratio));
        if(canvas.width!==w||canvas.height!==height){canvas.width=w;canvas.height=height;}
        this.view.setViewport([0,0,w,height]);this.camera.setCustomProjection(projection,near,far);this.camera.setModelMatrix(model);
        h.renderer.render(h.swap,this.view);
        // Preserve the last complete UI frame while Filament performs synchronous
        // mesh uploads. The application redraws the overlay before the browser's
        // next presentation, so users never see an intermediate cleared canvas.
        h.graphics.clearOverlay();
        h.frames++;globalThis.valthorneReady=true;
    }
    exposure(value){this.camera.setExposure(4,1/60,100*value);}
    environment(value){this.ambient.setIntensity(value);}
    antialias(enabled){this.view.setAntiAliasing(enabled?this.F.View$AntiAliasing.FXAA:this.F.View$AntiAliasing.NONE);this.view.setTemporalAntiAliasingOptions({enabled});}
    quality(value){const F=this.F;this.view.setAmbientOcclusionOptions({enabled:true,quality:value===0?F.View$QualityLevel.HIGH:F.View$QualityLevel.ULTRA,resolution:value===0?.5:1,lowPassFilter:F.View$QualityLevel.HIGH,upsampling:F.View$QualityLevel.HIGH,radius:.5,bias:.02,power:1});this.view.setMultiSampleAntiAliasingOptions({enabled:value>=2,sampleCount:value===3?8:4});}
    close(){if(this.closed)return;this.trim(0,0);for(const mesh of this.meshes)this.releaseMesh(mesh);const e=this.engine;e.destroyView(this.view);e.destroyScene(this.scene);e.destroyCameraComponent(this.cameraEntity);this.F.EntityManager.get().destroy(this.cameraEntity);for(const material of this.materials)e.destroyMaterial(material);e.destroyTexture(this.white);e.destroyIndirectLight(this.ambient);this.sampler.delete();this.closed=true;this.onClose?.();}
}
