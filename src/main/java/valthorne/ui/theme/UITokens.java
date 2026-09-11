package valthorne.ui.theme;

import valthorne.graphics.Color;

/**
 * Shared semantic style keys for UI dimensions and palette roles. ThemeData can
 * supply these as base tokens, while rules and per-node overrides may replace
 * them during resolution. Sizes use layout units; this holder does not scale
 * values for display density or apply them directly to widgets.
 *
 * <pre>{@code
 * ThemeData theme = new ThemeData();
 * theme.setToken(UITokens.CONTROL_HEIGHT, 36f);
 * theme.setToken(UITokens.SPACING, 8f);
 * theme.setToken(UITokens.ACCENT, new Color(0.2f, 0.5f, 1f, 1f));
 * }</pre>
 *
 * <p>ProfessionalTheme populates these roles alongside concrete widget style
 * properties. A role affects rendering only where a consumer reads it; setting
 * a semantic color does not automatically rewrite every widget-specific color
 * key. CONTROL_HEIGHT is also read by UINode when applying control styling.</p>
 *
 * <p>Keys are globally registered by name through StyleKey. The defaults below
 * apply when these names are first registered here; an earlier registration of
 * the same name and type keeps its original default. Color defaults and control
 * height are null, so consumers must handle unspecified values. Mutable colors
 * supplied by a theme are shared by reference rather than copied by the key.</p>
 *
 * @author Albert Beaupre
 */
public final class UITokens {
    /**
     * Minimum height applied by UINode to texture and Nano buttons and text fields
     * when both height and minimum height are automatic. No key-level default is
     * supplied; ProfessionalTheme assigns a density-scaled value in layout units.
     * An unspecified token leaves the control's existing sizing policy in effect.
     */
    public static final StyleKey<Float> CONTROL_HEIGHT = StyleKey.of("ui.controlHeight", Float.class);
    /**
     * Semantic spacing scale in layout units, initially defaulting to 8.
     * Consumers decide which gaps or padding values use this scale.
     */
    public static final StyleKey<Float> SPACING = StyleKey.of("ui.spacing", Float.class, 8f);
    /**
     * Semantic corner radius in layout units, initially defaulting to 6.
     * This does not automatically update a widget's concrete radius property.
     */
    public static final StyleKey<Float> RADIUS = StyleKey.of("ui.radius", Float.class, 6f);
    /**
     * Semantic font size in layout units, initially defaulting to 16.
     * Font selection and application of this size remain the consumer's responsibility.
     */
    public static final StyleKey<Float> FONT_SIZE = StyleKey.of("ui.fontSize", Float.class, 16f);
    /**
     * Base surface color role, with no default color. Themes supply the shared
     * color used by components that explicitly consume this role.
     */
    public static final StyleKey<Color> SURFACE = StyleKey.of("ui.surface", Color.class);
    /**
     * Primary text color role, with no default color. This semantic value is
     * separate from individual text widgets' concrete style keys.
     */
    public static final StyleKey<Color> TEXT = StyleKey.of("ui.text", Color.class);
    /**
     * Accent color role for emphasis and selection styling, with no default.
     * Widgets or theme builders choose where to apply the shared color value.
     */
    public static final StyleKey<Color> ACCENT = StyleKey.of("ui.accent", Color.class);
    /**
     * Error color role for validation or failure styling, with no default.
     * Assigning it does not set a node's error state or validate input.
     */
    public static final StyleKey<Color> ERROR = StyleKey.of("ui.error", Color.class);
    /**
     * Prevents instances of this static key holder. Token values belong to theme
     * or style maps rather than to an instance of this class.
     */
    private UITokens() {}
}
