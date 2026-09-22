package valthorne.ui.nodes;

import valthorne.graphics.Color;
import valthorne.ui.Canvas2D;
import valthorne.ui.nodes.nano.NanoNode;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * RGBA color editor with selectable channel-slider, hue/saturation-wheel, or combined
 * modes, hexadecimal entry, and a checkerboard alpha preview. Wheel brightness and
 * opacity have independent sliders. Colors are copied at input, output, and notification
 * boundaries so a listener cannot silently mutate the editor's internal value.
 * Hexadecimal text uses #RRGGBB or Valthorne's #AARRGGBB order, not CSS RRGGBBAA.
 * <pre>{@code
 * ColorPicker picker = new ColorPicker().mode(ColorPicker.Mode.WHEEL).color(Color.RED);
 * picker.getLayout().width(300).height(360);
 * picker.onChange(color -> material.setTint(color));
 * root.add(picker);
 * }</pre>
 * Programmatic color changes are silent; wheel, slider, and committed hex edits notify
 * once per changed packed value. Use on the UI thread. The preview uses the root's
 * shared NanoVG backend and owns no independent GPU resources.
 * @author Albert Beaupre
 */
public class ColorPicker extends Panel {
    /**
     * Selects the visible editing surfaces without changing the current color.
     * Both surfaces edit the same packed value and share hexadecimal and alpha input.
     * @author Albert Beaupre
     */
    public enum Mode {
        /**
         * Four red, green, blue and alpha channel sliders.
         */
        SLIDERS,
        /**
         * Hue/saturation wheel with brightness and alpha sliders.
         */
        WHEEL,
        /**
         * Wheel, brightness and all four RGBA sliders together.
         */
        BOTH
    }

    private Color value = Color.WHITE.copy(); // Owned current packed RGBA color.
    private final Slider[] channels = new Slider[4]; // Owned red, green, blue, and alpha editors.
    private final TextField hex = new TextField("#AARRGGBB"); // Owned hexadecimal commit field.
    private Consumer<Color> change = color -> {}; // User-change listener receiving defensive copies.
    private final Panel[] channelRows = new Panel[4]; // Owned RGBA rows whose layout follows the selected mode.
    private final Wheel wheel = new Wheel(); // Owned hue/saturation input and NanoVG painting surface.
    private final Panel brightnessRow = new Panel(); // Owned wheel brightness editor row.
    private final Slider brightness = new Slider(0, 1, 1); // HSV value component, independent of alpha.
    private Mode mode = Mode.SLIDERS; // Current visible editing surfaces.
    private float hue, saturation, luminance = 1; // HSV hue in turns, saturation, and value; retained through black edits.

    /**
     * Creates a white picker with integer 0..255 sliders and a 40-unit preview.
     */
    public ColorPicker() {
        getLayout().column().minWidth(240);
        Swatch swatch = new Swatch(); swatch.getLayout().height(40).widthPercent(100).noShrink(); add(swatch);
        wheel.getLayout().widthPercent(100).noShrink(); add(wheel);
        brightnessRow.getLayout().row().itemsCenter().widthPercent(100).noShrink();
        Label brightnessLabel = new Label("Value"); brightnessLabel.getLayout().minWidth(56).noShrink();
        brightness.getLayout().grow(1).minWidth(0).heightPercent(100);
        brightness.action(slider -> selectHSV(hue, saturation, slider.getValue()));
        brightnessRow.add(brightnessLabel, brightness); add(brightnessRow);
        String[] names = {"Red", "Green", "Blue", "Alpha"};
        for (int i = 0; i < 4; i++) {
            Panel row = new Panel(); row.getLayout().row().itemsCenter().height(32).widthPercent(100).noShrink();
            Label label = new Label(names[i]); label.getLayout().minWidth(56).noShrink();
            Slider slider = new Slider(0, 255, 255).stepSize(1).action(s -> editChannels());
            slider.getLayout().grow(1).minWidth(0).heightPercent(100);
            channels[i] = slider; channelRows[i] = row; row.add(label, slider); add(row);
        }
        hex.getLayout().height(36).widthPercent(100).noShrink();
        hex.action(field -> commitHex()); add(hex); color(value); mode(Mode.SLIDERS);
    }

    /**
     * Returns a defensive copy, including the stored alpha byte.
     * @return independently mutable current color
     */
    public Color getColor() { return value.copy(); }

    /**
     * Silently copies a color and updates all editors, replacing uncommitted text.
     * @param color nonnull input color
     * @return this picker
     */
    public ColorPicker color(Color color) {
        value = Objects.requireNonNull(color).copy();
        float r = value.r(), g = value.g(), b = value.b();
        float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b)), delta = max - min;
        luminance = max;
        if (max > 0) saturation = delta / max;
        if (delta > 0) {
            float sector = max == r ? (g - b) / delta : max == g ? 2 + (b - r) / delta : 4 + (r - g) / delta;
            hue = (sector / 6 + 1) % 1;
        }
        brightness.value(luminance);
        float[] components = {value.r(), value.g(), value.b(), value.a()};
        for (int i = 0; i < 4; i++) channels[i].value(Math.round(components[i] * 255));
        hex.text(value.toHex()); return this;
    }

    /**
     * Switches visible editing surfaces while preserving color and listeners.
     * Wheel modes require 220 additional layout units plus a 32-unit brightness row.
     * @param mode nonnull editor mode
     * @return this picker
     */
    public ColorPicker mode(Mode mode) {
        this.mode = Objects.requireNonNull(mode);
        boolean showWheel = mode != Mode.SLIDERS;
        wheel.setVisible(showWheel); wheel.getLayout().height(showWheel ? 220 : 0);
        brightnessRow.setVisible(showWheel); brightnessRow.getLayout().height(showWheel ? 32 : 0);
        for (int i = 0; i < 4; i++) {
            boolean show = i == 3 || mode != Mode.WHEEL;
            channelRows[i].setVisible(show); channelRows[i].getLayout().height(show ? 32 : 0);
        }
        markLayoutDirty(); return this;
    }

    /**
     * Reads the active editor mode without changing selection or layout.
     * @return current mode
     */
    public Mode getMode() { return mode; }

    /**
     * Exposes the wheel as a focus/layout target without transferring child ownership.
     * @return owned wheel node
     */
    public valthorne.ui.UINode getWheel() { return wheel; }

    /**
     * Borrows the HSV value slider for focus and styling; direct value setters are silent.
     * @return owned brightness slider
     */
    public Slider getBrightness() { return brightness; }

    /**
     * Applies a user HSV edit while retaining alpha. Hue wraps in turns; saturation
     * and value clamp to 0..1. Retained hue/saturation survive zero brightness so
     * raising brightness restores the chosen hue. Duplicate packed colors do not notify.
     * @param hue finite hue in turns, with zero representing red
     * @param saturation finite radial saturation
     * @param brightness finite HSV value
     * @throws IllegalArgumentException if any component is nonfinite
     */
    public void selectHSV(float hue, float saturation, float brightness) {
        if (!Float.isFinite(hue) || !Float.isFinite(saturation) || !Float.isFinite(brightness))
            throw new IllegalArgumentException("HSV components must be finite");
        if (isDisabled()) return;
        float h = hue - (float) Math.floor(hue), s = Math.clamp(saturation, 0, 1), v = Math.clamp(brightness, 0, 1);
        String before = value.toHex();
        color(hsv(h, s, v, value.a()));
        this.hue = h; this.saturation = s; this.luminance = v; this.brightness.value(v);
        if (!before.equals(value.toHex())) change.accept(value.copy());
    }

    /**
     * Converts normalized HSV components into packed color channels without shared state.
     * @param h hue in [0,1)
     * @param s saturation in [0,1]
     * @param v brightness in [0,1]
     * @param alpha opacity in [0,1]
     * @return independent color
     */
    private static Color hsv(float h, float s, float v, float alpha) {
        float sector = h * 6, fraction = sector - (float) Math.floor(sector);
        float p = v * (1 - s), q = v * (1 - s * fraction), t = v * (1 - s * (1 - fraction));
        return switch ((int) sector % 6) {
            case 0 -> new Color(v, t, p, alpha);
            case 1 -> new Color(q, v, p, alpha);
            case 2 -> new Color(p, v, t, alpha);
            case 3 -> new Color(p, q, v, alpha);
            case 4 -> new Color(t, p, v, alpha);
            default -> new Color(v, p, q, alpha);
        };
    }

    /**
     * Focusable hue/saturation disc. Pointer capture belongs to the root; only a
     * primary press inside the disc starts editing, and subsequent drags clamp to
     * its rim. Arrow keys adjust hue/saturation without requiring a pointing device.
     * @author Albert Beaupre
     */
    private final class Wheel extends Panel implements NanoNode {
        private boolean dragging; // Whether an accepted primary press owns this gesture.

        /**
         * Enables routed primary input and keyboard focus on the wheel surface.
         */
        private Wheel() { setClickable(true); setFocusable(true); }

        /**
         * Computes the disc radius leaving room for the selection marker and border.
         * @return nonnegative radius in layout units
         */
        private float radius() { return Math.max(0, Math.min(getWidth(), getHeight()) * .5f - 8); }

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
            float angle = distance < .001f ? hue : (float) (Math.atan2(dy, dx) / (Math.PI * 2));
            selectHSV(angle, distance / radius, luminance); return true;
        }

        /**
         * Begins editing only for an enabled primary press inside the circular surface.
         * @param event routed screen-space press
         */
        @Override public void onMousePress(valthorne.event.events.MousePressEvent event) {
            if (isDisabled() || ColorPicker.this.isDisabled() || event.getButton() != valthorne.Mouse.LEFT) return;
            dragging = point(event.getX(), event.getY(), true);
            if (dragging) event.consume();
        }

        /**
         * Continues a captured gesture, clamping saturation when dragged beyond the rim.
         * @param event routed drag with current screen-space endpoint
         */
        @Override public void onMouseDrag(valthorne.event.events.MouseDragEvent event) {
            if (dragging && !isDisabled() && !ColorPicker.this.isDisabled()) {
                point(event.getToX(), event.getToY(), false); event.consume();
            }
        }

        /**
         * Ends the current pointer gesture without generating an extra color change.
         * @param event routed button release
         */
        @Override public void onMouseRelease(valthorne.event.events.MouseReleaseEvent event) {
            if (dragging) { dragging = false; event.consume(); }
        }

        /**
         * Clears gesture state when focus loss or root removal cancels pointer capture.
         */
        @Override public void onPointerCancel() { dragging = false; }

        /**
         * Adjusts hue with Left/Right and saturation with Up/Down. Home selects the
         * neutral center and End the fully saturated rim; alpha and brightness persist.
         * @param event routed key press
         */
        @Override public void onKeyPress(valthorne.event.events.KeyPressEvent event) {
            if (isDisabled() || ColorPicker.this.isDisabled()) return;
            switch (event.getKey()) {
                case valthorne.Keyboard.LEFT -> selectHSV(hue - 1f / 180, saturation, luminance);
                case valthorne.Keyboard.RIGHT -> selectHSV(hue + 1f / 180, saturation, luminance);
                case valthorne.Keyboard.UP -> selectHSV(hue, saturation + .01f, luminance);
                case valthorne.Keyboard.DOWN -> selectHSV(hue, saturation - .01f, luminance);
                case valthorne.Keyboard.HOME -> selectHSV(hue, 0, luminance);
                case valthorne.Keyboard.END -> selectHSV(hue, 1, luminance);
                default -> { return; }
            }
            event.consume();
        }

        /**
         * Paints hue sectors with white-to-saturated gradients at the current brightness,
         * then a high-contrast selection marker. Scratch paint lives on the native stack.
         * @param vg borrowed active NanoVG context
         */
        @Override public void draw(long vg) {
            float cx = getAbsoluteX() + getWidth() * .5f, cy = getAbsoluteY() + getHeight() * .5f, r = radius();
            if (r <= 0) return;
            try (var stack = org.lwjgl.system.MemoryStack.stackPush()) {
                var paint = org.lwjgl.nanovg.NVGPaint.calloc(stack);
                var center = org.lwjgl.nanovg.NVGColor.calloc(stack).r(luminance).g(luminance).b(luminance).a(1);
                var edge = org.lwjgl.nanovg.NVGColor.calloc(stack);
                org.lwjgl.nanovg.NanoVG.nvgSave(vg);
                org.lwjgl.nanovg.NanoVG.nvgShapeAntiAlias(vg, false);
                for (int i = 0; i < 180; i++) {
                    float start = (float) (i * Math.PI / 90), end = (float) ((i + 1.05) * Math.PI / 90);
                    Color color = hsv(i / 180f, 1, luminance, 1);
                    edge.r(color.r()).g(color.g()).b(color.b()).a(1);
                    org.lwjgl.nanovg.NanoVG.nvgLinearGradient(vg, cx, cy, cx + (float) Math.cos(start) * r,
                            cy + (float) Math.sin(start) * r, center, edge, paint);
                    org.lwjgl.nanovg.NanoVG.nvgBeginPath(vg);
                    org.lwjgl.nanovg.NanoVG.nvgMoveTo(vg, cx, cy);
                    org.lwjgl.nanovg.NanoVG.nvgArc(vg, cx, cy, r, start, end, org.lwjgl.nanovg.NanoVG.NVG_CW);
                    org.lwjgl.nanovg.NanoVG.nvgClosePath(vg);
                    org.lwjgl.nanovg.NanoVG.nvgFillPaint(vg, paint); org.lwjgl.nanovg.NanoVG.nvgFill(vg);
                }
                org.lwjgl.nanovg.NanoVG.nvgRestore(vg);
                float angle = hue * (float) (Math.PI * 2);
                float x = cx + (float) Math.cos(angle) * r * saturation, y = cy + (float) Math.sin(angle) * r * saturation;
                org.lwjgl.nanovg.NanoVG.nvgBeginPath(vg); org.lwjgl.nanovg.NanoVG.nvgCircle(vg, x, y, 5);
                Canvas2D.color(vg, 0, 1); org.lwjgl.nanovg.NanoVG.nvgStrokeWidth(vg, 3); org.lwjgl.nanovg.NanoVG.nvgStroke(vg);
                Canvas2D.color(vg, 0xffffff, 1); org.lwjgl.nanovg.NanoVG.nvgStrokeWidth(vg, 1); org.lwjgl.nanovg.NanoVG.nvgStroke(vg);
            }
        }
    }

    /**
     * Borrows a channel slider for style or focus configuration. Direct slider.value
     * calls are silent; use color to update the complete picker programmatically.
     * @param index zero for red, one green, two blue, three alpha
     * @return owned slider
     */
    public Slider getChannel(int index) { return channels[index]; }

    /**
     * Borrows the hexadecimal text field; call commitHex to apply pending text.
     * @return owned entry field
     */
    public TextField getHexField() { return hex; }

    /**
     * Replaces the user-edit listener without an initial notification.
     * @param listener nonnull callback receiving a new color copy
     * @return this picker
     */
    public ColorPicker onChange(Consumer<Color> listener) { change = Objects.requireNonNull(listener); return this; }

    /**
     * Parses six RGB or eight ARGB hexadecimal digits, optionally prefixed by #.
     * Surrounding whitespace is ignored. Invalid text restores the committed color
     * without notifying; disabled controls ignore commits.
     * @return true for valid enabled input, even if its value was unchanged
     */
    public boolean commitHex() {
        if (isDisabled()) return false;
        String input = hex.getText().trim();
        if (input.startsWith("#")) input = input.substring(1);
        if (!input.matches("(?i)([0-9a-f]{6}|[0-9a-f]{8})")) { hex.text(value.toHex()); return false; }
        int packed = (int) Long.parseLong(input, 16);
        if (input.length() == 6) packed |= 0xff000000;
        apply(new Color(packed)); return true;
    }

    /**
     * Combines integer channel values after a routed slider edit; disabled pickers
     * do not notify. The alpha byte occupies the most significant bits.
     */
    private void editChannels() {
        if (isDisabled()) return;
        int packed = Math.round(channels[3].getValue()) << 24 | Math.round(channels[0].getValue()) << 16
                | Math.round(channels[1].getValue()) << 8 | Math.round(channels[2].getValue());
        apply(new Color(packed));
    }

    /**
     * Synchronizes a user value before notifying once for a changed packed color.
     * @param next independently created proposed value
     */
    private void apply(Color next) {
        String before = value.toHex(); color(next);
        if (!before.equals(value.toHex())) change.accept(value.copy());
    }

    /**
     * Live preview combining a neutral checkerboard with the current alpha-composited
     * color. Its NanoVG context and frame are borrowed from shared root dispatch.
     * Shared dispatch supplies ancestor scrolling and clipping in top-left UI coordinates.
     * @author Albert Beaupre
     */
    private final class Swatch extends Panel implements NanoNode {
        /**
         * Draws clipped eight-unit checks, then overlays the current color. No GPU
         * resources are allocated and no child traversal is needed for this leaf.
         * @param vg root-owned active NanoVG context
         */
        @Override public void draw(long vg) {
            for (int y = 0; y < getHeight(); y += 8) for (int x = 0; x < getWidth(); x += 8) {
                Canvas2D.color(vg, ((x / 8 + y / 8) & 1) == 0 ? 0xcccccc : 0x777777, 1);
                Canvas2D.beginPath(vg); Canvas2D.rect(vg, getAbsoluteX() + x, getAbsoluteY() + y,
                        Math.min(8, getWidth() - x), Math.min(8, getHeight() - y)); Canvas2D.fill(vg);
            }
            int rgb = Math.round(value.r() * 255) << 16 | Math.round(value.g() * 255) << 8 | Math.round(value.b() * 255);
            Canvas2D.color(vg, rgb, value.a()); Canvas2D.beginPath(vg);
            Canvas2D.rect(vg, getAbsoluteX(), getAbsoluteY(), getWidth(), getHeight()); Canvas2D.fill(vg);
        }
    }
}
