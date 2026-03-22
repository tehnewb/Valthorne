package valthorne.graphics.texture;

import valthorne.graphics.Color;
import valthorne.math.Vector2f;

import java.nio.ByteBuffer;
import java.util.*;

/**
 * A utility class for performing operations related to textures.
 */
public class TextureUtility {

    /**
     * Traces the contours of non-ignored areas within a given texture and returns the points
     * defining the largest closed contour as an array of {@code Vector2f} objects.
     *
     * @param texture the texture to be analyzed; must not be null
     * @param ignore  the colors to be ignored during the tracing process; optional parameter
     * @return an array of {@code Vector2f} objects representing the traced points of the largest
     * loop contour; returns an empty array if the texture has invalid data or no valid loops are found
     * @throws NullPointerException if the provided texture is null
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

    private static List<Point> removeDuplicateClosingPoint(List<Point> loop) {
        if (loop.size() > 1 && loop.getFirst().equals(loop.getLast()))
            return new ArrayList<>(loop.subList(0, loop.size() - 1));
        return loop;
    }

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

    private static float area(List<Point> points) {
        float sum = 0f;
        for (int i = 0; i < points.size(); i++) {
            Point a = points.get(i);
            Point b = points.get((i + 1) % points.size());
            sum += (float) a.x * b.y - (float) b.x * a.y;
        }
        return sum * 0.5f;
    }

    private static final class Point {
        final int x;
        final int y;

        Point(int x, int y) {
            this.x = x;
            this.y = y;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (!(obj instanceof Point other))
                return false;
            return x == other.x && y == other.y;
        }

        @Override
        public int hashCode() {
            return 31 * x + y;
        }
    }

    private static final class Edge {
        final Point a;
        final Point b;
        boolean used;

        Edge(Point a, Point b) {
            this.a = a;
            this.b = b;
        }
    }

    /**
     * Splits a given texture into a 2D array of smaller {@code TextureRegion} objects based on the specified
     * number of rows and columns.
     *
     * @param texture the texture to be split; must not be null
     * @param rows    the number of rows to divide the texture into; must be greater than 0
     * @param columns the number of columns to divide the texture into; must be greater than 0
     * @return a 2D array of {@code TextureRegion} objects representing the divided regions of the texture
     * @throws NullPointerException     if the provided texture is null
     * @throws IllegalArgumentException if rows or columns are less than or equal to zero
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
                int y = row * regionHeight;
                regions[row][column] = new TextureRegion(texture, x, y, regionWidth, regionHeight);
            }
        }

        return regions;
    }
}