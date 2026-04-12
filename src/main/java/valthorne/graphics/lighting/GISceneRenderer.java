package valthorne.graphics.lighting;

import valthorne.graphics.Color;
import valthorne.graphics.shader.Shader;
import valthorne.math.Vector2f;
import valthorne.math.geometry.Shape;

import java.util.Collection;

import static org.lwjgl.opengl.GL11.*;
import static org.lwjgl.opengl.GL14.glBlendFuncSeparate;

/**
 * Renders shape-based scene descriptions into a {@link GISceneBuffer}.
 */
public final class GISceneRenderer {

    private static final String VERTEX_SOURCE = """
            #version 120
            uniform vec4 u_color;
            varying vec4 v_color;

            void main() {
                gl_Position = ftransform();
                v_color = u_color;
            }
            """;

    private static final String FRAGMENT_SOURCE = """
            #version 120
            varying vec4 v_color;

            void main() {
                gl_FragColor = v_color;
            }
            """;

    private final Shader shader;
    private boolean drawing;
    private boolean additiveBlend;

    public GISceneRenderer() {
        shader = new Shader(VERTEX_SOURCE, FRAGMENT_SOURCE);
        shader.reload();
    }

    public void begin(GISceneBuffer buffer) {
        if (buffer == null) throw new NullPointerException("buffer cannot be null");
        if (drawing) throw new IllegalStateException("begin() called while already drawing");

        buffer.begin();

        glDisable(GL_TEXTURE_2D);
        glDisable(GL_DEPTH_TEST);
        glDisable(GL_BLEND);

        glMatrixMode(GL_PROJECTION);
        glPushMatrix();
        glLoadIdentity();
        glOrtho(0, buffer.getWidth(), 0, buffer.getHeight(), -1, 1);

        glMatrixMode(GL_MODELVIEW);
        glPushMatrix();
        glLoadIdentity();

        shader.bind();
        drawing = true;
        additiveBlend = false;
    }

    public void draw(GISceneShape sceneShape) {
        if (sceneShape == null) return;
        draw(sceneShape.shape(), sceneShape.emission(), sceneShape.transmittance(), sceneShape.additive());
    }

    public void drawAll(Collection<? extends GISceneShape> sceneShapes) {
        if (sceneShapes == null || sceneShapes.isEmpty()) return;
        for (GISceneShape sceneShape : sceneShapes) {
            draw(sceneShape);
        }
    }

    public void draw(Shape shape, Color emission, float transmittance, boolean additive) {
        if (!drawing) {
            throw new IllegalStateException("draw() requires begin() to be called first");
        }
        if (shape == null || emission == null) {
            return;
        }
        if (transmittance < 0f || transmittance > 1f) {
            throw new IllegalArgumentException("transmittance must be in [0, 1]");
        }

        setAdditiveBlend(additive);
        drawShape(shape, emission.r(), emission.g(), emission.b(), transmittance);
    }

    public void end(GISceneBuffer buffer) {
        if (!drawing) throw new IllegalStateException("end() called without begin()");
        if (buffer == null) throw new NullPointerException("buffer cannot be null");

        shader.unbind();

        glDisable(GL_BLEND);
        glEnable(GL_TEXTURE_2D);

        glMatrixMode(GL_PROJECTION);
        glPopMatrix();
        glMatrixMode(GL_MODELVIEW);
        glPopMatrix();

        buffer.end();
        drawing = false;
        additiveBlend = false;
    }

    public void render(GISceneBuffer buffer, Collection<? extends GISceneShape> sceneShapes) {
        begin(buffer);
        drawAll(sceneShapes);
        end(buffer);
    }

    public void dispose() {
        shader.dispose();
    }

    private void setAdditiveBlend(boolean additive) {
        if (additiveBlend == additive) {
            return;
        }

        additiveBlend = additive;
        if (additive) {
            glEnable(GL_BLEND);
            // Add radiance into RGB while preserving the destination transmittance in alpha.
            glBlendFuncSeparate(GL_ONE, GL_ONE, GL_ZERO, GL_ONE);
        } else {
            glDisable(GL_BLEND);
        }
    }

    private void drawShape(Shape shape, float r, float g, float b, float a) {
        Vector2f[] points = shape.points();
        if (points == null || points.length < 3) {
            return;
        }

        float centerX = 0f;
        float centerY = 0f;
        for (Vector2f point : points) {
            centerX += point.getX();
            centerY += point.getY();
        }
        centerX /= points.length;
        centerY /= points.length;

        shader.setUniform4f("u_color", r, g, b, a);

        glBegin(GL_TRIANGLE_FAN);
        glVertex2f(centerX, centerY);
        for (Vector2f point : points) {
            glVertex2f(point.getX(), point.getY());
        }
        glVertex2f(points[0].getX(), points[0].getY());
        glEnd();
    }
}
