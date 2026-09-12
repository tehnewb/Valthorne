package valthorne.website;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import valthorne.Application;
import valthorne.JGL;
import valthorne.Window;
import valthorne.ui.Canvas2D;
import valthorne.ui.UIRoot;
import valthorne.ui.nodes.nano.NanoContainer;

/**
 * Public product website rendered by Valthorne's UI system.
 *
 * <p>The content column is centered, while copy follows a consistent left edge.
 * Product media, feature sections, resource rows, and code examples each have a
 * layout suited to their content. Positions are logical CSS pixels; native
 * scrolling moves the document behind a fixed engine drawing surface.</p>
 *
 * <p>BrowserBridge supplies shared content and positions real HTML controls.
 * Urbanist is loaded by the host before layout begins. Finite entrance movement
 * preserves text opacity; only the visible, enabled lab requests ongoing frames.</p>
 */
public final class WebsiteApplication implements Application {
    private static final int BACKGROUND = 0x141414, PANEL = 0x1a1a1a, SURFACE = 0x222222;
    private static final int INK = 0xffffff, MUTED = 0xc7c7cc, PRIMARY = 0x703bf7;
    private static final int LINK = 0xb99bff, LINE = 0x686868, DIVIDER = 0x363636;
    private UIRoot root;
    private NanoContainer surface;
    private float width, height, margin, contentWidth, scroll, time;
    private long vg;

    /** Entry point used by the portable TeaVM target. */
    public static void main(String[] args) { JGL.init(new WebsiteApplication(), "Valthorne", 1280, 800); }

    /** One root owns the vector context, layout, and drawing resources. */
    @Override public void init() {
        root = new UIRoot();
        surface = new NanoContainer() {
            @Override public void draw(long context) { paint(context); }
        };
        root.add(surface);
    }

    /** Recomputes responsive geometry and advances only visible scene motion. */
    @Override public void update(float delta) {
        width = Window.getWidth(); height = Window.getHeight();
        margin = width < 700 ? 24 : Math.max(40, (width - 1240) / 2);
        contentWidth = width - margin * 2; scroll = BrowserBridge.scroll();
        if (BrowserBridge.animating()) time += Math.min(delta, .05f);
        root.setSize(width, height); surface.getLayout().width(width).height(height); root.update(delta);
    }

    @Override public void render() { root.draw(); BrowserBridge.ready(); }
    @Override public void dispose() { if (root != null) root.dispose(); }

    private void paint(long context) {
        vg = context; BrowserBridge.begin();
        Canvas2D.fontFace(vg, "default");
        Canvas2D.textAlign(vg, Canvas2D.ALIGN_LEFT | Canvas2D.ALIGN_TOP);
        rect(0, 0, width, height, BACKGROUND);
        float y = (width < 900 ? 164 : 154) - scroll;
        String page = BrowserBridge.page("id");
        y = page.equals("index") ? hero(y) : introduction(y);
        for (int index = 0; index < BrowserBridge.sections(); index++) y = section(index, y);
        y = footer(y);
        header(); // Always last: the host moves these seven links to the front of tab order.
        BrowserBridge.end(y + scroll);
    }

    /** Asymmetric product introduction: concise copy beside a real desktop capture. */
    private float hero(float y) {
        BrowserBridge.reveal("intro", y);
        boolean stacked = width < 1000;
        float copyWidth = stacked ? contentWidth : contentWidth * .405f;
        text(BrowserBridge.page("eyebrow"), margin, y, 12, LINK); y += 32;
        float top = y;
        float bottom = heading(BrowserBridge.page("title"), margin, y, copyWidth, width < 700 ? 46 : 68) + 26;
        bottom = paragraph(BrowserBridge.page("description"), margin, bottom, copyWidth, 20, MUTED, 1.5f) + 28;
        float bw = Math.min(174, (copyWidth - 12) / 2);
        button("Start building", "start.html", margin, bottom, bw, true);
        button("Explore demos", "examples.html", margin + bw + 12, bottom, bw, false);
        bottom += 76;
        text("VERSION 2.0.0  /  APACHE-2.0", margin, bottom, 12, MUTED); bottom += 24;
        float imageX = stacked ? margin : margin + contentWidth * .455f;
        float imageWidth = stacked ? contentWidth : contentWidth * .545f;
        float imageY = stacked ? bottom + 32 : top + 8;
        float imageHeight = imageWidth * .625f;
        panel(imageX, imageY, imageWidth, imageHeight, SURFACE);
        BrowserBridge.image(vg, BrowserBridge.page("heroImage"), imageX + 1, imageY + 1, imageWidth - 2, imageHeight - 2);
        link(BrowserBridge.page("heroCaption") + "  →", "examples.html", imageX, imageY + imageHeight + 10, imageWidth, 13);
        BrowserBridge.clearEffect();
        y = Math.max(bottom, imageY + imageHeight + 50) + 50;
        return facts(y);
    }

    /** Product facts replace decorative badges and provide useful information at a glance. */
    private float facts(float y) {
        int columns = width < 700 ? 2 : 4;
        float h = columns == 2 ? 180 : 108;
        rect(0, y, width, h, PANEL);
        String[] values = {"2D + 3D", "Java 25", "Open source", "10 examples"};
        String[] labels = {"Graphics and simulation", "One engine dependency", "Apache License 2.0", "Ready-to-run downloads"};
        for (int i = 0; i < values.length; i++) {
            float x = margin + (i % columns) * contentWidth / columns, yy = y + 26 + (i / columns) * 80;
            weightedText(values[i], x, yy, width < 700 ? 21 : 24, INK);
            text(labels[i], x, yy + 34, width < 700 ? 11 : 13, MUTED);
        }
        return y + h + 80;
    }

    private float introduction(float y) {
        BrowserBridge.reveal("intro", y);
        text(BrowserBridge.page("eyebrow"), margin, y, 12, LINK); y += 30;
        y = heading(BrowserBridge.page("title"), margin, y, Math.min(contentWidth, 1030), width < 700 ? 42 : 62) + 24;
        y = paragraph(BrowserBridge.page("description"), margin, y, Math.min(contentWidth, 820), 19, MUTED, 1.5f) + 46;
        BrowserBridge.clearEffect();
        return y;
    }

    /** Selects a content-specific layout, rather than rendering every section as matching cards. */
    private float section(int index, float y) {
        String page = BrowserBridge.page("id"), catalog = BrowserBridge.section(index, "catalog");
        String code = BrowserBridge.section(index, "code"), layout = BrowserBridge.section(index, "layout");
        if (page.equals("docs") && index == 0) return essentials(index, y);
        if (!catalog.isEmpty()) return catalog(index, y);
        if (!code.isEmpty()) return codeSection(index, code, y);
        if (BrowserBridge.section(index, "lab").equals("true")) {
            y = lab(y) + 32;
            y = heading(BrowserBridge.section(index, "title"), margin, y, contentWidth, 26) + 16;
            return paragraph(BrowserBridge.section(index, "description"), margin, y,
                    Math.min(contentWidth, 900), 17, MUTED, 1.5f) + 70;
        }
        BrowserBridge.reveal("section-" + index, y);
        float mediaBottom = y;
        String image = BrowserBridge.section(index, "image");
        boolean feature = layout.equals("feature") && !image.isEmpty();
        float headingWidth = feature && width >= 1000 ? contentWidth * .36f : Math.min(contentWidth, 850);
        float top = y;
        y = heading(BrowserBridge.section(index, "title"), margin, y, headingWidth, width < 700 ? 32 : 42) + 20;
        String description = BrowserBridge.section(index, "description");
        if (!description.isEmpty()) y = paragraph(description, margin, y, headingWidth, 18, MUTED, 1.5f) + 26;
        if (feature) {
            float imageWidth = width >= 1000 ? contentWidth * .59f : contentWidth;
            float imageX = width >= 1000 ? margin + contentWidth - imageWidth : margin;
            float imageY = width >= 1000 ? top : y + 4;
            float imageHeight = imageWidth * .59f;
            BrowserBridge.image(vg, image, imageX, imageY, imageWidth, imageHeight);
            mediaBottom = imageY + imageHeight + 26;
        }
        BrowserBridge.clearEffect(); y = Math.max(y, mediaBottom) + 12;
        if (layout.equals("rows")) y = resourceRows(index, y, false);
        else y = cards(index, y, layout.equals("showcase"));
        return y + 76;
    }

    /** Three direct documentation entry points keep search in the first screen. */
    private float essentials(int index, float y) {
        String[] labels = {"Installation", "Migration", "Platforms"};
        float w = Math.min(165, (contentWidth - 16) / 3);
        for (int i = 0; i < 3; i++) button(labels[i], BrowserBridge.card(index, i, "href"), margin + i * (w + 8), y, w, false);
        return y + 76;
    }

    private float catalog(int index, float y) {
        boolean guides = BrowserBridge.section(index, "catalog").equals("guides");
        float searchWidth = Math.min(contentWidth, 620);
        BrowserBridge.search(margin, y, searchWidth); y += 62;
        y = filters(guides ? new String[]{"all", "graphics", "runtime", "ui", "utilities"} : new String[]{"all", "3d", "2d", "systems"}, y);
        int visible = 0, count = BrowserBridge.cards(index);
        for (int i = 0; i < count; i++) if (matches(index, i)) visible++;
        text(visible + " of " + count + (guides ? " system guides" : " desktop examples"), margin, y + 4, 14, MUTED); y += 42;
        if (visible == 0) {
            y = paragraph("No results for this search. Try another term or reset the collection.", margin, y, contentWidth, 18, MUTED, 1.5f) + 22;
            button("Clear search & filters", "#clear", margin, y, 210, false);
            return y + 110;
        }
        return (guides ? resourceRows(index, y, true) : cards(index, y, true)) + 75;
    }

    private boolean matches(int section, int card) {
        String category = BrowserBridge.category(), query = BrowserBridge.query().trim().toLowerCase(Locale.ROOT);
        return (category.equals("all") || category.equals(BrowserBridge.card(section, card, "category")))
            && (BrowserBridge.card(section, card, "title") + " " + BrowserBridge.card(section, card, "text")).toLowerCase(Locale.ROOT).contains(query);
    }

    private float cards(int section, float y, boolean visual) {
        boolean catalog = !BrowserBridge.section(section, "catalog").isEmpty();
        int columns = width < 760 ? 1 : visual || width < 1100 ? 2 : 3;
        float gap = 24, w = (contentWidth - gap * (columns - 1)) / columns;
        int[] row = new int[columns]; int used = 0;
        for (int card = 0; card < BrowserBridge.cards(section); card++) {
            if (catalog && !matches(section, card)) continue;
            row[used++] = card;
            if (used == columns) { y = cardRow(section, row, used, y, w, gap, visual); used = 0; }
        }
        if (used > 0) y = cardRow(section, row, used, y, w, gap, visual);
        return y;
    }

    /** Measures complete rows first so actions remain aligned below different text lengths. */
    private float cardRow(int section, int[] row, int count, float y, float w, float gap, boolean visual) {
        float rowHeight = 0;
        for (int i = 0; i < count; i++) {
            int card = row[i];
            float h = visual ? w * .59f : 0;
            Canvas2D.fontFace(vg, "ui-medium"); h += wrap(BrowserBridge.card(section, card, "title"), w - 40, 24).size() * 29;
            Canvas2D.fontFace(vg, "default"); h += wrap(BrowserBridge.card(section, card, "text"), w - 40, 17).size() * 25.5f;
            rowHeight = Math.max(rowHeight, h + (BrowserBridge.card(section, card, "guide").isEmpty() ? 112 : 147));
        }
        for (int i = 0; i < count; i++) {
            int card = row[i]; float x = margin + i * (w + gap);
            if (y + rowHeight < 88 || y > height) continue;
            BrowserBridge.reveal("card-" + section + "-" + card, y);
            panel(x, y, w, rowHeight, PANEL); float yy = y;
            if (visual) {
                String image = BrowserBridge.card(section, card, "image");
                if (image.isEmpty()) demoArtwork(x, y, w, w * .59f, BrowserBridge.card(section, card, "category"));
                else BrowserBridge.image(vg, image, x, y, w, w * .59f);
                yy += w * .59f;
            }
            Canvas2D.fontFace(vg, "ui-medium");
            yy = paragraph(BrowserBridge.card(section, card, "title"), x + 20, yy + 24, w - 40, 24, INK, 1.21f) + 14;
            Canvas2D.fontFace(vg, "default");
            paragraph(BrowserBridge.card(section, card, "text"), x + 20, yy, w - 40, 17, MUTED, 1.5f);
            String guide = BrowserBridge.card(section, card, "guide");
            float linkY = y + rowHeight - (guide.isEmpty() ? 51 : 86);
            link(BrowserBridge.card(section, card, "label") + "  →", BrowserBridge.card(section, card, "href"), x + 20, linkY, w - 40, 15);
            if (!guide.isEmpty()) link("Controls & source  ↗", guide, x + 20, linkY + 35, w - 40, 14);
            BrowserBridge.clearEffect();
        }
        return y + rowHeight + gap;
    }

    /** Compact directory rows provide a scan-friendly alternative to tall guide cards. */
    private float resourceRows(int section, float y, boolean filter) {
        boolean compact = width < 900;
        for (int card = 0; card < BrowserBridge.cards(section); card++) {
            if (filter && !matches(section, card)) continue;
            float titleWidth = compact ? contentWidth : contentWidth * .30f;
            float bodyWidth = compact ? contentWidth : contentWidth * .45f;
            Canvas2D.fontFace(vg, "ui-medium"); float titleHeight = wrap(BrowserBridge.card(section, card, "title"), titleWidth, 21).size() * 26;
            Canvas2D.fontFace(vg, "default"); float bodyHeight = wrap(BrowserBridge.card(section, card, "text"), bodyWidth, 16).size() * 24;
            float h = compact ? titleHeight + bodyHeight + 89 : Math.max(titleHeight, bodyHeight) + 48;
            if (y + h >= 88 && y <= height) {
                BrowserBridge.reveal("row-" + section + "-" + card, y);
                rule(margin, y, contentWidth);
                Canvas2D.fontFace(vg, "ui-medium");
                paragraph(BrowserBridge.card(section, card, "title"), margin, y + 22, titleWidth, 21, INK, 1.24f);
                Canvas2D.fontFace(vg, "default");
                float bodyX = compact ? margin : margin + contentWidth * .34f;
                float bodyY = compact ? y + titleHeight + 32 : y + 22;
                paragraph(BrowserBridge.card(section, card, "text"), bodyX, bodyY, bodyWidth, 16, MUTED, 1.5f);
                link(BrowserBridge.card(section, card, "label") + "  →", BrowserBridge.card(section, card, "href"), compact ? margin : margin + contentWidth * .83f, compact ? y + h - 43 : y + 21, compact ? contentWidth : contentWidth * .17f, 14);
                BrowserBridge.clearEffect();
            }
            y += h;
        }
        return y;
    }

    /** Instructions sit beside their source on desktop and stack naturally on phones. */
    private float codeSection(int index, String source, float y) {
        boolean compact = width < 1050;
        float introWidth = compact ? contentWidth : contentWidth * .29f;
        float top = y;
        y = heading(BrowserBridge.section(index, "title"), margin, y, introWidth, width < 700 ? 32 : 38) + 20;
        y = paragraph(BrowserBridge.section(index, "description"), margin, y, introWidth, 17, MUTED, 1.55f) + 26;
        float codeWidth = compact ? contentWidth : contentWidth * .66f, codeX = margin + contentWidth - codeWidth;
        float bottom = code(index, source, codeX, compact ? y : top, codeWidth);
        y = Math.max(y, bottom) + 24;
        y = resourceRows(index, y, false);
        return y + 75;
    }

    private float code(int index, String source, float x, float y, float w) {
        BrowserBridge.reveal("code-" + index, y);
        Canvas2D.fontFace(vg, "monospace");
        float size = width < 700 ? 13 : 14, h = 74;
        for (String line : source.split("\n", -1)) h += wrap(line.isEmpty() ? " " : line, w - 36, size).size() * size * 1.6f;
        panel(x, y, w, h, PANEL);
        rect(x, y, w, 46, SURFACE);
        text(BrowserBridge.section(index, "filename"), x + 18, y + 16, 12, MUTED);
        link(BrowserBridge.copied(index) ? "Copied!" : "Copy code", "#copy=" + index, x + w - 120, y + 8, 106, 13);
        float yy = y + 61;
        for (String line : source.split("\n", -1)) yy = paragraph(line.isEmpty() ? " " : line, x + 18, yy, w - 36, size, INK, 1.6f);
        Canvas2D.fontFace(vg, "default"); BrowserBridge.clearEffect();
        return y + h;
    }

    /** A contained optional browser experiment, separate from the product's primary presentation. */
    private float lab(float y) {
        float h = width < 700 ? 365 : 440;
        if (y + h < 120 || y > height) return y + h;
        panel(margin, y, contentWidth, h, PANEL);
        text("INTERACTIVE GEOMETRY", margin + 20, y + 18, 11, MUTED);
        CrystalScene.draw(vg, margin + 6, y + 28, contentWidth - 12, h - 120, time, BrowserBridge.orbit(), BrowserBridge.tilt());
        BrowserBridge.scene(margin + 6, y + 34, contentWidth - 12, h - 140);
        text(width < 700 ? "Drag to orbit · Arrow keys to rotate" : "Drag to orbit · Arrow keys to rotate · Home to reset", margin + 18, y + h - 86, width < 400 ? 11 : 13, MUTED);
        float bw = Math.min(170, (contentWidth - 50) / 2);
        button(!BrowserBridge.motionEnabled() ? "Motion paused" : BrowserBridge.animating() ? "Pause rotation" : "Play rotation", "#animate", margin + 18, y + h - 59, bw, true);
        button("Reset view", "#reset", margin + bw + 30, y + h - 59, bw, false);
        return y + h;
    }

    private void demoArtwork(float x, float y, float w, float h, String category) {
        rect(x, y, w, h, SURFACE);
        text(category.equals("3d") ? "FIRST-PERSON" : "JAVA APPLICATION", x + 24, y + 25, 11, LINK);
        heading(category.equals("3d") ? "FPS arena" : "Application\nstarter", x + 24, y + h * .34f, w - 48, Math.min(42, w / 10));
        text("DOWNLOADABLE EXAMPLE", x + 24, y + h - 35, 11, MUTED);
    }

    private float footer(float y) {
        y += 22; rule(margin, y, contentWidth); y += 40;
        y = heading("Build with Valthorne.", margin, y, contentWidth, width < 700 ? 32 : 40) + 20;
        y = paragraph("Start with the integration guide, or explore a working example.", margin, y, contentWidth, 18, MUTED, 1.5f) + 25;
        float bw = Math.min(170, (contentWidth - 12) / 2);
        button("Get started", "start.html", margin, y, bw, true);
        button("Browse examples", "examples.html", margin + bw + 12, y, bw, false); y += 91;
        BrowserBridge.image(vg, "banner.png", margin, y, 210, 87);
        if (width >= 700) {
            weightedText("Valthorne", margin + 255, y + 5, 18, INK);
            text("A Java game engine by Albert Beaupre.", margin + 255, y + 36, 14, MUTED);
            text("Open source under Apache License 2.0.", margin + 255, y + 62, 13, MUTED);
        } else {
            y += 105; text("A Java game engine by Albert Beaupre.", margin, y, 13, MUTED);
            text("Apache License 2.0", margin, y + 24, 12, MUTED);
        }
        return y + (width < 700 ? 115 : 165);
    }

    private void header() {
        boolean compact = width < 900;
        float barHeight = compact ? 120 : 88;
        rect(0, 0, width, barHeight, 0x101010); rule(0, barHeight, width);
        BrowserBridge.image(vg, "valthorne.png", margin, 13, 32, 48);
        weightedText("VALTHORNE", margin + 44, 28, 22, INK);
        BrowserBridge.link("Valthorne home", "index.html", margin, 13, 220, 48);
        String[] labels = {"Engine", "Demos", "Docs", compact ? "Start" : "Get started", "Lab", "About"};
        String[] pages = {"engine", "examples", "docs", "start", "lab", "about"};
        float navWidth = compact ? contentWidth : 504;
        float x = compact ? margin : width - Math.max(margin, 82) - navWidth;
        for (int i = 0; i < labels.length; i++) {
            float w = navWidth / 6, yy = compact ? 82 : 31;
            boolean active = BrowserBridge.page("id").equals(pages[i]);
            if (i == 3 && !compact) rounded(x, yy - 10, w, 38, 4, PRIMARY);
            centeredText(labels[i], x + w / 2, yy, width < 400 ? 12 : 14, INK);
            if (active) rect(x + 10, yy + 27, w - 20, 2, LINK);
            BrowserBridge.link(labels[i], pages[i] + ".html", x, yy - 10, w, 42); x += w;
        }
    }

    private void button(String label, String href, float x, float y, float w, boolean primary) {
        rounded(x, y, w, 44, 4, primary ? PRIMARY : PANEL);
        Canvas2D.color(vg, primary ? PRIMARY : LINE, 1); Canvas2D.strokeWidth(vg, 1); Canvas2D.beginPath(vg);
        Canvas2D.roundedRect(vg, x + .5f, y + .5f, w - 1, 43, 4); Canvas2D.stroke(vg);
        Canvas2D.fontFace(vg, "ui-medium"); centeredText(label, x + w / 2, y + 13, 14, INK); Canvas2D.fontFace(vg, "default");
        BrowserBridge.link(label, href, x, y, w, 44);
    }
    private void link(String label, String href, float x, float y, float w, float size) {
        text(label, x, y + 8, size, LINK); BrowserBridge.link(label, href, x, y, w, 32);
    }
    private float filters(String[] values, float y) {
        float x = margin;
        for (String value : values) {
            float w = value.length() * 8 + 27;
            if (x > margin && x + w > margin + contentWidth) { x = margin; y += 54; }
            String label = value.equals("ui") ? "UI" : value.substring(0, 1).toUpperCase(Locale.ROOT) + value.substring(1);
            button(label, "#filter=" + value, x, y, w, BrowserBridge.category().equals(value));
            x += w + 8;
        }
        return y + 58;
    }
    private void panel(float x, float y, float w, float h, int color) { rounded(x, y, w, h, 4, color); }
    private void rule(float x, float y, float w) { rect(x, y, w, 1, DIVIDER); }
    private void rect(float x, float y, float w, float h, int color) {
        Canvas2D.color(vg, color, 1); Canvas2D.beginPath(vg); Canvas2D.rect(vg, x, y, w, h); Canvas2D.fill(vg);
    }
    private void rounded(float x, float y, float w, float h, float radius, int color) {
        Canvas2D.color(vg, color, 1); Canvas2D.beginPath(vg); Canvas2D.roundedRect(vg, x, y, w, h, radius); Canvas2D.fill(vg);
    }
    private void text(String value, float x, float y, float size, int color) {
        if (y + size < 0 || y > height) return;
        Canvas2D.color(vg, color, 1); Canvas2D.fontSize(vg, size); Canvas2D.text(vg, x, y, value);
    }
    private void weightedText(String value, float x, float y, float size, int color) {
        Canvas2D.fontFace(vg, "ui-medium"); text(value, x, y, size, color); Canvas2D.fontFace(vg, "default");
    }
    private void centeredText(String value, float x, float y, float size, int color) { text(value, x - BrowserBridge.measure(vg, value, size) / 2, y, size, color); }
    private float heading(String value, float x, float y, float w, float size) {
        Canvas2D.fontFace(vg, "display"); float bottom = paragraph(value, x, y, w, size, INK, 1.08f); Canvas2D.fontFace(vg, "default"); return bottom;
    }
    private float paragraph(String value, float x, float y, float w, float size, int color, float leading) {
        for (String line : wrap(value, w, size)) { text(line, x, y, size, color); y += size * leading; }
        return y;
    }
    /** Breaks long identifiers without losing indentation or changing the source copied by the browser. */
    private List<String> wrap(String value, float w, float size) {
        List<String> lines = new ArrayList<>();
        for (String block : value.split("\n", -1)) {
            if (block.isEmpty()) { lines.add(""); continue; }
            String remaining = block;
            while (!remaining.isEmpty()) {
                int cut = 1;
                while (cut < remaining.length() && BrowserBridge.measure(vg, remaining.substring(0, cut + 1), size) <= w) cut++;
                if (cut < remaining.length()) {
                    int space = remaining.lastIndexOf(' ', cut);
                    if (space > 0 && !remaining.substring(0, space).isBlank()) cut = space;
                }
                lines.add(remaining.substring(0, cut)); remaining = remaining.substring(cut);
                if (remaining.startsWith(" ")) remaining = remaining.substring(1);
            }
        }
        return lines;
    }
}
