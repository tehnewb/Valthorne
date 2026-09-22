package valthorne.ui.theme;

import org.lwjgl.BufferUtils;
import valthorne.graphics.Color;
import valthorne.graphics.Drawable;
import valthorne.graphics.font.Font;
import valthorne.graphics.font.FontData;
import valthorne.graphics.texture.NinePatchTexture;
import valthorne.graphics.texture.TextureData;
import valthorne.ui.nodes.*;
import valthorne.ui.nodes.nano.*;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import valthorne.graphics.texture.NinePatchDrawable;

/**
 * Builds a density-scaled light or dark skin for texture and NanoVG UI controls.
 * The theme owns its bundled font and all nine-patch textures allocated by
 * {@link #create()}. Theme data shares these resources and palette colors, so keep
 * this object alive while any styled root uses them. Create and close it on the
 * graphics thread, detaching or disposing dependent roots before closing.
 *
 * <pre>{@code
 * ProfessionalTheme theme = new ProfessionalTheme(false, 1.25f);
 * ThemeData styles = theme.create();
 * // Apply styles to the UI and retain theme for the UI's lifetime.
 * // After disposing all dependent roots:
 * theme.close();
 * }</pre>
 *
 * @author Albert Beaupre
 */
public final class ProfessionalTheme implements Theme, AutoCloseable {
    public final Color surface, raised, hover, text, muted, accent, border, disabled; // Mutable colors shared with generated styles.
    public final Color error = new Color(0xFFF87171); // Shared validation-error accent color.
    private final FontData fontData; // Owned rasterized data for the bundled UI font.
    private final Font font; // Owned font shared by all generated texture-control styles.
    private final List<NinePatchTexture> skins = new ArrayList<>(); // Owned skins accumulated across create calls.
    private final float density; // Scale applied to logical control, spacing, and font dimensions.
    private boolean closed; // Prevents repeated resource release and further style creation.

    /**
     * Selects a palette and loads the bundled Atkinson Hyperlegible font at a
     * rounded pixel size of {@code 16 * density}. Nine-patch skins are deferred
     * until {@link #create()}; the font is allocated immediately.
     *
     * @param light   true for the light palette, false for the dark palette
     * @param density finite size multiplier in the inclusive range 0.5 to 3
     * @throws IllegalArgumentException if density is outside the supported range
     * @throws IllegalStateException    if the bundled font is missing or cannot be read
     */
    public ProfessionalTheme(boolean light, float density) {
        if (!Float.isFinite(density) || density < .5f || density > 3)
            throw new IllegalArgumentException("Density must be between 0.5 and 3");
        this.density = density;
        surface = new Color(light ? 0xFFF4F6FA : 0xFF121823);
        raised = new Color(light ? 0xFFFFFFFF : 0xFF1C2533);
        hover = new Color(light ? 0xFFE4EAF5 : 0xFF29374B);
        text = new Color(light ? 0xFF172338 : 0xFFEAF0FA);
        muted = new Color(light ? 0xFF52627A : 0xFF9AAAC0);
        accent = new Color(light ? 0xFF285DCE : 0xFF76A5FF);
        border = new Color(light ? 0xFFBCC9DA : 0xFF3E506A);
        disabled = new Color(light ? 0xFFDDE3EC : 0xFF27303D);
        try (var stream = ProfessionalTheme.class.getResourceAsStream("/ui/AtkinsonHyperlegible-Regular.ttf")) {
            if (stream == null) throw new IllegalStateException("Bundled UI font is missing");
            fontData = FontData.load(stream.readAllBytes(), Math.round(16 * density), 32, 224);
            font = new Font(fontData);
        } catch (IOException ex) {throw new IllegalStateException("Cannot load UI font", ex);}
    }

    /**
     * Creates the dark palette at unit density and loads its owned UI font.
     * Has the same graphics-thread and resource-lifetime requirements as the
     * configurable constructor.
     */
    public ProfessionalTheme() {this(false, 1);}

    /**
     * Stores a shared color under a typed {@code nano.<name>.<key>} style key.
     * No color copy is made, allowing generated rules to share the palette.
     *
     * @param rule  destination rule
     * @param name  control namespace
     * @param key   property suffix
     * @param value color reference to store
     */
    private static void color(ThemeRule rule, String name, String key, Color value) {
        rule.set(StyleKey.of("nano." + name + "." + key, Color.class), value);
    }

    /**
     * Returns the font shared by generated styles. Ownership stays with this theme;
     * callers must not dispose it separately or use it after {@link #close()}.
     *
     * @return the shared font reference
     */
    public Font getFont() {return font;}

    /**
     * Builds a new style collection with semantic tokens and state-specific rules
     * for both UI backends. Nano control classes are initialized first to register
     * their keys. Each call allocates additional owned texture skins; previously
     * returned collections remain valid until this theme is closed.
     *
     * @return a new collection sharing this theme's font and palette
     * @throws IllegalStateException if the theme has already been closed
     */
    @Override
    public ThemeData create() {
        if (closed) throw new IllegalStateException("Theme has been closed");
        for (Class<?> type : List.of(NanoPanel.class, NanoButton.class, NanoTextField.class, NanoCheckbox.class,
                NanoSlider.class, NanoProgressBar.class, NanoScrollPanel.class, NanoGrid.class, NanoComboBox.class,
                NanoCollapsibleSection.class, NanoDataTable.class, NanoSplitPane.class, NanoTabbedPane.class,
                NanoTooltip.class, NanoVirtualList.class)) {
            try {
                Class.forName(type.getName(), true, type.getClassLoader());
            } catch (ClassNotFoundException impossible) {throw new AssertionError(impossible);}
        }
        ThemeData data = new ThemeData();
        data.setToken(UITokens.CONTROL_HEIGHT, 36 * density);
        data.setToken(UITokens.SPACING, 8 * density);
        data.setToken(UITokens.RADIUS, 6 * density);
        data.setToken(UITokens.FONT_SIZE, 16 * density);
        data.setToken(UITokens.SURFACE, surface);
        data.setToken(UITokens.TEXT, text);
        data.setToken(UITokens.ACCENT, accent);
        data.setToken(UITokens.ERROR, error);
        data.setToken(Label.FONT_KEY, font);
        data.setToken(Label.COLOR_KEY, text);
        Drawable normal = skin(raised, border), over = skin(hover, border), focus = skin(raised, accent);
        Drawable pressed = skin(hover, accent), inactive = skin(disabled, border);
        Drawable track = skin(surface, border), fill = skin(accent, accent);
        data.rule(Panel.class, "surface").set(Panel.BACKGROUND_KEY, normal);
        data.rule(Panel.class, "table-row").set(Panel.BACKGROUND_KEY, normal);
        data.rule(Panel.class, "table-row", StyleState.SELECTED).set(Panel.BACKGROUND_KEY, focus);
        data.rule(Panel.class, "table-row", StyleState.FOCUSED).set(Panel.BACKGROUND_KEY, focus);
        data.rule(Button.class).set(Button.BACKGROUND_KEY, normal);
        data.rule(Button.class, StyleState.SELECTED).set(Button.BACKGROUND_KEY, focus);
        data.rule(Button.class, StyleState.HOVERED).set(Button.BACKGROUND_KEY, over);
        data.rule(Button.class, StyleState.FOCUSED).set(Button.BACKGROUND_KEY, focus);
        data.rule(Button.class, StyleState.PRESSED).set(Button.BACKGROUND_KEY, pressed);
        data.rule(Button.class, StyleState.DISABLED).set(Button.BACKGROUND_KEY, inactive);
        data.rule(Panel.class, "window-frame").set(Panel.BACKGROUND_KEY, normal);
        data.rule(Button.class, "window-title").set(Button.BACKGROUND_KEY, over);
        data.rule(Button.class, "window-title", StyleState.FOCUSED).set(Button.BACKGROUND_KEY, focus);
        data.rule(Button.class, "window-title", StyleState.PRESSED).set(Button.BACKGROUND_KEY, pressed);
        data.rule(Button.class, "window-close").set(Button.BACKGROUND_KEY, over);
        data.rule(Button.class, "window-close", StyleState.HOVERED).set(Button.BACKGROUND_KEY, skin(error, error));
        data.rule(Button.class, "window-close", StyleState.FOCUSED).set(Button.BACKGROUND_KEY, focus);
        data.rule(Button.class, "window-close", StyleState.PRESSED).set(Button.BACKGROUND_KEY, skin(error, accent));
        Drawable clearGrip = squareSkin(new Color(0x00000000), new Color(0x00000000));
        data.rule(Button.class, "window-grip").set(Button.BACKGROUND_KEY, clearGrip);
        data.rule(Button.class, "window-grip", StyleState.HOVERED).set(Button.BACKGROUND_KEY, fill);
        data.rule(Button.class, "window-grip", StyleState.FOCUSED).set(Button.BACKGROUND_KEY, fill);
        data.rule(Button.class, "window-grip", StyleState.PRESSED).set(Button.BACKGROUND_KEY, fill);
        Drawable pane = squareSkin(raised, raised), chrome = squareSkin(surface, border);
        Drawable flat = squareSkin(surface, surface), rowHover = squareSkin(hover, hover);
        Drawable rowSelected = squareSkin(hover, accent), editor = squareSkin(raised, border);
        Drawable editorFocus = squareSkin(raised, accent), command = squareSkin(hover, border);
        data.rule(Panel.class, "chooser-pane").set(Panel.BACKGROUND_KEY, pane);
        data.rule(Panel.class, "chooser-chrome").set(Panel.BACKGROUND_KEY, chrome);
        data.rule(Button.class, "chooser-row").set(Button.BACKGROUND_KEY, pane);
        data.rule(Button.class, "chooser-row", StyleState.HOVERED).set(Button.BACKGROUND_KEY, rowHover);
        data.rule(Button.class, "chooser-row", StyleState.SELECTED).set(Button.BACKGROUND_KEY, rowSelected);
        data.rule(Button.class, "chooser-row", StyleState.FOCUSED).set(Button.BACKGROUND_KEY, rowSelected);
        for (String name : List.of("chooser-tool", "chooser-heading", "chooser-divider")) {
            data.rule(Button.class, name).set(Button.BACKGROUND_KEY, name.equals("chooser-heading") ? pane : flat);
            data.rule(Button.class, name, StyleState.HOVERED).set(Button.BACKGROUND_KEY, rowHover);
            data.rule(Button.class, name, StyleState.FOCUSED).set(Button.BACKGROUND_KEY, editorFocus);
        }
        data.rule(Button.class, "chooser-action").set(Button.BACKGROUND_KEY, command);
        data.rule(Button.class, "chooser-action", StyleState.FOCUSED).set(Button.BACKGROUND_KEY, editorFocus);
        data.rule(Button.class, "chooser-primary").set(Button.BACKGROUND_KEY, rowSelected);
        data.rule(TextField.class, "chooser-editor").set(TextField.BACKGROUND_KEY, editor)
                .set(TextField.HOVER_BACKGROUND_KEY, editor).set(TextField.FOCUSED_BACKGROUND_KEY, editorFocus)
                .set(TextField.PADDING_KEY, 6f);
        data.rule(TextField.class).set(TextField.BACKGROUND_KEY, normal).set(TextField.HOVER_BACKGROUND_KEY, over)
                .set(TextField.FOCUSED_BACKGROUND_KEY, focus).set(TextField.PLACEHOLDER_COLOR_KEY, muted)
                .set(TextField.CARET_COLOR_KEY, accent).set(TextField.SELECTION_COLOR_KEY, new Color(0x665B8DEF))
                .set(TextField.PADDING_KEY, 10 * density);
        data.rule(Slider.class).set(Slider.TRACK_KEY, track).set(Slider.FILL_KEY, fill).set(Slider.THUMB_KEY, focus)
                .set(Slider.THUMB_WIDTH_KEY, 18 * density).set(Slider.THUMB_HEIGHT_KEY, 18 * density)
                .set(Slider.TRACK_HEIGHT_KEY, 6 * density);
        data.rule(Checkbox.class).set(Checkbox.BACKGROUND_KEY, normal).set(Checkbox.CHECKMARK_KEY, fill);
        data.rule(Checkbox.class, StyleState.FOCUSED).set(Checkbox.BACKGROUND_KEY, focus);
        data.rule(ProgressBar.class).set(ProgressBar.BACKGROUND_KEY, track).set(ProgressBar.FOREGROUND_KEY, fill);
        data.rule(ScrollPanel.class).set(ScrollPanel.VERTICAL_BAR_BACKGROUND_KEY, track).set(ScrollPanel.VERTICAL_BAR_FOREGROUND_KEY, focus)
                .set(ScrollPanel.HORIZONTAL_BAR_BACKGROUND_KEY, track).set(ScrollPanel.HORIZONTAL_BAR_FOREGROUND_KEY, focus);
        data.rule(Modal.class).set(Modal.BACKGROUND_KEY, skin(new Color(0xAA070C15), new Color(0xAA070C15)))
                .set(Modal.DIALOG_BACKGROUND_KEY, normal);
        nano(data.rule(NanoPanel.class), "panel");
        nano(data.rule(NanoButton.class), "button");
        data.rule(NanoButton.class, StyleState.SELECTED)
                .set(NanoButton.BORDER_COLOR_KEY, accent)
                .set(NanoButton.HOVER_BORDER_COLOR_KEY, accent)
                .set(NanoButton.FOCUSED_BORDER_COLOR_KEY, accent)
                .set(NanoButton.BACKGROUND_COLOR_KEY, hover);
        nano(data.rule(NanoTextField.class), "textfield");
        nano(data.rule(NanoCheckbox.class), "checkbox");
        nano(data.rule(NanoSlider.class), "slider");
        nano(data.rule(NanoProgressBar.class), "progressbar");
        nano(data.rule(NanoScrollPanel.class), "scrollpanel");
        nano(data.rule(NanoGrid.class), "grid");
        nano(data.rule(NanoComboBox.class), "combobox");
        data.rule(NanoLabel.class).set(NanoLabel.COLOR_KEY, text).set(NanoLabel.FONT_SIZE_KEY, 16 * density);
        data.rule(NanoSlider.class).set(NanoSlider.TRACK_HEIGHT_KEY, 6 * density).set(NanoSlider.THUMB_SIZE_KEY, 18 * density);
        data.rule(NanoScrollPanel.class).set(NanoScrollPanel.VERTICAL_BAR_FOREGROUND_COLOR_KEY, muted)
                .set(NanoScrollPanel.HORIZONTAL_BAR_FOREGROUND_COLOR_KEY, muted)
                .set(NanoScrollPanel.VERTICAL_BAR_BACKGROUND_COLOR_KEY, surface)
                .set(NanoScrollPanel.HORIZONTAL_BAR_BACKGROUND_COLOR_KEY, surface);
        data.rule(NanoModal.class).set(NanoPanel.BACKGROUND_COLOR_KEY, new Color(0xAA070C15))
                .set(NanoPanel.HOVER_BACKGROUND_COLOR_KEY, new Color(0xAA070C15))
                .set(NanoPanel.FOCUSED_BACKGROUND_COLOR_KEY, new Color(0xAA070C15))
                .set(NanoPanel.PRESSED_BACKGROUND_COLOR_KEY, new Color(0xAA070C15))
                .set(NanoPanel.BORDER_WIDTH_KEY, 0f)
                .set(NanoModal.DIALOG_BACKGROUND_COLOR_KEY, raised).set(NanoModal.DIALOG_BORDER_COLOR_KEY, border);
        return data;
    }

    /**
     * Populates a Nano control rule with palette-based state colors and scaled
     * typography and corner radius. Keys use the control's existing namespace;
     * control-specific dimensions are supplied separately by {@link #create()}.
     *
     * @param rule mutable destination rule
     * @param name Nano control namespace suffix, such as {@code button}
     */
    private void nano(ThemeRule rule, String name) {
        // Adapter to the existing Nano key namespace; the public semantic palette is shared.
        color(rule, name, "backgroundColor", raised);
        color(rule, name, "hoverBackgroundColor", hover);
        color(rule, name, "focusedBackgroundColor", raised);
        color(rule, name, "pressedBackgroundColor", hover);
        color(rule, name, "disabledBackgroundColor", disabled);
        for (String key : List.of("borderColor", "hoverBorderColor", "disabledBorderColor"))
            color(rule, name, key, border);
        for (String key : List.of("focusedBorderColor", "pressedBorderColor", "fillColor", "hoverFillColor", "focusedFillColor", "foregroundColor", "checkmarkColor", "caretColor"))
            color(rule, name, key, accent);
        for (String key : List.of("textColor", "hoverTextColor", "focusedTextColor", "pressedTextColor", "thumbColor", "hoverThumbColor", "focusedThumbColor"))
            color(rule, name, key, text);
        color(rule, name, "disabledTextColor", muted);
        color(rule, name, "placeholderColor", muted);
        color(rule, name, "trackColor", surface);
        color(rule, name, "selectionColor", new Color(0x665B8DEF));
        rule.set(StyleKey.of("nano." + name + ".fontSize", Float.class), 16 * density);
        rule.set(StyleKey.of("nano." + name + ".cornerRadius", Float.class), 6 * density);
    }

    /**
     * Rasterizes a 20-by-20 RGBA rounded rectangle with six-pixel patch margins
     * and an antialiased border. The created texture is retained for disposal by
     * this theme; callers receive a drawable that borrows it.
     *
     * @param fill interior color sampled during rasterization
     * @param edge outline color sampled during rasterization
     * @return a drawable backed by a newly allocated owned nine-patch texture
     */
    private Drawable skin(Color fill, Color edge) {
        // Small antialiased nine-patch: texture controls retain their native rendering path.
        int size = 20, radius = 6;
        var pixels = BufferUtils.createByteBuffer(size * size * 4);
        for (int y = 0; y < size; y++)
            for (int x = 0; x < size; x++) {
                float dx = Math.max(Math.abs(x + .5f - size / 2f) - (size / 2f - radius), 0);
                float dy = Math.max(Math.abs(y + .5f - size / 2f) - (size / 2f - radius), 0);
                float distance = (float) Math.sqrt(dx * dx + dy * dy) - radius;
                float alpha = Math.clamp(.5f - distance, 0, 1);
                boolean outline = distance > -1.5f || x == 0 || y == 0 || x == size - 1 || y == size - 1;
                Color c = outline ? edge : fill;
                pixels.put((byte) (c.r() * 255)).put((byte) (c.g() * 255)).put((byte) (c.b() * 255)).put((byte) (c.a() * alpha * 255));
            }
        pixels.flip();
        var skin = new NinePatchTexture(new TextureData(pixels, size, size), radius, radius, radius, radius);
        skins.add(skin);
        return new NinePatchDrawable(skin);
    }

    /**
     * Creates a square one-pixel bordered skin for desktop-style chooser chrome.
     * The texture is owned with the rest of this theme and disposed by close.
     * @param fill interior color
     * @param edge one-pixel edge color, equal to fill for borderless rows
     * @return borrowed nine-patch drawable
     */
    private Drawable squareSkin(Color fill, Color edge) {
        int size = 4;
        var pixels = BufferUtils.createByteBuffer(size * size * 4);
        for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) {
            Color color = x == 0 || y == 0 || x == size - 1 || y == size - 1 ? edge : fill;
            pixels.put((byte) (color.r() * 255)).put((byte) (color.g() * 255)).put((byte) (color.b() * 255)).put((byte) (color.a() * 255));
        }
        pixels.flip();
        var texture = new NinePatchTexture(new TextureData(pixels, size, size), 1, 1, 1, 1); skins.add(texture);
        return new valthorne.graphics.texture.NinePatchDrawable(texture);
    }

    /**
     * Releases every generated skin, then the font and its data. Repeated calls
     * return immediately. Call on the graphics thread after dependent UI roots
     * have been detached or disposed; existing style collections retain references
     * to the released resources and must no longer be rendered.
     */
    @Override
    public void close() {
        if (closed) return;
        closed = true;
        for (var skin : skins) skin.dispose();
        skins.clear();
        font.dispose();
        fontData.dispose();
    }
}
