package valthorne.ui.nodes;

import valthorne.graphics.Drawable;
import valthorne.graphics.font.Font;
import valthorne.graphics.texture.TextureBatch;
import valthorne.math.MathUtils;
import valthorne.ui.theme.ResolvedStyle;
import valthorne.ui.theme.StyleKey;

/**
 * <p>
 * {@code ProgressBar} is a visual UI component used to display progress between
 * a configured minimum and maximum value. It supports smooth animated transitions
 * between progress values, optional percentage text display, and both horizontal
 * and vertical fill directions.
 * </p>
 *
 * <p>
 * The control is theme-driven and resolves its visuals from the active style.
 * It supports:
 * </p>
 *
 * <ul>
 *     <li>a background drawable for the full bar area</li>
 *     <li>a foreground drawable for the filled portion</li>
 *     <li>a font used to render percentage text when enabled</li>
 * </ul>
 *
 * <p>
 * The progress bar stores two progress values:
 * </p>
 *
 * <ul>
 *     <li>{@code progress}, which is the actual target progress value</li>
 *     <li>{@code displayedProgress}, which is the animated visual value</li>
 * </ul>
 *
 * <p>
 * Each frame, the displayed value interpolates toward the target value using
 * {@link MathUtils#lerp(float, float, float)}, producing a smooth visual fill
 * transition instead of an immediate jump.
 * </p>
 *
 * <p>
 * When percentage display is enabled and a font is available, the bar caches
 * a percentage string based on the animated value and draws it explicitly.
 * Disabling percentage display clears the borrowed font's legacy text property.
 * Drawable and font resources are borrowed from resolved style and are not disposed
 * here. Although this class extends Panel, its update and draw overrides do not
 * traverse children.
 * </p>
 *
 * <h2>Example Usage</h2>
 *
 * <pre>{@code
 * ProgressBar bar = new ProgressBar(0f, 100f);
 *
 * bar.getLayout()
 *    .width(240)
 *    .height(28);
 *
 * bar.progress(45f)
 *    .displayPercentage(true)
 *    .horizontal(true);
 *
 * float current = bar.getProgress();
 * boolean vertical = bar.isVertical();
 * boolean showingPercent = bar.isDisplayPercentage();
 * Font font = bar.getFont();
 *
 * root.add(bar);
 * // The root's normal update and draw lifecycle visits the progress bar.
 * }</pre>
 *
 * <p>
 * This example demonstrates the complete intended use of the class: construction,
 * sizing, progress updates, percentage display, orientation control, state queries,
 * update, and draw.
 * </p>
 *
 * @author Albert Beaupre
 * @since March 13th, 2026
 */
public class ProgressBar extends Panel {

    /**
     * Style key used to resolve the background drawable for the full progress bar area.
     */
    public static final StyleKey<Drawable> BACKGROUND_KEY = StyleKey.of("background", Drawable.class);

    /**
     * Style key used to resolve the foreground drawable for the filled portion of the bar.
     */
    public static final StyleKey<Drawable> FOREGROUND_KEY = StyleKey.of("foreground", Drawable.class);

    /**
     * Style key used to resolve the font used for percentage text rendering.
     */
    public static final StyleKey<Font> FONT_KEY = StyleKey.of("font", Font.class);

    private final float min; // Minimum logical progress value supported by this bar
    private final float max; // Maximum logical progress value supported by this bar

    private float progress; // Target progress value set by callers
    private float displayedProgress; // Animated progress value currently displayed
    private boolean displayPercentage; // Whether percentage text should be rendered
    private boolean vertical; // Whether the bar fills vertically instead of horizontally

    private Drawable background; // Resolved background drawable from the active style
    private Drawable foreground; // Resolved foreground drawable from the active style
    private String displayText = "0%"; // Cached formatted percentage drawn without changing the font's text during painting.
    private Font font; // Resolved font used for percentage text rendering

    /**
     * <p>
     * Creates a new progress bar with the supplied minimum and maximum bounds.
     * </p>
     *
     * <p>
     * The initial target and displayed progress values are both set to the minimum,
     * meaning the bar starts completely empty relative to the configured range.
     * Bounds are stored without validation or reordering; supply finite min and max
     * with max greater than min for ordinary progress. Equal bounds always display
     * zero percent. Initial orientation is horizontal and percentage text is disabled.
     * </p>
     *
     * @param min the minimum allowed progress value
     * @param max the maximum allowed progress value
     */
    public ProgressBar(float min, float max) {
        this.min = min;
        this.max = max;
        this.progress = min;
        this.displayedProgress = min;
    }

    /**
     * <p>
     * Updates the visual state of this progress bar.
     * </p>
     *
     * <p>
     * The displayed progress is smoothly interpolated toward the target progress.
     * Interpolation uses the factor 1 - exp(-max(0, delta) * 20), giving exponential
     * smoothing for finite frame intervals. Negative delta produces no movement;
     * NaN is not rejected. If percentage display and a font are available, the
     * cached string is refreshed to two decimal places using the default locale.
     * This override does not call the inherited child-update implementation.
     * </p>
     *
     * @param delta the elapsed frame time in seconds
     */
    @Override
    public void update(float delta) {
        displayedProgress = MathUtils.lerp(displayedProgress, progress, (float) (1 - Math.exp(-Math.max(0, delta) * 20f)));

        if (displayPercentage && font != null)
            displayText = String.format("%.2f%%", getPercentage() * 100f);
    }

    /**
     * <p>
     * Sets the target progress value for this bar.
     * </p>
     *
     * <p>
     * The value is clamped into the configured {@code [min, max]} range. If percentage
     * display is enabled and a font is available, the displayed text is also updated.
     * The animated value does not jump to the new target, so that text still
     * reflects the current displayed value until subsequent updates advance it.
     * </p>
     *
     * @param progress the new target progress value
     * @return this progress bar
     */
    public ProgressBar progress(float progress) {
        this.progress = MathUtils.clamp(progress, min, max);

        if (displayPercentage && font != null)
            displayText = String.format("%.2f%%", getPercentage() * 100f);
        return this;
    }

    /**
     * <p>
     * Returns the current target progress value.
     * </p>
     *
     * @return the target progress value
     */
    public float getProgress() {
        return progress;
    }

    /**
     * <p>
     * Enables or disables percentage text display.
     * </p>
     *
     * <p>
     * With a font available, enabling refreshes the cached percentage string.
     * Disabling instead clears the font's legacy text property, which can affect
     * another user of that shared Font. No layout invalidation is performed.
     * </p>
     *
     * @param displayPercentage whether percentage text should be shown
     * @return this progress bar
     */
    public ProgressBar displayPercentage(boolean displayPercentage) {
        this.displayPercentage = displayPercentage;

        if (font != null) {
            if (displayPercentage)
                displayText = String.format("%.2f%%", getPercentage() * 100f);
            else
                font.setText("");
        }
        return this;
    }

    /**
     * <p>
     * Returns whether percentage text display is enabled.
     * </p>
     *
     * @return {@code true} if percentage text should be shown
     */
    public boolean isDisplayPercentage() {
        return displayPercentage;
    }

    /**
     * <p>
     * Returns whether this progress bar is currently configured for vertical filling.
     * </p>
     *
     * @return {@code true} if the bar fills vertically
     */
    public boolean isVertical() {
        return vertical;
    }

    /**
     * <p>
     * Sets whether this progress bar should fill vertically.
     * </p>
     *
     * @param vertical {@code true} for vertical fill, {@code false} for horizontal fill
     * @return this progress bar
     */
    public ProgressBar vertical(boolean vertical) {
        this.vertical = vertical;
        return this;
    }

    /**
     * <p>
     * Sets whether this progress bar should fill horizontally.
     * </p>
     *
     * <p>
     * Internally this is implemented by storing the inverse into the
     * {@code vertical} flag.
     * </p>
     *
     * @param horizontal {@code true} for horizontal fill, {@code false} for vertical fill
     * @return this progress bar
     */
    public ProgressBar horizontal(boolean horizontal) {
        this.vertical = !horizontal;
        return this;
    }

    /**
     * <p>
     * Returns the font currently resolved for this progress bar.
     * This is a borrowed reference refreshed during layout, not an owned copy.
     * </p>
     *
     * @return the resolved font, or {@code null} if none is available
     */
    public Font getFont() {
        return font;
    }

    /**
     * <p>
     * Applies style-driven layout data and visual resources for this progress bar.
     * </p>
     *
     * <p>
     * The current style is resolved and used to update the background drawable,
     * foreground drawable, and optional font. With percentage display enabled,
     * the cached string is refreshed; otherwise a present font's legacy text is
     * cleared. If no style exists,
     * all resolved visual references are cleared.
     * </p>
     */
    @Override
    protected void applyLayout() {
        ResolvedStyle style = getStyle();

        if (style != null) {
            background = style.get(BACKGROUND_KEY);
            foreground = style.get(FOREGROUND_KEY);
            font = style.get(FONT_KEY);

            if (font != null) {
                if (displayPercentage)
                    displayText = String.format("%.2f%%", getPercentage() * 100f);
                else
                    font.setText("");
            }
        } else {
            background = null;
            foreground = null;
            font = null;
        }

        super.applyLayout();
    }

    /**
     * <p>
     * Draws this progress bar using the provided texture batch.
     * </p>
     *
     * <p>
     * The background is drawn first over the full bounds. The foreground is then
     * drawn using the current fill percentage, either horizontally or vertically.
     * If percentage display is enabled and a font is available, centered text is
     * rendered on top of the bar.
     * Horizontal fill grows from the left; vertical fill grows from the bottom
     * in render coordinates. Label.COLOR_KEY supplies text color when present,
     * otherwise white is used. The cached percentage is passed directly to Font.draw.
     * This method does not call Panel.draw or render children, and it relies on
     * the root for visibility checks and a prepared batch.
     * </p>
     *
     * @param batch the texture batch used for rendering
     */
    @Override
    public void draw(TextureBatch batch) {
        float percentage = getPercentage();

        if (background != null)
            background.draw(batch, getRenderX(), getRenderY(), getWidth(), getHeight());

        if (foreground != null) {
            if (vertical) {
                float filledHeight = getHeight() * percentage;
                foreground.draw(batch, getRenderX(), getRenderY(), getWidth(), filledHeight);
            } else {
                float filledWidth = getWidth() * percentage;
                foreground.draw(batch, getRenderX(), getRenderY(), filledWidth, getHeight());
            }
        }

        if (displayPercentage && font != null) {
            float textX = getRenderX() + (getWidth() - font.getWidth(displayText)) * 0.5f;
            float textY = getRenderY() + (getHeight() - font.getHeight(displayText)) * 0.5f;
            font.draw(batch, displayText, textX, textY, getStyle() != null && getStyle().get(Label.COLOR_KEY) != null
                    ? getStyle().get(Label.COLOR_KEY) : valthorne.graphics.Color.WHITE);
        }
    }

    /**
     * <p>
     * Computes the current displayed progress as a normalized percentage in the range
     * {@code [0, 1]}.
     * </p>
     *
     * <p>
     * The returned value is based on the animated {@code displayedProgress} rather
     * than the raw target progress. If the configured range is zero, the method
     * returns {@code 0}.
     * </p>
     *
     * @return the normalized displayed percentage
     */
    private float getPercentage() {
        float range = max - min;
        if (range == 0f)
            return 0f;

        return MathUtils.clamp((displayedProgress - min) / range, 0f, 1f);
    }
}
