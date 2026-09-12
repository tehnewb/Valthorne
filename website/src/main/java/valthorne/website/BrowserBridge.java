package valthorne.website;

import org.teavm.jso.JSBody;

/**
 * Browser services surrounding the engine surface. Content is generated from
 * content.json; layout and painting belong to WebsiteApplication. The host owns
 * semantic anchors and native editing so browser navigation and assistive tools
 * can use ordinary HTML without interpreting canvas pixels.
 */
final class BrowserBridge {
    private BrowserBridge() { }

    @JSBody(params="key", script="return site.page[key] || '';" )
    static native String page(String key);
    @JSBody(script="return site.page.sections.length;")
    static native int sections();
    @JSBody(params={"section","key"}, script="return site.page.sections[section][key] || '';")
    static native String section(int section, String key);
    @JSBody(params="section", script="return (site.page.sections[section].cards || []).length;")
    static native int cards(int section);
    @JSBody(params={"section","card","key"}, script="return site.page.sections[section].cards[card][key] || '';")
    static native String card(int section, int card, String key);
    @JSBody(script="return window.scrollY;")
    static native float scroll();
    @JSBody(script="return site.query;")
    static native String query();
    @JSBody(script="return site.category;")
    static native String category();
    @JSBody(script="return site.animating;")
    static native boolean animating();
    @JSBody(script="site.begin();")
    static native void begin();
    @JSBody(params="height", script="site.end(height);")
    static native void end(float height);
    @JSBody(params={"label","href","x","y","w","h"}, script="site.link(label,href,x,y,w,h);")
    static native void link(String label, String href, float x, float y, float w, float h);
    @JSBody(params={"x","y","w"}, script="site.search(x,y,w);")
    static native void search(float x, float y, float w);
    @JSBody(params={"vg","text","size"}, script="return site.measure(vg,text,size);")
    private static native float measureNative(int vg, String text, float size);
    static float measure(long vg, String text, float size) { return measureNative((int) vg, text, size); }
    @JSBody(params={"vg","file","x","y","w","h"}, script="site.image(vg,file,x,y,w,h);")
    private static native void imageNative(int vg, String file, float x, float y, float w, float h);
    static void image(long vg, String file, float x, float y, float w, float h) { imageNative((int) vg, file, x, y, w, h); }
    @JSBody(script="site.ready();")
    static native void ready();
}
