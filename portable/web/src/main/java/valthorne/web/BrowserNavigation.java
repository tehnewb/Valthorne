package valthorne.web;

import org.teavm.jso.JSBody;

/** Same-tab navigation does not depend on a popup surviving deferred UI dispatch. */
public final class BrowserNavigation {
    private BrowserNavigation() {}

    @JSBody(params="link", script="try { const url=new URL(link,location.href); if(url.protocol!=='https:'&&url.protocol!=='http:')return false; location.assign(url.href); return true; } catch(error) { return false; }")
    public static native boolean open(String link);
}
