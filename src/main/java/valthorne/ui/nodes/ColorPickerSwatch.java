package valthorne.ui.nodes;

import valthorne.ui.Canvas2D;
import valthorne.ui.nodes.nano.NanoNode;

/**
 * Live preview combining a neutral checkerboard with the current alpha-composited
 * color. Its NanoVG context and frame are borrowed from shared root dispatch.
 * Shared dispatch supplies ancestor scrolling and clipping in top-left UI coordinates.
 * @author Albert Beaupre
 */
final class ColorPickerSwatch extends Panel implements NanoNode {
    private final ColorPicker owner; // Borrowed control coordinating this helper's behavior.

    /**
     * Retains the control whose state this helper presents or routes.
     *
     * @param owner borrowed control; never disposed by this helper
     */
    ColorPickerSwatch(ColorPicker owner) {
        this.owner = owner;
    }

    /**
     * Draws clipped eight-unit checks, then overlays the current color. No GPU
     * resources are allocated and no child traversal is needed for this leaf.
     * @param vg root-owned active NanoVG context
     */
    @Override
    public void draw(long vg) {
        for (int y = 0; y < getHeight(); y += 8) for (int x = 0; x < getWidth(); x += 8) {
            Canvas2D.color(vg, ((x / 8 + y / 8) & 1) == 0 ? 0xcccccc : 0x777777, 1);
            Canvas2D.beginPath(vg);
            Canvas2D.rect(vg, getAbsoluteX() + x, getAbsoluteY() + y,
                    Math.min(8, getWidth() - x), Math.min(8, getHeight() - y));
            Canvas2D.fill(vg);
        }
        int rgb = Math.round(owner.value.r() * 255) << 16 | Math.round(owner.value.g() * 255) << 8 | Math.round(owner.value.b() * 255);
        Canvas2D.color(vg, rgb, owner.value.a());
        Canvas2D.beginPath(vg);
        Canvas2D.rect(vg, getAbsoluteX(), getAbsoluteY(), getWidth(), getHeight());
        Canvas2D.fill(vg);
    }
}
