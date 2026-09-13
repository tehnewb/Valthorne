package valthorne.website.browser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import org.teavm.jso.JSBody;
import org.teavm.jso.JSObject;
import org.teavm.jso.JSProperty;
import org.teavm.jso.ajax.XMLHttpRequest;
import org.teavm.jso.browser.History;
import org.teavm.jso.browser.Location;
import org.teavm.jso.browser.Window;
import org.teavm.jso.canvas.CanvasRenderingContext2D;
import org.teavm.jso.dom.events.Event;
import org.teavm.jso.dom.events.EventListener;
import org.teavm.jso.dom.events.MouseEvent;
import org.teavm.jso.dom.html.HTMLCanvasElement;
import org.teavm.jso.dom.html.HTMLDocument;
import org.teavm.jso.dom.html.HTMLElement;
import org.teavm.jso.dom.xml.DOMParser;
import org.teavm.jso.dom.xml.Node;
import org.teavm.jso.dom.xml.NodeList;

/**
 * Navigates between the website's Java pages without restarting Valthorne.
 *
 * <p>Java owns route selection, loading and deployment validation, visit history,
 * scroll restoration, request ordering, and transition timing. Browser bindings
 * only expose native URL, history, DOM, and animation-frame operations. The
 * semantic HTML companion is fetched alongside the Java page so assistive tools,
 * document metadata, and the text-view link follow the visible content.</p>
 */
public final class WebsiteNavigation {
    private static final Set<String> PAGE_IDS = Set.of("index", "engine", "examples", "docs", "start", "lab", "about");
    private static final String[] METADATA = {
        "link[rel=\"canonical\"]", "meta[name=\"description\"]", "meta[property^=\"og:\"]"
    };
    private static final int PREPARATION_TIMEOUT_MS = 8_000;
    private static final double OUTGOING_FADE_MS = 90;
    private static final double BACKGROUND_FADE_MS = 110;

    /** State preserved for each individual Back/Forward history entry. */
    public record VisitState(double scroll, String category, double orbit, double tilt, boolean animating) { }

    /** Integrates routing with the Java controller that owns the active page and painter. */
    public interface Hooks {
        VisitState captureState();
        HTMLCanvasElement surface();
        boolean motionEnabled();

        /** Decode opening media; invoke exactly one completion callback. */
        void prepare(String pageId, Runnable ready, Consumer<String> failure);

        /** Switch Java content and paint once; a null restored state means a fresh visit. */
        void render(String pageId, String absoluteUrl, VisitState restored);
    }

    private final Hooks hooks;
    private final HTMLDocument document = HTMLDocument.current();
    private final History history = History.current();
    private final Url root = Bindings.url(".", Location.current().getFullURL());
    private final String revision = buildRevision(document);
    private final Map<String, PageLoad> cache = new HashMap<>();
    private final Map<String, VisitState> visits = new HashMap<>();
    private String currentKey;
    private int sequence;
    private int request;
    private int navigations;
    private boolean installed;
    private boolean navigating;
    private HTMLElement overlay;
    private HTMLCanvasElement outgoing;
    private int animationFrame;
    private int transitionRequest;
    private double transitionStarted;

    public WebsiteNavigation(Hooks hooks) {
        this.hooks = hooks;
    }

    /** Install once after the first successful Java paint. Native modified clicks remain native. */
    public void install() {
        if (installed) return;
        installed = true;
        HistoryState state = historyState();
        currentKey = state == null ? null : Bindings.visit(state);
        if (currentKey == null || currentKey.isEmpty()) currentKey = newKey();
        Bindings.manualScrollRestoration(history);
        HistoryState initial = Bindings.copyState(history.getState());
        initial.setVisit(currentKey);
        history.replaceState(initial, "");

        document.addEventListener("click", (EventListener<NavigationClick>) event -> {
            if (event.isDefaultPrevented() || event.getButton() != MouseEvent.LEFT_BUTTON
                    || event.getCtrlKey() || event.getMetaKey() || event.getShiftKey() || event.getAltKey()) return;
            Route route = destination(anchor(event));
            if (route == null) return;
            event.preventDefault();
            navigate(route, null);
        });
        for (String name : new String[] { "pointerover", "focusin" }) {
            document.addEventListener(name, (EventListener<Event>) event -> {
                Route route = destination(anchor(event));
                if (route != null) load(route, ignored -> { }, ignored -> { });
            });
        }
        Window.current().addEventListener("popstate", (EventListener<NavigationPop>) event -> {
            Url url = Bindings.url(Location.current().getFullURL(), root.getHref());
            HistoryState target = (HistoryState) Bindings.present(event.getState());
            String visitKey = target == null ? null : Bindings.visit(target);
            if (visitKey == null || visitKey.isEmpty() || url.getSearchParams().has("view")) {
                Location.current().reload();
                return;
            }
            Route route = route(url);
            if (route == null) {
                Location.current().reload();
                return;
            }
            navigate(route, new Visit(visitKey, restore(Bindings.saved(target))));
        });
        Window.current().addEventListener("resize", (EventListener<Event>) event -> finishTransition());
        document.addEventListener("visibilitychange", (EventListener<Event>) event -> {
            if (Bindings.hidden(document)) finishTransition();
        });
    }

    public boolean isNavigating() { return navigating; }
    public int getNavigations() { return navigations; }

    /** Remove the outgoing snapshot immediately when motion stops or the viewport changes. */
    public void finishTransition() {
        if (animationFrame != 0) Window.cancelAnimationFrame(animationFrame);
        animationFrame = 0;
        if (overlay != null) overlay.delete();
        overlay = null;
        outgoing = null;
        int token = transitionRequest;
        transitionRequest = 0;
        if (token != 0) complete(token);
    }

    private String newKey() {
        return System.currentTimeMillis() + "-" + ++sequence;
    }

    /** Walk actual DOM ancestors rather than interpreting a clicked element's contents. */
    private HTMLElement anchor(Event event) {
        Node node = (Node) event.getTarget();
        while (node != null) {
            if (node.getNodeType() == Node.ELEMENT_NODE && "a".equalsIgnoreCase(node.getNodeName())) {
                return (HTMLElement) node;
            }
            node = node.getParentNode();
        }
        return null;
    }

    private Route destination(HTMLElement anchor) {
        if (anchor == null || anchor.hasAttribute("download")) return null;
        String target = anchor.getAttribute("target");
        if (target != null && !target.isEmpty() && !"_self".equals(target)) return null;
        String href = anchor.getAttribute("href");
        if (href == null || href.isEmpty() || href.startsWith("#")) return null;
        try {
            return route(Bindings.url(href, Location.current().getFullURL()));
        } catch (RuntimeException invalidUrl) {
            return null;
        }
    }

    private Route route(Url url) {
        if (!root.getOrigin().equals(url.getOrigin()) || !url.getHash().isEmpty()
                || url.getSearchParams().has("view") || !url.getPathname().startsWith(root.getPathname())) return null;
        String file = url.getPathname().substring(root.getPathname().length());
        if (file.isEmpty()) file = "index.html";
        if (!file.endsWith(".html")) return null;
        String id = file.substring(0, file.length() - 5);
        return PAGE_IDS.contains(id) ? new Route(url.getHref(), url.getPathname(), id) : null;
    }

    /** Concurrent hover, focus, and click requests share one validated document preparation. */
    private void load(Route route, Consumer<LoadedPage> ready, Consumer<String> failed) {
        PageLoad pending = cache.get(route.pathname());
        if (pending != null) {
            pending.subscribe(ready, failed);
            return;
        }
        pending = new PageLoad(route);
        cache.put(route.pathname(), pending);
        pending.subscribe(ready, failed);
        pending.start();
    }

    private VisitState remember() {
        VisitState state = hooks.captureState();
        visits.put(currentKey, state);
        return state;
    }

    private void navigate(Route route, Visit visit) {
        int token = ++request;
        navigating = true;
        document.getElementById("engine-links").setAttribute("aria-busy", "true");
        VisitState previous = remember();
        load(route, next -> {
            if (token != request) return;
            try {
                VisitState restored = visit == null ? null : visits.getOrDefault(visit.key(), visit.saved());
                snapshot();
                if (visit == null) {
                    // A Back request changes history before its document is ready. A new
                    // click must not replace that destination with the still-visible page.
                    HistoryState current = historyState();
                    if (current != null && currentKey.equals(Bindings.visit(current))) {
                        HistoryState saved = Bindings.copyState(current);
                        saved.setSaved(save(previous));
                        history.replaceState(saved, "");
                    }
                    currentKey = newKey();
                    HistoryState pushed = Bindings.copyState(null);
                    pushed.setVisit(currentKey);
                    history.pushState(pushed, "", route.href());
                } else {
                    currentKey = visit.key();
                }
                updateDocument(next.document());
                hooks.render(next.id(), route.href(), restored);
                remember();
                navigations++;
                HTMLElement navigation = document.getElementById("engine-links");
                navigation.setAttribute("aria-label", next.document().getTitle() + " — navigation and actions");
                navigation.setTabIndex(-1);
                Bindings.focusWithoutScrolling(navigation);
                document.getElementById("status").setTextContent("Rendered with Valthorne");
                if (overlay == null) complete(token);
                else startTransition(token);
            } catch (RuntimeException failure) {
                fallback(route, visit, token);
            }
        }, reason -> fallback(route, visit, token));
    }

    private void fallback(Route route, Visit visit, int token) {
        if (token != request) return;
        finishTransition();
        complete(token);
        // A new deployment or network failure retains ordinary document navigation.
        if (visit == null) Location.current().assign(route.href());
        else Location.current().reload();
    }

    private void complete(int token) {
        if (token != request) return;
        navigating = false;
        document.getElementById("engine-links").removeAttribute("aria-busy");
    }

    /** Snapshot the old body while CSS excludes the stationary header from the cover. */
    private void snapshot() {
        finishTransition();
        if (!hooks.motionEnabled()) return;
        HTMLCanvasElement surface = hooks.surface();
        outgoing = (HTMLCanvasElement) document.createElement("canvas");
        outgoing.setWidth(surface.getWidth());
        outgoing.setHeight(surface.getHeight());
        CanvasRenderingContext2D context = (CanvasRenderingContext2D) outgoing.getContext("2d");
        context.drawImage(surface, 0, 0);
        overlay = document.createElement("div");
        overlay.setClassName("page-transition");
        overlay.setAttribute("aria-hidden", "true");
        overlay.appendChild(outgoing);
        document.getBody().appendChild(overlay);
    }

    private void startTransition(int token) {
        transitionRequest = token;
        transitionStarted = Bindings.now();
        animationFrame = Window.requestAnimationFrame(this::animateTransition);
    }

    /** Fade the old page to charcoal before revealing the new page, avoiding overlapping headlines. */
    private void animateTransition(double now) {
        animationFrame = 0;
        if (overlay == null) return;
        if (!hooks.motionEnabled() || Bindings.hidden(document)) {
            finishTransition();
            return;
        }
        double elapsed = now - transitionStarted;
        double outgoingProgress = Math.min(1, Math.max(0, elapsed / OUTGOING_FADE_MS));
        outgoing.getStyle().setProperty("opacity", Double.toString(Math.pow(1 - outgoingProgress, 2.2)));
        if (elapsed > OUTGOING_FADE_MS) {
            double progress = Math.min(1, (elapsed - OUTGOING_FADE_MS) / BACKGROUND_FADE_MS);
            overlay.getStyle().setProperty("opacity", Double.toString(Math.pow(1 - progress, 2.2)));
        }
        if (elapsed >= OUTGOING_FADE_MS + BACKGROUND_FADE_MS) finishTransition();
        else animationFrame = Window.requestAnimationFrame(this::animateTransition);
    }

    private void updateDocument(HTMLDocument next) {
        HTMLElement content = document.getElementById("content");
        content.clear();
        NodeList<Node> children = next.getElementById("content").getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            content.appendChild(document.importNode(children.item(i), true));
        }
        NodeList<? extends HTMLElement> focusable = content.querySelectorAll("a,pre");
        for (int i = 0; i < focusable.getLength(); i++) focusable.item(i).setTabIndex(-1);
        document.getDocumentElement().setAttribute("data-page-id", next.getDocumentElement().getAttribute("data-page-id"));
        document.setTitle(next.getTitle());
        for (String selector : METADATA) {
            NodeList<? extends HTMLElement> old = document.getHead().querySelectorAll(selector);
            for (int i = 0; i < old.getLength(); i++) old.item(i).delete();
            NodeList<? extends HTMLElement> replacements = next.getHead().querySelectorAll(selector);
            for (int i = 0; i < replacements.getLength(); i++) {
                document.getHead().appendChild(document.importNode(replacements.item(i), true));
            }
        }
    }

    private static String buildRevision(HTMLDocument document) {
        HTMLElement metadata = document.querySelector("meta[name=\"valthorne-build\"]");
        return metadata == null ? "" : metadata.getAttribute("content");
    }

    private HistoryState historyState() { return (HistoryState) Bindings.present(history.getState()); }

    private static SavedState save(VisitState state) {
        SavedState saved = Bindings.emptySaved();
        saved.setScroll(state.scroll());
        saved.setCategory(state.category());
        saved.setOrbit(state.orbit());
        saved.setTilt(state.tilt());
        saved.setAnimating(state.animating());
        return saved;
    }

    private static VisitState restore(SavedState state) {
        return state == null ? null : new VisitState(state.getScroll(), state.getCategory(), state.getOrbit(),
                state.getTilt(), state.isAnimating());
    }

    private record Route(String href, String pathname, String id) { }
    private record Visit(String key, VisitState saved) { }
    private record LoadedPage(String id, HTMLDocument document) { }
    private record Subscriber(Consumer<LoadedPage> ready, Consumer<String> failed) { }

    /** One finite load, including image decoding; failed preparations are safe to retry. */
    private final class PageLoad {
        private final Route route;
        private final List<Subscriber> subscribers = new ArrayList<>();
        private final XMLHttpRequest response = new XMLHttpRequest();
        private int timeout;
        private boolean settled;
        private LoadedPage result;
        private String failure;

        PageLoad(Route route) { this.route = route; }

        void subscribe(Consumer<LoadedPage> ready, Consumer<String> failed) {
            if (!settled) subscribers.add(new Subscriber(ready, failed));
            else if (result != null) ready.accept(result);
            else failed.accept(failure);
        }

        void start() {
            timeout = Window.setTimeout(() -> {
                reject("Page preparation timed out");
                response.abort();
            }, PREPARATION_TIMEOUT_MS);
            response.onLoad(event -> loaded());
            response.onError(event -> reject("Page could not be loaded"));
            response.onAbort(event -> reject("Page loading was interrupted"));
            response.open("GET", route.pathname(), true);
            // Revalidate once per pathname so a cached old document cannot be mixed
            // with a running Java application from another Pages deployment.
            response.setRequestHeader("Cache-Control", "no-cache");
            response.send();
        }

        void loaded() {
            if (settled) return;
            if (response.getStatus() < 200 || response.getStatus() >= 300) {
                reject("Page could not be loaded");
                return;
            }
            try {
                HTMLDocument next = (HTMLDocument) new DOMParser().parseFromString(response.getResponseText(), "text/html");
                if (!revision.equals(buildRevision(next))) {
                    reject("Website version changed");
                    return;
                }
                String id = next.getDocumentElement().getAttribute("data-page-id");
                if (!route.id().equals(id) || !PAGE_IDS.contains(id) || next.getElementById("content") == null) {
                    reject("Invalid website page");
                    return;
                }
                hooks.prepare(id, () -> resolve(new LoadedPage(id, next)), this::reject);
            } catch (RuntimeException invalidDocument) {
                reject("Invalid website page");
            }
        }

        void resolve(LoadedPage page) {
            if (settled) return;
            settled = true;
            result = page;
            Window.clearTimeout(timeout);
            List<Subscriber> waiting = new ArrayList<>(subscribers);
            subscribers.clear();
            for (Subscriber subscriber : waiting) subscriber.ready().accept(page);
        }

        void reject(String reason) {
            if (settled) return;
            settled = true;
            failure = reason;
            Window.clearTimeout(timeout);
            if (cache.get(route.pathname()) == this) cache.remove(route.pathname());
            List<Subscriber> waiting = new ArrayList<>(subscribers);
            subscribers.clear();
            for (Subscriber subscriber : waiting) subscriber.failed().accept(reason);
        }
    }

    private interface NavigationClick extends MouseEvent {
        @JSProperty boolean isDefaultPrevented();
    }

    private interface NavigationPop extends Event {
        @JSProperty HistoryState getState();
    }

    private interface Url extends JSObject {
        @JSProperty String getHref();
        @JSProperty String getOrigin();
        @JSProperty String getPathname();
        @JSProperty String getHash();
        @JSProperty SearchParameters getSearchParams();
    }

    private interface SearchParameters extends JSObject { boolean has(String name); }

    /** Structured-clone-safe history values; no Java object is passed to the browser's history. */
    private interface HistoryState extends JSObject {
        @JSProperty("valthorneVisit") void setVisit(String key);
        @JSProperty("valthorneSaved") void setSaved(SavedState saved);
    }

    private interface SavedState extends JSObject {
        @JSProperty double getScroll();
        @JSProperty void setScroll(double value);
        @JSProperty String getCategory();
        @JSProperty void setCategory(String value);
        @JSProperty double getOrbit();
        @JSProperty void setOrbit(double value);
        @JSProperty double getTilt();
        @JSProperty void setTilt(double value);
        @JSProperty boolean isAnimating();
        @JSProperty void setAnimating(boolean value);
    }

    /** Direct browser primitives missing from TeaVM's standard DOM declarations. */
    private static final class Bindings {
        @JSBody(params = { "value", "base" }, script = "return new URL(value, base);")
        static native Url url(String value, String base);
        @JSBody(params = "history", script = "history.scrollRestoration = 'manual';")
        static native void manualScrollRestoration(History history);
        // Optional native properties can be undefined, which TeaVM distinguishes
        // from Java null. Normalize at the boundary before Java inspects them.
        @JSBody(params = "value", script = "return value == null ? null : value;")
        static native JSObject present(JSObject value);
        @JSBody(params = "state", script = "return state.valthorneVisit == null ? null : state.valthorneVisit;")
        static native String visit(HistoryState state);
        @JSBody(params = "state", script = "return state.valthorneSaved == null ? null : state.valthorneSaved;")
        static native SavedState saved(HistoryState state);
        @JSBody(params = "state", script = "return Object.assign({}, state);")
        static native HistoryState copyState(JSObject state);
        @JSBody(script = "return {};")
        static native SavedState emptySaved();
        @JSBody(params = "document", script = "return document.hidden;")
        static native boolean hidden(HTMLDocument document);
        @JSBody(params = "element", script = "element.focus({preventScroll:true});")
        static native void focusWithoutScrolling(HTMLElement element);
        @JSBody(script = "return performance.now();")
        static native double now();
    }
}
