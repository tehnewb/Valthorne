package valthorne;

import org.teavm.jso.JSBody;
import valthorne.graphics.Color;

/** Browser window backend. Browser-owned window placement is not emulated. */
public final class Window {
    private Window() {}
    public static valthorne.graphics.GraphicsCapabilities getGraphicsCapabilities(){return valthorne.graphics.GraphicsCapabilities.current();}
    public static void addWindowResizeListener(valthorne.event.listeners.WindowResizeListener listener){addWindowResizeListener(listener,0);}
    public static void addWindowResizeListener(valthorne.event.listeners.WindowResizeListener listener,int priority){JGL.subscribe(valthorne.event.EventTypes.WINDOW_RESIZE,priority,java.util.Objects.requireNonNull(listener));}
    public static void removeWindowResizeListener(valthorne.event.listeners.WindowResizeListener listener){JGL.unsubscribe(valthorne.event.EventTypes.WINDOW_RESIZE,java.util.Objects.requireNonNull(listener));}
    private static SwapInterval interval=SwapInterval.VSYNC;
    private static final float[] projection=new float[16];
    private static int projectionWidth,projectionHeight;
    private static void resizeProjection(){int w=getWidth(),h=getHeight();if(w!=projectionWidth||h!=projectionHeight){new org.joml.Matrix4f().setOrtho(0,w,0,h,-1,1).get(projection);projectionWidth=w;projectionHeight=h;}}
    public static float[] getProjectionMatrix(){resizeProjection();return projection;}
    public static void setProjectionMatrix(float[] values){if(values==null)throw new NullPointerException("matrixData");if(values.length<16)throw new IllegalArgumentException("matrixData must contain at least 16 floats");resizeProjection();System.arraycopy(values,0,projection,0,16);}
    public static void copyProjectionMatrix(float[] values){if(values==null)throw new NullPointerException("destination");if(values.length<16)throw new IllegalArgumentException("destination must contain at least 16 floats");System.arraycopy(getProjectionMatrix(),0,values,0,16);}
    static void configure(JGLConfiguration config){setTitle(config.getTitle());interval=config.getSwapInterval();installEvents((ow,oh,w,h)->JGL.postEvent(new valthorne.event.events.WindowResizeEvent(ow,oh,w,h)),focused->JGL.postEvent(new valthorne.event.events.WindowFocusEvent(focused)));}
    @org.teavm.jso.JSFunctor private interface Resize extends org.teavm.jso.JSObject{void accept(int oldWidth,int oldHeight,int width,int height);}
    @org.teavm.jso.JSFunctor private interface Focus extends org.teavm.jso.JSObject{void accept(boolean focused);}
    @JSBody(params={"resize","focus"},script="valthorneHost.platform.legacyResizeEvent=resize;valthorneHost.platform.legacyFocusEvent=focus;") private static native void installEvents(Resize resize,Focus focus);
    @JSBody(params="title",script="document.title=title;") public static native void setTitle(String title);
    @JSBody(script="return valthorneHost.platform.window.width;") public static native int getWidth();
    @JSBody(script="return valthorneHost.platform.window.height;") public static native int getHeight();
    public static void init(JGLConfiguration config){configure(java.util.Objects.requireNonNull(config));}
    @JSBody(params={"width","height"},script="valthorneHost.platform.window.size(width,height);") public static native void setSize(int width,int height);
    @JSBody(script="return valthorneHost.platform.window.stage.getBoundingClientRect().left;") public static native int getX();
    @JSBody(script="return valthorneHost.platform.window.stage.getBoundingClientRect().top;") public static native int getY();
    @JSBody(script="var h=valthorneHost,c=h.graphics.canvas||h.platform.canvas;return [c.width,c.height];") public static native int[] getFramebufferSize();
    @JSBody(params={"x","y"},script="valthorneHost.platform.window.position(x,y);") public static native void setPosition(int x,int y);
    @JSBody(script="return document.fullscreenElement===valthorneHost.platform.window.stage;") public static native boolean isFullscreen();
    @JSBody(params="value",script="valthorneHost.platform.window.fullscreen(value);") public static native void setFullscreen(boolean value);
    public static void toggleFullscreen(){setFullscreen(!isFullscreen());}
    @JSBody(script="return valthorneHost.platform.window.borderless;") public static native boolean isBorderless();
    @JSBody(params="value",script="valthorneHost.platform.window.setBorderless(value);") public static native void setBorderless(boolean value);
    @JSBody(script="return valthorneHost.platform.window.resizable;") public static native boolean isResizable();
    @JSBody(params="value",script="valthorneHost.platform.window.setResizable(value);") public static native void setResizable(boolean value);
    @JSBody(script="valthorneHost.platform.window.minimize();") public static native void minimize();
    @JSBody(script="valthorneHost.platform.window.maximize();") public static native void maximize();
    @JSBody(script="valthorneHost.platform.window.restore();") public static native void restore();
    @JSBody(script="valthorneHost.platform.window.focus();") public static native void focus();
    @JSBody(params="value",script="valthorneHost.platform.window.stage.style.zIndex=value?'2147483647':'';") public static native void setAlwaysOnTop(boolean value);
    @JSBody(params="value",script="valthorneHost.platform.window.opacity(value);") public static native void setOpacity(float value);
    public static void setIcon(valthorne.graphics.texture.TextureData texture){icon(valthorne.web.WindowImages.pixels(texture),texture.width(),texture.height());}
    @JSBody(params={"pixels","width","height"},script="valthorneHost.platform.window.icon(pixels,width,height);") private static native void icon(org.teavm.jso.typedarrays.Uint8Array pixels,int width,int height);
    @JSBody(script="valthorneHost.platform.window.center();") public static native void center();
    @JSBody(params={"minW","minH","maxW","maxH"},script="valthorneHost.platform.window.limits(minW,minH,maxW,maxH);") public static native void setSizeLimits(int minW,int minH,int maxW,int maxH);
    /** Browsers do not expose native window pointers. */
    public static long getAddress(){return 0;}
    private static final valthorne.ui.Dimensional dimensional=new valthorne.ui.Dimensional(){
      public float getX(){return 0;}public float getY(){return 0;}
      public void setX(float x){Window.setPosition((int)x,Window.getY());}public void setY(float y){Window.setPosition(Window.getX(),(int)y);}
      public void setPosition(float x,float y){Window.setPosition((int)x,(int)y);}
      public float getWidth(){return Window.getWidth();}public float getHeight(){return Window.getHeight();}
      public void setWidth(float w){Window.setSize((int)w,Window.getHeight());}public void setHeight(float h){Window.setSize(Window.getWidth(),(int)h);}
      public void setSize(float w,float h){Window.setSize((int)w,(int)h);}
    };
    public static valthorne.ui.Dimensional getDimensional(){return dimensional;}
    public static void requestClose(){JGL.requestClose();}
    static boolean shouldClose(){return JGL.shouldClose();}
    public static SwapInterval getSwapInterval(){return interval;}
    public static void setSwapInterval(SwapInterval value){if(value==null)throw new NullPointerException();interval=value;}
    public static void clear(Color color){clearNative(color.getRed()/255f,color.getGreen()/255f,color.getBlue()/255f,color.getAlpha()/255f);}
    public static void clear3D(Color color){clear3DNative(color.r(),color.g(),color.b(),color.a());}
    @JSBody(params={"r","g","b","a"},script="valthorneHost.graphics.clearWindow(r,g,b,a,false);") private static native void clearNative(float r,float g,float b,float a);
    @JSBody(params={"r","g","b","a"},script="valthorneHost.graphics.clearWindow(r,g,b,a,true);") private static native void clear3DNative(float r,float g,float b,float a);
}
