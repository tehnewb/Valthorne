package valthorne.web.graphics;
import java.nio.IntBuffer;
import org.teavm.jso.*;
/** OpenType parsing behind Slug's existing outline compilation algorithm. */
public final class BrowserOutlineFont {
 private final JSObject font;
 private BrowserOutlineFont(JSObject font){this.font=font;}
 public static BrowserOutlineFont load(byte[] bytes){return new BrowserOutlineFont(parse(bytes));}
 @JSBody(params="bytes",script="return valthorneHost.fonts.outline(bytes);") private static native JSObject parse(byte[] bytes);
 @JSBody(params="font",script="return [font.ascender,font.descender,font.tables.hhea.lineGap||0];") private static native int[] metrics(JSObject font);
 @JSBody(params={"font","code"},script="var glyph=font.charToGlyph(String.fromCodePoint(code));var b=glyph.getBoundingBox();return [glyph.advanceWidth||0,glyph.leftSideBearing||0,Math.floor(b.x1),Math.floor(b.y1),Math.ceil(b.x2),Math.ceil(b.y2),glyph.path.commands.length];") private static native int[] glyph(JSObject font,int code);
 @JSBody(params={"font","a","b"},script="return font.getKerningValue(font.charToGlyph(String.fromCodePoint(a)),font.charToGlyph(String.fromCodePoint(b))); ") private static native int kern(JSObject font,int a,int b);
 @JSBody(params="font",script="return font.unitsPerEm;") private static native int units(JSObject font);
 @JSBody(params={"font","code"},script="var output=[];font.charToGlyph(String.fromCodePoint(code)).path.commands.forEach(function(c){var kind={M:0,L:1,Q:2,C:3,Z:4}[c.type];if(kind===undefined)throw new Error('Unknown outline command '+c.type);output.push(kind,c.x||0,c.y||0,c.x1||0,c.y1||0,c.x2||0,c.y2||0);});return output;") private static native float[] outline(JSObject font,int code);
 public float[] commands(int code){return outline(font,code);}
 public static void stbtt_GetFontVMetrics(BrowserOutlineFont info,IntBuffer ascent,IntBuffer descent,IntBuffer gap){int[] values=metrics(info.font);ascent.put(0,values[0]);descent.put(0,values[1]);gap.put(0,values[2]);}
 public static float stbtt_ScaleForMappingEmToPixels(BrowserOutlineFont info,float pixels){return pixels/units(info.font);}
 public static void stbtt_GetCodepointHMetrics(BrowserOutlineFont info,int code,IntBuffer advance,IntBuffer bearing){int[] values=glyph(info.font,code);advance.put(0,values[0]);bearing.put(0,values[1]);}
 public static boolean stbtt_GetCodepointBox(BrowserOutlineFont info,int code,IntBuffer x0,IntBuffer y0,IntBuffer x1,IntBuffer y1){int[] values=glyph(info.font,code);x0.put(0,values[2]);y0.put(0,values[3]);x1.put(0,values[4]);y1.put(0,values[5]);return values[6]>0;}
 public static int stbtt_GetCodepointKernAdvance(BrowserOutlineFont info,int a,int b){return kern(info.font,a,b);}
}
