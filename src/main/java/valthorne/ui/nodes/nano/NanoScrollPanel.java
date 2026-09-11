package valthorne.ui.nodes.nano;

import valthorne.event.events.*;
import valthorne.graphics.Color;
import valthorne.graphics.texture.TextureBatch;
import valthorne.math.MathUtils;
import org.joml.Vector2f;
import valthorne.ui.NanoUtility;
import valthorne.ui.UINode;
import valthorne.ui.theme.ResolvedStyle;
import valthorne.ui.theme.StyleKey;

import static org.lwjgl.nanovg.NanoVG.*;

/**
 * Scrollable NanoVG container with one managed content node and optional horizontal
 * and vertical scrollbars. Offsets apply immediately; target fields retain the
 * same requested positions for post-layout clamping rather than driving animation.
 * Rendering clips and translates children through the shared root context, allowing
 * mixed NanoVG and texture content to scroll together.
 *
 * <p>Scrollbar metrics reuse a mutable cache. Maximum offsets include the cached
 * opposite scrollbar thickness, so range getters reflect the current metric cache
 * rather than independently solving both bar visibilities. Use the normal layout
 * and draw lifecycle after content/style changes. Configure the content through
 * setContent rather than adding unrelated children to this container.</p>
 *
 * <pre>{@code
 * NanoScrollPanel scroll = new NanoScrollPanel();
 * scroll.getLayout().width(400).height(250);
 * NanoPanel body = new NanoPanel();
 * body.getLayout().width(700).height(600);
 * scroll.setContent(body);
 * // After the root has laid out content:
 * scroll.scroll(80, 120);
 * }</pre>
 *
 * @author Albert Beaupre
 */
public class NanoScrollPanel extends NanoContainer {

    /**
     * Theme background color for scrollbar painting; sizes use UI units.
     */
    public static final StyleKey<Color> BACKGROUND_COLOR_KEY = StyleKey.of("nano.scrollpanel.backgroundColor", Color.class, new Color(0xFF2A2A2A));
    /**
     * Theme border color for scrollbar painting; sizes use UI units.
     */
    public static final StyleKey<Color> BORDER_COLOR_KEY = StyleKey.of("nano.scrollpanel.borderColor", Color.class, new Color(0xFF000000));
    /**
     * Theme border width for scrollbar painting; sizes use UI units.
     */
    public static final StyleKey<Float> BORDER_WIDTH_KEY = StyleKey.of("nano.scrollpanel.borderWidth", Float.class, 2f);

    /**
     * Theme horizontal bar background color for scrollbar painting; sizes use UI units.
     */
    public static final StyleKey<Color> HORIZONTAL_BAR_BACKGROUND_COLOR_KEY = StyleKey.of("nano.scrollpanel.horizontalBarBackgroundColor", Color.class, new Color(0xFF2A2A2A));
    /**
     * Theme horizontal bar foreground color for scrollbar painting; sizes use UI units.
     */
    public static final StyleKey<Color> HORIZONTAL_BAR_FOREGROUND_COLOR_KEY = StyleKey.of("nano.scrollpanel.horizontalBarForegroundColor", Color.class, new Color(0xFF5A5A5A));
    /**
     * Theme horizontal bar hover foreground color for scrollbar painting; sizes use UI units.
     */
    public static final StyleKey<Color> HORIZONTAL_BAR_HOVER_FOREGROUND_COLOR_KEY = StyleKey.of("nano.scrollpanel.horizontalBarHoverForegroundColor", Color.class, new Color(0xFF6A6A6A));
    /**
     * Theme horizontal bar pressed foreground color for scrollbar painting; sizes use UI units.
     */
    public static final StyleKey<Color> HORIZONTAL_BAR_PRESSED_FOREGROUND_COLOR_KEY = StyleKey.of("nano.scrollpanel.horizontalBarPressedForegroundColor", Color.class, new Color(0xFF7A7A7A));

    /**
     * Theme vertical bar background color for scrollbar painting; sizes use UI units.
     */
    public static final StyleKey<Color> VERTICAL_BAR_BACKGROUND_COLOR_KEY = StyleKey.of("nano.scrollpanel.verticalBarBackgroundColor", Color.class, new Color(0xFF2A2A2A));
    /**
     * Theme vertical bar foreground color for scrollbar painting; sizes use UI units.
     */
    public static final StyleKey<Color> VERTICAL_BAR_FOREGROUND_COLOR_KEY = StyleKey.of("nano.scrollpanel.verticalBarForegroundColor", Color.class, new Color(0xFF5A5A5A));
    /**
     * Theme vertical bar hover foreground color for scrollbar painting; sizes use UI units.
     */
    public static final StyleKey<Color> VERTICAL_BAR_HOVER_FOREGROUND_COLOR_KEY = StyleKey.of("nano.scrollpanel.verticalBarHoverForegroundColor", Color.class, new Color(0xFF6A6A6A));
    /**
     * Theme vertical bar pressed foreground color for scrollbar painting; sizes use UI units.
     */
    public static final StyleKey<Color> VERTICAL_BAR_PRESSED_FOREGROUND_COLOR_KEY = StyleKey.of("nano.scrollpanel.verticalBarPressedForegroundColor", Color.class, new Color(0xFF7A7A7A));

    /**
     * Theme horizontal bar height for scrollbar painting; sizes use UI units.
     */
    public static final StyleKey<Float> HORIZONTAL_BAR_HEIGHT_KEY = StyleKey.of("nano.scrollpanel.horizontalBarHeight", Float.class, 16f);
    /**
     * Theme vertical bar width for scrollbar painting; sizes use UI units.
     */
    public static final StyleKey<Float> VERTICAL_BAR_WIDTH_KEY = StyleKey.of("nano.scrollpanel.verticalBarWidth", Float.class, 16f);
    /**
     * Theme bar padding for scrollbar painting; sizes use UI units.
     */
    public static final StyleKey<Float> BAR_PADDING_KEY = StyleKey.of("nano.scrollpanel.barPadding", Float.class, 4f);
    /**
     * Theme min thumb size for scrollbar painting; sizes use UI units.
     */
    public static final StyleKey<Float> MIN_THUMB_SIZE_KEY = StyleKey.of("nano.scrollpanel.minThumbSize", Float.class, 18f);
    /**
     * Theme corner radius for scrollbar painting; sizes use UI units.
     */
    public static final StyleKey<Float> CORNER_RADIUS_KEY = StyleKey.of("nano.scrollpanel.cornerRadius", Float.class, 6f);
    private final ScrollMetrics metrics = new ScrollMetrics(); // Reused metric cache shared by range, input, and paint calculations.
    private UINode content = new NanoPanel(); // Managed content node whose dimensions determine overflow.
    private boolean horizontal = true; // Whether horizontal offset changes are allowed.
    private boolean vertical = true; // Whether vertical offset changes are allowed.
    private boolean drawHorizontalBar = true; // Whether an overflowing horizontal axis may display its bar.
    private boolean drawVerticalBar = true; // Whether an overflowing vertical axis may display its bar.
    private float scrollX; // Applied horizontal offset in UI units.
    private float scrollY; // Applied vertical offset in UI units.
    private float targetScrollX; // Requested X offset retained for post-layout clamping.
    private float targetScrollY; // Requested Y offset retained for post-layout clamping.
    private float scrollSpeed = 32f; // Wheel movement multiplier in UI units per offset unit.
    private boolean draggingHorizontalBar; // Whether pointer motion controls the horizontal thumb.
    private boolean draggingVerticalBar; // Whether pointer motion controls the vertical thumb.
    private boolean hoverHorizontalBar; // Whether the pointer lies over the horizontal track.
    private boolean hoverVerticalBar; // Whether the pointer lies over the vertical track.
    private Color backgroundColor = new Color(0xFF242424); // Current background color, borrowed from style or locally created fallback.
    private Color borderColor = new Color(0xFF000000); // Current border color, borrowed from style or locally created fallback.
    private Color horizontalBarBackgroundColor = new Color(0xFF2A2A2A); // Current horizontal bar background color, borrowed from style or locally created fallback.
    private Color horizontalBarForegroundColor = new Color(0xFF5A5A5A); // Current horizontal bar foreground color, borrowed from style or locally created fallback.
    private Color horizontalBarHoverForegroundColor = new Color(0xFF6A6A6A); // Current horizontal bar hover foreground color, borrowed from style or locally created fallback.
    private Color horizontalBarPressedForegroundColor = new Color(0xFF7A7A7A); // Current horizontal bar pressed foreground color, borrowed from style or locally created fallback.
    private Color verticalBarBackgroundColor = new Color(0xFF2A2A2A); // Current vertical bar background color, borrowed from style or locally created fallback.
    private Color verticalBarForegroundColor = new Color(0xFF5A5A5A); // Current vertical bar foreground color, borrowed from style or locally created fallback.
    private Color verticalBarHoverForegroundColor = new Color(0xFF6A6A6A); // Current vertical bar hover foreground color, borrowed from style or locally created fallback.
    private Color verticalBarPressedForegroundColor = new Color(0xFF7A7A7A); // Current vertical bar pressed foreground color, borrowed from style or locally created fallback.
    private float borderWidth = 1f; // Border stroke width in UI units.
    private float horizontalBarHeight = 8f; // Visible horizontal track thickness in UI units.
    private float verticalBarWidth = 8f; // Visible vertical track thickness in UI units.
    private float barPadding = 0f; // Horizontal track X inset in UI units.
    private float minThumbSize = 18f; // Requested minimum thumb length, capped to available track length.
    private float cornerRadius = 6f; // Background and border corner radius in UI units.

    /**
     * Creates a scrollable container with an attached empty NanoPanel as content.
     * Both axes and scrollbar painting start enabled; initial offsets are zero.
     */
    public NanoScrollPanel() {
        setBit(SCROLLABLE_BIT, true);
        add(content);
    }

    /**
     * Returns the managed content node whose laid-out dimensions define scroll range.
     * The node is live and may be configured through its own layout/child APIs.
     *
     * @return managed content, or null after clearing
     */
    public UINode getContent() {
        return content;
    }

    /**
     * Replaces managed content after rejecting an already-parented replacement.
     * Assigning the same node is a no-op; null removes content. Offsets are not reset
     * here and are reconciled by the normal layout lifecycle.
     *
     * @param child fresh unattached content, or null to clear
     * @throws IllegalStateException if a different replacement already has a parent
     */
    public void setContent(UINode child) {
        if (child == content) return;
        if (child != null && child.getParent() != null)
            throw new IllegalStateException("Content already has a parent.");
        remove(this.content);
        if (child == null) {
            this.content = null;
            return;
        }
        this.content = child;
        add(child);
        markLayoutDirty();
    }

    /**
     * Stores the wheel-to-offset multiplier without validation or moving content.
     * Negative values reverse wheel direction; callers should supply a finite value.
     *
     * @param speed UI units per wheel offset unit
     * @return this panel
     */
    public NanoScrollPanel scrollSpeed(float speed) {
        this.scrollSpeed = speed;
        return this;
    }

    /**
     * Enables horizontal scrolling or resets both X offsets and horizontal drag/hover
     * flags when disabled. Reenabling clamps existing offsets against the cached range.
     *
     * @param horizontal whether X scrolling is allowed
     * @return this panel
     */
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

    /**
     * Enables vertical scrolling or resets both Y offsets and vertical drag/hover
     * flags when disabled. Reenabling clamps existing offsets against the cached range.
     *
     * @param vertical whether Y scrolling is allowed
     * @return this panel
     */
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

    /**
     * Changes whether an overflowing enabled horizontal axis paints a scrollbar.
     * Does not disable horizontal scrolling or immediately refresh cached metrics.
     *
     * @param drawHorizontalBar requested bar visibility policy
     * @return this panel
     */
    public NanoScrollPanel horizontalBar(boolean drawHorizontalBar) {
        this.drawHorizontalBar = drawHorizontalBar;
        return this;
    }

    /**
     * Changes whether an overflowing enabled vertical axis paints a scrollbar.
     * Does not disable vertical scrolling or immediately refresh cached metrics.
     *
     * @param drawVerticalBar requested bar visibility policy
     * @return this panel
     */
    public NanoScrollPanel verticalBar(boolean drawVerticalBar) {
        this.drawVerticalBar = drawVerticalBar;
        return this;
    }

    /**
     * Reads the applied horizontal offset without recomputing scrollbar metrics.
     *
     * @return current X offset in UI units
     */
    public float getScrollX() {
        return scrollX;
    }

    /**
     * Reads the applied vertical offset without recomputing scrollbar metrics.
     *
     * @return current Y offset in UI units
     */
    public float getScrollY() {
        return scrollY;
    }

    /**
     * Clamps and immediately applies an X offset, or zero when horizontal scrolling
     * is disabled. Uses current content dimensions and cached opposite-bar thickness.
     *
     * @param scrollX finite requested horizontal offset in UI units
     * @return this panel
     */
    public NanoScrollPanel scrollX(float scrollX) {
        targetScrollX = horizontal ? clampScrollX(scrollX) : 0f;
        this.scrollX = targetScrollX;
        return this;
    }

    /**
     * Clamps and immediately applies a Y offset, or zero when vertical scrolling
     * is disabled. Uses current content dimensions and cached opposite-bar thickness.
     *
     * @param scrollY finite requested vertical offset in UI units
     * @return this panel
     */
    public NanoScrollPanel scrollY(float scrollY) {
        targetScrollY = vertical ? clampScrollY(scrollY) : 0f;
        this.scrollY = targetScrollY;
        return this;
    }

    /**
     * Applies each requested axis through its clamping setter without animation.
     * Disabled axes resolve to zero.
     *
     * @param scrollX finite requested horizontal offset
     * @param scrollY finite requested vertical offset
     * @return this panel
     */
    public NanoScrollPanel scroll(float scrollX, float scrollY) {
        scrollX(scrollX);
        scrollY(scrollY);
        return this;
    }

    /**
     * Adds displacements to target offsets on enabled axes, clamps them, and applies
     * the results immediately. Disabled axes are left unchanged.
     *
     * @param dx finite horizontal displacement in UI units
     * @param dy finite vertical displacement in UI units
     * @return this panel
     */
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

    /**
     * Computes nonnegative content-width overflow plus cached vertical-bar width.
     * Returns zero without content and does not refresh bar visibility itself.
     *
     * @return maximum X offset under the current cached metrics
     */
    public float getMaxScrollX() {
        if (content == null) return 0f;
        return Math.max(0f, (content.getWidth() - getWidth()) + metrics.verticalBarWidth);
    }

    /**
     * Computes nonnegative content-height overflow plus cached horizontal-bar height.
     * Returns zero without content and does not refresh bar visibility itself.
     *
     * @return maximum Y offset under the current cached metrics
     */
    public float getMaxScrollY() {
        if (content == null) return 0f;
        return Math.max(0f, (content.getHeight() - getHeight()) + metrics.horizontalBarHeight);
    }

    /**
     * Invalidates inherited style state and immediately rebuilds this panel's local
     * paint cache from defaults and resolved values.
     */
    @Override
    protected void invalidateStyleTree() {
        super.invalidateStyleTree();
        refreshStyleCache();
    }

    /**
     * Maps an unscrolled hit X into content coordinates by adding applied X offset.
     *
     * @param x incoming UI world X
     * @return child-query X corrected for horizontal scrolling
     */
    @Override
    protected float transformChildHitX(float x) {
        return x + scrollX;
    }

    /**
     * Maps an unscrolled hit Y into content coordinates by subtracting applied Y
     * offset in the root's world-coordinate convention.
     *
     * @param y incoming UI world Y
     * @return child-query Y corrected for vertical scrolling
     */
    @Override
    protected float transformChildHitY(float y) {
        return y - scrollY;
    }

    /**
     * Rejects hidden, disabled, and out-of-panel points, then protects scrollbar
     * regions from child hit tests. Queries content only inside the reduced clip area
     * and falls back to this node when its capabilities match. Converts world Y to
     * top-left layout Y for scrollbar comparisons.
     *
     * @param x hit-test world X
     * @param y hit-test world Y
     * @param requiredBit required capability bit, or negative for any
     * @return eligible descendant/panel, or null
     */
    @Override
    public UINode findNodeAt(float x, float y, int requiredBit) {
        if (!isVisible() || !isEnabled() || !contains(x, y)) return null;
        float layoutY = getRenderSpaceHeight() - y;
        ScrollMetrics metrics = getScrollMetrics();

        boolean overHorizontalBar = metrics.showHorizontalBar && x >= metrics.horizontalBarX && x <= metrics.horizontalBarX + metrics.horizontalBarWidth && layoutY >= metrics.horizontalBarY && layoutY <= metrics.horizontalBarY + metrics.horizontalBarHeight;

        boolean overVerticalBar = metrics.showVerticalBar && x >= metrics.verticalBarX && x <= metrics.verticalBarX + metrics.verticalBarWidth && layoutY >= metrics.verticalBarY && layoutY <= metrics.verticalBarY + metrics.verticalBarHeight;

        if (overHorizontalBar || overVerticalBar) return requiredBit < 0 || getBit(requiredBit) ? this : null;

        float clipX = getAbsoluteX();
        float clipY = getAbsoluteY();
        float clipWidth = Math.max(0f, getWidth() - metrics.verticalBarWidth);
        float clipHeight = Math.max(0f, getHeight() - metrics.horizontalBarHeight);

        if (x < clipX || x > clipX + clipWidth || layoutY < clipY || layoutY > clipY + clipHeight)
            return requiredBit < 0 || getBit(requiredBit) ? this : null;

        UINode child = super.findNodeAt(x, y, requiredBit);
        if (child != null) return child;

        return requiredBit < 0 || getBit(requiredBit) ? this : null;
    }

    /**
     * Converts the pointer endpoint to layout coordinates and updates whole-bar hover
     * flags. Hover is based on the entire track, not only the thumb.
     *
     * @param event routed pointer movement
     */
    @Override
    public void onMouseMove(MouseMoveEvent event) {
        updateBarHover(screenToLayout(event.getToX(), event.getToY()));
    }

    /**
     * Runs inherited cancellation and clears both scrollbar dragging flags without
     * synthesizing a release or altering scroll offsets.
     */
    @Override
    public void onPointerCancel() {
        super.onPointerCancel();
        draggingHorizontalBar = false;
        draggingVerticalBar = false;
    }

    /**
     * Delegates precise wheel routing and conditional consumption to ScrollBehavior,
     * then immediately applies the returned offsets. Enabled axes, cached ranges,
     * and configured speed determine whether scrolling can move.
     *
     * @param event routed wheel event
     */
    @Override
    public void onMouseScroll(MouseScrollEvent event) {
        Vector2f next = valthorne.ui.behavior.ScrollBehavior.wheel(event, horizontal, vertical,
                getScrollX(), getScrollY(), getMaxScrollX(), getMaxScrollY(), scrollSpeed);
        scroll(next.x(), next.y());
    }

    /**
     * Handles left presses on visible thumbs or tracks. Thumb presses begin dragging;
     * track presses first center the thumb on the pointer within travel bounds.
     * Sets pressed state for either drag and leaves event consumption to routing.
     *
     * @param event routed pointer press in screen coordinates
     */
    @Override
    public void onMousePress(MousePressEvent event) {
        if (event.getButton() != valthorne.Mouse.LEFT) return;
        Vector2f position = screenToLayout(event.getX(), event.getY());
        float mouseX = position.x();
        float mouseY = position.y();

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

    /**
     * Moves any active visible scrollbar by mapping the pointer to the thumb center
     * and clamping travel. The initial grab offset is not retained; each drag centers
     * the thumb on the pointer. Does not independently filter mouse buttons.
     *
     * @param event routed pointer drag
     */
    @Override
    public void onMouseDrag(MouseDragEvent event) {
        Vector2f position = screenToLayout(event.getToX(), event.getToY());
        float mouseX = position.x();
        float mouseY = position.y();

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

    /**
     * Clears both scrollbar drag flags and pressed state for any delivered release.
     * Does not consume the event or alter offsets.
     *
     * @param event routed pointer release
     */
    @Override
    public void onMouseRelease(MouseReleaseEvent event) {
        draggingHorizontalBar = false;
        draggingVerticalBar = false;
        setPressed(false);
    }

    /**
     * Refreshes local theme paint settings before normal container layout.
     * Post-layout offset clamping is performed separately by afterLayout.
     */
    @Override
    protected void applyLayout() {
        refreshStyleCache();
        super.applyLayout();
    }

    /**
     * Refreshes styles/metrics, paints background, then clips and translates shared
     * child traversal to the content viewport. Translation and scissor are restored
     * in finally before drawing bars and border. Skips hidden nodes and zero handles;
     * requires root attachment when content is present.
     *
     * @param vg borrowed active NanoVG context, or zero to skip
     */
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
            TextureBatch sharedBatch = getRoot().getRenderContext().getBatch();
            sharedBatch.beginScissor(getRenderX() + sharedBatch.getTranslationX(),
                    getRenderY() + sharedBatch.getTranslationY() + metrics.horizontalBarHeight,
                    width - metrics.verticalBarWidth, height - metrics.horizontalBarHeight);
            sharedBatch.pushTranslation(-scrollX, scrollY);
            try {
                super.draw(vg);
            } finally {
                sharedBatch.popTranslation();
                sharedBatch.endScissor();
            }
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

    /**
     * Runs inherited post-layout handling, clamps target offsets for enabled axes,
     * and synchronizes targets with applied positions. Uses current cached bar widths.
     */
    @Override
    protected void afterLayout() {
        super.afterLayout();
        scrollX = horizontal ? clampScrollX(targetScrollX) : 0f;
        scrollY = vertical ? clampScrollY(targetScrollY) : 0f;
        targetScrollX = scrollX;
        targetScrollY = scrollY;
    }

    /**
     * Recomputes inclusive hover tests against visible whole-track rectangles.
     * Null clears both flags without requiring a metric refresh.
     *
     * @param position top-left layout-space pointer coordinates, or null
     */
    private void updateBarHover(Vector2f position) {
        if (position == null) {
            hoverHorizontalBar = false;
            hoverVerticalBar = false;
            return;
        }

        ScrollMetrics metrics = getScrollMetrics();
        float x = position.x();
        float y = position.y();

        hoverHorizontalBar = metrics.showHorizontalBar && x >= metrics.horizontalBarX && x <= metrics.horizontalBarX + metrics.horizontalBarWidth && y >= metrics.horizontalBarY && y <= metrics.horizontalBarY + metrics.horizontalBarHeight;

        hoverVerticalBar = metrics.showVerticalBar && x >= metrics.verticalBarX && x <= metrics.verticalBarX + metrics.verticalBarWidth && y >= metrics.verticalBarY && y <= metrics.verticalBarY + metrics.verticalBarHeight;
    }

    /**
     * Rewrites and returns the shared metric object from current node/content sizes
     * and offsets. Visibility decisions use ranges based on the previously cached
     * opposite-bar thickness before new thicknesses are assigned. Hidden bar/thumbnail
     * geometry is zeroed. Callers must not retain this as an immutable snapshot.
     *
     * @return mutable reused scrollbar geometry
     */
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

    /**
     * Restores local paint defaults, allocating fresh fallback colors, then replaces
     * non-null values from the resolved style. Dimensions are clamped nonnegative.
     * Called during drawing as well as layout/style invalidation; this is not a
     * one-time or allocation-free cache initialization.
     */
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

    /**
     * Clamps a candidate to zero through the current cached horizontal range.
     * No independent finiteness check is performed.
     *
     * @param value requested X offset
     * @return clamped X offset
     */
    private float clampScrollX(float value) {
        return MathUtils.clamp(value, 0f, getMaxScrollX());
    }

    /**
     * Clamps a candidate to zero through the current cached vertical range.
     * No independent finiteness check is performed.
     *
     * @param value requested Y offset
     * @return clamped Y offset
     */
    private float clampScrollY(float value) {
        return MathUtils.clamp(value, 0f, getMaxScrollY());
    }

    /**
     * Reusable top-left layout-space geometry for visible scrollbar tracks and thumbs.
     * One instance belongs to the panel and is overwritten by each metric refresh;
     * all positions and dimensions use UI units. Hidden bars receive zero geometry.
     *
     * @author Albert Beaupre
     */
    private static final class ScrollMetrics {
        private boolean showHorizontalBar; // Whether the horizontal scrollbar is currently shown.
        private boolean showVerticalBar; // Whether the vertical scrollbar is currently shown.

        private float horizontalBarX; // Cached horizontal bar x in top-left layout-space UI units.
        private float horizontalBarY; // Cached horizontal bar y in top-left layout-space UI units.
        private float horizontalBarWidth; // Cached horizontal bar width in top-left layout-space UI units.
        private float horizontalBarHeight; // Cached horizontal bar height in top-left layout-space UI units.
        private float horizontalThumbX; // Cached horizontal thumb x in top-left layout-space UI units.
        private float horizontalThumbY; // Cached horizontal thumb y in top-left layout-space UI units.
        private float horizontalThumbWidth; // Cached horizontal thumb width in top-left layout-space UI units.
        private float horizontalThumbHeight; // Cached horizontal thumb height in top-left layout-space UI units.

        private float verticalBarX; // Cached vertical bar x in top-left layout-space UI units.
        private float verticalBarY; // Cached vertical bar y in top-left layout-space UI units.
        private float verticalBarWidth; // Cached vertical bar width in top-left layout-space UI units.
        private float verticalBarHeight; // Cached vertical bar height in top-left layout-space UI units.
        private float verticalThumbX; // Cached vertical thumb x in top-left layout-space UI units.
        private float verticalThumbY; // Cached vertical thumb y in top-left layout-space UI units.
        private float verticalThumbWidth; // Cached vertical thumb width in top-left layout-space UI units.
        private float verticalThumbHeight; // Cached vertical thumb height in top-left layout-space UI units.
    }
}
