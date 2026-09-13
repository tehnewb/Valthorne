package valthorne.website;

/** Narrow Java facade between Valthorne's painter and the native browser controls. */
final class BrowserBridge {
    private BrowserBridge() { }
    private static WebsiteController controller() { return WebsiteController.instance(); }

    static float scroll() { return org.teavm.jso.browser.Window.current().getScrollY(); }
    static void begin() { controller().begin(); }
    static void end(float documentHeight) { controller().end(documentHeight); }
    static void section(String id, float documentY) { controller().section(id, documentY); }
    static void reveal(String key, float y) { controller().reveal(key, y); }
    static void clearEffect() { controller().clearEffect(); }
    static void link(String label, String href, float x, float y, float width, float height, boolean header, boolean primary) {
        controller().link(label, href, x, y, width, height, header, primary);
    }
    static float measure(long context, String text, float size) {
        return (float) controller().measure((int) context, text, size);
    }
    static void image(long context, String file, float x, float y, float width, float height) {
        controller().image((int) context, file, x, y, width, height);
    }
    static void ready() { controller().ready(); }

    /** Smooth alpha shading keeps text readable over the portrait artwork crop. */
    static void shade(long context, float x, float y, float width, float height, int color, float from, float to) {
        valthorne.website.browser.BrowserPort.fillGradient((int) context, x, y, width, height, color, from, to);
    }
}
