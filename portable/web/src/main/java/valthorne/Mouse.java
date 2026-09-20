package valthorne;
import org.teavm.jso.*;
import valthorne.event.*;
import valthorne.event.events.*;
import valthorne.event.listeners.*;

/** Browser mouse backend, preserving desktop button IDs and bottom-left coordinates. */
public final class Mouse {
    private Mouse() {}
    private static final boolean[] buttons=new boolean[8];
    private static int mouseX,mouseY;private static float scrollX,scrollY;
    public static final int LEFT=0,RIGHT=1,MIDDLE=2,BUTTON_3=3,BUTTON_4=4,BUTTON_5=5,BUTTON_6=6,BUTTON_7=7;
    public static final int CURSOR_NORMAL=0x34001,CURSOR_HIDDEN=0x34002,CURSOR_DISABLED=0x34003;
    public static final int CURSOR_ARROW=0x36001,CURSOR_IBEAM=0x36002,CURSOR_CROSSHAIR=0x36003,CURSOR_HAND=0x36004,CURSOR_HRESIZE=0x36005,CURSOR_VRESIZE=0x36006,CURSOR_RESIZE_NWSE=0x36007,CURSOR_RESIZE_NESW=0x36008;
    private static Object cursorOwner;private static int cursorShape=CURSOR_ARROW;
    @JSFunctor private interface Callback extends JSObject {void accept(int kind,int button,int mods,int fromX,int fromY,int x,int y);}
    @JSFunctor private interface Scroll extends JSObject {void accept(float x,float y);}
    static void init(){java.util.Arrays.fill(buttons,false);mouseX=0;mouseY=Window.getHeight();scrollX=scrollY=0;install((kind,button,mods,fromX,fromY,x,y)->JGL.runInputTask(()->{
        mouseX=x;mouseY=y;if(button>=0&&button<buttons.length&&(kind==0||kind==1))buttons[button]=kind==0;
        Event event=switch(kind){
            case 0 -> new MousePressEvent(button,mods,x,y);
            case 1 -> new MouseReleaseEvent(button,mods,x,y);
            case 2 -> new MouseMoveEvent(button,mods,fromX,fromY,x,y);
            case 3 -> new MouseDragEvent(button,mods,fromX,fromY,x,y);
            default -> new MouseScrollEvent(x,y);
        };JGL.publish(event);
    }));installScroll((x,y)->JGL.runInputTask(()->{scrollX=x;scrollY=y;JGL.publish(new MouseScrollEvent(0,0).setPreciseOffsets(x,y));}));}
    public static void addMouseListener(MouseListener listener){JGL.subscribe(EventTypes.MOUSE_PRESS,listener);JGL.subscribe(EventTypes.MOUSE_RELEASE,listener);JGL.subscribe(EventTypes.MOUSE_MOVE,listener);JGL.subscribe(EventTypes.MOUSE_DRAG,listener);}
    public static void removeMouseListener(MouseListener listener){JGL.unsubscribe(EventTypes.MOUSE_PRESS,listener);JGL.unsubscribe(EventTypes.MOUSE_RELEASE,listener);JGL.unsubscribe(EventTypes.MOUSE_MOVE,listener);JGL.unsubscribe(EventTypes.MOUSE_DRAG,listener);}
    public static void addScrollListener(MouseScrollListener listener){JGL.subscribe(EventTypes.MOUSE_SCROLL,listener);}
    public static void removeScrollListener(MouseScrollListener listener){JGL.unsubscribe(EventTypes.MOUSE_SCROLL,listener);}
    public static short getX(){return (short)mouseX;}public static short getY(){return (short)mouseY;}
    public static byte getScrollX(){return (byte)scrollX;}public static byte getScrollY(){return (byte)scrollY;}
    public static boolean isButtonDown(int button){return button>=0&&button<buttons.length&&buttons[button];}
    @JSBody(params="mode",script="valthorneHost.platform.cursorMode(mode);") public static native void setCursorMode(int mode);
    /**
     * Accepts the shared desktop raw-motion preference. Browser pointer-lock input
     * remains browser controlled, so this backend does not alter acceleration.
     *
     * @param enabled requested desktop raw-motion state
     */
    public static void setRawMouseMotion(boolean enabled) {
        // Browser pointer-lock policy owns relative motion on this backend.
    }
    @JSBody(params="shape",script="valthorneHost.platform.cursorShape(shape);") public static native void setCursor(int shape);
    public static void overrideCursor(Object owner,int shape){
        if(owner==null)throw new IllegalArgumentException("owner");
        cursorOwner=owner;cursorShape=shape;setCursor(shape);
    }
    public static void clearCursorOverride(Object owner){
        if(cursorOwner!=owner)return;
        cursorOwner=null;cursorShape=CURSOR_ARROW;setCursor(CURSOR_ARROW);
    }
    public static int getCursorShape(){return cursorShape;}
    public static void setCursor(valthorne.graphics.texture.TextureData texture,int hotX,int hotY){
        cursor(valthorne.web.WindowImages.pixels(texture),texture.width(),texture.height(),Math.max(0,Math.min(texture.width()-1,hotX)),Math.max(0,Math.min(texture.height()-1,texture.height()-hotY-1)));
    }
    @JSBody(params={"pixels","w","h","x","y"},script="var p=valthorneHost.platform;p.canvas.style.cursor=p.cursorValue='url('+p.window.image(pixels,w,h)+') '+x+' '+y+', default';") private static native void cursor(org.teavm.jso.typedarrays.Uint8Array pixels,int w,int h,int x,int y);
    /** Changes engine coordinates; browser security prevents warping the OS pointer. */
    public static void setCursorPosition(double x,double y){if(!Double.isFinite(x+y))throw new IllegalArgumentException("Invalid cursor position");mouseX=(int)x;mouseY=(int)y;position(x,y);}
    @JSBody(params={"x","y"},script="var p=valthorneHost.platform;p.mouseX=x;p.mouseY=p.window.height-y;") private static native void position(double x,double y);
    @JSBody(params="callback",script="valthorneHost.platform.legacyMouseEvent=callback;") private static native void install(Callback callback);
    @JSBody(params="callback",script="valthorneHost.platform.legacyScrollEvent=callback;") private static native void installScroll(Scroll callback);
    static void resetScroll(){scrollX=scrollY=0;}
}
