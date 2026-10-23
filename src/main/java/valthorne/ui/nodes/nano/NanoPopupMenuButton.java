package valthorne.ui.nodes.nano;

import valthorne.graphics.Color;
import valthorne.event.events.MouseMoveEvent;
import valthorne.ui.NanoUtility;
import valthorne.ui.theme.ResolvedStyle;

import static org.lwjgl.nanovg.NanoVG.*;

/**
 * Transient row retaining its command index so Tab-driven focus and arrow-driven
 * highlighting agree. Rows are owned and recycled by the virtualized option list.
 * The immutable index belongs to one opening and is discarded when rows are cleared.
 * @author Albert Beaupre
 */
final class NanoPopupMenuButton extends NanoButton {
    private final NanoPopupMenu owner; // Borrowed control coordinating this helper's behavior.

    final int depth; // Flyout level containing this command.
    final int index; // Command position represented by this transient row.

    /**
     * Creates a themed command label without executing its callback.
     * @param index valid position in the current command snapshot
     */
    NanoPopupMenuButton(NanoPopupMenu owner, int depth, int index) {
        super(owner.pages.get(depth).get(index).text());
        this.owner = owner;

        this.depth = depth;
        this.index = index;
    }

    @Override
    public void onMouseMove(MouseMoveEvent event) {
        super.onMouseMove(event);
        owner.activeDepth = depth;
        owner.highlighted = index;
        NanoPopupMenuItem item = owner.pages.get(depth)
                .get(index);
        if (item.enabled() && item.submenu() != null) owner.openSubmenu(depth, index, false);
        else owner.closeFrom(depth + 1);
    }

    @Override
    public void draw(long vg) {
        super.draw(vg);
        NanoPopupMenuItem item = owner.pages.get(depth)
                .get(index);
        if (!item.checkable() && item.icon() == null && item.submenu() == null) return;
        float x = getAbsoluteX() + 11, y = getAbsoluteY() + (getHeight() - 14) * 0.5f;
        ResolvedStyle style = owner.getStyle();
        Color indicator = style == null ? NanoPopupMenu.INDICATOR_COLOR_KEY.getDefaultValue() : style.get(NanoPopupMenu.INDICATOR_COLOR_KEY);
        if (indicator == null) indicator = NanoPopupMenu.INDICATOR_COLOR_KEY.getDefaultValue();
        nvgSave(vg);
        if (item.icon() != null) {
            item.icon()
                    .draw(vg, x, y, 14);
        } else if (item.checkable()) {
            nvgStrokeColor(vg, NanoUtility.color1(indicator));
            nvgStrokeWidth(vg, 1f);
            nvgBeginPath(vg);
            Float radius = style == null ? null : style.get(NanoPopupMenu.CHECKBOX_CORNER_RADIUS_KEY);
            nvgRoundedRect(vg, x, y, 14, 14,
                    Math.max(0f, radius == null ? NanoPopupMenu.CHECKBOX_CORNER_RADIUS_KEY.getDefaultValue() : radius));
            nvgStroke(vg);
            if (item.checked()) {
                nvgBeginPath(vg);
                nvgMoveTo(vg, x + 3, y + 7);
                nvgLineTo(vg, x + 6, y + 10);
                nvgLineTo(vg, x + 11, y + 4);
                nvgStrokeWidth(vg, 1.8f);
                nvgStroke(vg);
            }
        }
        if (item.submenu() != null) {
            float arrowX = getAbsoluteX() + getWidth() - 16;
            float arrowY = getAbsoluteY() + getHeight() * 0.5f;
            nvgStrokeColor(vg, NanoUtility.color1(indicator));
            nvgStrokeWidth(vg, 1.5f);
            nvgBeginPath(vg);
            nvgMoveTo(vg, arrowX - 3, arrowY - 5);
            nvgLineTo(vg, arrowX + 2, arrowY);
            nvgLineTo(vg, arrowX - 3, arrowY + 5);
            nvgStroke(vg);
        }
        nvgRestore(vg);
    }
}
