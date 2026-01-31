package valthorne.ui.grid;

import valthorne.math.MathUtils;
import valthorne.ui.*;
import valthorne.ui.enums.*;

import java.util.HashMap;
import java.util.Map;

/**
 * The Grid class represents a two-dimensional layout container that arranges its child elements
 * into a grid structure. It allows for flexible control over the size, spacing, alignment,
 * and flow of its rows and columns.
 * <p>
 * This class offers customizable options for defining grid tracks, gaps between tracks, and
 * various alignment modes for its child elements. It extends the ElementContainer, inheriting
 * its capabilities for managing child elements while adding specific functionality for grid-based layouts.
 *
 * @author Albert Beaupre
 * @since January 30th, 2026
 */
public class Grid extends ElementContainer {

    private Value[] columns = new Value[]{Value.auto()};
    private Value[] rows = new Value[]{Value.auto()};

    private float columnGap = 0f;
    private float rowGap = 0f;

    private AutoFlow autoFlow = AutoFlow.ROW;

    private JustifyItems justifyItems = JustifyItems.STRETCH;
    private AlignItems alignItems = AlignItems.STRETCH;

    private JustifyContent justifyContent = JustifyContent.START;
    private AlignContent alignContent = AlignContent.START;

    private final Map<Element, GridConstraints> constraints = new HashMap<>();

    @Override
    protected void onAdd(Element element) {

    }

    @Override
    protected void onRemove(Element element) {
        constraints.remove(element);
    }

    public Grid setColumns(Value... cols) {
        if (cols == null || cols.length == 0) cols = new Value[]{Value.auto()};
        this.columns = cols;
        return this;
    }

    public Grid setRows(Value... rows) {
        if (rows == null || rows.length == 0) rows = new Value[]{Value.auto()};
        this.rows = rows;
        return this;
    }

    public Grid setGap(float gap) {
        this.columnGap = gap;
        this.rowGap = gap;
        return this;
    }

    public Grid setColumnGap(float gap) {
        this.columnGap = gap;
        return this;
    }

    public Grid setRowGap(float gap) {
        this.rowGap = gap;
        return this;
    }

    public Grid setAutoFlow(AutoFlow flow) {
        this.autoFlow = flow;
        return this;
    }

    public Grid setJustifyItems(JustifyItems justify) {
        this.justifyItems = justify;
        return this;
    }

    public Grid setAlignItems(AlignItems align) {
        this.alignItems = align;
        return this;
    }

    public Grid setJustifyContent(JustifyContent justify) {
        this.justifyContent = justify;
        return this;
    }

    public Grid setAlignContent(AlignContent align) {
        this.alignContent = align;
        return this;
    }

    public Grid setConstraints(Element element, GridConstraints c) {
        if (element == null) return this;
        if (c == null) constraints.remove(element);
        else constraints.put(element, c);
        return this;
    }

    public GridConstraints getConstraints(Element element) {
        return constraints.get(element);
    }

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

            if (lp.type() != ValueType.AUTO) padL = lp.type().resolve(lp.number(), 0, innerW, 0);
            if (rp.type() != ValueType.AUTO) padR = rp.type().resolve(rp.number(), 0, innerW, 0);
            if (tp.type() != ValueType.AUTO) padT = tp.type().resolve(tp.number(), 0, innerH, 0);
            if (bp.type() != ValueType.AUTO) padB = bp.type().resolve(bp.number(), 0, innerH, 0);
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

        if (itemCount > explicitCols * explicitRows) {
            int overflow = itemCount - (explicitCols * explicitRows);
            if (autoFlow == AutoFlow.ROW) {
                int cols = explicitCols;
                int addRows = (int) Math.ceil(overflow / (double) cols);
                neededRows = explicitRows + addRows;
            } else {
                int rowsN = explicitRows;
                int addCols = (int) Math.ceil(overflow / (double) rowsN);
                neededCols = explicitCols + addCols;
            }
        }

        Value[] colTracks = new Value[neededCols];
        Value[] rowTracks = new Value[neededRows];

        for (int c = 0; c < neededCols; c++) colTracks[c] = (c < explicitCols ? columns[c] : Value.auto());
        for (int r = 0; r < neededRows; r++) rowTracks[r] = (r < explicitRows ? rows[r] : Value.auto());

        float[] colSizes = resolveTrackSizes(colTracks, innerW, columnGap);
        float[] rowSizes = resolveTrackSizes(rowTracks, innerH, rowGap);

        float gridW = sum(colSizes) + columnGap * Math.max(0, colSizes.length - 1);
        float gridH = sum(rowSizes) + rowGap * Math.max(0, rowSizes.length - 1);

        float startX = computeContentStart(innerX, innerW, gridW, justifyContent, colSizes.length);
        float startYBottom = computeContentStart(innerY, innerH, gridH, toVertical(alignContent), rowSizes.length);

        float[] colStarts = new float[colSizes.length];
        float[] rowStarts = new float[rowSizes.length];

        float x = startX;
        for (int c = 0; c < colSizes.length; c++) {
            colStarts[c] = x;
            x += colSizes[c] + columnGap;
        }

        float gridTop = startYBottom + gridH; // top edge of the grid
        float penTop = gridTop;

        for (int r = 0; r < rowSizes.length; r++) {
            penTop -= rowSizes[r];
            rowStarts[r] = penTop;
            penTop -= rowGap;
        }

        boolean[][] occupied = new boolean[rowSizes.length][colSizes.length];

        for (int i = 0; i < size; i++) {
            Element child = elements[i];
            if (child == null || child.isHidden()) continue;

            GridConstraints c = constraints.get(child);
            if (c == null || c.row() < 0 || c.col() < 0) continue;

            int r0 = MathUtils.clamp(c.row(), 0, rowSizes.length - 1);
            int c0 = MathUtils.clamp(c.col(), 0, colSizes.length - 1);
            int rs = Math.max(1, c.rowSpan());
            int cs = Math.max(1, c.colSpan());

            for (int rr = r0; rr < Math.min(rowSizes.length, r0 + rs); rr++) {
                for (int cc = c0; cc < Math.min(colSizes.length, c0 + cs); cc++) {
                    occupied[rr][cc] = true;
                }
            }
        }

        int autoR = 0;
        int autoC = 0;

        for (int i = 0; i < size; i++) {
            Element child = elements[i];
            if (child == null || child.isHidden()) continue;

            GridConstraints c = constraints.get(child);

            int r0, c0, rs, cs;

            if (c != null && c.row() >= 0 && c.col() >= 0) {
                r0 = MathUtils.clamp(c.row(), 0, rowSizes.length - 1);
                c0 = MathUtils.clamp(c.col(), 0, colSizes.length - 1);
                rs = Math.max(1, c.rowSpan());
                cs = Math.max(1, c.colSpan());
            } else {
                int[] spot = findNextFreeCell(occupied, autoR, autoC, autoFlow);
                r0 = spot[0];
                c0 = spot[1];
                rs = 1;
                cs = 1;

                occupied[r0][c0] = true;

                // Advance cursor.
                if (autoFlow == AutoFlow.ROW) {
                    autoC = c0 + 1;
                    autoR = r0;
                    if (autoC >= colSizes.length) {
                        autoC = 0;
                        autoR = Math.min(autoR + 1, rowSizes.length - 1);
                    }
                } else {
                    autoR = r0 + 1;
                    autoC = c0;
                    if (autoR >= rowSizes.length) {
                        autoR = 0;
                        autoC = Math.min(autoC + 1, colSizes.length - 1);
                    }
                }
            }

            float cellX = colStarts[c0];
            float cellY = rowStarts[r0];

            float cellW = 0f;
            for (int cc = c0; cc < Math.min(colSizes.length, c0 + cs); cc++) {
                cellW += colSizes[cc];
                if (cc < c0 + cs - 1) cellW += columnGap;
            }

            float cellH = 0f;
            for (int rr = r0; rr < Math.min(rowSizes.length, r0 + rs); rr++) {
                cellH += rowSizes[rr];
                if (rr < r0 + rs - 1) cellH += rowGap;
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

    private float[] resolveTrackSizes(Value[] tracks, float totalSpace, float gap) {
        int n = tracks.length;
        float[] sizes = new float[n];

        float gapTotal = gap * Math.max(0, n - 1);
        float available = Math.max(0f, totalSpace - gapTotal);

        float fixed = 0f;
        int flexibleCount = 0;

        // Pass 1: resolve fixed, count flexible.
        for (int i = 0; i < n; i++) {
            Value v = tracks[i];
            if (v == null) v = Value.auto();

            ValueType t = v.type();
            float s;

            if (t == ValueType.PIXELS) {
                s = v.number();
                sizes[i] = s;
                fixed += s;
            } else if (t == ValueType.PERCENTAGE) {
                s = v.type().resolve(v.number(), 0, available, 0);
                sizes[i] = s;
                fixed += s;
            } else {
                // AUTO / FILL / anything else becomes flexible.
                flexibleCount++;
                sizes[i] = -1f;
            }
        }

        float remaining = available - fixed;
        if (remaining < 0) remaining = 0;

        float each = (flexibleCount > 0) ? (remaining / flexibleCount) : 0f;

        for (int i = 0; i < n; i++) {
            if (sizes[i] < 0f) sizes[i] = each;
        }

        return sizes;
    }

    private float sum(float[] a) {
        float s = 0f;
        for (float v : a) s += v;
        return s;
    }

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

    private float computeContentStart(float origin, float outerSize, float contentSize, JustifyContent mode, int trackCount) {
        float free = outerSize - contentSize;
        if (free < 0) free = 0;

        return switch (mode) {
            case START -> origin;
            case CENTER -> origin + free * 0.5f;
            case END -> origin + free;
            case SPACE_BETWEEN -> origin; // spacing handled by gaps already; keep simple.
            case SPACE_AROUND -> origin + free * 0.5f;  // simplified
            case SPACE_EVENLY -> origin + free * 0.5f;  // simplified
        };
    }

    private int[] findNextFreeCell(boolean[][] occupied, int startR, int startC, AutoFlow flow) {
        int rowsN = occupied.length;
        int colsN = occupied[0].length;

        int r = MathUtils.clamp(startR, 0, rowsN - 1);
        int c = MathUtils.clamp(startC, 0, colsN - 1);

        // Full scan with wrap.
        for (int k = 0; k < rowsN * colsN; k++) {
            if (!occupied[r][c]) return new int[]{r, c};

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

        return new int[]{0, 0};
    }

    private boolean isAutoWidth(Element child) {
        Layout l = child.getLayout();
        return l == null || l.getWidth() == null || l.getWidth().type() == ValueType.AUTO;
    }

    private boolean isAutoHeight(Element child) {
        Layout l = child.getLayout();
        return l == null || l.getHeight() == null || l.getHeight().type() == ValueType.AUTO;
    }
}
