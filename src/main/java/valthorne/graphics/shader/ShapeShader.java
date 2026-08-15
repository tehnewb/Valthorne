package valthorne.graphics.shader;

import org.lwjgl.BufferUtils;
import valthorne.Window;
import valthorne.graphics.Color;
import valthorne.math.Vector2f;
import valthorne.math.geometry.Shape;

import java.nio.FloatBuffer;
import java.util.Collection;

import static org.lwjgl.opengl.GL11.GL_FLOAT;
import static org.lwjgl.opengl.GL11.GL_LINE_LOOP;
import static org.lwjgl.opengl.GL11.GL_TRIANGLE_FAN;
import static org.lwjgl.opengl.GL11.glDrawArrays;
import static org.lwjgl.opengl.GL11.glLineWidth;
import static org.lwjgl.opengl.GL15.GL_ARRAY_BUFFER;
import static org.lwjgl.opengl.GL15.GL_STREAM_DRAW;
import static org.lwjgl.opengl.GL15.glBindBuffer;
import static org.lwjgl.opengl.GL15.glBufferData;
import static org.lwjgl.opengl.GL15.glBufferSubData;
import static org.lwjgl.opengl.GL15.glGenBuffers;
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

    private static final String VERT_SRC = """
            #version 330 core

            layout(location = 0) in vec2 a_position;

            uniform mat4 u_mvp;

            void main() {
                gl_Position = u_mvp * vec4(a_position, 0.0, 1.0);
            }
            """;

    private static final String FRAG_SRC = """
            #version 330 core

            uniform vec4 u_color;

            out vec4 fragColor;

            void main() {
                fragColor = u_color;
            }
            """;

    private static final int ATTR_POSITION = 0;
    private static final String UNIFORM_COLOR = "u_color";
    private static final String UNIFORM_MVP = "u_mvp";

    private final int vao;
    private final int vbo;
    private FloatBuffer vertexBuffer = BufferUtils.createFloatBuffer(32);
    private int floatCapacity = 32;

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
     * Draws a single shape using its fill color and optional border.
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
     * Draws a collection of shapes in a single bind, which is more efficient
     * when rendering many shapes per frame.
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

    @Override
    public void dispose() {
        super.dispose();
        if (vao != 0) org.lwjgl.opengl.GL30.glDeleteVertexArrays(vao);
        if (vbo != 0) org.lwjgl.opengl.GL15.glDeleteBuffers(vbo);
    }

    private void drawShape(Vector2f[] points, Color color) {
        float cx = 0f;
        float cy = 0f;
        for (Vector2f point : points) {
            cx += point.getX();
            cy += point.getY();
        }
        cx /= points.length;
        cy /= points.length;

        setColorUniform(color != null ? color : Color.WHITE);

        int vertexCount = points.length + 2;
        ensureCapacity(vertexCount * 2);
        vertexBuffer.clear();
        vertexBuffer.put(cx).put(cy);
        for (Vector2f point : points) {
            vertexBuffer.put(point.getX()).put(point.getY());
        }
        vertexBuffer.put(points[0].getX()).put(points[0].getY());
        vertexBuffer.flip();

        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferSubData(GL_ARRAY_BUFFER, 0, vertexBuffer);
        glDrawArrays(GL_TRIANGLE_FAN, 0, vertexCount);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
    }

    private void drawBorder(Vector2f[] points, Color color, float thickness) {
        setColorUniform(color);
        glLineWidth(thickness);

        int vertexCount = points.length;
        ensureCapacity(vertexCount * 2);
        vertexBuffer.clear();
        for (Vector2f point : points) {
            vertexBuffer.put(point.getX()).put(point.getY());
        }
        vertexBuffer.flip();

        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferSubData(GL_ARRAY_BUFFER, 0, vertexBuffer);
        glDrawArrays(GL_LINE_LOOP, 0, vertexCount);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
    }

    private void setColorUniform(Color color) {
        setUniform4f(UNIFORM_COLOR, color.r(), color.g(), color.b(), color.a());
    }

    private void ensureCapacity(int requiredFloats) {
        if (requiredFloats <= floatCapacity) return;

        floatCapacity = Math.max(requiredFloats, floatCapacity * 2);
        vertexBuffer = BufferUtils.createFloatBuffer(floatCapacity);

        glBindBuffer(GL_ARRAY_BUFFER, vbo);
        glBufferData(GL_ARRAY_BUFFER, (long) floatCapacity * Float.BYTES, GL_STREAM_DRAW);
        glBindBuffer(GL_ARRAY_BUFFER, 0);
    }
}
