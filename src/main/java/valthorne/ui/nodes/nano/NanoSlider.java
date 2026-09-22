package valthorne.ui.nodes.nano;

import valthorne.event.events.KeyPressEvent;
import valthorne.event.events.MouseDragEvent;
import valthorne.event.events.MousePressEvent;
import valthorne.event.events.MouseScrollEvent;
import valthorne.graphics.Color;
import valthorne.graphics.texture.TextureBatch;
import org.joml.Vector2f;
import valthorne.ui.NanoUtility;
import valthorne.ui.NodeAction;
import valthorne.ui.UINode;
import valthorne.ui.UIRoot;
import valthorne.ui.theme.ResolvedStyle;
import valthorne.ui.theme.StyleKey;
import valthorne.viewport.Viewport;

import static org.lwjgl.nanovg.NanoVG.*;
import valthorne.Mouse;
import valthorne.event.events.MouseReleaseEvent;
import valthorne.ui.behavior.RangeModel;

/**
 * Focusable NanoVG range control with mouse, wheel, and keyboard input. Values
 * are clamped and optionally snapped by RangeModel; horizontal values increase
 * rightward and vertical values increase upward. Input changes invoke a local
 * action or theme fallback, while programmatic value, percent, increment, and
 * decrement calls remain silent.
 *
 * <p>Construction sets a fixed 160-by-18 layout. Orientation changes supply
 * alternate dimensions only where layout is auto, so switching to vertical does
 * not automatically swap those fixed defaults. Track/thumb dimensions come from
 * resolved styles. The root owns capture routing, clipping, and NanoVG lifetime.</p>
 *
 * @author Albert Beaupre
 */
public class NanoSlider extends UINode implements NanoNode {
    /**
     * Theme track color; dimensions use UI units and colors are borrowed.
     */
    public static final StyleKey<Color> TRACK_COLOR_KEY = StyleKey.of("nano.slider.trackColor", Color.class, new Color(0xFF2A2A2A));
    /**
     * Theme hover track color; dimensions use UI units and colors are borrowed.
     */
    public static final StyleKey<Color> HOVER_TRACK_COLOR_KEY = StyleKey.of("nano.slider.hoverTrackColor", Color.class, new Color(0xFF323232));
    /**
     * Theme focused track color; dimensions use UI units and colors are borrowed.
     */
    public static final StyleKey<Color> FOCUSED_TRACK_COLOR_KEY = StyleKey.of("nano.slider.focusedTrackColor", Color.class, new Color(0xFF3A3A3A));
    /**
     * Theme disabled track color; dimensions use UI units and colors are borrowed.
     */
    public static final StyleKey<Color> DISABLED_TRACK_COLOR_KEY = StyleKey.of("nano.slider.disabledTrackColor", Color.class, new Color(0xFF242424));
    /**
     * Theme fill color; dimensions use UI units and colors are borrowed.
     */
    public static final StyleKey<Color> FILL_COLOR_KEY = StyleKey.of("nano.slider.fillColor", Color.class, new Color(0xFF555555));
    /**
     * Theme hover fill color; dimensions use UI units and colors are borrowed.
     */
    public static final StyleKey<Color> HOVER_FILL_COLOR_KEY = StyleKey.of("nano.slider.hoverFillColor", Color.class, new Color(0xFF666666));
    /**
     * Theme focused fill color; dimensions use UI units and colors are borrowed.
     */
    public static final StyleKey<Color> FOCUSED_FILL_COLOR_KEY = StyleKey.of("nano.slider.focusedFillColor", Color.class, new Color(0xFF7AA2FF));
    /**
     * Theme disabled fill color; dimensions use UI units and colors are borrowed.
     */
    public static final StyleKey<Color> DISABLED_FILL_COLOR_KEY = StyleKey.of("nano.slider.disabledFillColor", Color.class, new Color(0xFF3A3A3A));
    /**
     * Theme thumb color; dimensions use UI units and colors are borrowed.
     */
    public static final StyleKey<Color> THUMB_COLOR_KEY = StyleKey.of("nano.slider.thumbColor", Color.class, new Color(0xFF6A6A6A));
    /**
     * Theme hover thumb color; dimensions use UI units and colors are borrowed.
     */
    public static final StyleKey<Color> HOVER_THUMB_COLOR_KEY = StyleKey.of("nano.slider.hoverThumbColor", Color.class, new Color(0xFF7A7A7A));
    /**
     * Theme focused thumb color; dimensions use UI units and colors are borrowed.
     */
    public static final StyleKey<Color> FOCUSED_THUMB_COLOR_KEY = StyleKey.of("nano.slider.focusedThumbColor", Color.class, new Color(0xFF8A8A8A));
    /**
     * Theme pressed thumb color; dimensions use UI units and colors are borrowed.
     */
    public static final StyleKey<Color> PRESSED_THUMB_COLOR_KEY = StyleKey.of("nano.slider.pressedThumbColor", Color.class, new Color(0xFF9A9A9A));
    /**
     * Theme disabled thumb color; dimensions use UI units and colors are borrowed.
     */
    public static final StyleKey<Color> DISABLED_THUMB_COLOR_KEY = StyleKey.of("nano.slider.disabledThumbColor", Color.class, new Color(0xFF4A4A4A));
    /**
     * Theme track height; dimensions use UI units and colors are borrowed.
     */
    public static final StyleKey<Float> TRACK_HEIGHT_KEY = StyleKey.of("nano.slider.trackHeight", Float.class, 8f);
    /**
     * Theme thumb size; dimensions use UI units and colors are borrowed.
     */
    public static final StyleKey<Float> THUMB_SIZE_KEY = StyleKey.of("nano.slider.thumbSize", Float.class, 18f);
    /**
     * Theme input-change callback used when the local action is null.
     */
    public static final StyleKey<NodeAction<NanoSlider>> ACTION_KEY = StyleKey.of("action", (Class<NodeAction<NanoSlider>>) (Class<?>) NodeAction.class);
    private final RangeModel model = new RangeModel(0, 1, 0); // Owned finite range, value, and step calculations.
    private NodeAction<NanoSlider> action; // Optional local input-change callback overriding theme fallback.
    private float trackHeight = 8f; // Track cross-axis thickness in UI units.
    private float thumbSize = 18f; // Circular thumb diameter and reserved travel size in UI units.
    private boolean vertical; // Whether increasing values move the thumb upward.
    private Color trackColor = new Color(0xFF2A2A2A); // Borrowed track color for state-dependent painting.
    private Color hoverTrackColor = new Color(0xFF323232); // Borrowed hover track color for state-dependent painting.
    private Color focusedTrackColor = new Color(0xFF3A3A3A); // Borrowed focused track color for state-dependent painting.
    private Color disabledTrackColor = new Color(0xFF242424); // Borrowed disabled track color for state-dependent painting.
    private Color fillColor = new Color(0xFF555555); // Borrowed fill color for state-dependent painting.
    private Color hoverFillColor = new Color(0xFF666666); // Borrowed hover fill color for state-dependent painting.
    private Color focusedFillColor = new Color(0xFF7AA2FF); // Borrowed focused fill color for state-dependent painting.
    private Color disabledFillColor = new Color(0xFF3A3A3A); // Borrowed disabled fill color for state-dependent painting.
    private Color thumbColor = new Color(0xFF6A6A6A); // Borrowed thumb color for state-dependent painting.
    private Color hoverThumbColor = new Color(0xFF7A7A7A); // Borrowed hover thumb color for state-dependent painting.
    private Color focusedThumbColor = new Color(0xFF8A8A8A); // Borrowed focused thumb color for state-dependent painting.
    private Color pressedThumbColor = new Color(0xFF9A9A9A); // Borrowed pressed thumb color for state-dependent painting.
    private Color disabledThumbColor = new Color(0xFF4A4A4A); // Borrowed disabled thumb color for state-dependent painting.

    /**
     * Creates a horizontal [0, 1] slider at zero with no local action and default
     * fixed dimensions. Snapping is initially disabled.
     */
    public NanoSlider() {this(0f, 1f, 0f);}

    /**
     * Creates a horizontal slider with validated range and value, using theme action
     * fallback. A reversed range collapses at min and the value is clamped.
     *
     * @param min finite minimum
     * @param max finite requested maximum
     * @param value finite initial value
     * @throws IllegalArgumentException if an input or effective range width is non-finite
     */
    public NanoSlider(float min, float max, float value) {this(min, max, value, null);}

    /**
     * Stores the local action, validates/clamps range state, enables pointer, keyboard,
     * and wheel capabilities, and assigns default dimensions. Initialization does
     * not invoke the action; a reversed range collapses at min.
     *
     * @param min finite minimum
     * @param max finite requested maximum
     * @param value finite initial value
     * @param action optional local input-change callback
     * @throws IllegalArgumentException if an input or effective range width is non-finite
     */
    public NanoSlider(float min, float max, float value, NodeAction<NanoSlider> action) {
        this.action = action;
        model.range(min, max);
        model.value(value);
        setBit(CLICKABLE_BIT, true);
        setBit(FOCUSABLE_BIT, true);
        setBit(DRAGGING_BIT, false);
        setBit(SCROLLABLE_BIT, true);
        getLayout().width(160).height(Math.max(trackHeight, thumbSize));
    }

    /**
     * Replaces the local input-change callback without invoking it. Null restores
     * resolved theme fallback rather than necessarily disabling notifications.
     *
     * @param action synchronous callback, or null
     * @return this slider
     */
    public NanoSlider action(NodeAction<NanoSlider> action) {
        this.action = action;
        return this;
    }

    /**
     * Retains a non-null track color without copying it. Null keeps the current
     * reference; resolved styles may replace the value during layout.
     *
     * @param color mutable paint color, or null
     * @return this slider
     */
    public NanoSlider trackColor(Color color) {
        if (color != null) this.trackColor = color;
        return this;
    }

    /**
     * Retains a non-null hover track color without copying it. Null keeps the current
     * reference; resolved styles may replace the value during layout.
     *
     * @param color mutable paint color, or null
     * @return this slider
     */
    public NanoSlider hoverTrackColor(Color color) {
        if (color != null) this.hoverTrackColor = color;
        return this;
    }

    /**
     * Retains a non-null focused track color without copying it. Null keeps the current
     * reference; resolved styles may replace the value during layout.
     *
     * @param color mutable paint color, or null
     * @return this slider
     */
    public NanoSlider focusedTrackColor(Color color) {
        if (color != null) this.focusedTrackColor = color;
        return this;
    }

    /**
     * Retains a non-null disabled track color without copying it. Null keeps the current
     * reference; resolved styles may replace the value during layout.
     *
     * @param color mutable paint color, or null
     * @return this slider
     */
    public NanoSlider disabledTrackColor(Color color) {
        if (color != null) this.disabledTrackColor = color;
        return this;
    }

    /**
     * Retains a non-null fill color without copying it. Null keeps the current
     * reference; resolved styles may replace the value during layout.
     *
     * @param color mutable paint color, or null
     * @return this slider
     */
    public NanoSlider fillColor(Color color) {
        if (color != null) this.fillColor = color;
        return this;
    }

    /**
     * Retains a non-null hover fill color without copying it. Null keeps the current
     * reference; resolved styles may replace the value during layout.
     *
     * @param color mutable paint color, or null
     * @return this slider
     */
    public NanoSlider hoverFillColor(Color color) {
        if (color != null) this.hoverFillColor = color;
        return this;
    }

    /**
     * Retains a non-null focused fill color without copying it. Null keeps the current
     * reference; resolved styles may replace the value during layout.
     *
     * @param color mutable paint color, or null
     * @return this slider
     */
    public NanoSlider focusedFillColor(Color color) {
        if (color != null) this.focusedFillColor = color;
        return this;
    }

    /**
     * Retains a non-null disabled fill color without copying it. Null keeps the current
     * reference; resolved styles may replace the value during layout.
     *
     * @param color mutable paint color, or null
     * @return this slider
     */
    public NanoSlider disabledFillColor(Color color) {
        if (color != null) this.disabledFillColor = color;
        return this;
    }

    /**
     * Retains a non-null thumb color without copying it. Null keeps the current
     * reference; resolved styles may replace the value during layout.
     *
     * @param color mutable paint color, or null
     * @return this slider
     */
    public NanoSlider thumbColor(Color color) {
        if (color != null) this.thumbColor = color;
        return this;
    }

    /**
     * Retains a non-null hover thumb color without copying it. Null keeps the current
     * reference; resolved styles may replace the value during layout.
     *
     * @param color mutable paint color, or null
     * @return this slider
     */
    public NanoSlider hoverThumbColor(Color color) {
        if (color != null) this.hoverThumbColor = color;
        return this;
    }

    /**
     * Retains a non-null focused thumb color without copying it. Null keeps the current
     * reference; resolved styles may replace the value during layout.
     *
     * @param color mutable paint color, or null
     * @return this slider
     */
    public NanoSlider focusedThumbColor(Color color) {
        if (color != null) this.focusedThumbColor = color;
        return this;
    }

    /**
     * Retains a non-null pressed thumb color without copying it. Null keeps the current
     * reference; resolved styles may replace the value during layout.
     *
     * @param color mutable paint color, or null
     * @return this slider
     */
    public NanoSlider pressedThumbColor(Color color) {
        if (color != null) this.pressedThumbColor = color;
        return this;
    }

    /**
     * Retains a non-null disabled thumb color without copying it. Null keeps the current
     * reference; resolved styles may replace the value during layout.
     *
     * @param color mutable paint color, or null
     * @return this slider
     */
    public NanoSlider disabledThumbColor(Color color) {
        if (color != null) this.disabledThumbColor = color;
        return this;
    }

    /**
     * Reads the model's already clamped and snapped numeric value.
     *
     * @return current value within the effective endpoints
     */
    public float getValue() {
        return model.value();
    }

    /**
     * Sets a finite value through range clamping and optional minimum-anchored
     * snapping. Does not invoke the widget action, even when the value changes.
     *
     * @param value requested numeric value
     * @return this slider
     * @throws IllegalArgumentException if value is non-finite
     */
    public NanoSlider value(float value) {
        model.value(value);
        return this;
    }

    /**
     * Normalizes current value to [0, 1], returning zero for a collapsed range.
     *
     * @return fractional progress, not a zero-to-one-hundred percentage
     */
    public float getPercent() {
        return model.percent();
    }

    /**
     * Clamps a finite fraction to [0, 1], maps it to the range, and applies snapping.
     * The resulting fraction may differ from the request. No widget action is fired.
     *
     * @param percent requested normalized fraction
     * @return this slider
     * @throws IllegalArgumentException if percent is non-finite
     */
    public NanoSlider percent(float percent) {
        model.percent(percent);
        return this;
    }

    /**
     * Sets the finite step and reapplies it to the current value without notification.
     * Negative values become zero, disabling snapping; positive values snap relative
     * to the minimum using ties-to-even rounding, with exact endpoints reachable.
     *
     * @param stepSize requested increment in numeric range units
     * @return this slider
     * @throws IllegalArgumentException if stepSize is non-finite
     */
    public NanoSlider stepSize(float stepSize) {
        model.step(stepSize);
        return this;
    }

    /**
     * Increases by one configured step, or one percent of the range with a minimum
     * increment of 0.000001 when stepping is disabled. Clamps/snaps without firing
     * the widget action.
     *
     * @return this slider
     */
    public NanoSlider increment() {
        model.increment(1);
        return this;
    }

    /**
     * Decreases by one configured or fallback increment through model clamping and
     * snapping. This programmatic operation does not fire the widget action.
     *
     * @return this slider
     */
    public NanoSlider decrement() {
        model.increment(-1);
        return this;
    }

    /**
     * Changes orientation, supplies default dimensions only for axes still auto,
     * and marks layout dirty. Existing fixed dimensions and numeric value are retained.
     *
     * @param vertical true for bottom-to-top increase
     * @return this slider
     */
    public NanoSlider vertical(boolean vertical) {
        this.vertical = vertical;
        if (vertical) {
            if (getLayout().getWidth().isAuto()) getLayout().width(Math.max(trackHeight, thumbSize));
            if (getLayout().getHeight().isAuto()) getLayout().height(160);
        } else {
            if (getLayout().getWidth().isAuto()) getLayout().width(160);
            if (getLayout().getHeight().isAuto()) getLayout().height(Math.max(trackHeight, thumbSize));
        }
        markLayoutDirty();
        return this;
    }

    /**
     * Delegates to vertical with the inverse flag, retaining existing fixed dimensions.
     *
     * @param horizontal true for left-to-right increase
     * @return this slider
     */
    public NanoSlider horizontal(boolean horizontal) {return vertical(!horizontal);}

    /**
     * Computes the absolute left edge, centering track thickness across a vertical node.
     *
     * @return track X in UI world coordinates
     */
    public float getTrackX() {
        if (vertical) return getAbsoluteX() + (getWidth() - trackHeight) * 0.5f;
        return getAbsoluteX();
    }

    /**
     * Computes the absolute top edge, centering track thickness across a horizontal node.
     *
     * @return track Y in UI world coordinates
     */
    public float getTrackY() {
        if (vertical) return getAbsoluteY();
        return getAbsoluteY() + (getHeight() - trackHeight) * 0.5f;
    }

    /**
     * Returns node width for horizontal orientation or configured thickness for vertical.
     *
     * @return drawn track width in UI units
     */
    public float getTrackWidth() {
        if (vertical) return trackHeight;
        return getWidth();
    }

    /**
     * Returns node height for vertical orientation or configured thickness for horizontal.
     *
     * @return drawn track height in UI units
     */
    public float getTrackActualHeight() {
        if (vertical) return getHeight();
        return trackHeight;
    }

    /**
     * Computes absolute thumb center X. Horizontal travel is clamped nonnegative
     * after subtracting thumb size; vertical orientation uses the node midpoint.
     *
     * @return thumb center X in UI world coordinates
     */
    public float getThumbCenterX() {
        if (vertical) return getAbsoluteX() + getWidth() * 0.5f;
        float usableWidth = Math.max(0f, getTrackWidth() - thumbSize);
        return getTrackX() + getPercent() * usableWidth + thumbSize * 0.5f;
    }

    /**
     * Computes absolute thumb center Y. Vertical travel reverses normalized value
     * and reserves half a thumb at each end; horizontal orientation uses node midpoint.
     *
     * @return thumb center Y in UI world coordinates
     */
    public float getThumbCenterY() {
        if (vertical) {
            float usableHeight = Math.max(0f, getTrackActualHeight() - thumbSize);
            return getTrackY() + (1f - getPercent()) * usableHeight + thumbSize * 0.5f;
        }
        return getAbsoluteY() + getHeight() * 0.5f;
    }

    /**
     * Starts dragging and updates from a left press while not disabled. Other buttons
     * are ignored. This handler does not consume the press; root routing owns capture.
     *
     * @param event routed pointer press
     */
    @Override
    public void onMousePress(MousePressEvent event) {
        if (event.getButton() != Mouse.LEFT || isDisabled()) return;
        setDragging(true);
        updateFromPointer(event.getX(), event.getY());
    }

    /**
     * Updates from the drag endpoint only for the left button while dragging is set.
     * Relies on root routing for current eligibility and does not consume the event.
     *
     * @param event routed drag with screen-space endpoint
     */
    @Override
    public void onMouseDrag(MouseDragEvent event) {
        if (event.getButton() == Mouse.LEFT && isDragging())
            updateFromPointer(event.getToX(), event.getToY());
    }

    /**
     * Converts screen coordinates to node-local space, maps thumb travel to a clamped
     * value, and fires the action only on numeric change. Nonpositive usable travel
     * leaves the model unchanged.
     *
     * @param screenX pointer screen X
     * @param screenY pointer screen Y
     */
    private void updateFromPointer(int screenX, int screenY) {
        Vector2f local = screenToLocal(screenX, screenY);
        float previous = model.value();
        model.pointer(vertical ? local.y() : local.x(), vertical ? getHeight() : getWidth(),
                thumbSize, vertical);
        if (previous != model.value()) fireAction();
    }

    /**
     * Uses precise vertical wheel offset, or horizontal when vertical is zero, as
     * an increment count. Consumes and notifies only if the value changes, allowing
     * unchanged boundary scrolls to remain available to ancestors. Enabled-state
     * filtering is supplied by normal routing rather than checked in this method.
     *
     * @param event routed scroll event
     */
    @Override
    public void onMouseScroll(MouseScrollEvent event) {
        float previous = model.value();
        model.increment(event.preciseYOffset() != 0 ? event.preciseYOffset() : event.preciseXOffset());
        if (previous != model.value()) {
            event.consume();
            fireAction();
        }
    }

    /**
     * Allocates no native resources; the root supplies the NanoVG context.
     */
    @Override
    public void onCreate() {}

    /**
     * Performs no native cleanup because all rendering context resources are borrowed.
     */
    @Override
    public void onDestroy() {}

    /**
     * Performs no timed work or child traversal; input handlers drive value changes.
     *
     * @param delta elapsed update seconds, unused
     */
    @Override
    public void update(float delta) {}

    /**
     * Enters shared root dispatch for NanoVG painting and mixed-backend state handling.
     *
     * @param batch active texture batch used by UI traversal
     */
    @Override
    public void draw(TextureBatch batch) {
        render(batch);
    }

    /**
     * Handles Home/End and orientation-appropriate arrows unless disabled. Recognized
     * keys are consumed even at an unchanged endpoint; action fires only on value
     * change. Other keys remain untouched.
     *
     * @param event routed key press
     */
    @Override
    public void onKeyPress(KeyPressEvent event) {
        if (isDisabled()) return;
        float previous = model.value();
        if (model.key(event.getKey(), vertical)) event.consume();
        if (previous != model.value()) fireAction();
    }

    /**
     * Applies resolved state colors and nonnegative track/thumb sizes, then supplies
     * auto cross-axis dimensions and vertical default length before base layout.
     * The action remains dynamically resolved at notification time.
     */
    @Override
    protected void applyLayout() {
        ResolvedStyle style = getStyle();
        if (style != null) {
            Color resolvedTrackColor = style.get(TRACK_COLOR_KEY);
            Color resolvedHoverTrackColor = style.get(HOVER_TRACK_COLOR_KEY);
            Color resolvedFocusedTrackColor = style.get(FOCUSED_TRACK_COLOR_KEY);
            Color resolvedDisabledTrackColor = style.get(DISABLED_TRACK_COLOR_KEY);
            Color resolvedFillColor = style.get(FILL_COLOR_KEY);
            Color resolvedHoverFillColor = style.get(HOVER_FILL_COLOR_KEY);
            Color resolvedFocusedFillColor = style.get(FOCUSED_FILL_COLOR_KEY);
            Color resolvedDisabledFillColor = style.get(DISABLED_FILL_COLOR_KEY);
            Color resolvedThumbColor = style.get(THUMB_COLOR_KEY);
            Color resolvedHoverThumbColor = style.get(HOVER_THUMB_COLOR_KEY);
            Color resolvedFocusedThumbColor = style.get(FOCUSED_THUMB_COLOR_KEY);
            Color resolvedPressedThumbColor = style.get(PRESSED_THUMB_COLOR_KEY);
            Color resolvedDisabledThumbColor = style.get(DISABLED_THUMB_COLOR_KEY);
            Float resolvedTrackHeight = style.get(TRACK_HEIGHT_KEY);
            Float resolvedThumbSize = style.get(THUMB_SIZE_KEY);
            if (resolvedTrackColor != null) trackColor = resolvedTrackColor;
            if (resolvedHoverTrackColor != null) hoverTrackColor = resolvedHoverTrackColor;
            if (resolvedFocusedTrackColor != null) focusedTrackColor = resolvedFocusedTrackColor;
            if (resolvedDisabledTrackColor != null) disabledTrackColor = resolvedDisabledTrackColor;
            if (resolvedFillColor != null) fillColor = resolvedFillColor;
            if (resolvedHoverFillColor != null) hoverFillColor = resolvedHoverFillColor;
            if (resolvedFocusedFillColor != null) focusedFillColor = resolvedFocusedFillColor;
            if (resolvedDisabledFillColor != null) disabledFillColor = resolvedDisabledFillColor;
            if (resolvedThumbColor != null) thumbColor = resolvedThumbColor;
            if (resolvedHoverThumbColor != null) hoverThumbColor = resolvedHoverThumbColor;
            if (resolvedFocusedThumbColor != null) focusedThumbColor = resolvedFocusedThumbColor;
            if (resolvedPressedThumbColor != null) pressedThumbColor = resolvedPressedThumbColor;
            if (resolvedDisabledThumbColor != null) disabledThumbColor = resolvedDisabledThumbColor;
            if (resolvedTrackHeight != null) trackHeight = Math.max(0f, resolvedTrackHeight);
            if (resolvedThumbSize != null) thumbSize = Math.max(0f, resolvedThumbSize);
        }
        if (vertical) {
            if (getLayout().getWidth().isAuto()) getLayout().width(Math.max(trackHeight, thumbSize));
            if (getLayout().getHeight().isAuto()) getLayout().height(160);
        } else {if (getLayout().getHeight().isAuto()) getLayout().height(Math.max(trackHeight, thumbSize));}
        super.applyLayout();
    }

    /**
     * Draws track, normalized fill, and circular thumb. Disabled colors win; pressed
     * state selects its own thumb while retaining focus/hover track and fill colors.
     * Assumes the root has handled visibility and provided a valid NanoVG frame.
     *
     * @param vg borrowed active NanoVG context
     */
    @Override
    public void draw(long vg) {
        Color drawTrack = trackColor;
        Color drawFill = fillColor;
        Color drawThumb = thumbColor;
        if (!isEnabled()) {
            drawTrack = disabledTrackColor;
            drawFill = disabledFillColor;
            drawThumb = disabledThumbColor;
        } else if (isPressed()) {
            drawTrack = isFocused() ? focusedTrackColor : isHovered() ? hoverTrackColor : trackColor;
            drawFill = isFocused() ? focusedFillColor : isHovered() ? hoverFillColor : fillColor;
            drawThumb = pressedThumbColor;
        } else if (isFocused()) {
            drawTrack = focusedTrackColor;
            drawFill = focusedFillColor;
            drawThumb = focusedThumbColor;
        } else if (isHovered()) {
            drawTrack = hoverTrackColor;
            drawFill = hoverFillColor;
            drawThumb = hoverThumbColor;
        }
        float trackX = getTrackX();
        float trackY = getTrackY();
        float trackW = getTrackWidth();
        float trackH = getTrackActualHeight();
        nvgBeginPath(vg);
        nvgFillColor(vg, NanoUtility.color1(drawTrack));
        nvgRoundedRect(vg, trackX, trackY, trackW, trackH, Math.min(trackW, trackH) * 0.5f);
        nvgFill(vg);
        float percent = getPercent();
        if (vertical) {
            float fillH = percent * trackH;
            if (fillH > 0f) {
                float fillY = trackY + (trackH - fillH);
                nvgBeginPath(vg);
                nvgFillColor(vg, NanoUtility.color1(drawFill));
                nvgRoundedRect(vg, trackX, fillY, trackW, fillH, Math.min(trackW, trackH) * 0.5f);
                nvgFill(vg);
            }
        } else {
            float fillW = percent * trackW;
            if (fillW > 0f) {
                nvgBeginPath(vg);
                nvgFillColor(vg, NanoUtility.color1(drawFill));
                nvgRoundedRect(vg, trackX, trackY, fillW, trackH, Math.min(trackW, trackH) * 0.5f);
                nvgFill(vg);
            }
        }
        nvgBeginPath(vg);
        nvgFillColor(vg, NanoUtility.color1(drawThumb));
        nvgCircle(vg, getThumbCenterX(), getThumbCenterY(), thumbSize * 0.5f);
        nvgFill(vg);
    }

    /**
     * Returns the owning root's viewport when attached. This helper is not currently
     * used by pointer conversion, which delegates to inherited coordinate methods.
     *
     * @return borrowed viewport, or null when detached
     */
    private Viewport getViewport() {
        UIRoot root = getRoot();
        if (root == null) return null;
        return root.getViewport();
    }

    /**
     * Clears dragging for any delivered release without consuming it or changing value.
     * Capture lifecycle and cancellation are managed by the surrounding root/node policy.
     *
     * @param event routed pointer release
     */
    @Override
    public void onMouseRelease(MouseReleaseEvent event) {setDragging(false);}

    /**
     * Invokes the local callback or resolved theme fallback synchronously. Missing
     * actions are a no-op; callback errors propagate after the value change.
     */
    private void fireAction() {
        NodeAction<NanoSlider> resolvedAction = action;
        if (resolvedAction == null) {
            ResolvedStyle style = getStyle();
            if (style != null) resolvedAction = style.get(ACTION_KEY);
        }
        if (resolvedAction != null) resolvedAction.perform(this);
    }

    /**
     * Returns its argument unchanged. This unused helper does not perform snapping;
     * actual input and setter snapping is delegated to RangeModel.
     *
     * @param value candidate value
     * @return the same value
     */
    private float snap(float value) {
        return value;
    }
}
