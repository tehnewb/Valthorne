package valthorne.website;

/**
 * Java facade used by the Valthorne painter. Page data and interaction state
 * belong to WebsiteController; browser-specific calls are isolated in its port.
 */
final class BrowserBridge {
    private BrowserBridge() { }
    private static WebsiteController controller() { return WebsiteController.instance(); }

    static String page(String key) { return controller().page().get(key); }
    static int sections() { return controller().page().sections().size(); }
    static String section(int section, String key) { return controller().page().sections().get(section).get(key); }
    static int cards(int section) { return controller().page().sections().get(section).cards().size(); }
    static String card(int section, int card, String key) { return controller().page().sections().get(section).cards().get(card).get(key); }
    static float scroll() { return org.teavm.jso.browser.Window.current().getScrollY(); }
    static String query() { return controller().query(); }
    static String category() { return controller().category(); }
    static boolean animating() { return controller().running(); }
    static boolean motionEnabled() { return controller().motionEnabled(); }
    static float orbit() { return (float) controller().orbit(); }
    static float tilt() { return (float) controller().tilt(); }
    static boolean copied(int index) { return controller().copied(index); }
    static void reveal(String key, float y) { controller().reveal(key, y); }
    static void clearEffect() { controller().clearEffect(); }
    static void scene(float x, float y, float width, float height) { controller().scene(x, y, width, height); }
    static void begin() { controller().begin(); }
    static void end(float height) { controller().end(height); }
    static void link(String label, String href, float x, float y, float width, float height) { controller().link(label, href, x, y, width, height); }
    static void search(float x, float y, float width) { controller().search(x, y, width); }
    static float measure(long context, String text, float size) { return (float) controller().measure((int) context, text, size); }
    static void image(long context, String file, float x, float y, float width, float height) { controller().image((int) context, file, x, y, width, height); }
    static void ready() { controller().ready(); }
}
