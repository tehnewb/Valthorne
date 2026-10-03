package valthorne.ui.nodes.nano;

import valthorne.graphics.Color;
import valthorne.ui.NanoUtility;

import java.util.Objects;
import java.util.function.BooleanSupplier;

import static org.lwjgl.nanovg.NanoVG.*;

/**
 * Noninteractive vector disclosure triangle shared by Nano and classic section
 * headers. Reads the owning section's expansion state during drawing and points
 * down when expanded or right when collapsed. The path needs no glyph, texture,
 * or per-frame allocation and stays centered within its header's height.
 */
public final class NanoDisclosureIndicator extends NanoPanel {
    /*
     * Muted neutral triangle remains legible on charcoal section headers.
     */
    private static final Color NORMAL = new Color(0xFF999999);
    /*
     * Hover feedback highlights the disclosure together with its header.
     */
    private static final Color HOVERED = new Color(0xFFCCCCCC);
    /*
     * Disabled headers retain a quieter disclosure without changing geometry.
     */
    private static final Color DISABLED = new Color(0xFF555555);

    private final BooleanSupplier expanded; // Borrowed section state, evaluated without mutation.

    /**
     * Places an eight-unit triangle in the header's leading icon slot.
     *
     * @param expanded owning section's expansion state
     */
    public NanoDisclosureIndicator(BooleanSupplier expanded) {
        this.expanded = Objects.requireNonNull(expanded);
        setClickable(false);
        getLayout().absolute().left(7).top(0).width(10).heightPercent(100);
    }

    @Override
    public void draw(long vg) {
        if (!isVisible() || vg == 0 || getParent() == null) return;
        float x = getAbsoluteX() + 1;
        float y = getAbsoluteY() + getHeight() * .5f;
        nvgBeginPath(vg);
        if (expanded.getAsBoolean()) {
            nvgMoveTo(vg, x, y - 2);
            nvgLineTo(vg, x + 8, y - 2);
            nvgLineTo(vg, x + 4, y + 3);
        } else {
            nvgMoveTo(vg, x + 1, y - 4);
            nvgLineTo(vg, x + 1, y + 4);
            nvgLineTo(vg, x + 6, y);
        }
        nvgClosePath(vg);
        nvgFillColor(vg, NanoUtility.color1(getParent().isDisabled() ? DISABLED : getParent().isHovered() ? HOVERED : NORMAL));
        nvgFill(vg);
    }
}
