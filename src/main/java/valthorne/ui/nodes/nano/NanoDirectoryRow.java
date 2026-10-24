package valthorne.ui.nodes.nano;

import valthorne.Mouse;
import valthorne.event.events.MousePressEvent;
import valthorne.event.events.MouseReleaseEvent;
import valthorne.graphics.Color;
import valthorne.graphics.font.TextFont;
import valthorne.ui.NanoText;
import valthorne.ui.NanoUtility;

import java.nio.file.Path;

import static org.lwjgl.nanovg.NanoVG.*;

/**
 * One materialized directory row with borderless editor selection, a warm vector
 * folder, and a width-aware caption. It holds immutable branch metadata so
 * callbacks remain correct even if navigation replaces the owner's row snapshot.
 * Caption elision is cached by width, font size, and root font identity; drawing
 * performs no filesystem work or per-frame text slicing.
 *
 * @author Albert Beaupre
 */
final class NanoDirectoryRow extends NanoContainer {
    /**
     * Ellipsis appended to captions that exceed the visible row width.
     */
    private static final String ELLIPSIS = "\u2026";

    private final NanoDirectoryTree tree; // Stable owner supplying selection, callbacks, and theme colors.
    private final NanoDirectoryBranch branch; // Immutable directory and hierarchy depth for this row.
    private final String name; // Full human-readable caption retained for the tooltip.
    private final NanoDirectoryDisclosure disclosure; // Independent focusable expansion hit target.
    private String caption; // Cached full or ellipsized caption for the current width.
    private float captionWidth = Float.NaN; // Last measured horizontal space, invalidated by resizing.
    private float captionSize = Float.NaN; // Last measured font size, invalidated by theme changes.
    private TextFont captionFont; // Borrowed smooth font identity used by the last caption measurement.

    /**
     * Creates one virtual row with no button chrome or separate text child.
     *
     * @param tree owning directory hierarchy
     * @param branch immutable directory and indentation metadata
     */
    NanoDirectoryRow(NanoDirectoryTree tree, NanoDirectoryBranch branch) {
        this.tree = tree;
        this.branch = branch;
        Path path = branch.path();
        name = tree.caption(path);
        caption = name;
        setClickable(true);
        setFocusable(true);
        setTooltip(path.toString());
        disclosure = new NanoDirectoryDisclosure(tree, path);
        disclosure.getLayout()
                .absolute()
                .left(4 + branch.depth() * tree.getIndent())
                .top(0)
                .width(16)
                .heightPercent(100);
        add(disclosure);
    }

    /**
     * Reads the stable path for tree-level context routing.
     *
     * @return immutable directory represented by this row
     */
    Path getPath() {
        return branch.path();
    }

    /**
     * Updates the caption only when its width, size, or smooth font changes.
     * A bounded binary search avoids repeated character-by-character shrinking.
     * Surrogate pairs remain intact when a Unicode directory name is elided.
     *
     * @param vg borrowed NanoVG context used only for fallback font metrics
     * @param available nonnegative horizontal caption space
     * @param size resolved logical em size
     */
    private void fitCaption(long vg, float available, float size) {
        TextFont font = getTheme() == null ? null : getTheme().getToken(NanoText.FONT);
        if (font == null && getRoot() != null) font = getRoot().getDefaultFont();
        if (captionWidth == available && captionSize == size && captionFont == font) return;
        captionWidth = available;
        captionSize = size;
        captionFont = font;
        if (NanoText.measureTextWidth(this, vg, "default", size, name) <= available) {
            caption = name;
            return;
        }
        if (NanoText.measureTextWidth(this, vg, "default", size, ELLIPSIS) > available) {
            caption = "";
            return;
        }
        int low = 0;
        int high = name.length();
        while (low < high) {
            int middle = (low + high + 1) >>> 1;
            String candidate = name.substring(0, middle) + ELLIPSIS;
            if (NanoText.measureTextWidth(this, vg, "default", size, candidate) <= available) low = middle;
            else high = middle - 1;
        }
        if (low > 0 && low < name.length() && Character.isHighSurrogate(name.charAt(low - 1))) low--;
        caption = name.substring(0, low) + ELLIPSIS;
    }

    /**
     * Paints a small folder from convex primitives, avoiding a bitmap dependency
     * and preserving a clean icon on scaled or stencil-free UI surfaces.
     *
     * @param vg active NanoVG context
     * @param x left icon edge
     * @param centerY row center
     */
    private void drawFolder(long vg, float x, float centerY) {
        Color folder = tree.folderColor();
        nvgFillColor(vg, NanoUtility.color1(folder));
        nvgBeginPath(vg);
        nvgRect(vg, x, centerY - 6, 6, 3);
        nvgFill(vg);
        nvgBeginPath(vg);
        nvgRect(vg, x, centerY - 4, 15, 10);
        nvgFill(vg);
        nvgFillColor(vg, NanoUtility.color1(0x26000000));
        nvgBeginPath(vg);
        nvgRect(vg, x, centerY - 4, 15, 2);
        nvgFill(vg);
        if (tree.isExpanded(branch.path())) {
            nvgFillColor(vg, NanoUtility.color1(folder));
            nvgBeginPath(vg);
            nvgMoveTo(vg, x + 1, centerY - 2);
            nvgLineTo(vg, x + 17, centerY - 2);
            nvgLineTo(vg, x + 14, centerY + 6);
            nvgLineTo(vg, x, centerY + 6);
            nvgClosePath(vg);
            nvgFill(vg);
        }
    }

    @Override
    public void onMousePress(MousePressEvent event) {
        if (!tree.isDisabled() && event.getButton() == Mouse.LEFT) tree.focusRow(branch.path());
    }

    @Override
    public void onMouseRelease(MouseReleaseEvent event) {
        if (tree.isDisabled() || !isActivationRelease(event)) return;
        event.consume();
        tree.clickRow(branch.path(), event);
    }

    @Override
    protected void applyLayout() {
        disclosure.getLayout()
                .left(4 + branch.depth() * tree.getIndent());
        super.applyLayout();
    }

    @Override
    public void draw(long vg) {
        if (!isVisible() || vg == 0) return;
        float x = getAbsoluteX();
        float y = getAbsoluteY();
        float centerY = y + getHeight() * .5f;
        float leading = 4 + branch.depth() * tree.getIndent();
        boolean selected = tree.isRowSelected(branch.path());
        boolean hovered = isHovered() || disclosure.isHovered();
        if (selected || hovered) {
            nvgFillColor(vg, NanoUtility.color1(tree.rowColor(selected)));
            nvgBeginPath(vg);
            nvgRect(vg, x, y, getWidth(), getHeight());
            nvgFill(vg);
        }
        if (selected && tree.isFocused()) {
            nvgFillColor(vg, NanoUtility.color1(tree.focusColor()));
            nvgBeginPath(vg);
            nvgRect(vg, x, y, 2, getHeight());
            nvgFill(vg);
        }
        drawFolder(vg, x + leading + 19, centerY);
        float textX = x + leading + 40;
        float size = tree.getFontSize();
        fitCaption(vg, Math.max(0, getWidth() - leading - 48), size);
        nvgFontFace(vg, "default");
        nvgFontSize(vg, size);
        nvgTextAlign(vg, NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE);
        Color text = tree.textColor();
        nvgFillColor(vg, NanoUtility.color1(text));
        NanoText.draw(this, vg, textX, centerY, caption, size, text, NVG_ALIGN_LEFT | NVG_ALIGN_MIDDLE);
        super.draw(vg);
    }
}
