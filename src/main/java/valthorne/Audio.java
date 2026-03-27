package valthorne;

import org.lwjgl.openal.AL;
import org.lwjgl.openal.ALC;
import org.lwjgl.openal.ALCCapabilities;
import valthorne.audio.sound.SoundData;
import valthorne.audio.sound.SoundPlayer;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.LockSupport;

import static org.lwjgl.openal.ALC10.*;
import static org.lwjgl.system.MemoryUtil.NULL;

/**
 * <p>
 * {@code Audio} is the central audio runtime manager for Valthorne. It owns the
 * OpenAL device and context, runs a dedicated audio thread, executes audio-bound
 * tasks on that thread, tracks active {@link SoundPlayer} instances, and ensures
 * that OpenAL operations happen from the correct execution context.
 * </p>
 *
 * <p>
 * This class is intentionally static and non-instantiable. It behaves like a global
 * subsystem that is started once, kept alive while the engine is running, and then
 * disposed when audio is no longer needed. Internally, it creates a daemon thread
 * dedicated to audio work. That thread:
 * </p>
 *
 * <ul>
 *     <li>opens the OpenAL device</li>
 *     <li>creates and activates the OpenAL context</li>
 *     <li>drains queued audio tasks</li>
 *     <li>updates all registered {@link SoundPlayer} instances</li>
 *     <li>disposes registered players during shutdown</li>
 * </ul>
 *
 * <p>
 * The public API is designed around safe thread delegation. Code outside the audio
 * thread can submit work through {@link #run(Runnable)}, synchronously execute work
 * through {@link #sync(Runnable)}, or return values from audio-thread operations
 * through {@link #call(Callable)}. This allows the rest of the engine to create,
 * destroy, and manipulate audio resources without directly owning the OpenAL thread.
 * </p>
 *
 * <p>
 * Sound playback objects are represented by {@link SoundPlayer}. New players are
 * typically created through {@link #load(String)}, {@link #load(byte[])}, or
 * {@link #create(SoundData)}. Once created, they are automatically registered so
 * the audio thread can call {@link SoundPlayer#update()} each cycle.
 * </p>
 *
 * <p>
 * This design avoids forcing engine users to manually update every individual sound
 * player each frame. Instead, the audio subsystem owns that update lifecycle
 * internally and keeps OpenAL interaction isolated to a single thread.
 * </p>
 *
 * <h2>Example Usage</h2>
 *
 * <pre>{@code
 * Audio.init();
 *
 * SoundPlayer player = Audio.load("audio/music/theme.ogg");
 * player.play();
 *
 * Audio.run(() -> {
 *     player.setLooping(true);
 *     player.setGain(0.6f);
 * });
 *
 * boolean onAudioThread = Audio.isAudioThread();
 *
 * Audio.destroy(player);
 * Audio.dispose();
 * }</pre>
 *
 * <p>
 * This example demonstrates the intended full usage of the class: startup,
 * resource creation, queued audio-thread work, state inspection, cleanup of
 * players, and subsystem shutdown.
 * </p>
 *
 * @author Albert Beaupre
 * @since December 5th, 2025
 */
public final class Audio {

    /**
     * Shared lock used to guard initialization and shutdown of the audio thread.
     */
    private static final Object LOCK = new Object();

    /**
     * Queue of tasks that must execute on the dedicated audio thread.
     */
    private static final ConcurrentLinkedQueue<Runnable> TASKS = new ConcurrentLinkedQueue<>();

    /**
     * Registry of all active sound players that should be updated by the audio thread.
     */
    private static final List<SoundPlayer> PLAYERS = new CopyOnWriteArrayList<>();

    /**
     * Flag indicating whether the audio run loop should continue processing.
     */
    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);

    private static volatile Thread audioThread; // Dedicated thread that owns OpenAL and processes audio work
    private static volatile long device; // Native OpenAL device handle
    private static volatile long context; // Native OpenAL context handle

    /**
     * <p>
     * Private constructor to prevent instantiation.
     * </p>
     *
     * <p>
     * {@code Audio} is a static subsystem and should never be constructed directly.
     * </p>
     */
    private Audio() {
    }

    /**
     * <p>
     * Initializes the audio subsystem and starts the dedicated audio thread if it is
     * not already running.
     * </p>
     *
     * <p>
     * This method is package-private because it is intended to be controlled by the
     * engine bootstrap process rather than arbitrary external code. Initialization is
     * synchronized so only one thread can start the subsystem. The method creates a
     * daemon thread, waits for it to finish its initial OpenAL setup, and then either
     * returns successfully or throws an exception if startup failed.
     * </p>
     *
     * <p>
     * If initialization has already happened, the method returns immediately without
     * recreating the audio thread or OpenAL context.
     * </p>
     *
     * @throws IllegalStateException if OpenAL device or context creation fails
     */
    static void init() {
        synchronized (LOCK) {
            if (audioThread != null) {
                return;
            }

            CountDownLatch started = new CountDownLatch(1);
            AtomicReference<Throwable> failure = new AtomicReference<>();

            RUNNING.set(true);

            audioThread = new Thread(() -> runLoop(started, failure), "Valthorne-Audio");
            audioThread.setDaemon(true);
            audioThread.start();

            await(started);

            Throwable throwable = failure.get();
            if (throwable != null) {
                audioThread = null;
                RUNNING.set(false);
                throw new IllegalStateException("Failed to initialize audio", throwable);
            }
        }
    }

    /**
     * <p>
     * Shuts down the audio subsystem and waits for the dedicated audio thread to exit.
     * </p>
     *
     * <p>
     * This method is package-private because it is part of subsystem lifecycle
     * management. Shutdown is synchronized so that thread state transitions are safe.
     * The method stops the run loop, unparks the audio thread if it is sleeping, and
     * joins it when shutdown is requested from a different thread. After the thread
     * exits, the shared thread reference is cleared.
     * </p>
     *
     * <p>
     * If the subsystem is not running, this method returns without doing anything.
     * </p>
     */
    static void dispose() {
        Thread thread;

        synchronized (LOCK) {
            thread = audioThread;
            if (thread == null) {
                return;
            }

            RUNNING.set(false);
            LockSupport.unpark(thread);
        }

        if (Thread.currentThread() != thread) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        synchronized (LOCK) {
            audioThread = null;
        }
    }

    /**
     * <p>
     * Loads sound data from a file path and creates a managed {@link SoundPlayer}
     * for it.
     * </p>
     *
     * <p>
     * This is a convenience method that first loads {@link SoundData} from the given
     * path and then delegates to {@link #create(SoundData)} so the resulting player
     * is created on the audio thread and registered for automatic updates.
     * </p>
     *
     * @param path the path to the sound resource
     * @return a newly created and registered sound player
     */
    public static SoundPlayer load(String path) {
        return create(SoundData.load(path));
    }

    /**
     * <p>
     * Loads sound data from raw encoded bytes and creates a managed
     * {@link SoundPlayer} for it.
     * </p>
     *
     * <p>
     * This is a convenience method that first decodes {@link SoundData} from the
     * provided byte array and then delegates to {@link #create(SoundData)} so the
     * resulting player is created on the audio thread and registered for updates.
     * </p>
     *
     * @param data encoded sound data bytes
     * @return a newly created and registered sound player
     */
    public static SoundPlayer load(byte[] data) {
        return create(SoundData.load(data));
    }

    /**
     * <p>
     * Creates a new {@link SoundPlayer} from already prepared {@link SoundData}.
     * </p>
     *
     * <p>
     * The actual player construction happens on the audio thread through
     * {@link #call(Callable)} so OpenAL resource creation remains thread-safe.
     * After creation, the player is added to the tracked player list so the audio
     * subsystem updates it automatically during each loop iteration.
     * </p>
     *
     * @param data the sound data used to create the player
     * @return the newly created and registered sound player
     */
    public static SoundPlayer create(SoundData data) {
        return call(() -> {
            SoundPlayer player = new SoundPlayer(data);
            PLAYERS.add(player);
            return player;
        });
    }

    /**
     * <p>
     * Destroys a managed {@link SoundPlayer} and removes it from the update registry.
     * </p>
     *
     * <p>
     * If the provided player is {@code null}, this method returns immediately. When
     * a valid player is provided, destruction is queued onto the audio thread so
     * OpenAL cleanup happens safely and consistently.
     * </p>
     *
     * @param player the player to destroy
     */
    public static void destroy(SoundPlayer player) {
        if (player == null) {
            return;
        }

        run(() -> {
            PLAYERS.remove(player);
            player.dispose();
        });
    }

    /**
     * <p>
     * Queues an asynchronous task to run on the audio thread.
     * </p>
     *
     * <p>
     * If the subsystem has not been started yet, it is started automatically through
     * {@link #ensureStarted()}. If the current thread is already the audio thread,
     * the runnable executes immediately. Otherwise the runnable is added to the task
     * queue and the audio thread is unparked so it can process the work promptly.
     * </p>
     *
     * @param runnable the task to execute on the audio thread
     */
    public static void run(Runnable runnable) {
        if (runnable == null) {
            return;
        }

        ensureStarted();

        if (isAudioThread()) {
            runnable.run();
            return;
        }

        TASKS.add(runnable);
        LockSupport.unpark(audioThread);
    }

    /**
     * <p>
     * Executes a runnable synchronously on the audio thread and blocks until it
     * completes.
     * </p>
     *
     * <p>
     * This method is a convenience wrapper around {@link #call(Callable)} for
     * callers that do not need a return value. The runnable is executed on the
     * audio thread whether the caller is already on that thread or not.
     * </p>
     *
     * @param runnable the task to execute synchronously on the audio thread
     */
    public static void sync(Runnable runnable) {
        call(() -> {
            runnable.run();
            return null;
        });
    }

    /**
     * <p>
     * Executes a callable on the audio thread and returns its result.
     * </p>
     *
     * <p>
     * If the current thread is already the audio thread, the callable is invoked
     * immediately. Otherwise a {@link FutureTask} is enqueued and the calling thread
     * blocks until the result is available.
     * </p>
     *
     * <p>
     * Checked exceptions thrown by the callable are wrapped in
     * {@link RuntimeException}. Runtime exceptions are rethrown directly.
     * </p>
     *
     * @param callable the task to execute
     * @param <T>      the result type
     * @return the result produced by the callable
     * @throws RuntimeException if execution fails or waiting for the result fails
     */
    public static <T> T call(Callable<T> callable) {
        ensureStarted();

        if (isAudioThread()) {
            try {
                return callable.call();
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        FutureTask<T> future = new FutureTask<>(() -> {
            try {
                return callable.call();
            } catch (RuntimeException e) {
                throw e;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        TASKS.add(future);
        LockSupport.unpark(audioThread);

        try {
            return future.get();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * <p>
     * Returns whether the current thread is the dedicated audio thread.
     * </p>
     *
     * <p>
     * This check is useful when enforcing thread ownership rules for OpenAL-backed
     * operations or when deciding whether a task should execute immediately or be
     * queued for later processing.
     * </p>
     *
     * @return {@code true} if the current thread is the audio thread
     */
    public static boolean isAudioThread() {
        return Thread.currentThread() == audioThread;
    }

    /**
     * <p>
     * Registers a {@link SoundPlayer} with the audio subsystem so it will be updated
     * each loop iteration.
     * </p>
     *
     * <p>
     * If the player is {@code null}, the method returns immediately. If the current
     * thread is already the audio thread, registration happens immediately. Otherwise
     * registration is queued through {@link #run(Runnable)}. Duplicate registration
     * is avoided by checking whether the player already exists in the tracked list.
     * </p>
     *
     * @param player the player to register
     */
    public static void register(SoundPlayer player) {
        if (player == null) {
            return;
        }

        if (isAudioThread()) {
            if (!PLAYERS.contains(player)) {
                PLAYERS.add(player);
            }
            return;
        }

        run(() -> {
            if (!PLAYERS.contains(player)) {
                PLAYERS.add(player);
            }
        });
    }

    /**
     * <p>
     * Unregisters a {@link SoundPlayer} from the audio subsystem.
     * </p>
     *
     * <p>
     * This removes the player from the automatic update list but does not, by itself,
     * guarantee full resource destruction unless caller logic also disposes it.
     * </p>
     *
     * <p>
     * If the current thread is already the audio thread, removal happens immediately.
     * Otherwise it is queued for execution on the audio thread.
     * </p>
     *
     * @param player the player to unregister
     */
    public static void unregister(SoundPlayer player) {
        if (player == null) {
            return;
        }

        if (isAudioThread()) {
            PLAYERS.remove(player);
            return;
        }

        run(() -> PLAYERS.remove(player));
    }

    /**
     * <p>
     * Verifies that the current thread is the audio thread.
     * </p>
     *
     * <p>
     * This helper is intended for internal or closely related audio classes that must
     * enforce OpenAL thread ownership. If called from any thread other than the audio
     * thread, an {@link IllegalStateException} is thrown.
     * </p>
     *
     * @throws IllegalStateException if called from a non-audio thread
     */
    static void assertAudioThread() {
        if (!isAudioThread()) {
            throw new IllegalStateException("Audio operation must run on the audio thread");
        }
    }

    /**
     * <p>
     * Ensures that the audio subsystem has been initialized.
     * </p>
     *
     * <p>
     * If the audio thread has not yet been created, this method triggers startup by
     * calling {@link #init()}. If audio is already running, nothing happens.
     * </p>
     */
    private static void ensureStarted() {
        if (audioThread == null) {
            init();
        }
    }

    /**
     * <p>
     * Runs the dedicated audio thread loop.
     * </p>
     *
     * <p>
     * This method performs the full native audio lifecycle for the subsystem:
     * </p>
     *
     * <ul>
     *     <li>open the OpenAL device</li>
     *     <li>create the OpenAL context</li>
     *     <li>make that context current on the thread</li>
     *     <li>create LWJGL OpenAL capabilities</li>
     *     <li>drain queued tasks and update players while running</li>
     *     <li>dispose registered players when shutting down</li>
     *     <li>always release OpenAL resources in the {@code finally} block</li>
     * </ul>
     *
     * <p>
     * If any failure occurs during startup, it is recorded into {@code failure} and
     * the startup latch is released so the initializing thread can react properly.
     * </p>
     *
     * @param started latch used to signal initialization completion
     * @param failure reference used to publish startup failure information
     */
    private static void runLoop(CountDownLatch started, AtomicReference<Throwable> failure) {
        try {
            device = alcOpenDevice((ByteBuffer) null);
            if (device == NULL) {
                throw new IllegalStateException("Failed to open OpenAL device");
            }

            context = alcCreateContext(device, (IntBuffer) null);
            if (context == NULL) {
                throw new IllegalStateException("Failed to create OpenAL context");
            }

            alcMakeContextCurrent(context);

            ALCCapabilities capabilities = ALC.createCapabilities(device);
            AL.createCapabilities(capabilities);

            started.countDown();

            while (RUNNING.get()) {
                drainTasks();
                updatePlayers();
                drainTasks();
                LockSupport.parkNanos(5_000_000L);
            }

            drainTasks();
            disposePlayers();
        } catch (Throwable throwable) {
            failure.set(throwable);
            started.countDown();
        } finally {
            shutdownOpenAL();
        }
    }

    /**
     * <p>
     * Updates all currently registered {@link SoundPlayer} instances.
     * </p>
     *
     * <p>
     * The method iterates over the player list, skips null references, and calls
     * {@link SoundPlayer#update()} on each valid player. Failures from individual
     * players are caught and printed so one broken player does not prevent the rest
     * of the audio subsystem from continuing.
     * </p>
     */
    private static void updatePlayers() {
        for (SoundPlayer player : PLAYERS) {
            if (player == null) {
                continue;
            }

            try {
                player.update();
            } catch (Throwable throwable) {
                throwable.printStackTrace();
            }
        }
    }

    /**
     * <p>
     * Disposes all currently registered {@link SoundPlayer} instances.
     * </p>
     *
     * <p>
     * This method is used during shutdown so every remaining player has a chance to
     * release its resources. Exceptions from individual players are caught and printed,
     * then the registry is cleared after iteration completes.
     * </p>
     */
    private static void disposePlayers() {
        for (SoundPlayer player : PLAYERS) {
            if (player == null) {
                continue;
            }

            try {
                player.dispose();
            } catch (Throwable throwable) {
                throwable.printStackTrace();
            }
        }

        PLAYERS.clear();
    }

    /**
     * <p>
     * Drains and executes all queued audio-thread tasks.
     * </p>
     *
     * <p>
     * Tasks are polled from {@link #TASKS} until the queue becomes empty. This method
     * is called multiple times during each loop iteration so queued work can run both
     * before and after player updates.
     * </p>
     */
    private static void drainTasks() {
        Runnable task;

        while ((task = TASKS.poll()) != null) {
            task.run();
        }
    }

    /**
     * <p>
     * Shuts down OpenAL resources owned by the audio subsystem.
     * </p>
     *
     * <p>
     * The current context is first cleared from the thread. The OpenAL context is
     * then destroyed if valid, followed by closing the device if valid. Finally,
     * the queued task list is cleared even if shutdown work throws unexpectedly.
     * </p>
     */
    private static void shutdownOpenAL() {
        try {
            alcMakeContextCurrent(NULL);

            if (context != NULL) {
                alcDestroyContext(context);
                context = NULL;
            }

            if (device != NULL) {
                alcCloseDevice(device);
                device = NULL;
            }
        } finally {
            TASKS.clear();
        }
    }

    /**
     * <p>
     * Waits for the given latch while preserving interruption state.
     * </p>
     *
     * <p>
     * This helper repeatedly waits until the latch completes. If interruption occurs,
     * the method remembers that fact, continues waiting, and restores the interrupted
     * status on the current thread once waiting has completed.
     * </p>
     *
     * @param latch the latch to wait on
     */
    private static void await(CountDownLatch latch) {
        boolean interrupted = false;

        while (true) {
            try {
                latch.await();
                break;
            } catch (InterruptedException e) {
                interrupted = true;
            }
        }

        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}