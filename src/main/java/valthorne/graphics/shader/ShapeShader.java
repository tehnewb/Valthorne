package valthorne.graphics.shader;

import valthorne.graphics.bgfx.BgfxGraphics;
import valthorne.graphics.bgfx.BgfxMeshRenderer;

import static org.lwjgl.bgfx.BGFX.*;

import org.lwjgl.BufferUtils;
import valthorne.Window;
import valthorne.graphics.Color;
import org.joml.Vector2f;
import valthorne.math.geometry.Shape;

import java.nio.FloatBuffer;
import java.util.Collection;

/**
 * A simple, general-purpose shader for drawing {@link Shape} polygons with explicit
 * vertex buffers and projection uniforms.
 *
 * <p>Filled polygons are rendered as triangle fans built from the shape centroid and
 * border paths are rendered as line loops when requested.</p>
 *
 * @author Albert Beaupre
 * @since April 9th, 2026
 */
public class ShapeShader extends Shader {

    /**
     * Uniform name for RGBA fill or border tint.
     */
    private static final String UNIFORM_COLOR = "u_color";
    /**
     * Uniform name for the engine's current projection matrix.
     */
    private static final String UNIFORM_MVP = "u_mvp";

    private final BgfxMeshRenderer bgfxRenderer; // Owned procedural-mesh path, owned by this renderer.
    private final int[] viewport = new int[4]; // Reused physical viewport for pixel-width polygon borders.
    private FloatBuffer vertexBuffer = BufferUtils.createFloatBuffer(32); // Reusable direct CPU position staging buffer.
    private int floatCapacity = 32; // Current CPU/GPU staging capacity in floats.

    /**
     * Compiles the bundled polygon shader and allocates an owned VAO/VBO with
     * two-float position attributes. Requires a current graphics device and leaves array
     * bindings at zero.
     */
    public ShapeShader() {
        super("engine/shape.vert", "engine/shape.frag");
        bgfxRenderer = new BgfxMeshRenderer(2);
    }

    /**
     * Draws a shape's centroid triangle fan and optional positive-width line-loop
     * border using the current Window projection. Skips null shapes and boundaries
     * with fewer than three points. Fan filling assumes a suitable polygon and does
     * not tessellate arbitrary concavity. Leaves the shader and vertex array unbound;
     * border drawing changes GL line width.
     *
     * @param shape borrowed polygon to draw
     */
    public void draw(Shape shape) {
        if (shape == null) return;

        Vector2f[] points = shape.points();
        if (points == null || points.length < 3) return;

        bind();
        setUniformMatrix4(UNIFORM_MVP, Window.getProjectionMatrix());

        try {
            drawShape(points, shape.getColor());

            if (shape.hasBorder() && shape.getBorder() != null) {
                Color borderColor = shape.getBorder().getColor();
                float thickness = shape.getBorder().getThickness();
                if (borderColor != null && thickness > 0f) {
                    drawBorder(points, borderColor, thickness);
                }
            }
        } finally {

            unbind();
        }
    }

    /**
     * Draws valid shapes in collection order under one shader bind. Each shape still
     * uploads and draws its own fan and optional border. Null collection, empty input,
     * null entries, or fewer than three points are skipped.
     *
     * @param shapes borrowed polygon collection
     */
    public void drawAll(Collection<? extends Shape> shapes) {
        if (shapes == null || shapes.isEmpty()) return;

        bind();
        setUniformMatrix4(UNIFORM_MVP, Window.getProjectionMatrix());

        try {
            for (Shape shape : shapes) {
                if (shape == null) continue;

                Vector2f[] points = shape.points();
                if (points == null || points.length < 3) continue;

                drawShape(points, shape.getColor());

                if (shape.hasBorder() && shape.getBorder() != null) {
                    Color borderColor = shape.getBorder().getColor();
                    float thickness = shape.getBorder().getThickness();
                    if (borderColor != null && thickness > 0f) {
                        drawBorder(points, borderColor, thickness);
                    }
                }
            }
        } finally {

            unbind();
        }
    }

    /**
     * Disposes the shader program and owned vertex-array/buffer names. Requires
     * the graphics device. This override does not clear its numeric object names, so
     * callers should dispose the instance once.
     */
    @Override
    public void dispose() {
        super.dispose();
        bgfxRenderer.close();
    }

    /**
     * Uses the arithmetic mean of boundary vertices as a fan center, appends the
     * boundary and repeated first point, uploads them, and draws a filled fan.
     * Null color selects white. The caller has bound this shader and vertex array.
     *
     * @param points ordered boundary with at least three nonnull points
     * @param color fill tint, or null for white
     */
    private void drawShape(Vector2f[] points, Color color) {
        float cx = 0f;
        float cy = 0f;
        for (Vector2f point : points) {
            cx += point.x();
            cy += point.y();
        }
        cx /= points.length;
        cy /= points.length;

        setColorUniform(color != null ? color : Color.WHITE);

        ensureCapacity(points.length * 6);
        vertexBuffer.clear();
        for (int i = 0; i < points.length; i++) {
            Vector2f a = points[i];
            Vector2f b = points[(i + 1) % points.length];
            vertexBuffer.put(cx).put(cy).put(a.x).put(a.y).put(b.x).put(b.y);
        }
        vertexBuffer.flip();
        submitBgfx();
    }

    /**
     * Uploads the boundary as a GL line loop and sets the requested line width.
     * Does not restore the prior width; supported width ranges depend on the context.
     *
     * @param points ordered polygon boundary
     * @param color nonnull border tint
     * @param thickness requested GL line width
     */
    private void drawBorder(Vector2f[] points, Color color, float thickness) {
        setColorUniform(color);
        ensureCapacity(points.length * 12);
        vertexBuffer.clear();
        float[] matrix = Window.getProjectionMatrix();
        BgfxGraphics.getViewport(viewport);
        for (int i = 0; i < points.length; i++) {
            Vector2f a = points[i];
            Vector2f b = points[(i + 1) % points.length];
            float dx = b.x - a.x;
            float dy = b.y - a.y;
            float px = (matrix[0] * dx + matrix[4] * dy) * viewport[2] * .5f;
            float py = (matrix[1] * dx + matrix[5] * dy) * viewport[3] * .5f;
            float length = (float) Math.hypot(px, py);
            if (length == 0) continue;
            float determinant = matrix[0] * matrix[5] - matrix[4] * matrix[1];
            if (determinant == 0) continue;
            float nx = -py / length * thickness / viewport[2];
            float ny = px / length * thickness / viewport[3];
            float ox = (matrix[5] * nx - matrix[4] * ny) / determinant;
            float oy = (-matrix[1] * nx + matrix[0] * ny) / determinant;
            vertexBuffer.put(a.x + ox).put(a.y + oy).put(a.x - ox).put(a.y - oy).put(b.x - ox).put(b.y - oy);
            vertexBuffer.put(b.x - ox).put(b.y - oy).put(b.x + ox).put(b.y + oy).put(a.x + ox).put(a.y + oy);
        }
        vertexBuffer.flip();
        submitBgfx();
    }

    /**
     * Copies RGBA components into the currently bound shader's color uniform.
     *
     * @param color nonnull tint
     */
    private void setColorUniform(Color color) {
        setUniform4f(UNIFORM_COLOR, color.r(), color.g(), color.b(), color.a());
    }

    /**
     * Replaces CPU/GPU staging storage when needed, choosing the larger of the
     * requested capacity or twice the old capacity. Existing contents are discarded
     * because the next draw writes a complete replacement.
     *
     * @param requiredFloats required two-component position storage
     */
    private void ensureCapacity(int requiredFloats) {
        if (requiredFloats <= floatCapacity) return;

        floatCapacity = Math.max(requiredFloats, floatCapacity * 2);
        vertexBuffer = BufferUtils.createFloatBuffer(floatCapacity);

    }

    /**
     * Submits the staged triangles using straight-alpha source-over blending.
     */
    private void submitBgfx() {
        bgfxRenderer.submit(vertexBuffer, this, BGFX_STATE_WRITE_RGB | BGFX_STATE_WRITE_A | BGFX_STATE_BLEND_FUNC_SEPARATE(BGFX_STATE_BLEND_SRC_ALPHA, BGFX_STATE_BLEND_INV_SRC_ALPHA, BGFX_STATE_BLEND_ONE, BGFX_STATE_BLEND_INV_SRC_ALPHA));
    }
}
