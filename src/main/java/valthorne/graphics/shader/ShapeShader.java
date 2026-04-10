package valthorne.graphics.shader;

import valthorne.graphics.Color;
import valthorne.math.Vector2f;
import valthorne.math.geometry.Shape;

import java.util.Collection;

import static org.lwjgl.opengl.GL11.*;

/**
 * A simple, general-purpose shader for drawing any {@link Shape} implementor
 * from the {@code valthorne.math.geometry} package.
 *
 * <p>Rendering behavior:</p>
 * <ul>
 *   <li>Fills the shape using a triangle fan built from the shape's centroid and vertices.</li>
 *   <li>Optionally draws the shape's border if {@link Shape#hasBorder()} is true.</li>
 *   <li>Uses a single uniform color per draw call (no texturing).</li>
 * </ul>
 *
 * <p>Typical usage:</p>
 * <pre>{@code
 * ShapeShader shader = new ShapeShader();
 * shader.draw(myShape);
 * }</code></pre>
 *
 * <p>This shader does not sample any textures; it relies solely on a uniform color.
 * It is designed to integrate smoothly with the existing fixed-function style
 * vertex submission used elsewhere in the engine.</p>
 *
 * @author Albert Beaupre
 * @since April 9th, 2026
 */
public class ShapeShader extends Shader {

    private static final String VERT_SRC = """
            #version 120
            // Use the fixed-function transform for compatibility with existing matrices
            varying vec4 v_color;
            uniform vec4 u_color; // RGBA for the current shape fill or border
            void main(){
                gl_Position = ftransform();
                v_color = u_color; // pass-through
            }
            """;

    private static final String FRAG_SRC = """
            #version 120
            varying vec4 v_color;
            void main(){
                gl_FragColor = v_color;
            }
            """;

    public ShapeShader() {
        super(VERT_SRC, FRAG_SRC);
    }

    /**
     * Draws a single shape using its fill color and optional border.
     */
    public void draw(Shape shape) {
        if (shape == null) return;
        Vector2f[] pts = shape.points();
        if (pts == null || pts.length < 3) return; // Need at least a triangle

        // Bind once for fill
        bind();

        // Disable texturing to avoid state interference from other pipelines
        glDisable(GL_TEXTURE_2D);

        // Fill pass
        Color c = shape.getColor();
        setUniform4f("u_color", c.r(), c.g(), c.b(), c.a());

        // Build a triangle fan from centroid
        float cx = 0f, cy = 0f;
        for (Vector2f p : pts) {
            cx += p.getX();
            cy += p.getY();
        }
        cx /= pts.length;
        cy /= pts.length;

        glBegin(GL_TRIANGLE_FAN);
        glVertex2f(cx, cy);
        for (Vector2f p : pts) glVertex2f(p.getX(), p.getY());
        glVertex2f(pts[0].getX(), pts[0].getY()); // close the fan
        glEnd();

        // Optional border pass
        if (shape.hasBorder()) {
            Color bc = shape.getBorder().getColor();
            float t = shape.getBorder().getThickness();
            if (bc != null && t > 0f) {
                setUniform4f("u_color", bc.r(), bc.g(), bc.b(), bc.a());
                glLineWidth(t);
                glBegin(GL_LINE_LOOP);
                for (Vector2f p : pts) glVertex2f(p.getX(), p.getY());
                glEnd();
            }
        }

        glEnable(GL_TEXTURE_2D);
        unbind();
    }

    /**
     * Draws a collection of shapes in a single bind, which is more efficient
     * when rendering many shapes per frame.
     */
    public void drawAll(Collection<? extends Shape> shapes) {
        if (shapes == null || shapes.isEmpty()) return;

        bind();
        glDisable(GL_TEXTURE_2D);

        for (Shape shape : shapes) {
            if (shape == null) continue;
            Vector2f[] pts = shape.points();
            if (pts == null || pts.length < 3) continue;

            // Fill
            Color c = shape.getColor();
            setUniform4f("u_color", c.r(), c.g(), c.b(), c.a());

            float cx = 0f, cy = 0f;
            for (Vector2f p : pts) { cx += p.getX(); cy += p.getY(); }
            cx /= pts.length; cy /= pts.length;

            glBegin(GL_TRIANGLE_FAN);
            glVertex2f(cx, cy);
            for (Vector2f p : pts) glVertex2f(p.getX(), p.getY());
            glVertex2f(pts[0].getX(), pts[0].getY());
            glEnd();

            // Border
            if (shape.hasBorder()) {
                Color bc = shape.getBorder().getColor();
                float t = shape.getBorder().getThickness();
                if (bc != null && t > 0f) {
                    setUniform4f("u_color", bc.r(), bc.g(), bc.b(), bc.a());
                    glLineWidth(t);
                    glBegin(GL_LINE_LOOP);
                    for (Vector2f p : pts) glVertex2f(p.getX(), p.getY());
                    glEnd();
                }
            }
        }

        glEnable(GL_TEXTURE_2D);
        unbind();
    }
}
