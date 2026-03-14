package valthorne.ui.nodes;

import valthorne.graphics.texture.TextureBatch;
import valthorne.ui.LayoutValue;
import valthorne.ui.UIContainer;
import valthorne.ui.UINode;

/**
 * <p>
 * {@code Grid} is a layout-oriented container that arranges its children in a
 * wrapped row-based grid using the underlying Yoga flex layout system.
 * </p>
 *
 * <p>
 * This node is designed to make it easy to place UI children into evenly sized
 * cells while still relying on the engine's standard layout pipeline. Internally,
 * the grid configures itself as a row-based wrapping container and then, during
 * layout application, pushes width and height constraints into each child based
 * on the configured cell size rules.
 * </p>
 *
 * <p>
 * The grid supports:
 * </p>
 *
 * <ul>
 *     <li>a configurable column count</li>
 *     <li>optional fixed cell width</li>
 *     <li>optional fixed cell height</li>
 *     <li>uniform gap configuration</li>
 *     <li>separate row gap and column gap configuration</li>
 *     <li>automatic container width and height calculation when cell sizes are point-based</li>
 * </ul>
 *
 * <p>
 * When a fixed point-based cell width is used, the grid computes its total width
 * from the number of active columns and the configured horizontal gap. Likewise,
 * when a fixed point-based cell height is used, the grid computes its total height
 * from the number of rows and the configured vertical gap.
 * </p>
 *
 * <p>
 * If cell width or height is set to auto, the grid leaves that dimension to the
 * underlying layout system instead of forcing a fixed measurement.
 * </p>
 *
 * <p>
 * This class is especially useful for icon grids, inventory slots, gallery layouts,
 * menu tiles, or any other UI where children should be laid out in a consistent
 * multi-column arrangement.
 * </p>
 *
 * <h2>Example Usage</h2>
 *
 * <pre>{@code
 * Grid grid = new Grid()
 *         .columns(4)
 *         .cellSize(64, 64)
 *         .gap(8);
 *
 * grid.add(new Button("One"));
 * grid.add(new Button("Two"));
 * grid.add(new Button("Three"));
 * grid.add(new Button("Four"));
 * grid.add(new Button("Five"));
 *
 * int columns = grid.getColumns();
 * LayoutValue width = grid.getCellWidth();
 * LayoutValue height = grid.getCellHeight();
 *
 * grid.update(delta);
 * grid.draw(batch);
 * }</pre>
 *
 * <p>
 * This example demonstrates full usage of the class by configuring columns,
 * assigning cell size and gaps, adding children, querying layout values, and
 * participating in the standard update and draw cycle.
 * </p>
 *
 * @author Albert Beaupre
 * @since March 13th, 2026
 */
public class Grid extends UIContainer {

    private int columns = 1; // Number of columns the grid should use when distributing children
    private LayoutValue cellWidth = LayoutValue.auto(); // Width applied to each child cell when not auto
    private LayoutValue cellHeight = LayoutValue.auto(); // Height applied to each child cell when not auto

    /**
     * <p>
     * Creates a new grid container.
     * </p>
     *
     * <p>
     * The grid is initialized as a row-based wrapping layout with centered content.
     * This gives it grid-like behavior while still using Yoga flexbox rules.
     * </p>
     */
    public Grid() {
        getLayout().row().wrap().centerContent();
    }

    /**
     * <p>
     * Returns the configured number of columns.
     * </p>
     *
     * @return the column count
     */
    public int getColumns() {
        return columns;
    }

    /**
     * <p>
     * Sets the number of columns used by the grid.
     * </p>
     *
     * <p>
     * The value must be at least {@code 1}. If the new value matches the current
     * value, the method returns immediately. Otherwise, layout is marked dirty so
     * the grid can recompute its layout constraints.
     * </p>
     *
     * @param columns the new column count
     * @return this grid
     * @throws IllegalArgumentException if {@code columns} is less than {@code 1}
     */
    public Grid columns(int columns) {
        if (columns < 1)
            throw new IllegalArgumentException("columns must be at least 1");

        if (this.columns == columns)
            return this;

        this.columns = columns;
        markLayoutDirty();
        return this;
    }

    /**
     * <p>
     * Returns the configured cell width rule.
     * </p>
     *
     * @return the cell width layout value
     */
    public LayoutValue getCellWidth() {
        return cellWidth;
    }

    /**
     * <p>
     * Sets the cell width rule using a {@link LayoutValue}.
     * </p>
     *
     * <p>
     * A non-null value is required. This affects how child widths are constrained
     * during layout application.
     * </p>
     *
     * @param cellWidth the new cell width rule
     * @return this grid
     * @throws NullPointerException if {@code cellWidth} is {@code null}
     */
    public Grid cellWidth(LayoutValue cellWidth) {
        if (cellWidth == null)
            throw new NullPointerException("cellWidth");

        this.cellWidth = cellWidth;
        markLayoutDirty();
        return this;
    }

    /**
     * <p>
     * Sets the cell width rule using a fixed point value.
     * </p>
     *
     * @param cellWidth the fixed cell width in points
     * @return this grid
     */
    public Grid cellWidth(float cellWidth) {
        this.cellWidth = LayoutValue.points(cellWidth);
        markLayoutDirty();
        return this;
    }

    /**
     * <p>
     * Returns the configured cell height rule.
     * </p>
     *
     * @return the cell height layout value
     */
    public LayoutValue getCellHeight() {
        return cellHeight;
    }

    /**
     * <p>
     * Sets the cell height rule using a {@link LayoutValue}.
     * </p>
     *
     * <p>
     * A non-null value is required. This affects how child heights are constrained
     * during layout application.
     * </p>
     *
     * @param cellHeight the new cell height rule
     * @return this grid
     * @throws NullPointerException if {@code cellHeight} is {@code null}
     */
    public Grid cellHeight(LayoutValue cellHeight) {
        if (cellHeight == null)
            throw new NullPointerException("cellHeight");

        this.cellHeight = cellHeight;
        markLayoutDirty();
        return this;
    }

    /**
     * <p>
     * Sets the cell height rule using a fixed point value.
     * </p>
     *
     * @param cellHeight the fixed cell height in points
     * @return this grid
     */
    public Grid cellHeight(float cellHeight) {
        this.cellHeight = LayoutValue.points(cellHeight);
        markLayoutDirty();
        return this;
    }

    /**
     * <p>
     * Sets both cell width and cell height using fixed point values.
     * </p>
     *
     * @param cellWidth the fixed cell width in points
     * @param cellHeight the fixed cell height in points
     * @return this grid
     */
    public Grid cellSize(float cellWidth, float cellHeight) {
        this.cellWidth = LayoutValue.points(cellWidth);
        this.cellHeight = LayoutValue.points(cellHeight);
        markLayoutDirty();
        return this;
    }

    /**
     * <p>
     * Sets both row and column gap to the same value.
     * </p>
     *
     * @param gap the uniform gap value
     * @return this grid
     */
    public Grid gap(float gap) {
        getLayout().gap(gap);
        markLayoutDirty();
        return this;
    }

    /**
     * <p>
     * Sets the vertical gap between rows.
     * </p>
     *
     * @param rowGap the row gap value
     * @return this grid
     */
    public Grid rowGap(float rowGap) {
        getLayout().rowGap(rowGap);
        markLayoutDirty();
        return this;
    }

    /**
     * <p>
     * Sets the horizontal gap between columns.
     * </p>
     *
     * @param columnGap the column gap value
     * @return this grid
     */
    public Grid columnGap(float columnGap) {
        getLayout().columnGap(columnGap);
        markLayoutDirty();
        return this;
    }

    /**
     * <p>
     * Applies layout constraints for the grid and its children.
     * </p>
     *
     * <p>
     * This method calculates the effective number of used columns and rows based on
     * the current child count and configured column limit. It then applies width and
     * height constraints to each child when cell size rules are not auto.
     * </p>
     *
     * <p>
     * If the grid is using point-based fixed cell sizes, it also computes and applies
     * explicit width and height constraints for the grid itself based on the number
     * of active columns, rows, and configured gaps. If either dimension is auto,
     * the corresponding size is left automatic.
     * </p>
     */
    @Override
    protected void applyLayout() {
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
     * <p>
     * Updates the grid and all of its children.
     * </p>
     *
     * <p>
     * This implementation delegates directly to the superclass update method.
     * </p>
     *
     * @param delta the frame delta time
     */
    @Override
    public void update(float delta) {
        super.update(delta);
    }

    /**
     * <p>
     * Draws the grid and all of its children.
     * </p>
     *
     * <p>
     * This implementation delegates directly to the superclass draw method.
     * </p>
     *
     * @param batch the batch used for rendering
     */
    @Override
    public void draw(TextureBatch batch) {
        super.draw(batch);
    }
}