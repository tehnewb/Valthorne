package valthorne.ui.nodes;

import valthorne.Keyboard;
import valthorne.event.events.*;
import valthorne.graphics.Drawable;
import valthorne.graphics.texture.TextureBatch;
import valthorne.math.MathUtils;
import valthorne.math.Vector2f;
import valthorne.ui.NodeAction;
import valthorne.ui.UIRoot;
import valthorne.ui.theme.ResolvedStyle;
import valthorne.ui.theme.StyleKey;
import valthorne.viewport.Viewport;

/**
 * <p>
 * {@code Slider} is an interactive UI control used to select a numeric value within
 * a configurable range. It supports mouse dragging, mouse wheel adjustment,
 * keyboard adjustment, horizontal and vertical orientation, optional snapping
 * through step size, and theme-driven rendering for the track, fill, and thumb.
 * </p>
 *
 * <p>
 * This control is built on top of {@link Panel}, which means it participates in
 * the standard Valthorne UI node lifecycle, theming system, layout system, and
 * rendering pipeline. The slider stores a minimum value, maximum value, current
 * value, and an optional step size that determines how values are snapped during
 * changes.
 * </p>
 *
 * <p>
 * Rendering is entirely style-driven. The slider resolves:
 * </p>
 *
 * <ul>
 *     <li>a track drawable used as the full background of the slider path</li>
 *     <li>a fill drawable used to show the selected portion of the slider</li>
 *     <li>a thumb drawable used for the draggable handle</li>
 *     <li>track and thumb sizing values from style keys</li>
 *     <li>an optional action from style when no explicit action is assigned</li>
 * </ul>
 *
 * <p>
 * Interaction behavior includes:
 * </p>
 *
 * <ul>
 *     <li>mouse press begins dragging</li>
 *     <li>mouse drag moves the value along the track</li>
 *     <li>mouse release stops dragging</li>
 *     <li>mouse scroll increments or decrements the value</li>
 *     <li>arrow keys, Home, and End adjust the value from the keyboard</li>
 * </ul>
 *
 * <p>
 * The slider can operate either horizontally or vertically. In horizontal mode,
 * the value increases from left to right. In vertical mode, the value increases
 * from bottom to top based on the current implementation of thumb position and
 * drag delta handling.
 * </p>
 *
 * <p>
 * A slider may optionally perform a {@link NodeAction} whenever its value changes.
 * The action resolution order is:
 * </p>
 *
 * <ol>
 *     <li>use the explicitly assigned action if one exists</li>
 *     <li>otherwise resolve {@link #ACTION_KEY} from the current style</li>
 * </ol>
 *
 * <h2>Example Usage</h2>
 *
 * <pre>{@code
 * Slider slider = new Slider(0f, 100f, 25f);
 *
 * slider.getLayout()
 *       .width(220)
 *       .height(24);
 *
 * slider.stepSize(5f)
 *       .horizontal(true)
 *       .action(s -> {
 *           System.out.println("Slider value: " + s.getValue());
 *       });
 *
 * slider.increment();
 * slider.decrement();
 * slider.percent(0.5f);
 *
 * float value = slider.getValue();
 * float percent = slider.getPercent();
 * boolean dragging = slider.isDragging();
 *
 * slider.update(delta);
 * slider.draw(batch);
 * }</pre>
 *
 * <p>
 * This example demonstrates the complete use of the class: construction, layout,
 * range management, stepping, orientation, action binding, querying state,
 * update, and draw.
 * </p>
 *
 * @author Albert Beaupre
 * @since March 13th, 2026
 */
public class Slider extends Panel {

    /**
     * Style key used to resolve the slider track drawable.
     */
    public static final StyleKey<Drawable> TRACK_KEY = StyleKey.of("track", Drawable.class);

    /**
     * Style key used to resolve the slider fill drawable.
     */
    public static final StyleKey<Drawable> FILL_KEY = StyleKey.of("fill", Drawable.class);

    /**
     * Style key used to resolve the slider thumb drawable.
     */
    public static final StyleKey<Drawable> THUMB_KEY = StyleKey.of("thumb", Drawable.class);

    /**
     * Style key used to resolve the slider track thickness.
     */
    public static final StyleKey<Float> TRACK_HEIGHT_KEY = StyleKey.of("trackHeight", Float.class, 8f);

    /**
     * Style key used to resolve the slider thumb width.
     */
    public static final StyleKey<Float> THUMB_WIDTH_KEY = StyleKey.of("thumbWidth", Float.class, 6f);

    /**
     * Style key used to resolve the slider thumb height.
     */
    public static final StyleKey<Float> THUMB_HEIGHT_KEY = StyleKey.of("thumbHeight", Float.class, 18f);

    /**
     * Style key used to resolve the vertical thumb offset in horizontal mode.
     */
    public static final StyleKey<Float> THUMB_OFFSET_Y_KEY = StyleKey.of("thumbOffsetY", Float.class, 0f);

    /**
     * Style key used to resolve a fallback slider action when no explicit action is assigned.
     */
    public static final StyleKey<NodeAction<Slider>> ACTION_KEY = StyleKey.of("action", (Class<NodeAction<Slider>>) (Class<?>) NodeAction.class);

    private NodeAction<Slider> action; // Explicit action performed when the slider value changes

    private float min; // Minimum allowed value
    private float max; // Maximum allowed value
    private float value; // Current value
    private float stepSize; // Optional step size used to snap values

    private float trackHeight = 8f; // Thickness of the track
    private float thumbWidth = 18f; // Width of the thumb
    private float thumbHeight = 18f; // Height of the thumb
    private float thumbOffsetY; // Vertical offset applied to the thumb in horizontal mode

    private boolean dragging; // Whether the slider is currently being dragged
    private boolean vertical; // Whether the slider is vertical instead of horizontal
    private float mouseStartX; // Previous mouse X position used during dragging
    private float mouseStartY; // Previous mouse Y position used during dragging

    /**
     * <p>
     * Creates a new slider with a default range of {@code 0} to {@code 1}
     * and an initial value of {@code 0}.
     * </p>
     */
    public Slider() {
        this(0f, 1f, 0f);
    }

    /**
     * <p>
     * Creates a new slider with the supplied range and initial value.
     * </p>
     *
     * <p>
     * No explicit action is assigned.
     * </p>
     *
     * @param min the minimum value
     * @param max the maximum value
     * @param value the initial value
     */
    public Slider(float min, float max, float value) {
        this(min, max, value, null);
    }

    /**
     * <p>
     * Creates a new slider with the supplied range, initial value, and action.
     * </p>
     *
     * <p>
     * The maximum is clamped so it is never lower than the minimum. The initial
     * value is clamped into the valid range. The slider is configured as clickable,
     * focusable, draggable-capable, and scrollable. A default layout size is also set.
     * </p>
     *
     * @param min the minimum value
     * @param max the maximum value
     * @param value the initial value
     * @param action the explicit action to perform when the value changes
     */
    public Slider(float min, float max, float value, NodeAction<Slider> action) {
        this.action = action;
        this.min = min;
        this.max = Math.max(min, max);
        this.value = MathUtils.clamp(value, min, this.max);

        setBit(CLICKABLE_BIT, true);
        setBit(FOCUSABLE_BIT, true);
        setBit(DRAGGING_BIT, false);
        setBit(SCROLLABLE_BIT, true);

        getLayout().width(160).height(Math.max(trackHeight, thumbHeight));
    }

    /**
     * <p>
     * Assigns an explicit action to this slider.
     * </p>
     *
     * @param action the action to perform when the value changes
     * @return this slider
     */
    public Slider action(NodeAction<Slider> action) {
        this.action = action;
        return this;
    }

    /**
     * <p>
     * Returns the explicitly assigned action.
     * </p>
     *
     * @return the explicit action, or {@code null} if none is assigned
     */
    public NodeAction<Slider> getAction() {
        return action;
    }

    /**
     * <p>
     * Returns the minimum allowed value.
     * </p>
     *
     * @return the minimum value
     */
    public float getMin() {
        return min;
    }

    /**
     * <p>
     * Sets the minimum allowed value.
     * </p>
     *
     * <p>
     * If the current maximum becomes invalid, it is raised to match the new minimum.
     * The current slider value is then clamped into the adjusted range.
     * </p>
     *
     * @param min the new minimum value
     * @return this slider
     */
    public Slider min(float min) {
        this.min = min;
        if (this.max < min)
            this.max = min;
        this.value = MathUtils.clamp(this.value, this.min, this.max);
        return this;
    }

    /**
     * <p>
     * Returns the maximum allowed value.
     * </p>
     *
     * @return the maximum value
     */
    public float getMax() {
        return max;
    }

    /**
     * <p>
     * Sets the maximum allowed value.
     * </p>
     *
     * <p>
     * The maximum is never allowed to fall below the current minimum. The current
     * value is clamped into the resulting range.
     * </p>
     *
     * @param max the new maximum value
     * @return this slider
     */
    public Slider max(float max) {
        this.max = Math.max(this.min, max);
        this.value = MathUtils.clamp(this.value, this.min, this.max);
        return this;
    }

    /**
     * <p>
     * Sets both the minimum and maximum values at once.
     * </p>
     *
     * <p>
     * The maximum is forced to be at least as large as the minimum, and the current
     * value is clamped into the new range.
     * </p>
     *
     * @param min the new minimum value
     * @param max the new maximum value
     * @return this slider
     */
    public Slider range(float min, float max) {
        this.min = min;
        this.max = Math.max(min, max);
        this.value = MathUtils.clamp(this.value, this.min, this.max);
        return this;
    }

    /**
     * <p>
     * Returns the current slider value.
     * </p>
     *
     * @return the current value
     */
    public float getValue() {
        return value;
    }

    /**
     * <p>
     * Sets the current slider value.
     * </p>
     *
     * <p>
     * The value is clamped into the valid range and then passed through
     * {@link #snap(float)} so step-based snapping is applied when enabled.
     * </p>
     *
     * @param value the new value
     * @return this slider
     */
    public Slider value(float value) {
        this.value = snap(MathUtils.clamp(value, this.min, this.max));
        return this;
    }

    /**
     * <p>
     * Returns the configured step size.
     * </p>
     *
     * @return the step size, or {@code 0} when free movement is enabled
     */
    public float getStepSize() {
        return stepSize;
    }

    /**
     * <p>
     * Sets the step size used for snapping.
     * </p>
     *
     * <p>
     * Negative values are treated as {@code 0}. After the new step size is applied,
     * the current value is re-snapped to ensure consistency.
     * </p>
     *
     * @param stepSize the new step size
     * @return this slider
     */
    public Slider stepSize(float stepSize) {
        this.stepSize = Math.max(0f, stepSize);
        this.value = snap(this.value);
        return this;
    }

    /**
     * <p>
     * Increments the slider value by one logical step.
     * </p>
     *
     * <p>
     * If a step size is configured, that step is used. Otherwise, a fallback increment
     * equal to one hundredth of the range is used.
     * </p>
     *
     * @return this slider
     */
    public Slider increment() {
        float amount = stepSize > 0f ? stepSize : Math.max((max - min) / 100f, 0.000001f);
        value(value + amount);
        return this;
    }

    /**
     * <p>
     * Decrements the slider value by one logical step.
     * </p>
     *
     * <p>
     * If a step size is configured, that step is used. Otherwise, a fallback decrement
     * equal to one hundredth of the range is used.
     * </p>
     *
     * @return this slider
     */
    public Slider decrement() {
        float amount = stepSize > 0f ? stepSize : Math.max((max - min) / 100f, 0.000001f);
        value(value - amount);
        return this;
    }

    /**
     * <p>
     * Returns whether the slider is currently vertical.
     * </p>
     *
     * @return {@code true} if vertical
     */
    public boolean isVertical() {
        return vertical;
    }

    /**
     * <p>
     * Sets whether the slider should use vertical orientation.
     * </p>
     *
     * <p>
     * When switching to vertical mode, default auto layout sizing is adjusted so the
     * width matches the larger of the track height and thumb width, while height
     * defaults to {@code 160}. In horizontal mode, auto sizing defaults back to a
     * width of {@code 160} and a height based on the larger of the track height and
     * thumb height.
     * </p>
     *
     * @param vertical whether the slider should be vertical
     * @return this slider
     */
    public Slider vertical(boolean vertical) {
        this.vertical = vertical;

        if (vertical) {
            if (getLayout().getWidth().isAuto())
                getLayout().width(Math.max(trackHeight, thumbWidth));

            if (getLayout().getHeight().isAuto())
                getLayout().height(160);
        } else {
            if (getLayout().getWidth().isAuto())
                getLayout().width(160);

            if (getLayout().getHeight().isAuto())
                getLayout().height(Math.max(trackHeight, thumbHeight));
        }

        markLayoutDirty();
        return this;
    }

    /**
     * <p>
     * Sets whether the slider should use horizontal orientation.
     * </p>
     *
     * @param horizontal whether the slider should be horizontal
     * @return this slider
     */
    public Slider horizontal(boolean horizontal) {
        return vertical(!horizontal);
    }

    /**
     * <p>
     * Returns the current track thickness.
     * </p>
     *
     * @return the track height value
     */
    public float getTrackHeight() {
        return trackHeight;
    }

    /**
     * <p>
     * Sets the track thickness.
     * </p>
     *
     * <p>
     * Negative values are clamped to {@code 0}. Layout is then marked dirty.
     * </p>
     *
     * @param trackHeight the new track thickness
     * @return this slider
     */
    public Slider trackHeight(float trackHeight) {
        this.trackHeight = Math.max(0f, trackHeight);
        markLayoutDirty();
        return this;
    }

    /**
     * <p>
     * Returns the current thumb width.
     * </p>
     *
     * @return the thumb width
     */
    public float getThumbWidth() {
        return thumbWidth;
    }

    /**
     * <p>
     * Sets the thumb width.
     * </p>
     *
     * <p>
     * Negative values are clamped to {@code 0}. Layout is then marked dirty.
     * </p>
     *
     * @param thumbWidth the new thumb width
     * @return this slider
     */
    public Slider thumbWidth(float thumbWidth) {
        this.thumbWidth = Math.max(0f, thumbWidth);
        markLayoutDirty();
        return this;
    }

    /**
     * <p>
     * Returns the current thumb height.
     * </p>
     *
     * @return the thumb height
     */
    public float getThumbHeight() {
        return thumbHeight;
    }

    /**
     * <p>
     * Sets the thumb height.
     * </p>
     *
     * <p>
     * Negative values are clamped to {@code 0}. Layout is then marked dirty.
     * </p>
     *
     * @param thumbHeight the new thumb height
     * @return this slider
     */
    public Slider thumbHeight(float thumbHeight) {
        this.thumbHeight = Math.max(0f, thumbHeight);
        markLayoutDirty();
        return this;
    }

    /**
     * <p>
     * Sets both thumb width and thumb height at once.
     * </p>
     *
     * <p>
     * Negative values are clamped to {@code 0}. Layout is then marked dirty.
     * </p>
     *
     * @param width the new thumb width
     * @param height the new thumb height
     * @return this slider
     */
    public Slider thumbSize(float width, float height) {
        this.thumbWidth = Math.max(0f, width);
        this.thumbHeight = Math.max(0f, height);
        markLayoutDirty();
        return this;
    }

    /**
     * <p>
     * Returns the current vertical thumb offset used in horizontal mode.
     * </p>
     *
     * @return the thumb Y offset
     */
    public float getThumbOffsetY() {
        return thumbOffsetY;
    }

    /**
     * <p>
     * Sets the vertical thumb offset used in horizontal mode.
     * </p>
     *
     * @param thumbOffsetY the new thumb Y offset
     * @return this slider
     */
    public Slider thumbOffsetY(float thumbOffsetY) {
        this.thumbOffsetY = thumbOffsetY;
        return this;
    }

    /**
     * <p>
     * Returns the current value as a normalized percentage in the range {@code [0, 1]}.
     * </p>
     *
     * <p>
     * If the slider range is zero or negative, the method returns {@code 0}.
     * </p>
     *
     * @return the normalized percentage
     */
    public float getPercent() {
        float range = max - min;
        if (range <= 0f)
            return 0f;
        return (value - min) / range;
    }

    /**
     * <p>
     * Sets the slider value using a normalized percentage in the range {@code [0, 1]}.
     * </p>
     *
     * @param percent the normalized percentage
     * @return this slider
     */
    public Slider percent(float percent) {
        value(min + MathUtils.clamp(percent, 0f, 1f) * (max - min));
        return this;
    }

    /**
     * <p>
     * Returns whether the slider is currently being dragged.
     * </p>
     *
     * @return {@code true} if dragging is active
     */
    public boolean isDragging() {
        return dragging;
    }

    /**
     * <p>
     * Returns the X position of the rendered track.
     * </p>
     *
     * <p>
     * In vertical mode, the track is centered horizontally within the full slider
     * width. In horizontal mode, the track begins at the slider's render X.
     * </p>
     *
     * @return the track X position
     */
    public float getTrackX() {
        if (vertical)
            return getRenderX() + (getWidth() - trackHeight) * 0.5f;
        return getRenderX();
    }

    /**
     * <p>
     * Returns the Y position of the rendered track.
     * </p>
     *
     * <p>
     * In vertical mode, the track begins at the slider's render Y. In horizontal
     * mode, the track is vertically centered within the slider bounds.
     * </p>
     *
     * @return the track Y position
     */
    public float getTrackY() {
        if (vertical)
            return getRenderY();
        return getRenderY() + (getHeight() - trackHeight) * 0.5f;
    }

    /**
     * <p>
     * Returns the rendered track width.
     * </p>
     *
     * <p>
     * In vertical mode, this is the track thickness. In horizontal mode, it is the
     * full slider width.
     * </p>
     *
     * @return the track width
     */
    public float getTrackWidth() {
        if (vertical)
            return trackHeight;
        return getWidth();
    }

    /**
     * <p>
     * Returns the rendered track height.
     * </p>
     *
     * <p>
     * In vertical mode, this is the full slider height. In horizontal mode, it is
     * the configured track thickness.
     * </p>
     *
     * @return the track height
     */
    public float getTrackActualHeight() {
        if (vertical)
            return getHeight();
        return trackHeight;
    }

    /**
     * <p>
     * Returns the current X position of the thumb.
     * </p>
     *
     * <p>
     * In vertical mode, the thumb is horizontally centered. In horizontal mode,
     * the thumb position is computed from the current value percentage and the
     * available track width minus thumb width.
     * </p>
     *
     * @return the thumb X position
     */
    public float getThumbX() {
        if (vertical)
            return getRenderX() + (getWidth() - thumbWidth) * 0.5f;

        float usableWidth = Math.max(0f, getTrackWidth() - thumbWidth);
        return getTrackX() + getPercent() * usableWidth;
    }

    /**
     * <p>
     * Returns the current Y position of the thumb.
     * </p>
     *
     * <p>
     * In vertical mode, the thumb position is computed from the current value
     * percentage and the available track height minus thumb height. In horizontal
     * mode, the thumb is vertically centered with the configured offset applied.
     * </p>
     *
     * @return the thumb Y position
     */
    public float getThumbY() {
        if (vertical) {
            float usableHeight = Math.max(0f, getTrackActualHeight() - thumbHeight);
            return getTrackY() + getPercent() * usableHeight;
        }

        return getRenderY() + (getHeight() - thumbHeight) * 0.5f + thumbOffsetY;
    }

    /**
     * <p>
     * Returns the width of the filled portion of the slider in horizontal mode.
     * </p>
     *
     * <p>
     * In vertical mode, this simply returns the track width.
     * </p>
     *
     * @return the fill width
     */
    public float getFillWidth() {
        if (vertical)
            return getTrackWidth();
        return getThumbX() - getTrackX() + thumbWidth * 0.5f;
    }

    /**
     * <p>
     * Returns the height of the filled portion of the slider in vertical mode.
     * </p>
     *
     * <p>
     * In horizontal mode, this simply returns the track height.
     * </p>
     *
     * @return the fill height
     */
    public float getFillHeight() {
        if (vertical)
            return getThumbY() - getTrackY() + thumbHeight * 0.5f;
        return getTrackActualHeight();
    }

    /**
     * <p>
     * Handles the start of slider dragging.
     * </p>
     *
     * <p>
     * Dragging state and pressed state are enabled. The starting mouse position is
     * recorded either in raw screen space or viewport world space depending on
     * whether a viewport is active.
     * </p>
     *
     * @param event the mouse press event
     */
    @Override
    public void onMousePress(MousePressEvent event) {
        dragging = true;
        setDragging(true);
        setPressed(true);

        Viewport viewport = getViewport();
        if (viewport != null) {
            Vector2f world = viewport.screenToWorld(event.getX(), event.getY());
            if (world == null)
                return;

            mouseStartX = world.getX();
            mouseStartY = world.getY();
        } else {
            mouseStartX = event.getX();
            mouseStartY = event.getY();
        }
    }

    /**
     * <p>
     * Handles slider dragging while the mouse moves.
     * </p>
     *
     * <p>
     * If dragging is not active, the method returns immediately. Otherwise the mouse
     * movement delta is computed either in world space or screen space. The slider
     * value is then changed proportionally along the relevant axis based on track
     * size and range span. If the value changes, the resolved action is fired.
     * </p>
     *
     * @param event the mouse drag event
     */
    @Override
    public void onMouseDrag(MouseDragEvent event) {
        if (!dragging)
            return;

        float diffX = event.getDeltaX();
        float diffY = event.getDeltaY();

        float previous = value;

        if (vertical) {
            float usableHeight = Math.max(0f, getTrackActualHeight() - thumbHeight);
            if (usableHeight <= 0f)
                return;

            float valueDelta = diffY * ((max - min) / usableHeight);
            value(value + valueDelta);
        } else {
            float usableWidth = Math.max(0f, getTrackWidth() - thumbWidth);
            if (usableWidth <= 0f)
                return;

            float valueDelta = diffX * ((max - min) / usableWidth);
            value(value + valueDelta);
        }

        if (previous != value)
            fireAction();
    }

    /**
     * <p>
     * Handles mouse wheel adjustment for this slider.
     * </p>
     *
     * <p>
     * The value is increased or decreased by one logical step. In vertical mode,
     * the Y scroll offset is used. In horizontal mode, Y is preferred, with X used
     * as a fallback when Y is zero. If the value changes, the resolved action is fired.
     * </p>
     *
     * @param event the mouse scroll event
     */
    @Override
    public void onMouseScroll(MouseScrollEvent event) {
        float previous = value;

        float amount = stepSize > 0f ? stepSize : Math.max((max - min) / 100f, 0.000001f);
        if (vertical) {
            value(value + event.yOffset() * amount);
        } else {
            float offset = event.yOffset() != 0 ? event.yOffset() : event.xOffset();
            value(value + offset * amount);
        }

        if (previous != value)
            fireAction();
    }

    /**
     * <p>
     * Handles the end of slider dragging.
     * </p>
     *
     * <p>
     * Dragging state and pressed state are cleared.
     * </p>
     *
     * @param event the mouse release event
     */
    @Override
    public void onMouseRelease(MouseReleaseEvent event) {
        dragging = false;
        setDragging(false);
        setPressed(false);
    }

    /**
     * <p>
     * Handles keyboard interaction for this slider.
     * </p>
     *
     * <p>
     * If the slider is disabled, no action is taken. Otherwise arrow keys adjust the
     * value according to orientation, and Home/End jump directly to the minimum or
     * maximum value. If the value changes, the resolved action is fired.
     * </p>
     *
     * @param event the key press event
     */
    @Override
    public void onKeyPress(KeyPressEvent event) {
        if (isDisabled())
            return;

        float previous = value;

        if (vertical) {
            if (event.getKey() == Keyboard.DOWN) {
                decrement();
            } else if (event.getKey() == Keyboard.UP) {
                increment();
            } else if (event.getKey() == Keyboard.HOME) {
                value(min);
            } else if (event.getKey() == Keyboard.END) {
                value(max);
            }
        } else {
            if (event.getKey() == Keyboard.LEFT) {
                decrement();
            } else if (event.getKey() == Keyboard.RIGHT) {
                increment();
            } else if (event.getKey() == Keyboard.HOME) {
                value(min);
            } else if (event.getKey() == Keyboard.END) {
                value(max);
            }
        }

        if (previous != value)
            fireAction();
    }

    /**
     * <p>
     * Applies layout and style-driven visual configuration for this slider.
     * </p>
     *
     * <p>
     * The current style is resolved and used to update the track height, thumb width,
     * thumb height, and thumb offset values when present. Auto layout defaults are
     * then applied based on the current orientation. Finally, the superclass layout
     * application continues.
     * </p>
     */
    @Override
    protected void applyLayout() {
        ResolvedStyle style = getStyle();

        if (style != null) {
            Float resolvedTrackHeight = style.get(TRACK_HEIGHT_KEY);
            Float resolvedThumbWidth = style.get(THUMB_WIDTH_KEY);
            Float resolvedThumbHeight = style.get(THUMB_HEIGHT_KEY);
            Float resolvedThumbOffsetY = style.get(THUMB_OFFSET_Y_KEY);

            if (resolvedTrackHeight != null)
                trackHeight = Math.max(0f, resolvedTrackHeight);
            if (resolvedThumbWidth != null)
                thumbWidth = Math.max(0f, resolvedThumbWidth);
            if (resolvedThumbHeight != null)
                thumbHeight = Math.max(0f, resolvedThumbHeight);
            if (resolvedThumbOffsetY != null)
                thumbOffsetY = resolvedThumbOffsetY;
        }

        if (vertical) {
            if (getLayout().getWidth().isAuto())
                getLayout().width(Math.max(trackHeight, thumbWidth));

            if (getLayout().getHeight().isAuto())
                getLayout().height(160);
        } else {
            if (getLayout().getHeight().isAuto())
                getLayout().height(Math.max(trackHeight, thumbHeight));
        }

        super.applyLayout();
    }

    /**
     * <p>
     * Draws the slider using the provided batch.
     * </p>
     *
     * <p>
     * The current style is resolved for the track, fill, and thumb drawables.
     * The slider then computes current geometry for each visual component and
     * renders them in order: track first, fill second, and thumb last.
     * </p>
     *
     * @param batch the texture batch used for rendering
     */
    @Override
    public void draw(TextureBatch batch) {
        ResolvedStyle style = getStyle();
        if (style == null)
            return;

        Drawable track = style.get(TRACK_KEY);
        Drawable fill = style.get(FILL_KEY);
        Drawable thumb = style.get(THUMB_KEY);

        float trackX = getTrackX();
        float trackY = getTrackY();
        float trackW = getTrackWidth();
        float trackH = getTrackActualHeight();
        float fillW = getFillWidth();
        float fillH = getFillHeight();
        float thumbX = getThumbX();
        float thumbY = getThumbY();

        if (track != null)
            track.draw(batch, trackX, trackY, trackW, trackH);

        if (fill != null) {
            if (vertical) {
                if (fillH > 0f)
                    fill.draw(batch, trackX, trackY, trackW, fillH);
            } else {
                if (fillW > 0f)
                    fill.draw(batch, trackX, trackY, fillW, trackH);
            }
        }

        if (thumb != null)
            thumb.draw(batch, thumbX, thumbY, thumbWidth, thumbHeight);
    }

    /**
     * <p>
     * Returns the viewport currently associated with this slider's root, if any.
     * </p>
     *
     * @return the active viewport, or {@code null} if none exists
     */
    private Viewport getViewport() {
        UIRoot root = getRoot();
        if (root == null)
            return null;
        return root.getViewport();
    }

    /**
     * <p>
     * Resolves and fires the slider action.
     * </p>
     *
     * <p>
     * The explicit action is used first if present. Otherwise the slider attempts
     * to resolve {@link #ACTION_KEY} from the current style. If a resolved action
     * exists, it is performed with this slider as the target.
     * </p>
     */
    private void fireAction() {
        NodeAction<Slider> resolvedAction = action;
        if (resolvedAction == null) {
            ResolvedStyle style = getStyle();
            if (style != null)
                resolvedAction = style.get(ACTION_KEY);
        }

        if (resolvedAction != null)
            resolvedAction.perform(this);
    }

    /**
     * <p>
     * Applies step-based snapping to the supplied value.
     * </p>
     *
     * <p>
     * If step size is zero or negative, the value is returned unchanged. Otherwise
     * the value is rounded to the nearest step offset from the minimum and then
     * clamped back into the valid range.
     * </p>
     *
     * @param value the input value to snap
     * @return the snapped value
     */
    private float snap(float value) {
        if (stepSize <= 0f)
            return value;

        float steps = Math.round((value - min) / stepSize);
        float snapped = min + steps * stepSize;
        return MathUtils.clamp(snapped, min, max);
    }
}