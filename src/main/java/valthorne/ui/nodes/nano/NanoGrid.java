package valthorne.ui.nodes.nano;

import valthorne.graphics.Color;
import valthorne.ui.LayoutValue;
import valthorne.ui.NanoUtility;
import valthorne.ui.UINode;
import valthorne.ui.theme.ResolvedStyle;
import valthorne.ui.theme.StyleKey;

import static org.lwjgl.nanovg.NanoVG.*;

/**
 * Wrapping NanoVG container with uniform optional cell constraints and state-aware
 * background/border painting. Point-valued cell sizes determine the grid's own
 * fixed extent from the configured column count and child count. Percentage or
 * auto cell dimensions leave the corresponding grid dimension automatic, so
 * actual wrapping remains subject to the layout solver and parent constraints.
 *
 * <p>Non-auto cell sizes overwrite child width/height constraints and disable
 * grow/shrink. Switching a cell dimension back to auto skips those assignments
 * but does not restore each child's previous constraints. Child order is retained
 * and mixed-renderer painting is delegated to the root render context.</p>
 *
 * @author Albert Beaupre
 */
public class NanoGrid extends NanoContainer {

    /**
     * Theme override for the normal background fill; null preserves the local setting.
     */
    public static final StyleKey<Color> BACKGROUND_COLOR_KEY = StyleKey.of("nano.grid.backgroundColor", Color.class, new Color(0xFF242424));
    /**
     * Theme override for the hovered background fill; null preserves the local setting.
     */
    public static final StyleKey<Color> HOVER_BACKGROUND_COLOR_KEY = StyleKey.of("nano.grid.hoverBackgroundColor", Color.class, new Color(0xFF242424));
    /**
     * Theme override for the focused background fill; null preserves the local setting.
     */
    public static final StyleKey<Color> FOCUSED_BACKGROUND_COLOR_KEY = StyleKey.of("nano.grid.focusedBackgroundColor", Color.class, new Color(0xFF242424));
    /**
     * Theme override for the pressed background fill; null preserves the local setting.
     */
    public static final StyleKey<Color> PRESSED_BACKGROUND_COLOR_KEY = StyleKey.of("nano.grid.pressedBackgroundColor", Color.class, new Color(0xFF242424));
    /**
     * Theme override for the disabled background fill; null preserves the local setting.
     */
    public static final StyleKey<Color> DISABLED_BACKGROUND_COLOR_KEY = StyleKey.of("nano.grid.disabledBackgroundColor", Color.class, new Color(0xFF242424));

    /**
     * Theme override for the normal border stroke; null preserves the local setting.
     */
    public static final StyleKey<Color> BORDER_COLOR_KEY = StyleKey.of("nano.grid.borderColor", Color.class, new Color(0xFF242424));
    /**
     * Theme override for the hovered border stroke; null preserves the local setting.
     */
    public static final StyleKey<Color> HOVER_BORDER_COLOR_KEY = StyleKey.of("nano.grid.hoverBorderColor", Color.class, new Color(0xFF242424));
    /**
     * Theme override for the focused border stroke; null preserves the local setting.
     */
    public static final StyleKey<Color> FOCUSED_BORDER_COLOR_KEY = StyleKey.of("nano.grid.focusedBorderColor", Color.class, new Color(0xFF242424));
    /**
     * Theme override for the pressed border stroke; null preserves the local setting.
     */
    public static final StyleKey<Color> PRESSED_BORDER_COLOR_KEY = StyleKey.of("nano.grid.pressedBorderColor", Color.class, new Color(0xFF242424));
    /**
     * Theme override for the disabled border stroke; null preserves the local setting.
     */
    public static final StyleKey<Color> DISABLED_BORDER_COLOR_KEY = StyleKey.of("nano.grid.disabledBorderColor", Color.class, new Color(0xFF242424));

    /**
     * Theme corner radius in UI units, defaulting to six.
     */
    public static final StyleKey<Float> CORNER_RADIUS_KEY = StyleKey.of("nano.grid.cornerRadius", Float.class, 6f);
    /**
     * Theme border stroke width in UI units, defaulting to one.
     */
    public static final StyleKey<Float> BORDER_WIDTH_KEY = StyleKey.of("nano.grid.borderWidth", Float.class, 1f);

    private int columns = 1; // Configured column count used for row count and point-based extents.
    private LayoutValue cellWidth = LayoutValue.auto(); // Immutable cell-width specification applied during layout.
    private LayoutValue cellHeight = LayoutValue.auto(); // Immutable cell-height specification applied during layout.

    private Color backgroundColor = new Color(0xFF242424); // Borrowed normal background fill color used when that state wins precedence.
    private Color hoverBackgroundColor = new Color(0xFF242424); // Borrowed hovered background fill color used when that state wins precedence.
    private Color focusedBackgroundColor = new Color(0xFF242424); // Borrowed focused background fill color used when that state wins precedence.
    private Color pressedBackgroundColor = new Color(0xFF242424); // Borrowed pressed background fill color used when that state wins precedence.
    private Color disabledBackgroundColor = new Color(0xFF242424); // Borrowed disabled background fill color used when that state wins precedence.

    private Color borderColor = new Color(0xFF242424); // Borrowed normal border stroke color used when that state wins precedence.
    private Color hoverBorderColor = new Color(0xFF242424); // Borrowed hovered border stroke color used when that state wins precedence.
    private Color focusedBorderColor = new Color(0xFF242424); // Borrowed focused border stroke color used when that state wins precedence.
    private Color pressedBorderColor = new Color(0xFF242424); // Borrowed pressed border stroke color used when that state wins precedence.
    private Color disabledBorderColor = new Color(0xFF242424); // Borrowed disabled border stroke color used when that state wins precedence.

    private float cornerRadius = 6f; // Rounded background radius in UI units.
    private float borderWidth = 1f; // Inset stroke width in UI units; zero disables the border.

    /**
     * Creates a wrapping row layout with centered content, one configured column,
     * and automatic cell dimensions. Child geometry is assigned during layout.
     */
    public NanoGrid() {
        getLayout().row().wrap().centerContent();
    }

    /**
     * Reads the column count used to calculate fixed grid extents. Actual wrapping
     * with automatic or percentage widths still depends on available layout space.
     *
     * @return configured positive column count
     */
    public int getColumns() {
        return columns;
    }

    /**
     * Sets the positive column count and marks layout dirty only when it changes.
     * Existing child order and per-cell dimensions are retained.
     *
     * @param columns requested number of columns
     * @return this grid
     * @throws IllegalArgumentException if columns is below one
     */
    public NanoGrid columns(int columns) {
        if (columns < 1)
            throw new IllegalArgumentException("columns must be at least 1");

        if (this.columns == columns)
            return this;

        this.columns = columns;
        markLayoutDirty();
        return this;
    }

    /**
     * Returns the immutable cell-width specification, without resolving percentages
     * or automatic sizing against available parent space.
     *
     * @return configured width value
     */
    public LayoutValue getCellWidth() {
        return cellWidth;
    }

    /**
     * Stores a non-null width specification and marks layout dirty. Non-auto values
     * will replace child width/min/max/basis and disable child flex growth/shrink.
     * Auto skips future assignment without restoring old child constraints.
     *
     * @param cellWidth immutable cell-width specification
     * @return this grid
     * @throws NullPointerException if cellWidth is null
     */
    public NanoGrid cellWidth(LayoutValue cellWidth) {
        if (cellWidth == null)
            throw new NullPointerException("cellWidth");

        this.cellWidth = cellWidth;
        markLayoutDirty();
        return this;
    }

    /**
     * Stores a point-valued cell width and marks layout dirty. The next layout fixes
     * the grid width from occupied columns and column gaps. No numeric validation
     * is performed by this convenience setter.
     *
     * @param cellWidth requested fixed width in UI units
     * @return this grid
     */
    public NanoGrid cellWidth(float cellWidth) {
        this.cellWidth = LayoutValue.points(cellWidth);
        markLayoutDirty();
        return this;
    }

    /**
     * Returns the immutable cell-height specification used during grid layout.
     * Automatic sizing leaves each child's existing constraints untouched.
     *
     * @return configured height value
     */
    public LayoutValue getCellHeight() {
        return cellHeight;
    }

    /**
     * Stores a non-null height specification and marks layout dirty. Non-auto values
     * replace child height/min/max and disable growth/shrink; auto does not restore
     * constraints previously installed on the children.
     *
     * @param cellHeight immutable cell-height specification
     * @return this grid
     * @throws NullPointerException if cellHeight is null
     */
    public NanoGrid cellHeight(LayoutValue cellHeight) {
        if (cellHeight == null)
            throw new NullPointerException("cellHeight");

        this.cellHeight = cellHeight;
        markLayoutDirty();
        return this;
    }

    /**
     * Stores a point-valued cell height and marks layout dirty. The next layout fixes
     * grid height from row count and row gaps. No numeric validation is performed.
     *
     * @param cellHeight requested fixed height in UI units
     * @return this grid
     */
    public NanoGrid cellHeight(float cellHeight) {
        this.cellHeight = LayoutValue.points(cellHeight);
        markLayoutDirty();
        return this;
    }

    /**
     * Stores point-valued width and height together and marks layout dirty. Values
     * are retained without validation; nonnegative finite dimensions are expected.
     *
     * @param cellWidth cell width in UI units
     * @param cellHeight cell height in UI units
     * @return this grid
     */
    public NanoGrid cellSize(float cellWidth, float cellHeight) {
        this.cellWidth = LayoutValue.points(cellWidth);
        this.cellHeight = LayoutValue.points(cellHeight);
        markLayoutDirty();
        return this;
    }

    /**
     * Assigns both layout gaps and marks this grid dirty for extent recalculation.
     *
     * @param gap spacing between adjacent rows and columns in UI units
     * @return this grid
     */
    public NanoGrid gap(float gap) {
        getLayout().gap(gap);
        markLayoutDirty();
        return this;
    }

    /**
     * Assigns vertical row spacing and marks grid layout dirty.
     *
     * @param rowGap spacing between wrapped rows in UI units
     * @return this grid
     */
    public NanoGrid rowGap(float rowGap) {
        getLayout().rowGap(rowGap);
        markLayoutDirty();
        return this;
    }

    /**
     * Assigns horizontal column spacing and marks grid layout dirty.
     *
     * @param columnGap spacing between adjacent columns in UI units
     * @return this grid
     */
    public NanoGrid columnGap(float columnGap) {
        getLayout().columnGap(columnGap);
        markLayoutDirty();
        return this;
    }

    /**
     * Retains a non-null color for the normal background fill; null leaves it unchanged.
     * The color is borrowed rather than copied, and resolved styles may replace it.
     *
     * @param color mutable color reference, or null to keep the current value
     * @return this grid
     */
    public NanoGrid backgroundColor(Color color) {
        if (color != null)
            this.backgroundColor = color;
        return this;
    }

    /**
     * Retains a non-null color for the hovered background fill; null leaves it unchanged.
     * The color is borrowed rather than copied, and resolved styles may replace it.
     *
     * @param color mutable color reference, or null to keep the current value
     * @return this grid
     */
    public NanoGrid hoverBackgroundColor(Color color) {
        if (color != null)
            this.hoverBackgroundColor = color;
        return this;
    }

    /**
     * Retains a non-null color for the focused background fill; null leaves it unchanged.
     * The color is borrowed rather than copied, and resolved styles may replace it.
     *
     * @param color mutable color reference, or null to keep the current value
     * @return this grid
     */
    public NanoGrid focusedBackgroundColor(Color color) {
        if (color != null)
            this.focusedBackgroundColor = color;
        return this;
    }

    /**
     * Retains a non-null color for the pressed background fill; null leaves it unchanged.
     * The color is borrowed rather than copied, and resolved styles may replace it.
     *
     * @param color mutable color reference, or null to keep the current value
     * @return this grid
     */
    public NanoGrid pressedBackgroundColor(Color color) {
        if (color != null)
            this.pressedBackgroundColor = color;
        return this;
    }

    /**
     * Retains a non-null color for the disabled background fill; null leaves it unchanged.
     * The color is borrowed rather than copied, and resolved styles may replace it.
     *
     * @param color mutable color reference, or null to keep the current value
     * @return this grid
     */
    public NanoGrid disabledBackgroundColor(Color color) {
        if (color != null)
            this.disabledBackgroundColor = color;
        return this;
    }

    /**
     * Retains a non-null color for the normal border stroke; null leaves it unchanged.
     * The color is borrowed rather than copied, and resolved styles may replace it.
     *
     * @param color mutable color reference, or null to keep the current value
     * @return this grid
     */
    public NanoGrid borderColor(Color color) {
        if (color != null)
            this.borderColor = color;
        return this;
    }

    /**
     * Retains a non-null color for the hovered border stroke; null leaves it unchanged.
     * The color is borrowed rather than copied, and resolved styles may replace it.
     *
     * @param color mutable color reference, or null to keep the current value
     * @return this grid
     */
    public NanoGrid hoverBorderColor(Color color) {
        if (color != null)
            this.hoverBorderColor = color;
        return this;
    }

    /**
     * Retains a non-null color for the focused border stroke; null leaves it unchanged.
     * The color is borrowed rather than copied, and resolved styles may replace it.
     *
     * @param color mutable color reference, or null to keep the current value
     * @return this grid
     */
    public NanoGrid focusedBorderColor(Color color) {
        if (color != null)
            this.focusedBorderColor = color;
        return this;
    }

    /**
     * Retains a non-null color for the pressed border stroke; null leaves it unchanged.
     * The color is borrowed rather than copied, and resolved styles may replace it.
     *
     * @param color mutable color reference, or null to keep the current value
     * @return this grid
     */
    public NanoGrid pressedBorderColor(Color color) {
        if (color != null)
            this.pressedBorderColor = color;
        return this;
    }

    /**
     * Retains a non-null color for the disabled border stroke; null leaves it unchanged.
     * The color is borrowed rather than copied, and resolved styles may replace it.
     *
     * @param color mutable color reference, or null to keep the current value
     * @return this grid
     */
    public NanoGrid disabledBorderColor(Color color) {
        if (color != null)
            this.disabledBorderColor = color;
        return this;
    }

    /**
     * Sets the background corner radius, clamping negative values to zero.
     * Does not mark layout dirty; a later resolved style can overwrite this value.
     *
     * @param cornerRadius requested radius in UI units; supply a finite value
     * @return this grid
     */
    public NanoGrid cornerRadius(float cornerRadius) {
        this.cornerRadius = Math.max(0f, cornerRadius);
        return this;
    }

    /**
     * Sets the inset border stroke width, clamping negative values to zero.
     * Zero omits the border. A later resolved style can overwrite this value.
     *
     * @param borderWidth requested stroke width in UI units; supply a finite value
     * @return this grid
     */
    public NanoGrid borderWidth(float borderWidth) {
        this.borderWidth = Math.max(0f, borderWidth);
        return this;
    }

    /**
     * Applies resolved paint settings, constrains each child for non-auto cell sizes,
     * and computes fixed point-based grid extents including gaps. Non-point grid
     * dimensions are reset to auto. Counts all children, including invisible entries,
     * and then delegates normal container layout.
     */
    @Override
    protected void applyLayout() {
        ResolvedStyle style = getStyle();

        if (style != null) {
            Color resolvedBackgroundColor = style.get(BACKGROUND_COLOR_KEY);
            Color resolvedHoverBackgroundColor = style.get(HOVER_BACKGROUND_COLOR_KEY);
            Color resolvedFocusedBackgroundColor = style.get(FOCUSED_BACKGROUND_COLOR_KEY);
            Color resolvedPressedBackgroundColor = style.get(PRESSED_BACKGROUND_COLOR_KEY);
            Color resolvedDisabledBackgroundColor = style.get(DISABLED_BACKGROUND_COLOR_KEY);

            Color resolvedBorderColor = style.get(BORDER_COLOR_KEY);
            Color resolvedHoverBorderColor = style.get(HOVER_BORDER_COLOR_KEY);
            Color resolvedFocusedBorderColor = style.get(FOCUSED_BORDER_COLOR_KEY);
            Color resolvedPressedBorderColor = style.get(PRESSED_BORDER_COLOR_KEY);
            Color resolvedDisabledBorderColor = style.get(DISABLED_BORDER_COLOR_KEY);

            Float resolvedCornerRadius = style.get(CORNER_RADIUS_KEY);
            Float resolvedBorderWidth = style.get(BORDER_WIDTH_KEY);

            if (resolvedBackgroundColor != null)
                backgroundColor = resolvedBackgroundColor;
            if (resolvedHoverBackgroundColor != null)
                hoverBackgroundColor = resolvedHoverBackgroundColor;
            if (resolvedFocusedBackgroundColor != null)
                focusedBackgroundColor = resolvedFocusedBackgroundColor;
            if (resolvedPressedBackgroundColor != null)
                pressedBackgroundColor = resolvedPressedBackgroundColor;
            if (resolvedDisabledBackgroundColor != null)
                disabledBackgroundColor = resolvedDisabledBackgroundColor;

            if (resolvedBorderColor != null)
                borderColor = resolvedBorderColor;
            if (resolvedHoverBorderColor != null)
                hoverBorderColor = resolvedHoverBorderColor;
            if (resolvedFocusedBorderColor != null)
                focusedBorderColor = resolvedFocusedBorderColor;
            if (resolvedPressedBorderColor != null)
                pressedBorderColor = resolvedPressedBorderColor;
            if (resolvedDisabledBorderColor != null)
                disabledBorderColor = resolvedDisabledBorderColor;

            if (resolvedCornerRadius != null)
                cornerRadius = Math.max(0f, resolvedCornerRadius);
            if (resolvedBorderWidth != null)
                borderWidth = Math.max(0f, resolvedBorderWidth);
        }

        int childCount = size();
        int usedColumns = childCount == 0 ? 0 : Math.min(columns, childCount);
        int rows = childCount == 0 ? 0 : (childCount + columns - 1) / columns;

        float horizontalGap = getLayout().getColumnGap();
        float verticalGap = getLayout().getRowGap();

        for (int i = 0; i < childCount; i++) {
            UINode child = get(i);

            if (!cellWidth.isAuto()) {
                child.getLayout()
                        .width(cellWidth)
                        .minWidth(cellWidth)
                        .maxWidth(cellWidth)
                        .flexBasis(cellWidth)
                        .noGrow()
                        .noShrink();
            }

            if (!cellHeight.isAuto()) {
                child.getLayout()
                        .height(cellHeight)
                        .minHeight(cellHeight)
                        .maxHeight(cellHeight)
                        .noGrow()
                        .noShrink();
            }
        }

        if (!cellWidth.isAuto() && cellWidth.isPoints()) {
            float totalWidth = usedColumns == 0 ? 0f : (usedColumns * cellWidth.getValue()) + Math.max(0, usedColumns - 1) * horizontalGap;
            getLayout()
                    .width(totalWidth)
                    .minWidth(totalWidth)
                    .maxWidth(totalWidth);
        } else {
            getLayout()
                    .widthAuto()
                    .minWidthAuto()
                    .maxWidthAuto();
        }

        if (!cellHeight.isAuto() && cellHeight.isPoints()) {
            float totalHeight = rows == 0 ? 0f : (rows * cellHeight.getValue()) + Math.max(0, rows - 1) * verticalGap;
            getLayout()
                    .height(totalHeight)
                    .minHeight(totalHeight)
                    .maxHeight(totalHeight);
        } else {
            getLayout()
                    .heightAuto()
                    .minHeightAuto()
                    .maxHeightAuto();
        }

        super.applyLayout();
    }

    /**
     * Paints background and optional inset border using disabled, pressed, focused,
     * then hovered color precedence. Draws children through root-managed mixed-backend
     * traversal afterward. Requires a valid context and enclosing root dispatch.
     *
     * @param vg borrowed active NanoVG context
     */
    @Override
    public void draw(long vg) {
        Color drawBackground = backgroundColor;
        Color drawBorder = borderColor;

        if (!isEnabled()) {
            drawBackground = disabledBackgroundColor;
            drawBorder = disabledBorderColor;
        } else if (isPressed()) {
            drawBackground = pressedBackgroundColor;
            drawBorder = pressedBorderColor;
        } else if (isFocused()) {
            drawBackground = focusedBackgroundColor;
            drawBorder = focusedBorderColor;
        } else if (isHovered()) {
            drawBackground = hoverBackgroundColor;
            drawBorder = hoverBorderColor;
        }

        float x = getAbsoluteX();
        float y = getAbsoluteY();
        float width = getWidth();
        float height = getHeight();

        nvgBeginPath(vg);
        nvgFillColor(vg, NanoUtility.color1(drawBackground));
        nvgRoundedRect(vg, x, y, width, height, cornerRadius);
        nvgFill(vg);

        if (borderWidth > 0f) {
            float inset = borderWidth * 0.5f;
            nvgBeginPath(vg);
            nvgStrokeWidth(vg, borderWidth);
            nvgStrokeColor(vg, NanoUtility.color2(drawBorder));
            nvgRoundedRect(vg, x + inset, y + inset, Math.max(0f, width - borderWidth), Math.max(0f, height - borderWidth), Math.max(0f, cornerRadius - inset));
            nvgStroke(vg);
        }

        super.draw(vg);
    }
}
