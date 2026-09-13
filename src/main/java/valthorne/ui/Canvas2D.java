package valthorne.ui;

import static org.lwjgl.nanovg.NanoVG.*;

/** Drawing operations for custom NanoContainer HUDs, in top-left UI coordinates. */
public final class Canvas2D {
    private Canvas2D() {}
    public static final int ALIGN_LEFT=1, ALIGN_CENTER=2, ALIGN_TOP=8;
    public static void color(long vg,int rgb,float alpha){
        var ink=NanoUtility.color1((Math.round(Math.max(0,Math.min(1,alpha))*255)<<24)|(rgb&0xffffff));
        nvgFillColor(vg,ink);nvgStrokeColor(vg,ink);
    }
    public static void beginPath(long vg){nvgBeginPath(vg);}
    public static void fill(long vg){nvgFill(vg);}
    public static void stroke(long vg){nvgStroke(vg);}
    public static void roundedRect(long vg,float x,float y,float w,float h,float r){nvgRoundedRect(vg,x,y,w,h,r);}
    public static void rect(long vg,float x,float y,float w,float h){nvgRect(vg,x,y,w,h);}
    public static void moveTo(long vg,float x,float y){nvgMoveTo(vg,x,y);}
    public static void lineTo(long vg,float x,float y){nvgLineTo(vg,x,y);}
    public static void fontFace(long vg,String face){nvgFontFace(vg,face);}
    public static void fontSize(long vg,float size){nvgFontSize(vg,size);}
    public static void textAlign(long vg,int align){nvgTextAlign(vg,align);}
    public static void text(long vg,float x,float y,String text){nvgText(vg,x,y,text);}
    public static void strokeWidth(long vg,float width){nvgStrokeWidth(vg,width);}
}
