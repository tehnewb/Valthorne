package valthorne.ui.elements;

import valthorne.Keyboard;
import valthorne.Mouse;
import valthorne.event.events.KeyPressEvent;
import valthorne.event.events.MouseDragEvent;
import valthorne.event.events.MousePressEvent;
import valthorne.event.events.MouseReleaseEvent;
import valthorne.graphics.Drawable;
import valthorne.graphics.texture.TextureBatch;
import valthorne.math.MathUtils;
import valthorne.math.Vector2f;
import valthorne.ui.Element;
import valthorne.ui.UIAction;
import valthorne.ui.styles.SliderStyle;
import valthorne.viewport.Viewport;

/**
 * <h1>Slider</h1>
 *
 * <p>
 * {@code Slider} is an interactive UI element that lets the user choose a numeric value from a range
 * by dragging a thumb along a horizontal track. It supports mouse dragging, keyboard adjustment,
 * snapping to step sizes, custom drawables for multiple visual states, and an optional action callback
 * that fires whenever the value changes through user interaction.
 * </p>
 *
 * <p>
 * The slider is made of three visual parts:
 * </p>
 * <ul>
 *     <li>A <b>track</b>, which represents the entire selectable range.</li>
 *     <li>A <b>fill</b>, which represents the portion of the range from minimum to current value.</li>
 *     <li>A <b>thumb</b>, which represents the draggable handle the user interacts with.</li>
 * </ul>
 *
 * <h2>Range behavior</h2>
 * <p>
 * The slider stores a minimum value, maximum value, and current value. The current value is always
 * clamped into the valid range. If a step size is configured, values are snapped to the nearest valid
 * step from the minimum.
 * </p>
 *
 * <h2>Input behavior</h2>
 * <p>
 * Mouse presses begin dragging immediately and update the value from the current pointer position.
 * Mouse drags continue updating the value while the slider remains in a dragging state. Keyboard input
 * supports left and right arrow adjustment as well as home/end shortcuts for jumping to the minimum
 * and maximum.
 * </p>
 *
 * <h2>State-based styling</h2>
 * <p>
 * The slider resolves different drawables depending on whether it is disabled, pressed, hovered, or
 * focused. This allows the track, fill, and thumb to change appearance dynamically without requiring
 * the control to manage low-level rendering logic directly.
 * </p>
 *
 * <h2>Coordinate behavior</h2>
 * <p>
 * Pointer interaction is resolved through the UI's viewport when one exists. This ensures that the
 * slider behaves correctly even when the UI is rendered through a scaled or transformed viewport.
 * </p>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * Slider volumeSlider = new Slider(0f, 100f, 50f, slider -> {
 *     System.out.println("Volume: " + slider.getValue());
 * });
 *
 * volumeSlider
 *     .setStepSize(5f)
 *     .setTrackHeight(10f)
 *     .setThumbSize(20f, 20f)
 *     .setPosition(40f, 40f)
 *     .setSize(220f, 24f);
 *
 * volumeSlider.setStyle(SliderStyle.of()
 *     .track(trackDrawable)
 *     .fill(fillDrawable)
 *     .thumb(thumbDrawable)
 *     .hoveredThumb(hoveredThumbDrawable)
 *     .pressedThumb(pressedThumbDrawable));
 * }</pre>
 *
 * @author Albert Beaupre
 * @since March 7th, 2026
 */
public class Slider extends Element {

    private SliderStyle style; // The visual style that supplies drawables for the slider's states and parts.
    private UIAction<Slider> action; // The optional action invoked when the slider value changes through interaction.

    private float min; // The minimum selectable value of the slider range.
    private float max; // The maximum selectable value of the slider range.
    private float value; // The current selected value stored by the slider.
    private float stepSize; // The optional snap interval used to quantize values when non-zero.

    private float trackHeight = 8f; // The rendered height of the slider track.
    private float thumbWidth = 18f; // The rendered width of the slider thumb.
    private float thumbHeight = 18f; // The rendered height of the slider thumb.
    private float thumbOffsetY; // The extra vertical offset applied to the thumb relative to its centered position.

    private boolean dragging; // Whether the user is actively dragging the slider thumb.

    /**
     * Creates a slider with the default range {@code 0..1} and an initial value of {@code 0}.
     *
     * <p>
     * This is a convenience constructor for general-purpose sliders where the exact range will be
     * configured later.
     * </p>
     */
    public Slider() {
        this(0f, 1f, 0f);
    }

    /**
     * Creates a slider with the given range and initial value.
     *
     * <p>
     * This constructor uses a default empty {@link SliderStyle} and no action callback.
     * </p>
     *
     * @param min   the minimum selectable value
     * @param max   the maximum selectable value
     * @param value the initial current value
     */
    public Slider(float min, float max, float value) {
        this(min, max, value, null, new SliderStyle());
    }

    /**
     * Creates a slider with the given range, initial value, and style.
     *
     * <p>
     * This constructor leaves the action callback unset while allowing immediate style customization.
     * </p>
     *
     * @param min   the minimum selectable value
     * @param max   the maximum selectable value
     * @param value the initial current value
     * @param style the visual style to use
     */
    public Slider(float min, float max, float value, SliderStyle style) {
        this(min, max, value, null, style);
    }

    /**
     * Creates a slider with the given range, initial value, and action.
     *
     * <p>
     * This constructor uses a default empty {@link SliderStyle}.
     * </p>
     *
     * @param min    the minimum selectable value
     * @param max    the maximum selectable value
     * @param value  the initial current value
     * @param action the callback invoked when the value changes
     */
    public Slider(float min, float max, float value, UIAction<Slider> action) {
        this(min, max, value, action, new SliderStyle());
    }

    /**
     * Creates a fully configured slider with the given range, value, action, and style.
     *
     * <p>
     * The maximum is normalized so it is never lower than the minimum. The initial value is clamped
     * into the resulting range. The slider is marked focusable so keyboard control can work, and a
     * default size is assigned using the larger of the track and thumb heights.
     * </p>
     *
     * @param min    the minimum selectable value
     * @param max    the maximum selectable value
     * @param value  the initial current value
     * @param action the callback invoked when the value changes
     * @param style  the visual style to use
     */
    public Slider(float min, float max, float value, UIAction<Slider> action, SliderStyle style) {
        this.style = style == null ? new SliderStyle() : style;
        this.action = action;
        this.min = min;
        this.max = Math.max(min, max);
        this.value = MathUtils.clamp(value, min, this.max);
        this.setFocusable(true);
        this.setSize(160f, Math.max(trackHeight, thumbHeight));
    }

    /**
     * Updates the slider each frame.
     *
     * <p>
     * The base slider has no time-based behavior, so this method currently performs no work. It still
     * exists as part of the element lifecycle and allows future expansion without changing the public
     * API.
     * </p>
     *
     * @param delta the elapsed time in seconds since the previous update
     */
    @Override
    public void update(float delta) {
    }

    /**
     * Draws the slider using the currently resolved state drawables.
     *
     * <p>
     * This method resolves the track, fill, and thumb drawables according to the current element state,
     * computes the correct layout rectangles for each part, and renders them in the proper order:
     * track first, then fill, then thumb.
     * </p>
     *
     * @param batch the texture batch used for drawing
     */
    @Override
    public void draw(TextureBatch batch) {
        Drawable track = resolveTrackDrawable();
        Drawable fill = resolveFillDrawable();
        Drawable thumb = resolveThumbDrawable();

        float trackX = getTrackX();
        float trackY = getTrackY();
        float trackW = getTrackWidth();
        float trackH = getTrackHeight();
        float fillW = getFillWidth();
        float thumbX = getThumbX();
        float thumbY = getThumbY();

        if (track != null) {
            track.draw(batch, trackX, trackY, trackW, trackH);
        }

        if (fill != null && fillW > 0f) {
            fill.draw(batch, trackX, trackY, fillW, trackH);
        }

        if (thumb != null) {
            thumb.draw(batch, thumbX, thumbY, thumbWidth, thumbHeight);
        }
    }

    /**
     * Handles mouse press input for the slider.
     *
     * <p>
     * Pressing the slider starts a dragging interaction immediately and updates the value from the
     * pointer position right away. Disabled sliders ignore the event completely.
     * </p>
     *
     * @param event the mouse press event
     */
    @Override
    public void onMousePress(MousePressEvent event) {
        if (isDisabled()) {
            return;
        }

        dragging = true;
        updateValueFromPointer(event.getX(), event.getY());
    }

    /**
     * Handles mouse dragging for the slider.
     *
     * <p>
     * While the slider is actively being dragged, this method continuously converts the current pointer
     * position into a slider value. If the slider is disabled or not currently dragging, the event is
     * ignored.
     * </p>
     *
     * @param event the mouse drag event
     */
    @Override
    public void onMouseDrag(MouseDragEvent event) {
        if (isDisabled() || !dragging) {
            return;
        }

        updateValueFromPointer(Mouse.getX(), Mouse.getY());
    }

    /**
     * Handles mouse release input for the slider.
     *
     * <p>
     * Releasing the mouse ends any active dragging interaction.
     * </p>
     *
     * @param event the mouse release event
     */
    @Override
    public void onMouseRelease(MouseReleaseEvent event) {
        dragging = false;
    }

    /**
     * Handles keyboard control for the slider.
     *
     * <p>
     * The slider responds to:
     * </p>
     * <ul>
     *     <li>Left arrow to decrement</li>
     *     <li>Right arrow to increment</li>
     *     <li>Home to jump to the minimum</li>
     *     <li>End to jump to the maximum</li>
     * </ul>
     *
     * <p>
     * If the value changes as a result of the key press, the configured action is fired.
     * Disabled sliders ignore keyboard input.
     * </p>
     *
     * @param event the key press event
     */
    @Override
    public void onKeyPress(KeyPressEvent event) {
        if (isDisabled()) {
            return;
        }

        float previous = value;

        if (event.getKey() == Keyboard.LEFT) {
            decrement();
        } else if (event.getKey() == Keyboard.RIGHT) {
            increment();
        } else if (event.getKey() == Keyboard.HOME) {
            setValue(min);
        } else if (event.getKey() == Keyboard.END) {
            setValue(max);
        }

        if (previous != value) {
            fireAction();
        }
    }

    /**
     * Updates the disabled state of the slider.
     *
     * <p>
     * When the slider becomes disabled, any active dragging interaction is immediately cancelled so the
     * element cannot remain in a partially interactive state.
     * </p>
     *
     * @param value true to disable the slider, false to enable it
     */
    @Override
    public void setDisabled(boolean value) {
        super.setDisabled(value);
        if (value) {
            dragging = false;
        }
    }

    /**
     * Sets the visual style used by the slider.
     *
     * <p>
     * Passing {@code null} resets the slider to a fresh empty style object rather than leaving it with
     * a null style reference.
     * </p>
     *
     * @param style the new style to apply
     * @return this slider for chaining
     */
    public Slider setStyle(SliderStyle style) {
        this.style = style == null ? new SliderStyle() : style;
        return this;
    }

    /**
     * Returns the current style used by the slider.
     *
     * @return the active slider style
     */
    public SliderStyle getStyle() {
        return style;
    }

    /**
     * Sets the action callback invoked when the slider value changes through interaction.
     *
     * @param action the action to assign, or null to clear it
     * @return this slider for chaining
     */
    public Slider setAction(UIAction<Slider> action) {
        this.action = action;
        return this;
    }

    /**
     * Returns the action currently assigned to the slider.
     *
     * @return the current slider action, or null if none is assigned
     */
    public UIAction<Slider> getAction() {
        return action;
    }

    /**
     * Returns the minimum selectable value of the slider.
     *
     * @return the minimum range value
     */
    public float getMin() {
        return min;
    }

    /**
     * Sets the minimum selectable value of the slider.
     *
     * <p>
     * If the existing maximum is below the new minimum, the maximum is raised to match it. The current
     * value is then clamped into the updated range.
     * </p>
     *
     * @param min the new minimum value
     * @return this slider for chaining
     */
    public Slider setMin(float min) {
        this.min = min;
        if (this.max < min) {
            this.max = min;
        }
        this.value = MathUtils.clamp(this.value, this.min, this.max);
        return this;
    }

    /**
     * Returns the maximum selectable value of the slider.
     *
     * @return the maximum range value
     */
    public float getMax() {
        return max;
    }

    /**
     * Sets the maximum selectable value of the slider.
     *
     * <p>
     * The maximum is normalized so it can never drop below the current minimum. The current value is
     * then clamped into the updated range.
     * </p>
     *
     * @param max the new maximum value
     * @return this slider for chaining
     */
    public Slider setMax(float max) {
        this.max = Math.max(this.min, max);
        this.value = MathUtils.clamp(this.value, this.min, this.max);
        return this;
    }

    /**
     * Sets both the minimum and maximum range values at once.
     *
     * <p>
     * The maximum is normalized so it is never below the minimum. The current value is clamped into
     * the resulting range.
     * </p>
     *
     * @param min the new minimum value
     * @param max the new maximum value
     * @return this slider for chaining
     */
    public Slider setRange(float min, float max) {
        this.min = min;
        this.max = Math.max(min, max);
        this.value = MathUtils.clamp(this.value, this.min, this.max);
        return this;
    }

    /**
     * Returns the current slider value.
     *
     * @return the current selected value
     */
    public float getValue() {
        return value;
    }

    /**
     * Sets the current slider value.
     *
     * <p>
     * The supplied value is first clamped into the valid range and then snapped to the nearest step if
     * stepping is enabled.
     * </p>
     *
     * @param value the new value to assign
     * @return this slider for chaining
     */
    public Slider setValue(float value) {
        this.value = snap(MathUtils.clamp(value, this.min, this.max));
        return this;
    }

    /**
     * Returns the current step size used for snapping.
     *
     * @return the current step size, or zero if stepping is disabled
     */
    public float getStepSize() {
        return stepSize;
    }

    /**
     * Sets the step size used for value snapping.
     *
     * <p>
     * Negative values are treated as zero. After updating the step size, the current value is snapped
     * immediately so it remains consistent with the new stepping configuration.
     * </p>
     *
     * @param stepSize the new step size
     * @return this slider for chaining
     */
    public Slider setStepSize(float stepSize) {
        this.stepSize = Math.max(0f, stepSize);
        this.value = snap(this.value);
        return this;
    }

    /**
     * Increments the slider value by one logical step.
     *
     * <p>
     * If a custom step size is configured, that value is used. Otherwise a small default fraction of
     * the range is used.
     * </p>
     *
     * @return this slider for chaining
     */
    public Slider increment() {
        float amount = stepSize > 0f ? stepSize : Math.max((max - min) / 100f, 0.000001f);
        setValue(value + amount);
        return this;
    }

    /**
     * Decrements the slider value by one logical step.
     *
     * <p>
     * If a custom step size is configured, that value is used. Otherwise a small default fraction of
     * the range is used.
     * </p>
     *
     * @return this slider for chaining
     */
    public Slider decrement() {
        float amount = stepSize > 0f ? stepSize : Math.max((max - min) / 100f, 0.000001f);
        setValue(value - amount);
        return this;
    }

    /**
     * Returns the rendered height of the track.
     *
     * @return the track height
     */
    public float getTrackHeight() {
        return trackHeight;
    }

    /**
     * Sets the rendered height of the track.
     *
     * <p>
     * Negative heights are clamped to zero.
     * </p>
     *
     * @param trackHeight the new track height
     * @return this slider for chaining
     */
    public Slider setTrackHeight(float trackHeight) {
        this.trackHeight = Math.max(0f, trackHeight);
        return this;
    }

    /**
     * Returns the rendered width of the thumb.
     *
     * @return the thumb width
     */
    public float getThumbWidth() {
        return thumbWidth;
    }

    /**
     * Sets the rendered width of the thumb.
     *
     * <p>
     * Negative widths are clamped to zero.
     * </p>
     *
     * @param thumbWidth the new thumb width
     * @return this slider for chaining
     */
    public Slider setThumbWidth(float thumbWidth) {
        this.thumbWidth = Math.max(0f, thumbWidth);
        return this;
    }

    /**
     * Returns the rendered height of the thumb.
     *
     * @return the thumb height
     */
    public float getThumbHeight() {
        return thumbHeight;
    }

    /**
     * Sets the rendered height of the thumb.
     *
     * <p>
     * Negative heights are clamped to zero.
     * </p>
     *
     * @param thumbHeight the new thumb height
     * @return this slider for chaining
     */
    public Slider setThumbHeight(float thumbHeight) {
        this.thumbHeight = Math.max(0f, thumbHeight);
        return this;
    }

    /**
     * Sets both thumb dimensions at once.
     *
     * <p>
     * Negative dimensions are clamped to zero.
     * </p>
     *
     * @param width  the new thumb width
     * @param height the new thumb height
     * @return this slider for chaining
     */
    public Slider setThumbSize(float width, float height) {
        this.thumbWidth = Math.max(0f, width);
        this.thumbHeight = Math.max(0f, height);
        return this;
    }

    /**
     * Returns the extra vertical thumb offset.
     *
     * @return the thumb y offset
     */
    public float getThumbOffsetY() {
        return thumbOffsetY;
    }

    /**
     * Sets the extra vertical offset applied to the thumb.
     *
     * @param thumbOffsetY the new thumb vertical offset
     * @return this slider for chaining
     */
    public Slider setThumbOffsetY(float thumbOffsetY) {
        this.thumbOffsetY = thumbOffsetY;
        return this;
    }

    /**
     * Returns the normalized slider position as a percentage in the range {@code 0..1}.
     *
     * <p>
     * If the slider range has no size, this method returns {@code 0}.
     * </p>
     *
     * @return the normalized slider percent
     */
    public float getPercent() {
        float range = max - min;
        if (range <= 0f) {
            return 0f;
        }
        return (value - min) / range;
    }

    /**
     * Sets the slider value from a normalized percentage in the range {@code 0..1}.
     *
     * <p>
     * The supplied percent is clamped into the valid range before being converted into a concrete
     * slider value.
     * </p>
     *
     * @param percent the normalized percent to apply
     * @return this slider for chaining
     */
    public Slider setPercent(float percent) {
        setValue(min + MathUtils.clamp(percent, 0, 1) * (max - min));
        return this;
    }

    /**
     * Returns whether the slider is currently in a dragging interaction.
     *
     * @return true if dragging is active, otherwise false
     */
    public boolean isDragging() {
        return dragging;
    }

    /**
     * Returns the x position of the track.
     *
     * <p>
     * The track begins at the slider element's x position.
     * </p>
     *
     * @return the track x position
     */
    public float getTrackX() {
        return x;
    }

    /**
     * Returns the y position of the track.
     *
     * <p>
     * The track is vertically centered inside the slider element's bounds.
     * </p>
     *
     * @return the track y position
     */
    public float getTrackY() {
        return y + (height - trackHeight) * 0.5f;
    }

    /**
     * Returns the width of the track.
     *
     * <p>
     * The track spans the full width of the slider element.
     * </p>
     *
     * @return the track width
     */
    public float getTrackWidth() {
        return width;
    }

    /**
     * Returns the x position of the thumb.
     *
     * <p>
     * The thumb is positioned according to the current slider percentage across the usable width,
     * which is the track width minus the thumb width.
     * </p>
     *
     * @return the thumb x position
     */
    public float getThumbX() {
        float usableWidth = Math.max(0f, getTrackWidth() - thumbWidth);
        return getTrackX() + getPercent() * usableWidth;
    }

    /**
     * Returns the y position of the thumb.
     *
     * <p>
     * The thumb is vertically centered inside the slider bounds and then shifted by the configured
     * thumb offset.
     * </p>
     *
     * @return the thumb y position
     */
    public float getThumbY() {
        return y + (height - thumbHeight) * 0.5f + thumbOffsetY;
    }

    /**
     * Returns the width of the fill portion of the slider.
     *
     * <p>
     * The fill extends from the track start to the center of the thumb.
     * </p>
     *
     * @return the fill width
     */
    public float getFillWidth() {
        return getThumbX() - getTrackX() + thumbWidth * 0.5f;
    }

    /**
     * Updates the hovered state of the slider.
     *
     * <p>
     * This method currently delegates directly to the base implementation, but remains available for
     * future slider-specific hover behavior.
     * </p>
     *
     * @param value true if hovered, otherwise false
     */
    @Override
    protected void setHovered(boolean value) {
        super.setHovered(value);
    }

    /**
     * Updates the focused state of the slider.
     *
     * <p>
     * This method currently delegates directly to the base implementation, but remains available for
     * future slider-specific focus behavior.
     * </p>
     *
     * @param value true if focused, otherwise false
     */
    @Override
    protected void setFocused(boolean value) {
        super.setFocused(value);
    }

    /**
     * Resolves the drawable used for the track based on the slider's current state.
     *
     * <p>
     * Resolution priority is disabled, pressed, hovered, focused, then normal.
     * </p>
     *
     * @return the drawable chosen for the track, or null if none is available
     */
    private Drawable resolveTrackDrawable() {
        if (isDisabled() && style.getDisabledTrack() != null) {
            return style.getDisabledTrack();
        }
        if (isPressed() && style.getPressedTrack() != null) {
            return style.getPressedTrack();
        }
        if (isHovered() && style.getHoveredTrack() != null) {
            return style.getHoveredTrack();
        }
        if (isFocused() && style.getFocusedTrack() != null) {
            return style.getFocusedTrack();
        }
        return style.getTrack();
    }

    /**
     * Resolves the drawable used for the fill based on the slider's current state.
     *
     * <p>
     * Resolution priority is disabled, pressed, hovered, focused, then normal.
     * </p>
     *
     * @return the drawable chosen for the fill, or null if none is available
     */
    private Drawable resolveFillDrawable() {
        if (isDisabled() && style.getDisabledFill() != null) {
            return style.getDisabledFill();
        }
        if (isPressed() && style.getPressedFill() != null) {
            return style.getPressedFill();
        }
        if (isHovered() && style.getHoveredFill() != null) {
            return style.getHoveredFill();
        }
        if (isFocused() && style.getFocusedFill() != null) {
            return style.getFocusedFill();
        }
        return style.getFill();
    }

    /**
     * Resolves the drawable used for the thumb based on the slider's current state.
     *
     * <p>
     * Resolution priority is disabled, pressed, hovered, focused, then normal.
     * </p>
     *
     * @return the drawable chosen for the thumb, or null if none is available
     */
    private Drawable resolveThumbDrawable() {
        if (isDisabled() && style.getDisabledThumb() != null) {
            return style.getDisabledThumb();
        }
        if (isPressed() && style.getPressedThumb() != null) {
            return style.getPressedThumb();
        }
        if (isHovered() && style.getHoveredThumb() != null) {
            return style.getHoveredThumb();
        }
        if (isFocused() && style.getFocusedThumb() != null) {
            return style.getFocusedThumb();
        }
        return style.getThumb();
    }

    /**
     * Updates the slider value from a pointer position.
     *
     * <p>
     * The pointer is converted into local slider space, mapped across the usable width of the slider,
     * transformed into a normalized percent, and then converted into a concrete slider value. If the
     * value changes, the action callback is fired.
     * </p>
     *
     * @param screenX the pointer x coordinate in screen space
     * @param screenY the pointer y coordinate in screen space
     */
    private void updateValueFromPointer(float screenX, float screenY) {
        float localX = resolvePointerX(screenX, screenY);
        float trackX = getTrackX();
        float usableWidth = Math.max(0f, getTrackWidth() - thumbWidth);

        if (usableWidth <= 0f) {
            return;
        }

        float percent = (localX - trackX - thumbWidth * 0.5f) / usableWidth;
        float previous = value;

        setPercent(percent);

        if (previous != value) {
            fireAction();
        }
    }

    /**
     * Resolves a pointer x coordinate into world/UI space using the current UI viewport when available.
     *
     * <p>
     * If no UI or viewport exists, the original screen-space x coordinate is returned. If the viewport
     * cannot resolve the point, the original x coordinate is also used as a fallback.
     * </p>
     *
     * @param screenX the pointer x coordinate in screen space
     * @param screenY the pointer y coordinate in screen space
     * @return the resolved x coordinate used for slider interaction
     */
    private float resolvePointerX(float screenX, float screenY) {
        if (getUI() == null) {
            return screenX;
        }

        Viewport viewport = getUI().getViewport();
        if (viewport == null) {
            return screenX;
        }

        Vector2f world = viewport.screenToWorld(screenX, screenY);
        return world == null ? screenX : world.getX();
    }

    /**
     * Fires the slider action callback if one is assigned.
     *
     * <p>
     * The slider itself is passed to the action so external code can inspect the current state and
     * value.
     * </p>
     */
    private void fireAction() {
        if (action != null) {
            action.perform(this);
        }
    }

    /**
     * Snaps a value to the nearest valid step when stepping is enabled.
     *
     * <p>
     * If the step size is zero or negative, the value is returned unchanged. Otherwise the value is
     * snapped relative to the slider minimum and then clamped back into the valid range.
     * </p>
     *
     * @param value the value to snap
     * @return the snapped result
     */
    private float snap(float value) {
        if (stepSize <= 0f) return value;

        float steps = Math.round((value - min) / stepSize);
        float snapped = min + steps * stepSize;
        return Math.clamp(snapped, min, max);
    }
}