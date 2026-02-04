package valthorne.ui.grid;

import valthorne.math.MathUtils;
import valthorne.ui.*;
import valthorne.ui.enums.*;

import java.util.HashMap;
import java.util.Map;

/**
 * A two-dimensional grid layout container that places child {@link Element}s into rows and columns,
 * supporting:
 *
 * <ul>
 *     <li><b>Explicit tracks</b>: {@link #columns} and {@link #rows} define the initial grid template.</li>
 *     <li><b>Implicit tracks</b>: additional rows/cols are created automatically when there are more children
 *         than explicit cells (based on {@link #autoFlow}).</li>
 *     <li><b>Gaps</b>: separate {@link #columnGap} and {@link #rowGap} spacing.</li>
 *     <li><b>Cell placement</b>:
 *         <ul>
 *             <li>Explicit placement using {@link GridConstraints} stored in {@link #constraints}</li>
 *             <li>Auto-placement into the next available free cell when constraints are missing</li>
 *         </ul>
 *     </li>
 *     <li><b>Spans</b>: rowSpan/colSpan are respected when computing the occupied map and cell size.</li>
 *     <li><b>Item alignment inside cells</b>: {@link #justifyItems} and {@link #alignItems} control per-item
 *         alignment and stretch behavior.</li>
 *     <li><b>Content alignment inside the container</b>: {@link #justifyContent} and {@link #alignContent}
 *         shift the entire grid area within the container’s inner content box.</li>
 * </ul>
 *
 * <h2>Coordinate system assumptions</h2>
 * <ul>
 *     <li>X increases to the right.</li>
 *     <li>Y increases upward.</li>
 *     <li>Row positioning is computed from a <b>top edge</b> and proceeds downward per row.</li>
 * </ul>
 *
 * <h2>Track sizing model</h2>
 * <p>Track values are resolved into concrete pixel sizes by {@link #resolveTrackSizesInto(Value[], int, float, float, float[])}:</p>
 * <ul>
 *     <li>{@link ValueType#PIXELS}: fixed pixel size.</li>
 *     <li>{@link ValueType#PERCENTAGE}: percentage of available inner space (after gaps).</li>
 *     <li>{@link ValueType#AUTO} (and any non-fixed types): treated as flexible; remaining space is split evenly
 *         among all flexible tracks.</li>
 * </ul>
 *
 * <h2>Auto placement</h2>
 * <p>Children without explicit constraints (or with negative row/col) are placed in the next free cell according
 * to {@link #autoFlow}:</p>
 * <ul>
 *     <li>{@link AutoFlow#ROW}: left → right, then next row.</li>
 *     <li>{@link AutoFlow#COLUMN}: top → down, then next column.</li>
 * </ul>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * Grid grid = new Grid()
 *     .setColumns(Value.pixels(200), Value.fill(), Value.pixels(120))
 *     .setRows(Value.pixels(48), Value.pixels(48), Value.fill())
 *     .setGap(10)
 *     .setJustifyItems(JustifyItems.CENTER)
 *     .setAlignItems(AlignItems.CENTER)
 *     .setJustifyContent(JustifyContent.CENTER)
 *     .setAlignContent(AlignContent.START);
 *
 * Button ok = new Button("OK");
 * Button cancel = new Button("Cancel");
 *
 * grid.add(ok);
 * grid.add(cancel);
 *
 * grid.setConstraints(ok, new GridConstraints(0, 1));      // row 0, col 1
 * grid.setConstraints(cancel, new GridConstraints(0, 2));  // row 0, col 2
 *
 * grid.setPosition(20, 20);
 * grid.setSize(600, 300);
 * }</pre>
 *
 * @author Albert Beaupre
 * @since February 3rd, 2026
 */
public class Grid extends ElementContainer {

    private static final Value DEFAULT_AUTO = Value.auto();             // Shared default track value when a null/empty track is provided.

    private final Map<Element, GridConstraints> constraints = new HashMap<>(); // Per-child explicit constraints (row/col/spans) for grid placement.
    private Value[] columns = new Value[]{DEFAULT_AUTO};                // Column track definitions (explicit template columns).
    private Value[] rows = new Value[]{DEFAULT_AUTO};                   // Row track definitions (explicit template rows).
    private float columnGap = 0f;                                       // Horizontal spacing between columns.
    private float rowGap = 0f;                                          // Vertical spacing between rows.
    private AutoFlow autoFlow = AutoFlow.ROW;                           // Auto placement order when child constraints are missing.
    private JustifyItems justifyItems = JustifyItems.STRETCH;           // Per-item horizontal alignment within its cell (and stretch behavior).
    private AlignItems alignItems = AlignItems.STRETCH;                 // Per-item vertical alignment within its cell (and stretch behavior).
    private JustifyContent justifyContent = JustifyContent.START;       // How the entire grid is positioned horizontally in the inner content box.
    private AlignContent alignContent = AlignContent.START;             // How the entire grid is positioned vertically in the inner content box.

    private Value[] colTrackBuf = new Value[0];                         // Reusable buffer for resolved column track list (explicit + implicit).
    private Value[] rowTrackBuf = new Value[0];                         // Reusable buffer for resolved row track list (explicit + implicit).
    private float[] colSizesBuf = new float[0];                         // Resolved column pixel sizes (parallel to colTrackBuf).
    private float[] rowSizesBuf = new float[0];                         // Resolved row pixel sizes (parallel to rowTrackBuf).
    private float[] colStartsBuf = new float[0];                        // Resolved column start X positions in world/UI space.
    private float[] rowStartsBuf = new float[0];                        // Resolved row start Y positions (top-left style, Y-up).
    private boolean[] occupiedBuf = new boolean[0];                     // Occupancy map for auto placement (rows * cols).
    private final int[] spotBuf = new int[2];                           // Temporary output buffer for next-free-cell search (row, col).

    /**
     * Creates an empty Grid container.
     *
     * <p>Defaults:</p>
     * <ul>
     *     <li>Click-through enabled (container does not intercept clicks)</li>
     *     <li>Not focusable (children handle focus)</li>
     *     <li>1x1 explicit grid (AUTO track for both columns and rows)</li>
     * </ul>
     */
    public Grid() {
        this.setClickThrough(true);
        this.setFocusable(false);
    }

    /**
     * Called when an element is added to this grid.
     *
     * <p>This implementation is intentionally empty. Override if you want to
     * auto-assign constraints or invalidate cached layout state.</p>
     *
     * @param element the element being added
     */
    @Override
    protected void onAdd(Element element) {}

    /**
     * Called when an element is removed from this grid.
     *
     * <p>This removes any stored {@link GridConstraints} for the removed child.</p>
     *
     * @param element the element being removed
     */
    @Override
    protected void onRemove(Element element) {
        constraints.remove(element);
    }

    /**
     * Sets the explicit column track definitions for this grid.
     *
     * <p>If {@code cols} is null or empty, the grid falls back to a single AUTO column.</p>
     *
     * @param cols column track values
     * @return this grid for chaining
     */
    public Grid setColumns(Value... cols) {
        if (cols == null || cols.length == 0) cols = new Value[]{DEFAULT_AUTO};
        this.columns = cols;
        return this;
    }

    /**
     * Sets the explicit row track definitions for this grid.
     *
     * <p>If {@code rows} is null or empty, the grid falls back to a single AUTO row.</p>
     *
     * @param rows row track values
     * @return this grid for chaining
     */
    public Grid setRows(Value... rows) {
        if (rows == null || rows.length == 0) rows = new Value[]{DEFAULT_AUTO};
        this.rows = rows;
        return this;
    }

    /**
     * Sets both {@link #columnGap} and {@link #rowGap} to the same value.
     *
     * @param gap gap size in pixels
     * @return this grid for chaining
     */
    public Grid setGap(float gap) {
        this.columnGap = gap;
        this.rowGap = gap;
        return this;
    }

    /**
     * Sets the horizontal spacing between columns.
     *
     * @param gap gap size in pixels
     * @return this grid for chaining
     */
    public Grid setColumnGap(float gap) {
        this.columnGap = gap;
        return this;
    }

    /**
     * Sets the vertical spacing between rows.
     *
     * @param gap gap size in pixels
     * @return this grid for chaining
     */
    public Grid setRowGap(float gap) {
        this.rowGap = gap;
        return this;
    }

    /**
     * Sets the auto placement flow direction used when children do not have explicit constraints.
     *
     * @param flow auto flow mode (ROW or COLUMN)
     * @return this grid for chaining
     */
    public Grid setAutoFlow(AutoFlow flow) {
        this.autoFlow = flow;
        return this;
    }

    /**
     * Sets how items are aligned horizontally within their grid cells.
     *
     * <p>STRETCH will expand auto-width children to fill the cell width.</p>
     *
     * @param justify horizontal item alignment mode
     * @return this grid for chaining
     */
    public Grid setJustifyItems(JustifyItems justify) {
        this.justifyItems = justify;
        return this;
    }

    /**
     * Sets how items are aligned vertically within their grid cells.
     *
     * <p>STRETCH will expand auto-height children to fill the cell height.</p>
     *
     * @param align vertical item alignment mode
     * @return this grid for chaining
     */
    public Grid setAlignItems(AlignItems align) {
        this.alignItems = align;
        return this;
    }

    /**
     * Sets how the entire grid area is positioned horizontally within the inner content box.
     *
     * @param justify horizontal content distribution mode
     * @return this grid for chaining
     */
    public Grid setJustifyContent(JustifyContent justify) {
        this.justifyContent = justify;
        return this;
    }

    /**
     * Sets how the entire grid area is positioned vertically within the inner content box.
     *
     * @param align vertical content distribution mode
     * @return this grid for chaining
     */
    public Grid setAlignContent(AlignContent align) {
        this.alignContent = align;
        return this;
    }

    /**
     * Assigns grid constraints to a child element.
     *
     * <p>Behavior:</p>
     * <ul>
     *     <li>If {@code element} is null: no-op.</li>
     *     <li>If {@code c} is null: removes existing constraints for the element.</li>
     *     <li>Otherwise: stores the provided constraints for the element.</li>
     * </ul>
     *
     * @param element the child element to associate constraints with
     * @param c       constraints to store (null removes)
     * @return this grid for chaining
     */
    public Grid setConstraints(Element element, GridConstraints c) {
        if (element == null) return this;
        if (c == null) constraints.remove(element);
        else constraints.put(element, c);
        return this;
    }

    /**
     * Retrieves the stored grid constraints for a child.
     *
     * @param element child element
     * @return constraints for the child, or null if none assigned
     */
    public GridConstraints getConstraints(Element element) {
        return constraints.get(element);
    }

    /**
     * Lays out this grid and positions each visible child into its computed cell rectangle.
     *
     * <p>Major phases:</p>
     * <ol>
     *     <li><b>Resolve container layout</b> by calling {@link ElementContainer#layout()}.</li>
     *     <li><b>Compute inner content box</b> by subtracting padding from this element’s bounds.</li>
     *     <li><b>Compute needed track counts</b> (explicit + implicit) based on visible children and {@link #autoFlow}.</li>
     *     <li><b>Resolve track sizes</b> for columns and rows into pixel buffers.</li>
     *     <li><b>Compute grid origin</b> using {@link #justifyContent} and {@link #alignContent}.</li>
     *     <li><b>Build per-track start positions</b> (colStartsBuf/rowStartsBuf).</li>
     *     <li><b>Pre-mark occupied cells</b> for explicitly-constrained children (including spans).</li>
     *     <li><b>Place children</b>:
     *         <ul>
     *             <li>Explicitly constrained children go to their fixed cell positions.</li>
     *             <li>Unconstrained children are auto-placed into the next free cell.</li>
     *         </ul>
     *     </li>
     *     <li><b>Compute final child size and position</b> based on item alignment + stretch rules.</li>
     * </ol>
     *
     * <p>Top-left behavior in a Y-up system:</p>
     * <ul>
     *     <li>{@code rowStartsBuf[r]} stores the <b>top edge</b> of row {@code r} (not the bottom).</li>
     *     <li>Child Y placement uses {@code cellY + (cellH - targetH)} for START to mean "top".</li>
     * </ul>
     */
    @Override
    public void layout() {
        super.layout();

        float innerX = this.x;
        float innerY = this.y;
        float innerW = this.width;
        float innerH = this.height;

        float padL = 0, padR = 0, padT = 0, padB = 0;

        Layout l = getLayout();
        if (l != null) {
            Value lp = l.getLeftPadding();
            Value rp = l.getRightPadding();
            Value tp = l.getTopPadding();
            Value bp = l.getBottomPadding();

            if (lp != null && lp.type() != ValueType.AUTO) padL = lp.type().resolve(lp.number(), 0, innerW, 0);
            if (rp != null && rp.type() != ValueType.AUTO) padR = rp.type().resolve(rp.number(), 0, innerW, 0);
            if (tp != null && tp.type() != ValueType.AUTO) padT = tp.type().resolve(tp.number(), 0, innerH, 0);
            if (bp != null && bp.type() != ValueType.AUTO) padB = bp.type().resolve(bp.number(), 0, innerH, 0);
        }

        innerX += padL;
        innerY += padB; // Y grows upward; bottom padding pushes content upward
        innerW -= (padL + padR);
        innerH -= (padT + padB);

        if (innerW < 0) innerW = 0;
        if (innerH < 0) innerH = 0;

        // 1) Determine how many rows/cols we actually need (add implicit tracks).
        int explicitCols = Math.max(1, columns.length);
        int explicitRows = Math.max(1, rows.length);

        // Count visible items.
        int itemCount = 0;
        for (int i = 0; i < size; i++) {
            Element child = elements[i];
            if (child == null || child.isHidden()) continue;
            itemCount++;
        }

        // Worst-case needed cells (ignores spanning; simple + safe).
        int neededCols = explicitCols;
        int neededRows = explicitRows;

        int explicitCells = explicitCols * explicitRows;
        if (itemCount > explicitCells) {
            int overflow = itemCount - explicitCells;
            if (autoFlow == AutoFlow.ROW) {
                int addRows = (int) Math.ceil(overflow / (double) explicitCols);
                neededRows = explicitRows + addRows;
            } else {
                int addCols = (int) Math.ceil(overflow / (double) explicitRows);
                neededCols = explicitCols + addCols;
            }
        }

        ensureTrackCapacity(neededCols, neededRows);

        for (int c = 0; c < neededCols; c++) {
            colTrackBuf[c] = (c < explicitCols ? columns[c] : DEFAULT_AUTO);
            if (colTrackBuf[c] == null) colTrackBuf[c] = DEFAULT_AUTO;
        }
        for (int r = 0; r < neededRows; r++) {
            rowTrackBuf[r] = (r < explicitRows ? rows[r] : DEFAULT_AUTO);
            if (rowTrackBuf[r] == null) rowTrackBuf[r] = DEFAULT_AUTO;
        }

        resolveTrackSizesInto(colTrackBuf, neededCols, innerW, columnGap, colSizesBuf);
        resolveTrackSizesInto(rowTrackBuf, neededRows, innerH, rowGap, rowSizesBuf);

        float gridW = sum(colSizesBuf, neededCols) + columnGap * Math.max(0, neededCols - 1);
        float gridH = sum(rowSizesBuf, neededRows) + rowGap * Math.max(0, neededRows - 1);

        float startX = computeContentStart(innerX, innerW, gridW, justifyContent);
        float startYBottom = computeContentStart(innerY, innerH, gridH, toVertical(alignContent));

        // Build start positions into buffers
        float penX = startX;
        for (int c = 0; c < neededCols; c++) {
            colStartsBuf[c] = penX;
            penX += colSizesBuf[c] + columnGap;
        }

        float penTop = startYBottom + gridH;
        for (int r = 0; r < neededRows; r++) {
            penTop -= rowSizesBuf[r];
            rowStartsBuf[r] = penTop;
            penTop -= rowGap;
        }

        ensureOccupiedCapacity(neededRows * neededCols);
        clearOccupied(neededRows * neededCols);

        for (int i = 0; i < size; i++) {
            Element child = elements[i];
            if (child == null || child.isHidden()) continue;

            GridConstraints gc = constraints.get(child);
            if (gc == null || gc.row() < 0 || gc.col() < 0) continue;

            int r0 = MathUtils.clamp(gc.row(), 0, neededRows - 1);
            int c0 = MathUtils.clamp(gc.col(), 0, neededCols - 1);
            int rs = Math.max(1, gc.rowSpan());
            int cs = Math.max(1, gc.colSpan());

            int rEnd = Math.min(neededRows, r0 + rs);
            int cEnd = Math.min(neededCols, c0 + cs);

            for (int rr = r0; rr < rEnd; rr++) {
                int base = rr * neededCols;
                for (int cc = c0; cc < cEnd; cc++) {
                    occupiedBuf[base + cc] = true;
                }
            }
        }

        int autoR = 0;
        int autoC = 0;

        for (int i = 0; i < size; i++) {
            Element child = elements[i];
            if (child == null || child.isHidden()) continue;

            GridConstraints gc = constraints.get(child);

            int r0, c0, rs, cs;

            if (gc != null && gc.row() >= 0 && gc.col() >= 0) {
                r0 = MathUtils.clamp(gc.row(), 0, neededRows - 1);
                c0 = MathUtils.clamp(gc.col(), 0, neededCols - 1);
                rs = Math.max(1, gc.rowSpan());
                cs = Math.max(1, gc.colSpan());
            } else {
                findNextFreeCellInto(occupiedBuf, neededRows, neededCols, autoR, autoC, autoFlow, spotBuf);
                r0 = spotBuf[0];
                c0 = spotBuf[1];
                rs = 1;
                cs = 1;

                occupiedBuf[r0 * neededCols + c0] = true;

                if (autoFlow == AutoFlow.ROW) {
                    autoC = c0 + 1;
                    autoR = r0;
                    if (autoC >= neededCols) {
                        autoC = 0;
                        autoR = Math.min(autoR + 1, neededRows - 1);
                    }
                } else {
                    autoR = r0 + 1;
                    autoC = c0;
                    if (autoR >= neededRows) {
                        autoR = 0;
                        autoC = Math.min(autoC + 1, neededCols - 1);
                    }
                }
            }

            float cellX = colStartsBuf[c0];
            float cellY = rowStartsBuf[r0];

            int cEnd = Math.min(neededCols, c0 + cs);
            int rEnd = Math.min(neededRows, r0 + rs);

            float cellW = 0f;
            for (int cc = c0; cc < cEnd; cc++) {
                cellW += colSizesBuf[cc];
                if (cc < cEnd - 1) cellW += columnGap;
            }

            float cellH = 0f;
            for (int rr = r0; rr < rEnd; rr++) {
                cellH += rowSizesBuf[rr];
                if (rr < rEnd - 1) cellH += rowGap;
            }

            child.layout();

            boolean autoWidth = isAutoWidth(child);
            boolean autoHeight = isAutoHeight(child);

            float targetW = child.getWidth();
            float targetH = child.getHeight();

            if (justifyItems == JustifyItems.STRETCH && autoWidth) targetW = cellW;
            if (alignItems == AlignItems.STRETCH && autoHeight) targetH = cellH;

            targetW = Math.min(targetW, cellW);
            targetH = Math.min(targetH, cellH);

            float finalX = switch (justifyItems) {
                case START, STRETCH -> cellX;
                case CENTER -> cellX + (cellW - targetW) * 0.5f;
                case END -> cellX + (cellW - targetW);
            };

            float finalY = switch (alignItems) {
                case START, STRETCH -> cellY + (cellH - targetH);
                case CENTER -> cellY + (cellH - targetH) * 0.5f;
                case END -> cellY;
            };

            child.setSize(targetW, targetH);
            child.setPosition(finalX, finalY);
        }
    }

    /**
     * Ensures internal track buffers are large enough to handle the computed number of columns and rows.
     *
     * <p>This method grows (reallocates) buffers as needed and never shrinks them, allowing the grid to
     * reuse arrays across frames and reduce GC pressure.</p>
     *
     * @param neededCols number of columns required (explicit + implicit)
     * @param neededRows number of rows required (explicit + implicit)
     */
    private void ensureTrackCapacity(int neededCols, int neededRows) {
        if (colTrackBuf.length < neededCols) colTrackBuf = new Value[neededCols];
        if (rowTrackBuf.length < neededRows) rowTrackBuf = new Value[neededRows];

        if (colSizesBuf.length < neededCols) colSizesBuf = new float[neededCols];
        if (rowSizesBuf.length < neededRows) rowSizesBuf = new float[neededRows];

        if (colStartsBuf.length < neededCols) colStartsBuf = new float[neededCols];
        if (rowStartsBuf.length < neededRows) rowStartsBuf = new float[neededRows];
    }

    /**
     * Ensures the occupancy buffer is large enough for the current grid cell count.
     *
     * @param needed required occupancy cell count (rows * cols)
     */
    private void ensureOccupiedCapacity(int needed) {
        if (occupiedBuf.length < needed) occupiedBuf = new boolean[needed];
    }

    /**
     * Clears the occupancy buffer for the currently used range.
     *
     * @param used number of occupancy entries to clear
     */
    private void clearOccupied(int used) {
        for (int i = 0; i < used; i++) occupiedBuf[i] = false;
    }

    /**
     * Resolves a list of track {@link Value}s into concrete pixel sizes.
     *
     * <p>Algorithm:</p>
     * <ol>
     *     <li>Compute available space = totalSpace - totalGaps</li>
     *     <li>Resolve PIXELS and PERCENTAGE tracks as fixed sizes</li>
     *     <li>Mark AUTO (and other non-fixed) tracks as flexible</li>
     *     <li>Distribute remaining space evenly among flexible tracks</li>
     * </ol>
     *
     * <p>This is a simplified sizing model (good for predictable behavior). If you later add "min-content",
     * "max-content", "fr units", etc., this is the method you’d expand.</p>
     *
     * @param tracks     track values to resolve
     * @param n          number of tracks used from {@code tracks}
     * @param totalSpace total inner space available along that axis (width or height)
     * @param gap        gap between tracks on that axis
     * @param outSizes   output buffer for resolved sizes (length >= n)
     */
    private void resolveTrackSizesInto(Value[] tracks, int n, float totalSpace, float gap, float[] outSizes) {
        float gapTotal = gap * Math.max(0, n - 1);
        float available = Math.max(0f, totalSpace - gapTotal);

        float fixed = 0f;
        int flexibleCount = 0;

        for (int i = 0; i < n; i++) {
            Value v = tracks[i];
            if (v == null) v = DEFAULT_AUTO;

            ValueType t = v.type();
            float s;

            if (t == ValueType.PIXELS) {
                s = v.number();
                outSizes[i] = s;
                fixed += s;
            } else if (t == ValueType.PERCENTAGE) {
                s = t.resolve(v.number(), 0, available, 0);
                outSizes[i] = s;
                fixed += s;
            } else {
                flexibleCount++;
                outSizes[i] = -1f;
            }
        }

        float remaining = available - fixed;
        if (remaining < 0) remaining = 0;

        float each = (flexibleCount > 0) ? (remaining / flexibleCount) : 0f;

        for (int i = 0; i < n; i++) {
            if (outSizes[i] < 0f) outSizes[i] = each;
        }
    }

    /**
     * Sums the first {@code n} entries of a float array.
     *
     * @param a array of values
     * @param n number of elements to sum
     * @return sum of values
     */
    private float sum(float[] a, int n) {
        float s = 0f;
        for (int i = 0; i < n; i++) s += a[i];
        return s;
    }

    /**
     * Converts vertical {@link AlignContent} options into a {@link JustifyContent}-style distribution.
     *
     * <p>This allows reuse of {@link #computeContentStart(float, float, float, JustifyContent)} for Y-axis
     * alignment by mapping the enum values into equivalent "justify" behavior.</p>
     *
     * @param a align-content value
     * @return equivalent justify-content value for vertical distribution
     */
    private JustifyContent toVertical(AlignContent a) {
        return switch (a) {
            case START -> JustifyContent.START;
            case CENTER -> JustifyContent.CENTER;
            case END -> JustifyContent.END;
            case SPACE_BETWEEN -> JustifyContent.SPACE_BETWEEN;
            case SPACE_AROUND -> JustifyContent.SPACE_AROUND;
            case SPACE_EVENLY -> JustifyContent.SPACE_EVENLY;
        };
    }

    /**
     * Computes the starting origin for a content block within an outer region given a distribution mode.
     *
     * <p>This is a simplified implementation:</p>
     * <ul>
     *     <li>START/CENTER/END are precise</li>
     *     <li>SPACE_* modes are treated as centered-ish while gaps remain unchanged</li>
     * </ul>
     *
     * <p>If you later want true SPACE_* behavior at the content level, you would need to adjust
     * the effective inter-track gaps, not just the origin.</p>
     *
     * @param origin      outer region origin (x or y)
     * @param outerSize   outer region size (width or height)
     * @param contentSize content block size
     * @param mode        distribution mode
     * @return starting coordinate for the content block
     */
    private float computeContentStart(float origin, float outerSize, float contentSize, JustifyContent mode) {
        float free = outerSize - contentSize;
        if (free < 0) free = 0;

        return switch (mode) {
            case START -> origin;
            case CENTER -> origin + free * 0.5f;
            case END -> origin + free;
            case SPACE_BETWEEN -> origin;              // spacing handled by gaps already; keep simple.
            case SPACE_AROUND -> origin + free * 0.5f; // simplified
            case SPACE_EVENLY -> origin + free * 0.5f; // simplified
        };
    }

    /**
     * Finds the next unoccupied cell in the occupancy map, starting from a given row/col cursor.
     *
     * <p>Search order depends on {@link AutoFlow}:</p>
     * <ul>
     *     <li>ROW: advances columns first, then wraps to next row</li>
     *     <li>COLUMN: advances rows first, then wraps to next column</li>
     * </ul>
     *
     * <p>This method performs a bounded search of at most {@code rowsN * colsN} steps and wraps around
     * if needed.</p>
     *
     * @param occupied occupancy map (length >= rowsN * colsN)
     * @param rowsN    number of rows
     * @param colsN    number of columns
     * @param startR   initial row cursor
     * @param startC   initial column cursor
     * @param flow     auto flow order
     * @param outRC    output buffer: outRC[0] = row, outRC[1] = col
     */
    private void findNextFreeCellInto(boolean[] occupied, int rowsN, int colsN, int startR, int startC, AutoFlow flow, int[] outRC) {
        int r = MathUtils.clamp(startR, 0, rowsN - 1);
        int c = MathUtils.clamp(startC, 0, colsN - 1);

        for (int k = 0; k < rowsN * colsN; k++) {
            if (!occupied[r * colsN + c]) {
                outRC[0] = r;
                outRC[1] = c;
                return;
            }

            if (flow == AutoFlow.ROW) {
                c++;
                if (c >= colsN) {
                    c = 0;
                    r++;
                    if (r >= rowsN) r = 0;
                }
            } else {
                r++;
                if (r >= rowsN) {
                    r = 0;
                    c++;
                    if (c >= colsN) c = 0;
                }
            }
        }

        outRC[0] = 0;
        outRC[1] = 0;
    }

    /**
     * Determines whether a child should be considered "auto width" for stretch decisions.
     *
     * <p>A child is considered auto-width when:</p>
     * <ul>
     *     <li>It has no {@link Layout}, or</li>
     *     <li>Its {@link Layout#getWidth()} is null, or</li>
     *     <li>Its width {@link ValueType} is {@link ValueType#AUTO}</li>
     * </ul>
     *
     * @param child child element to check
     * @return true if the child's width is treated as auto
     */
    private boolean isAutoWidth(Element child) {
        Layout l = child.getLayout();
        return l == null || l.getWidth() == null || l.getWidth().type() == ValueType.AUTO;
    }

    /**
     * Determines whether a child should be considered "auto height" for stretch decisions.
     *
     * <p>A child is considered auto-height when:</p>
     * <ul>
     *     <li>It has no {@link Layout}, or</li>
     *     <li>Its {@link Layout#getHeight()} is null, or</li>
     *     <li>Its height {@link ValueType} is {@link ValueType#AUTO}</li>
     * </ul>
     *
     * @param child child element to check
     * @return true if the child's height is treated as auto
     */
    private boolean isAutoHeight(Element child) {
        Layout l = child.getLayout();
        return l == null || l.getHeight() == null || l.getHeight().type() == ValueType.AUTO;
    }
}
