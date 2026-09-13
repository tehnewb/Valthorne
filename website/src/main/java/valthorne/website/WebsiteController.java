package valthorne.website;

import java.util.*;
import java.util.function.Consumer;
import org.teavm.jso.JSObject;
import org.teavm.jso.browser.Storage;
import org.teavm.jso.browser.Window;
import org.teavm.jso.dom.events.*;
import org.teavm.jso.dom.html.*;
import org.teavm.jso.dom.xml.NodeList;
import valthorne.website.browser.BrowserDom;
import valthorne.website.browser.BrowserPort;
import valthorne.website.browser.WebsiteNavigation;
import valthorne.website.content.*;

/**
 * Java owner of website state, input, animation scheduling, and browser controls.
 * BrowserDom and BrowserPort only bind primitive platform operations. Content,
 * behavior, transitions, and rendering decisions remain in Java source.
 */
public final class WebsiteController {
    private static WebsiteController instance;
    private final Window window = Window.current();
    private final HTMLDocument document = HTMLDocument.current();
    private final HTMLElement semantic = element("content"), links = element("engine-links"), status = element("status");
    private final HTMLElement interaction = element("scene-interaction"), motionToggle = element("motion-toggle"), backTop = element("back-top");
    private final HTMLInputElement search = (HTMLInputElement) element("engine-search");
    private final BrowserDom.MediaQuery reduced = BrowserDom.media("(prefers-reduced-motion: reduce)");
    private final Map<String, Double> reveals = new HashMap<>(), measurements = new HashMap<>();
    private final Map<String, HTMLImageElement> images = new HashMap<>();
    private final Map<String, Integer> occurrences = new HashMap<>();
    private final Map<String, Anchor> pool = new LinkedHashMap<>();
    private final List<Anchor> anchors = new ArrayList<>();
    private Page page;
    private String query, category = "all";
    private boolean playing = true, paused, closed, connected, ready, urgent, revealing, settleReveals;
    private boolean searchUsed, sceneUsed, sceneVisible;
    private double orbit, tilt, now, lastFrame, offset;
    private int raf, frames, copied = -1, copyTimer, startupTimer;
    private int pointerId = -1, pointerX, pointerY;
    private WebsiteNavigation navigation;
    private JSObject pageView;

    private WebsiteController() {
        String id = document.getDocumentElement().getAttribute("data-page-id");
        page = WebsiteContent.page(id == null ? "index" : id);
        query = query(window.getLocation().getFullURL());
        try { paused = "paused".equals(Storage.getSessionStorage().getItem("valthorne-motion")); }
        catch (RuntimeException ignored) { /* Browser storage is optional. */ }
        updatePageView();
    }

    /** Load the Java-selected font, then enter the normal Valthorne lifecycle. */
    public static void launch(Runnable application) {
        instance = new WebsiteController();
        if (!instance.showTextView()) instance.install(application);
    }
    static WebsiteController instance() { return instance; }

    /** Text mode uses the exported document without creating graphics resources. */
    private boolean showTextView() {
        BrowserDom.Url url = BrowserDom.url(window.getLocation().getFullURL(), window.getLocation().getFullURL());
        if (!"text".equals(url.getSearchParams().get("view"))) return false;
        status.setTextContent("Text version");
        HTMLElement toggle = element("view-toggle");
        toggle.setTextContent("Engine version");
        url.getSearchParams().delete("view");
        toggle.setAttribute("href", url.getHref());
        return true;
    }

    private void install(Runnable application) {
        startupTimer = Window.setTimeout(() -> { if (!ready) fail("Website engine startup timed out"); }, 20000);
        try {
            BrowserPort.initialize().await();
            if (closed) { BrowserPort.close(); return; }
            BrowserPort.setFontResolver(this::font);
            BrowserPort.onConnect(() -> { connected = true; invalidate(); });
            BrowserPort.onFailure(this::fail);
            BrowserPort.resize(Math.min(window.getDevicePixelRatio(), 2));
            installEvents(); installDiagnostics(); syncMotion();
            // Stay in TeaVM's Java coroutine while waiting: JGL.init itself suspends
            // as it loads resources and must not start inside a native Promise callback.
            BrowserDom.Font font = BrowserDom.font("UrbanistWebsite", "url(assets/fonts/Urbanist-Variable.ttf)", "100 900").load().await();
            if (closed) return;
            BrowserDom.addFont(font); application.run();
        } catch (Throwable failure) { fail(failure.toString()); }
    }

    /** Font names and weights are authored once for both drawing and measurement. */
    private String font(String face, String registered, double size) {
        if ("display".equals(face) || "ui-medium".equals(face) || "default".equals(face)) {
            int weight = "display".equals(face) ? 700 : "ui-medium".equals(face) ? 600 : 400;
            return weight + " " + size + "px UrbanistWebsite, sans-serif";
        }
        String family = registered == null || registered.isEmpty() ? face : registered;
        if (family == null || family.isEmpty()) family = "sans-serif";
        if (!List.of("serif", "sans-serif", "monospace", "system-ui").contains(family)) {
            family = "\"" + family.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
        }
        return size + "px " + family;
    }

    /** Schedule finite reveals at 60 Hz and a visible lab at 30 Hz; idle pages do no work. */
    private void invalidate() { urgent = true; schedule(); }
    private void schedule() {
        if (closed || BrowserDom.hidden() || !connected || raf != 0) return;
        raf = Window.requestAnimationFrame(this::frame);
    }
    private void frame(double timestamp) {
        raf = 0;
        try {
            if (!urgent && lastFrame != 0 && timestamp - lastFrame < 1000.0 / (revealing ? 60 : 30) - 1) { schedule(); return; }
            urgent = false; now = timestamp;
            BrowserPort.callFrame(lastFrame == 0 ? 0 : Math.min((timestamp - lastFrame) / 1000, .05));
            lastFrame = timestamp; frames++;
            if (revealing || running()) schedule();
        } catch (Throwable failure) { fail(failure.toString()); }
    }

    private void fail(String message) {
        Window.clearTimeout(startupTimer); closed = true; Window.cancelAnimationFrame(raf); raf = 0;
        if (navigation != null) navigation.finishTransition();
        document.getDocumentElement().getClassList().remove("engine-ready");
        for (HTMLElement item : List.of(element("scene"), links, search, interaction, motionToggle, backTop)) item.setHidden(true);
        forEach(semantic.querySelectorAll("a"), item -> item.removeAttribute("tabindex"));
        forEach(semantic.querySelectorAll("pre"), item -> item.setTabIndex(0));
        status.setTextContent("The engine view could not start. The complete text version is available below.");
        BrowserDom.exposeString("valthorneError", message); BrowserDom.error(message);
    }

    Page page() { return page; }
    String query() { return query; }
    String category() { return category; }
    double orbit() { return orbit; }
    double tilt() { return tilt; }
    boolean copied(int index) { return copied == index; }
    boolean motionEnabled() { return !paused && !reduced.isMatches(); }
    boolean running() { return playing && sceneVisible && motionEnabled(); }

    void begin() {
        searchUsed = false; sceneUsed = false; revealing = false;
        clearEffect(); anchors.clear(); occurrences.clear();
    }
    void end(float height) {
        for (Anchor anchor : pool.values()) anchor.node.setHidden(!anchors.contains(anchor));
        search.setHidden(!searchUsed); interaction.setHidden(!sceneUsed); sceneVisible = sceneUsed;
        style(element("scroll-space"), "height", px(Math.max(window.getInnerHeight(), Math.ceil(height))));
        style(element("access-bar"), "top", px(Math.max(window.getInnerHeight() - 58, height - 58)));
        backTop.setHidden(window.getScrollY() < 650);
        int headerStart = Math.max(0, anchors.size() - 7);
        List<Anchor> ordered = new ArrayList<>(anchors.subList(headerStart, anchors.size()));
        ordered.addAll(anchors.subList(0, headerStart));
        for (int i = 0; i < ordered.size(); i++) {
            Anchor anchor = ordered.get(i); boolean header = i < Math.min(7, anchors.size());
            double covered = header ? 0 : Math.max(0, headerHeight() - anchor.y);
            style(anchor.node, "z-index", header ? "3" : "1");
            style(anchor.node, "clip-path", covered > 0 ? "inset(" + px(covered) + " 0 0)" : "");
            anchor.node.setTabIndex(covered > 0 ? -1 : 0);
            if (covered >= anchor.height) anchor.node.setHidden(true);
            if (links.getChildren().item(i) != anchor.node) links.insertBefore(anchor.node, links.getChildren().item(i));
        }
    }
    void ready() {
        if (ready) return;
        ready = true; Window.clearTimeout(startupTimer);
        document.getDocumentElement().getClassList().add("engine-ready");
        forEach(semantic.querySelectorAll("a,pre"), item -> item.setTabIndex(-1));
        status.setTextContent("Rendered with Valthorne");
        document.setTitle(page.id().equals("index") ? "Valthorne — Java game engine" : page.title() + " — Valthorne");
        navigation = new WebsiteNavigation(new WebsiteNavigation.Hooks() {
            public WebsiteNavigation.VisitState captureState() {
                return new WebsiteNavigation.VisitState(window.getScrollY(), category, orbit, tilt, playing);
            }
            public HTMLCanvasElement surface() { return BrowserPort.surface(1); }
            public boolean motionEnabled() { return WebsiteController.this.motionEnabled(); }
            public void prepare(String id, Runnable complete, Consumer<String> failure) { preparePage(WebsiteContent.page(id), complete); }
            public void render(String id, String url, WebsiteNavigation.VisitState state) { changePage(WebsiteContent.page(id), url, state); }
        });
        navigation.install(); BrowserDom.flag("valthorneReady", true);
    }

    /** A reveal changes only painting coordinates, leaving document geometry stable. */
    void reveal(String key, float y) {
        clearEffect();
        if (settleReveals) { if (y < window.getInnerHeight()) reveals.put(key, now - 600); return; }
        if (!motionEnabled()) return;
        Double started = reveals.get(key);
        if (started == null) {
            if (y >= window.getInnerHeight() - 28) return;
            started = y < headerHeight() ? now - 600 : now; reveals.put(key, started);
        }
        double progress = Math.min(1, Math.max(0, (now - started) / 600));
        offset = Math.pow(1 - progress, 3) * 20; BrowserPort.setEffect(1, offset);
        if (progress < 1) revealing = true;
    }
    void clearEffect() { offset = 0; BrowserPort.setEffect(1, 0); }
    void scene(float x, float y, float width, float height) {
        double top = Math.max(y + offset, headerHeight()), bottom = Math.min(y + height + offset, window.getInnerHeight());
        sceneUsed = bottom > top && y + height > headerHeight();
        bounds(interaction, x, top, width, Math.max(0, bottom - top));
    }
    double measure(int context, String text, double size) {
        String key = BrowserPort.getFace(context) + ":" + size + ":" + text;
        Double result = measurements.get(key);
        if (result != null) return result;
        double measured = BrowserPort.measure(context, text, size);
        if (measurements.size() > 15000) measurements.clear();
        measurements.put(key, measured); return measured;
    }

    /** Real browser anchors provide focus, native gestures, and ordinary URLs. */
    void link(String label, String href, float x, float y, float width, float height) {
        double top = y + offset;
        if (top + height < 0 || top > window.getInnerHeight()) return;
        int occurrence = occurrences.getOrDefault(href, 0); occurrences.put(href, occurrence + 1);
        String key = href + "|" + occurrence;
        Anchor anchor = pool.get(key);
        if (anchor == null) {
            anchor = new Anchor(document.createElement("a"));
            Anchor created = anchor;
            anchor.node.setClassName("engine-link");
            anchor.node.addEventListener("keydown", (KeyboardEvent event) -> {
                if ("Space".equals(event.getCode()) && "button".equals(created.node.getAttribute("role"))) { event.preventDefault(); created.node.click(); }
            });
            anchor.node.addEventListener("click", (Event event) -> action(event, created.node));
            pool.put(key, anchor); links.appendChild(anchor.node);
        }
        anchors.add(anchor); anchor.y = top; anchor.height = height; anchor.node.setHidden(false);
        HTMLElement node = anchor.node;
        node.setAttribute("href", href); node.setAttribute("aria-label", label); node.setTitle(label); node.setTextContent(label);
        attribute(node, "role", href.startsWith("#") ? "button" : null);
        attribute(node, "aria-pressed", href.startsWith("#filter=") ? String.valueOf(category.equals(href.substring(8))) : href.equals("#animate") ? String.valueOf(running()) : null);
        attribute(node, "aria-disabled", href.equals("#animate") && !motionEnabled() ? "true" : null);
        attribute(node, "aria-current", href.equals(page.id() + ".html") ? "page" : null);
        toggle(node, "primary-action", label.equals("Start building")); toggle(node, "pill-action", height == 44);
        bounds(node, x, top, width, height);
    }
    private void action(Event event, HTMLElement node) {
        String action = node.getAttribute("href");
        if (!action.startsWith("#")) return;
        event.preventDefault();
        if (action.equals("#animate") && motionEnabled()) { playing = !playing; lastFrame = 0; }
        else if (action.equals("#reset")) { orbit = 0; tilt = 0; }
        else if (action.equals("#clear")) { category = "all"; search.setValue(""); searchChanged(); BrowserDom.focus(search); }
        else if (action.startsWith("#filter=")) category = action.substring(8);
        else if (action.startsWith("#copy=")) copyCode(Integer.parseInt(action.substring(6)));
        invalidate();
    }
    void search(float x, float y, float width) {
        double top = y + offset; searchUsed = top >= headerHeight() && top < window.getInnerHeight();
        bounds(search, x, top, width, 44);
    }
    void image(int context, String file, float x, float y, float width, float height) {
        if (y + height < 0 || y > window.getInnerHeight()) return;
        HTMLImageElement image = loadImage(file);
        if (image.getNaturalWidth() == 0) return;
        boolean branding = file.equals("banner.png") || file.equals("valthorne.png");
        BrowserPort.drawImage(context, image, x, y, width, height, branding, branding ? 0 : 4);
    }
    private HTMLImageElement loadImage(String file) {
        HTMLImageElement image = images.get(file);
        if (image != null) return image;
        image = (HTMLImageElement) document.createElement("img");
        image.setAttribute("decoding", "async");
        image.addEventListener("load", event -> invalidate()); image.addEventListener("error", event -> invalidate());
        image.setSrc("assets/" + file); images.put(file, image); return image;
    }
    private void preparePage(Page next, Runnable complete) {
        Section first = next.sections().get(0);
        Set<String> files = new HashSet<>(List.of(next.heroImage(), first.image()));
        for (int i = 0; i < Math.min(2, first.cards().size()); i++) files.add(first.cards().get(i).image());
        files.remove("");
        if (files.isEmpty()) { complete.run(); return; }
        int[] remaining = { files.size() };
        Runnable done = () -> { if (--remaining[0] == 0) complete.run(); };
        for (String file : files) BrowserDom.decode(loadImage(file)).then(value -> { done.run(); return null; }, error -> { done.run(); return null; });
    }

    /** Change all page state atomically and draw before releasing the transition cover. */
    private void changePage(Page next, String url, WebsiteNavigation.VisitState restored) {
        Window.clearTimeout(copyTimer); page = next; updatePageView(); query = query(url); search.setValue(query);
        category = restored == null ? "all" : restored.category(); copied = -1;
        orbit = restored == null ? 0 : restored.orbit(); tilt = restored == null ? 0 : restored.tilt(); playing = restored == null || restored.animating();
        pointerId = -1; interaction.getClassList().remove("dragging");
        reveals.clear(); revealing = false; clearEffect(); lastFrame = 0; Window.cancelAnimationFrame(raf); raf = 0;
        double y = restored == null ? 0 : restored.scroll();
        HTMLElement spacer = element("scroll-space");
        style(spacer, "height", px(Math.max(spacer.getOffsetHeight(), y + window.getInnerHeight())));
        BrowserDom.scroll(y, "instant"); now = BrowserDom.now(); settleReveals = true;
        try { BrowserPort.callFrame(0); frames++; } finally { settleReveals = false; }
        invalidate();
    }

    private void installEvents() {
        search.setValue(query); search.addEventListener("input", event -> searchChanged());
        interaction.addEventListener("pointerdown", (BrowserDom.Pointer event) -> {
            if (event.getButton() != 0) return;
            pointerId = event.getPointerId(); pointerX = event.getClientX(); pointerY = event.getClientY();
            BrowserDom.capture(interaction, pointerId); interaction.getClassList().add("dragging");
        });
        interaction.addEventListener("pointermove", (BrowserDom.Pointer event) -> {
            if (event.getPointerId() != pointerId) return;
            orbit += (event.getClientX() - pointerX) * .009;
            tilt = clamp(tilt + (event.getClientY() - pointerY) * .004, -.45, .45);
            pointerX = event.getClientX(); pointerY = event.getClientY(); invalidate();
        });
        for (String name : List.of("pointerup", "pointercancel", "lostpointercapture")) interaction.addEventListener(name, event -> {
            pointerId = -1; interaction.getClassList().remove("dragging");
        });
        interaction.addEventListener("keydown", (KeyboardEvent event) -> {
            String key = event.getKey();
            if (!List.of("ArrowLeft", "ArrowRight", "ArrowUp", "ArrowDown", "Home").contains(key)) return;
            event.preventDefault();
            if (key.equals("Home")) { orbit = 0; tilt = 0; }
            else if (key.equals("ArrowLeft")) orbit -= .15;
            else if (key.equals("ArrowRight")) orbit += .15;
            else tilt = clamp(tilt + (key.equals("ArrowDown") ? .06 : -.06), -.45, .45);
            invalidate();
        });
        motionToggle.addEventListener("click", event -> {
            paused = !paused;
            try { Storage.getSessionStorage().setItem("valthorne-motion", paused ? "paused" : "enabled"); }
            catch (RuntimeException ignored) { /* Preferences do not require storage. */ }
            syncMotion();
        });
        reduced.addEventListener("change", event -> syncMotion());
        backTop.addEventListener("click", event -> BrowserDom.scroll(0, motionEnabled() ? "smooth" : "instant"));
        BrowserDom.passive(window, "scroll", event -> invalidate());
        window.addEventListener("resize", event -> { BrowserPort.resize(Math.min(window.getDevicePixelRatio(), 2)); invalidate(); });
        document.addEventListener("visibilitychange", event -> {
            lastFrame = 0;
            if (BrowserDom.hidden()) { Window.cancelAnimationFrame(raf); raf = 0; } else invalidate();
        });
        window.addEventListener("pagehide", (BrowserDom.PageEvent event) -> {
            if (!event.isPersisted()) { closed = true; Window.cancelAnimationFrame(raf); BrowserPort.shutdownApplication(); }
        });
        window.addEventListener("pageshow", event -> invalidate());
    }
    private void syncMotion() {
        boolean enabled = motionEnabled();
        toggle(document.getDocumentElement(), "motion-paused", !enabled);
        String label = reduced.isMatches() ? "Reduced motion is enabled in your system settings" : enabled ? "Pause motion" : "Enable motion";
        motionToggle.setAttribute("aria-label", label); motionToggle.setTitle(label);
        motionToggle.setAttribute("aria-pressed", String.valueOf(!enabled));
        attribute(motionToggle, "disabled", reduced.isMatches() ? "" : null);
        if (!enabled) { reveals.replaceAll((key, value) -> -1000.0); if (navigation != null) navigation.finishTransition(); }
        lastFrame = 0; invalidate();
    }
    private void searchChanged() {
        query = search.getValue(); BrowserDom.Url url = BrowserDom.url(window.getLocation().getFullURL(), window.getLocation().getFullURL());
        if (query.isEmpty()) url.getSearchParams().delete("q"); else url.getSearchParams().set("q", query);
        window.getHistory().replaceState(window.getHistory().getState(), "", url.getHref()); invalidate();
    }
    private void copyCode(int index) {
        Page origin = page;
        try {
            BrowserDom.copy(page.sections().get(index).code()).then(value -> {
                if (page != origin) return null;
                status.setTextContent("Code copied."); copied = index; Window.clearTimeout(copyTimer); invalidate();
                copyTimer = Window.setTimeout(() -> { copied = -1; invalidate(); }, 2400); return null;
            }, error -> { copyFallback(origin, index); return null; });
        } catch (Throwable unavailable) { copyFallback(origin, index); }
    }

    /** A denied or unavailable clipboard opens selectable source at the matching block. */
    private void copyFallback(Page origin, int index) {
        if (page == origin) window.getLocation().setFullURL("?view=text#code-" + index);
    }

    /** Read-only diagnostics project Java state to browser tooling; they contain no site behavior. */
    private void installDiagnostics() {
        JSObject metrics = BrowserDom.object(), view = BrowserDom.object();
        BrowserDom.numberGetter(metrics, "frames", () -> frames);
        BrowserDom.booleanGetter(metrics, "animations", this::running);
        BrowserDom.booleanGetter(metrics, "revealing", () -> revealing);
        BrowserDom.booleanGetter(metrics, "sceneVisible", () -> sceneVisible);
        BrowserDom.booleanGetter(metrics, "reducedMotion", reduced::isMatches);
        BrowserDom.booleanGetter(metrics, "motionEnabled", this::motionEnabled);
        BrowserDom.booleanGetter(metrics, "navigating", () -> navigation != null && navigation.isNavigating());
        BrowserDom.numberGetter(metrics, "navigations", () -> navigation == null ? 0 : navigation.getNavigations());
        BrowserDom.objectGetter(view, "page", () -> pageView);
        BrowserDom.stringGetter(view, "query", () -> query); BrowserDom.stringGetter(view, "category", () -> category);
        BrowserDom.numberGetter(view, "orbit", () -> orbit); BrowserDom.numberGetter(view, "tilt", () -> tilt);
        BrowserDom.numberGetter(view, "copied", () -> copied); BrowserDom.measureFunction(view, this::measure);
        BrowserDom.expose("site", view); BrowserDom.expose("websiteMetrics", metrics);
    }
    private void updatePageView() {
        pageView = BrowserDom.object(); BrowserDom.string(pageView, "id", page.id()); BrowserDom.string(pageView, "title", page.title());
    }
    private String query(String url) { String value = BrowserDom.url(url, window.getLocation().getFullURL()).getSearchParams().get("q"); return value == null ? "" : value; }
    private HTMLElement element(String id) { return document.getElementById(id); }
    private double headerHeight() { return window.getInnerWidth() < 900 ? 120 : 88; }
    private static double clamp(double value, double low, double high) { return Math.max(low, Math.min(high, value)); }
    private static String px(double value) { return value + "px"; }
    private static void style(HTMLElement node, String name, String value) { node.getStyle().setProperty(name, value); }
    private static void bounds(HTMLElement node, double x, double y, double width, double height) {
        style(node, "left", px(x)); style(node, "top", px(y)); style(node, "width", px(width)); style(node, "height", px(height));
    }
    private static void toggle(HTMLElement node, String name, boolean present) { if (present) node.getClassList().add(name); else node.getClassList().remove(name); }
    private static void attribute(HTMLElement node, String name, String value) { if (value == null) node.removeAttribute(name); else node.setAttribute(name, value); }
    private static void forEach(NodeList<? extends HTMLElement> nodes, Consumer<HTMLElement> action) { for (int i = 0; i < nodes.getLength(); i++) action.accept(nodes.item(i)); }
    private static final class Anchor {
        final HTMLElement node; double y, height;
        Anchor(HTMLElement node) { this.node = node; }
    }
}
