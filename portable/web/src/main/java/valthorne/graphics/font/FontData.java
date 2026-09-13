package valthorne.graphics.font;
import java.nio.ByteBuffer;
import java.util.IdentityHashMap;
import org.lwjgl.stb.STBTTFontinfo;
import org.teavm.jso.*;
import org.teavm.jso.typedarrays.*;
import org.teavm.interop.*;
import valthorne.graphics.texture.TextureData;
import valthorne.web.BrowserIO;

/** Browser font atlas with the original data API; native STB handles are absent on web. */
public record FontData(TextureData textureData,int fontSize,char startChar,char endChar,int atlasWidth,int atlasHeight,float ascent,float descent,float scale,float baseline,float lineHeight,Glyph[] glyphs,STBTTFontinfo stbInfo,ByteBuffer stbFontBuffer){
 private static final IdentityHashMap<FontData,Atlas> atlases=new IdentityHashMap<>();
 public static FontData load(byte[] bytes,int size,int start,int count){
  if(bytes==null||bytes.length==0)throw new IllegalArgumentException("fontBytes cannot be null/empty");if(size<=0)throw new IllegalArgumentException("fontSize must be > 0");if(count<=0)throw new IllegalArgumentException("numChars must be > 0");
  return Decoder.bake(bytes,size,start,count);
 }
 public static FontData load(String path,int size,int start,int count){return load(BrowserIO.read(path),size,start,count);}
 public Font asFont(){return new Font(this);}
 public void dispose(){Atlas atlas=atlases.remove(this);if(atlas!=null)Decoder.release(atlas);if(textureData!=null)textureData.dispose();}
 public Glyph glyph(char c){int index=c-startChar;return index<0||index>=glyphs.length?null:glyphs[index];}
 public float getKerningAdvance(char left,char right){Atlas atlas=atlases.get(this);return left==0||right==0||atlas==null?0:Decoder.kern(atlas,left,right);}
 public boolean contains(char c){return c>=startChar&&c-startChar<glyphs.length;}
 private interface Atlas extends JSObject{@JSProperty int getSide();@JSProperty Uint8Array getPixels();@JSProperty Float32Array getGlyphs();@JSProperty float getAscent();@JSProperty float getDescent();@JSProperty float getLineHeight();@JSProperty float getScale();}
 private static final class Decoder {
  @Async static native FontData bake(byte[] bytes,int size,int start,int count);
  private static void bake(byte[] bytes,int size,int start,int count,AsyncCallback<FontData> callback){
   Uint8Array input=Uint8Array.create(bytes.length);for(int i=0;i<bytes.length;i++)input.set(i,(short)(bytes[i]&255));
   load(input,size,start,count,(atlas,error)->{if(error!=null){callback.error(new IllegalArgumentException(error));return;}
    try{Uint8Array pixels=atlas.getPixels();ByteBuffer buffer=ByteBuffer.allocate(pixels.getLength());for(int i=0;i<pixels.getLength();i++)buffer.put((byte)pixels.get(i));buffer.flip();Glyph[] glyphs=new Glyph[count];Float32Array g=atlas.getGlyphs();for(int i=0;i<count;i++){int at=i*9;glyphs[i]=new Glyph((char)(start+i),(int)g.get(at),(int)g.get(at+1),(int)g.get(at+2),(int)g.get(at+3),g.get(at+4),g.get(at+5),g.get(at+6),g.get(at+7),g.get(at+8));}
     FontData result=new FontData(new TextureData(buffer,atlas.getSide(),atlas.getSide()),size,(char)start,(char)(start+count-1),atlas.getSide(),atlas.getSide(),atlas.getAscent(),atlas.getDescent(),atlas.getScale(),atlas.getAscent(),atlas.getLineHeight(),glyphs,null,ByteBuffer.wrap(bytes));transferred(atlas);atlases.put(result,atlas);callback.complete(result);
    }catch(RuntimeException|Error failure){release(atlas);callback.error(failure);}
   });
  }
  @JSFunctor interface Loaded extends JSObject{void accept(Atlas atlas,String error);}
  @JSBody(params={"bytes","size","start","count","callback"},script="valthorneHost.fonts.bake(bytes,size,start,count).then(font=>callback(font,null),error=>callback(null,String(error)));") static native void load(Uint8Array bytes,int size,int start,int count,Loaded callback);
  @JSBody(params="atlas",script="valthorneHost.fonts.release(atlas);") static native void release(Atlas atlas);
  @JSBody(params="atlas",script="atlas.pixels=null;atlas.glyphs=null;") static native void transferred(Atlas atlas);
  @JSBody(params={"atlas","left","right"},script="return valthorneHost.fonts.kern(atlas,left,right);") static native float kern(Atlas atlas,int left,int right);
 }
}
