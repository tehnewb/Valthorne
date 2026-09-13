package valthorne.website;

import java.util.ArrayList;
import java.util.List;
import valthorne.Application;
import valthorne.JGL;
import valthorne.Window;
import valthorne.ui.Canvas2D;
import valthorne.ui.UIRoot;
import valthorne.ui.nodes.nano.NanoContainer;
import valthorne.website.content.LandingContent;

/**
 * The single Valthorne landing page, composed and painted in Java.
 *
 * <p>A wide illustrated hero leads into open editorial sections. The Java HTML
 * exporter uses the same copy. Logical pixel geometry follows the viewport;
 * native browser anchors supply focus and link semantics.</p>
 */
public final class WebsiteApplication implements Application {
    private static final int DARK = 0x101114, PAPER = 0xf3f3ef, INK = 0x151619;
    private static final int WHITE = 0xffffff, MUTED = 0xbfc3cb, PAPER_MUTED = 0x575b62;
    private static final int ACCENT = 0xc7e6ff, RULE = 0x34373d, PAPER_RULE = 0xc9cbc9;
    private UIRoot root;
    private NanoContainer surface;
    private float width, height, margin, contentWidth, scroll;
    private long vg;

    /** TeaVM calls the Java entry point after loading the browser port. */
    public static void main(String[] args) {
        WebsiteController.launch(() -> JGL.init(new WebsiteApplication(), "Valthorne", 1440, 980));
    }

    @Override
    public void init() {
        root = new UIRoot();
        surface = new NanoContainer() {
            @Override public void draw(long context) { paint(context); }
        };
        root.add(surface);
    }

    @Override
    public void update(float delta) {
        width = Window.getWidth();
        height = Window.getHeight();
        margin = width < 760 ? 24 : Math.max(40, (width - 1320) / 2);
        contentWidth = width - 2 * margin;
        scroll = BrowserBridge.scroll();
        root.setSize(width, height);
        surface.getLayout().width(width).height(height);
        root.update(delta);
    }

    @Override public void render() { root.draw(); BrowserBridge.ready(); }
    @Override public void dispose() { if (root != null) root.dispose(); }

    private void paint(long context) {
        vg = context;
        BrowserBridge.begin();
        Canvas2D.textAlign(vg, Canvas2D.ALIGN_LEFT | Canvas2D.ALIGN_TOP);
        Canvas2D.fontFace(vg, "default");
        fill(0, 0, width, height, DARK);
        BrowserBridge.section("top", 0);
        float y = hero(headerHeight() - scroll);
        y = engine(y);
        y = resources(y);
        y = start(y);
        y = footer(y);
        header();
        BrowserBridge.end(y + scroll);
    }

    /** Artwork occupies the whole opening composition instead of a boxed preview. */
    private float hero(float y) {
        boolean mobile = width < 760;
        float h = mobile ? 790 : Math.min(860, Math.max(690, height - 110));
        BrowserBridge.image(vg, "world.svg", 0, y, width, h);
        if (mobile) {
            BrowserBridge.shade(vg, 0, y, width, h, DARK, .15f, .97f);
        }
        float top = y + (mobile ? 280 : h * .20f);
        BrowserBridge.reveal("hero", top);
        text(LandingContent.EYEBROW, margin, top, 12, ACCENT, "ui-medium");
        float copyWidth = mobile ? contentWidth : Math.min(contentWidth * .53f, 700);
        float font = mobile ? 76 : width < 1100 ? 102 : 130;
        float bottom = paragraph(LandingContent.TITLE.replace(" yours.", "\nyours."), margin, top + 37, copyWidth, font, WHITE, 1.00f, "display") + 24;
        bottom = paragraph(LandingContent.DESCRIPTION, margin, bottom, Math.min(copyWidth, 510), mobile ? 18 : 20, WHITE, 1.45f, "default") + 29;
        action("Get started", LandingContent.GET_STARTED, margin, bottom, 151, true, false);
        action("Explore engine", "#engine", margin + 167, bottom, mobile ? contentWidth - 167 : 176, false, false);
        BrowserBridge.clearEffect();
        text("OPEN SOURCE / APACHE 2.0", margin, y + h - 40, 11, 0xd5d8dd, "ui-medium");
        if (!mobile) rightText("BUILT FOR JAVA DEVELOPERS", width - margin, y + h - 40, 11, 0xd5d8dd, "ui-medium");
        return y + h;
    }

    /** Large type and ruled rows replace the previous repeated gallery cards. */
    private float engine(float y) {
        BrowserBridge.section("engine", y + scroll);
        float start = y, top = y + (width < 760 ? 66 : 96);
        float headingWidth = width < 1000 ? contentWidth : contentWidth * .54f;
        float headingSize = width < 760 ? 43 : 60;
        float introHeight = textHeight(LandingContent.INTRO_TITLE, headingWidth, headingSize, 1.08f, "display");
        float descWidth = width < 1000 ? contentWidth : contentWidth * .34f;
        float descHeight = textHeight(LandingContent.INTRO_TEXT, descWidth, 19, 1.55f, "default");
        float rowsY = width < 1000 ? top + introHeight + 25 + descHeight + 52 : top + Math.max(introHeight, descHeight) + 76;
        float end = rowsY;
        for (var feature : LandingContent.FEATURES) end += featureHeight(feature.title(), feature.text());
        end += width < 760 ? 64 : 96;
        fill(0, start, width, end - start, PAPER);
        BrowserBridge.reveal("engine-intro", top);
        paragraph(LandingContent.INTRO_TITLE, margin, top, headingWidth, headingSize, INK, 1.08f, "display");
        paragraph(LandingContent.INTRO_TEXT, width < 1000 ? margin : margin + contentWidth * .66f,
                width < 1000 ? top + introHeight + 25 : top + 8, descWidth, 19, PAPER_MUTED, 1.55f, "default");
        BrowserBridge.clearEffect();
        for (var feature : LandingContent.FEATURES) {
            float rowHeight = featureHeight(feature.title(), feature.text());
            line(margin, rowsY, width - margin, rowsY, PAPER_RULE);
            BrowserBridge.reveal("feature-" + feature.number(), rowsY + 32);
            if (width < 1000) {
                text(feature.number(), margin, rowsY + 29, 12, PAPER_MUTED, "ui-medium");
                float bottom = paragraph(feature.title(), margin, rowsY + 63, contentWidth - 34, 31, INK, 1.12f, "ui-medium") + 17;
                paragraph(feature.text(), margin, bottom, contentWidth, 17, PAPER_MUTED, 1.5f, "default");
                arrow(width - margin - 19, rowsY + 69, INK);
            } else {
                text(feature.number(), margin, rowsY + 39, 12, PAPER_MUTED, "ui-medium");
                paragraph(feature.title(), margin + contentWidth * .20f, rowsY + 32, contentWidth * .34f, 32, INK, 1.15f, "ui-medium");
                paragraph(feature.text(), margin + contentWidth * .62f, rowsY + 33, contentWidth * .32f, 17, PAPER_MUTED, 1.5f, "default");
                arrow(width - margin - 19, rowsY + 40, INK);
            }
            BrowserBridge.link(feature.title(), feature.href(), margin, rowsY + 15, contentWidth, rowHeight - 25, false, false);
            BrowserBridge.clearEffect();
            rowsY += rowHeight;
        }
        line(margin, rowsY, width - margin, rowsY, PAPER_RULE);
        return end;
    }

    private float featureHeight(String title, String description) {
        if (width < 1000)
            return 63 + textHeight(title, contentWidth - 34, 31, 1.12f, "ui-medium")
                    + 17 + textHeight(description, contentWidth, 17, 1.5f, "default") + 34;
        return Math.max(145, Math.max(textHeight(title, contentWidth * .34f, 32, 1.15f, "ui-medium"),
                textHeight(description, contentWidth * .32f, 17, 1.5f, "default")) + 66);
    }

    private float resources(float y) {
        BrowserBridge.section("resources", y + scroll);
        y += width < 760 ? 70 : 100;
        BrowserBridge.reveal("resources-heading", y);
        text("GO FROM IDEA TO FIRST FRAME", margin, y, 12, ACCENT, "ui-medium");
        y = paragraph(LandingContent.RESOURCES_TITLE, margin, y + 34, contentWidth, width < 760 ? 43 : 60, WHITE, 1.08f, "display") + 24;
        y = paragraph(LandingContent.RESOURCES_TEXT, margin, y, Math.min(contentWidth, 660), 19, MUTED, 1.5f, "default") + 50;
        BrowserBridge.clearEffect();
        boolean stack = width < 900;
        float gap = 42, column = stack ? contentWidth : (contentWidth - gap * 2) / 3;
        float rowHeight = 0;
        for (var resource : LandingContent.RESOURCES) {
            float h = 43 + textHeight(resource.title(), column, 29, 1.2f, "ui-medium") + 18
                    + textHeight(resource.text(), column, 17, 1.5f, "default") + 75;
            rowHeight = Math.max(rowHeight, h);
        }
        int index = 0;
        for (var resource : LandingContent.RESOURCES) {
            float x = stack ? margin : margin + index * (column + gap);
            float yy = stack ? y + index * (rowHeight + 28) : y;
            line(x, yy, x + column, yy, RULE);
            BrowserBridge.reveal("resource-" + index, yy + 33);
            float bottom = paragraph(resource.title(), x, yy + 33, column, 29, WHITE, 1.2f, "ui-medium") + 18;
            paragraph(resource.text(), x, bottom, column, 17, MUTED, 1.5f, "default");
            text(resource.label(), x, yy + rowHeight - 33, 15, ACCENT, "ui-medium");
            arrow(x + column - 18, yy + rowHeight - 28, ACCENT);
            BrowserBridge.link(resource.title(), resource.href(), x, yy + 16, column, rowHeight - 8, false, false);
            BrowserBridge.clearEffect();
            index++;
        }
        return y + (stack ? index * (rowHeight + 28) : rowHeight) + (width < 760 ? 50 : 95);
    }

    private float start(float y) {
        BrowserBridge.section("start", y + scroll);
        line(margin, y, width - margin, y, RULE);
        y += width < 760 ? 69 : 93;
        BrowserBridge.reveal("start", y);
        float copyWidth = width < 1000 ? contentWidth : contentWidth * .66f;
        float bottom = paragraph(LandingContent.CTA_TITLE, margin, y, copyWidth, width < 760 ? 54 : 82, WHITE, 1.06f, "display") + 27;
        bottom = paragraph(LandingContent.CTA_TEXT, margin, bottom, Math.min(copyWidth, 580), 19, MUTED, 1.5f, "default") + 32;
        action("Read the quick start", LandingContent.GET_STARTED, margin, bottom, 196, true, false);
        if (width >= 1000) {
            text("VALTHORNE " + LandingContent.VERSION, margin + contentWidth * .77f, y + 20, 12, ACCENT, "ui-medium");
            paragraph("Open source.\nYours to build with.", margin + contentWidth * .77f, y + 54, contentWidth * .23f, 24, WHITE, 1.35f, "ui-medium");
            text("Apache License 2.0", margin + contentWidth * .77f, y + 141, 14, MUTED, "default");
        }
        BrowserBridge.clearEffect();
        return bottom + 48 + (width < 760 ? 70 : 100);
    }

    private float footer(float y) {
        line(margin, y, width - margin, y, RULE);
        float top = y + 39;
        BrowserBridge.image(vg, "banner.png", margin, top - 7, width < 760 ? 188 : 230, width < 760 ? 78 : 95);
        if (width >= 1000) {
            text(LandingContent.FOOTER_TEXT, margin + 300, top + 19, 14, MUTED, "default");
            text("© 2026 Valthorne", margin + 300, top + 45, 12, MUTED, "default");
        } else {
            text("An open-source engine by Albert Beaupre.", margin, top + 101, 13, MUTED, "default");
            text("© 2026 Valthorne", margin, top + 125, 12, MUTED, "default");
        }
        return top + (width < 1000 ? 160 : 115) + 86;
    }

    /** The fixed header stays independent of scroll reveals. */
    private void header() {
        float h = headerHeight();
        fill(0, 0, width, h, DARK);
        line(0, h - 1, width, h - 1, RULE);
        BrowserBridge.image(vg, "valthorne.png", margin, 14, 24, 37);
        text("VALTHORNE", margin + 36, 20, width < 760 ? 20 : 23, WHITE, "display");
        BrowserBridge.link("Valthorne home", "#top", margin - 4, 10, width < 760 ? 163 : 184, 46, true, false);
        if (width < 760) {
            action("Get started", "#start", width - margin - 111, 13, 111, true, true);
            nav("Engine", "#engine", margin, 67, 77);
            nav("Resources", "#resources", margin + 92, 67, 99);
            nav("GitHub", LandingContent.REPOSITORY, width - margin - 67, 67, 67);
        } else {
            float x = width - margin - 477;
            nav("Engine", "#engine", x, 22, 72);
            nav("Resources", "#resources", x + 105, 22, 98);
            nav("GitHub", LandingContent.REPOSITORY, x + 234, 22, 71);
            action("Get started", "#start", width - margin - 130, 16, 130, true, true);
        }
    }

    private void nav(String label, String href, float x, float y, float w) {
        text(label, x + 8, y + 8, 15, WHITE, "ui-medium");
        BrowserBridge.link(label, href, x, y, w, 40, true, false);
    }

    private void action(String label, String href, float x, float y, float w, boolean primary, boolean header) {
        fill(x, y, w, 48, primary ? ACCENT : DARK, primary ? 1 : .4f);
        if (!primary) {
            Canvas2D.color(vg, 0x91979f, 1);
            Canvas2D.strokeWidth(vg, 1);
            Canvas2D.beginPath(vg);
            Canvas2D.rect(vg, x + .5f, y + .5f, w - 1, 47);
            Canvas2D.stroke(vg);
        }
        Canvas2D.fontFace(vg, "ui-medium");
        float size = w < 130 ? 14 : 15;
        float measured = BrowserBridge.measure(vg, label, size);
        text(label, x + (w - measured) / 2, y + 15, size, primary ? INK : WHITE, "ui-medium");
        BrowserBridge.link(label, href, x, y, w, 48, header, primary);
    }

    private float headerHeight() { return width < 760 ? 112 : 80; }

    private void text(String value, float x, float y, float size, int color, String face) {
        if (y + size < 0 || y > height) return;
        Canvas2D.fontFace(vg, face);
        Canvas2D.color(vg, color, 1);
        Canvas2D.fontSize(vg, size);
        Canvas2D.text(vg, x, y, value);
    }

    private void rightText(String value, float x, float y, float size, int color, String face) {
        Canvas2D.fontFace(vg, face);
        text(value, x - BrowserBridge.measure(vg, value, size), y, size, color, face);
    }

    private float paragraph(String value, float x, float y, float w, float size, int color, float leading, String face) {
        for (String line : wrap(value, w, size, face)) {
            text(line, x, y, size, color, face);
            y += size * leading;
        }
        return y;
    }

    private float textHeight(String value, float w, float size, float leading, String face) {
        return wrap(value, w, size, face).size() * size *leading;
    }

    /** Preserve explicit line breaks and split long words on narrow screens. */
    private List<String> wrap(String value, float w, float size, String face) {
        Canvas2D.fontFace(vg, face);
        List<String> lines = new ArrayList<>();
        for (String block : value.split("\n", -1)) {
            if (block.isEmpty()) { lines.add(""); continue; }
            String rest = block;
            while (!rest.isEmpty()) {
                int cut = 1;
                while (cut < rest.length() && BrowserBridge.measure(vg, rest.substring(0, cut + 1), size) <= w) cut++;
                if (cut < rest.length()) {
                    int space = rest.lastIndexOf(' ', cut);
                    if (space > 0) cut = space;
                }
                lines.add(rest.substring(0, cut));
                rest = rest.substring(cut);
                if (rest.startsWith(" ")) rest = rest.substring(1);
            }
        }
        return lines;
    }

    private void fill(float x, float y, float w, float h, int color) { fill(x, y, w, h, color, 1); }
    private void fill(float x, float y, float w, float h, int color, float alpha) {
        if (y + h < 0 || y > height || h <= 0 || w <= 0) return;
        Canvas2D.color(vg, color, alpha);
        Canvas2D.beginPath(vg);
        Canvas2D.rect(vg, x, y, w, h);
        Canvas2D.fill(vg);
    }

    private void line(float x1, float y1, float x2, float y2, int color) {
        if (Math.max(y1, y2) < 0 || Math.min(y1, y2) > height) return;
        Canvas2D.color(vg, color, 1);
        Canvas2D.strokeWidth(vg, 1);
        Canvas2D.beginPath(vg);
        Canvas2D.moveTo(vg, x1, y1);
        Canvas2D.lineTo(vg, x2, y2);
        Canvas2D.stroke(vg);
    }

    private void arrow(float x, float y, int color) {
        line(x, y + 11, x + 15, y, color);
        line(x + 4, y, x + 15, y, color);
        line(x + 15, y, x + 15, y + 11, color);
    }
}
