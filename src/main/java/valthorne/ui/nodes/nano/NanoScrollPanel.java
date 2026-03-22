package valthorne.ui.nodes.nano;

import valthorne.event.events.*;
import valthorne.graphics.Color;
import valthorne.math.MathUtils;
import valthorne.math.Vector2f;
import valthorne.ui.NanoUtility;
import valthorne.ui.UINode;
import valthorne.ui.theme.ResolvedStyle;
import valthorne.ui.theme.StyleKey;

import static org.lwjgl.nanovg.NanoVG.*;

public class NanoScrollPanel extends NanoContainer {

    public static final StyleKey<Color> BACKGROUND_COLOR_KEY = StyleKey.of("nano.scrollpanel.backgroundColor", Color.class, new Color(0xFF2A2A2A));
    public static final StyleKey<Color> BORDER_COLOR_KEY = StyleKey.of("nano.scrollpanel.borderColor", Color.class, new Color(0xFF000000));
    public static final StyleKey<Float> BORDER_WIDTH_KEY = StyleKey.of("nano.scrollpanel.borderWidth", Float.class, 2f);

    public static final StyleKey<Color> HORIZONTAL_BAR_BACKGROUND_COLOR_KEY = StyleKey.of("nano.scrollpanel.horizontalBarBackgroundColor", Color.class, new Color(0xFF2A2A2A));
    public static final StyleKey<Color> HORIZONTAL_BAR_FOREGROUND_COLOR_KEY = StyleKey.of("nano.scrollpanel.horizontalBarForegroundColor", Color.class, new Color(0xFF5A5A5A));
    public static final StyleKey<Color> HORIZONTAL_BAR_HOVER_FOREGROUND_COLOR_KEY = StyleKey.of("nano.scrollpanel.horizontalBarHoverForegroundColor", Color.class, new Color(0xFF6A6A6A));
    public static final StyleKey<Color> HORIZONTAL_BAR_PRESSED_FOREGROUND_COLOR_KEY = StyleKey.of("nano.scrollpanel.horizontalBarPressedForegroundColor", Color.class, new Color(0xFF7A7A7A));

    public static final StyleKey<Color> VERTICAL_BAR_BACKGROUND_COLOR_KEY = StyleKey.of("nano.scrollpanel.verticalBarBackgroundColor", Color.class, new Color(0xFF2A2A2A));
    public static final StyleKey<Color> VERTICAL_BAR_FOREGROUND_COLOR_KEY = StyleKey.of("nano.scrollpanel.verticalBarForegroundColor", Color.class, new Color(0xFF5A5A5A));
    public static final StyleKey<Color> VERTICAL_BAR_HOVER_FOREGROUND_COLOR_KEY = StyleKey.of("nano.scrollpanel.verticalBarHoverForegroundColor", Color.class, new Color(0xFF6A6A6A));
    public static final StyleKey<Color> VERTICAL_BAR_PRESSED_FOREGROUND_COLOR_KEY = StyleKey.of("nano.scrollpanel.verticalBarPressedForegroundColor", Color.class, new Color(0xFF7A7A7A));

    public static final StyleKey<Float> HORIZONTAL_BAR_HEIGHT_KEY = StyleKey.of("nano.scrollpanel.horizontalBarHeight", Float.class, 16f);
    public static final StyleKey<Float> VERTICAL_BAR_WIDTH_KEY = StyleKey.of("nano.scrollpanel.verticalBarWidth", Float.class, 16f);
    public static final StyleKey<Float> BAR_PADDING_KEY = StyleKey.of("nano.scrollpanel.barPadding", Float.class, 4f);
    public static final StyleKey<Float> MIN_THUMB_SIZE_KEY = StyleKey.of("nano.scrollpanel.minThumbSize", Float.class, 18f);
    public static final StyleKey<Float> CORNER_RADIUS_KEY = StyleKey.of("nano.scrollpanel.cornerRadius", Float.class, 6f);

    private UINode content = new NanoPanel();

    private boolean horizontal = true;
    private boolean vertical = true;
    private boolean drawHorizontalBar = true;
    private boolean drawVerticalBar = true;

    private float scrollX;
    private float scrollY;
    private float targetScrollX;
    private float targetScrollY;
    private float scrollSpeed = 32f;

    private boolean draggingHorizontalBar;
    private boolean draggingVerticalBar;
    private boolean hoverHorizontalBar;
    private boolean hoverVerticalBar;

    private Color backgroundColor = new Color(0xFF242424);
    private Color borderColor = new Color(0xFF000000);

    private Color horizontalBarBackgroundColor = new Color(0xFF2A2A2A);
    private Color horizontalBarForegroundColor = new Color(0xFF5A5A5A);
    private Color horizontalBarHoverForegroundColor = new Color(0xFF6A6A6A);
    private Color horizontalBarPressedForegroundColor = new Color(0xFF7A7A7A);

    private Color verticalBarBackgroundColor = new Color(0xFF2A2A2A);
    private Color verticalBarForegroundColor = new Color(0xFF5A5A5A);
    private Color verticalBarHoverForegroundColor = new Color(0xFF6A6A6A);
    private Color verticalBarPressedForegroundColor = new Color(0xFF7A7A7A);

    private float borderWidth = 1f;
    private float horizontalBarHeight = 8f;
    private float verticalBarWidth = 8f;
    private float barPadding = 0f;
    private float minThumbSize = 18f;
    private float cornerRadius = 6f;

    private final ScrollMetrics metrics = new ScrollMetrics();

    public NanoScrollPanel() {
        setBit(SCROLLABLE_BIT, true);
        add(content);
    }

    public UINode getContent() {
        return content;
    }

    public void setContent(UINode child) {
        remove(this.content);
        if (child == null) {
            this.content = null;
            return;
        }
        this.content = child;
        add(child);
        markLayoutDirty();
    }

    public NanoScrollPanel scrollSpeed(float speed) {
        this.scrollSpeed = speed;
        return this;
    }

    public NanoScrollPanel horizontal(boolean horizontal) {
        this.horizontal = horizontal;
        if (!horizontal) {
            scrollX = 0f;
            targetScrollX = 0f;
            draggingHorizontalBar = false;
            hoverHorizontalBar = false;
        } else {
            scrollX = clampScrollX(scrollX);
            targetScrollX = clampScrollX(targetScrollX);
        }
        return this;
    }

    public NanoScrollPanel vertical(boolean vertical) {
        this.vertical = vertical;
        if (!vertical) {
            scrollY = 0f;
            targetScrollY = 0f;
            draggingVerticalBar = false;
            hoverVerticalBar = false;
        } else {
            scrollY = clampScrollY(scrollY);
            targetScrollY = clampScrollY(targetScrollY);
        }
        return this;
    }

    public NanoScrollPanel horizontalBar(boolean drawHorizontalBar) {
        this.drawHorizontalBar = drawHorizontalBar;
        return this;
    }

    public NanoScrollPanel verticalBar(boolean drawVerticalBar) {
        this.drawVerticalBar = drawVerticalBar;
        return this;
    }

    public float getScrollX() {
        return scrollX;
    }

    public float getScrollY() {
        return scrollY;
    }

    public NanoScrollPanel scrollX(float scrollX) {
        targetScrollX = horizontal ? clampScrollX(scrollX) : 0f;
        this.scrollX = targetScrollX;
        return this;
    }

    public NanoScrollPanel scrollY(float scrollY) {
        targetScrollY = vertical ? clampScrollY(scrollY) : 0f;
        this.scrollY = targetScrollY;
        return this;
    }

    public NanoScrollPanel scroll(float scrollX, float scrollY) {
        scrollX(scrollX);
        scrollY(scrollY);
        return this;
    }

    public NanoScrollPanel scrollBy(float dx, float dy) {
        if (horizontal) {
            targetScrollX = clampScrollX(targetScrollX + dx);
            scrollX = targetScrollX;
        }

        if (vertical) {
            targetScrollY = clampScrollY(targetScrollY + dy);
            scrollY = targetScrollY;
        }

        return this;
    }

    public float getMaxScrollX() {
        if (content == null) return 0f;
        return Math.max(0f, (content.getWidth() - getWidth()) + metrics.verticalBarWidth);
    }

    public float getMaxScrollY() {
        if (content == null) return 0f;
        return Math.max(0f, (content.getHeight() - getHeight()) + metrics.horizontalBarHeight);
    }

    @Override
    protected void invalidateStyleTree() {
        super.invalidateStyleTree();
        refreshStyleCache();
    }

    @Override
    protected float transformChildHitX(float x) {
        return x + scrollX;
    }

    @Override
    protected float transformChildHitY(float y) {
        return y - scrollY;
    }

    @Override
    public UINode findNodeAt(float x, float y, int requiredBit) {
        ScrollMetrics metrics = getScrollMetrics();

        boolean overHorizontalBar = metrics.showHorizontalBar && x >= metrics.horizontalBarX && x <= metrics.horizontalBarX + metrics.horizontalBarWidth && y >= metrics.horizontalBarY && y <= metrics.horizontalBarY + metrics.horizontalBarHeight;

        boolean overVerticalBar = metrics.showVerticalBar && x >= metrics.verticalBarX && x <= metrics.verticalBarX + metrics.verticalBarWidth && y >= metrics.verticalBarY && y <= metrics.verticalBarY + metrics.verticalBarHeight;

        if (overHorizontalBar || overVerticalBar) return requiredBit < 0 || getBit(requiredBit) ? this : null;

        float clipX = getAbsoluteX();
        float clipY = getAbsoluteY() + metrics.horizontalBarHeight;
        float clipWidth = Math.max(0f, getWidth() - metrics.verticalBarWidth);
        float clipHeight = Math.max(0f, getHeight() - metrics.horizontalBarHeight);

        if (x < clipX || x > clipX + clipWidth || y < clipY || y > clipY + clipHeight)
            return requiredBit < 0 || getBit(requiredBit) ? this : null;

        UINode child = super.findNodeAt(x, y, requiredBit);
        if (child != null) return child;

        return requiredBit < 0 || getBit(requiredBit) ? this : null;
    }

    @Override
    public void onMouseMove(MouseMoveEvent event) {
        updateBarHover(screenToWorld(event.getX(), event.getY()));
    }

    @Override
    public void onMouseScroll(MouseScrollEvent event) {
        if (vertical && getMaxScrollY() > 0f) {
            scrollBy(0f, -event.yOffset() * scrollSpeed);
        } else if (horizontal && getMaxScrollX() > 0f) {
            float offset = event.xOffset() != 0f ? event.xOffset() : event.yOffset();
            scrollBy(-offset * scrollSpeed, 0f);
        }
    }

    @Override
    public void onMousePress(MousePressEvent event) {
        Vector2f position = screenToWorld(event.getX(), event.getY());
        float mouseX = position.getX();
        float mouseY = position.getY();

        ScrollMetrics metrics = getScrollMetrics();

        updateBarHover(position);

        draggingHorizontalBar = false;
        draggingVerticalBar = false;

        if (metrics.showHorizontalBar && mouseX >= metrics.horizontalThumbX && mouseX <= metrics.horizontalThumbX + metrics.horizontalThumbWidth && mouseY >= metrics.horizontalThumbY && mouseY <= metrics.horizontalThumbY + metrics.horizontalThumbHeight) {
            draggingHorizontalBar = true;
            setPressed(true);
            return;
        }

        if (metrics.showVerticalBar && mouseX >= metrics.verticalThumbX && mouseX <= metrics.verticalThumbX + metrics.verticalThumbWidth && mouseY >= metrics.verticalThumbY && mouseY <= metrics.verticalThumbY + metrics.verticalThumbHeight) {
            draggingVerticalBar = true;
            setPressed(true);
            return;
        }

        if (metrics.showHorizontalBar && mouseX >= metrics.horizontalBarX && mouseX <= metrics.horizontalBarX + metrics.horizontalBarWidth && mouseY >= metrics.horizontalBarY && mouseY <= metrics.horizontalBarY + metrics.horizontalBarHeight) {
            float maxThumbTravel = Math.max(0f, metrics.horizontalBarWidth - metrics.horizontalThumbWidth);
            if (maxThumbTravel > 0f && getMaxScrollX() > 0f) {
                float thumbLeft = MathUtils.clamp(mouseX - metrics.horizontalBarX - metrics.horizontalThumbWidth * 0.5f, 0f, maxThumbTravel);
                float percent = thumbLeft / maxThumbTravel;
                scrollX(percent * getMaxScrollX());
            }
            draggingHorizontalBar = true;
            setPressed(true);
            return;
        }

        if (metrics.showVerticalBar && mouseX >= metrics.verticalBarX && mouseX <= metrics.verticalBarX + metrics.verticalBarWidth && mouseY >= metrics.verticalBarY && mouseY <= metrics.verticalBarY + metrics.verticalBarHeight) {
            float maxThumbTravel = Math.max(0f, metrics.verticalBarHeight - metrics.verticalThumbHeight);
            if (maxThumbTravel > 0f && getMaxScrollY() > 0f) {
                float thumbTop = MathUtils.clamp(mouseY - metrics.verticalBarY - metrics.verticalThumbHeight * 0.5f, 0f, maxThumbTravel);
                float percent = thumbTop / maxThumbTravel;
                scrollY(percent * getMaxScrollY());
            }
            draggingVerticalBar = true;
            setPressed(true);
        }
    }

    @Override
    public void onMouseDrag(MouseDragEvent event) {
        Vector2f position = screenToWorld(event.getX(), event.getY());
        float mouseX = position.getX();
        float mouseY = position.getY();

        ScrollMetrics metrics = getScrollMetrics();

        updateBarHover(position);

        if (draggingHorizontalBar && metrics.showHorizontalBar) {
            float maxThumbTravel = Math.max(0f, metrics.horizontalBarWidth - metrics.horizontalThumbWidth);
            if (maxThumbTravel > 0f && getMaxScrollX() > 0f) {
                float thumbLeft = MathUtils.clamp(mouseX - metrics.horizontalBarX - metrics.horizontalThumbWidth * 0.5f, 0f, maxThumbTravel);
                float percent = thumbLeft / maxThumbTravel;
                scrollX(percent * getMaxScrollX());
            }
        }

        if (draggingVerticalBar && metrics.showVerticalBar) {
            float maxThumbTravel = Math.max(0f, metrics.verticalBarHeight - metrics.verticalThumbHeight);
            if (maxThumbTravel > 0f && getMaxScrollY() > 0f) {
                float thumbTop = MathUtils.clamp(mouseY - metrics.verticalBarY - metrics.verticalThumbHeight * 0.5f, 0f, maxThumbTravel);
                float percent = thumbTop / maxThumbTravel;
                scrollY(percent * getMaxScrollY());
            }
        }
    }

    @Override
    public void onMouseRelease(MouseReleaseEvent event) {
        draggingHorizontalBar = false;
        draggingVerticalBar = false;
        setPressed(false);
    }

    @Override
    protected void applyLayout() {
        refreshStyleCache();
        super.applyLayout();
    }

    @Override
    public void draw(long vg) {
        if (!isVisible() || vg == 0L) return;

        refreshStyleCache();
        ScrollMetrics metrics = getScrollMetrics();

        float x = getAbsoluteX();
        float y = getAbsoluteY();
        float width = getWidth();
        float height = getHeight();

        nvgBeginPath(vg);
        nvgFillColor(vg, NanoUtility.color1(backgroundColor));
        nvgRoundedRect(vg, x, y, width, height, cornerRadius);
        nvgFill(vg);

        if (content != null) {
            nvgSave(vg);
            nvgIntersectScissor(vg, x, y, width - metrics.verticalBarWidth, height - horizontalBarHeight);
            nvgTranslate(vg, -scrollX, -scrollY);
            super.draw(vg);
            nvgRestore(vg);
        }

        if (metrics.showHorizontalBar && metrics.horizontalBarWidth > 0f) {
            nvgBeginPath(vg);
            nvgFillColor(vg, NanoUtility.color2(horizontalBarBackgroundColor));
            nvgRoundedRect(vg, metrics.horizontalBarX, metrics.horizontalBarY, metrics.horizontalBarWidth, metrics.horizontalBarHeight, metrics.horizontalBarHeight * 0.5f);
            nvgFill(vg);

            Color thumbColor = draggingHorizontalBar ? horizontalBarPressedForegroundColor : hoverHorizontalBar ? horizontalBarHoverForegroundColor : horizontalBarForegroundColor;

            nvgBeginPath(vg);
            nvgFillColor(vg, NanoUtility.color3(thumbColor));
            nvgRoundedRect(vg, metrics.horizontalThumbX, metrics.horizontalThumbY, metrics.horizontalThumbWidth, metrics.horizontalThumbHeight, metrics.horizontalThumbHeight * 0.5f);
            nvgFill(vg);
        }

        if (metrics.showVerticalBar && metrics.verticalBarHeight > 0f) {
            nvgBeginPath(vg);
            nvgFillColor(vg, NanoUtility.color4(verticalBarBackgroundColor));
            nvgRoundedRect(vg, metrics.verticalBarX, metrics.verticalBarY, metrics.verticalBarWidth, metrics.verticalBarHeight, metrics.verticalBarWidth * 0.5f);
            nvgFill(vg);

            Color thumbColor = draggingVerticalBar ? verticalBarPressedForegroundColor : hoverVerticalBar ? verticalBarHoverForegroundColor : verticalBarForegroundColor;

            nvgBeginPath(vg);
            nvgFillColor(vg, NanoUtility.color1(thumbColor));
            nvgRoundedRect(vg, metrics.verticalThumbX, metrics.verticalThumbY, metrics.verticalThumbWidth, metrics.verticalThumbHeight, metrics.verticalThumbWidth * 0.5f);
            nvgFill(vg);
        }

        if (borderWidth > 0f) {
            float inset = borderWidth * 0.5f;
            nvgBeginPath(vg);
            nvgStrokeWidth(vg, borderWidth);
            nvgStrokeColor(vg, NanoUtility.color2(borderColor));
            nvgRoundedRect(vg, x - inset, y - inset, (width + inset * 2), (height + inset), cornerRadius);
            nvgStroke(vg);
        }
    }

    @Override
    protected void afterLayout() {
        super.afterLayout();
        scrollX = horizontal ? clampScrollX(targetScrollX) : 0f;
        scrollY = vertical ? clampScrollY(targetScrollY) : 0f;
        targetScrollX = scrollX;
        targetScrollY = scrollY;
    }

    private void updateBarHover(Vector2f position) {
        if (position == null) {
            hoverHorizontalBar = false;
            hoverVerticalBar = false;
            return;
        }

        ScrollMetrics metrics = getScrollMetrics();
        float x = position.getX();
        float y = position.getY();

        hoverHorizontalBar = metrics.showHorizontalBar && x >= metrics.horizontalBarX && x <= metrics.horizontalBarX + metrics.horizontalBarWidth && y >= metrics.horizontalBarY && y <= metrics.horizontalBarY + metrics.horizontalBarHeight;

        hoverVerticalBar = metrics.showVerticalBar && x >= metrics.verticalBarX && x <= metrics.verticalBarX + metrics.verticalBarWidth && y >= metrics.verticalBarY && y <= metrics.verticalBarY + metrics.verticalBarHeight;
    }

    private ScrollMetrics getScrollMetrics() {
        float maxScrollX = getMaxScrollX();
        float maxScrollY = getMaxScrollY();

        metrics.showHorizontalBar = drawHorizontalBar && horizontal && maxScrollX > 0f;
        metrics.showVerticalBar = drawVerticalBar && vertical && maxScrollY > 0f;

        metrics.horizontalBarHeight = metrics.showHorizontalBar ? horizontalBarHeight : 0f;
        metrics.verticalBarWidth = metrics.showVerticalBar ? verticalBarWidth : 0f;

        if (metrics.showHorizontalBar) {
            metrics.horizontalBarX = getAbsoluteX() + barPadding;
            metrics.horizontalBarY = getAbsoluteY() + getHeight() - metrics.horizontalBarHeight;
            metrics.horizontalBarWidth = getWidth() - metrics.verticalBarWidth;
            metrics.horizontalBarHeight = horizontalBarHeight;

            float visibleWidth = getWidth();
            float contentWidth = content != null ? content.getWidth() : 0f;

            metrics.horizontalThumbHeight = horizontalBarHeight;
            metrics.horizontalThumbWidth = Math.min(metrics.horizontalBarWidth, Math.max(minThumbSize, metrics.horizontalBarWidth * (visibleWidth / Math.max(visibleWidth, contentWidth))));

            float maxThumbTravel = Math.max(0f, metrics.horizontalBarWidth - metrics.horizontalThumbWidth);
            metrics.horizontalThumbX = metrics.horizontalBarX + (maxScrollX <= 0f ? 0f : (scrollX / maxScrollX) * maxThumbTravel);
            metrics.horizontalThumbY = metrics.horizontalBarY;
        } else {
            metrics.horizontalBarX = 0f;
            metrics.horizontalBarY = 0f;
            metrics.horizontalBarWidth = 0f;
            metrics.horizontalBarHeight = 0f;
            metrics.horizontalThumbX = 0f;
            metrics.horizontalThumbY = 0f;
            metrics.horizontalThumbWidth = 0f;
            metrics.horizontalThumbHeight = 0f;
        }

        if (metrics.showVerticalBar) {
            metrics.verticalBarWidth = verticalBarWidth;
            metrics.verticalBarHeight = getHeight() - metrics.horizontalBarHeight;
            metrics.verticalBarX = getAbsoluteX() + getWidth() - metrics.verticalBarWidth;
            metrics.verticalBarY = getAbsoluteY();

            float visibleHeight = getHeight();
            float contentHeight = content != null ? content.getHeight() : 0f;

            metrics.verticalThumbWidth = verticalBarWidth;
            metrics.verticalThumbHeight = Math.min(metrics.verticalBarHeight, Math.max(minThumbSize, metrics.verticalBarHeight * (visibleHeight / Math.max(visibleHeight, contentHeight))));

            float maxThumbTravel = Math.max(0f, metrics.verticalBarHeight - metrics.verticalThumbHeight);
            metrics.verticalThumbX = metrics.verticalBarX;
            metrics.verticalThumbY = metrics.verticalBarY + (maxScrollY <= 0f ? 0f : (scrollY / maxScrollY) * maxThumbTravel);
        } else {
            metrics.verticalBarX = 0f;
            metrics.verticalBarY = 0f;
            metrics.verticalBarWidth = 0f;
            metrics.verticalBarHeight = 0f;
            metrics.verticalThumbX = 0f;
            metrics.verticalThumbY = 0f;
            metrics.verticalThumbWidth = 0f;
            metrics.verticalThumbHeight = 0f;
        }

        return metrics;
    }

    private void refreshStyleCache() {
        ResolvedStyle style = getStyle();

        backgroundColor = new Color(0xFF242424);
        borderColor = new Color(0xFF000000);
        borderWidth = 1f;

        horizontalBarBackgroundColor = new Color(0xFF2A2A2A);
        horizontalBarForegroundColor = new Color(0xFF5A5A5A);
        horizontalBarHoverForegroundColor = new Color(0xFF6A6A6A);
        horizontalBarPressedForegroundColor = new Color(0xFF7A7A7A);

        verticalBarBackgroundColor = new Color(0xFF2A2A2A);
        verticalBarForegroundColor = new Color(0xFF5A5A5A);
        verticalBarHoverForegroundColor = new Color(0xFF6A6A6A);
        verticalBarPressedForegroundColor = new Color(0xFF7A7A7A);

        horizontalBarHeight = 8f;
        verticalBarWidth = 8f;
        barPadding = 4f;
        minThumbSize = 18f;
        cornerRadius = 6f;

        if (style == null) return;

        Color resolvedBackgroundColor = style.get(BACKGROUND_COLOR_KEY);
        Color resolvedBorderColor = style.get(BORDER_COLOR_KEY);

        Color resolvedHorizontalBarBackgroundColor = style.get(HORIZONTAL_BAR_BACKGROUND_COLOR_KEY);
        Color resolvedHorizontalBarForegroundColor = style.get(HORIZONTAL_BAR_FOREGROUND_COLOR_KEY);
        Color resolvedHorizontalBarHoverForegroundColor = style.get(HORIZONTAL_BAR_HOVER_FOREGROUND_COLOR_KEY);
        Color resolvedHorizontalBarPressedForegroundColor = style.get(HORIZONTAL_BAR_PRESSED_FOREGROUND_COLOR_KEY);

        Color resolvedVerticalBarBackgroundColor = style.get(VERTICAL_BAR_BACKGROUND_COLOR_KEY);
        Color resolvedVerticalBarForegroundColor = style.get(VERTICAL_BAR_FOREGROUND_COLOR_KEY);
        Color resolvedVerticalBarHoverForegroundColor = style.get(VERTICAL_BAR_HOVER_FOREGROUND_COLOR_KEY);
        Color resolvedVerticalBarPressedForegroundColor = style.get(VERTICAL_BAR_PRESSED_FOREGROUND_COLOR_KEY);

        Float resolvedBorderWidth = style.get(BORDER_WIDTH_KEY);
        Float resolvedHorizontalBarHeight = style.get(HORIZONTAL_BAR_HEIGHT_KEY);
        Float resolvedVerticalBarWidth = style.get(VERTICAL_BAR_WIDTH_KEY);
        Float resolvedBarPadding = style.get(BAR_PADDING_KEY);
        Float resolvedMinThumbSize = style.get(MIN_THUMB_SIZE_KEY);
        Float resolvedCornerRadius = style.get(CORNER_RADIUS_KEY);

        if (resolvedBackgroundColor != null) backgroundColor = resolvedBackgroundColor;
        if (resolvedBorderColor != null) borderColor = resolvedBorderColor;

        if (resolvedHorizontalBarBackgroundColor != null)
            horizontalBarBackgroundColor = resolvedHorizontalBarBackgroundColor;
        if (resolvedHorizontalBarForegroundColor != null)
            horizontalBarForegroundColor = resolvedHorizontalBarForegroundColor;
        if (resolvedHorizontalBarHoverForegroundColor != null)
            horizontalBarHoverForegroundColor = resolvedHorizontalBarHoverForegroundColor;
        if (resolvedHorizontalBarPressedForegroundColor != null)
            horizontalBarPressedForegroundColor = resolvedHorizontalBarPressedForegroundColor;

        if (resolvedVerticalBarBackgroundColor != null) verticalBarBackgroundColor = resolvedVerticalBarBackgroundColor;
        if (resolvedVerticalBarForegroundColor != null) verticalBarForegroundColor = resolvedVerticalBarForegroundColor;
        if (resolvedVerticalBarHoverForegroundColor != null)
            verticalBarHoverForegroundColor = resolvedVerticalBarHoverForegroundColor;
        if (resolvedVerticalBarPressedForegroundColor != null)
            verticalBarPressedForegroundColor = resolvedVerticalBarPressedForegroundColor;

        if (resolvedBorderWidth != null) borderWidth = Math.max(0f, resolvedBorderWidth);
        if (resolvedHorizontalBarHeight != null) horizontalBarHeight = Math.max(0f, resolvedHorizontalBarHeight);
        if (resolvedVerticalBarWidth != null) verticalBarWidth = Math.max(0f, resolvedVerticalBarWidth);
        if (resolvedBarPadding != null) barPadding = Math.max(0f, resolvedBarPadding);
        if (resolvedMinThumbSize != null) minThumbSize = Math.max(0f, resolvedMinThumbSize);
        if (resolvedCornerRadius != null) cornerRadius = Math.max(0f, resolvedCornerRadius);
    }

    private float clampScrollX(float value) {
        return MathUtils.clamp(value, 0f, getMaxScrollX());
    }

    private float clampScrollY(float value) {
        return MathUtils.clamp(value, 0f, getMaxScrollY());
    }

    private static final class ScrollMetrics {
        private boolean showHorizontalBar;
        private boolean showVerticalBar;

        private float horizontalBarX;
        private float horizontalBarY;
        private float horizontalBarWidth;
        private float horizontalBarHeight;
        private float horizontalThumbX;
        private float horizontalThumbY;
        private float horizontalThumbWidth;
        private float horizontalThumbHeight;

        private float verticalBarX;
        private float verticalBarY;
        private float verticalBarWidth;
        private float verticalBarHeight;
        private float verticalThumbX;
        private float verticalThumbY;
        private float verticalThumbWidth;
        private float verticalThumbHeight;
    }
}