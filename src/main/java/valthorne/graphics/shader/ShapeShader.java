package valthorne.graphics.shader;

import org.lwjgl.BufferUtils;
import valthorne.Window;
import valthorne.graphics.Color;
import org.joml.Vector2f;
import valthorne.math.geometry.Shape;

import java.nio.FloatBuffer;
import java.util.Collection;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL15.*;
import static org.lwjgl.opengl.GL20.glEnableVertexAttribArray;
import static org.lwjgl.opengl.GL20.glVertexAttribPointer;
import static org.lwjgl.opengl.GL30.glBindVertexArray;
import static org.lwjgl.opengl.GL30.glGenVertexArrays;

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
     * Bundled polygon vertex source loaded once at class initialization.
     */
    private static final String VERT_SRC = ShaderSources.load("core/shape.vert");

    /**
     * Bundled solid-color fragment source loaded once at class initialization.
     */
    private static final String FRAG_SRC = ShaderSources.load("core/shape.frag");

    /**
     * Attribute location for two-float polygon positions.
     */
    private static final int ATTR_POSITION = 0;
    /**
     * Uniform name for RGBA fill or border tint.
     */
    private static final String UNIFORM_COLOR = "u_color";
    /**
     * Uniform name for the engine's current projection matrix.
     */
    private static final String UNIFORM_MVP = "u_mvp";

    private final int vao; // Owned polygon vertex-array name.
    private final int vbo; // Owned streaming position-buffer name.
    private FloatBuffer vertexBuffer = BufferUtils.createFloatBuffer(32); // Reusable direct CPU position staging buffer.
    private int floatCapacity = 32; // Current CPU/GPU staging capacity in floats.

    /**
     * Compiles the bundled polygon shader and allocates an owned VAO/VBO with
     * two-float position attributes. Requires a current GL context and leaves array
     * bindings at zero.
     */
    public ShapeShader() {
        super(VERT_SRC, FRAG_SRC);

        vao = glGenVertexArrays();
        vbo = glGenBuffers();

        glBindVertexArray(vao);
        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, (long) floatCapacity * Float.BYTES, GL_STREAM_DRAW);
        glEnableVertexAttribArray(ATTR_POSITION);
        glVertexAttribPointer(ATTR_POSITION, 2, GL_FLOAT, false, (int) (2L * Float.BYTES), 0L);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
        glBindVertexArray(0);
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
        glBindVertexArray(vao);

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
            glBindVertexArray(0);
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
        glBindVertexArray(vao);

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
            glBindVertexArray(0);
            unbind();
        }
    }

    /**
     * Disposes the shader program and owned vertex-array/buffer names. Requires
     * the GL context. This override does not clear its numeric object names, so
     * callers should dispose the instance once.
     */
    @Override
    public void dispose() {
        super.dispose();
        if (vao != 0) org.lwjgl.opengl.GL30.glDeleteVertexArrays(vao);
        if (vbo != 0) org.lwjgl.opengl.GL15.glDeleteBuffers(vbo);
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

        int vertexCount = points.length + 2;
        ensureCapacity(vertexCount * 2);
        vertexBuffer.clear();
        vertexBuffer.put(cx).put(cy);
        for (Vector2f point : points) {
            vertexBuffer.put(point.x()).put(point.y());
        }
        vertexBuffer.put(points[0].x()).put(points[0].y());
        vertexBuffer.flip();

        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferSubData(GL_ARRAY_BUFFER, 0, vertexBuffer);
        glDrawArrays(GL_TRIANGLE_FAN, 0, vertexCount);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
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
        glLineWidth(thickness);

        int vertexCount = points.length;
        ensureCapacity(vertexCount * 2);
        vertexBuffer.clear();
        for (Vector2f point : points) {
            vertexBuffer.put(point.x()).put(point.y());
        }
        vertexBuffer.flip();

        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferSubData(GL_ARRAY_BUFFER, 0, vertexBuffer);
        glDrawArrays(GL_LINE_LOOP, 0, vertexCount);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
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

        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, (long) floatCapacity * Float.BYTES, GL_STREAM_DRAW);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
    }
}
