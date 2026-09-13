package valthorne.web;

import org.teavm.jso.JSBody;
import valthorne.portable.PlatformServices;

/** Browser platform services supplied by the host; positions are CSS pixels. */
public final class WebPlatform implements PlatformServices {
    public int viewportWidth(){return dimension(false);}public int viewportHeight(){return dimension(true);}
    @JSBody(params="height",script="return height?innerHeight:innerWidth;") private static native int dimension(boolean height);
    public boolean keyDown(String code){return keyDownNative(code);}
    public boolean takeKeyPress(String code){return takeKeyPressNative(code);}
    @JSBody(params="code",script="return valthorneHost.platform.takeKeyPress(code);") private static native boolean takeKeyPressNative(String code);
    @JSBody(params="code",script="return valthorneHost.platform.keyDown(code);") private static native boolean keyDownNative(String code);
    public boolean buttonDown(int button){return buttonDownNative(button);}
    @JSBody(params="button",script="return valthorneHost.platform.buttons.has(button);") private static native boolean buttonDownNative(int button);
    public float takeLookX(){return takeLookXNative();}
    @JSBody(script="return valthorneHost.platform.takeLook(0);") private static native float takeLookXNative();
    public float takeLookY(){return takeLookYNative();}
    @JSBody(script="return valthorneHost.platform.takeLook(1);") private static native float takeLookYNative();
    public void capturePointer(boolean capture){capturePointerNative(capture);}
    @JSBody(params="capture",script="valthorneHost.platform.capture(capture);") private static native void capturePointerNative(boolean capture);
    public String loadSetting(String key){return loadSettingNative(key);}
    @JSBody(params="key",script="return valthorneHost.platform.loadSetting(key);") private static native String loadSettingNative(String key);
    public void saveSetting(String key,String value){saveSettingNative(key,value);}
    @JSBody(params={"key","value"},script="valthorneHost.platform.saveSetting(key,value);") private static native void saveSettingNative(String key,String value);
    public void removeSetting(String key){removeSettingNative(key);}
    @JSBody(params="key",script="valthorneHost.platform.removeSetting(key);") private static native void removeSettingNative(String key);
    public void tone(float frequency,float seconds,float volume,float pan){toneNative(frequency,seconds,volume,pan);}
    @JSBody(params={"frequency","seconds","volume","pan"},script="valthorneHost.platform.tone(frequency,seconds,volume,pan);") private static native void toneNative(float frequency,float seconds,float volume,float pan);
    public void beginOverlay(){beginOverlayNative();}
    @JSBody(script="valthorneHost.platform.beginOverlay();") private static native void beginOverlayNative();
    public void rectangle(float x,float y,float w,float h,int rgb,float alpha){rectangleNative(x,y,w,h,rgb,alpha);}
    @JSBody(params={"x","y","w","h","rgb","alpha"},script="valthorneHost.platform.rectangle(x,y,w,h,rgb,alpha);") private static native void rectangleNative(float x,float y,float w,float h,int rgb,float alpha);
    public void text(String text,float x,float y,float size,int rgb){textNative(text,x,y,size,rgb);}
    @JSBody(params={"text","x","y","size","rgb"},script="valthorneHost.platform.text(text,x,y,size,rgb);") private static native void textNative(String text,float x,float y,float size,int rgb);
    public void close(){closeNative();}
    @JSBody(script="valthorneHost.platform.close();") private static native void closeNative();
}
