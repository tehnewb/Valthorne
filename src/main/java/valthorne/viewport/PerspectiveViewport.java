package valthorne.viewport;

import valthorne.Window;
import valthorne.camera.Camera3D;
import valthorne.camera.PerspectiveCamera;
import valthorne.graphics.DrawFunction;
import valthorne.math.Matrix4f;
import valthorne.math.Ray3f;
import valthorne.math.Vector3f;

import static org.lwjgl.opengl.GL11.GL_VIEWPORT;
import static org.lwjgl.opengl.GL11.glGetIntegerv;
import static org.lwjgl.opengl.GL11.glViewport;

/**
 * Viewport that binds a 3D camera's combined projection-view matrix into the engine.
 *
 * <p>While this viewport is bound, existing 2D shader-based renderers continue to work.
 * Their vertices are rendered on the world XY plane at {@code z = 0}, which makes it
 * easy to mix sprites, shapes, and other 2D content into a 3D camera setup.</p>
 */
public final class PerspectiveViewport {

    private final int[] oldViewport = new int[4];
    private final float[] oldProjectionMatrix = new float[16];
    private final float[] worldProjectionMatrix = new float[16];
    private final Matrix4f overlayProjection = new Matrix4f();
    private final Ray3f screenRay = new Ray3f();
    private final Vector3f screenPoint = new Vector3f();

    private Camera3D camera;
    private int x;
    private int y;
    private int width;
    private int height;
    private float overlayWidth;
    private float overlayHeight;
    private boolean bound;
    private boolean overlayActive;

    public PerspectiveViewport(int width, int height) {
        this(width, height, new PerspectiveCamera());
    }

    public PerspectiveViewport(int width, int height, Camera3D camera) {
        if (camera == null) throw new NullPointerException("camera");
        setBounds(0, 0, width, height);
        this.camera = camera;
        this.overlayWidth = width;
        this.overlayHeight = height;
    }

    public void update(int screenWidth, int screenHeight) {
        setBounds(0, 0, screenWidth, screenHeight);
        if (overlayWidth <= 0f || overlayHeight <= 0f) {
            overlayWidth = screenWidth;
            overlayHeight = screenHeight;
        }
    }

    public void setBounds(int x, int y, int width, int height) {
        if (width <= 0) throw new IllegalArgumentException("width must be > 0");
        if (height <= 0) throw new IllegalArgumentException("height must be > 0");
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
    }

    public void setOverlaySize(float overlayWidth, float overlayHeight) {
        if (overlayWidth <= 0f) throw new IllegalArgumentException("overlayWidth must be > 0");
        if (overlayHeight <= 0f) throw new IllegalArgumentException("overlayHeight must be > 0");
        this.overlayWidth = overlayWidth;
        this.overlayHeight = overlayHeight;
    }

    public void apply() {
        glViewport(x, y, width, height);
        camera.rebuild(width, height);
        Window.setProjectionMatrix(camera.getCombined().get());
    }

    public void bind() {
        if (bound) throw new IllegalStateException("PerspectiveViewport is already bound");
        glGetIntegerv(GL_VIEWPORT, oldViewport);
        Window.copyProjectionMatrix(oldProjectionMatrix);
        apply();
        bound = true;
    }

    public void unbind() {
        if (!bound) return;
        if (overlayActive) {
            endOverlay2D();
        }
        Window.setProjectionMatrix(oldProjectionMatrix);
        glViewport(oldViewport[0], oldViewport[1], oldViewport[2], oldViewport[3]);
        bound = false;
    }

    public void render(DrawFunction function) {
        if (function == null) throw new NullPointerException("function");
        bind();
        try {
            function.draw();
        } finally {
            unbind();
        }
    }

    public void renderWithOverlay(DrawFunction worldFunction, DrawFunction overlayFunction) {
        if (worldFunction == null) throw new NullPointerException("worldFunction");
        if (overlayFunction == null) throw new NullPointerException("overlayFunction");

        bind();
        try {
            worldFunction.draw();
            beginOverlay2D();
            try {
                overlayFunction.draw();
            } finally {
                endOverlay2D();
            }
        } finally {
            unbind();
        }
    }

    public void beginOverlay2D() {
        if (!bound) throw new IllegalStateException("PerspectiveViewport must be bound before beginning an overlay");
        if (overlayActive) throw new IllegalStateException("2D overlay is already active");
        Window.copyProjectionMatrix(worldProjectionMatrix);
        overlayProjection.ortho(0f, overlayWidth, 0f, overlayHeight, -1f, 1f);
        Window.setProjectionMatrix(overlayProjection.get());
        overlayActive = true;
    }

    public void endOverlay2D() {
        if (!overlayActive) return;
        Window.setProjectionMatrix(worldProjectionMatrix);
        overlayActive = false;
    }

    public boolean containsScreenPoint(float screenX, float screenY) {
        return screenX >= x && screenX <= x + width && screenY >= y && screenY <= y + height;
    }

    public Ray3f screenToRay(float screenX, float screenY) {
        if (!containsScreenPoint(screenX, screenY)) {
            return null;
        }
        return camera.screenPointToRay(screenX, screenY, x, y, width, height, screenRay);
    }

    public Vector3f project(Vector3f world) {
        if (world == null) throw new NullPointerException("world");
        return camera.project(world, x, y, width, height, screenPoint);
    }

    public Vector3f unproject(float screenX, float screenY, float depth) {
        if (!containsScreenPoint(screenX, screenY)) {
            return null;
        }
        return camera.unproject(screenX, screenY, depth, x, y, width, height, screenPoint);
    }

    public Camera3D getCamera() {
        return camera;
    }

    public void setCamera(Camera3D camera) {
        if (camera == null) throw new NullPointerException("camera");
        this.camera = camera;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public float getOverlayWidth() {
        return overlayWidth;
    }

    public float getOverlayHeight() {
        return overlayHeight;
    }
}
