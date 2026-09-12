package valthorne.website;

import valthorne.Application;
import valthorne.JGL;
import valthorne.Window;
import valthorne.ui.Canvas2D;
import valthorne.ui.UIRoot;
import valthorne.ui.nodes.nano.NanoContainer;

/**
 * Valthorne's public website, rendered by its own UI system. All positions are
 * logical CSS pixels in top-left coordinates. Browser scrolling moves the
 * document beneath a fixed engine viewport; only visible cards are painted.
 * Content and its semantic HTML companion share one source (content.json).
 *
 * <p>The host invokes the normal JGL frame callback on demand. The optional lab
 * animation is the only continuous workload. UIRoot owns its rendering context
 * and is disposed with the application.</p>
 */
public final class WebsiteApplication implements Application {
    private static final int BACKGROUND = 0x101211, PANEL = 0x191c19;
    private static final int INK = 0xf1f0e6, MUTED = 0xaeb6a8, ACCENT = 0xd5ec84, LINE = 0x343b32;
    private UIRoot root;
    private NanoContainer surface;
    private float width, height, margin, contentWidth, scroll, time;
    private long vg;

    /** Entry point selected by the portable TeaVM build. */
    public static void main(String[] args) {
        JGL.init(new WebsiteApplication(), "Valthorne", 1280, 800);
    }

    /** Creates one root and one custom NanoVG painter; no game assets are loaded. */
    @Override public void init() {
        root = new UIRoot();
        surface = new NanoContainer() {
            @Override public void draw(long context) { paint(context); }
        };
        root.add(surface);
    }

    /** Updates responsive geometry; animation time advances only on opt-in frames. */
    @Override public void update(float delta) {
        width = Window.getWidth(); height = Window.getHeight();
        margin = width < 700 ? 22 : Math.max(42, (width - 1280) / 2);
        contentWidth = width - margin * 2; scroll = BrowserBridge.scroll();
        if (BrowserBridge.animating()) time += Math.min(delta, .05f);
        root.setSize(width, height);
        surface.getLayout().width(width).height(height);
        root.update(delta);
    }

    /** Draws one complete frame. The host retains it until another frame is needed. */
    @Override public void render() { root.draw(); BrowserBridge.ready(); }

    /** Releases root-owned NanoVG, Yoga, and batch resources. */
    @Override public void dispose() { if (root != null) root.dispose(); }

    private void paint(long context) {
        vg = context; BrowserBridge.begin();
        Canvas2D.fontFace(vg, "default");
        Canvas2D.textAlign(vg, Canvas2D.ALIGN_LEFT | Canvas2D.ALIGN_TOP);
        rect(0, 0, width, height, BACKGROUND);
        float y = 142 - scroll;
        boolean home = BrowserBridge.page("id").equals("index");
        if (home) y = hero(y); else {
            text(BrowserBridge.page("eyebrow"), margin, y, 12, ACCENT); y += 38;
            y = paragraph(BrowserBridge.page("title"), margin, y, Math.min(contentWidth, 1050), width < 700 ? 43 : 72, INK, 1.04f) + 24;
            y = paragraph(BrowserBridge.page("description"), margin, y, Math.min(contentWidth, 820), 19, MUTED, 1.55f) + 48;
        }
        for (int section = 0; section < BrowserBridge.sections(); section++) y = section(section, y);
        rule(margin, y, contentWidth); y += 35;
        y = paragraph("Build something only you could make.", margin, y, contentWidth, width < 700 ? 30 : 48, INK, 1.1f) + 30;
        button("Start building", "start.html", margin, y, 175, true); y += 85;
        text("VALTHORNE / APACHE-2.0 / JAVA", margin, y, 11, MUTED); y += 28;
        y = paragraph("Created by Albert Beaupre. Drawn with Valthorne.", margin, y, contentWidth, 13, MUTED, 1.5f) + 50;
        header();
        BrowserBridge.end(y + scroll);
    }

    /** Editorial landing layout with an authentic engine capture and clear entry points. */
    private float hero(float y) {
        float top = y;
        text("OPEN SOURCE JAVA GAME ENGINE", margin, y, 11, ACCENT); y += 40;
        float titleSize = width < 700 ? 61 : width < 1100 ? 68 : 92;
        text("Your world.", margin, y, titleSize, INK); y += titleSize * 1.04f;
        text("Your rules.", margin, y, titleSize, ACCENT); y += titleSize * 1.22f;
        float column = width < 900 ? contentWidth : contentWidth * .45f;
        y = paragraph(BrowserBridge.page("description"), margin, y, column, 19, MUTED, 1.55f) + 28;
        button("Start building", "start.html", margin, y, 164, true);
        button("Explore demos", "examples.html", margin + 176, y, Math.min(164, contentWidth - 176), false); y += 78;
        text("Java 25   /   Desktop 2D + 3D   /   v2.0.0", margin, y, 11, MUTED); y += 42;
        float pictureX = width < 900 ? margin : margin + contentWidth * .52f;
        float pictureY = width < 900 ? y : top + 12;
        float pictureWidth = width < 900 ? contentWidth : contentWidth * .48f;
        float pictureHeight = pictureWidth * .77f;
        rect(pictureX, pictureY, pictureWidth, pictureHeight + 70, PANEL);
        text("VALTHORNE / IN THE FIELD", pictureX + 18, pictureY + 14, 10, MUTED);
        BrowserBridge.image(vg, "fps.png", pictureX, pictureY + 38, pictureWidth, pictureHeight);
        text("FPS ARENA", pictureX + 16, pictureY + pictureHeight + 48, 10, ACCENT);
        BrowserBridge.link("Explore the FPS arena demo", "examples.html?q=fps", pictureX, pictureY, pictureWidth, pictureHeight + 70);
        y = Math.max(y, pictureY + pictureHeight + 116);
        rule(margin, y, contentWidth); y += 30;
        String[] values = {"2D + 3D", "JAVA 25", "OPEN SOURCE", "10 DEMOS"};
        String[] notes = {"One engine library", "Your application", "Apache License 2.0", "Download and explore"};
        int columns = width < 700 ? 2 : 4;
        for (int i = 0; i < 4; i++) {
            float x = margin + (i % columns) * contentWidth / columns, yy = y + (i / columns) * 72;
            text(values[i], x, yy, 15, INK); text(notes[i], x, yy + 26, 12, MUTED);
        }
        return y + (columns == 2 ? 170 : 100);
    }

    private float section(int index, float y) {
        rule(margin, y, contentWidth); y += 38;
        y = paragraph(BrowserBridge.section(index, "title"), margin, y, contentWidth, width < 700 ? 31 : 43, INK, 1.15f) + 22;
        String description = BrowserBridge.section(index, "description");
        if (!description.isEmpty()) y = paragraph(description, margin, y, Math.min(contentWidth, 850), 17, MUTED, 1.55f) + 28;
        boolean catalog = !BrowserBridge.section(index, "catalog").isEmpty();
        if (catalog) {
            BrowserBridge.search(margin, y, Math.min(contentWidth, 560)); y += 62;
            String[] filters = BrowserBridge.section(index, "catalog").equals("examples")
                ? new String[]{"all", "3d", "2d", "systems"} : new String[]{"all", "graphics", "runtime", "ui", "utilities"};
            float x = margin;
            for (String filter : filters) {
                float w = filter.length() * 8 + 25;
                button(filter.toUpperCase(), "#filter=" + filter, x, y, w, BrowserBridge.category().equals(filter)); x += w + 7;
                if (x + 95 > width - margin && !filter.equals(filters[filters.length - 1])) { x = margin; y += 50; }
            }
            y += 68;
        }
        String code = BrowserBridge.section(index, "code");
        if (!code.isEmpty()) y = code(index, code, y);
        if (BrowserBridge.page("id").equals("lab") && index == 0) y = lab(y);
        int count = BrowserBridge.cards(index), visible = 0;
        boolean images = count > 0 && !BrowserBridge.card(index, 0, "image").isEmpty();
        int columns = width < 760 ? 1 : images || width < 1100 ? 2 : 3;
        float gap = 20, cardWidth = (contentWidth - gap * (columns - 1)) / columns;
        int[] row = new int[columns]; int used = 0;
        for (int card = 0; card < count; card++) {
            if (catalog && !matches(index, card)) continue;
            visible++; row[used++] = card;
            if (used == columns) { y = cardRow(index, row, used, y, cardWidth, gap); used = 0; }
        }
        if (used > 0) y = cardRow(index, row, used, y, cardWidth, gap);
        if (catalog) {
            String message = visible == 0 ? "No matches. Try another search or choose ALL." : visible + " of " + count + " " + BrowserBridge.section(index, "catalog");
            y = paragraph(message, margin, y + 8, contentWidth, 14, MUTED, 1.5f) + 24;
        }
        return y + 50;
    }

    private boolean matches(int section, int card) {
        String category = BrowserBridge.category(), query = BrowserBridge.query().trim().toLowerCase(java.util.Locale.ROOT);
        return (category.equals("all") || category.equals(BrowserBridge.card(section, card, "category")))
            && (BrowserBridge.card(section, card, "title") + " " + BrowserBridge.card(section, card, "text")).toLowerCase(java.util.Locale.ROOT).contains(query);
    }

    /** Computes a common row height before drawing so wrapped text never overlaps links. */
    private float cardRow(int section, int[] cards, int count, float y, float w, float gap) {
        float rowHeight = 0;
        for (int i = 0; i < count; i++) {
            int card = cards[i];
            float h = BrowserBridge.card(section, card, "image").isEmpty() ? 0 : w * .57f;
            h += lineCount(BrowserBridge.card(section, card, "title"), w - 40, 23) * 27;
            h += lineCount(BrowserBridge.card(section, card, "text"), w - 40, 15) * 23;
            h += BrowserBridge.card(section, card, "guide").isEmpty() ? 108 : 141;
            rowHeight = Math.max(rowHeight, h);
        }
        for (int i = 0; i < count; i++) {
            int card = cards[i]; float x = margin + i * (w + gap);
            if (y + rowHeight < 88 || y > height) continue;
            rect(x, y, w, rowHeight, PANEL); float yy = y;
            String image = BrowserBridge.card(section, card, "image");
            if (!image.isEmpty()) { BrowserBridge.image(vg, image, x, yy, w, w * .57f); yy += w * .57f; }
            yy = paragraph(BrowserBridge.card(section, card, "title"), x + 20, yy + 23, w - 40, 23, INK, 1.17f) + 15;
            paragraph(BrowserBridge.card(section, card, "text"), x + 20, yy, w - 40, 15, MUTED, 1.53f);
            String guide = BrowserBridge.card(section, card, "guide");
            float linkY = y + rowHeight - (guide.isEmpty() ? 49 : 81);
            link(BrowserBridge.card(section, card, "label") + "  →", BrowserBridge.card(section, card, "href"), x + 20, linkY, w - 40);
            if (!guide.isEmpty()) link("Controls & source  ↗", guide, x + 20, linkY + 32, w - 40);
        }
        return y + rowHeight + gap;
    }

    private float code(int index, String source, float y) {
        String[] lines = source.split("\n"); float fontSize = width < 700 ? 11 : 14;
        float h = 68;
        for (String line : lines) h += lineCount(line.isEmpty() ? " " : line, contentWidth - 38, fontSize) * (fontSize * 1.6f);
        rect(margin, y, contentWidth, h, PANEL);
        link("Copy code  ↗", "#copy=" + index, margin + 18, y + 12, 160);
        float yy = y + 50;
        for (String line : lines) yy = paragraph(line.isEmpty() ? " " : line, margin + 18, yy, contentWidth - 38, fontSize, ACCENT, 1.6f);
        return y + h + 24;
    }

    /** A small Java-projected wireframe drawing, deliberately opt-in and inexpensive. */
    private float lab(float y) {
        float h = width < 700 ? 330 : 440;
        rect(margin, y, contentWidth, h, PANEL);
        float cx = width / 2, cy = y + h / 2, scale = Math.min(contentWidth * .3f, 150);
        for (int ring = 0; ring < 3; ring++) {
            int ink = ring == 0 ? ACCENT : ring == 1 ? 0x68bca3 : 0x4a7365;
            float rotation = time * (.25f + ring * .08f) + ring * .55f;
            for (int edge = 0; edge < 12; edge++) {
                int a = edge < 4 ? edge : edge < 8 ? edge : edge - 8;
                int b = edge < 4 ? (edge + 1) % 4 : edge < 8 ? 4 + (edge - 3) % 4 : edge - 4;
                float[] p = project(a, rotation, scale * (1 - ring * .2f));
                float[] q = project(b, rotation, scale * (1 - ring * .2f));
                Canvas2D.color(vg, ink, 1); Canvas2D.strokeWidth(vg, 1.5f); Canvas2D.beginPath(vg);
                Canvas2D.moveTo(vg, cx + p[0], cy + p[1]); Canvas2D.lineTo(vg, cx + q[0], cy + q[1]); Canvas2D.stroke(vg);
            }
        }
        text("JAVA / CANVAS2D / LIVE", margin + 20, y + 18, 11, MUTED);
        button(BrowserBridge.animating() ? "Pause animation" : "Start animation", "#animate", margin + 18, y + h - 66, 190, true);
        return y + h + 30;
    }

    private float[] project(int vertex, float rotation, float scale) {
        float x = (vertex % 4 == 0 || vertex % 4 == 3) ? -1 : 1;
        float z = vertex % 4 < 2 ? -1 : 1, y = vertex < 4 ? -1 : 1;
        float xx = (float)(x * Math.cos(rotation) - z * Math.sin(rotation));
        float zz = (float)(x * Math.sin(rotation) + z * Math.cos(rotation));
        float yy = y * .8f - zz * .45f, depth = 4 + y * .45f + zz * .8f;
        return new float[]{xx * scale * 3 / depth, yy * scale * 3 / depth};
    }

    private void header() {
        float barHeight = width < 760 ? 102 : 80;
        rect(0, 0, width, barHeight, BACKGROUND); rule(0, barHeight, width);
        text("V / VALTHORNE", margin, 25, 17, INK);
        BrowserBridge.link("Valthorne home", "index.html", margin, 12, 185, 44);
        String[] labels = {"Engine", "Demos", "Docs", "Start", "Lab", "About"};
        String[] pages = {"engine", "examples", "docs", "start", "lab", "about"};
        float x = width < 760 ? margin : Math.max(margin + 225, width - margin - 462);
        for (int i = 0; i < labels.length; i++) {
            float w = width < 760 ? contentWidth / 6 : 77, yy = width < 760 ? 66 : 29;
            text(labels[i], x, yy, width < 400 ? 11 : 13, BrowserBridge.page("id").equals(pages[i]) ? ACCENT : MUTED);
            BrowserBridge.link(labels[i], pages[i] + ".html", x - 4, yy - 12, w, 42); x += w;
        }
    }

    private void button(String label, String href, float x, float y, float w, boolean primary) {
        rect(x, y, w, 44, primary ? ACCENT : LINE);
        text(label, x + 13, y + 14, 13, primary ? BACKGROUND : INK);
        BrowserBridge.link(label, href, x, y, w, 44);
    }
    private void link(String label, String href, float x, float y, float w) {
        text(label, x, y + 7, 14, ACCENT); BrowserBridge.link(label, href, x, y, w, 32);
    }
    private void rect(float x, float y, float w, float h, int color) {
        Canvas2D.color(vg, color, 1); Canvas2D.beginPath(vg); Canvas2D.rect(vg, x, y, w, h); Canvas2D.fill(vg);
    }
    private void rule(float x, float y, float w) { rect(x, y, w, 1, LINE); }
    private void text(String value, float x, float y, float size, int color) {
        if (y + size < 0 || y > height) return;
        Canvas2D.color(vg, color, 1); Canvas2D.fontSize(vg, size); Canvas2D.text(vg, x, y, value);
    }
    private int lineCount(String value, float w, float size) { return wrap(value, w, size).size(); }
    private float paragraph(String value, float x, float y, float w, float size, int color, float leading) {
        for (String line : wrap(value, w, size)) { text(line, x, y, size, color); y += size * leading; }
        return y;
    }
    /** Word wrapping also breaks long coordinates and identifiers to fit narrow screens. */
    private java.util.List<String> wrap(String value, float w, float size) {
        var lines = new java.util.ArrayList<String>();
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
                lines.add(remaining.substring(0, cut));
                remaining = remaining.substring(cut);
                if (remaining.startsWith(" ")) remaining = remaining.substring(1);
            }
        }
        return lines;
    }
}
