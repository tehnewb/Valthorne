package valthorne.ui.nodes.nano;

import valthorne.ui.UINode;

import valthorne.graphics.Color;
import valthorne.ui.theme.ResolvedStyle;
import valthorne.ui.theme.UITokens;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * NanoVG variant. RGBA color editor with selectable channel-slider, hue/saturation-wheel, or combined
 * modes, hexadecimal entry, and a checkerboard alpha preview. Wheel brightness and
 * opacity have independent sliders. Colors are copied at input, output, and notification
 * boundaries so a listener cannot silently mutate the editor's internal value.
 * Hexadecimal text uses #RRGGBB or Valthorne's #AARRGGBB order, not CSS RRGGBBAA.
 * <pre>{@code
 * NanoColorPicker picker = new NanoColorPicker().mode(NanoColorPickerMode.WHEEL).color(Color.RED);
 * picker.getLayout().width(300).height(360);
 * picker.onChange(color -> material.setTint(color));
 * root.add(picker);
 * }</pre>
 * Programmatic color changes are silent; wheel, slider, and committed hex edits notify
 * once per changed packed value. Use on the UI thread. The wheel owns a cached NanoVG image, released with the picker.
 * @author Albert Beaupre
 */
public class NanoColorPicker extends NanoContainer {

    Color value = Color.WHITE.copy(); // Owned current packed RGBA color.
    private final NanoSlider[] channels = new NanoSlider[4]; // Owned red, green, blue, and alpha editors.
    private final NanoTextField hex = new NanoTextField("#AARRGGBB"); // Owned hexadecimal commit field.
    private Consumer<Color> change = color -> {}; // User-change listener receiving defensive copies.
    private final NanoPanel[] channelRows = new NanoPanel[4]; // Owned RGBA rows whose layout follows the selected mode.
    private final NanoColorPickerWheel wheel = new NanoColorPickerWheel(this); // Owned hue/saturation input and NanoVG painting surface.
    private final NanoPanel brightnessRow = NanoWidgetSupport.panel(); // Owned wheel brightness editor row.
    private final NanoSlider brightness = new NanoSlider(0, 1, 1); // HSV value component, independent of alpha.
    private NanoColorPickerMode mode = NanoColorPickerMode.SLIDERS; // Current visible editing surfaces.
    float hue; // HSV hue in turns, saturation, and value; retained through black edits.
    float saturation; // HSV hue in turns, saturation, and value; retained through black edits.
    float luminance = 1; // HSV hue in turns, saturation, and value; retained through black edits.

    /**
     * Creates a white picker with integer 0..255 sliders and a 40-unit preview.
     */
    public NanoColorPicker() {
        getLayout()
                .column()
                .minWidth(240);
        NanoColorPickerSwatch swatch = new NanoColorPickerSwatch(this);
        swatch.getLayout()
                .height(40)
                .widthPercent(100)
                .noShrink();
        add(swatch);
        wheel.getLayout()
                .widthPercent(100)
                .noShrink();
        add(wheel);
        brightnessRow.getLayout()
                .row()
                .itemsCenter()
                .widthPercent(100)
                .noShrink();
        NanoLabel brightnessLabel = new NanoLabel("Value");
        brightnessLabel.getLayout()
                .minWidth(56)
                .noShrink();
        brightness.getLayout()
                .grow(1)
                .minWidth(0)
                .heightPercent(100);
        brightness.action(slider -> selectHSV(hue, saturation, slider.getValue()));
        brightnessRow.add(brightnessLabel, brightness);
        add(brightnessRow);
        String[] names = {"Red", "Green", "Blue", "Alpha"};
        for (int i = 0; i < 4; i++) {
            NanoPanel row = NanoWidgetSupport.panel();
            row.getLayout()
                    .row()
                    .itemsCenter()
                    .height(26)
                    .widthPercent(100)
                    .noShrink();
            NanoLabel label = new NanoLabel(names[i]);
            label.getLayout()
                    .minWidth(56)
                    .noShrink();
            NanoSlider slider = new NanoSlider(0, 255, 255)
                    .stepSize(1)
                    .action(s -> editChannels());
            slider.getLayout()
                    .grow(1)
                    .minWidth(0)
                    .heightPercent(100);
            channels[i] = slider;
            channelRows[i] = row;
            row.add(label, slider);
            add(row);
        }
        hex.getLayout()
                .widthPercent(100)
                .noShrink();
        hex.action(field -> commitHex());
        add(hex);
        color(value);
        mode(NanoColorPickerMode.SLIDERS);
    }

    /**
     * Returns a defensive copy, including the stored alpha byte.
     * @return independently mutable current color
     */
    public Color getColor() {
        return value.copy();
    }

    /**
     * Silently copies a color and updates all editors, replacing uncommitted text.
     * @param color nonnull input color
     * @return this picker
     */
    public NanoColorPicker color(Color color) {
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
        hex.text(value.toHex());
        return this;
    }

    /**
     * Switches visible editing surfaces while preserving color and listeners.
     * Wheel modes require 220 additional layout units plus a themed brightness row.
     * @param mode nonnull editor mode
     * @return this picker
     */
    public NanoColorPicker mode(NanoColorPickerMode mode) {
        this.mode = Objects.requireNonNull(mode);
        float height = controlHeight();
        boolean showWheel = mode != NanoColorPickerMode.SLIDERS;
        wheel.setVisible(showWheel);
        wheel.getLayout()
                .height(showWheel ? 220 : 0);
        brightnessRow.setVisible(showWheel);
        brightnessRow.getLayout()
                .height(showWheel ? height : 0);
        for (int i = 0; i < 4; i++) {
            boolean show = i == 3 || mode != NanoColorPickerMode.WHEEL;
            channelRows[i].setVisible(show);
            channelRows[i].getLayout()
                    .height(show ? height : 0);
        }
        markLayoutDirty();
        return this;
    }

    @Override
    protected void applyLayout() {
        float height = controlHeight();
        brightnessRow.getLayout()
                .height(brightnessRow.isVisible() ? height : 0);
        for (NanoPanel row : channelRows) row.getLayout()
                .height(row.isVisible() ? height : 0);
        super.applyLayout();
    }

    /**
     * Reads the active theme's height for channel and brightness editors.
     *
     * @return finite positive height in UI units
     */
    private float controlHeight() {
        ResolvedStyle style = getStyle();
        Float height = style == null ? null : style.get(UITokens.CONTROL_HEIGHT);
        return height != null && Float.isFinite(height) && height > 0 ? height : 26f;
    }

    /**
     * Reads the active editor mode without changing selection or layout.
     * @return current mode
     */
    public NanoColorPickerMode getMode() {
        return mode;
    }

    /**
     * Exposes the wheel as a focus/layout target without transferring child ownership.
     * @return owned wheel node
     */
    public UINode getWheel() {
        return wheel;
    }

    /**
     * Borrows the HSV value slider for focus and styling; direct value setters are silent.
     * @return owned brightness slider
     */
    public NanoSlider getBrightness() {
        return brightness;
    }

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
        this.hue = h;
        this.saturation = s;
        this.luminance = v;
        this.brightness.value(v);
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
     * Borrows a channel slider for style or focus configuration. Direct slider.value
     * calls are silent; use color to update the complete picker programmatically.
     * @param index zero for red, one green, two blue, three alpha
     * @return owned slider
     */
    public NanoSlider getChannel(int index) {
        return channels[index];
    }

    /**
     * Borrows the hexadecimal text field; call commitHex to apply pending text.
     * @return owned entry field
     */
    public NanoTextField getHexField() {
        return hex;
    }

    /**
     * Replaces the user-edit listener without an initial notification.
     * @param listener nonnull callback receiving a new color copy
     * @return this picker
     */
    public NanoColorPicker onChange(Consumer<Color> listener) {
        change = Objects.requireNonNull(listener);
        return this;
    }

    /**
     * Parses six RGB or eight ARGB hexadecimal digits, optionally prefixed by #.
     * Surrounding whitespace is ignored. Invalid text restores the committed color
     * without notifying; disabled controls ignore commits.
     * @return true for valid enabled input, even if its value was unchanged
     */
    public boolean commitHex() {
        if (isDisabled()) return false;
        String input = hex.getText()
                .trim();
        if (input.startsWith("#")) input = input.substring(1);
        if (!input.matches("(?i)([0-9a-f]{6}|[0-9a-f]{8})")) {
            hex.text(value.toHex());
            return false;
        }
        int packed = (int) Long.parseLong(input, 16);
        if (input.length() == 6) packed |= 0xff000000;
        apply(new Color(packed));
        return true;
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
        String before = value.toHex();
        color(next);
        if (!before.equals(value.toHex())) change.accept(value.copy());
    }

}
