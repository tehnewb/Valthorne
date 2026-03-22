package valthorne.ui.nodes.nano;

import valthorne.graphics.Color;
import valthorne.ui.LayoutValue;
import valthorne.ui.NanoUtility;
import valthorne.ui.UINode;
import valthorne.ui.theme.ResolvedStyle;
import valthorne.ui.theme.StyleKey;

import static org.lwjgl.nanovg.NanoVG.*;

public class NanoGrid extends NanoContainer {

    public static final StyleKey<Color> BACKGROUND_COLOR_KEY = StyleKey.of("nano.grid.backgroundColor", Color.class, new Color(0xFF242424));
    public static final StyleKey<Color> HOVER_BACKGROUND_COLOR_KEY = StyleKey.of("nano.grid.hoverBackgroundColor", Color.class, new Color(0xFF242424));
    public static final StyleKey<Color> FOCUSED_BACKGROUND_COLOR_KEY = StyleKey.of("nano.grid.focusedBackgroundColor", Color.class, new Color(0xFF242424));
    public static final StyleKey<Color> PRESSED_BACKGROUND_COLOR_KEY = StyleKey.of("nano.grid.pressedBackgroundColor", Color.class, new Color(0xFF242424));
    public static final StyleKey<Color> DISABLED_BACKGROUND_COLOR_KEY = StyleKey.of("nano.grid.disabledBackgroundColor", Color.class, new Color(0xFF242424));

    public static final StyleKey<Color> BORDER_COLOR_KEY = StyleKey.of("nano.grid.borderColor", Color.class, new Color(0xFF242424));
    public static final StyleKey<Color> HOVER_BORDER_COLOR_KEY = StyleKey.of("nano.grid.hoverBorderColor", Color.class, new Color(0xFF242424));
    public static final StyleKey<Color> FOCUSED_BORDER_COLOR_KEY = StyleKey.of("nano.grid.focusedBorderColor", Color.class, new Color(0xFF242424));
    public static final StyleKey<Color> PRESSED_BORDER_COLOR_KEY = StyleKey.of("nano.grid.pressedBorderColor", Color.class, new Color(0xFF242424));
    public static final StyleKey<Color> DISABLED_BORDER_COLOR_KEY = StyleKey.of("nano.grid.disabledBorderColor", Color.class, new Color(0xFF242424));

    public static final StyleKey<Float> CORNER_RADIUS_KEY = StyleKey.of("nano.grid.cornerRadius", Float.class, 6f);
    public static final StyleKey<Float> BORDER_WIDTH_KEY = StyleKey.of("nano.grid.borderWidth", Float.class, 1f);

    private int columns = 1;
    private LayoutValue cellWidth = LayoutValue.auto();
    private LayoutValue cellHeight = LayoutValue.auto();

    private Color backgroundColor = new Color(0xFF242424);
    private Color hoverBackgroundColor = new Color(0xFF242424);
    private Color focusedBackgroundColor = new Color(0xFF242424);
    private Color pressedBackgroundColor = new Color(0xFF242424);
    private Color disabledBackgroundColor = new Color(0xFF242424);

    private Color borderColor = new Color(0xFF242424);
    private Color hoverBorderColor = new Color(0xFF242424);
    private Color focusedBorderColor = new Color(0xFF242424);
    private Color pressedBorderColor = new Color(0xFF242424);
    private Color disabledBorderColor = new Color(0xFF242424);

    private float cornerRadius = 6f;
    private float borderWidth = 1f;

    public NanoGrid() {
        getLayout().row().wrap().centerContent();
    }

    public int getColumns() {
        return columns;
    }

    public NanoGrid columns(int columns) {
        if (columns < 1)
            throw new IllegalArgumentException("columns must be at least 1");

        if (this.columns == columns)
            return this;

        this.columns = columns;
        markLayoutDirty();
        return this;
    }

    public LayoutValue getCellWidth() {
        return cellWidth;
    }

    public NanoGrid cellWidth(LayoutValue cellWidth) {
        if (cellWidth == null)
            throw new NullPointerException("cellWidth");

        this.cellWidth = cellWidth;
        markLayoutDirty();
        return this;
    }

    public NanoGrid cellWidth(float cellWidth) {
        this.cellWidth = LayoutValue.points(cellWidth);
        markLayoutDirty();
        return this;
    }

    public LayoutValue getCellHeight() {
        return cellHeight;
    }

    public NanoGrid cellHeight(LayoutValue cellHeight) {
        if (cellHeight == null)
            throw new NullPointerException("cellHeight");

        this.cellHeight = cellHeight;
        markLayoutDirty();
        return this;
    }

    public NanoGrid cellHeight(float cellHeight) {
        this.cellHeight = LayoutValue.points(cellHeight);
        markLayoutDirty();
        return this;
    }

    public NanoGrid cellSize(float cellWidth, float cellHeight) {
        this.cellWidth = LayoutValue.points(cellWidth);
        this.cellHeight = LayoutValue.points(cellHeight);
        markLayoutDirty();
        return this;
    }

    public NanoGrid gap(float gap) {
        getLayout().gap(gap);
        markLayoutDirty();
        return this;
    }

    public NanoGrid rowGap(float rowGap) {
        getLayout().rowGap(rowGap);
        markLayoutDirty();
        return this;
    }

    public NanoGrid columnGap(float columnGap) {
        getLayout().columnGap(columnGap);
        markLayoutDirty();
        return this;
    }

    public NanoGrid backgroundColor(Color color) {
        if (color != null)
            this.backgroundColor = color;
        return this;
    }

    public NanoGrid hoverBackgroundColor(Color color) {
        if (color != null)
            this.hoverBackgroundColor = color;
        return this;
    }

    public NanoGrid focusedBackgroundColor(Color color) {
        if (color != null)
            this.focusedBackgroundColor = color;
        return this;
    }

    public NanoGrid pressedBackgroundColor(Color color) {
        if (color != null)
            this.pressedBackgroundColor = color;
        return this;
    }

    public NanoGrid disabledBackgroundColor(Color color) {
        if (color != null)
            this.disabledBackgroundColor = color;
        return this;
    }

    public NanoGrid borderColor(Color color) {
        if (color != null)
            this.borderColor = color;
        return this;
    }

    public NanoGrid hoverBorderColor(Color color) {
        if (color != null)
            this.hoverBorderColor = color;
        return this;
    }

    public NanoGrid focusedBorderColor(Color color) {
        if (color != null)
            this.focusedBorderColor = color;
        return this;
    }

    public NanoGrid pressedBorderColor(Color color) {
        if (color != null)
            this.pressedBorderColor = color;
        return this;
    }

    public NanoGrid disabledBorderColor(Color color) {
        if (color != null)
            this.disabledBorderColor = color;
        return this;
    }

    public NanoGrid cornerRadius(float cornerRadius) {
        this.cornerRadius = Math.max(0f, cornerRadius);
        return this;
    }

    public NanoGrid borderWidth(float borderWidth) {
        this.borderWidth = Math.max(0f, borderWidth);
        return this;
    }

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