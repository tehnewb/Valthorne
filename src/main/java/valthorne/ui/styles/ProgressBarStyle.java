package valthorne.ui.styles;

import valthorne.graphics.Drawable;
import valthorne.graphics.font.FontData;

/**
 * <h1>ProgressBarStyle</h1>
 *
 * <p>
 * {@code ProgressBarStyle} defines the visual configuration used by a
 * {@link valthorne.ui.elements.ProgressBar}. It provides the background,
 * foreground fill, and font data used when rendering the progress bar.
 * </p>
 *
 * <p>
 * Separating visual configuration from the element logic allows progress bars
 * to share behavior while using different visual skins or themes.
 * </p>
 *
 * <h2>Drawable roles</h2>
 *
 * <ul>
 *     <li><b>Background</b> represents the empty bar.</li>
 *     <li><b>Foreground</b> represents the filled portion corresponding to progress.</li>
 * </ul>
 *
 * <h2>Font usage</h2>
 *
 * <p>
 * The {@link FontData} stored in the style is used to construct the font responsible
 * for rendering percentage text when the progress bar displays it.
 * </p>
 *
 * <h2>Example</h2>
 *
 * <pre>{@code
 * ProgressBarStyle style = ProgressBarStyle.of()
 *     .background(backgroundDrawable)
 *     .foreground(foregroundDrawable)
 *     .fontData(fontData);
 * }</pre>
 *
 * @author Albert Beaupre
 * @since March 7th, 2026
 */
public class ProgressBarStyle {

    private FontData fontData; // The font data used to create the progress bar percentage font.
    private Drawable background; // The drawable representing the empty bar.
    private Drawable foreground; // The drawable representing the filled progress portion.

    /**
     * Creates a new empty progress bar style.
     *
     * <p>
     * This static factory method is provided for fluent builder-style configuration.
     * </p>
     *
     * @return a new progress bar style instance
     */
    public static ProgressBarStyle of() {
        return new ProgressBarStyle();
    }

    /**
     * Returns the font data used by this style.
     *
     * @return the font data instance
     */
    public FontData getFontData() {
        return fontData;
    }

    /**
     * Sets the font data used for rendering percentage text.
     *
     * @param fontData the font data instance
     * @return this style for chaining
     */
    public ProgressBarStyle fontData(FontData fontData) {
        this.fontData = fontData;
        return this;
    }

    /**
     * Returns the background drawable.
     *
     * @return the background drawable
     */
    public Drawable getBackground() {
        return background;
    }

    /**
     * Sets the background drawable used for the empty bar.
     *
     * @param background the background drawable
     * @return this style for chaining
     */
    public ProgressBarStyle background(Drawable background) {
        this.background = background;
        return this;
    }

    /**
     * Returns the foreground drawable.
     *
     * @return the foreground drawable
     */
    public Drawable getForeground() {
        return foreground;
    }

    /**
     * Sets the foreground drawable used for the filled progress portion.
     *
     * @param foreground the foreground drawable
     * @return this style for chaining
     */
    public ProgressBarStyle foreground(Drawable foreground) {
        this.foreground = foreground;
        return this;
    }
}