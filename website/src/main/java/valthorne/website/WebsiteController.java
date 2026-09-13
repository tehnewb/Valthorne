package valthorne.website;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.teavm.jso.JSObject;
import org.teavm.jso.browser.Storage;
import org.teavm.jso.browser.Window;
import org.teavm.jso.dom.events.Event;
import org.teavm.jso.dom.events.EventListener;
import org.teavm.jso.dom.events.MouseEvent;
import org.teavm.jso.dom.html.HTMLDocument;
import org.teavm.jso.dom.html.HTMLElement;
import org.teavm.jso.dom.html.HTMLImageElement;
import valthorne.website.browser.BrowserDom;
import valthorne.website.browser.BrowserPort;

/**
 * Owns landing-page input, fragment navigation, finite motion and browser lifecycle.
 *
 * <p>The renderer supplies document coordinates and visible link bounds. Java
 * maps those coordinates to native browser links without recreating the engine.
 * The Java-exported semantic document remains available if graphics cannot start.</p>
 */
public final class WebsiteController {
    private static WebsiteController instance;
    private final Window window = Window.current();
    private final HTMLDocument document = HTMLDocument.current();
    private final HTMLElement links = element("engine-links"), status = element("status");
    private final HTMLElement motion = element("motion-toggle"), backTop = element("back-top");
    private final BrowserDom.MediaQuery reduced = BrowserDom.media("(prefers-reduced-motion: reduce)");
    private final Map<String, Double> reveals = new HashMap<>(), measurements = new HashMap<>();
    private final Map<String, HTMLImageElement> images = new HashMap<>();
    private final Map<String, Anchor> pool = new LinkedHashMap<>();
    private final Map<String, Integer> occurrences = new HashMap<>();
    private final Map<String, Double> sections = new LinkedHashMap<>();
    private final List<Anchor> visible = new ArrayList<>();
    private boolean paused, closed, connected, ready, revealing;
    private int raf, frames, startupTimer;
    private double now, offset;
    private String activeSection = "top";

    private WebsiteController() {
        try { paused = "paused".equals(Storage.getSessionStorage().getItem("valthorne-motion")); }
        catch (RuntimeException ignored) { /* Storage is optional. */ }
    }

    /** Enter Java before choosing either the semantic or graphical presentation. */
    public static void launch(Runnable application) {
        instance = new WebsiteController();
        if (!instance.showTextView()) instance.install(application);
    }

    static WebsiteController instance() { return instance; }

    private boolean showTextView() {
        BrowserDom.Url url = currentUrl();
        if (!"text".equals(url.getSearchParams().get("view"))) return false;
        status.setTextContent("Text version");
        element("view-toggle").setTextContent("Engine version");
        url.getSearchParams().delete("view");
        element("view-toggle").setAttribute("href", url.getHref());
        return true;
    }

    private void install(Runnable application) {
        startupTimer = Window.setTimeout(() -> {
            if (!ready) fail("Website engine startup timed out");
        }, 20000);
        try {
            BrowserPort.initialize().await();
            if (closed) { BrowserPort.close(); return; }
            BrowserPort.setFontResolver(this::font);
            BrowserPort.onConnect(() -> { connected = true; invalidate(); });
            BrowserPort.onFailure(this::fail);
            BrowserPort.resize(Math.min(window.getDevicePixelRatio(), 2));
            installEvents();
            installDiagnostics();
            syncMotion();
            // Load artwork and fonts before the first engine frame, keeping
            // both awaits inside the TeaVM Java coroutine used by JGL.init.
            HTMLImageElement artwork = loadImage("world.svg");
            loadImage("valthorne.png");
            loadImage("banner.png");
            BrowserDom.Font font = BrowserDom.font("UrbanistWebsite",
                    "url(assets/fonts/Urbanist-Variable.ttf)", "100 900").load().await();
            BrowserDom.addFont(font);
            BrowserDom.decode(artwork).await();
            if (!closed) application.run();
        } catch (Throwable failure) { fail(failure.toString()); }
    }

    /** The painter and text measurements share exactly the same font definition. */
    private String font(String face, String registered, double size) {
        int weight = "display".equals(face) ? 700 : "ui-medium".equals(face) ? 600 : 400;
        return weight + " " + size + "px UrbanistWebsite, sans-serif";
    }

    private void invalidate() {
        if (closed || !connected || BrowserDom.hidden() || raf != 0) return;
        raf = Window.requestAnimationFrame(this::frame);
    }

    private void frame(double timestamp) {
        raf = 0;
        try {
            now = timestamp;
            BrowserPort.callFrame(0);
            frames++;
            if (revealing) invalidate();
        } catch (Throwable failure) { fail(failure.toString()); }
    }

    private void fail(String message) {
        closed = true;
        Window.clearTimeout(startupTimer);
        Window.cancelAnimationFrame(raf);
        raf = 0;
        document.getDocumentElement().getClassList().remove("engine-ready");
        var focusable = element("content").querySelectorAll("a");
        for (int i = 0; i < focusable.getLength(); i++) focusable.item(i).removeAttribute("tabindex");
        status.setTextContent("The complete text version is available below.");
        BrowserDom.exposeString("valthorneError", message);
        BrowserDom.error(message);
    }

    void begin() {
        clearEffect();
        revealing = false;
        visible.clear();
        occurrences.clear();
        sections.clear();
    }

    /** Register stable document coordinates before the native scroll position is applied. */
    void section(String id, double documentY) { sections.put(id, documentY); }

    void end(float documentHeight) {
        style(element("scroll-space"), "height", px(Math.max(window.getInnerHeight(), documentHeight)));
        style(element("access-bar"), "top", px(documentHeight - 84));
        backTop.setHidden(window.getScrollY() < 600);
        for (Anchor anchor : pool.values()) anchor.node.setHidden(!visible.contains(anchor));
        visible.sort(Comparator.comparing((Anchor item) -> !item.header));
        for (int i = 0; i < visible.size(); i++) {
            Anchor anchor = visible.get(i);
            double covered = anchor.header ? 0 : Math.max(0, headerHeight() - anchor.y);
            style(anchor.node, "z-index", anchor.header ? "3" : "1");
            style(anchor.node, "clip-path", covered > 0 ? "inset(" + px(covered) + " 0 0)" : "");
            anchor.node.setTabIndex(covered > 0 ? -1 : 0);
            if (covered >= anchor.height) anchor.node.setHidden(true);
            if (links.getChildren().item(i) != anchor.node)
                links.insertBefore(anchor.node, links.getChildren().item(i));
        }
        activeSection = "top";
        double position = window.getScrollY() + headerHeight() + 18;
        for (var section : sections.entrySet()) {
            if (section.getValue() <= position) activeSection = section.getKey();
        }
        // A short final section cannot always align with the top of a tall
        // viewport. Reaching the document's end still selects that section.
        if (window.getScrollY() > 0 && window.getScrollY() + window.getInnerHeight() >= document.getBody().getScrollHeight() - 2)
            activeSection = "start";
        for (Anchor anchor : visible) {
            attribute(anchor.node, "aria-current", anchor.header && anchor.href.equals("#" + activeSection) ? "location" : null);
        }
    }

    void ready() {
        if (ready) return;
        ready = true;
        Window.clearTimeout(startupTimer);
        document.getDocumentElement().getClassList().add("engine-ready");
        var focusable = element("content").querySelectorAll("a");
        for (int i = 0; i < focusable.getLength(); i++) focusable.item(i).setTabIndex(-1);
        status.setTextContent("Made with Valthorne");
        BrowserDom.flag("valthorneReady", true);
        BrowserDom.manualScrollRestoration();
        Window.setTimeout(() -> navigate(fragment(), false, false), 0);
    }

    /** Only translation animates; text remains fully opaque throughout a finite reveal. */
    void reveal(String key, float y) {
        clearEffect();
        if (!motionEnabled() || y >= window.getInnerHeight() - 32) return;
        Double started = reveals.get(key);
        if (started == null) {
            started = y < headerHeight() ? now - 600 : now;
            reveals.put(key, started);
        }
        double progress = Math.min(1, Math.max(0, (now - started) / 600));
        offset = Math.pow(1 - progress, 3) * 16;
        BrowserPort.setEffect(1, offset);
        if (progress < 1) revealing = true;
    }

    void clearEffect() { offset = 0; BrowserPort.setEffect(1, 0); }
    boolean motionEnabled() { return !paused && !reduced.isMatches(); }

    /** Real anchors preserve modifier clicks, open-in-new-tab, and keyboard activation. */
    void link(String label, String href, float x, float y, float width, float height, boolean header, boolean primary) {
        double top = y + offset;
        if (top + height < 0 || top > window.getInnerHeight()) return;
        String group = (header ? "header:" : "body:") + href;
        int occurrence = occurrences.getOrDefault(group, 0);
        occurrences.put(group, occurrence + 1);
        String key = group + ":" + occurrence;
        Anchor anchor = pool.get(key);
        if (anchor == null) {
            anchor = new Anchor(document.createElement("a"));
            Anchor created = anchor;
            anchor.node.setClassName("engine-link");
            anchor.node.addEventListener("click", (MouseEvent event) -> {
                if (event.getButton() != MouseEvent.LEFT_BUTTON || event.getCtrlKey()
                        || event.getMetaKey() || event.getShiftKey() || event.getAltKey()) return;
                if (!created.href.startsWith("#") || !sections.containsKey(created.href.substring(1))) return;
                event.preventDefault();
                navigate(created.href.substring(1), true, true);
                links.setTabIndex(-1);
                BrowserDom.focus(links);
            });
            pool.put(key, anchor);
            links.appendChild(anchor.node);
        }
        anchor.y = top;
        anchor.height = height;
        anchor.header = header;
        anchor.href = href;
        visible.add(anchor);
        anchor.node.setHidden(false);
        anchor.node.setTextContent(label);
        anchor.node.setAttribute("href", href);
        anchor.node.setAttribute("aria-label", label);
        anchor.node.setAttribute("data-header", String.valueOf(header));
        anchor.node.setTitle(label);
        toggle(anchor.node, "primary-action", primary);
        style(anchor.node, "left", px(x));
        style(anchor.node, "top", px(top));
        style(anchor.node, "width", px(width));
        style(anchor.node, "height", px(height));
    }

    /** Fragment transitions scroll one running engine; there are no secondary routes to fetch. */
    private void navigate(String id, boolean push, boolean animate) {
        Double position = sections.get(id);
        if (position == null) return;
        if (push && !id.equals(fragment())) {
            window.getHistory().pushState(null, "", "#" + id);
        }
        double target = id.equals("top") ? 0 : Math.max(0, position - headerHeight());
        BrowserDom.scroll(target, animate && motionEnabled() ? "smooth" : "instant");
        invalidate();
    }

    private String fragment() {
        String hash = currentUrl().getHash();
        return hash.isEmpty() ? "top" : hash.substring(1);
    }

    private BrowserDom.Url currentUrl() {
        return BrowserDom.url(window.getLocation().getFullURL(), window.getLocation().getFullURL());
    }

    private void installEvents() {
        motion.addEventListener("click", event -> {
            paused = !paused;
            try { Storage.getSessionStorage().setItem("valthorne-motion", paused ? "paused" : "enabled"); }
            catch (RuntimeException ignored) { /* Preferences are optional. */ }
            syncMotion();
        });
        reduced.addEventListener("change", event -> syncMotion());
        backTop.addEventListener("click", event -> navigate("top", true, true));
        BrowserDom.passive(window, "scroll", event -> invalidate());
        window.addEventListener("resize", event -> {
            BrowserPort.resize(Math.min(window.getDevicePixelRatio(), 2));
            invalidate();
        });
        window.addEventListener("popstate", (EventListener<Event>) event -> navigate(fragment(), false, false));
        window.addEventListener("hashchange", (EventListener<Event>) event -> navigate(fragment(), false, true));
        document.addEventListener("visibilitychange", (EventListener<Event>) event -> {
            if (BrowserDom.hidden()) { Window.cancelAnimationFrame(raf); raf = 0; }
            else invalidate();
        });
        window.addEventListener("pagehide", (BrowserDom.PageEvent event) -> {
            if (!event.isPersisted()) {
                closed = true;
                Window.cancelAnimationFrame(raf);
                BrowserPort.shutdownApplication();
            }
        });
        window.addEventListener("pageshow", event -> invalidate());
    }

    private void syncMotion() {
        boolean enabled = motionEnabled();
        toggle(document.getDocumentElement(), "motion-paused", !enabled);
        String label = reduced.isMatches() ? "Reduced motion is enabled in your system settings" : enabled ? "Pause motion" : "Enable motion";
        motion.setAttribute("aria-label", label);
        motion.setTitle(label);
        motion.setTextContent(enabled ? "Pause motion" : "Enable motion");
        motion.setAttribute("aria-pressed", String.valueOf(!enabled));
        attribute(motion, "disabled", reduced.isMatches() ? "" : null);
        if (!enabled) reveals.replaceAll((key, value) -> -1000.0);
        invalidate();
    }

    double measure(int context, String text, double size) {
        String key = BrowserPort.getFace(context) + ":" + size + ":" + text;
        Double cached = measurements.get(key);
        if (cached != null) return cached;
        double result = BrowserPort.measure(context, text, size);
        measurements.put(key, result);
        return result;
    }

    void image(int context, String file, float x, float y, float width, float height) {
        if (y + height < 0 || y > window.getInnerHeight()) return;
        HTMLImageElement image = loadImage(file);
        if (image.getNaturalWidth() == 0) return;
        boolean branding = !file.equals("world.svg");
        double focus = branding || window.getInnerWidth() >= 760 ? .5 : .87;
        BrowserPort.drawImageFocused(context, image, x, y, width, height, branding, focus);
    }

    private HTMLImageElement loadImage(String file) {
        HTMLImageElement cached = images.get(file);
        if (cached != null) return cached;
        HTMLImageElement image = (HTMLImageElement) document.createElement("img");
        image.setAttribute("decoding", "async");
        image.addEventListener("load", event -> invalidate());
        image.addEventListener("error", event -> invalidate());
        image.setSrc("assets/" + file);
        images.put(file, image);
        return image;
    }

    /** Browser checks observe Java-owned state; these read-only getters contain no behavior. */
    private void installDiagnostics() {
        JSObject metrics = BrowserDom.object();
        BrowserDom.numberGetter(metrics, "frames", () -> frames);
        BrowserDom.booleanGetter(metrics, "revealing", () -> revealing);
        BrowserDom.booleanGetter(metrics, "motionEnabled", this::motionEnabled);
        BrowserDom.booleanGetter(metrics, "reducedMotion", reduced::isMatches);
        BrowserDom.stringGetter(metrics, "activeSection", () -> activeSection);
        BrowserDom.expose("websiteMetrics", metrics);
    }

    private double headerHeight() { return window.getInnerWidth() < 760 ? 112 : 80; }
    private HTMLElement element(String id) { return document.getElementById(id); }
    private static String px(double value) { return value + "px"; }
    private static void style(HTMLElement node, String key, String value) { node.getStyle().setProperty(key, value); }
    private static void attribute(HTMLElement node, String key, String value) {
        if (value == null) node.removeAttribute(key); else node.setAttribute(key, value);
    }
    private static void toggle(HTMLElement node, String key, boolean enabled) {
        if (enabled) node.getClassList().add(key); else node.getClassList().remove(key);
    }

    private static final class Anchor {
        final HTMLElement node;
        String href = "";
        double y, height;
        boolean header;
        Anchor(HTMLElement node) { this.node = node; }
    }
}
