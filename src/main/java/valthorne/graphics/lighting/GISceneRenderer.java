package valthorne.graphics.lighting;

import valthorne.graphics.Color;
import valthorne.graphics.shader.Shader;
import valthorne.math.Vector2f;
import valthorne.math.geometry.Shape;

import java.util.Collection;

import static org.lwjgl.opengl.GL11.GL_BLEND;
import static org.lwjgl.opengl.GL11.GL_DEPTH_TEST;
import static org.lwjgl.opengl.GL11.GL_ONE;
import static org.lwjgl.opengl.GL11.GL_ZERO;
import static org.lwjgl.opengl.GL11.GL_TRIANGLES;
import static org.lwjgl.opengl.GL11.glDisable;
import static org.lwjgl.opengl.GL11.glEnable;
import static org.lwjgl.opengl.GL14.glBlendFuncSeparate;

/**
 * Renders shape-based scene descriptions into a {@link GISceneBuffer}.
 */
public final class GISceneRenderer {

    private static final int DEFAULT_MAX_VERTICES = 12288;

    private static final String VERTEX_SOURCE = """
            #version 330 core
            layout (location = 0) in vec2 a_position;
            layout (location = 2) in vec4 a_color;

            uniform vec2 u_sceneSize;

            out vec4 v_color;

            void main() {
                vec2 ndc = vec2(
                    (a_position.x / u_sceneSize.x) * 2.0 - 1.0,
                    (a_position.y / u_sceneSize.y) * 2.0 - 1.0
                );
                gl_Position = vec4(ndc, 0.0, 1.0);
                v_color = a_color;
            }
            """;

    private static final String FRAGMENT_SOURCE = """
            #version 330 core
            in vec4 v_color;

            out vec4 fragColor;

            void main() {
                fragColor = v_color;
            }
            """;

    private final Shader shader;
    private final ShapeMesh mesh;
    private boolean drawing;
    private boolean additiveBlend;

    public GISceneRenderer() {
        this(DEFAULT_MAX_VERTICES);
    }

    public GISceneRenderer(int maxVertices) {
        shader = new Shader(VERTEX_SOURCE, FRAGMENT_SOURCE);
        shader.bindAttribLocation(0, "a_position");
        shader.bindAttribLocation(2, "a_color");
        shader.reload();
        mesh = new ShapeMesh(maxVertices);
    }

    public void begin(GISceneBuffer buffer) {
        if (buffer == null) throw new NullPointerException("buffer cannot be null");
        if (drawing) throw new IllegalStateException("begin() called while already drawing");

        buffer.begin();

        glDisable(GL_DEPTH_TEST);
        glDisable(GL_BLEND);

        shader.bind();
        shader.setUniform2f("u_sceneSize", buffer.getWidth(), buffer.getHeight());
        drawing = true;
        additiveBlend = false;
    }

    public void draw(GISceneShape sceneShape) {
        if (sceneShape == null) {
            return;
        }
        draw(sceneShape.shape(), sceneShape.emission(), sceneShape.transmittance(), sceneShape.additive());
    }

    public void drawAll(Collection<? extends GISceneShape> sceneShapes) {
        if (sceneShapes == null || sceneShapes.isEmpty()) {
            return;
        }
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
        mesh.setShape(shape, emission.r(), emission.g(), emission.b(), transmittance);
        mesh.render();
    }

    public void end(GISceneBuffer buffer) {
        if (!drawing) throw new IllegalStateException("end() called without begin()");
        if (buffer == null) throw new NullPointerException("buffer cannot be null");

        shader.unbind();
        glDisable(GL_BLEND);
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
        mesh.dispose();
        shader.dispose();
    }

    private void setAdditiveBlend(boolean additive) {
        if (additiveBlend == additive) {
            return;
        }

        additiveBlend = additive;
        if (additive) {
            glEnable(GL_BLEND);
            glBlendFuncSeparate(GL_ONE, GL_ONE, GL_ZERO, GL_ONE);
        } else {
            glDisable(GL_BLEND);
        }
    }

    private static final class ShapeMesh extends DynamicMesh2D {

        private ShapeMesh(int maxVertices) {
            super(maxVertices, GL_TRIANGLES);
        }

        private void setShape(Shape shape, float r, float g, float b, float a) {
            Vector2f[] points = shape.points();
            if (points == null || points.length < 3) {
                clearVertices();
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

            beginWrite();
            for (int i = 0; i < points.length; i++) {
                Vector2f current = points[i];
                Vector2f next = points[(i + 1) % points.length];

                putVertex(centerX, centerY, 0f, 0f, r, g, b, a);
                putVertex(current.getX(), current.getY(), 0f, 0f, r, g, b, a);
                putVertex(next.getX(), next.getY(), 0f, 0f, r, g, b, a);
            }
            finishWrite();
        }
    }
}
