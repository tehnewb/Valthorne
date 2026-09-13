export class BrowserParticles {
    constructor(host){this.host=host;this.items=[];this.materials=new Map();this.material=host.engine.createMaterial('particle.filamat');this.nextSeed=1;this.lightCount=0;}
    random(){this.nextSeed=(Math.imul(this.nextSeed,1664525)+1013904223)>>>0;return this.nextSeed/4294967296;}
    burst(x,y,z,rgb,count,physics,illuminate){
        if(![x,y,z,count].every(Number.isFinite)||!Number.isInteger(count)||count<0||count>64)throw new Error('Invalid particle burst');
        const h=this.host,{F,engine:e}=h;
        // A fixed eight-color palette bounds native material instances across arbitrary input colors.
        const palette=[0x65d9ff,0xff6655,0xffca66,0x99ff88,0xbb88ff,0xffffff,0xff88bb,0x88aaff];
        rgb=palette.reduce((best,color)=>{const distance=c=>((c>>16&255)-(rgb>>16&255))**2+((c>>8&255)-(rgb>>8&255))**2+((c&255)-(rgb&255))**2;return distance(color)<distance(best)?color:best;},palette[0]);
        let material=this.materials.get(rgb);
        if(!material){material=this.material.createInstance();material.setFloat4Parameter('color',[...h.color(rgb).map(c=>c*.5),.5]);this.materials.set(rgb,material);}
        for(let i=0;i<count&&this.items.length<64;i++){
            if(physics&&h.objects.length>=120)break;
            const velocity=[(this.random()-.5)*5,this.random()*4+1,(this.random()-.5)*5];
            let object=null,entity;
            if(physics){object=h.createBox(x,y,z,[.045,.045,.045],rgb,true,false,material);entity=object.entity;h.changeBody(object.id,0,...velocity);}
            else{entity=F.EntityManager.get().create();F.RenderableManager.Builder(1).boundingBox({center:[0,0,0],halfExtent:[1,1,1]}).material(0,material).geometry(0,F.RenderableManager$PrimitiveType.TRIANGLES,h.mesh.vb,h.mesh.ib).castShadows(false).receiveShadows(false).build(e,entity);h.scene.addEntity(entity);}
            let light=null;
            if(illuminate&&this.lightCount<8){light=F.EntityManager.get().create();F.LightManager.Builder(F.LightManager$Type.POINT).color(h.color(rgb)).intensity(3500).falloff(2).position([x,y,z]).castShadows(false).build(e,light);h.scene.addEntity(light);this.lightCount++;}
            const particle={object,entity,light,x,y,z,velocity,remaining:.6+this.random()*.6};this.items.push(particle);this.transform(particle);
        }
    }
    transform(p){
        const h=this.host;
        if(p.object){p.x=h.bodyComponent(p.object.id,0);p.y=h.bodyComponent(p.object.id,1);p.z=h.bodyComponent(p.object.id,2);}
        else{const manager=h.engine.getTransformManager(),instance=manager.getInstance(p.entity);try{manager.setTransform(instance,[.045,0,0,0,0,.045,0,0,0,0,.045,0,p.x,p.y,p.z,1]);}finally{instance.delete();}}
        if(p.light){const manager=h.engine.getLightManager(),instance=manager.getInstance(p.light);try{manager.setPosition(instance,[p.x,p.y,p.z]);manager.setIntensity(instance,3500*Math.min(1,p.remaining*3));}finally{instance.delete();}}
    }
    step(dt){for(let i=this.items.length-1;i>=0;i--){const p=this.items[i];p.remaining-=dt;if(p.remaining<=0){this.release(i);continue;}if(!p.object){p.velocity[1]-=9.81*dt;p.x+=p.velocity[0]*dt;p.y+=p.velocity[1]*dt;p.z+=p.velocity[2]*dt;}this.transform(p);}}
    release(index){const p=this.items[index],h=this.host;this.items.splice(index,1);if(p.object)h.releaseBody(p.object.id);else{h.scene.remove(p.entity);h.engine.destroyEntity(p.entity);h.F.EntityManager.get().destroy(p.entity);}if(p.light){h.scene.remove(p.light);h.engine.destroyEntity(p.light);h.F.EntityManager.get().destroy(p.light);this.lightCount--;}}
    clear(){while(this.items.length)this.release(this.items.length-1);}
    close(){this.clear();for(const material of this.materials.values())this.host.engine.destroyMaterialInstance(material);this.materials.clear();this.host.engine.destroyMaterial(this.material);}
}
