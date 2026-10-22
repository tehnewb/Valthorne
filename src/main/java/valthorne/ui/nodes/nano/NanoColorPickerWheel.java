package valthorne.ui.nodes.nano;

import valthorne.Keyboard;
import valthorne.Mouse;
import valthorne.event.events.KeyPressEvent;
import valthorne.event.events.MouseDragEvent;
import valthorne.event.events.MousePressEvent;
import valthorne.event.events.MouseReleaseEvent;
import valthorne.ui.nodes.ColorWheelImage;

import valthorne.ui.Canvas2D;

/**
 * Focusable hue/saturation disc. Pointer capture belongs to the root; only a
 * primary press inside the disc starts editing, and subsequent drags clamp to
 * its rim. Arrow keys adjust hue/saturation without requiring a pointing device.
 * @author Albert Beaupre
 */
final class NanoColorPickerWheel extends NanoPanel implements NanoNode {
    private final NanoColorPicker owner; // Borrowed control coordinating this helper's behavior.

    private boolean dragging; // Whether an accepted primary press owns this gesture.
    private final ColorWheelImage wheelImage = new ColorWheelImage(); // Owned lazily generated color-wheel texture; released with the picker.

    /**
     * Enables routed primary input and keyboard focus on the wheel surface.
     */
    NanoColorPickerWheel(NanoColorPicker owner) {
        this.owner = owner;
        setClickable(true);
        setFocusable(true);
    }

    /**
     * Computes the disc radius leaving room for the selection marker and border.
     * @return nonnegative radius in layout units
     */
    private float radius() {
        return Math.max(0, Math.min(getWidth(), getHeight()) * .5f - 8);
    }

    /**
     * Maps a screen point into hue and radial saturation using shared UI transforms.
     * @param x screen x
     * @param y screen y
     * @param initial whether points outside the disc must be ignored
     * @return whether the point produced an edit
     */
    private boolean point(float x, float y, boolean initial) {
        var local = screenToLocal(x, y);
        float dx = local.x() - getWidth() * .5f, dy = local.y() - getHeight() * .5f;
        float distance = (float) Math.hypot(dx, dy), radius = radius();
        if (radius <= 0 || (initial && distance > radius)) return false;
        float angle = distance < .001f ? owner.hue : (float) (Math.atan2(dy, dx) / (Math.PI * 2));
        owner.selectHSV(angle, distance / radius, owner.luminance);
        return true;
    }

    /**
     * Begins editing only for an enabled primary press inside the circular surface.
     * @param event routed screen-space press
     */
    @Override
    public void onMousePress(MousePressEvent event) {
        if (isDisabled() || owner.isDisabled() || event.getButton() != Mouse.LEFT) return;
        dragging = point(event.getX(), event.getY(), true);
        if (dragging) event.consume();
    }

    /**
     * Continues a captured gesture, clamping saturation when dragged beyond the rim.
     * @param event routed drag with current screen-space endpoint
     */
    @Override
    public void onMouseDrag(MouseDragEvent event) {
        if (dragging && !isDisabled() && !owner.isDisabled()) {
            point(event.getToX(), event.getToY(), false);
            event.consume();
        }
    }

    /**
     * Ends the current pointer gesture without generating an extra color change.
     * @param event routed button release
     */
    @Override
    public void onMouseRelease(MouseReleaseEvent event) {
        if (dragging) {
            dragging = false;
            event.consume();
        }
    }

    /**
     * Clears gesture state when focus loss or root removal cancels pointer capture.
     */
    @Override
    public void onPointerCancel() {
        dragging = false;
    }

    @Override
    public void onDestroy() {
        wheelImage.dispose();
    }

    /**
     * Adjusts hue with Left/Right and saturation with Up/Down. Home selects the
     * neutral center and End the fully saturated rim; alpha and brightness persist.
     * @param event routed key press
     */
    @Override
    public void onKeyPress(KeyPressEvent event) {
        if (isDisabled() || owner.isDisabled()) return;
        switch (event.getKey()) {
            case Keyboard.LEFT -> owner.selectHSV(owner.hue - 1f / 180, owner.saturation, owner.luminance);
            case Keyboard.RIGHT -> owner.selectHSV(owner.hue + 1f / 180, owner.saturation, owner.luminance);
            case Keyboard.UP -> owner.selectHSV(owner.hue, owner.saturation + .01f, owner.luminance);
            case Keyboard.DOWN -> owner.selectHSV(owner.hue, owner.saturation - .01f, owner.luminance);
            case Keyboard.HOME -> owner.selectHSV(owner.hue, 0, owner.luminance);
            case Keyboard.END -> owner.selectHSV(owner.hue, 1, owner.luminance);
            default -> {
                return;
            }
        }
        event.consume();
    }

    /**
     * Paints the continuous hue/saturation image and selection marker.
     */
    @Override
    public void draw(long vg) {
        float cx = getAbsoluteX() + getWidth() * .5f, cy = getAbsoluteY() + getHeight() * .5f, r = radius();
        if (r <= 0) return;
        wheelImage.draw(vg, getAbsoluteX(), getAbsoluteY(), getWidth(), getHeight(), r, owner.luminance);
        try (var stack = org.lwjgl.system.MemoryStack.stackPush()) {
            float angle = owner.hue * (float) (Math.PI * 2);
            float x = cx + (float) Math.cos(angle) * r * owner.saturation, y = cy + (float) Math.sin(angle) * r * owner.saturation;
            org.lwjgl.nanovg.NanoVG.nvgBeginPath(vg);
            org.lwjgl.nanovg.NanoVG.nvgCircle(vg, x, y, 5);
            Canvas2D.color(vg, 0, 1);
            org.lwjgl.nanovg.NanoVG.nvgStrokeWidth(vg, 3);
            org.lwjgl.nanovg.NanoVG.nvgStroke(vg);
            Canvas2D.color(vg, 0xffffff, 1);
            org.lwjgl.nanovg.NanoVG.nvgStrokeWidth(vg, 1);
            org.lwjgl.nanovg.NanoVG.nvgStroke(vg);
        }
    }
}
