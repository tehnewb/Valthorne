package valthorne.graphics.texture;

import valthorne.graphics.Color;
import org.joml.Vector2f;

import java.nio.ByteBuffer;
import java.util.*;

/**
 * CPU helpers for extracting a simplified pixel contour and dividing a texture
 * into grid regions. Contour tracing reads retained RGBA bytes, selects the largest
 * four-connected nontransparent component, and returns its largest boundary loop
 * after collinear and 0.75-pixel Ramer-Douglas-Peucker simplification. Holes and
 * smaller disconnected components are not returned.
 * <p>
 * Tracing does not read GPU pixels: textures without retained TextureData produce
 * an empty contour. Returned points lie on pixel edges in the source buffer's
 * coordinate orientation; region traces are local to the extracted region.
 * Texture splitting creates borrowed views without copying pixels or owning the
 * source texture.
 * </p>
 * @author Albert Beaupre
 */
public class TextureUtility {

    /**
     * Traces the outer boundary of the largest four-connected solid pixel component.
     * Alpha-zero pixels are always excluded; ignore colors match all four byte channels
     * exactly. Retained data must be readable, tightly packed RGBA. The returned loop
     * does not repeat its closing point and is simplified with 0.75-pixel tolerance.
     *
     * @param texture source with optional retained CPU data
     * @param ignore exact colors to exclude; null array or entries have no effect
     * @return independent pixel-edge points, or an empty array for absent data, nonpositive dimensions, or no boundary
     * @throws NullPointerException if texture is null
     */
    public static Vector2f[] trace(Texture texture, Color... ignore) {
        if (texture == null)
            throw new NullPointerException("Texture cannot be null");

        TextureData data = texture.getData();
        if (data == null)
            return new Vector2f[0];

        int width = data.width();
        int height = data.height();

        if (width <= 0 || height <= 0)
            return new Vector2f[0];

        boolean[][] solid = buildSolidMask(data.buffer(), width, height, ignore);
        boolean[][] component = extractLargestComponent(solid);

        List<Edge> edges = buildBoundaryEdges(component, width, height);
        if (edges.isEmpty())
            return new Vector2f[0];

        List<List<Point>> loops = stitchLoops(edges);
        if (loops.isEmpty())
            return new Vector2f[0];

        List<Point> outer = largestLoop(loops);
        outer = removeDuplicateClosingPoint(outer);
        outer = simplifyCollinear(outer);
        outer = simplifyRdpClosed(outer, 0.75f);

        Vector2f[] result = new Vector2f[outer.size()];
        for (int i = 0; i < outer.size(); i++) {
            Point p = outer.get(i);
            result[i] = new Vector2f(p.x, p.y);
        }

        return result;
    }

    /**
     * Copies a region's retained RGBA pixels and traces its largest solid component.
     * Region coordinates and dimensions are truncated to integers. Returned points
     * are relative to the region origin, without adding the atlas offset or applying
     * a UV flip; the closing point is omitted.
     *
     * @param textureRegion source region with valid bounds inside retained pixel data
     * @param ignore exact RGBA colors to exclude in addition to transparent pixels
     * @return simplified region-local boundary, or empty when data or contour is absent
     * @throws NullPointerException if textureRegion is null
     * @throws IndexOutOfBoundsException if region bounds exceed readable source data
     */
    public static Vector2f[] trace(TextureRegion textureRegion, Color... ignore) {
        if (textureRegion == null)
            throw new NullPointerException("TextureRegion cannot be null");

        Texture texture = textureRegion.getTexture();
        int x = (int) textureRegion.getRegionX();
        int y = (int) textureRegion.getRegionY();
        int width = (int) textureRegion.getRegionWidth();
        int height = (int) textureRegion.getRegionHeight();

        TextureData data = texture.getData();
        if (data == null)
            return new Vector2f[0];

        if (width <= 0 || height <= 0)
            return new Vector2f[0];

        ByteBuffer subBuffer = extractSubBuffer(data.buffer(), x, y, width, height, data.width());
        boolean[][] solid = buildSolidMask(subBuffer, width, height, ignore);
        boolean[][] component = extractLargestComponent(solid);

        List<Edge> edges = buildBoundaryEdges(component, width, height);
        if (edges.isEmpty())
            return new Vector2f[0];

        List<List<Point>> loops = stitchLoops(edges);
        if (loops.isEmpty())
            return new Vector2f[0];

        List<Point> outer = largestLoop(loops);
        outer = removeDuplicateClosingPoint(outer);
        outer = simplifyCollinear(outer);
        outer = simplifyRdpClosed(outer, 0.75f);

        Vector2f[] result = new Vector2f[outer.size()];
        for (int i = 0; i < outer.size(); i++) {
            Point p = outer.get(i);
            result[i] = new Vector2f(p.x, p.y);
        }

        return result;
    }

    /**
     * Copies a rectangular RGBA byte range using absolute accesses, preserving the
     * source buffer position. Does not clamp or validate the requested rectangle.
     *
     * @param buffer readable tightly packed RGBA source
     * @param x first source column
     * @param y first source row
     * @param width copied columns
     * @param height copied rows
     * @param textureWidth source row width in pixels
     * @return heap buffer containing tightly packed region pixels
     * @throws IndexOutOfBoundsException if a source access is outside the buffer limit
     */
    private static ByteBuffer extractSubBuffer(ByteBuffer buffer, int x, int y, int width, int height, int textureWidth) {
        ByteBuffer subBuffer = ByteBuffer.allocate(width * height * 4);
        for (int row = 0; row < height; row++) {
            int srcPos = (x + (y + row) * textureWidth) * 4;
            int length = width * 4;
            int destPos = row * width * 4;

            for (int i = 0; i < length; i++) {
                subBuffer.put(destPos + i, buffer.get(srcPos + i));
            }
        }
        return subBuffer;
    }

    /**
     * Builds an X-major solid mask from RGBA bytes. Zero alpha always means empty;
     * otherwise only an exact ignored RGBA value excludes the pixel. Null ignored
     * entries use a nonmatching sentinel.
     *
     * @param buffer readable tightly packed RGBA pixels
     * @param width pixel columns
     * @param height pixel rows
     * @param ignore exact colors to exclude
     * @return mask indexed by X then Y
     */
    private static boolean[][] buildSolidMask(ByteBuffer buffer, int width, int height, Color... ignore) {
        boolean[][] solid = new boolean[width][height];

        int[] ignoreRgba = null;
        if (ignore != null && ignore.length > 0) {
            ignoreRgba = new int[ignore.length * 4];
            for (int i = 0; i < ignore.length; i++) {
                Color c = ignore[i];
                if (c == null) {
                    ignoreRgba[i * 4] = -1;
                    ignoreRgba[i * 4 + 1] = -1;
                    ignoreRgba[i * 4 + 2] = -1;
                    ignoreRgba[i * 4 + 3] = -1;
                } else {
                    ignoreRgba[i * 4] = c.getRed();
                    ignoreRgba[i * 4 + 1] = c.getGreen();
                    ignoreRgba[i * 4 + 2] = c.getBlue();
                    ignoreRgba[i * 4 + 3] = c.getAlpha();
                }
            }
        }

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int index = (x + y * width) * 4;

                int r = buffer.get(index) & 0xFF;
                int g = buffer.get(index + 1) & 0xFF;
                int b = buffer.get(index + 2) & 0xFF;
                int a = buffer.get(index + 3) & 0xFF;

                if (a == 0)
                    continue;

                boolean ignored = false;

                if (ignoreRgba != null) {
                    for (int i = 0; i < ignoreRgba.length; i += 4) {
                        if (ignoreRgba[i] == r &&
                                ignoreRgba[i + 1] == g &&
                                ignoreRgba[i + 2] == b &&
                                ignoreRgba[i + 3] == a) {
                            ignored = true;
                            break;
                        }
                    }
                }

                solid[x][y] = !ignored;
            }
        }

        return solid;
    }

    /**
     * Flood-fills four-neighbor solid components and returns a mask containing only
     * the one with the most pixels. Equal-size ties retain the first component found
     * in row-major scanning. Assumes a nonempty rectangular input mask.
     *
     * @param solid X-major solid-pixel mask
     * @return new mask containing the largest component, or all false if none
     */
    private static boolean[][] extractLargestComponent(boolean[][] solid) {
        int width = solid.length;
        int height = solid[0].length;

        boolean[][] visited = new boolean[width][height];
        boolean[][] best = new boolean[width][height];
        int bestCount = 0;

        int[] ox = {1, -1, 0, 0};
        int[] oy = {0, 0, 1, -1};

        ArrayDeque<Point> queue = new ArrayDeque<>();
        ArrayList<Point> component = new ArrayList<>();

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (!solid[x][y] || visited[x][y])
                    continue;

                component.clear();

                visited[x][y] = true;
                queue.addLast(new Point(x, y));

                while (!queue.isEmpty()) {
                    Point p = queue.removeFirst();
                    component.add(p);

                    for (int i = 0; i < 4; i++) {
                        int nx = p.x + ox[i];
                        int ny = p.y + oy[i];

                        if (nx < 0 || ny < 0 || nx >= width || ny >= height)
                            continue;
                        if (!solid[nx][ny] || visited[nx][ny])
                            continue;

                        visited[nx][ny] = true;
                        queue.addLast(new Point(nx, ny));
                    }
                }

                if (component.size() > bestCount) {
                    bestCount = component.size();
                    for (int yy = 0; yy < height; yy++)
                        Arrays.fill(best[yy < width ? yy : 0], false);
                    best = new boolean[width][height];
                    for (Point p : component)
                        best[p.x][p.y] = true;
                }
            }
        }

        return best;
    }

    /**
     * Creates directed unit pixel-edge segments where a solid pixel touches empty
     * space or the image boundary. Orientations are consistent around each pixel
     * so matching endpoints can be stitched.
     *
     * @param solid X-major component mask
     * @param width pixel columns
     * @param height pixel rows
     * @return newly allocated boundary edges
     */
    private static List<Edge> buildBoundaryEdges(boolean[][] solid, int width, int height) {
        ArrayList<Edge> edges = new ArrayList<>();

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                if (!solid[x][y])
                    continue;

                if (y == 0 || !solid[x][y - 1])
                    edges.add(new Edge(new Point(x, y), new Point(x + 1, y)));

                if (x == width - 1 || !solid[x + 1][y])
                    edges.add(new Edge(new Point(x + 1, y), new Point(x + 1, y + 1)));

                if (y == height - 1 || !solid[x][y + 1])
                    edges.add(new Edge(new Point(x + 1, y + 1), new Point(x, y + 1)));

                if (x == 0 || !solid[x - 1][y])
                    edges.add(new Edge(new Point(x, y + 1), new Point(x, y)));
            }
        }

        return edges;
    }

    /**
     * Groups outgoing directed edges by starting point and consumes them into
     * boundary walks, marking each edge used. Closed walks repeat their first point.
     * Retains walks with at least four points; valid pixel-boundary input is expected
     * to close them.
     *
     * @param edges mutable-use boundary segments
     * @return retained boundary walks
     */
    private static List<List<Point>> stitchLoops(List<Edge> edges) {
        HashMap<Point, ArrayDeque<Edge>> outgoing = new HashMap<>();
        for (Edge edge : edges)
            outgoing.computeIfAbsent(edge.a, k -> new ArrayDeque<>()).add(edge);

        ArrayList<List<Point>> loops = new ArrayList<>();

        while (true) {
            Edge start = null;
            for (ArrayDeque<Edge> list : outgoing.values()) {
                while (!list.isEmpty() && list.peekFirst().used)
                    list.removeFirst();
                if (!list.isEmpty()) {
                    start = list.peekFirst();
                    break;
                }
            }

            if (start == null)
                break;

            ArrayList<Point> loop = new ArrayList<>();
            Point startPoint = start.a;
            Point current = startPoint;

            while (true) {
                ArrayDeque<Edge> list = outgoing.get(current);
                if (list == null)
                    break;

                while (!list.isEmpty() && list.peekFirst().used)
                    list.removeFirst();

                if (list.isEmpty())
                    break;

                Edge edge = list.removeFirst();
                edge.used = true;

                loop.add(edge.a);
                current = edge.b;

                if (current.equals(startPoint)) {
                    loop.add(current);
                    break;
                }
            }

            if (loop.size() >= 4)
                loops.add(loop);
        }

        return loops;
    }

    /**
     * Selects the supplied loop with greatest absolute shoelace area. Equal areas
     * retain the first loop in the input list.
     *
     * @param loops nonempty candidate loops
     * @return borrowed largest-area loop
     */
    private static List<Point> largestLoop(List<List<Point>> loops) {
        List<Point> best = loops.getFirst();
        float bestArea = Math.abs(area(best));

        for (int i = 1; i < loops.size(); i++) {
            List<Point> loop = loops.get(i);
            float area = Math.abs(area(loop));
            if (area > bestArea) {
                bestArea = area;
                best = loop;
            }
        }

        return best;
    }

    /**
     * Removes a repeated closing point by copying all preceding points. Returns
     * the original list when its endpoints differ or it has fewer than two points.
     *
     * @param loop candidate closed walk
     * @return loop without a repeated endpoint
     */
    private static List<Point> removeDuplicateClosingPoint(List<Point> loop) {
        if (loop.size() > 1 && loop.getFirst().equals(loop.getLast()))
            return new ArrayList<>(loop.subList(0, loop.size() - 1));
        return loop;
    }

    /**
     * Repeatedly removes a vertex whose adjacent edge cross product is zero,
     * including the wraparound junction. Short inputs are returned unchanged;
     * other inputs are simplified in a copied list.
     *
     * @param points cyclic polygon vertices
     * @return vertices with collinear intermediates removed
     */
    private static List<Point> simplifyCollinear(List<Point> points) {
        if (points.size() < 3)
            return points;

        ArrayList<Point> result = new ArrayList<>(points);
        boolean changed;

        do {
            changed = false;
            if (result.size() < 3)
                break;

            for (int i = 0; i < result.size(); i++) {
                Point prev = result.get((i - 1 + result.size()) % result.size());
                Point curr = result.get(i);
                Point next = result.get((i + 1) % result.size());

                int dx1 = curr.x - prev.x;
                int dy1 = curr.y - prev.y;
                int dx2 = next.x - curr.x;
                int dy2 = next.y - curr.y;

                if (dx1 * dy2 - dy1 * dx2 == 0) {
                    result.remove(i);
                    changed = true;
                    break;
                }
            }
        } while (changed);

        return result;
    }

    /**
     * Rotates a closed polygon to its leftmost, then lowest, point and temporarily
     * duplicates that endpoint for recursive distance simplification. Removes the
     * duplicate afterward and runs a final collinear pass.
     *
     * @param points cyclic vertices without a required closing duplicate
     * @param epsilon perpendicular-distance tolerance in pixels
     * @return simplified cyclic polygon
     */
    private static List<Point> simplifyRdpClosed(List<Point> points, float epsilon) {
        if (points.size() < 4)
            return points;

        int leftMost = 0;
        for (int i = 1; i < points.size(); i++) {
            Point a = points.get(i);
            Point b = points.get(leftMost);
            if (a.x < b.x || (a.x == b.x && a.y < b.y))
                leftMost = i;
        }

        ArrayList<Point> rotated = new ArrayList<>(points.size() + 1);
        for (int i = 0; i < points.size(); i++)
            rotated.add(points.get((leftMost + i) % points.size()));
        rotated.add(rotated.getFirst());

        ArrayList<Point> simplified = new ArrayList<>();
        rdp(rotated, 0, rotated.size() - 1, epsilon, simplified);

        if (!simplified.isEmpty() && simplified.getFirst().equals(simplified.getLast()))
            simplified.removeLast();

        return simplifyCollinear(simplified);
    }

    /**
     * Recursively keeps the farthest intermediate point when its distance from the
     * endpoint line exceeds epsilon; otherwise retains only endpoints. Appends to
     * a shared output while removing duplicate recursion junctions.
     *
     * @param points ordered input path
     * @param start inclusive first point
     * @param end inclusive last point
     * @param epsilon distance tolerance
     * @param out mutable simplified output
     */
    private static void rdp(List<Point> points, int start, int end, float epsilon, List<Point> out) {
        if (end <= start + 1) {
            if (out.isEmpty() || !out.getLast().equals(points.get(start)))
                out.add(points.get(start));
            out.add(points.get(end));
            return;
        }

        float maxDistance = -1f;
        int index = -1;

        Point a = points.get(start);
        Point b = points.get(end);

        for (int i = start + 1; i < end; i++) {
            float distance = perpendicularDistance(points.get(i), a, b);
            if (distance > maxDistance) {
                maxDistance = distance;
                index = i;
            }
        }

        if (maxDistance > epsilon) {
            rdp(points, start, index, epsilon, out);
            out.removeLast();
            rdp(points, index, end, epsilon, out);
        } else {
            if (out.isEmpty() || !out.getLast().equals(a))
                out.add(a);
            out.add(b);
        }
    }

    /**
     * Measures distance to the infinite line through two points. Coincident endpoints
     * instead produce ordinary distance to that endpoint.
     *
     * @param p point to measure
     * @param a first line point
     * @param b second line point
     * @return distance in pixel coordinates
     */
    private static float perpendicularDistance(Point p, Point a, Point b) {
        float dx = b.x - a.x;
        float dy = b.y - a.y;

        if (dx == 0f && dy == 0f) {
            float px = p.x - a.x;
            float py = p.y - a.y;
            return (float) Math.sqrt(px * px + py * py);
        }

        float numerator = Math.abs(dy * p.x - dx * p.y + b.x * a.y - b.y * a.x);
        float denominator = (float) Math.sqrt(dx * dx + dy * dy);
        return numerator / denominator;
    }

    /**
     * Computes signed shoelace area, treating the last and first vertices as joined.
     * The sign depends on winding and the source coordinate orientation.
     *
     * @param points polygon vertices
     * @return signed area in square pixels
     */
    private static float area(List<Point> points) {
        float sum = 0f;
        for (int i = 0; i < points.size(); i++) {
            Point a = points.get(i);
            Point b = points.get((i + 1) % points.size());
            sum += (float) a.x * b.y - (float) b.x * a.y;
        }
        return sum * 0.5f;
    }

    /**
     * Divides a texture into equal integer-sized borrowed regions indexed by row then
     * column. Row zero uses the highest Y interval, and columns increase from X zero.
     * Integer division discards remainder columns at the right and remainder rows
     * at the low-Y edge. Too many rows or columns can produce zero-sized regions.
     *
     * @param texture nonnull source texture
     * @param rows positive row count
     * @param columns positive column count
     * @return independent region grid sharing the source texture
     * @throws NullPointerException if texture is null
     * @throws IllegalArgumentException if rows or columns are nonpositive
     */
    public static TextureRegion[][] split(Texture texture, int rows, int columns) {
        if (texture == null) throw new NullPointerException("Texture cannot be null");
        if (rows <= 0) throw new IllegalArgumentException("rows must be > 0");
        if (columns <= 0) throw new IllegalArgumentException("columns must be > 0");

        int textureWidth = texture.getWidth();
        int textureHeight = texture.getHeight();

        int regionWidth = textureWidth / columns;
        int regionHeight = textureHeight / rows;

        TextureRegion[][] regions = new TextureRegion[rows][columns];

        for (int row = 0; row < rows; row++) {
            for (int column = 0; column < columns; column++) {
                int x = column * regionWidth;
                int y = textureHeight - (row + 1) * regionHeight;
                regions[row][column] = new TextureRegion(texture, x, y, regionWidth, regionHeight);
            }
        }

        return regions;
    }

    /**
     * Immutable integer pixel-edge coordinate used as a graph key during boundary
     * stitching. Equality and hashing compare coordinate values.
     * <p>Pixel-edge coordinates identify corners between image pixels rather than pixel-center
     * samples. Value equality lets independently created boundary segments meet at a
     * shared graph vertex.</p>
     *
     * @author Albert Beaupre
     */
    private static final class Point {
        final int x; // Horizontal pixel-edge coordinate.
        final int y; // Vertical pixel-edge coordinate.

        /**
         * Stores an integer pixel-edge coordinate.
         *
         * @param x horizontal coordinate
         * @param y vertical coordinate
         */
        Point(int x, int y) {
            this.x = x;
            this.y = y;
        }

        /**
         * Compares coordinate values with another internal point.
         *
         * @param obj candidate point
         * @return true for matching X and Y coordinates
         */
        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (!(obj instanceof Point other))
                return false;
            return x == other.x && y == other.y;
        }

        /**
         * Combines coordinate values consistently with equality for boundary lookup.
         *
         * @return coordinate hash
         */
        @Override
        public int hashCode() {
            return 31 * x + y;
        }
    }

    /**
     * Directed pixel-boundary segment with a mutable traversal marker. Endpoints
     * are immutable point values; stitching consumes each edge at most once.
     * <p>Direction preserves the orientation of the extracted pixel outline. The used flag
     * belongs to one stitching traversal and does not alter endpoint coordinates or
     * change the source image.</p>
     *
     * @author Albert Beaupre
     */
    private static final class Edge {
        final Point a; // Directed edge start.
        final Point b; // Directed edge end.
        boolean used; // Whether loop stitching has consumed this edge.

        /**
         * Stores directed boundary endpoints with an initially unused traversal marker.
         *
         * @param a start point
         * @param b end point
         */
        Edge(Point a, Point b) {
            this.a = a;
            this.b = b;
        }
    }
}
