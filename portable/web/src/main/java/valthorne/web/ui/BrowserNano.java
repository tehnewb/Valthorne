package valthorne.web.ui;
import org.teavm.jso.*; import org.teavm.jso.typedarrays.Uint8Array; import org.teavm.interop.*; import org.lwjgl.nanovg.*; import java.nio.ByteBuffer;
public final class BrowserNano {
private BrowserNano() {}
public static final int NVG_ANTIALIAS=1,NVG_STENCIL_STROKES=2,NVG_ALIGN_LEFT=1,NVG_ALIGN_CENTER=2,NVG_ALIGN_RIGHT=4,NVG_ALIGN_TOP=8,NVG_ALIGN_MIDDLE=16,NVG_ALIGN_BOTTOM=32,NVG_ALIGN_BASELINE=64,NVG_ROUND=1,NVG_IMAGE_FLIPY=8;
public static long nvgCreate(int flags){return bnvgCreate(flags);}
public static void nvgClosePath(long vg){closePath((int)vg);}
@JSBody(params="vg",script="valthorneHost.nano.path(vg,'closePath',[]);") private static native void closePath(int vg);
public static void nvgGlobalAlpha(long vg,float alpha){globalAlpha((int)vg,alpha);}
@JSBody(params={"vg","alpha"},script="valthorneHost.nano.set(vg,'alpha',Math.max(0,Math.min(1,alpha)));") private static native void globalAlpha(int vg,float alpha);
@JSBody(params={"flags"},script="return valthorneHost.nano.create();") private static native int bnvgCreate(int flags);
public static void nvgDelete(long vg){bnvgDelete((int)vg);}
@JSBody(params={"vg"},script="valthorneHost.nano.delete(vg);") private static native void bnvgDelete(int vg);
public static void nvgBeginFrame(long vg,float width,float height,float ratio){bnvgBeginFrame((int)vg,width,height,ratio);}
@JSBody(params={"vg","width","height","ratio"},script="valthorneHost.nano.begin(vg,width,height,ratio);") private static native void bnvgBeginFrame(int vg,float width,float height,float ratio);
public static void nvgEndFrame(long vg){bnvgEndFrame((int)vg);}
@JSBody(params={"vg"},script="valthorneHost.nano.end(vg);") private static native void bnvgEndFrame(int vg);
public static void nvgSave(long vg){bnvgSave((int)vg);}
@JSBody(params={"vg"},script="valthorneHost.nano.save(vg);") private static native void bnvgSave(int vg);
public static void nvgRestore(long vg){bnvgRestore((int)vg);}
@JSBody(params={"vg"},script="valthorneHost.nano.restore(vg);") private static native void bnvgRestore(int vg);
public static void nvgResetTransform(long vg){bnvgResetTransform((int)vg);}
@JSBody(params={"vg"},script="valthorneHost.nano.resetTransform(vg);") private static native void bnvgResetTransform(int vg);
public static void nvgResetScissor(long vg){bnvgResetScissor((int)vg);}
@JSBody(params={"vg"},script="valthorneHost.nano.resetScissor(vg);") private static native void bnvgResetScissor(int vg);
public static void nvgBeginPath(long vg){bnvgBeginPath((int)vg);}
@JSBody(params={"vg"},script="valthorneHost.nano.beginPath(vg);") private static native void bnvgBeginPath(int vg);
public static void nvgFill(long vg){bnvgFill((int)vg);}
@JSBody(params={"vg"},script="valthorneHost.nano.fill(vg);") private static native void bnvgFill(int vg);
public static void nvgStroke(long vg){bnvgStroke((int)vg);}
@JSBody(params={"vg"},script="valthorneHost.nano.stroke(vg);") private static native void bnvgStroke(int vg);
public static void nvgTranslate(long vg,float x,float y){bnvgTranslate((int)vg,x,y);}
@JSBody(params={"vg","x","y"},script="valthorneHost.nano.translate(vg,x,y);") private static native void bnvgTranslate(int vg,float x,float y);
public static void nvgScale(long vg,float x,float y){bnvgScale((int)vg,x,y);}
@JSBody(params={"vg","x","y"},script="valthorneHost.nano.scale(vg,x,y);") private static native void bnvgScale(int vg,float x,float y);
public static void nvgScissor(long vg,float x,float y,float w,float h){bnvgScissor((int)vg,x,y,w,h);}
@JSBody(params={"vg","x","y","w","h"},script="valthorneHost.nano.scissor(vg,x,y,w,h,false);") private static native void bnvgScissor(int vg,float x,float y,float w,float h);
public static void nvgIntersectScissor(long vg,float x,float y,float w,float h){bnvgIntersectScissor((int)vg,x,y,w,h);}
@JSBody(params={"vg","x","y","w","h"},script="valthorneHost.nano.scissor(vg,x,y,w,h,true);") private static native void bnvgIntersectScissor(int vg,float x,float y,float w,float h);
public static void nvgRect(long vg,float x,float y,float w,float h){bnvgRect((int)vg,x,y,w,h);}
@JSBody(params={"vg","x","y","w","h"},script="valthorneHost.nano.path(vg,'rect',[x,y,w,h]);") private static native void bnvgRect(int vg,float x,float y,float w,float h);
public static void nvgRoundedRect(long vg,float x,float y,float w,float h,float r){bnvgRoundedRect((int)vg,x,y,w,h,r);}
@JSBody(params={"vg","x","y","w","h","r"},script="valthorneHost.nano.roundedRect(vg,x,y,w,h,r);") private static native void bnvgRoundedRect(int vg,float x,float y,float w,float h,float r);
public static void nvgCircle(long vg,float x,float y,float r){bnvgCircle((int)vg,x,y,r);}
@JSBody(params={"vg","x","y","r"},script="valthorneHost.nano.circle(vg,x,y,r);") private static native void bnvgCircle(int vg,float x,float y,float r);
public static void nvgMoveTo(long vg,float x,float y){bnvgMoveTo((int)vg,x,y);}
@JSBody(params={"vg","x","y"},script="valthorneHost.nano.path(vg,'moveTo',[x,y]);") private static native void bnvgMoveTo(int vg,float x,float y);
public static void nvgLineTo(long vg,float x,float y){bnvgLineTo((int)vg,x,y);}
@JSBody(params={"vg","x","y"},script="valthorneHost.nano.path(vg,'lineTo',[x,y]);") private static native void bnvgLineTo(int vg,float x,float y);
public static void nvgFontFace(long vg,String value){bnvgFontFace((int)vg,value);}
@JSBody(params={"vg","value"},script="valthorneHost.nano.set(vg,'face',value);") private static native void bnvgFontFace(int vg,String value);
public static void nvgFontSize(long vg,float value){bnvgFontSize((int)vg,value);}
@JSBody(params={"vg","value"},script="valthorneHost.nano.set(vg,'size',value);") private static native void bnvgFontSize(int vg,float value);
public static void nvgTextAlign(long vg,int value){bnvgTextAlign((int)vg,value);}
@JSBody(params={"vg","value"},script="valthorneHost.nano.set(vg,'align',value);") private static native void bnvgTextAlign(int vg,int value);
public static void nvgStrokeWidth(long vg,float value){bnvgStrokeWidth((int)vg,value);}
@JSBody(params={"vg","value"},script="valthorneHost.nano.set(vg,'width',value);") private static native void bnvgStrokeWidth(int vg,float value);
public static void nvgLineCap(long vg,int value){bnvgLineCap((int)vg,value);}
@JSBody(params={"vg","value"},script="valthorneHost.nano.set(vg,'cap',['butt','round','square'][value]);") private static native void bnvgLineCap(int vg,int value);
public static void nvgLineJoin(long vg,int value){bnvgLineJoin((int)vg,value);}
@JSBody(params={"vg","value"},script="valthorneHost.nano.set(vg,'join',['miter','round','bevel','miter','bevel'][value]);") private static native void bnvgLineJoin(int vg,int value);
public static void nvgFillColor(long vg,NVGColor c){color((int)vg,"fill",c.r(),c.g(),c.b(),c.a());}
public static void nvgStrokeColor(long vg,NVGColor c){color((int)vg,"stroke",c.r(),c.g(),c.b(),c.a());}
@JSBody(params={"vg","slot","r","g","b","a"},script="valthorneHost.nano.color(vg,slot,r,g,b,a);") private static native void color(int vg,String slot,float r,float g,float b,float a);
public static float nvgText(long vg,float x,float y,String text){return bnvgText((int)vg,x,y,text);}
@JSBody(params={"vg","x","y","text"},script="return valthorneHost.nano.text(vg,x,y,text);") private static native float bnvgText(int vg,float x,float y,String text);
public static float nvgTextBounds(long vg,float x,float y,String text,float[] bounds){float[] values=bounds((int)vg,x,y,text);if(bounds!=null)System.arraycopy(values,0,bounds,0,4);return values[4];}
@JSBody(params={"vg","x","y","text"},script="return valthorneHost.nano.bounds(vg,x,y,text);") private static native float[] bounds(int vg,float x,float y,String text);
public static void nvgTextMetrics(long vg,float[] ascent,float[] descent,float[] height){float[] v=metrics((int)vg);if(ascent!=null)ascent[0]=v[0];if(descent!=null)descent[0]=v[1];if(height!=null)height[0]=v[2];}
@JSBody(params="vg",script="return valthorneHost.nano.metrics(vg);") private static native float[] metrics(int vg);
public static int nvgCreateFont(long vg,String name,String path){return FontLoader.load((int)vg,name,path);}
private static final class FontLoader {
@Async static native int load(int vg,String name,String path);
private static void load(int vg,String name,String path,AsyncCallback<Integer> callback){fetch(vg,name,path,(value,error)->{if(error!=null)callback.error(new IllegalArgumentException(error));else callback.complete(value);});}
@JSFunctor interface Loaded extends JSObject {void accept(int value,String error);}
@JSBody(params={"vg","name","path","callback"},script="valthorneHost.nano.fontFace(vg,name,path).then(id=>callback(id,null),error=>callback(-1,String(error)));") private static native void fetch(int vg,String name,String path,Loaded callback);
}
public static int nvgCreateImageRGBA(long vg,int width,int height,int flags,ByteBuffer data){Uint8Array bytes=Uint8Array.create(data.remaining());for(int i=0;i<data.remaining();i++)bytes.set(i,(short)(data.get(data.position()+i)&255));return image((int)vg,width,height,flags,bytes);}
@JSBody(params={"vg","w","h","flags","bytes"},script="return valthorneHost.nano.image(vg,w,h,flags,bytes);") private static native int image(int vg,int w,int h,int flags,Uint8Array bytes);
public static void nvgDeleteImage(long vg,int image){bnvgDeleteImage((int)vg,image);}
@JSBody(params={"vg","image"},script="valthorneHost.nano.deleteImage(vg,image);") private static native void bnvgDeleteImage(int vg,int image);
public static NVGPaint nvgImagePattern(long vg,float x,float y,float w,float h,float angle,int image,float alpha,NVGPaint target){target.x=x;target.y=y;target.width=w;target.height=h;target.angle=angle;target.image=image;target.alpha=alpha;return target;}
public static void nvgFillPaint(long vg,NVGPaint p){paint((int)vg,p.x,p.y,p.width,p.height,p.angle,p.image,p.alpha);}
@JSBody(params={"vg","x","y","w","h","angle","image","alpha"},script="valthorneHost.nano.paint(vg,x,y,w,h,angle,image,alpha);") private static native void paint(int vg,float x,float y,float w,float h,float angle,int image,float alpha);
}
