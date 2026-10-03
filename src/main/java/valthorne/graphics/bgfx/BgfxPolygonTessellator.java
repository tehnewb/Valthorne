package valthorne.graphics.bgfx;

import org.joml.Vector2f;
import valthorne.graphics.Color;
import valthorne.math.geometry.Border;
import valthorne.math.geometry.Shape;

import java.util.Arrays;

/**
 * Reads live shape boundaries into reusable primitive storage and produces fill
 * triangles and centered border strips. Convex boundaries take a linear fan path;
 * simple concave polygons use ear clipping with quadratic worst-case work. Holes
 * and self-intersecting polygons are not supported. Border joins use a bounded
 * miter of at most four half-widths; no driver-dependent wide lines are used.
 * Geometry is recomputed because Shape exposes mutable arrays without revisions.
 */
final class BgfxPolygonTessellator {
    private float[] x = new float[64]; // Reused boundary X coordinates.
    private float[] y = new float[64]; // Reused boundary Y coordinates.
    private float[] leftX = new float[64]; // Reused first side of the centered border.
    private float[] leftY = new float[64]; // Reused first-side Y coordinates.
    private float[] rightX = new float[64]; // Reused opposite border-side X coordinates.
    private float[] rightY = new float[64]; // Reused opposite-side Y coordinates.
    private int[] remaining = new int[64]; // Boundary indices still present during ear clipping.
    private int[] triangles = new int[186]; // Completed fill triangle indices before submission.
    private int count; // Number of distinct nonnull boundary vertices copied.

    /**
     * Creates reusable tessellation storage without inspecting any shape.
     */
    BgfxPolygonTessellator() {
    }

    /**
     * Copies, triangulates, and appends a shape's fill followed by its border.
     * Null vertices and duplicate consecutive positions are omitted. Non-finite
     * positions are rejected before any part of that shape is submitted.
     *
     * @param shape borrowed shape with a live boundary
     * @param renderer destination for packed triangle geometry
     * @throws IllegalArgumentException for non-finite or untessellatable geometry
     */
    void draw(Shape shape, BgfxShapeRenderer renderer) {
        Vector2f[] points = shape.points();
        if (points == null || points.length < 3) return;
        ensureCapacity(points.length);
        count = 0;
        for (Vector2f point : points) {
            if (point == null) continue;
            if (!Float.isFinite(point.x) || !Float.isFinite(point.y))
                throw new IllegalArgumentException("Shape boundary coordinates must be finite");
            if (count > 0 && point.x == x[count - 1] && point.y == y[count - 1]) continue;
            x[count] = point.x;
            y[count++] = point.y;
        }
        if (count > 1 && x[0] == x[count - 1] && y[0] == y[count - 1]) count--;
        if (count < 3) return;
        Border border = shape.getBorder();
        float thickness = border == null ? 0 : border.getThickness();
        if (!Float.isFinite(thickness)) throw new IllegalArgumentException("Border thickness must be finite");
        int indexCount = triangulate();
        Color fill = shape.getColor();
        int tint = BgfxVertexFormat.pack(fill == null ? Color.WHITE : fill);
        renderer.appendPolygon(x, y, triangles, indexCount, tint);
        if (border != null && border.getColor() != null && thickness > 0) {
            stroke(thickness * .5f, BgfxVertexFormat.pack(border.getColor()), renderer);
        }
    }

    /**
     * Grows all scratch arrays together when a boundary exceeds their capacity.
     *
     * @param required boundary vertex capacity
     */
    private void ensureCapacity(int required) {
        if (required <= x.length) return;
        int capacity = Math.max(required, Math.multiplyExact(x.length, 2));
        x = Arrays.copyOf(x, capacity);
        y = Arrays.copyOf(y, capacity);
        leftX = Arrays.copyOf(leftX, capacity);
        leftY = Arrays.copyOf(leftY, capacity);
        rightX = Arrays.copyOf(rightX, capacity);
        rightY = Arrays.copyOf(rightY, capacity);
        remaining = Arrays.copyOf(remaining, capacity);
        triangles = Arrays.copyOf(triangles, Math.multiplyExact(capacity - 2, 3));
    }

    /**
     * Completes the fill indices before modifying the renderer's draw ranges.
     *
     * @return number of generated triangle indices; zero for a zero-area boundary
     * @throws IllegalArgumentException if a concave boundary has no valid ear
     */
    private int triangulate() {
        double area = 0;
        for (int i = 0, previous = count - 1; i < count; previous = i++)
            area += (double) x[previous] * y[i] - (double) y[previous] * x[i];
        if (area == 0) return 0;
        double winding = area > 0 ? 1 : -1;
        boolean convex = true;
        for (int i = 0; i < count; i++) {
            int previous = i == 0 ? count - 1 : i - 1;
            int next = i + 1 == count ? 0 : i + 1;
            if (cross(previous, i, next) * winding < 0) {
                convex = false;
                break;
            }
        }
        int written = 0;
        if (convex) {
            for (int i = 1; i < count - 1; i++) {
                triangles[written++] = 0;
                triangles[written++] = i;
                triangles[written++] = i + 1;
            }
            return written;
        }
        for (int i = 0; i < count; i++) remaining[i] = i;
        int vertices = count;
        while (vertices > 3) {
            boolean found = false;
            for (int i = 0; i < vertices; i++) {
                int a = remaining[i == 0 ? vertices - 1 : i - 1];
                int b = remaining[i];
                int c = remaining[i + 1 == vertices ? 0 : i + 1];
                double turn = cross(a, b, c) * winding;
                if (turn < 0) continue;
                if (turn > 0) {
                    boolean occupied = false;
                    for (int j = 0; j < vertices; j++) {
                        int p = remaining[j];
                        if (p == a || p == b || p == c) continue;
                        if (cross(a, b, p) * winding >= 0 && cross(b, c, p) * winding >= 0 && cross(c, a, p) * winding >= 0) {
                            occupied = true;
                            break;
                        }
                    }
                    if (occupied) continue;
                    triangles[written++] = a;
                    triangles[written++] = b;
                    triangles[written++] = c;
                }
                System.arraycopy(remaining, i + 1, remaining, i, vertices - i - 1);
                vertices--;
                found = true;
                break;
            }
            if (!found) throw new IllegalArgumentException("Shape boundary must be a simple polygon without holes");
        }
        triangles[written++] = remaining[0];
        triangles[written++] = remaining[1];
        triangles[written++] = remaining[2];
        return written;
    }

    /**
     * Calculates an oriented triangle area using double intermediates.
     *
     * @param a first boundary index
     * @param b second boundary index
     * @param c third boundary index
     * @return signed twice-area
     */
    private double cross(int a, int b, int c) {
        return ((double) x[b] - x[a]) * ((double) y[c] - y[a]) - ((double) y[b] - y[a]) * ((double) x[c] - x[a]);
    }

    /**
     * Calculates shared join positions and appends two triangles per border edge.
     * Ordinary convex joins share edges exactly, avoiding alpha overdraw between
     * neighboring segment quads. Concave or very wide strokes may overlap themselves.
     *
     * @param halfWidth half the requested world-space thickness
     * @param tint packed border color
     * @param renderer destination for border triangles
     */
    private void stroke(float halfWidth, int tint, BgfxShapeRenderer renderer) {
        for (int i = 0; i < count; i++) {
            int previous = i == 0 ? count - 1 : i - 1;
            int next = i + 1 == count ? 0 : i + 1;
            double ax = (double) x[i] - x[previous];
            double ay = (double) y[i] - y[previous];
            double bx = (double) x[next] - x[i];
            double by = (double) y[next] - y[i];
            double aLength = Math.hypot(ax, ay);
            double bLength = Math.hypot(bx, by);
            double nx = -ay / aLength - by / bLength;
            double ny = ax / aLength + bx / bLength;
            double length = Math.hypot(nx, ny);
            if (length < 1e-12) {
                nx = -by / bLength;
                ny = bx / bLength;
            } else {
                nx /= length;
                ny /= length;
            }
            double dot = nx * (-by / bLength) + ny * (bx / bLength);
            double distance = Math.min(halfWidth * 4.0, halfWidth / Math.max(1e-12, dot));
            float dx = (float) (nx * distance);
            float dy = (float) (ny * distance);
            leftX[i] = x[i] + dx;
            leftY[i] = y[i] + dy;
            rightX[i] = x[i] - dx;
            rightY[i] = y[i] - dy;
        }
        renderer.appendBorder(leftX, leftY, rightX, rightY, count, tint);
    }
}
