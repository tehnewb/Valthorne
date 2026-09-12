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
    // Gold metal, blue flame, and dark steel sampled visually from the original branding.
    private static final int BACKGROUND = 0x070e19, PANEL = 0x101d2c;
    private static final int INK = 0xf5eee2, MUTED = 0xb1c0d2, ACCENT = 0xe2ba79;
    private static final int BLUE = 0x65beff, LINE = 0x293e57;
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
        margin = width < 700 ? 22 : Math.max(42, (width - 1160) / 2);
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
        float y = (width < 900 ? 162 : 144) - scroll;
        boolean home = BrowserBridge.page("id").equals("index");
        if (home) y = hero(y); else {
            centeredText(BrowserBridge.page("eyebrow"), width / 2, y, 11, BLUE); y += 36;
            y = heading(BrowserBridge.page("title"), y, Math.min(contentWidth, 920), width < 700 ? 39 : 62) + 24;
            y = centeredParagraph(BrowserBridge.page("description"), width / 2, y, Math.min(contentWidth, 740), 18, MUTED, 1.6f) + 55;
        }
        for (int section = 0; section < BrowserBridge.sections(); section++) y = section(section, y);
        ornament(y); y += 45;
        y = heading("Your next world starts here.", y, contentWidth, width < 700 ? 32 : 46) + 30;
        button("Start building", "start.html", (width - 180) / 2, y, 180, true); y += 82;
        centeredText("VALTHORNE  /  JAVA  /  APACHE-2.0", width / 2, y, 11, ACCENT); y += 26;
        y = centeredParagraph("Created by Albert Beaupre. Built with Valthorne.", width / 2, y, contentWidth, 13, MUTED, 1.5f) + 60;
        header();
        BrowserBridge.end(y + scroll);
    }

    /** Centered brand presentation. Original artwork is displayed without cropping or recoloring. */
    private float hero(float y) {
        centeredText("OPEN SOURCE JAVA GAME ENGINE", width / 2, y, 11, BLUE); y += 28;
        float bannerWidth = Math.min(contentWidth, 860), bannerHeight = bannerWidth * 623 / 1511;
        BrowserBridge.image(vg, "banner.png", (width - bannerWidth) / 2, y, bannerWidth, bannerHeight);
        y += bannerHeight + (width < 700 ? 28 : 18);
        y = heading("Build your next world.", y, contentWidth, width < 700 ? 39 : 57) + 20;
        y = centeredParagraph(BrowserBridge.page("description"), width / 2, y, Math.min(contentWidth, 700), 18, MUTED, 1.6f) + 28;
        float buttonWidth = Math.min(180, (contentWidth - 14) / 2);
        button("Start building", "start.html", width / 2 - buttonWidth - 7, y, buttonWidth, true);
        button("Explore demos", "examples.html", width / 2 + 7, y, buttonWidth, false); y += 76;
        centeredText("DESKTOP 2D + 3D   ·   VERSION 2.0.0", width / 2, y, 11, MUTED); y += 52;
        ornament(y); y += 35;
        String[] values = {"2D + 3D", "JAVA 25", "OPEN SOURCE", "10 DEMOS"};
        String[] notes = {"One engine library", "Your application", "Apache License 2.0", "Download and explore"};
        int columns = width < 700 ? 2 : 4;
        for (int i = 0; i < 4; i++) {
            float x = margin + (i % columns + .5f) * contentWidth / columns, yy = y + (i / columns) * 72;
            centeredText(values[i], x, yy, 14, ACCENT); centeredText(notes[i], x, yy + 26, 12, MUTED);
        }
        return y + (columns == 2 ? 170 : 100);
    }

    private float section(int index, float y) {
        ornament(y); y += 44;
        y = heading(BrowserBridge.section(index, "title"), y, Math.min(contentWidth, 900), width < 700 ? 30 : 42) + 22;
        String description = BrowserBridge.section(index, "description");
        if (!description.isEmpty()) y = centeredParagraph(description, width / 2, y, Math.min(contentWidth, 760), 17, MUTED, 1.6f) + 30;
        boolean catalog = !BrowserBridge.section(index, "catalog").isEmpty();
        if (catalog) {
            float searchWidth = Math.min(contentWidth, 560);
            BrowserBridge.search((width - searchWidth) / 2, y, searchWidth); y += 62;
            String[] filters = BrowserBridge.section(index, "catalog").equals("examples")
                ? new String[]{"all", "3d", "2d", "systems"} : new String[]{"all", "graphics", "runtime", "ui", "utilities"};
            y = filters(filters, y) + 24;
        }
        String code = BrowserBridge.section(index, "code");
        if (!code.isEmpty()) y = code(index, code, y);
        if (BrowserBridge.page("id").equals("lab") && index == 0) y = lab(y);
        int count = BrowserBridge.cards(index), visible = 0;
        boolean images = false;
        for (int card = 0; card < count; card++) images |= !BrowserBridge.card(index, card, "image").isEmpty();
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
            y = centeredParagraph(message, width / 2, y + 8, contentWidth, 14, MUTED, 1.5f) + 24;
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
            // A partially populated row remains balanced around the same page center.
            int card = cards[i]; float x = (width - (count * w + (count - 1) * gap)) / 2 + i * (w + gap);
            if (y + rowHeight < 88 || y > height) continue;
            panel(x, y, w, rowHeight); float yy = y;
            String image = BrowserBridge.card(section, card, "image");
            if (!image.isEmpty()) { BrowserBridge.image(vg, image, x, yy, w, w * .57f); yy += w * .57f; }
            yy = centeredParagraph(BrowserBridge.card(section, card, "title"), x + w / 2, yy + 23, w - 40, 23, INK, 1.17f) + 15;
            centeredParagraph(BrowserBridge.card(section, card, "text"), x + w / 2, yy, w - 40, 15, MUTED, 1.53f);
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
        panel(margin, y, contentWidth, h);
        link("Copy code  ↗", "#copy=" + index, margin + 18, y + 12, 160);
        float yy = y + 50;
        for (String line : lines) yy = paragraph(line.isEmpty() ? " " : line, margin + 18, yy, contentWidth - 38, fontSize, ACCENT, 1.6f);
        return y + h + 24;
    }

    /** A small Java-projected wireframe drawing, deliberately opt-in and inexpensive. */
    private float lab(float y) {
        float h = width < 700 ? 330 : 440;
        panel(margin, y, contentWidth, h);
        float cx = width / 2, cy = y + h / 2, scale = Math.min(contentWidth * .3f, 150);
        for (int ring = 0; ring < 3; ring++) {
            int ink = ring == 0 ? ACCENT : ring == 1 ? BLUE : 0x35618b;
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
        centeredText("JAVA / CANVAS2D / LIVE", width / 2, y + 18, 11, BLUE);
        button(BrowserBridge.animating() ? "Pause animation" : "Start animation", "#animate", (width - 190) / 2, y + h - 66, 190, true);
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
        boolean compact = width < 900;
        float barHeight = compact ? 120 : 88;
        rect(0, 0, width, barHeight, BACKGROUND); rule(0, barHeight, width);
        float brandX = compact ? (width - 208) / 2 : (width - 772) / 2;
        BrowserBridge.image(vg, "valthorne.png", brandX, compact ? 8 : 14, 38, 57);
        Canvas2D.fontFace(vg, "Georgia");
        text("VALTHORNE", brandX + 49, compact ? 25 : 31, 22, ACCENT);
        Canvas2D.fontFace(vg, "default");
        BrowserBridge.link("Valthorne home", "index.html", brandX, compact ? 8 : 14, 216, 57);
        String[] labels = {"Engine", "Demos", "Docs", "Start", "Lab", "About"};
        String[] pages = {"engine", "examples", "docs", "start", "lab", "about"};
        float x = compact ? margin : brandX + 280;
        for (int i = 0; i < labels.length; i++) {
            float w = compact ? contentWidth / 6 : 82, yy = compact ? 86 : 37;
            boolean active = BrowserBridge.page("id").equals(pages[i]);
            centeredText(labels[i], x + w / 2, yy, width < 400 ? 11 : 13, active ? ACCENT : MUTED);
            if (active) rect(x + w / 2 - 12, yy + 25, 24, 2, ACCENT);
            BrowserBridge.link(labels[i], pages[i] + ".html", x, yy - 10, w, 42); x += w;
        }
    }

    private void button(String label, String href, float x, float y, float w, boolean primary) {
        rect(x, y, w, 44, primary ? ACCENT : PANEL);
        if (!primary) { Canvas2D.color(vg, BLUE, .65f); Canvas2D.strokeWidth(vg, 1); Canvas2D.beginPath(vg); Canvas2D.rect(vg, x, y, w, 44); Canvas2D.stroke(vg); }
        centeredText(label, x + w / 2, y + 14, 13, primary ? BACKGROUND : INK);
        BrowserBridge.link(label, href, x, y, w, 44);
    }
    private void link(String label, String href, float x, float y, float w) {
        centeredText(label, x + w / 2, y + 7, 14, BLUE); BrowserBridge.link(label, href, x, y, w, 32);
    }
    /** Wraps filter controls as centered rows, including narrow phone layouts. */
    private float filters(String[] values, float y) {
        int first = 0;
        while (first < values.length) {
            int end = first; float rowWidth = 0;
            while (end < values.length) {
                float next = values[end].length() * 8 + 25;
                if (end > first && rowWidth + 7 + next > contentWidth) break;
                rowWidth += (end == first ? 0 : 7) + next; end++;
            }
            float x = (width - rowWidth) / 2;
            for (int i = first; i < end; i++) {
                float w = values[i].length() * 8 + 25;
                button(values[i].toUpperCase(), "#filter=" + values[i], x, y, w, BrowserBridge.category().equals(values[i])); x += w + 7;
            }
            first = end; y += 53;
        }
        return y;
    }
    private void panel(float x, float y, float w, float h) {
        rect(x, y, w, h, PANEL);
        Canvas2D.color(vg, LINE, 1); Canvas2D.strokeWidth(vg, 1); Canvas2D.beginPath(vg);
        Canvas2D.rect(vg, x + .5f, y + .5f, w - 1, h - 1); Canvas2D.stroke(vg);
    }
    /** A restrained gold diamond and blue rules echo the metalwork around the banner. */
    private void ornament(float y) {
        float half = Math.min(contentWidth / 2 - 24, 150);
        rect(width / 2 - half - 18, y, half, 1, LINE); rect(width / 2 + 18, y, half, 1, LINE);
        Canvas2D.color(vg, ACCENT, 1); Canvas2D.beginPath(vg);
        Canvas2D.moveTo(vg, width / 2, y - 4); Canvas2D.lineTo(vg, width / 2 + 4, y);
        Canvas2D.lineTo(vg, width / 2, y + 4); Canvas2D.lineTo(vg, width / 2 - 4, y); Canvas2D.fill(vg);
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
    private void centeredText(String value, float center, float y, float size, int color) {
        text(value, center - BrowserBridge.measure(vg, value, size) / 2, y, size, color);
    }
    private float centeredParagraph(String value, float center, float y, float w, float size, int color, float leading) {
        for (String line : wrap(value, w, size)) { centeredText(line, center, y, size, color); y += size * leading; }
        return y;
    }
    private float heading(String value, float y, float w, float size) {
        Canvas2D.fontFace(vg, "Georgia");
        float bottom = centeredParagraph(value, width / 2, y, w, size, INK, 1.15f);
        Canvas2D.fontFace(vg, "default");
        return bottom;
    }
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
