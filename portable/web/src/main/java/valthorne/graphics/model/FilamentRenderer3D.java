package valthorne.graphics.model;

import java.util.*;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.teavm.jso.*;
import org.teavm.jso.typedarrays.Float32Array;
import valthorne.Window;
import valthorne.camera.Camera3D;
import valthorne.graphics.Color;

/** Browser Filament backend for the existing scene API. Source models remain caller-owned. */
public final class FilamentRenderer3D implements AutoCloseable {
    public enum Quality {PERFORMANCE,INTERACTIVE,HIGH,ULTRA}
    private final JSObject handle;
    private final BrowserSceneCollector collector=new BrowserSceneCollector();
    private final OcclusionCuller3D occlusion=new OcclusionCuller3D();
    private final IdentityHashMap<Model3D,JSObject> meshes=new IdentityHashMap<>();
    private final Set<Model3D> used=Collections.newSetFromMap(new IdentityHashMap<>());
    private final Float32Array transform=Float32Array.create(16),projection=Float32Array.create(16),cameraModel=Float32Array.create(16),state=Float32Array.create(22),light=Float32Array.create(9);
    private final Matrix4f inverseView=new Matrix4f();
    private final long[] profileNanos=new long[4];
    private boolean closed,occlusionEnabled=true;
    private long uploadedSourceVertices,uploadedUniqueVertices;
    private int occludedCount,offscreenCount,updatedEntryCount;
    public FilamentRenderer3D(){handle=create();}
    private void check(){if(closed)throw new IllegalStateException("Renderer is closed");}
    public long getUploadedSourceVertices(){return uploadedSourceVertices;}
    public long getUploadedUniqueVertices(){return uploadedUniqueVertices;}
    public long getProfileNanos(int phase){return profileNanos[phase];}
    public void setOcclusionCullingEnabled(boolean enabled){check();occlusionEnabled=enabled;}
    public int getOccludedCount(){return occludedCount;}
    public int getOffscreenCount(){return offscreenCount;}
    public int getUpdatedEntryCount(){return updatedEntryCount;}
    public int getCachedMeshCount(){return meshes.size();}
    public int getPointLightCount(){check();return lightCount(handle);}
    public void setExposure(float value){check();if(!(value>0)||!Float.isFinite(value))throw new IllegalArgumentException("Exposure must be positive and finite");option(handle,0,value);}
    public void setEnvironmentIntensity(float value){check();if(value<0||!Float.isFinite(value))throw new IllegalArgumentException("Environment intensity must be finite and nonnegative");option(handle,1,value);}
    public void setAntiAliasing(boolean enabled){check();option(handle,2,enabled?1:0);}
    public void setQuality(Quality quality){check();option(handle,3,Objects.requireNonNull(quality).ordinal());}
    public void invalidate(){check();invalidate(handle);}
    public void render(Scene3D source,Camera3D camera){
        check();Objects.requireNonNull(source);Objects.requireNonNull(camera);
        camera.rebuild(Window.getWidth(),Window.getHeight());copy(camera.getProjection(),projection);copy(inverseView.set(camera.getView()).invert(),cameraModel);
        var snapshot=collector.capture(source);
        boolean reject=occlusionEnabled;
        for(var item:snapshot.instances)if(item.material().getTransmission()>.01f){reject=false;break;}
        if(reject){occlusion.begin(camera.getCombined(),Window.getWidth(),Window.getHeight());for(var item:snapshot.instances)occlusion.addOccluder(item.model(),item.transform(),item.material());}
        occludedCount=offscreenCount=updatedEntryCount=0;used.clear();int index=0;
        for(var item:snapshot.instances){
            Model3D model=item.model();if(model.getTriangleCount()==0)continue;
            Material3D material=item.material();
            boolean visible=true;
            if(reject&&!material.isCastsShadow()&&(!material.isEmissionLightEnabled()||material.getEmissionStrength()*material.getEmissive().a()<=0)){
                var visibility=occlusion.test(model,item.transform());visible=visibility==OcclusionCuller3D.Visibility.VISIBLE;
                if(visibility==OcclusionCuller3D.Visibility.OCCLUDED)occludedCount++;
                if(visibility==OcclusionCuller3D.Visibility.OFFSCREEN)offscreenCount++;
            }
            JSObject mesh=meshes.get(model);if(mesh==null){mesh=upload(model);meshes.put(model,mesh);}used.add(model);
            copy(item.transform(),transform);pack(material,state);
            int kind=material.getRenderPass()==RenderPass3D.TRANSLUCENT?1:material.getTransmission()>.01f?2:0;
            if(entry(handle,index++,mesh,kind,transform,state,visible,material.getTexture()==null?0:material.getTexture().getTextureID()))updatedEntryCount++;
        }
        int lights=0;
        for(PointLight3D sourceLight:source.getLights()){
            Vector3f p=sourceLight.getPosition();Color c=sourceLight.getColor();float power=sourceLight.getIntensity()*1000;
            if(!p.isFinite()||!Float.isFinite(power)||!Float.isFinite(c.r())||!Float.isFinite(c.g())||!Float.isFinite(c.b())||c.r()<0||c.g()<0||c.b()<0)throw new IllegalArgumentException("Point light position, RGB and intensity must be finite and nonnegative");
            if(power==0||c.r()+c.g()+c.b()==0)continue;
            light.set(0,p.x);light.set(1,p.y);light.set(2,p.z);light.set(3,linear(c.r()));light.set(4,linear(c.g()));light.set(5,linear(c.b()));light.set(6,power);light.set(7,sourceLight.getRange());light.set(8,sourceLight.isCastsShadows()?1:0);
            light(handle,lights++,light);
        }
        trim(handle,index,lights);
        var iterator=meshes.entrySet().iterator();while(iterator.hasNext()){var mesh=iterator.next();if(!used.contains(mesh.getKey())){releaseMesh(handle,mesh.getValue());iterator.remove();}}
        draw(handle,projection,cameraModel,camera.getNear(),camera.getFar());
    }
    private static void copy(Matrix4f matrix,Float32Array out){for(int c=0;c<4;c++)for(int r=0;r<4;r++)out.set(c*4+r,matrix.get(c,r));}
    private static float linear(float v){return v<=.04045f?v/12.92f:(float)Math.pow((v+.055)/1.055,2.4);}
    private static void pack(Material3D m,Float32Array out){
        Color t=m.getTint(),e=m.getEmissive();float power=m.getEmissionStrength()*e.a();
        out.set(0,linear(t.r()));out.set(1,linear(t.g()));out.set(2,linear(t.b()));out.set(3,t.a());
        out.set(4,linear(e.r())*power*200);out.set(5,linear(e.g())*power*200);out.set(6,linear(e.b())*power*200);
        out.set(7,m.getRoughness());out.set(8,m.getMetallic());out.set(9,m.getAlphaCutoff());out.set(10,m.isCastsShadow()?1:0);out.set(11,m.isReceivesShadow()?1:0);
        out.set(12,m.getTransmission());out.set(13,m.getIndexOfRefraction());out.set(14,power);out.set(15,m.isEmissionLightEnabled()?1:0);
        out.set(16,m.isDepthWrite()?1:0);out.set(17,m.isDepthTest()?1:0);out.set(18,m.isCullBackFaces()?1:0);
        out.set(19,linear(e.r()));out.set(20,linear(e.g()));out.set(21,linear(e.b()));
    }
    private JSObject upload(Model3D model){
        int count=model.getTriangleCount()*3;float[] data=new float[count*12];int at=0;
        for(Model3D.Triangle t:model.triangles()){
            at=vertex(data,at,t.a,t.normalA,t.color,t.uvA.x,t.uvA.y);at=vertex(data,at,t.b,t.normalB,t.color,t.uvB.x,t.uvB.y);at=vertex(data,at,t.c,t.normalC,t.color,t.uvC.x,t.uvC.y);
        }
        int[] indices=new int[count];int unique=VertexCompaction3D.compact(data,12,indices);var b=model.localBounds();
        float[] bounds={(b.minX+b.maxX)/2,(b.minY+b.maxY)/2,(b.minZ+b.maxZ)/2,Math.max(.001f,(b.maxX-b.minX)/2),Math.max(.001f,(b.maxY-b.minY)/2),Math.max(.001f,(b.maxZ-b.minZ)/2)};
        JSObject result=mesh(handle,data,indices,unique,bounds);uploadedSourceVertices+=count;uploadedUniqueVertices+=unique;return result;
    }
    private static int vertex(float[] data,int i,Vector3f p,Vector3f n,Color c,float u,float v){
        if(!p.isFinite()||!n.isFinite()||n.lengthSquared()==0)throw new IllegalArgumentException("Mesh positions must be finite and normals finite and nonzero");
        data[i++]=p.x;data[i++]=p.y;data[i++]=p.z;data[i++]=n.x;data[i++]=n.y;data[i++]=n.z;data[i++]=linear(c.r());data[i++]=linear(c.g());data[i++]=linear(c.b());data[i++]=c.a();data[i++]=u;data[i++]=v;return i;
    }
    @Override public void close(){if(closed)return;close(handle);meshes.clear();used.clear();collector.clear();closed=true;}
    @JSBody(script="return valthorneHost.createSceneRenderer();") private static native JSObject create();
    @JSBody(params={"h","op","value"},script="if(op===0)h.exposure(value);else if(op===1)h.environment(value);else if(op===2)h.antialias(!!value);else h.quality(value);") private static native void option(JSObject h,int op,float value);
    @JSBody(params="h",script="h.entries.forEach(item=>{item.state.fill(NaN);item.transform.fill(NaN);});") private static native void invalidate(JSObject h);
    @JSBody(params={"h","data","indices","unique","bounds"},script="return h.mesh(data,indices,unique,bounds);") private static native JSObject mesh(JSObject h,float[] data,int[] indices,int unique,float[] bounds);
    @JSBody(params={"h","index","mesh","kind","transform","state","visible","texture"},script="return h.entry(index,mesh,kind,transform,state,visible,texture);") private static native boolean entry(JSObject h,int index,JSObject mesh,int kind,Float32Array transform,Float32Array state,boolean visible,int texture);
    @JSBody(params={"h","index","data"},script="h.light(index,data);") private static native void light(JSObject h,int index,Float32Array data);
    @JSBody(params="h",script="return h.lightCount();") private static native int lightCount(JSObject h);
    @JSBody(params={"h","entries","lights"},script="h.trim(entries,lights);") private static native void trim(JSObject h,int entries,int lights);
    @JSBody(params={"h","mesh"},script="h.releaseMesh(mesh);") private static native void releaseMesh(JSObject h,JSObject mesh);
    @JSBody(params={"h","projection","model","near","far"},script="h.render(projection,model,near,far);") private static native void draw(JSObject h,Float32Array projection,Float32Array model,float near,float far);
    @JSBody(params="h",script="h.close();") private static native void close(JSObject h);
}
