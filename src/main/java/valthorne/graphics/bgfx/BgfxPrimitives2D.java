package valthorne.graphics.bgfx;

import org.joml.Vector2f;
import org.lwjgl.bgfx.BGFXVertexLayout;
import valthorne.graphics.Color;
import valthorne.math.geometry.Border;
import valthorne.math.geometry.Circle;
import valthorne.math.geometry.Rectangle;
import valthorne.math.geometry.Shape;

import java.nio.ByteBuffer;

import static org.lwjgl.bgfx.BGFX.*;
import static org.lwjgl.system.MemoryUtil.memAlloc;
import static org.lwjgl.system.MemoryUtil.memFree;

/**
 * Recognizes live rectangle and canonical sampled-circle boundaries and batches
 * both through one shared quad. Each instance contains two vec4 records: bounds
 * and RGBA. A negative width identifies a circle, whose fourth bound component
 * contains its perimeter sample count. The fragment shader clips to the same
 * regular polygon rather than silently replacing it with a smooth disc.
 *
 * <p>Point edits, subclasses, borders and unsupported sample counts fall back to
 * polygon tessellation. Every circle boundary is checked against reusable double
 * trigonometric samples, matching Circle's coordinate rounding. MSAA circles also
 * fall back, retaining triangle-edge sample coverage. Scratch tables allocate only
 * for new segment counts. GPU geometry and instance staging have explicit lifetime.</p>
 */
final class BgfxPrimitives2D implements AutoCloseable {
    private final BgfxDevice device; // Borrowed runtime and packed vertex declaration.
    private final BgfxVertexBuffer instances; // Owned thirty-two-byte instance staging and GPU buffer.
    private final double[][] cosines = new double[1_025][]; // Reused canonical circle X samples by segment count.
    private final double[][] sines = new double[1_025][]; // Reused canonical circle Y samples by segment count.
    private BgfxMesh mesh; // Lazily owned unit quad shared by rectangle and circle draws.

    /**
     * Creates compact instance storage without uploading a quad until first use.
     *
     * @param device live owning runtime
     */
    BgfxPrimitives2D(BgfxDevice device) {
        this.device = device;
        try (BGFXVertexLayout layout = BGFXVertexLayout.calloc()) {
            bgfx_vertex_layout_begin(layout, BGFX_RENDERER_TYPE_OPENGL);
            bgfx_vertex_layout_add(layout, BGFX_ATTRIB_TEXCOORD0, (byte) 4, BGFX_ATTRIB_TYPE_FLOAT, false, false);
            bgfx_vertex_layout_add(layout, BGFX_ATTRIB_TEXCOORD1, (byte) 4, BGFX_ATTRIB_TYPE_FLOAT, false, false);
            bgfx_vertex_layout_end(layout);
            instances = new BgfxVertexBuffer(layout, 16_384);
        }
    }

    /**
     * Attempts compact submission while preserving the shape's current boundary.
     *
     * @param shape borrowed shape to inspect
     * @param renderer ordered command destination
     * @param circles whether sample coverage permits shader-clipped circles
     * @return true when handled, including invisible or degenerate fills
     */
    boolean draw(Shape shape, BgfxShapeRenderer renderer, boolean circles) {
        if (shape.getRotation() != 0) return false;
        Class<?> type = shape.getClass();
        if (type != Rectangle.class && (type != Circle.class || !circles)) return false;
        Border border = shape.getBorder();
        if (border != null && (!Float.isFinite(border.getThickness()) || (border.getColor() != null && border.getThickness() > 0))) return false;
        Vector2f[] points = shape.points();
        Color color = shape.getColor();
        if (color == null) color = Color.WHITE;
        if (type == Rectangle.class) return rectangle(points, color, renderer);
        return circle((Circle) shape, points, color, renderer);
    }

    /**
     * Checks the live corners rather than assuming bounds setters describe them.
     *
     * @param points borrowed current rectangle boundary
     * @param color copied tint
     * @param renderer ordered command destination
     * @return whether this boundary is an axis-aligned rectangle
     */
    private boolean rectangle(Vector2f[] points, Color color, BgfxShapeRenderer renderer) {
        if (points == null || points.length != 4) return false;
        for (int i = 0; i < 4; i++)
            if (points[i] == null || !points[i].isFinite()) return false;
        Vector2f a = points[0];
        Vector2f b = points[1];
        Vector2f c = points[2];
        Vector2f d = points[3];
        if (a.y != b.y || b.x != c.x || c.y != d.y || d.x != a.x) return false;
        float x = Math.min(a.x, b.x);
        float y = Math.min(a.y, d.y);
        float width = Math.abs(b.x - a.x);
        float height = Math.abs(d.y - a.y);
        if (!Float.isFinite(width) || !Float.isFinite(height)) return false;
        if (width == 0 || height == 0 || color.getAlpha() == 0) return true;
        ensureMesh();
        renderer.appendPrimitive(mesh, x, y, width, height, color);
        return true;
    }

    /**
     * Checks every perimeter position against Circle's defining fields. External
     * edits trigger tessellation on the same draw, with no stale cache revision.
     *
     * @param circle borrowed circle parameters
     * @param points borrowed live boundary
     * @param color copied tint
     * @param renderer ordered command destination
     * @return whether the sampled boundary is canonical and supported
     */
    private boolean circle(Circle circle, Vector2f[] points, Color color, BgfxShapeRenderer renderer) {
        int segments = circle.getSegments();
        if (segments < 3 || segments > 1_024 || points == null || points.length != segments) return false;
        float radius = circle.getRadius();
        float centerX = circle.getX() + radius;
        float centerY = circle.getY() + radius;
        float diameter = radius * 2;
        if (!Float.isFinite(centerX) || !Float.isFinite(centerY) || !Float.isFinite(diameter)) return false;
        if (cosines[segments] == null) samples(segments);
        double[] cos = cosines[segments];
        double[] sin = sines[segments];
        for (int i = 0; i < segments; i++) {
            Vector2f point = points[i];
            if (point == null || point.x != (float) (centerX + cos[i] * radius) || point.y != (float) (centerY + sin[i] * radius)) return false;
        }
        if (radius == 0 || color.getAlpha() == 0) return true;
        ensureMesh();
        renderer.appendPrimitive(mesh, circle.getX(), circle.getY(), -diameter, segments, color);
        return true;
    }

    /**
     * Builds canonical double samples using the same operation order as Circle.
     *
     * @param segments supported perimeter sample count
     */
    private void samples(int segments) {
        double[] cos = new double[segments];
        double[] sin = new double[segments];
        double step = Math.PI * 2 / segments;
        for (int i = 0; i < segments; i++) {
            double angle = i * step;
            cos[i] = Math.cos(angle);
            sin[i] = Math.sin(angle);
        }
        cosines[segments] = cos;
        sines[segments] = sin;
    }

    /**
     * Uploads a unit quad once, or again after explicitly clearing mesh caches.
     */
    private void ensureMesh() {
        if (mesh != null) return;
        ByteBuffer data = memAlloc(96);
        try {
            BgfxVertexFormat.vertex(data, 0, 0, 0, -1);
            BgfxVertexFormat.vertex(data, 1, 0, 0, -1);
            BgfxVertexFormat.vertex(data, 1, 1, 0, -1);
            BgfxVertexFormat.vertex(data, 0, 0, 0, -1);
            BgfxVertexFormat.vertex(data, 1, 1, 0, -1);
            BgfxVertexFormat.vertex(data, 0, 1, 0, -1);
            mesh = new BgfxMesh(data, device.layout());
        } finally {
            memFree(data);
        }
    }

    /**
     * Returns the current shared quad for identifying compact command ranges.
     *
     * @return borrowed quad, or null before its first use
     */
    BgfxMesh mesh() {
        return mesh;
    }

    /**
     * Returns the compact instance stream borrowed by ordered submission.
     *
     * @return owned stream, which callers must not close
     */
    BgfxVertexBuffer instances() {
        return instances;
    }

    /**
     * Releases the unit quad outside an open batch without releasing stream capacity.
     */
    void clearMesh() {
        if (mesh == null) return;
        mesh.close();
        mesh = null;
    }

    @Override
    public void close() {
        clearMesh();
        instances.close();
    }
}
