package valthorne;

import valthorne.event.Event;
import valthorne.event.EventListener;
import valthorne.event.EventPublisher;

import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;

import static org.lwjgl.glfw.GLFW.*;


/**
 * The {@code JGL} class forms the core of the Valthorne 2D game engine framework.
 * It operates as a high-level orchestrator responsible for managing the application's lifecycle,
 * initializing essential subsystems, and executing the game loop. The class is designed
 * to be versatile, allowing developers to focus on implementing game logic while JGL
 * handles resource management and low-level operations.
 * <p>
 * Key capabilities of the {@code JGL} class include:
 * <ul>
 *   <li>Framework Initialization: Initializes GLFW as a windowing system and configures
 *       systems like audio, input devices (keyboard, mouse), and window display.</li>
 *   <li>Main Application Loop: Ensures consistent updates to the game state and renders
 *       every frame, calculating delta time and frame rates for precise updates.</li>
 *   <li>Event-Driven Design: Provides a centralized event bus to facilitate communication
 *       between various components by subscribing, unsubscribing, and broadcasting events.</li>
 *   <li>Resource Cleanup: At the end of the application's lifecycle, ensures proper
 *       disposal of resources like the input devices, window, and audio systems.</li>
 * </ul>
 * The {@code JGL} class requires that an {@link Application} implementation is passed to
 * provide game-specific lifecycle methods, offering entry points for initialization,
 * updates, rendering, and cleanup.
 *
 * @author Albert Beaupre
 * @since October 17th, 2025
 */
public class JGL {

    private static final EventPublisher events = new EventPublisher();
    private static final BlockingDeque<Runnable> tasks = new LinkedBlockingDeque<>();
    private static float deltaTime;
    private static short framesPerSecond;

    /**
     * Initializes the application and configures the window settings such as title and dimensions.
     * This method delegates to another {@code init} method that accepts a {@code JGLConfiguration}
     * object with default settings.
     *
     * @param application the {@code Application} instance containing the logic for the application.
     *                    It must implement the lifecycle methods defined in the {@code Application} interface.
     * @param title       the title of the application window.
     * @param width       the width of the application window in pixels.
     * @param height      the height of the application window in pixels.
     * @throws NullPointerException if {@code application} is {@code null}.
     */
    public static void init(Application application, String title, int width, int height) {
        init(application, JGLConfiguration.defaults().title(title).size(width, height));
    }

    /**
     * Initializes the application and its associated systems. This method sets up the
     * necessary components for the application to run, including input devices, audio,
     * and the rendering window. It also initializes the provided {@code Application}
     * instance, starts the application's main loop, handles frame updates, and ensures
     * proper cleanup upon exit.
     *
     * @param application the {@code Application} instance containing the logic for the application.
     *                    It must implement the lifecycle methods defined in the {@code Application} interface.
     * @param config      the {@code JGLConfiguration} object containing the settings for the application,
     *                    such as window size, title, and rendering options.
     * @throws NullPointerException  if {@code application} or {@code config} is {@code null}.
     * @throws IllegalStateException if the GLFW library could not be initialized.
     */
    public static void init(Application application, JGLConfiguration config) {
        if (application == null) throw new NullPointerException("Application cannot be null");
        if (config == null) throw new NullPointerException("JGLConfiguration cannot be null");

        if (!glfwInit())
            throw new IllegalStateException("Unable to initialize GLFW");

        Window.init(config);
        Audio.init();
        Mouse.init();
        Keyboard.init();

        float lastTime = (float) glfwGetTime();
        double fpsTime = 0;
        short frames = 0;

        application.init();
        while (!Window.shouldClose()) {
            while (!tasks.isEmpty()) tasks.poll().run();

            float now = (float) glfwGetTime();
            deltaTime = now - lastTime;
            lastTime = now;

            glfwPollEvents();

            application.update(deltaTime);
            application.render();

            fpsTime += deltaTime;
            frames++;

            if (fpsTime >= 1) {
                framesPerSecond = frames;
                fpsTime = 0;
                frames = 0;
            }

            glfwSwapBuffers(Window.getAddress());
            Mouse.resetScroll();
        }

        application.dispose();
        dispose();
    }

    /**
     * Schedules a task for execution. The provided {@code Runnable} task will
     * be added to the task list for processing. If the specified task is null,
     * a {@code NullPointerException} is thrown.
     *
     * @param task the {@code Runnable} task to be added to the task list
     * @throws NullPointerException if {@code task} is null
     */
    public static void runTask(Runnable task) {
        if (task == null)
            throw new NullPointerException("A null task cannot be executed.");
        tasks.add(task);
    }

    /**
     * Registers an {@code EventListener} for a specific type of {@code Event}.
     * The provided {@code listener} will handle events of the given {@code eventType}.
     * Throws a {@code NullPointerException} if either the {@code eventType} or {@code listener} is null.
     *
     * @param <T>       the type of {@code Event} the listener will handle
     * @param eventType the class object representing the type of event to be handled
     * @param listener  the {@code EventListener} responsible for handling the specified event type
     * @throws NullPointerException if {@code eventType} or {@code listener} is null
     */
    public static <T extends Event> void subscribe(Class<T> eventType, EventListener<T> listener) {
        if (eventType == null)
            throw new NullPointerException("A null event type cannot be registered for event listeners.");
        if (listener == null)
            throw new NullPointerException("A null EventListener cannot be registered for " + eventType.getSimpleName() + " events.");

        runTask(() -> events.register(eventType, listener));
    }

    /**
     * Unregisters an {@code EventListener} for a specific type of {@code Event}.
     * The provided {@code listener} will no longer handle events of the specified {@code eventType}.
     * Throws a {@code NullPointerException} if either the {@code eventType} or {@code listener} is null.
     *
     * @param <T>       the type of {@code Event} the listener was handling
     * @param eventType the class object representing the type of event to be unregistered
     * @param listener  the {@code EventListener} to be unregistered from handling the specified event type
     * @throws NullPointerException if {@code eventType} or {@code listener} is null
     */
    public static <T extends Event> void unsubscribe(Class<T> eventType, EventListener<T> listener) {
        if (eventType == null)
            throw new NullPointerException("A null event type cannot be unregistered for event listeners.");
        if (listener == null)
            throw new NullPointerException("A null EventListener cannot be unregistered for " + eventType.getSimpleName() + " events.");
        runTask(() -> events.unregister(eventType, listener));
    }

    /**
     * Publishes the specified {@code Event} to the event bus, allowing all
     * registered listeners for the event's type to handle it. If the event
     * is null, a {@code NullPointerException} is thrown.
     *
     * @param event the event to be published; must not be null
     * @throws NullPointerException if the provided event is null
     */
    public static void publish(Event event) {
        if (event == null) throw new NullPointerException("A null event cannot be published to the event bus.");
        runTask(() -> events.publish(event));
    }

    /**
     * Retrieves the current time in seconds since the GLFW timer was initialized.
     *
     * @return The current time in seconds as a float.
     */
    public static float getTime() {
        return (float) glfwGetTime();
    }

    /**
     * Retrieves the current frames per second (FPS) value, providing an
     * indication of the application's performance and rendering speed.
     *
     * @return The current frames per second as a double.
     */
    public static short getFramesPerSecond() {
        return framesPerSecond;
    }

    /**
     * Retrieves the time elapsed between the current frame and the previous frame.
     * This value is typically used to calculate frame-dependent operations, such as
     * animations or physics updates, ensuring consistent behavior regardless of frame rate.
     *
     * @return The time difference (delta time) in seconds as a double.
     */
    public static float getDeltaTime() {
        return deltaTime;
    }

    /**
     * Releases all resources associated with the application, including input devices
     * and the application window. This method ensures that resources such as the keyboard,
     * mouse, and window are properly disposed of, and terminates GLFW to clean up
     * native resources.
     * <p>
     * This method is called as part of the shutdown process to ensure proper cleanup
     * and prevent resource leaks. It invokes the dispose methods of the {@code Keyboard},
     * {@code Mouse}, and {@code Window} classes, followed by the termination of GLFW.
     * <p>
     * Usage:
     * - This method is intended for internal use and is invoked at the end of the
     * application's lifecycle.
     * - It ensures that GLFW and other application-level resources are completely
     * released.
     * <p>
     * Steps performed:
     * 1. Disposes of resources associated with the {@code Keyboard}.
     * 2. Disposes of resources associated with the {@code Mouse}.
     * 3. Disposes of resources associated with the {@code Window}.
     * 4. Terminates GLFW to release any remaining native resources.
     */
    private static void dispose() {
        Keyboard.dispose();
        Mouse.dispose();
        Window.dispose();
        Audio.dispose();

        glfwTerminate();
    }
}