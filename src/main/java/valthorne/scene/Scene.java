package valthorne.scene;

import valthorne.Keyboard;
import valthorne.Mouse;
import valthorne.Window;
import valthorne.camera.Camera;
import valthorne.event.events.*;
import valthorne.graphics.texture.TextureBatch;
import valthorne.ui.UIRoot;
import valthorne.viewport.ScreenViewport;
import valthorne.viewport.Viewport;

/**
 * <h1>Scene</h1>
 *
 * <p>
 * {@code Scene} is the abstract runtime foundation for a single active state in the engine, such as
 * a title screen, gameplay screen, pause menu, editor, dialogue scene, or loading scene. A scene
 * groups together the systems and callbacks needed to manage one self-contained part of the
 * application's lifecycle.
 * </p>
 *
 * <p>
 * Every scene owns a rendering batch, a UI root, and a viewport. It may also own a camera depending
 * on how the scene wants to render world content. The class handles the shared setup for these
 * objects and wires the scene into the global window, keyboard, mouse, and scroll event systems.
 * Concrete subclasses then focus only on scene-specific initialization, rendering, updating, and
 * disposal.
 * </p>
 *
 * <h2>Lifecycle model</h2>
 * <p>
 * The intended lifecycle is:
 * </p>
 * <ol>
 *     <li>{@link #init()} performs subclass-specific setup.</li>
 *     <li>{@link #draw(TextureBatch)} is used by the scene controller to render the world and UI.</li>
 *     <li>{@link #update(float)} is used by the scene controller to update the scene when not paused.</li>
 *     <li>{@link #dispose()} releases subclass-owned resources.</li>
 * </ol>
 *
 * <h2>Important design rule</h2>
 * <p>
 * Subclasses should place their custom cleanup in {@link #dispose()}, then call
 * {@link #disposeScene()} at the end of that method. The infrastructure disposal method
 * intentionally does <b>not</b> call {@link #dispose()} again. This avoids recursive disposal bugs.
 * </p>
 *
 * <h2>Rendering behavior</h2>
 * <p>
 * {@link #drawScene()} wraps the scene render flow in a single {@link TextureBatch} begin/end cycle.
 * It first calls {@link #draw(TextureBatch)} so subclasses can draw scene content, then it draws the
 * scene UI. This keeps the common render path in one place and avoids duplicating batch begin/end code
 * in every controller.
 * </p>
 *
 * <h2>Pause behavior</h2>
 * <p>
 * {@link #updateScene(float)} respects the scene's paused flag. When paused, update logic is skipped,
 * but rendering can still continue. This is useful for pause menus, overlays, and frozen gameplay
 * states where the scene should remain visible but not simulate.
 * </p>
 *
 * <h2>Example</h2>
 * <pre>{@code
 * public final class GameplayScene extends Scene {
 *
 *     @Override
 *     public void init() {
 *
 *     }
 *
 *     @Override
 *     public void render(TextureBatch batch) {
 *
 *     }
 *
 *     @Override
 *     public void update(float delta) {
 *
 *     }
 *
 *     @Override
 *     public void dispose() {
 *
 *     }
 * }
 * }</pre>
 *
 * @author Albert Beaupre
 * @since March 7th, 2026
 */
public abstract class Scene {

    protected UIRoot ui; // The root UI object owned by this scene.
    protected Camera camera; // The optional camera used to render world content for this scene.
    protected Viewport viewport; // The viewport controlling projection, coordinate mapping, and UI/world bounds.
    protected TextureBatch batch; // The shared texture batch used to render this scene.
    private boolean paused; // Whether this scene is currently paused.

    private SceneWindowResizeListener windowResizeListener; // The registered resize listener that forwards events into this scene.
    private SceneKeyListener keyListener; // The registered keyboard listener that forwards events into this scene.
    private SceneMouseListener mouseListener; // The registered mouse listener that forwards press, release, drag, and move events into this scene.
    private SceneMouseScrollListener mouseScrollListener; // The registered mouse scroll listener that forwards wheel events into this scene.
    private boolean initialized; // Whether the shared scene infrastructure has already been initialized.
    private boolean infrastructureDisposed; // Whether the shared scene infrastructure has already been disposed.

    /**
     * Initializes the common runtime fields used by all scenes.
     *
     * <p>
     * This method prepares the scene's shared runtime infrastructure. It creates the rendering batch,
     * creates the UI root, creates a default screen viewport sized to the current window, assigns that
     * viewport to the UI, and registers all listener wrappers required for event forwarding.
     * </p>
     *
     * <p>
     * This method is safe against repeated calls. Once initialization has already happened, later calls
     * do nothing. That prevents duplicate listener registration and accidental recreation of scene
     * systems.
     * </p>
     */
    protected void initializeFields() {
        if (initialized)
            return;

        this.batch = new TextureBatch(4096, 16);
        this.ui = new UIRoot();
        this.viewport = new ScreenViewport(Window.getWidth(), Window.getHeight());
        this.viewport.update(Window.getWidth(), Window.getHeight());
        this.ui.setViewport(new ScreenViewport(Window.getWidth(), Window.getHeight()));

        this.windowResizeListener = new SceneWindowResizeListener(this);
        this.keyListener = new SceneKeyListener(this);
        this.mouseListener = new SceneMouseListener(this);
        this.mouseScrollListener = new SceneMouseScrollListener(this);

        Window.addWindowResizeListener(windowResizeListener);
        Keyboard.addKeyListener(keyListener);
        Mouse.addMouseListener(mouseListener);
        Mouse.addScrollListener(mouseScrollListener);

        this.initialized = true;
        this.infrastructureDisposed = false;
        init();
    }

    /**
     * Draws the complete scene using the shared batch.
     *
     * <p>
     * This method provides the standard render flow for a scene. It begins the scene batch, calls the
     * subclass render method so world and scene-specific content can be drawn, then draws the scene UI,
     * and finally ends the batch.
     * </p>
     *
     * <p>
     * Controllers such as {@code GameScreen} can call this method directly instead of duplicating the
     * batch begin/end flow.
     * </p>
     */
    protected void drawScene() {
        if (batch == null)
            return;

        viewport.bind();
        batch.begin();
        draw(batch);
        batch.end();
        viewport.unbind();

        if (ui != null)
            ui.draw();
    }

    /**
     * Updates the scene while respecting the paused state.
     *
     * <p>
     * If the scene is paused, this method returns immediately and does not call the subclass update
     * method. If the scene is not paused, the provided delta time is forwarded to
     * {@link #update(float)}.
     * </p>
     *
     * @param delta the elapsed time in seconds since the last update
     */
    protected void updateScene(float delta) {
        if (paused) {
            return;
        }

        update(delta);

        if (ui != null)
            ui.update(delta);
    }

    /**
     * Initializes scene-specific resources and state.
     *
     * <p>
     * Subclasses implement this method to load assets, configure cameras, build UI widgets, spawn
     * world entities, or perform any other startup logic required once the shared scene infrastructure
     * already exists.
     * </p>
     */
    public abstract void init();

    /**
     * Renders scene-specific content using the provided texture batch.
     *
     * <p>
     * This method should contain the actual draw logic for the scene's world or custom content. The
     * shared render flow already manages batch begin/end externally, so implementations should usually
     * only queue draw operations here.
     * </p>
     *
     * @param batch the texture batch supplied for rendering this scene
     */
    public abstract void draw(TextureBatch batch);

    /**
     * Updates scene-specific logic.
     *
     * <p>
     * Subclasses implement this method to perform simulation, input-driven logic, animation state
     * changes, timers, entity updates, and any other time-based progression for the scene.
     * </p>
     *
     * @param delta the elapsed time in seconds since the previous update
     */
    public abstract void update(float delta);

    /**
     * Disposes scene-specific resources.
     *
     * <p>
     * Subclasses implement this method to release anything they own directly, such as textures, maps,
     * sounds, scene objects, or custom data structures. Implementations should normally call
     * {@link #disposeScene()} at the end so the shared batch, UI, viewport references,
     * and registered listeners are also cleaned up.
     * </p>
     */
    public abstract void dispose();

    /**
     * Disposes the shared infrastructure created by {@link #initializeFields()}.
     *
     * <p>
     * This method removes all registered listeners created for the scene and disposes shared runtime
     * objects such as the UI and texture batch. It also clears references to the viewport and camera.
     * </p>
     *
     * <p>
     * This method does not call {@link #dispose()}. That is intentional. Calling back into the scene's
     * abstract disposal method from here would create a recursion problem when subclasses call this
     * helper from their own {@link #dispose()} implementation.
     * </p>
     *
     * <p>
     * Repeated calls are safe. Once the infrastructure has already been disposed, later calls do
     * nothing.
     * </p>
     */
    protected void disposeScene() {
        if (infrastructureDisposed) {
            return;
        }

        if (windowResizeListener != null) {
            Window.removeWindowResizeListener(windowResizeListener);
            windowResizeListener = null;
        }

        if (keyListener != null) {
            Keyboard.removeKeyListener(keyListener);
            keyListener = null;
        }

        if (mouseListener != null) {
            Mouse.removeMouseListener(mouseListener);
            mouseListener = null;
        }

        if (mouseScrollListener != null) {
            Mouse.removeScrollListener(mouseScrollListener);
            mouseScrollListener = null;
        }

        if (ui != null) {
            ui.dispose();
            ui = null;
        }

        if (batch != null) {
            batch.dispose();
            batch = null;
        }

        viewport = null;
        camera = null;
        initialized = false;
        infrastructureDisposed = true;
        dispose();
    }

    /**
     * Handles a window resize event.
     *
     * <p>
     * The default implementation updates the scene viewport to the new window size. If both a camera
     * and viewport are present, the camera is then recentered to the midpoint of the viewport's world
     * bounds so the scene remains visually centered after the resize.
     * </p>
     *
     * @param event the window resize event containing the new dimensions
     */
    public void windowResized(WindowResizeEvent event) {
        if (viewport != null) {
            viewport.update(event.getNewWidth(), event.getNewHeight());
        }

        if (camera != null && viewport != null) {
            camera.setCenter(event.getNewWidth() * 0.5f, event.getNewHeight() * 0.5f);
        }
    }

    /**
     * Handles a key press event.
     *
     * <p>
     * The base implementation does nothing. Subclasses may override this method to respond to key
     * presses.
     * </p>
     *
     * @param event the key press event
     */
    public void keyPressed(KeyPressEvent event) {
    }

    /**
     * Handles a key release event.
     *
     * <p>
     * The base implementation does nothing. Subclasses may override this method to respond to key
     * releases.
     * </p>
     *
     * @param event the key release event
     */
    public void keyReleased(KeyReleaseEvent event) {
    }

    /**
     * Handles a mouse press event.
     *
     * <p>
     * The base implementation does nothing. Subclasses may override this method to respond to mouse
     * button presses.
     * </p>
     *
     * @param event the mouse press event
     */
    public void mousePressed(MousePressEvent event) {
    }

    /**
     * Handles a mouse release event.
     *
     * <p>
     * The base implementation does nothing. Subclasses may override this method to respond to mouse
     * button releases.
     * </p>
     *
     * @param event the mouse release event
     */
    public void mouseReleased(MouseReleaseEvent event) {
    }

    /**
     * Handles a mouse drag event.
     *
     * <p>
     * The base implementation does nothing. Subclasses may override this method to respond to dragging
     * while a mouse button is held.
     * </p>
     *
     * @param event the mouse drag event
     */
    public void mouseDragged(MouseDragEvent event) {
    }

    /**
     * Handles a mouse move event.
     *
     * <p>
     * The base implementation does nothing. Subclasses may override this method to respond to hover
     * behavior or cursor movement tracking.
     * </p>
     *
     * @param event the mouse move event
     */
    public void mouseMoved(MouseMoveEvent event) {
    }

    /**
     * Handles a mouse scroll event.
     *
     * <p>
     * The base implementation does nothing. Subclasses may override this method to respond to mouse
     * wheel input.
     * </p>
     *
     * @param event the mouse scroll event
     */
    public void mouseScrolled(MouseScrollEvent event) {
    }

    /**
     * Returns whether the scene is currently paused.
     *
     * @return true if the scene is paused, otherwise false
     */
    public boolean isPaused() {
        return paused;
    }

    /**
     * Sets whether the scene is paused.
     *
     * <p>
     * A paused scene may still render, but higher-level code can use this state to skip update logic
     * until the scene is resumed.
     * </p>
     *
     * @param paused true to pause the scene, false to resume it
     */
    public void setPaused(boolean paused) {
        this.paused = paused;
    }

    /**
     * Returns the UI owned by this scene.
     *
     * @return the scene UI, or null if the scene infrastructure has not been initialized
     */
    public UIRoot getUI() {
        return ui;
    }

    /**
     * Returns the camera used by this scene.
     *
     * @return the scene camera, or null if no camera is assigned
     */
    public Camera getCamera() {
        return camera;
    }

    /**
     * Assigns the scene camera.
     *
     * @param camera the camera to assign, or null to remove the current camera
     */
    public void setCamera(Camera camera) {
        this.camera = camera;
    }

    /**
     * Returns the viewport used by this scene.
     *
     * @return the scene viewport, or null if the scene infrastructure has not been initialized
     */
    public Viewport getViewport() {
        return viewport;
    }

    /**
     * Assigns the viewport used by this scene.
     *
     * <p>
     * If the scene UI already exists, the same viewport is immediately assigned to the UI so UI
     * coordinate conversion remains synchronized with the scene.
     * </p>
     *
     * @param viewport the viewport to use for this scene
     */
    public void setViewport(Viewport viewport) {
        this.viewport = viewport;
    }

    /**
     * Returns the texture batch owned by this scene.
     *
     * @return the scene batch, or null if the scene infrastructure has not been initialized
     */
    public TextureBatch getBatch() {
        return batch;
    }

    /**
     * Returns whether the shared scene infrastructure has already been initialized.
     *
     * @return true if initialization has occurred, otherwise false
     */
    public boolean isInitialized() {
        return initialized;
    }
}