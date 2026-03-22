package valthorne.ui.nodes.nano;

import valthorne.Keyboard;
import valthorne.event.events.KeyPressEvent;
import valthorne.event.events.MouseDragEvent;
import valthorne.event.events.MousePressEvent;
import valthorne.event.events.MouseScrollEvent;
import valthorne.graphics.Color;
import valthorne.graphics.texture.TextureBatch;
import valthorne.math.MathUtils;
import valthorne.math.Vector2f;
import valthorne.ui.NanoUtility;
import valthorne.ui.NodeAction;
import valthorne.ui.UINode;
import valthorne.ui.UIRoot;
import valthorne.ui.theme.ResolvedStyle;
import valthorne.ui.theme.StyleKey;
import valthorne.viewport.Viewport;

import static org.lwjgl.nanovg.NanoVG.*;

public class NanoSlider  extends UINode implements NanoNode  {
    public static final StyleKey<Color> TRACK_COLOR_KEY = StyleKey.of("nano.slider.trackColor", Color.class, new Color(0xFF2A2A2A));
    public static final StyleKey<Color> HOVER_TRACK_COLOR_KEY = StyleKey.of("nano.slider.hoverTrackColor", Color.class, new Color(0xFF323232));
    public static final StyleKey<Color> FOCUSED_TRACK_COLOR_KEY = StyleKey.of("nano.slider.focusedTrackColor", Color.class, new Color(0xFF3A3A3A));
    public static final StyleKey<Color> DISABLED_TRACK_COLOR_KEY = StyleKey.of("nano.slider.disabledTrackColor", Color.class, new Color(0xFF242424));
    public static final StyleKey<Color> FILL_COLOR_KEY = StyleKey.of("nano.slider.fillColor", Color.class, new Color(0xFF555555));
    public static final StyleKey<Color> HOVER_FILL_COLOR_KEY = StyleKey.of("nano.slider.hoverFillColor", Color.class, new Color(0xFF666666));
    public static final StyleKey<Color> FOCUSED_FILL_COLOR_KEY = StyleKey.of("nano.slider.focusedFillColor", Color.class, new Color(0xFF7AA2FF));
    public static final StyleKey<Color> DISABLED_FILL_COLOR_KEY = StyleKey.of("nano.slider.disabledFillColor", Color.class, new Color(0xFF3A3A3A));
    public static final StyleKey<Color> THUMB_COLOR_KEY = StyleKey.of("nano.slider.thumbColor", Color.class, new Color(0xFF6A6A6A));
    public static final StyleKey<Color> HOVER_THUMB_COLOR_KEY = StyleKey.of("nano.slider.hoverThumbColor", Color.class, new Color(0xFF7A7A7A));
    public static final StyleKey<Color> FOCUSED_THUMB_COLOR_KEY = StyleKey.of("nano.slider.focusedThumbColor", Color.class, new Color(0xFF8A8A8A));
    public static final StyleKey<Color> PRESSED_THUMB_COLOR_KEY = StyleKey.of("nano.slider.pressedThumbColor", Color.class, new Color(0xFF9A9A9A));
    public static final StyleKey<Color> DISABLED_THUMB_COLOR_KEY = StyleKey.of("nano.slider.disabledThumbColor", Color.class, new Color(0xFF4A4A4A));
    public static final StyleKey<Float> TRACK_HEIGHT_KEY = StyleKey.of("nano.slider.trackHeight", Float.class, 8f);
    public static final StyleKey<Float> THUMB_SIZE_KEY = StyleKey.of("nano.slider.thumbSize", Float.class, 18f);
    public static final StyleKey<NodeAction<NanoSlider>> ACTION_KEY = StyleKey.of("action", (Class<NodeAction<NanoSlider>>) (Class<?>) NodeAction.class);
    private NodeAction<NanoSlider> action;
    private float min;
    private float max;
    private float value;
    private float stepSize;
    private float trackHeight = 8f;
    private float thumbSize = 18f;
    private boolean vertical;
    private Color trackColor = new Color(0xFF2A2A2A);
    private Color hoverTrackColor = new Color(0xFF323232);
    private Color focusedTrackColor = new Color(0xFF3A3A3A);
    private Color disabledTrackColor = new Color(0xFF242424);
    private Color fillColor = new Color(0xFF555555);
    private Color hoverFillColor = new Color(0xFF666666);
    private Color focusedFillColor = new Color(0xFF7AA2FF);
    private Color disabledFillColor = new Color(0xFF3A3A3A);
    private Color thumbColor = new Color(0xFF6A6A6A);
    private Color hoverThumbColor = new Color(0xFF7A7A7A);
    private Color focusedThumbColor = new Color(0xFF8A8A8A);
    private Color pressedThumbColor = new Color(0xFF9A9A9A);
    private Color disabledThumbColor = new Color(0xFF4A4A4A);

    public NanoSlider() {this(0f, 1f, 0f);}

    public NanoSlider(float min, float max, float value) {this(min, max, value, null);}

    public NanoSlider(float min, float max, float value, NodeAction<NanoSlider> action) {
        this.action = action;
        this.min = min;
        this.max = Math.max(min, max);
        this.value = MathUtils.clamp(value, min, this.max);
        setBit(CLICKABLE_BIT, true);
        setBit(FOCUSABLE_BIT, true);
        setBit(DRAGGING_BIT, false);
        setBit(SCROLLABLE_BIT, true);
        getLayout().width(160).height(Math.max(trackHeight, thumbSize));
    }

    public NanoSlider action(NodeAction<NanoSlider> action) {
        this.action = action;
        return this;
    }

    public NanoSlider trackColor(Color color) {
        if (color != null) this.trackColor = color;
        return this;
    }

    public NanoSlider hoverTrackColor(Color color) {
        if (color != null) this.hoverTrackColor = color;
        return this;
    }

    public NanoSlider focusedTrackColor(Color color) {
        if (color != null) this.focusedTrackColor = color;
        return this;
    }

    public NanoSlider disabledTrackColor(Color color) {
        if (color != null) this.disabledTrackColor = color;
        return this;
    }

    public NanoSlider fillColor(Color color) {
        if (color != null) this.fillColor = color;
        return this;
    }

    public NanoSlider hoverFillColor(Color color) {
        if (color != null) this.hoverFillColor = color;
        return this;
    }

    public NanoSlider focusedFillColor(Color color) {
        if (color != null) this.focusedFillColor = color;
        return this;
    }

    public NanoSlider disabledFillColor(Color color) {
        if (color != null) this.disabledFillColor = color;
        return this;
    }

    public NanoSlider thumbColor(Color color) {
        if (color != null) this.thumbColor = color;
        return this;
    }

    public NanoSlider hoverThumbColor(Color color) {
        if (color != null) this.hoverThumbColor = color;
        return this;
    }

    public NanoSlider focusedThumbColor(Color color) {
        if (color != null) this.focusedThumbColor = color;
        return this;
    }

    public NanoSlider pressedThumbColor(Color color) {
        if (color != null) this.pressedThumbColor = color;
        return this;
    }

    public NanoSlider disabledThumbColor(Color color) {
        if (color != null) this.disabledThumbColor = color;
        return this;
    }

    public float getValue() {return value;}

    public NanoSlider value(float value) {
        this.value = snap(MathUtils.clamp(value, min, max));
        return this;
    }

    public float getPercent() {
        float range = max - min;
        if (range <= 0f) return 0f;
        return (value - min) / range;
    }

    public NanoSlider percent(float percent) {
        value(min + MathUtils.clamp(percent, 0f, 1f) * (max - min));
        return this;
    }

    public NanoSlider stepSize(float stepSize) {
        this.stepSize = Math.max(0f, stepSize);
        this.value = snap(this.value);
        return this;
    }

    public NanoSlider increment() {
        float amount = stepSize > 0f ? stepSize : Math.max((max - min) / 100f, 0.000001f);
        value(value + amount);
        return this;
    }

    public NanoSlider decrement() {
        float amount = stepSize > 0f ? stepSize : Math.max((max - min) / 100f, 0.000001f);
        value(value - amount);
        return this;
    }

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

    public NanoSlider horizontal(boolean horizontal) {return vertical(!horizontal);}

    public float getTrackX() {
        if (vertical) return getAbsoluteX() + (getWidth() - trackHeight) * 0.5f;
        return getAbsoluteX();
    }

    public float getTrackY() {
        if (vertical) return getAbsoluteY();
        return getAbsoluteY() + (getHeight() - trackHeight) * 0.5f;
    }

    public float getTrackWidth() {
        if (vertical) return trackHeight;
        return getWidth();
    }

    public float getTrackActualHeight() {
        if (vertical) return getHeight();
        return trackHeight;
    }

    public float getThumbCenterX() {
        if (vertical) return getAbsoluteX() + getWidth() * 0.5f;
        float usableWidth = Math.max(0f, getTrackWidth() - thumbSize);
        return getTrackX() + getPercent() * usableWidth + thumbSize * 0.5f;
    }

    public float getThumbCenterY() {
        if (vertical) {
            float usableHeight = Math.max(0f, getTrackActualHeight() - thumbSize);
            return getTrackY() + (1f - getPercent()) * usableHeight + thumbSize * 0.5f;
        }
        return getAbsoluteY() + getHeight() * 0.5f;
    }

    @Override
    public void onMouseDrag(MouseDragEvent event) {
        Vector2f position = this.screenToWorld(event.getX(), event.getY());
        float previous = value;
        if (vertical) {
            float usableHeight = Math.max(0f, getTrackActualHeight() - thumbSize);
            if (usableHeight <= 0f) return;
            float thumbTop = MathUtils.clamp(position.getY() - getTrackY() - thumbSize * 0.5f, 0f, usableHeight);
            float percent = 1f - (thumbTop / usableHeight);
            value(min + percent * (max - min));
        } else {
            float usableWidth = Math.max(0f, getTrackWidth() - thumbSize);
            if (usableWidth <= 0f) return;
            float thumbLeft = MathUtils.clamp(position.getX() - getTrackX() - thumbSize * 0.5f, 0f, usableWidth);
            float percent = thumbLeft / usableWidth;
            value(min + percent * (max - min));
        }
        if (previous != value) fireAction();
    }

    @Override
    public void onMouseScroll(MouseScrollEvent event) {
        float previous = value;
        float amount = stepSize > 0f ? stepSize : Math.max((max - min) / 100f, 0.000001f);
        if (vertical) {value(value + event.yOffset() * amount);} else {
            float offset = event.yOffset() != 0 ? event.yOffset() : event.xOffset();
            value(value + offset * amount);
        }
        if (previous != value) fireAction();
    }

    @Override
    public void onCreate() {}

    @Override
    public void onDestroy() {}

    @Override
    public void update(float delta) {}

    @Override
    public void draw(TextureBatch batch) {

    }

    @Override
    public void onKeyPress(KeyPressEvent event) {
        float previous = value;
        if (vertical) {
            if (event.getKey() == Keyboard.DOWN) {decrement();} else if (event.getKey() == Keyboard.UP) {
                increment();
            } else if (event.getKey() == Keyboard.HOME) {value(min);} else if (event.getKey() == Keyboard.END) {
                value(max);
            }
        } else {
            if (event.getKey() == Keyboard.LEFT) {decrement();} else if (event.getKey() == Keyboard.RIGHT) {
                increment();
            } else if (event.getKey() == Keyboard.HOME) {value(min);} else if (event.getKey() == Keyboard.END) {
                value(max);
            }
        }
        if (previous != value) fireAction();
    }

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

    private Viewport getViewport() {
        UIRoot root = getRoot();
        if (root == null) return null;
        return root.getViewport();
    }

    private void fireAction() {
        NodeAction<NanoSlider> resolvedAction = action;
        if (resolvedAction == null) {
            ResolvedStyle style = getStyle();
            if (style != null) resolvedAction = style.get(ACTION_KEY);
        }
        if (resolvedAction != null) resolvedAction.perform(this);
    }

    private float snap(float value) {
        if (stepSize <= 0f) return value;
        float steps = Math.round((value - min) / stepSize);
        float snapped = min + steps * stepSize;
        return MathUtils.clamp(snapped, min, max);
    }
}