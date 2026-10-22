package valthorne.ui.nodes.nano;

import valthorne.graphics.Color;
import valthorne.ui.NanoUtility;
import valthorne.ui.UINode;
import valthorne.ui.theme.ResolvedStyle;
import valthorne.ui.theme.UITokens;

import static org.lwjgl.nanovg.NanoVG.nvgBeginPath;
import static org.lwjgl.nanovg.NanoVG.nvgFill;
import static org.lwjgl.nanovg.NanoVG.nvgFillColor;
import static org.lwjgl.nanovg.NanoVG.nvgMoveTo;
import static org.lwjgl.nanovg.NanoVG.nvgLineTo;
import static org.lwjgl.nanovg.NanoVG.nvgClosePath;

/**
 * Noninteractive up or down triangle centered in a numeric spinner's step button.
 * Both UI backends can own this NanoVG child through the root's shared rendering
 * path. Its geometry scales with the button's density while avoiding font-dependent baseline and bearing
 * offsets, and drawing requires no glyph resources or per-frame allocations.
 */
public final class NanoSpinnerIndicator extends NanoContainer {

    /**
     * Neutral step symbol remains readable on the button's charcoal surface.
     */
    private static final Color NORMAL = new Color(0xFFC8C8C8);

    /**
     * Hover and focus feedback brighten the centered symbol.
     */
    private static final Color ACTIVE = new Color(0xFFF0F0F0);

    /**
     * Disabled endpoint buttons retain a subdued symbol.
     */
    private static final Color DISABLED = new Color(0xFF686868);

    private final boolean increase; // Whether the triangle points upward instead of downward.

    /**
     * Creates a symbol covering the owning button without intercepting input.
     *
     * @param increase true for an upward arrow, false for a downward arrow
     */
    public NanoSpinnerIndicator(boolean increase) {
        this.increase = increase;
        setClickable(false);
        getLayout()
                .absolute()
                .left(0)
                .top(0)
                .widthPercent(100)
                .heightPercent(100);
    }

    @Override
    public void draw(long vg) {
        if (!isVisible() || vg == 0 || getParent() == null) return;
        float x = getAbsoluteX() + getWidth() * .5f;
        float y = getAbsoluteY() + getHeight() * .5f;
        float side = Math.min(getWidth() * .38f, getHeight() * .62f);
        ResolvedStyle style = getParent().getStyle();
        boolean disabled = false;
        for (UINode node = getParent(); node != null; node = node.getParent())
            if (node.isDisabled()) {
                disabled = true;
                break;
            }
        boolean active = getParent().isHovered() || getParent().isPressed();
        Color ink = disabled ? DISABLED : style == null ? null : style.get(active ? UITokens.TEXT : UITokens.MUTED);
        if (ink == null) ink = active || getParent().isFocused() ? ACTIVE : NORMAL;
        nvgBeginPath(vg);
        float direction = increase ? -1f : 1f;
        nvgMoveTo(vg, x - side * .5f, y - direction * side * .22f);
        nvgLineTo(vg, x + side * .5f, y - direction * side * .22f);
        nvgLineTo(vg, x, y + direction * side * .38f);
        nvgClosePath(vg);
        nvgFillColor(vg, NanoUtility.color1(ink));
        nvgFill(vg);
    }
}
