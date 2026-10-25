package valthorne;

import org.lwjgl.openal.ALUtil;
import valthorne.audio.AudioState;
import valthorne.audio.SoundData;
import valthorne.audio.SoundPlayer;

import java.nio.IntBuffer;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;

import static org.lwjgl.openal.ALC10.*;
import static org.lwjgl.openal.ALC11.ALC_ALL_DEVICES_SPECIFIER;
import static org.lwjgl.openal.ALC11.ALC_DEFAULT_ALL_DEVICES_SPECIFIER;
import static org.lwjgl.openal.SOFTReopenDevice.alcReopenDeviceSOFT;
import static org.lwjgl.system.MemoryUtil.NULL;

/**
 * Owns Valthorne's asynchronous audio lifecycle and delegates native operations to
 * a dedicated OpenAL thread. Engine startup starts that thread and returns without
 * waiting for device discovery or context creation. Requests submitted during startup
 * are held by that session until it is ready, or completed exceptionally if startup
 * fails or shutdown intervenes. Native handles never migrate to another session.
 *
 * <p>{@link #startAsync()}, {@link #submitAsync(Callable)}, {@link #createAsync(SoundData)},
 * and {@link #loadAsync(String)} provide nonblocking startup and resource creation.
 * Existing {@link #call(Callable)}, {@link #sync(Runnable)}, {@link #create(SoundData)},
 * load methods, and device queries remain synchronous: they intentionally wait for
 * readiness and their result. Use asynchronous methods from application startup or
 * rendering callbacks to avoid delaying a frame.</p>
 *
 * <pre>{@code
 * Audio.loadAsync("audio/music/theme.ogg").thenAccept(player -> {
 *     player.setLooping(true);
 *     player.play();
 * }).exceptionally(failure -> {
 *     failure.printStackTrace();
 *     return null;
 * });
 * // Continue rendering immediately; callbacks normally run on the audio thread.
 * }</pre>
 *
 * <p>Completion callbacks must not block waiting for work that needs the audio thread.
 * Use an asynchronous completion executor for unrelated long-running work. Loading
 * decodes on the common worker pool and creates native resources on the captured
 * audio session; a late decode cannot silently restart a stopped session.</p>
 *
 * <p>Initialization failures are exposed through readiness, {@link #getFailure()},
 * and queued request outcomes, and are reported to standard error. They do not abort
 * the JGL rendering loop. An explicit {@link #startAsync()} can retry a failed session
 * after its native teardown completes. {@link #shutdownAsync()} closes admission
 * immediately; native cleanup stays on the owner, including when shutdown occurs
 * during a slow driver call. Engine disposal waits for that cleanup.</p>
 */
public final class Audio {
    /**
     * Serializes publication of new audio sessions; never held while waiting for a driver.
     */
    private static final Object LOCK = new Object();

    /**
     * Current session, retained after termination so failures remain observable.
     */
    private static volatile AudioRuntime runtime;

    /**
     * Latest immutable listener coordinates for ambient attenuation.
     */
    private static volatile ListenerPosition listenerPosition = new ListenerPosition(0, 0, 0);

    /**
     * Immutable logical listener coordinates in the same units as ambient sound areas.
     * Retained as a member type for compatibility with existing Audio.ListenerPosition users.
     *
     * @param x horizontal world coordinate
     * @param y vertical world coordinate
     * @param z depth world coordinate
     */
    public record ListenerPosition(float x, float y, float z) {
    }

    /**
     * Prevents construction of this static subsystem facade.
     */
    private Audio() {
    }

    /**
     * Starts audio without waiting for native initialization. Repeated calls share
     * the pending or ready session. A terminated failed session may be retried explicitly;
     * a session still stopping is returned with its existing readiness outcome.
     *
     * @return read-only readiness stage, exceptional on startup failure or early shutdown
     */
    public static CompletionStage<Void> startAsync() {
        /*
         * Thread publication is serialized, but OpenAL work belongs entirely to the
         * new worker. Returning a minimal stage prevents callers from completing readiness.
         */
        synchronized (LOCK) {
            AudioRuntime current = runtime;
            if (current == null || current.terminated().isDone()) {
                current = new AudioRuntime();
                runtime = current;
                current.start();
            }
            return current.ready().minimalCompletionStage();
        }
    }

    /**
     * Exposes readiness of the current session, starting it lazily if necessary.
     * Unlike startAsync(), this does not retry a failed session.
     *
     * @return read-only readiness stage for the captured current session
     */
    public static CompletionStage<Void> whenReady() {
        return ensureStarted().ready().minimalCompletionStage();
    }

    /**
     * Returns whether the current session has a usable native context.
     *
     * @return true only while the current session is READY
     */
    public static boolean isReady() {
        AudioRuntime current = runtime;
        return current != null && current.state() == AudioState.READY;
    }

    /**
     * Reads the current lifecycle state without starting audio or waiting for the driver.
     *
     * @return NOT_STARTED before the first request, otherwise the current session state
     */
    public static AudioState getState() {
        AudioRuntime current = runtime;
        return current == null ? AudioState.NOT_STARTED : current.state();
    }

    /**
     * Returns the current session's recorded lifecycle failure without blocking.
     *
     * @return startup, runtime, or cleanup failure, or null if none is recorded
     */
    public static Throwable getFailure() {
        AudioRuntime current = runtime;
        return current == null ? null : current.failure();
    }

    /**
     * Starts the engine-owned audio worker without waiting for its readiness stage.
     * Startup failure is observable through the public asynchronous lifecycle API.
     */
    static void init() {
        startAsync();
    }

    /**
     * Requests shutdown without joining the worker. Pending commands are rejected by
     * the captured session; an in-flight command may finish before native teardown.
     * A slow native startup call must return before its handles can be safely released.
     *
     * @return read-only stage completing after native teardown, exceptional on lifecycle failure
     */
    public static CompletionStage<Void> shutdownAsync() {
        AudioRuntime current = runtime;
        if (current == null) return CompletableFuture.<Void>completedFuture(null).minimalCompletionStage();
        current.stop();
        return current.terminated().minimalCompletionStage();
    }

    /**
     * Stops the captured engine session and waits for its owner to release native
     * resources. No lock is held while joining, and audio-thread shutdown never joins itself.
     * Interruption is restored after cleanup rather than allowing premature restart.
     */
    static void dispose() {
        AudioRuntime current = runtime;
        if (current == null) return;
        current.stop();
        current.awaitTermination();
    }

    /**
     * Publishes finite listener coordinates without touching OpenAL or waiting for readiness.
     * Equal coordinates retain the old snapshot to avoid redundant area-gain work.
     *
     * @param x finite horizontal coordinate
     * @param y finite vertical coordinate
     * @param z finite depth coordinate
     * @throws IllegalArgumentException if any coordinate is nonfinite
     */
    public static void setListenerPosition(float x, float y, float z) {
        if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(z))
            throw new IllegalArgumentException("Listener coordinates must be finite");
        ListenerPosition current = listenerPosition;
        if (current.x() == x && current.y() == y && current.z() == z) return;
        listenerPosition = new ListenerPosition(x, y, z);
    }

    /**
     * Returns the latest immutable ambient listener snapshot.
     *
     * @return logical world coordinates
     */
    public static ListenerPosition getListenerPosition() {
        return listenerPosition;
    }

    /**
     * Submits an audio-thread operation without waiting for initialization or execution.
     * Early work belongs to the captured session and is rejected if that session fails
     * or stops. Cancelling its returned future skips work still queued; work already
     * executing may finish. Callbacks normally execute on the completing thread.
     *
     * @param callable nonnull native/audio operation
     * @param <T> result type
     * @return independent result future
     */
    public static <T> CompletableFuture<T> submitAsync(Callable<T> callable) {
        Objects.requireNonNull(callable, "callable");
        return ensureStarted().submit(callable);
    }

    /**
     * Queues a command without waiting for readiness. Null is ignored for compatibility;
     * failures of this fire-and-forget API are reported to standard error. Use submitAsync
     * when a caller needs to handle a command's outcome explicitly.
     *
     * @param runnable audio-thread work, or null to do nothing
     */
    public static void run(Runnable runnable) {
        if (runnable == null) return;
        AudioRuntime current = ensureStarted();
        if (current.isAudioThread()) {
            if (current.state() != AudioState.READY) throw new IllegalStateException("Audio session is not ready", current.failure());
            runnable.run();
            return;
        }
        current.submit(() -> {
            runnable.run();
            return null;
        }).whenComplete((result, failure) -> {
            if (failure != null) failure.printStackTrace();
        });
    }

    /**
     * Executes a command synchronously, deliberately waiting for readiness and completion.
     * Audio-thread calls execute inline. Prefer run or submitAsync on the rendering thread.
     *
     * @param runnable nonnull operation
     */
    public static void sync(Runnable runnable) {
        Objects.requireNonNull(runnable, "runnable");
        call(() -> {
            runnable.run();
            return null;
        });
    }

    /**
     * Runs an operation synchronously on the audio thread. Startup failure and shutdown
     * complete queued results exceptionally instead of leaving a waiter blocked forever.
     * Interruption restores the caller's interrupt flag and cancels unexecuted work.
     *
     * @param callable nonnull operation
     * @param <T> result type
     * @return operation result
     * @throws RuntimeException if interrupted or the operation/session fails
     */
    public static <T> T call(Callable<T> callable) {
        return awaitResult(submitAsync(callable));
    }

    /**
     * Waits for one captured session's command without resubmitting it to a replacement.
     *
     * @param result command outcome
     * @param <T> result type
     * @return completed command value
     * @throws RuntimeException if the wait is interrupted or command execution fails
     */
    private static <T> T awaitResult(CompletableFuture<T> result) {
        try {
            return result.get();
        } catch (InterruptedException failure) {
            result.cancel(false);
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted waiting for audio", failure);
        } catch (ExecutionException failure) {
            Throwable cause = failure.getCause();
            if (cause instanceof RuntimeException runtimeFailure) throw runtimeFailure;
            if (cause instanceof Error error) throw error;
            throw new IllegalStateException("Audio operation failed", cause);
        }
    }

    /**
     * Creates a managed player after the current session becomes ready, without blocking.
     * SoundData and its borrowed PCM storage must remain valid until creation completes.
     *
     * @param data prepared nonnull sound data
     * @return completion with the session-owned player
     */
    public static CompletableFuture<SoundPlayer> createAsync(SoundData data) {
        Objects.requireNonNull(data, "data");
        AudioRuntime current = ensureStarted();
        return current.submit(() -> createPlayer(current, data));
    }

    /**
     * Decodes a path on the common worker pool, then creates a player on the captured
     * audio session. A stopped session is never restarted by delayed decode completion.
     *
     * @param path nonnull sound path
     * @return completion with a managed player or decode/lifecycle failure
     */
    public static CompletableFuture<SoundPlayer> loadAsync(String path) {
        /*
         * Capture lifecycle identity before scheduling decoding, which can outlive shutdown.
         */
        Objects.requireNonNull(path, "path");
        AudioRuntime current = ensureStarted();
        return CompletableFuture.supplyAsync(() -> SoundData.load(path))
                .thenCompose(data -> current.submit(() -> createPlayer(current, data)));
    }

    /**
     * Decodes encoded bytes on the common worker pool and creates a managed player
     * on the captured session. The caller must not mutate bytes until decoding completes.
     *
     * @param bytes nonnull borrowed encoded bytes
     * @return completion with a managed player or decode/lifecycle failure
     */
    public static CompletableFuture<SoundPlayer> loadAsync(byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes");
        AudioRuntime current = ensureStarted();
        return CompletableFuture.supplyAsync(() -> SoundData.load(bytes))
                .thenCompose(data -> current.submit(() -> createPlayer(current, data)));
    }

    /**
     * Synchronously decodes a path and waits for native player creation.
     *
     * @param path sound path
     * @return managed player
     */
    public static SoundPlayer load(String path) {
        return create(SoundData.load(path));
    }

    /**
     * Synchronously decodes bytes and waits for native player creation.
     *
     * @param bytes encoded sound bytes
     * @return managed player
     */
    public static SoundPlayer load(byte[] bytes) {
        return create(SoundData.load(bytes));
    }

    /**
     * Synchronously creates and registers a player on the audio thread.
     *
     * @param data prepared nonnull sound data
     * @return managed player
     */
    public static SoundPlayer create(SoundData data) {
        /*
         * Capture the session before waiting so a shutdown cannot redirect this creation.
         */
        return awaitResult(createAsync(data));
    }

    /**
     * Constructs and registers one player while its session owns the current context.
     *
     * @param current owning session
     * @param data prepared sound data
     * @return registered player
     */
    private static SoundPlayer createPlayer(AudioRuntime current, SoundData data) {
        /*
         * Registration ensures teardown owns the player even if a result is abandoned.
         */
        SoundPlayer player = new SoundPlayer(data);
        try {
            current.register(player);
            return player;
        } catch (RuntimeException | Error failure) {
            try {
                player.dispose();
            } catch (RuntimeException | Error cleanup) {
                failure.addSuppressed(cleanup);
            }
            throw failure;
        }
    }

    /**
     * Queues managed player disposal; null is ignored. Native cleanup stays on the owner.
     *
     * @param player player to dispose
     */
    public static void destroy(SoundPlayer player) {
        if (player != null) run(player::dispose);
    }

    /**
     * Reports whether the caller owns the current session's audio thread.
     *
     * @return true on the current native owner, including its teardown callbacks
     */
    public static boolean isAudioThread() {
        /*
         * Session replacement is prohibited until prior native teardown has completed.
         */
        AudioRuntime current = runtime;
        return current != null && current.isAudioThread();
    }

    /**
     * Registers a player for automatic updates, delegating to the current session.
     *
     * @param player player to register, or null to ignore
     */
    public static void register(SoundPlayer player) {
        if (player == null) return;
        AudioRuntime current = ensureStarted();
        if (current.isAudioThread()) current.register(player);
        else current.submit(() -> {
            current.register(player);
            return null;
        }).whenComplete((result, failure) -> {
            if (failure != null) failure.printStackTrace();
        });
    }

    /**
     * Removes a player from automatic updates without disposing its native resources.
     *
     * @param player player to remove, or null to ignore
     */
    public static void unregister(SoundPlayer player) {
        /*
         * Inline dispatch permits player disposal to unregister during owner-thread teardown.
         */
        if (player == null) return;
        AudioRuntime current = ensureStarted();
        if (current.isAudioThread()) current.unregister(player);
        else current.submit(() -> {
            current.unregister(player);
            return null;
        }).whenComplete((result, failure) -> {
            if (failure != null) failure.printStackTrace();
        });
    }

    /**
     * Enforces native ownership for engine-side operations requiring the current context.
     *
     * @throws IllegalStateException if called outside the current audio thread
     */
    static void assertAudioThread() {
        if (!isAudioThread()) throw new IllegalStateException("Audio operation must run on the audio thread");
    }

    /**
     * Publishes a lazy session when none exists or a previous shutdown has finished.
     * A failed session remains observable and rejects work until explicitly restarted.
     *
     * @return current session accepting or rejecting this request
     */
    private static AudioRuntime ensureStarted() {
        /*
         * The fast path requires only volatile reads; native initialization never holds LOCK.
         */
        AudioRuntime current = runtime;
        if (current != null && current.state() != AudioState.STOPPED) return current;
        synchronized (LOCK) {
            current = runtime;
            if (current == null || current.state() == AudioState.STOPPED && current.terminated().isDone()) {
                current = new AudioRuntime();
                runtime = current;
                current.start();
            }
            return current;
        }
    }

    /**
     * Enumerates playback endpoint names synchronously after readiness.
     *
     * @return immutable device-name snapshot, empty if enumeration is unsupported
     */
    public static List<String> getDevices() {
        return call(() -> {
            int token;
            if (alcIsExtensionPresent(NULL, "ALC_ENUMERATE_ALL_EXT")) token = ALC_ALL_DEVICES_SPECIFIER;
            else if (alcIsExtensionPresent(NULL, "ALC_ENUMERATION_EXT")) token = ALC_DEVICE_SPECIFIER;
            else return List.of();
            List<String> devices = ALUtil.getStringList(NULL, token);
            return devices == null ? List.of() : List.copyOf(devices);
        });
    }

    /**
     * Queries the system's current default playback name synchronously.
     *
     * @return current default output name, or null if unavailable
     */
    public static String getDefaultDevice() {
        return call(() -> alcGetString(NULL, alcIsExtensionPresent(NULL, "ALC_ENUMERATE_ALL_EXT") ? ALC_DEFAULT_ALL_DEVICES_SPECIFIER : ALC_DEFAULT_DEVICE_SPECIFIER));
    }

    /**
     * Queries the active session's playback endpoint synchronously.
     *
     * @return current endpoint name, or null if unavailable
     */
    public static String getCurrentDevice() {
        return call(() -> alcGetString(runtime.device(), alcIsExtensionPresent(NULL, "ALC_ENUMERATE_ALL_EXT") ? ALC_ALL_DEVICES_SPECIFIER : ALC_DEVICE_SPECIFIER));
    }

    /**
     * Queries support for resource-preserving output switching synchronously.
     *
     * @return whether the active device supports ALC_SOFT_reopen_device
     */
    public static boolean isDeviceSwitchingSupported() {
        return call(() -> alcIsExtensionPresent(runtime.device(), "ALC_SOFT_reopen_device"));
    }

    /**
     * Synchronously switches the current device without recreating players. Null
     * selects the system default. Unsupported or failed reopen returns false.
     *
     * @param deviceName exact endpoint name, or null for the system default
     * @return whether the device was reopened successfully
     * @throws IllegalArgumentException if a nonnull name is blank or contains NUL
     */
    public static boolean switchDevice(String deviceName) {
        if (deviceName != null && (deviceName.isBlank() || deviceName.indexOf('\0') >= 0))
            throw new IllegalArgumentException("Device name must be nonblank and contain no NUL characters");
        return call(() -> {
            long device = runtime.device();
            if (!alcIsExtensionPresent(device, "ALC_SOFT_reopen_device")) return false;
            return alcReopenDeviceSOFT(device, deviceName, (IntBuffer) null);
        });
    }
}
