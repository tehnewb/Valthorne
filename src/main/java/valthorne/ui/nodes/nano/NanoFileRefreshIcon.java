package valthorne.ui.nodes.nano;

import valthorne.ui.Canvas2D;

import valthorne.graphics.Color;
import valthorne.ui.theme.ResolvedStyle;
import valthorne.ui.theme.UITokens;

/**
 * Vector refresh arrow independent of font glyph coverage. It inherits the current
 * theme text color and does not intercept the owning command's pointer events.
 * @author Albert Beaupre
 */
final class NanoFileRefreshIcon extends NanoPanel implements NanoNode {
    /**
     * Centers a noninteractive icon inside the refresh toolbar button.
     */
    NanoFileRefreshIcon() {
        setClickable(false);
        getLayout()
                .absolute()
                .left(5)
                .top(6)
                .width(18)
                .height(18);
    }

    /**
     * Draws a partial circle with an arrowhead using the root-owned vector context.
     * @param vg active NanoVG context
     */
    @Override
    public void draw(long vg) {
        ResolvedStyle style = getStyle();
        Color color = style == null ? null : style.get(UITokens.TEXT);
        if (color == null) color = style == null ? Color.WHITE : style.get(NanoButton.TEXT_COLOR_KEY);
        int rgb = color == null ? 0x667788 : Math.round(color.r() * 255) << 16 | Math.round(color.g() * 255) << 8 | Math.round(color.b() * 255);
        float x = getAbsoluteX() + 9, y = getAbsoluteY() + 9;
        Canvas2D.color(vg, rgb, 1);
        org.lwjgl.nanovg.NanoVG.nvgBeginPath(vg);
        org.lwjgl.nanovg.NanoVG.nvgArc(vg, x, y, 5, -.7f, 4.7f, org.lwjgl.nanovg.NanoVG.NVG_CW);
        org.lwjgl.nanovg.NanoVG.nvgStrokeWidth(vg, 1.4f);
        org.lwjgl.nanovg.NanoVG.nvgStroke(vg);
        org.lwjgl.nanovg.NanoVG.nvgBeginPath(vg);
        org.lwjgl.nanovg.NanoVG.nvgMoveTo(vg, x + 5, y - 7);
        org.lwjgl.nanovg.NanoVG.nvgLineTo(vg, x + 5, y - 1);
        org.lwjgl.nanovg.NanoVG.nvgLineTo(vg, x, y - 3);
        org.lwjgl.nanovg.NanoVG.nvgClosePath(vg);
        org.lwjgl.nanovg.NanoVG.nvgFill(vg);
    }
}
