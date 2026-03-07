package valthorne.ui.elements;

import valthorne.graphics.font.Font;
import valthorne.graphics.texture.TextureBatch;
import valthorne.math.MathUtils;
import valthorne.math.Vector2f;
import valthorne.ui.Element;
import valthorne.ui.enums.Alignment;
import valthorne.ui.styles.ProgressBarStyle;

/**
 * <h1>ProgressBar</h1>
 *
 * <p>
 * {@code ProgressBar} is a UI element that visually represents progress within a numeric range.
 * It renders a background, a foreground fill representing the current progress, and optionally
 * a percentage label centered inside the bar.
 * </p>
 *
 * <p>
 * The element interpolates its visible progress value toward the real progress value over time.
 * This smoothing effect produces visually pleasing transitions instead of abrupt jumps when
 * progress values change.
 * </p>
 *
 * <h2>Progress behavior</h2>
 *
 * <p>
 * The progress bar operates between a minimum and maximum value. The internal progress value
 * represents the actual target progress, while the displayed progress interpolates toward that
 * value each frame using {@link MathUtils#lerp}.
 * </p>
 *
 * <p>
 * This design allows progress updates to appear animated automatically without requiring explicit
 * animation logic from the caller.
 * </p>
 *
 * <h2>Rendering structure</h2>
 *
 * <p>
 * Rendering occurs in three stages:
 * </p>
 *
 * <ol>
 *     <li>The background drawable fills the full bounds of the element.</li>
 *     <li>The foreground drawable fills a portion of the width based on the current percentage.</li>
 *     <li>An optional centered percentage label is rendered using a {@link Font}.</li>
 * </ol>
 *
 * <h2>Percentage display</h2>
 *
 * <p>
 * When enabled, the progress bar renders text showing the progress percentage with two decimal
 * places. The label is automatically centered inside the progress bar using
 * {@link Alignment#align}.
 * </p>
 *
 * <h2>Example</h2>
 *
 * <pre>{@code
 * ProgressBar bar = new ProgressBar(
 *     0f,
 *     100f,
 *     ProgressBarStyle.of()
 *         .background(backgroundDrawable)
 *         .foreground(foregroundDrawable)
 *         .fontData(fontData)
 * );
 *
 * bar.setSize(300f, 24f);
 * bar.setDisplayPercentage(true);
 * bar.setProgress(50f);
 * }</pre>
 *
 * <p>
 * The progress bar will smoothly animate toward 50% and display the percentage text in the center.
 * </p>
 *
 * @author Albert Beaupre
 * @since March 7th, 2026
 */
public class ProgressBar extends Element {

    private ProgressBarStyle style; // The visual style providing background, foreground, and font configuration.
    private final Font font; // The font used to render the optional percentage text.
    private final float min; // The minimum progress value.
    private final float max; // The maximum progress value.

    private float progress; // The target progress value.
    private float displayedProgress; // The interpolated progress value used for rendering.
    private boolean displayPercentage; // Whether the percentage text should be displayed.

    /**
     * Creates a new progress bar with the specified range and style.
     *
     * <p>
     * The progress initially starts at the minimum value. A {@link Font} is created using the
     * style's {@link valthorne.graphics.font.FontData}.
     * </p>
     *
     * @param min   the minimum progress value
     * @param max   the maximum progress value
     * @param style the visual style used for rendering
     */
    public ProgressBar(float min, float max, ProgressBarStyle style) {
        this.min = min;
        this.max = max;
        this.progress = min;
        this.style = style;

        this.font = new Font(style.getFontData());
        this.font.setText("0.00%");
    }

    /**
     * Updates the progress bar.
     *
     * <p>
     * The displayed progress interpolates toward the real progress value using linear interpolation.
     * This creates a smooth animation when the progress changes.
     * </p>
     *
     * <p>
     * When percentage display is enabled and the displayed progress changes, the text label
     * is updated accordingly.
     * </p>
     *
     * @param delta the elapsed time in seconds since the previous update
     */
    @Override
    public void update(float delta) {
        this.displayedProgress = MathUtils.lerp(displayedProgress, progress, delta * 20f);

        if (displayPercentage && displayedProgress != progress) {
            this.font.setText(String.format("%.2f%%", this.displayedProgress));
        }
    }

    /**
     * Draws the progress bar.
     *
     * <p>
     * The background is drawn first to fill the full element bounds. The foreground is then
     * drawn over it with a width proportional to the current displayed progress percentage.
     * </p>
     *
     * <p>
     * If percentage display is enabled, the centered percentage label is rendered last.
     * </p>
     *
     * @param batch the texture batch used for rendering
     */
    @Override
    public void draw(TextureBatch batch) {
        float percentage = displayedProgress / (max - min);

        style.getBackground().draw(batch, this.x, this.y, this.width, this.height);
        style.getForeground().draw(batch, this.x, this.y, this.width * percentage, this.height);

        if (displayPercentage) {
            font.draw(batch);
        }
    }

    /**
     * Sets the position of the progress bar.
     *
     * <p>
     * After updating the element position, the percentage label is re-centered inside the bar.
     * </p>
     *
     * @param x the new x coordinate
     * @param y the new y coordinate
     */
    @Override
    public void setPosition(float x, float y) {
        super.setPosition(x, y);

        Vector2f fontPosition = Alignment.align(this, font, Alignment.CENTER);
        font.setPosition(fontPosition.getX(), fontPosition.getY());
    }

    /**
     * Sets the size of the progress bar.
     *
     * <p>
     * After resizing the element, the percentage label is re-centered within the new bounds.
     * </p>
     *
     * @param width  the new width
     * @param height the new height
     */
    @Override
    public void setSize(float width, float height) {
        super.setSize(width, height);

        Vector2f fontPosition = Alignment.align(this, font, Alignment.CENTER);
        font.setPosition(fontPosition.getX(), fontPosition.getY());
    }

    /**
     * Updates the target progress value.
     *
     * <p>
     * The value is clamped between the configured minimum and maximum. The visible progress
     * will gradually interpolate toward this value during updates.
     * </p>
     *
     * <p>
     * If percentage display is enabled, the label text is updated immediately and realigned.
     * </p>
     *
     * @param progress the new progress value
     */
    public void setProgress(float progress) {
        this.progress = MathUtils.clamp(progress, min, max);

        if (displayPercentage) {
            this.font.setText(String.format("%.2f%%", this.progress));

            Vector2f fontPosition = Alignment.align(this, font, Alignment.CENTER);
            font.setPosition(fontPosition.getX(), fontPosition.getY());
        }
    }

    /**
     * Returns the current target progress value.
     *
     * @return the current progress
     */
    public float getProgress() {
        return progress;
    }

    /**
     * Enables or disables percentage text rendering.
     *
     * <p>
     * When enabled, the percentage label is immediately updated and centered.
     * </p>
     *
     * @param displayPercentage true to display the percentage text
     */
    public void setDisplayPercentage(boolean displayPercentage) {
        this.displayPercentage = displayPercentage;

        if (displayPercentage) {
            this.font.setText(String.format("%.2f%%", this.progress));

            Vector2f fontPosition = Alignment.align(this, font, Alignment.CENTER);
            font.setPosition(fontPosition.getX(), fontPosition.getY());
        }
    }

    /**
     * Returns whether the percentage text is displayed.
     *
     * @return true if percentage display is enabled
     */
    public boolean isDisplayPercentage() {
        return displayPercentage;
    }

    /**
     * Sets the visual style used by the progress bar.
     *
     * <p>
     * The style controls the background drawable, foreground drawable,
     * and font configuration used by the progress bar.
     * </p>
     *
     * @param style the new style
     * @return this progress bar for chaining
     */
    public ProgressBar setStyle(ProgressBarStyle style) {
        this.style = style;
        return this;
    }
}