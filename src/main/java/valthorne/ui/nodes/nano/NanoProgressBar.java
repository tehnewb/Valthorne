package valthorne.ui.nodes.nano;

import valthorne.graphics.Color;
import valthorne.graphics.texture.TextureBatch;
import valthorne.math.MathUtils;
import valthorne.ui.NanoUtility;
import valthorne.ui.NodeAction;
import valthorne.ui.UINode;
import valthorne.ui.theme.ResolvedStyle;
import valthorne.ui.theme.StyleKey;

import static org.lwjgl.nanovg.NanoVG.*;

/**
 * NanoVG progress indicator with a target value and a separately animated display
 * value. Horizontal fill grows left to right; vertical fill grows from the bottom.
 * The optional percentage text and finished state use displayed progress rather
 * than the pending target. Call update with elapsed seconds to advance animation.
 *
 * <p>Range endpoints are retained without validation; use finite ordered values.
 * Colors and registered fonts are borrowed, and style application can overwrite
 * local paint and animation settings. The progress callback runs synchronously
 * when displayed progress changes, rather than on every target assignment.</p>
 *
 * @author Albert Beaupre
 */
public class NanoProgressBar extends UINode implements NanoNode {

    /**
     * Theme color for the unfilled background.
     */
    public static final StyleKey<Color> BACKGROUND_COLOR_KEY = StyleKey.of("nano.progressbar.backgroundColor", Color.class, new Color(0xFF2A2A2A));
    /**
     * Theme color for the displayed filled region.
     */
    public static final StyleKey<Color> FOREGROUND_COLOR_KEY = StyleKey.of("nano.progressbar.foregroundColor", Color.class, new Color(0xFF7AA2FF));
    /**
     * Theme border-stroke color.
     */
    public static final StyleKey<Color> BORDER_COLOR_KEY = StyleKey.of("nano.progressbar.borderColor", Color.class, new Color(0xFF555555));
    /**
     * Theme percentage-text color.
     */
    public static final StyleKey<Color> TEXT_COLOR_KEY = StyleKey.of("nano.progressbar.textColor", Color.class, Color.WHITE);

    /**
     * Theme registered font name for percentage text.
     */
    public static final StyleKey<String> FONT_NAME_KEY = StyleKey.of("nano.progressbar.fontName", String.class, "default");
    /**
     * Theme percentage font size in UI units.
     */
    public static final StyleKey<Float> FONT_SIZE_KEY = StyleKey.of("nano.progressbar.fontSize", Float.class, 16f);
    /**
     * Theme corner radius in UI units.
     */
    public static final StyleKey<Float> CORNER_RADIUS_KEY = StyleKey.of("nano.progressbar.cornerRadius", Float.class, 6f);
    /**
     * Theme border width and fill inset in UI units.
     */
    public static final StyleKey<Float> BORDER_WIDTH_KEY = StyleKey.of("nano.progressbar.borderWidth", Float.class, 1f);
    /**
     * Theme horizontal percentage-label padding in UI units.
     */
    public static final StyleKey<Float> TEXT_PADDING_X_KEY = StyleKey.of("nano.progressbar.textPaddingX", Float.class, 8f);
    /**
     * Theme interpolation duration in seconds, defaulting to 0.25.
     */
    public static final StyleKey<Float> ANIMATION_DURATION_KEY = StyleKey.of("nano.progressbar.animationDuration", Float.class, 0.25f);

    private final float min; // Unchecked lower endpoint of the immutable progress range.
    private final float max; // Unchecked upper endpoint of the immutable progress range.

    private float progress; // Clamped requested target value.
    private float displayedProgress; // Animated value used by drawing and completion checks.
    private float animationStartProgress; // Displayed value captured when the current transition starts.
    private float animationTargetProgress; // Target captured for the current transition.
    private float animationElapsed; // Elapsed seconds within the current transition.
    private float animationDuration = 0.25f; // Transition duration in seconds; zero disables interpolation.
    private boolean displayPercentage; // Whether to paint a percentage label.
    private boolean vertical; // Whether fill grows upward instead of rightward.

    private Color backgroundColor = new Color(0xFF2A2A2A); // Borrowed unfilled background color.
    private Color foregroundColor = new Color(0xFF7AA2FF); // Borrowed filled region color.
    private Color borderColor = new Color(0xFF555555); // Borrowed border stroke color.
    private Color textColor = Color.WHITE; // Borrowed percentage text color.

    private String fontName = "default"; // Borrowed NanoVG font registration name.
    private float fontSize = 16f; // Percentage font size in UI units.
    private float cornerRadius = 6f; // Rounded-corner radius in UI units.
    private float borderWidth = 1f; // Border width and fill inset in UI units.
    private float textPaddingX = 8f; // Horizontal percentage padding in UI units.
    private NodeAction<NanoProgressBar> progressAction; // Optional synchronous displayed-value callback.

    /**
     * Initializes target, displayed value, and animation endpoints to min. The
     * constructor stores range endpoints unchecked; use finite min not greater than
     * max. A zero-width range draws zero percent regardless of its finished state.
     *
     * @param min lower progress endpoint
     * @param max upper progress endpoint
     */
    public NanoProgressBar(float min, float max) {
        this.min = min;
        this.max = max;
        this.progress = min;
        this.displayedProgress = min;
        this.animationStartProgress = min;
        this.animationTargetProgress = min;
    }

    /**
     * Clamps a new target to the stored range and starts interpolation from the
     * current displayed value. An unchanged target does nothing. With zero duration,
     * snaps the display and invokes the callback immediately; otherwise notification
     * waits for update to change the displayed value.
     *
     * @param progress requested finite target value
     * @return this progress bar
     */
    public NanoProgressBar progress(float progress) {
        float clamped = MathUtils.clamp(progress, min, max);

        if (this.progress == clamped)
            return this;

        this.progress = clamped;
        this.animationStartProgress = displayedProgress;
        this.animationTargetProgress = clamped;
        this.animationElapsed = 0f;

        if (animationDuration <= 0f) {
            this.displayedProgress = clamped;
            this.animationStartProgress = clamped;
            this.animationTargetProgress = clamped;
            this.animationElapsed = 0f;

            if (progressAction != null)
                progressAction.perform(this);
        }

        return this;
    }


    /**
     * Replaces the synchronous displayed-value callback. It runs during animated
     * updates or immediate target snaps, and can read both target and displayed
     * values. Assigning null disables notifications.
     *
     * @param action callback receiving this bar, or null
     * @return this progress bar
     */
    public NanoProgressBar onProgress(NodeAction<NanoProgressBar> action) {
        this.progressAction = action;
        return this;
    }

    /**
     * Reads the clamped target value, which may be ahead of the animated display.
     *
     * @return current target in the configured range's units
     */
    public float getProgress() {
        return progress;
    }

    /**
     * Reads the value currently represented by fill, percentage text, and completion.
     *
     * @return animated display value
     */
    public float getDisplayedProgress() {
        return displayedProgress;
    }

    /**
     * Reads the configured interpolation duration, which styles may also set.
     *
     * @return transition duration in seconds
     */
    public float getAnimationDuration() {
        return animationDuration;
    }

    /**
     * Clamps negative duration to zero. Zero snaps all animation state to the current
     * target without invoking the progress callback. Changing a positive duration
     * retains elapsed time and endpoints for the next update.
     *
     * @param animationDuration finite requested transition seconds
     * @return this progress bar
     */
    public NanoProgressBar animationDuration(float animationDuration) {
        this.animationDuration = Math.max(0f, animationDuration);

        if (this.animationDuration <= 0f) {
            this.displayedProgress = progress;
            this.animationStartProgress = progress;
            this.animationTargetProgress = progress;
            this.animationElapsed = 0f;
        }

        return this;
    }

    /**
     * Enables or hides centered percentage text without changing progress or layout.
     * Text uses displayed progress formatted with two decimal places.
     *
     * @param displayPercentage whether to draw the percentage label
     * @return this progress bar
     */
    public NanoProgressBar displayPercentage(boolean displayPercentage) {
        this.displayPercentage = displayPercentage;
        return this;
    }

    /**
     * Reads whether the percentage label is enabled independently of node visibility.
     *
     * @return configured percentage-text flag
     */
    public boolean isDisplayPercentage() {
        return displayPercentage;
    }

    /**
     * Reads fill orientation without changing animation state.
     *
     * @return true for bottom-to-top fill, false for left-to-right fill
     */
    public boolean isVertical() {
        return vertical;
    }

    /**
     * Selects bottom-to-top fill when true and left-to-right fill otherwise.
     * Progress values, animation timing, and layout dimensions are unchanged.
     *
     * @param vertical requested vertical orientation
     * @return this progress bar
     */
    public NanoProgressBar vertical(boolean vertical) {
        this.vertical = vertical;
        return this;
    }

    /**
     * Selects left-to-right fill when true and bottom-to-top fill otherwise.
     * Equivalent to assigning the inverse vertical flag.
     *
     * @param horizontal requested horizontal orientation
     * @return this progress bar
     */
    public NanoProgressBar horizontal(boolean horizontal) {
        this.vertical = !horizontal;
        return this;
    }

    /**
     * Retains a non-null color for the unfilled background without copying it.
     * Null preserves the current reference; resolved styles may later replace it.
     *
     * @param color mutable paint color, or null to keep the current value
     * @return this progress bar
     */
    public NanoProgressBar backgroundColor(Color color) {
        if (color != null) this.backgroundColor = color;
        return this;
    }

    /**
     * Retains a non-null color for the filled region without copying it.
     * Null preserves the current reference; resolved styles may later replace it.
     *
     * @param color mutable paint color, or null to keep the current value
     * @return this progress bar
     */
    public NanoProgressBar foregroundColor(Color color) {
        if (color != null) this.foregroundColor = color;
        return this;
    }

    /**
     * Retains a non-null color for the border stroke without copying it.
     * Null preserves the current reference; resolved styles may later replace it.
     *
     * @param color mutable paint color, or null to keep the current value
     * @return this progress bar
     */
    public NanoProgressBar borderColor(Color color) {
        if (color != null) this.borderColor = color;
        return this;
    }

    /**
     * Retains a non-null color for the percentage text without copying it.
     * Null preserves the current reference; resolved styles may later replace it.
     *
     * @param color mutable paint color, or null to keep the current value
     * @return this progress bar
     */
    public NanoProgressBar textColor(Color color) {
        if (color != null) this.textColor = color;
        return this;
    }

    /**
     * Retains a nonblank registered NanoVG font name; null or blank leaves it unchanged.
     * No font is loaded, and resolved styles may later replace the name.
     *
     * @param fontName existing font registration name
     * @return this progress bar
     */
    public NanoProgressBar fontName(String fontName) {
        if (fontName != null && !fontName.isBlank()) this.fontName = fontName;
        return this;
    }

    /**
     * Sets percentage font size in UI units, clamping below 1 to 1.
     * This changes painting rather than measured layout; styles may override it.
     *
     * @param fontSize finite requested percentage font size
     * @return this progress bar
     */
    public NanoProgressBar fontSize(float fontSize) {
        this.fontSize = Math.max(1f, fontSize);
        return this;
    }

    /**
     * Sets rounded-corner radius in UI units, clamping below 0 to 0.
     * This changes painting rather than measured layout; styles may override it.
     *
     * @param cornerRadius finite requested rounded-corner radius
     * @return this progress bar
     */
    public NanoProgressBar cornerRadius(float cornerRadius) {
        this.cornerRadius = Math.max(0f, cornerRadius);
        return this;
    }

    /**
     * Sets border width and fill inset in UI units, clamping below 0 to 0.
     * This changes painting rather than measured layout; styles may override it.
     *
     * @param borderWidth finite requested border width and fill inset
     * @return this progress bar
     */
    public NanoProgressBar borderWidth(float borderWidth) {
        this.borderWidth = Math.max(0f, borderWidth);
        return this;
    }

    /**
     * Sets horizontal percentage padding in UI units, clamping below 0 to 0.
     * This changes painting rather than measured layout; styles may override it.
     *
     * @param textPaddingX finite requested horizontal percentage padding
     * @return this progress bar
     */
    public NanoProgressBar textPaddingX(float textPaddingX) {
        this.textPaddingX = Math.max(0f, textPaddingX);
        return this;
    }

    /**
     * Checks the displayed value against max. A target reaching max is insufficient
     * until animation arrives; equal range endpoints are finished immediately.
     *
     * @return true when displayed progress is at least max
     */
    public boolean isFinished() {
        return displayedProgress >= max;
    }

    /**
     * Allocates no resources. The root manages the NanoVG context and the application
     * must register the font used for percentage text.
     */
    @Override
    public void onCreate() {
    }

    /**
     * Releases no native resources because the widget borrows its colors and font
     * registration and owns no NanoVG context.
     */
    @Override
    public void onDestroy() {
    }

    /**
     * Interpolates displayed progress toward the target, clamping negative elapsed
     * time to zero and the animation fraction to one. Invokes the callback only
     * when the displayed value changes. A completed transition aligns endpoints
     * with the target; a clean display returns immediately.
     *
     * @param delta finite elapsed seconds since the previous update
     */
    @Override
    public void update(float delta) {
        if (displayedProgress == progress)
            return;

        float previousDisplayedProgress = displayedProgress;

        if (animationDuration <= 0f) {
            displayedProgress = progress;
            animationStartProgress = progress;
            animationTargetProgress = progress;
            animationElapsed = 0f;
        } else {
            animationElapsed = Math.min(animationDuration, animationElapsed + Math.max(0f, delta));
            float alpha = MathUtils.clamp(animationElapsed / animationDuration, 0f, 1f);
            displayedProgress = MathUtils.lerp(animationStartProgress, animationTargetProgress, alpha);

            if (alpha >= 1f) {
                displayedProgress = progress;
                animationStartProgress = progress;
                animationTargetProgress = progress;
            }
        }

        if (previousDisplayedProgress != displayedProgress && progressAction != null)
            progressAction.perform(this);
    }

    /**
     * Routes painting through the root's mixed-renderer dispatch and prepared state.
     *
     * @param batch active texture batch used by the UI traversal
     */
    @Override
    public void draw(TextureBatch batch) {
        render(batch);
    }

    /**
     * Applies non-null theme colors, font settings, dimensions, and duration before
     * base layout. Theme duration assignment does not use the public setter's
     * immediate snap; the next update resolves a zero-duration transition.
     */
    @Override
    protected void applyLayout() {
        ResolvedStyle style = getStyle();

        if (style != null) {
            Color resolvedBackgroundColor = style.get(BACKGROUND_COLOR_KEY);
            Color resolvedForegroundColor = style.get(FOREGROUND_COLOR_KEY);
            Color resolvedBorderColor = style.get(BORDER_COLOR_KEY);
            Color resolvedTextColor = style.get(TEXT_COLOR_KEY);

            String resolvedFontName = style.get(FONT_NAME_KEY);
            Float resolvedFontSize = style.get(FONT_SIZE_KEY);
            Float resolvedCornerRadius = style.get(CORNER_RADIUS_KEY);
            Float resolvedBorderWidth = style.get(BORDER_WIDTH_KEY);
            Float resolvedTextPaddingX = style.get(TEXT_PADDING_X_KEY);
            Float resolvedAnimationDuration = style.get(ANIMATION_DURATION_KEY);

            if (resolvedBackgroundColor != null) backgroundColor = resolvedBackgroundColor;
            if (resolvedForegroundColor != null) foregroundColor = resolvedForegroundColor;
            if (resolvedBorderColor != null) borderColor = resolvedBorderColor;
            if (resolvedTextColor != null) textColor = resolvedTextColor;

            if (resolvedFontName != null && !resolvedFontName.isBlank()) fontName = resolvedFontName;
            if (resolvedFontSize != null) fontSize = Math.max(1f, resolvedFontSize);
            if (resolvedCornerRadius != null) cornerRadius = Math.max(0f, resolvedCornerRadius);
            if (resolvedBorderWidth != null) borderWidth = Math.max(0f, resolvedBorderWidth);
            if (resolvedTextPaddingX != null) textPaddingX = Math.max(0f, resolvedTextPaddingX);
            if (resolvedAnimationDuration != null) animationDuration = Math.max(0f, resolvedAnimationDuration);
        }

        super.applyLayout();
    }

    /**
     * Paints background, border-inset fill, optional border, and optional percentage
     * text using the displayed fraction. Skips invisible nodes and a zero handle.
     * The root supplies the active frame, transforms, and clipping; this leaf does
     * not traverse children or manage NanoVG lifetime.
     *
     * @param vg borrowed active NanoVG context, or zero to skip
     */
    @Override
    public void draw(long vg) {
        if (!isVisible() || vg == 0L) return;

        float x = getAbsoluteX();
        float y = getAbsoluteY();
        float width = getWidth();
        float height = getHeight();
        float percentage = getPercentage();

        nvgBeginPath(vg);
        nvgFillColor(vg, NanoUtility.color1(backgroundColor));
        nvgRoundedRect(vg, x, y, width, height, cornerRadius);
        nvgFill(vg);

        if (percentage > 0f) {
            if (vertical) {
                float filledHeight = height * percentage;
                float filledY = y + (height - filledHeight);
                float innerWidth = Math.max(0f, width - borderWidth * 2f);
                float innerHeight = Math.max(0f, filledHeight - borderWidth * 2f);

                if (innerWidth > 0f && innerHeight > 0f) {
                    nvgBeginPath(vg);
                    nvgFillColor(vg, NanoUtility.color1(foregroundColor));
                    nvgRoundedRect(vg, x + borderWidth, filledY + borderWidth, innerWidth, innerHeight, cornerRadius);
                    nvgFill(vg);
                }
            } else {
                float filledWidth = width * percentage;
                float innerWidth = Math.max(0f, filledWidth - borderWidth * 2f);
                float innerHeight = Math.max(0f, height - borderWidth * 2f);

                if (innerWidth > 0f && innerHeight > 0f) {
                    nvgBeginPath(vg);
                    nvgFillColor(vg, NanoUtility.color1(foregroundColor));
                    nvgRoundedRect(vg, x + borderWidth, y + borderWidth, innerWidth, innerHeight, cornerRadius);
                    nvgFill(vg);
                }
            }
        }

        if (borderWidth > 0f) {
            nvgBeginPath(vg);
            nvgStrokeWidth(vg, borderWidth);
            nvgStrokeColor(vg, NanoUtility.color1(borderColor));
            nvgRoundedRect(vg, x + borderWidth * 0.5f, y + borderWidth * 0.5f, width - borderWidth, height - borderWidth, Math.max(0f, cornerRadius - borderWidth * 0.5f));
            nvgStroke(vg);
        }

        if (displayPercentage) {
            String text = String.format("%.2f%%", percentage * 100f);
            float textWidth = NanoUtility.measureTextWidth(vg, fontName, fontSize, text);
            float textY = NanoUtility.getTextCenterY(vg, fontName, fontSize, y, height);

            float textX = x + (width - textWidth) * 0.5f;
            textX = Math.max(x + textPaddingX, textX);
            textX = Math.min(x + width - textPaddingX - textWidth, textX);

            nvgFontSize(vg, fontSize);
            nvgFontFace(vg, fontName);
            nvgTextAlign(vg, NVG_ALIGN_LEFT | NVG_ALIGN_BASELINE);
            nvgFillColor(vg, NanoUtility.color1(textColor));
            nvgText(vg, textX, textY, text);
        }
    }

    /**
     * Normalizes displayed progress against the stored endpoints and clamps the
     * result to [0, 1]. A zero-width range explicitly returns zero; endpoints and
     * values are otherwise assumed finite and ordered.
     *
     * @return normalized displayed fraction
     */
    private float getPercentage() {
        float range = max - min;
        if (range == 0f) return 0f;
        return MathUtils.clamp((displayedProgress - min) / range, 0f, 1f);
    }
}
