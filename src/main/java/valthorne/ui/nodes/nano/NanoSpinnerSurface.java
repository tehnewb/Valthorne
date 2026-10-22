package valthorne.ui.nodes.nano;

import valthorne.graphics.Color;
import valthorne.ui.NanoUtility;
import valthorne.ui.UINode;
import valthorne.ui.theme.ResolvedStyle;
import valthorne.ui.theme.StyleKey;
import valthorne.ui.theme.UITokens;

import static org.lwjgl.nanovg.NanoVG.*;

/**
 * Shared numeric-input surface painted behind the text editor and stacked arrows.
 * The entire outline follows focus anywhere inside its owning spinner, giving
 * both rendering backends one coherent field rather than separate button boxes.
 * This noninteractive child borrows theme colors and owns no GPU resources.
 * Drawing uses convex fill/stroke geometry and performs no allocations.
 */
public final class NanoSpinnerSurface extends NanoContainer {

    /**
     * Rounded outer field corners, expressed in logical UI units.
     */
    public static final StyleKey<Float> CORNER_RADIUS_KEY = StyleKey.of("spinner.cornerRadius", Float.class, 4f);

    /**
     * Inset outline width in logical UI units; zero suppresses the outline.
     */
    public static final StyleKey<Float> BORDER_WIDTH_KEY = StyleKey.of("spinner.borderWidth", Float.class, 1f);

    /**
     * Recessed numeric-input surface used without a resolved theme.
     */
    private static final Color FIELD = new Color(0xFF111111);

    /**
     * Quiet neutral outline used without a resolved theme.
     */
    private static final Color BORDER = new Color(0xFF454545);

    /**
     * Full-field focus outline used without a resolved theme.
     */
    private static final Color ACCENT = new Color(0xFF3B82F6);

    /**
     * Creates a background child that covers its owner without receiving input.
     */
    public NanoSpinnerSurface() {
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
        UINode owner = getParent();
        if (!isVisible() || vg == 0 || owner == null) return;
        ResolvedStyle style = owner.getStyle();
        Color fill = style == null ? null : style.get(UITokens.FIELD);
        Color edge = style == null ? null : style.get(UITokens.BORDER);
        if (fill == null) fill = FIELD;
        if (edge == null) edge = BORDER;
        boolean enabled = true;
        for (UINode node = owner; node != null; node = node.getParent())
            if (node.isDisabled()) {
                enabled = false;
                break;
            }
        if (enabled && getRoot() != null) {
            for (UINode node = getRoot().getFocused(); node != null; node = node.getParent())
                if (node == owner) {
                    Color accent = style == null ? null : style.get(UITokens.ACCENT);
                    edge = accent == null ? ACCENT : accent;
                    break;
                }
        }
        Float resolvedRadius = style == null ? null : style.get(CORNER_RADIUS_KEY);
        Float resolvedWidth = style == null ? null : style.get(BORDER_WIDTH_KEY);
        float radius = resolvedRadius == null ? 4f : Math.max(0, resolvedRadius);
        float stroke = resolvedWidth == null ? 1f : Math.max(0, resolvedWidth);
        float x = getAbsoluteX();
        float y = getAbsoluteY();
        float width = getWidth();
        float height = getHeight();
        nvgBeginPath(vg);
        nvgRoundedRect(vg, x, y, width, height, radius);
        nvgFillColor(vg, NanoUtility.color1(fill));
        nvgFill(vg);
        if (stroke <= 0) return;
        nvgBeginPath(vg);
        nvgRoundedRect(vg, x + stroke * .5f, y + stroke * .5f, Math.max(0, width - stroke), Math.max(0, height - stroke), radius);
        nvgStrokeColor(vg, NanoUtility.color1(edge));
        nvgStrokeWidth(vg, stroke);
        nvgStroke(vg);
    }
}
