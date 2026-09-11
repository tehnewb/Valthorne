package valthorne;

import org.lwjgl.glfw.GLFW;
import valthorne.event.Event;
import valthorne.event.EventHandler;
import valthorne.event.EventPublisher;
import valthorne.event.EventType;

import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;

import static org.lwjgl.glfw.GLFW.glfwGetTime;
import static org.lwjgl.glfw.GLFW.glfwInit;
import static org.lwjgl.glfw.GLFW.glfwPollEvents;
import static org.lwjgl.glfw.GLFW.glfwSwapBuffers;


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

    /**
     * Shared engine event registry cleared during lifecycle reset.
     */
    private static final EventPublisher events = new EventPublisher();
    /**
     * Thread-safe pending tasks drained by the application loop.
     */
    private static final BlockingDeque<Runnable> tasks = new LinkedBlockingDeque<>();
    /**
     * Most recently recorded frame duration in seconds.
     */
    private static float deltaTime;
    /**
     * Cached frame-rate estimate stored as a short.
     */
    private static short framesPerSecond;
    /**
     * Application-loop continuation flag.
     */
    private static boolean running;

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

        if (running) {
            throw new IllegalStateException("JGL is already running");
        }

        running = true;

        boolean glfwInitialized = false;
        boolean applicationInitialized = false;
        Throwable failure = null;

        try {
            if (!glfwInit())
                throw new IllegalStateException("Unable to initialize GLFW");

            glfwInitialized = true;

            Window.init(config);
            Audio.init();
            Mouse.init();
            Keyboard.init();

            double fpsTime = 0;
            short frames = 0;

            application.init();
            applicationInitialized = true;
            double lastTime = glfwGetTime();

            while (!Window.shouldClose()) {
                drainTasks();

                double now = glfwGetTime();
                deltaTime = (float) (now - lastTime);
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
        } catch (Throwable throwable) {
            failure = throwable;
            throw throwable;
        } finally {
            Throwable cleanupFailure = null;

            if (applicationInitialized) {
                cleanupFailure = appendSuppressed(cleanupFailure, disposeApplication(application));
            }

            if (glfwInitialized) {
                cleanupFailure = appendSuppressed(cleanupFailure, dispose());
            } else {
                resetState();
            }

            if (cleanupFailure != null) {
                if (failure != null) {
                    failure.addSuppressed(cleanupFailure);
                } else {
                    rethrowUnchecked(cleanupFailure);
                }
            }
        }
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
     * Registers an {@code EventHandler} for one numeric event route.
     *
     * @param <T>       the event payload type routed by {@code eventType}
     * @param eventType the concrete event route to subscribe to
     * @param listener  the handler that should receive publications on that route
     * @throws NullPointerException if {@code eventType} or {@code listener} is null
     */
    public static <T extends Event> void subscribe(EventType<T> eventType, EventHandler<? super T> listener) {
        if (eventType == null)
            throw new NullPointerException("A null event type cannot be registered for event listeners.");
        if (listener == null)
            throw new NullPointerException("A null EventHandler cannot be registered for " + eventType.name() + " events.");

        events.register(eventType, listener);
    }

    /**
     * Registers an {@code EventHandler} with an explicit priority.
     *
     * @param <T>       the event payload type routed by {@code eventType}
     * @param eventType the concrete event route to subscribe to
     * @param priority  larger values run first
     * @param listener  the handler that should receive publications on that route
     * @throws NullPointerException if {@code eventType} or {@code listener} is null
     */
    public static <T extends Event> void subscribe(EventType<T> eventType, int priority, EventHandler<? super T> listener) {
        if (eventType == null)
            throw new NullPointerException("A null event type cannot be registered for event listeners.");
        if (listener == null)
            throw new NullPointerException("A null EventHandler cannot be registered for " + eventType.name() + " events.");

        events.register(eventType, priority, listener);
    }

    /**
     * Unregisters an {@code EventHandler} from one numeric event route.
     *
     * @param <T>       the event payload type routed by {@code eventType}
     * @param eventType the concrete event route to unsubscribe from
     * @param listener  the exact handler instance to remove
     * @throws NullPointerException if {@code eventType} or {@code listener} is null
     */
    public static <T extends Event> void unsubscribe(EventType<T> eventType, EventHandler<? super T> listener) {
        if (eventType == null)
            throw new NullPointerException("A null event type cannot be unregistered for event listeners.");
        if (listener == null)
            throw new NullPointerException("A null EventHandler cannot be unregistered for " + eventType.name() + " events.");
        events.unregister(eventType, listener);
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
        events.publish(event);
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
     * Invokes the application's own cleanup and captures any failure for combination
     * with subsequent engine cleanup.
     *
     * @param application application being shut down
     * @return failure, or null on success
     */
    private static Throwable disposeApplication(Application application) {
        try {
            application.dispose();
            return null;
        } catch (Throwable throwable) {
            return throwable;
        }
    }

    /**
     * Attempts keyboard, mouse, window, audio, and GLFW shutdown in order even when
     * an earlier action fails, then resets Java event/task/timing state.
     *
     * @return first cleanup failure with later failures suppressed, or null
     */
    private static Throwable dispose() {
        Throwable failure = null;

        failure = appendSuppressed(failure, runSafe(Keyboard::dispose));
        failure = appendSuppressed(failure, runSafe(Mouse::dispose));
        failure = appendSuppressed(failure, runSafe(Window::dispose));
        failure = appendSuppressed(failure, runSafe(Audio::dispose));
        failure = appendSuppressed(failure, runSafe(GLFW::glfwTerminate));

        resetState();
        return failure;
    }

    /**
     * Clears queued tasks and event subscriptions and resets frame timing/running
     * flags. Does not perform native resource cleanup.
     */
    static void resetState() {
        tasks.clear();
        events.clear();
        deltaTime = 0f;
        framesPerSecond = 0;
        running = false;
    }

    /**
     * Runs queued tasks in polling order until the queue is observed empty. Tasks
     * added during draining may run in the same pass. A task exception propagates
     * and leaves remaining queued work for the surrounding lifecycle to handle.
     */
    private static void drainTasks() {
        Runnable task;
        while ((task = tasks.poll()) != null) {
            task.run();
        }
    }

    /**
     * Runs one cleanup action and captures any thrown failure so later cleanup
     * actions can still be attempted.
     *
     * @param action cleanup operation
     * @return thrown failure, or null on success
     */
    private static Throwable runSafe(Runnable action) {
        try {
            action.run();
            return null;
        } catch (Throwable throwable) {
            return throwable;
        }
    }

    /**
     * Keeps the first cleanup failure and attaches a later nonnull failure as
     * suppressed. Callers must avoid passing the same throwable as both arguments.
     *
     * @param primary earlier failure, possibly null
     * @param next later failure, possibly null
     * @return first available failure
     */
    private static Throwable appendSuppressed(Throwable primary, Throwable next) {
        if (next == null) {
            return primary;
        }
        if (primary == null) {
            return next;
        }
        primary.addSuppressed(next);
        return primary;
    }

    /**
     * Rethrows runtime exceptions and errors unchanged, wrapping other throwable
     * types so cleanup can report them without a checked throws declaration.
     *
     * @param throwable nonnull failure to propagate
     */
    private static void rethrowUnchecked(Throwable throwable) {
        if (throwable instanceof RuntimeException runtimeException) {
            throw runtimeException;
        }
        if (throwable instanceof Error error) {
            throw error;
        }
        throw new RuntimeException(throwable);
    }
}
